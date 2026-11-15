"""Check an untouched neighbour's rendered width in an adb screenrecord, using FFmpeg."""

import argparse
from collections import Counter
import json
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--sample", nargs=3, type=int, required=True, metavar=("X", "Y", "WIDTH"))
    parser.add_argument("--rest-width", type=int, required=True)
    parser.add_argument("--expect-still", action="store_true", help="Verify the pre-fix failure recording")
    args = parser.parse_args()
    x, y, width = args.sample
    assert x >= 0 and y >= 0 and width > args.rest_width > 0
    result = subprocess.run([
        args.ffmpeg, "-hide_banner", "-loglevel", "error", "-i", args.video,
        "-vf", f"format=rgb24,crop={width}:1:{x}:{y}", "-fps_mode", "passthrough",
        "-pix_fmt", "rgb24", "-f", "rawvideo", "pipe:1",
    ], check=True, capture_output=True)
    data = result.stdout
    stride = width * 3
    assert data and len(data) % stride == 0, "Recording must contain complete RGB scanlines"
    color = Counter(tuple(data[i:i + 3]) for i in range(0, stride, 3)).most_common(1)[0][0]
    widths = []
    for offset in range(0, len(data), stride):
        line = data[offset:offset + stride]
        fill = [i for i in range(width) if max(abs(line[i * 3 + c] - color[c]) for c in range(3)) <= 10]
        assert fill, "Sample lost the neighbour's fill; check the recording and crop"
        widths.append(fill[-1] - fill[0] + 1)
    assert len(widths) >= 10, "Record the entire click/release, including idle endpoints"
    compression = widths[0] - min(widths)
    print(json.dumps({
        "frames": len(widths), "fill_rgb": color, "rest_px": widths[0],
        "minimum_px": min(widths), "maximum_px": max(widths),
        "peak_frame": widths.index(min(widths)), "final_px": widths[-1],
        "compression_px": compression,
    }, indent=2))
    assert abs(widths[0] - args.rest_width) <= 3, "Sample must isolate the intended neighbour"
    assert abs(widths[-1] - widths[0]) <= 3, "Neighbour must return to its original width"
    if args.expect_still:
        assert max(widths) - min(widths) <= 3, "Pre-fix neighbour was expected to remain still"
    else:
        assert compression >= 12, "Click never visibly compressed the untouched neighbour"
        assert max(widths) <= widths[0] + 5, "Sample crossed into another tile or an unrelated transition"


if __name__ == "__main__":
    main()
