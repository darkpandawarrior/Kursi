#!/usr/bin/env python3
"""Discard screenshot re-renders that differ only by rasterisation noise.

The screenshots workflow decided "did anything change?" with `git diff`, which is a BYTE
comparison. Skia does not promise byte-identical output across runs: measured on 2026-08-15,
docs/screenshots/4p_chit_dossier.png differed from a fresh CI render by exactly ONE pixel, in the
blue channel, by a value of 1 — out of 1,296,000 pixels, a 3-byte file difference.

That was enough to open a baseline-refresh PR. Every run. Two were stacked open at once, and one of
them (#64) proposed content byte-identical to main, so it could never have "fixed" anything.

A gate that fires on invisible noise trains people to merge baseline PRs without looking, which is
precisely when a real regression slips through. This restores any file whose difference is below the
threshold, so `git diff` afterwards sees only changes a human would actually see.

Deliberately conservative — it discards nothing that could plausibly be a real change:

  * max per-channel delta must be <= MAX_DELTA (default 2)
  * AND the share of differing pixels must be < MAX_CHANGED_FRACTION (default 0.05%)

Both conditions must hold. A genuine UI change fails the second immediately: a moved label or a
recoloured chip touches thousands of pixels, not hundreds. A theme change fails both.

Anything above threshold is left staged and the refresh PR opens as before. Size or mode changes are
never discarded.

Usage:  python3 scripts/drop-screenshot-noise.py docs/screenshots
Exit:   always 0 — this is a filter, not a gate. The gate is the git diff that follows.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

try:
    from PIL import Image, ImageChops
except ImportError:  # pragma: no cover - the workflow installs Pillow
    print("drop-screenshot-noise: Pillow not installed; leaving every change staged", file=sys.stderr)
    raise SystemExit(0)

MAX_DELTA = 2
MAX_CHANGED_FRACTION = 0.0005  # 0.05%


def changed_files(directory: str) -> list[str]:
    """Files git already considers modified — the only ones worth opening."""
    out = subprocess.run(
        ["git", "diff", "--name-only", "--", directory],
        capture_output=True, text=True, check=False,
    ).stdout
    return [line for line in out.splitlines() if line.endswith(".png")]


def committed_bytes(path: str) -> bytes | None:
    r = subprocess.run(["git", "show", f"HEAD:{path}"], capture_output=True, check=False)
    return r.stdout if r.returncode == 0 else None


def is_noise(path: str) -> tuple[bool, str]:
    import io

    old_raw = committed_bytes(path)
    if old_raw is None:
        return False, "new file"
    new_img = Image.open(path).convert("RGBA")
    old_img = Image.open(io.BytesIO(old_raw)).convert("RGBA")
    if new_img.size != old_img.size:
        return False, f"size {old_img.size} -> {new_img.size}"

    diff = ImageChops.difference(new_img, old_img)
    flat = list(diff.convert("L").getdata())
    changed = sum(1 for v in flat if v)
    if changed == 0:
        return True, "identical pixels, encoder-only difference"

    max_delta = max(max(band.getdata()) for band in diff.split())
    fraction = changed / len(flat)
    if max_delta <= MAX_DELTA and fraction < MAX_CHANGED_FRACTION:
        return True, f"{changed} px ({fraction * 100:.4f}%), max delta {max_delta}"
    return False, f"{changed} px ({fraction * 100:.2f}%), max delta {max_delta}"


def main() -> int:
    directory = sys.argv[1] if len(sys.argv) > 1 else "docs/screenshots"
    files = changed_files(directory)
    if not files:
        print("drop-screenshot-noise: nothing changed")
        return 0

    dropped, kept = [], []
    for path in files:
        if not Path(path).exists():
            continue
        noise, why = is_noise(path)
        (dropped if noise else kept).append((path, why))

    for path, why in dropped:
        subprocess.run(["git", "checkout", "--", path], check=False)
        print(f"  noise, restored: {path} — {why}")
    for path, why in kept:
        print(f"  REAL CHANGE:     {path} — {why}")

    print(f"drop-screenshot-noise: {len(dropped)} restored as noise, {len(kept)} kept as real")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
