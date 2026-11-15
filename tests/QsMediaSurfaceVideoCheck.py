"""Check the media-card handoff on a stationary backdrop, using two empty RGB patches."""

import argparse
import json
import subprocess


def distance(a, b):
    return max(abs(x - y) for x, y in zip(a, b))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--card", type=int, nargs=2, required=True, metavar=("X", "Y"))
    parser.add_argument("--panel", type=int, nargs=2, required=True, metavar=("X", "Y"))
    parser.add_argument("--alpha", type=int, default=138, help="Resting SE1 alpha, 0..255")
    parser.add_argument("--expect-overdraw", action="store_true", help="Check the pre-fix positive control")
    args = parser.parse_args()
    assert 0 <= args.alpha <= 255 and min(*args.card, *args.panel) >= 0
    size = 10
    left, top = min(args.card[0], args.panel[0]), min(args.card[1], args.panel[1])
    width, height = abs(args.card[0] - args.panel[0]) + size, abs(args.card[1] - args.panel[1]) + size
    data = subprocess.run([
        args.ffmpeg, "-hide_banner", "-loglevel", "error", "-i", args.video,
        "-vf", f"fps=60,format=rgb24,crop={width}:{height}:{left}:{top}",
        "-pix_fmt", "rgb24", "-f", "rawvideo", "pipe:1",
    ], check=True, capture_output=True).stdout
    stride = width * height * 3
    assert data and len(data) % stride == 0, "Recording must contain complete RGB frames"

    def sample(frame, point):
        x, y = point[0] - left, point[1] - top
        indices = [((y + dy) * width + x + dx) * 3 for dy in range(size) for dx in range(size)]
        return tuple(sum(frame[i + channel] for i in indices) / len(indices) for channel in range(3))

    frames = [(sample(memoryview(data)[i:i + stride], args.card),
               sample(memoryview(data)[i:i + stride], args.panel)) for i in range(0, len(data), stride)]
    assert len(frames) >= 90, "Record the collapse and at least half a second at rest"
    rest_card, rest_panel = frames[-1]
    assert all(distance(panel, rest_panel) <= 2 and distance(card, rest_card) <= 2
               for card, panel in frames[-30:]), "Backdrop/card must be stationary at the end"
    assert max(distance(panel, rest_panel) for _, panel in frames) > 5, "The adjacent patch must be covered before collapse"
    # Once the adjacent panel is exposed and stable, geometry is near its destination.
    # Calibrate the stationary spatial gradient at rest; only the panel's transmitted portion
    # may vary. A second SE1 fill produces an additional dark/light step before handoff.
    checked = []
    for index, (card, panel) in enumerate(frames):
        if distance(panel, rest_panel) > 2:
            continue
        expected = tuple(c + (p - r) * (1 - args.alpha / 255) for c, p, r in zip(rest_card, panel, rest_panel))
        checked.append((distance(card, expected), index))
    assert len(checked) > 30, "Include the nearly collapsed card as well as its resting state"
    error, index = max(checked)
    print(json.dumps({"frames": len(frames), "checked_frames": len(checked),
                      "peak_rgb_error": round(error, 3), "peak_seconds": round(index / 60, 3)}, indent=2))
    if args.expect_overdraw:
        assert error > 4, "Positive control did not reproduce the duplicate material"
    else:
        assert error <= 4, "The media background changes strength at handoff"


if __name__ == "__main__":
    main()
