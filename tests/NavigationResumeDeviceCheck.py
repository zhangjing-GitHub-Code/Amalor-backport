"""Verify the currently open Amalor toolbar survives Home -> launcher re-entry (Chinese UI)."""

import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--title", required=True)
    parser.add_argument("--kill-background", action="store_true")
    args = parser.parse_args()
    path = "/sdcard/amalor-navigation-check.xml"

    def adb(*command):
        return subprocess.check_output([args.adb, *command], encoding="utf-8", errors="replace").strip()

    def title_visible():
        adb("shell", "uiautomator", "dump", path)
        root = ET.fromstring(adb("exec-out", "cat", path))
        bounds = list(map(int, re.findall(r"\d+", root.find("node").get("bounds"))))
        height = bounds[3] - bounds[1]
        return any(n.get("package") == "me.acardia.amalor" and n.get("text") == args.title and
                   int(re.findall(r"\d+", n.get("bounds"))[1]) < height / 4 for n in root.iter("node"))

    try:
        assert title_visible(), "Open the requested Amalor page before running the check"
        before = adb("shell", "pidof", "me.acardia.amalor")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        time.sleep(1)
        if args.kill_background:
            adb("shell", "am", "kill", "me.acardia.amalor")
            time.sleep(0.5)
        launch = adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c",
                     "android.intent.category.LAUNCHER", "-f", "0x10200000", "-n", "me.acardia.amalor/.MainActivity")
        print(launch)
        after = adb("shell", "pidof", "me.acardia.amalor")
        print(f"Activity process: {before} -> {after}")
        assert title_visible(), "Returning to Amalor lost the current navigation destination"
        if args.kill_background:
            assert before != after, "The process-death case did not actually restart the app process"
        print("Navigation resume check passed:", args.title)
    finally:
        adb("shell", "rm", "-f", path)


if __name__ == "__main__":
    main()
