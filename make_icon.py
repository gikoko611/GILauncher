#!/usr/bin/env python3
"""Cyber-gold 3D G.I launcher icons for mdpi through xxxhdpi."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT = Path(__file__).resolve().parent / "assets" / "fonts" / "Orbitron.ttf"
ROOT = Path(__file__).resolve().parent / "res"
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def vgrad(w, h, stops):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        col = stops[-1][1]
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                u = 0 if p1 == p0 else (t - p0) / (p1 - p0)
                col = lerp(c0, c1, u)
                break
        for x in range(w):
            px[x, y] = col
    return img


def paste_round(base, box, radius, fill_img):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    tile = fill_img.resize((w, h), Image.Resampling.BILINEAR)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    base.paste(tile, (x0, y0), mask)


def render(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    s = size
    m = int(s * 0.06)
    plate = (m, m, s - m, s - m)
    r = int(s * 0.24)

    shadow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (plate[0], plate[1] + int(s * 0.04), plate[2], plate[3] + int(s * 0.03)),
        radius=r,
        fill=(0, 0, 0, 140),
    )
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(max(1, s // 48))))

    gold = vgrad(
        s,
        s,
        [
            (0.0, (255, 246, 216, 255)),
            (0.18, (255, 231, 168, 255)),
            (0.48, (224, 177, 90, 255)),
            (0.78, (138, 100, 32, 255)),
            (1.0, (58, 42, 16, 255)),
        ],
    )
    paste_round(img, plate, r, gold)

    inset = int(s * 0.085)
    inner = (plate[0] + inset, plate[1] + inset, plate[2] - inset, plate[3] - inset)
    core = vgrad(
        s,
        s,
        [
            (0.0, (42, 36, 24, 255)),
            (0.45, (16, 14, 11, 255)),
            (1.0, (7, 6, 10, 255)),
        ],
    )
    paste_round(img, inner, int(r * 0.72), core)

    d = ImageDraw.Draw(img)
    # thin gold inner rim
    d.rounded_rectangle(inner, radius=int(r * 0.72), outline=(224, 177, 90, 160), width=max(1, s // 64))

    # top specular — a short band, not a lid over the mark
    spec_h = int(s * 0.16)
    spec = vgrad(s, spec_h, [(0.0, (255, 255, 255, 90)), (1.0, (255, 255, 255, 0))])
    spec_box = (plate[0] + int(s * 0.08), plate[1] + int(s * 0.04), plate[2] - int(s * 0.08), plate[1] + spec_h)
    paste_round(img, spec_box, int(r * 0.7), spec)

    d = ImageDraw.Draw(img)
    # corner ticks
    tick = max(2, s // 28)
    arm = int(s * 0.11)
    gold_line = (255, 231, 168, 220)
    corners = [
        (inner[0] + int(s * 0.06), inner[1] + int(s * 0.06), 1, 1),
        (inner[2] - int(s * 0.06), inner[1] + int(s * 0.06), -1, 1),
        (inner[0] + int(s * 0.06), inner[3] - int(s * 0.06), 1, -1),
        (inner[2] - int(s * 0.06), inner[3] - int(s * 0.06), -1, -1),
    ]
    for x, y, sx, sy in corners:
        d.line((x, y, x + arm * sx, y), fill=gold_line, width=tick)
        d.line((x, y, x, y + arm * sy), fill=gold_line, width=tick)

    text = "G.I"
    font = ImageFont.truetype(str(FONT), max(10, int(s * 0.30)))
    box = d.textbbox((0, 0), text, font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    x = (s - tw) / 2 - box[0]
    y = (s - th) / 2 - box[1]
    d.text((x, y + max(1, s // 80)), text, font=font, fill=(58, 36, 8, 180))
    d.text((x, y), text, font=font, fill=(255, 246, 216, 255))
    return img


def main() -> None:
    for folder, size in SIZES.items():
        out = ROOT / folder
        out.mkdir(parents=True, exist_ok=True)
        render(size).save(out / "ic_launcher.png")
    print("icons ok")


if __name__ == "__main__":
    main()
