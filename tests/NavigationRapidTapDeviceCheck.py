"""Chinese-UI smoke test: repeated list taps, one-back return, and immediate re-entry."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    args = parser.parse_args()
    remote = "/sdcard/amalor-navigation-repeat.xml"

    def adb(*command):
        return subprocess.check_output([args.adb, *command], encoding="utf-8", errors="replace", timeout=30).strip()

    def nodes():
        adb("shell", "uiautomator", "dump", "--compressed", remote)
        return [node for node in ET.fromstring(adb("exec-out", "cat", remote)).iter("node")
                if node.get("package") == "me.acardia.amalor"]

    def bounds(node):
        return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))

    def click(node):
        x1, y1, x2, y2 = bounds(node)
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

    def title_visible(items, title):
        return any(n.get("text") == title and bounds(n)[1] < 600 for n in items)

    def config_page():
        for _ in range(6):
            items = nodes()
            if title_visible(items, "配置") and any(n.get("text") == "微信输入法" for n in items):
                return items
            if any(n.get("content-desc") == "返回" for n in items):
                adb("shell", "input", "keyevent", "4")
            else:
                matches = [n for n in items if n.get("text") == "配置"]
                if matches:
                    click(matches[-1])
                else:
                    time.sleep(.4)
        raise AssertionError("Cannot reach the configuration list")

    try:
        adb("shell", "am", "start", "-W", "-n", "me.acardia.amalor/.MainActivity")
        config_page()
        process = adb("shell", "pidof", "me.acardia.amalor")
        for title, repeats in (("微信输入法", 5), ("系统界面", 3)):
            for attempt in range(repeats):
                items = config_page()
                target = next(n for n in items if n.get("text") == title and bounds(n)[1] >= 600)
                # Capture the old row's coordinates once, like a user's queued double tap.
                click(target)
                click(target)
                time.sleep(.4)
                assert title_visible(nodes(), title), "Repeated tap failed to open " + title
                assert adb("shell", "pidof", "me.acardia.amalor") == process, "Application process restarted"
                adb("shell", "input", "keyevent", "4")
                time.sleep(.3)
                items = nodes()
                assert title_visible(items, "配置"), "One back left a duplicate destination behind"
                print(title, "repeat/back/re-entry", attempt + 1, "passed", flush=True)
        print("Navigation device checks passed; process:", process)
    finally:
        adb("shell", "rm", "-f", remote)


if __name__ == "__main__":
    main()
