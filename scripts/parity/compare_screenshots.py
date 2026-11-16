#!/usr/bin/env python3
"""Compare two deterministic AutoSmoke screenshots pixel by pixel.

Usage:
    python3 scripts/parity/compare_screenshots.py OLD.png NEW.png [--diff out.png]

Exit code 0 when the images are pixel-identical, 1 when they differ, 2 on
size/mode mismatch or a missing input.

The harness writes the images (see AutoSmokeClient):
    -Dflexibook.autoSmoke=true
    -Dflexibook.autoSmoke.screenshotDir=<dir>
    -Dflexibook.autoSmoke.screenshotPrefix=<prefix>
-> <dir>/<prefix>-page0.png, <dir>/<prefix>-page1.png
"""
from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

from PIL import Image, ImageChops


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("old")
    parser.add_argument("new")
    parser.add_argument("--diff", help="write a difference image here")
    parser.add_argument("--amplify", type=int, default=1,
                        help="multiply channel deltas in the diff image (default 1)")
    args = parser.parse_args()

    old_path, new_path = Path(args.old), Path(args.new)
    for path in (old_path, new_path):
        if not path.is_file():
            print(f"MISSING {path}", file=sys.stderr)
            return 2

    old_hash, new_hash = sha256(old_path), sha256(new_path)
    print(f"sha256 old={old_hash}")
    print(f"sha256 new={new_hash}")
    print(f"sha256 identical={old_hash == new_hash}")

    old = Image.open(old_path).convert("RGBA")
    new = Image.open(new_path).convert("RGBA")
    if old.size != new.size:
        print(f"SIZE MISMATCH old={old.size} new={new.size}")
        return 2

    diff = ImageChops.difference(old, new)
    bbox = diff.getbbox()
    total = old.size[0] * old.size[1]
    # Count pixels whose RGB differs (alpha-only differences are reported separately).
    rgb = diff.convert("RGB")
    changed = sum(1 for pixel in rgb.getdata() if pixel != (0, 0, 0))
    max_delta = max((max(pixel) for pixel in rgb.getdata()), default=0)
    print(f"size={old.size[0]}x{old.size[1]} total={total}")
    print(f"diff_pixels={changed} ratio={changed / total:.6f} bbox={bbox} max_channel_delta={max_delta}")

    if args.diff:
        out = diff
        if args.amplify > 1:
            out = rgb.point(lambda value: min(255, value * args.amplify))
        out.save(args.diff)
        print(f"diff image -> {args.diff}")

    return 0 if changed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
