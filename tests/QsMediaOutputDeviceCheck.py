"""Check nested media-output dismissal restores the real card pixels (Chinese ColorOS; requires Pillow)."""

import argparse
import io
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageCms, ImageStat


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--output", type=Path, required=True, help="Existing directory for screenshots")
    args = parser.parse_args()
    assert args.output.is_dir()
    command = [args.adb] + (["-s", args.serial] if args.serial else [])
    remote = "/data/local/tmp/amalor-media-output-check.xml"

    def adb(*arguments):
        return subprocess.check_output(command + list(map(str, arguments)), timeout=35)

    def tree():
        adb("shell", "uiautomator", "dump", "--compressed", remote)
        return ET.fromstring(adb("exec-out", "cat", remote))

    def node(identifier):
        return next(n for n in tree().iter("node") if n.get("resource-id") == "com.android.systemui:id/" + identifier)

    def bounds(view):
        return tuple(map(int, re.findall(r"\d+", view.get("bounds"))))

    def tap(view):
        left, top, right, bottom = bounds(view)
        adb("shell", "input", "tap", (left + right) // 2, (top + bottom) // 2)

    def capture(name):
        source = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p")))
        profile = source.info.get("icc_profile")
        image = ImageCms.profileToProfile(source, ImageCms.ImageCmsProfile(io.BytesIO(profile)),
                    ImageCms.createProfile("sRGB"), outputMode="RGB") if profile else source.convert("RGB")
        image.save(args.output / f"media-output-{name}.png")
        return image

    def patch(image, x, y):
        return ImageStat.Stat(image.crop((x, y, x + 10, y + 10))).mean

    def distance(a, b):
        return max(abs(x - y) for x, y in zip(a, b))

    original = adb("shell", "cmd", "uimode", "night").decode().strip().split()[-1]
    assert original in ("yes", "no", "auto", "custom")
    pid = adb("shell", "pidof", "com.android.systemui").strip()
    try:
        for mode in ("yes", "no"):
            adb("shell", "cmd", "statusbar", "collapse")
            adb("shell", "cmd", "uimode", "night", mode)
            time.sleep(1)
            adb("shell", "cmd", "statusbar", "expand-settings")
            area = bounds(node("oplus_qs_media_panel"))
            left, top, right, bottom = area
            points = [(left + int((right - left) * fraction), bottom - 28) for fraction in (0.5, 0.75)]
            before = capture(f"{mode}-before")
            expected = [patch(before, *point) for point in points]
            panel = patch(before, right + 12, bottom - 28)
            assert max(distance(color, panel) for color in expected) > 4, "The baseline card already has no background"
            for cycle in range(2):
                adb("shell", "input", "tap", (left + right) // 2, top + int((bottom - top) * 0.4))
                node("oplus_media_panel_container_photo_layout")
                tap(node("oplus_media_panel_ll_output_switch"))
                node("detail_container")
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(1)
                assert bounds(node("oplus_qs_media_panel")) == area, "Media card geometry did not return"
                after = capture(f"{mode}-after-{cycle}")
                actual = [patch(after, *point) for point in points]
                error = max(distance(a, b) for a, b in zip(actual, expected))
                assert error <= 4, f"Media output dismissal lost/changed the card background: RGB error={error:.2f}"
                assert adb("shell", "pidof", "com.android.systemui").strip() == pid, "SystemUI restarted"
                print(f"night={mode}, cycle={cycle + 1}: background restored, max RGB error={error:.2f}", flush=True)
    finally:
        adb("shell", "cmd", "statusbar", "collapse")
        adb("shell", "cmd", "uimode", "night", original)
        adb("shell", "rm", "-f", remote)
        print("Restored night mode:", original, flush=True)


if __name__ == "__main__":
    main()
