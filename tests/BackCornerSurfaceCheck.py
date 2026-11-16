"""Check the compositor's retained corner metadata during a held predictive-back preview."""

import argparse
import json
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dump", help="dumpsys SurfaceFlinger captured while the finger is held")
    parser.add_argument("--component", default="com.android.settings.SubSettings")
    parser.add_argument("--expect-vendor", action="store_true", help="Check the pre-fix positive control")
    args = parser.parse_args()
    text = Path(args.dump).read_text(encoding="utf-8")
    apps = {}
    preview = False
    for block in re.split(r"(?m)^\s*Layer \[", text)[1:]:
        lines = [line.strip() for line in block.splitlines() if line.strip()]
        header = lines[0]
        if ("animation-leash of predict_back" in header and "scale x=" in block
                and "layer not reachable from root" not in block and "hidden by parent" not in block):
            scale = float(re.search(r"scale x=([\d.]+)", block)[1])
            preview |= 0 < scale < 1
        if "VRI-" not in header or args.component not in header or "invisible reason=" in block:
            continue
        # Read the separate vendor fields AFTER CornerRadius(...), not its always-2 defaults.
        curve = re.search(r"cornerRadius=.*?\)\s+weight=([-\d.]+)\s+type=(\d+)", block)
        assert curve, "The visible app layer must report its corner state"
        apps[header] = {"weight": float(curve[1]), "type": int(curve[2])}
    assert preview and apps, "Capture a transformed predictive leash and its visible app buffer"
    vendor = any(value["weight"] != 2 or value["type"] != 0 for value in apps.values())
    print(json.dumps({"preview": preview, "app_layers": apps}, ensure_ascii=False, indent=2))
    if args.expect_vendor:
        assert vendor, "Positive control did not reproduce inherited vendor curvature"
    else:
        assert not vendor, "The app surface still clips with vendor curvature below its standard leash"


if __name__ == "__main__":
    main()
