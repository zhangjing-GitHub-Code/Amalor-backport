"""Check the enabled native flashlight panel on Chinese ColorOS; start with QS visible and torch off."""

import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    args = parser.parse_args()
    command = [args.adb] + (["-s", args.serial] if args.serial else [])
    path = "/data/local/tmp/amalor-flashlight-check.xml"

    def adb(*arguments):
        return subprocess.check_output(command + list(map(str, arguments)), timeout=35,
                                       encoding="utf-8", errors="replace").strip()

    def nodes():
        adb("shell", "uiautomator", "dump", "--compressed", path)
        return list(ET.fromstring(adb("exec-out", "cat", path)).iter("node"))

    def by_id(identifier):
        return next(n for n in nodes() if n.get("resource-id") == identifier)

    def tile():
        return next(n for n in nodes() if n.get("content-desc") == "手电筒" and
                    n.get("resource-id", "").endswith(":id/oplus_separate_qs_resizeable_tile_container"))

    def bounds(node):
        return list(map(int, re.findall(r"\d+", node.get("bounds"))))

    def click(node, long=False):
        x1, y1, x2, y2 = bounds(node)
        x, y = (x1 + x2) // 2, (y1 + y2) // 2
        if long:
            adb("shell", "input", "swipe", x, y, x, y, 750)
        else:
            adb("shell", "input", "tap", x, y)
        time.sleep(0.4)

    def open_panel():
        click(tile(), long=True)
        by_id("flashlight_title")
        return by_id("com.android.systemui:id/slider")

    started = False
    try:
        assert tile().get("checked") == "false", "Turn the flashlight off before running"
        started = True
        pid = adb("shell", "pidof", "com.android.systemui")
        since = adb("shell", "date '+%m-%d %H:%M:%S.000'")
        x1, y1, x2, y2 = bounds(open_panel())
        x = (x1 + x2) // 2
        for _ in range(2):
            adb("shell", "input", "swipe", x, y2 - 10, x, y1 - 40, 900)
            time.sleep(0.3)
        logs = adb("logcat", "-d", "-T", since, "-s", "CameraService:I", "*:S")
        levels = {int(level) for level in re.findall(
            rf"Torch strength for camera id \S+ changed to (\d+) for client PID {pid}\b", logs)}
        assert len(levels) >= 2, f"CameraService did not confirm multiple brightness levels: {levels}"
        click(by_id("flashlight_off"))
        assert tile().get("checked") == "false", "Off must dismiss the panel and turn the light off"
        print("Native QS entry / CameraService levels / Off:", sorted(levels), flush=True)

        click(tile())
        assert tile().get("checked") == "true", "Normal QS click must still turn the light on"
        open_panel()
        click(by_id("flashlight_done"))
        assert tile().get("checked") == "true", "Done must keep the current torch state"
        click(tile())
        assert tile().get("checked") == "false", "Normal QS click must still turn the light off"
        print("Single-click toggle / Done preserves light: passed", flush=True)

        for close in ("done", "back", "outside"):
            slider = open_panel()
            if close == "done":
                click(by_id("flashlight_done"))
            elif close == "back":
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
            else:
                adb("shell", "input", "tap", x, bounds(slider)[1] // 2)
            assert tile().get("checked") == "false", f"Reopen/{close} must preserve the off state"
            print(f"Reopen/{close}: passed", flush=True)
        assert adb("shell", "pidof", "com.android.systemui") == pid, "SystemUI restarted during the check"
        crashes = adb("logcat", "-d", "-T", since, f"--pid={pid}", "-s", "AndroidRuntime:E", "*:S")
        assert "FATAL EXCEPTION" not in crashes, crashes
        print("Repeated Done / Back / outside dismissal, stable SystemUI: passed", flush=True)
    finally:
        try:
            if started:
                off = next((n for n in nodes() if n.get("resource-id") == "flashlight_off"), None)
                if off is not None:
                    click(off)
                current = tile()
                if current.get("checked") == "true":
                    click(current)
                assert tile().get("checked") == "false", "Could not restore the flashlight to off"
        finally:
            adb("shell", "rm", "-f", path)


if __name__ == "__main__":
    main()
