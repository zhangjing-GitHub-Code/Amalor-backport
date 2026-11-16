"""Check an untouched QS glyph moves with its tile without changing rendered dimensions."""

import argparse
import json
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--region", nargs=4, type=int, required=True, metavar=("X", "Y", "WIDTH", "HEIGHT"))
    parser.add_argument("--light", action="store_true", help="Measure a light glyph on a dark tile")
    parser.add_argument("--threshold", type=int, default=100)
    args = parser.parse_args()
    x, y, width, height = args.region
    assert min(x, y) >= 0 and min(width, height) > 0
    data = subprocess.check_output([
        args.ffmpeg, "-hide_banner", "-loglevel", "error", "-i", args.video,
        "-vf", f"format=rgb24,crop={width}:{height}:{x}:{y}", "-fps_mode", "passthrough",
        "-pix_fmt", "rgb24", "-f", "rawvideo", "pipe:1",
    ])
    stride = width * height * 3
    assert data and len(data) % stride == 0, "Record complete frames"
    bounds = []
    for offset in range(0, len(data), stride):
        frame = data[offset:offset + stride]
        points = [i // 3 for i in range(0, stride, 3)
                  if (min(frame[i:i + 3]) > args.threshold if args.light else max(frame[i:i + 3]) < args.threshold)]
        assert points, "Glyph disappeared or crop/threshold is incorrect"
        xs = [p % width for p in points]
        ys = [p // width for p in points]
        bounds.append((min(xs), min(ys), max(xs), max(ys)))
    widths = [r - l + 1 for l, _, r, _ in bounds]
    heights = [b - t + 1 for _, t, _, b in bounds]
    centres = [(l + r) / 2 for l, _, r, _ in bounds]
    movement = max(centres) - min(centres)
    print(json.dumps({
        "frames": len(bounds), "width_range": [min(widths), max(widths)],
        "height_range": [min(heights), max(heights)], "centre_movement_px": movement,
        "peak_frame": max(range(len(centres)), key=lambda i: abs(centres[i] - centres[0])),
        "endpoint_shift_px": centres[-1] - centres[0],
    }, indent=2))
    assert len(bounds) >= 10, "Include both idle endpoints and the entire pulse"
    assert max(widths) - min(widths) <= 2, "Glyph width changed during tile deformation"
    assert max(heights) - min(heights) <= 2, "Glyph height changed or was clipped"
    assert movement >= 6, "Glyph did not follow the neighbour's displacement"
    assert abs(centres[-1] - centres[0]) <= 2, "Glyph failed to return with its tile"


if __name__ == "__main__":
    main()
