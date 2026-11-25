#!/usr/bin/env python3
"""Frames raw phone screenshots for the store listings: brand background, caption, rounded screenshot.

Usage: scripts/frame-screenshots.py RAW_DIR

RAW_DIR holds 1080x2400 screenshots named as in SHOTS below (take them with
`adb exec-out screencap -p > RAW_DIR/1_home.png`). Writes 1080x1920 (9:16) PNGs to
fastlane/metadata/android/en-US/images/phoneScreenshots/. Needs Pillow.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "fastlane/metadata/android/en-US/images/phoneScreenshots"
W, H = 1080, 1920
TOP_GREEN, BOTTOM_GREEN = (46, 139, 98), (19, 90, 62)

# file name, caption, crop box on the raw screenshot (status and navigation bars removed)
APP = (0, 90, 1080, 2330)
SHOTS = [
    ("1_home.png", "Know what you can\nspend today", APP),
    ("2_add.png", "Log a spend\nin two taps", APP),
    ("3_insights.png", "See where your\nmoney goes", APP),
    ("4_activity.png", "Every change kept,\nwith restore", APP),
    ("5_payments.png", "Spots UPI payments,\nasks before logging", APP),
    ("6_widgets.png", "Widgets, shortcuts\nand a Quick Settings tile", (0, 330, 1080, 1290)),
]


def font(px):
    for p in ("/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf", "/usr/share/fonts/noto/NotoSans-Bold.ttf"):
        if Path(p).exists():
            return ImageFont.truetype(p, px)
    return ImageFont.load_default(px)


def background():
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP_GREEN, BOTTOM_GREEN)))
    return img


def frame(raw: Image.Image, caption: str, box) -> Image.Image:
    img = background()
    d = ImageDraw.Draw(img)
    f = font(64)
    d.multiline_text((W // 2, 70), caption, font=f, fill="white", anchor="ma", align="center", spacing=14)

    shot = raw.convert("RGB").crop(box)
    top, bottom_margin = 330, 70
    max_w, max_h = W - 140, H - top - bottom_margin
    scale = min(max_w / shot.width, max_h / shot.height)
    shot = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    x, y = (W - shot.width) // 2, top + (max_h - shot.height) // 2 if shot.height < max_h * 0.8 else top

    radius = 44
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), radius=radius, fill=255)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x, y + 16, x + shot.width, y + shot.height + 16), radius=radius, fill=(0, 0, 0, 90))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    img = Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB")
    img.paste(shot, (x, y), mask)
    return img


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    raw_dir = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    for i, (name, caption, box) in enumerate(SHOTS, start=1):
        src = raw_dir / name
        if not src.exists():
            print("skip (missing):", src)
            continue
        frame(Image.open(src), caption, box).save(OUT / f"{i}.png", optimize=True)
        print("wrote", OUT / f"{i}.png")


if __name__ == "__main__":
    main()
