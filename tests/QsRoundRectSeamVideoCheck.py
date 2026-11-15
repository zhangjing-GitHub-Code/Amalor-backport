"""Detect a thin horizontal seam in a plain capsule/gutter region of a screenrecord."""

import argparse
import json
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--region", nargs=4, type=int, required=True, metavar=("X", "Y", "WIDTH", "HEIGHT"))
    parser.add_argument("--expect-seam", action="store_true", help="Check the pre-fix positive control")
    args = parser.parse_args()
    x, y, width, height = args.region
    assert x >= 0 and y >= 0 and width >= 12 and height > 6
    result = subprocess.run([
        args.ffmpeg, "-hide_banner", "-loglevel", "error", "-i", args.video,
        "-vf", f"format=rgb24,crop={width}:{height}:{x}:{y}", "-fps_mode", "passthrough",
        "-pix_fmt", "rgb24", "-f", "rawvideo", "pipe:1",
    ], check=True, capture_output=True)
    data = result.stdout
    stride = width * height * 3
    assert data and len(data) % stride == 0, "Recording must contain complete RGB frames"
    count = len(data) // stride
    assert count >= 10, "Record the click and the complete spring release"
    peak = (0, 0, 0)
    for index in range(count):
        frame = data[index * stride:(index + 1) * stride]
        light = [frame[i] + frame[i + 1] + frame[i + 2] for i in range(0, stride, 3)]
        for row in range(3, height - 3):
            run = 0
            for col in range(width):
                at = row * width + col
                # A thin ridge differs from BOTH sides in the same direction. A normal edge
                # or codec block boundary only differs from one side and must not qualify.
                above = light[at] - light[at - 3 * width]
                below = light[at] - light[at + 3 * width]
                ridge = above * below > 0 and min(abs(above), abs(below)) > 3 * 3
                run = run + 1 if ridge else 0
                if run > peak[0]:
                    peak = (run, index, y + row)
    print(json.dumps({"frames": count, "longest_seam_px": peak[0], "peak_frame": peak[1], "screen_y": peak[2]}, indent=2))
    if args.expect_seam:
        assert peak[0] >= 12, "Positive control did not reproduce the seam"
    else:
        assert peak[0] < 12, "A horizontal seam remains in the capsule background"


if __name__ == "__main__":
    main()
