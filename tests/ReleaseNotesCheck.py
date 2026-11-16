"""Run with python tests/ReleaseNotesCheck.py; exercises real divergent Git branches."""

import importlib.util
import os
import subprocess
import sys
import tempfile
from pathlib import Path


script = Path(__file__).resolve().parents[1] / ".github/scripts/generate_release_notes.py"
spec = importlib.util.spec_from_file_location("release_notes", script)
notes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notes)

with tempfile.TemporaryDirectory(prefix="amalor-release-check-") as temp:
    repo = Path(temp)

    def git(*args):
        return subprocess.check_output(["git", *args], cwd=repo, encoding="utf-8", stderr=subprocess.PIPE).strip()

    def commit(filename, subject):
        (repo / filename).write_text(subject, encoding="utf-8")
        git("add", "--", filename)
        git("-c", "user.name=ReleaseNotesCheck", "-c", "user.email=check@example.invalid", "commit", "-m", subject)
        return git("rev-parse", "HEAD")

    git("init", "--initial-branch=main")
    shared = commit("shared.txt", "feat: shared release feature")
    git("tag", "v1.2.0")
    git("branch", "dev")
    release = commit("release.txt", "build: main-only release commit")
    git("tag", "v1.2.5")
    git("checkout", "dev")
    fix = commit("fix.txt", "fix: dev-only notification update")

    previous = Path.cwd()
    try:
        os.chdir(repo)
        entries = notes.collect_commits("v1.2.5", "dev")
        assert [entry["sha"] for entry in entries] == [fix], "Divergent release must include only unreleased dev commits"
        assert [entry["sha"] for entry in notes.collect_commits("v1.2.0", "dev")] == [fix]
        assert notes.collect_commits("v1.2.5", "main") == []
        assert [entry["sha"] for entry in notes.collect_commits("", "dev")] == [shared, fix]
        try:
            notes.collect_commits("missing-release-tag", "dev")
        except subprocess.CalledProcessError:
            pass
        else:
            raise AssertionError("Invalid release references must still fail")
    finally:
        os.chdir(previous)

    output = repo / "release-notes.md"
    env = dict(os.environ, GITHUB_STEP_SUMMARY="")
    subprocess.run([sys.executable, str(script), "--base", "v1.2.5", "--head", "dev", "--tag", "v1.2.6",
                    "--repository", "Example/Amalor", "--output", str(output)], cwd=repo, env=env, check=True)
    text = output.read_text(encoding="utf-8")
    assert "dev-only notification update" in text
    assert "main-only release commit" not in text and "shared release feature" not in text
    assert text.index("## 简体中文") < text.index("## English")
    assert "#### 🐛 修复" in text and "#### 🐛 Fixes" in text
    assert "Highlights" not in text
    assert "compare/v1.2.5...v1.2.6" in text

print("Release-note Git range checks passed: divergent branches, ancestor, empty range, initial release and invalid references")
