#!/usr/bin/env python
"""Extract frames from a video so they can be inspected as images.

Usage:
    python tools/video_frames.py VIDEO [--out DIR] [--every SECONDS] [--max N]
                                      [--width PX] [--sheet COLSxROWS]

Produces:
    <out>/frame_0001.png ...   individual frames
    <out>/sheet.png            contact sheet (grid) for a quick overview

Needs the ffmpeg binary that ships with imageio-ffmpeg:
    pip install imageio-ffmpeg
"""
import argparse
import os
import subprocess
import sys

try:
    import imageio_ffmpeg
except ImportError:
    print("imageio-ffmpeg is not installed: pip install imageio-ffmpeg", file=sys.stderr)
    sys.exit(2)


def ffmpeg_exe():
    exe = imageio_ffmpeg.get_ffmpeg_exe()
    if not os.path.exists(exe):
        print("ffmpeg binary not found at", exe, file=sys.stderr)
        sys.exit(2)
    return exe


def run(args):
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stderr[-2000:], file=sys.stderr)
        sys.exit(result.returncode)


def probe(exe, video):
    """Returns (duration_seconds, width, height, fps)."""
    out = subprocess.run([exe, "-i", video], capture_output=True, text=True)
    text = out.stderr
    duration = 0.0
    for line in text.splitlines():
        if "Duration:" in line:
            stamp = line.split("Duration:")[1].split(",")[0].strip()
            try:
                h, m, s = stamp.split(":")
                duration = int(h) * 3600 + int(m) * 60 + float(s)
            except ValueError:
                duration = 0.0
    width = height = 0
    for line in text.splitlines():
        if "Stream #" in line and "Video:" in line:
            parts = line.split(",")
            for part in parts:
                part = part.strip()
                if part.startswith("Video:"):
                    res = part.split("Video:")[1].strip().split(" ")[0]
                    if "x" in res:
                        try:
                            width, height = (int(v) for v in res.split("x")[:2])
                        except ValueError:
                            pass
    return duration, width, height


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("--out", default="tools/frames")
    ap.add_argument("--every", type=float, default=0.5, help="seconds between frames")
    ap.add_argument("--max", type=int, default=40)
    ap.add_argument("--width", type=int, default=960, help="scale width of the frames")
    ap.add_argument("--sheet", default="4x3", help="contact sheet grid, e.g. 4x3")
    args = ap.parse_args()

    exe = ffmpeg_exe()
    os.makedirs(args.out, exist_ok=True)

    duration, width, height = probe(exe, args.video)
    print(f"video : {args.video}")
    print(f"size  : {width}x{height}")
    print(f"time  : {duration:.2f}s")

    pattern = os.path.join(args.out, "frame_%04d.png")
    run([exe, "-hide_banner", "-loglevel", "error", "-y",
         "-i", args.video,
         "-vf", f"fps=1/{max(0.05, args.every)},scale={args.width}:-2",
         "-frames:v", str(args.max),
         pattern])
    frames = sorted(f for f in os.listdir(args.out) if f.startswith("frame_"))
    print(f"frames: {len(frames)} written to {args.out}")

    if not frames:
        print("no frames produced")
        return

    cols, rows = (int(v) for v in args.sheet.lower().split("x"))
    sheet = os.path.join(args.out, "sheet.png")
    run([exe, "-hide_banner", "-loglevel", "error", "-y",
         "-i", pattern,
         "-frames:v", "1",
         "-vf", f"tile={cols}x{rows}", sheet])
    print(f"sheet : {sheet} ({cols}x{rows} tiles)")


if __name__ == "__main__":
    main()
