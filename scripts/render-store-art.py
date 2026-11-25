#!/usr/bin/env python3
"""Renders the store icon (512x512) and feature graphic (1024x500) from the launcher icon vectors.

Reads the glyph path and gradient from app/src/main/res/drawable/ic_launcher_{foreground,background}.xml and
writes PNGs into fastlane/metadata/android/en-US/images/. Needs Pillow (python3 -m pip install pillow).

Usage: scripts/render-store-art.py
"""
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable"
OUT = ROOT / "fastlane/metadata/android/en-US/images"
SS = 4  # supersampling factor for smooth edges


def read_launcher():
    fg = (RES / "ic_launcher_foreground.xml").read_text()
    bg = (RES / "ic_launcher_background.xml").read_text()
    path = re.search(r'android:pathData="([^"]+)"', fg).group(1)
    scale = float(re.search(r'android:scaleX="([\d.]+)"', fg).group(1))
    tx = float(re.search(r'android:translateX="([\d.]+)"', fg).group(1))
    ty = float(re.search(r'android:translateY="([\d.]+)"', fg).group(1))
    start = re.search(r'android:startColor="#FF([0-9A-Fa-f]{6})"', bg).group(1)
    end = re.search(r'android:endColor="#FF([0-9A-Fa-f]{6})"', bg).group(1)
    return path, scale, tx, ty, hex_rgb(start), hex_rgb(end)


def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def subpaths(d):
    """Flattens an absolute M/L/H/V/Q/C/Z path into polygons (lists of points)."""
    tokens = re.findall(r"[MLHVQCZ]|-?\d*\.?\d+", d)
    polys, cur, i, cmd = [], [], 0, None
    x = y = 0.0

    def num():
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    while i < len(tokens):
        if tokens[i].isalpha():
            cmd = tokens[i]
            i += 1
            if cmd == "Z":
                if cur:
                    polys.append(cur)
                cur = []
                continue
        if cmd == "M":
            if cur:
                polys.append(cur)
            x, y = num(), num()
            cur = [(x, y)]
            cmd = "L"
        elif cmd == "L":
            x, y = num(), num()
            cur.append((x, y))
        elif cmd == "H":
            x = num()
            cur.append((x, y))
        elif cmd == "V":
            y = num()
            cur.append((x, y))
        elif cmd == "Q":
            cx, cy, ex, ey = num(), num(), num(), num()
            for t in (k / 12 for k in range(1, 13)):
                cur.append(((1 - t) ** 2 * x + 2 * (1 - t) * t * cx + t * t * ex,
                            (1 - t) ** 2 * y + 2 * (1 - t) * t * cy + t * t * ey))
            x, y = ex, ey
        elif cmd == "C":
            c1x, c1y, c2x, c2y, ex, ey = (num() for _ in range(6))
            for t in (k / 16 for k in range(1, 17)):
                m = 1 - t
                cur.append((m ** 3 * x + 3 * m * m * t * c1x + 3 * m * t * t * c2x + t ** 3 * ex,
                            m ** 3 * y + 3 * m * m * t * c1y + 3 * m * t * t * c2y + t ** 3 * ey))
            x, y = ex, ey
    if cur:
        polys.append(cur)
    return polys


def glyph_mask(size, path, to_px):
    """White-on-black mask of the glyph; subpaths combine even-odd (holes stay holes)."""
    mask = Image.new("1", (size, size), 0)
    for poly in subpaths(path):
        layer = Image.new("1", (size, size), 0)
        ImageDraw.Draw(layer).polygon([to_px(px, py) for px, py in poly], fill=1)
        mask = Image.frombytes("1", mask.size, bytes(a ^ b for a, b in zip(mask.tobytes(), layer.tobytes())))
    return mask.convert("L")


def gradient(w, h, start, end):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for yy in range(h):
        for xx in range(w):
            t = (xx / w + yy / h) / 2
            px[xx, yy] = tuple(round(s + (e - s) * t) for s, e in zip(start, end))
    return img


def icon(size, launcher):
    path, scale, tx, ty, start, end = launcher
    big = size * SS
    # The visible part of an adaptive icon is the middle 72 of its 108 units.
    unit = big / 72.0

    def to_px(px, py):
        return ((tx + px * scale - 18) * unit, (ty + py * scale - 18) * unit)

    img = gradient(big, big, start, end)
    img.paste((255, 255, 255), (0, 0), glyph_mask(big, path, to_px))
    return img.resize((size, size), Image.LANCZOS)


def font(bold, px):
    name = "NotoSans-Bold.ttf" if bold else "NotoSans-Regular.ttf"
    for base in ("/usr/share/fonts/truetype/noto", "/usr/share/fonts/noto", "/Library/Fonts"):
        p = Path(base) / name
        if p.exists():
            return ImageFont.truetype(str(p), px)
    return ImageFont.load_default(px)


def feature_graphic(launcher):
    _, _, _, _, start, end = launcher
    w, h = 1024 * SS, 500 * SS
    img = gradient(w, h, start, end)
    tile = 300 * SS
    ic = icon(300, launcher).resize((tile, tile), Image.LANCZOS)
    rounded = Image.new("L", (tile, tile), 0)
    ImageDraw.Draw(rounded).rounded_rectangle((0, 0, tile, tile), radius=tile // 4, fill=255)
    left, top = 90 * SS, (h - tile) // 2
    # Soft shadow under the tile.
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((left, top + 14 * SS, left + tile, top + tile + 14 * SS),
                                             radius=tile // 4, fill=(0, 0, 0, 70))
    img = Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB")
    img.paste(ic, (left, top), rounded)

    d = ImageDraw.Draw(img)
    x = left + tile + 70 * SS
    d.text((x, 130 * SS), "DayPurse", font=font(True, 104 * SS), fill="white")
    d.text((x, 272 * SS), "What you can spend today.", font=font(False, 40 * SS), fill=(230, 245, 236))
    d.text((x, 336 * SS), "Offline · Private · Open source", font=font(False, 30 * SS), fill=(190, 225, 205))
    return img.resize((1024, 500), Image.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    launcher = read_launcher()
    icon(512, launcher).save(OUT / "icon.png", optimize=True)
    feature_graphic(launcher).save(OUT / "featureGraphic.png", optimize=True)
    print("Wrote", OUT / "icon.png", "and", OUT / "featureGraphic.png")


if __name__ == "__main__":
    main()
