"""V8 ekipman simgeleri (art/v6/items):
- kask/ceket/bot/pelerin: eski 3D renderlardan piksel simgeye (küçült, renk azalt, 1 px koyu kontur)
- yüzük ve kolyeler: kodla çizilen küçük piksel simgeler
- sandık kademeleri: chest.png (alet sandığı) renk tonuyla ahşap / demir / askerî

Kullanım: python3 tools/pixel/equip_icons.py [render_items_dizini]
"""
import colorsys
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
OUT = os.path.join(ROOT, "art", "v6", "items")
SRC = sys.argv[1] if len(sys.argv) > 1 else "/home/user/zx_render3/items"
OUTLINE = (18, 14, 12, 255)


def outline(im):
    a = np.array(im)
    solid = a[..., 3] > 0
    h, w = solid.shape
    pad = np.zeros((h + 2, w + 2, 4), np.uint8)
    pad[1:-1, 1:-1] = a
    sp = np.zeros((h + 2, w + 2), bool)
    sp[1:-1, 1:-1] = solid
    ring = np.zeros_like(sp)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        ring |= np.roll(np.roll(sp, dy, 0), dx, 1)
    ring &= ~sp
    pad[ring] = OUTLINE
    return Image.fromarray(pad)


def pixelize(path, size=30, colors=20):
    im = Image.open(path).convert("RGBA")
    a = np.array(im)
    ys, xs = np.nonzero(a[..., 3] > 20)
    im = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    k = size / max(im.size)
    im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
    a = np.array(im)
    alpha = a[..., 3] > 110
    rgb = Image.fromarray(a[..., :3]).quantize(colors, method=Image.Quantize.MEDIANCUT).convert("RGB")
    rgb = np.array(rgb)
    # biraz kontrast + doygunluk (3D render soluk kalır)
    hsv = np.array(Image.fromarray(rgb).convert("HSV"), float)
    hsv[..., 1] = np.clip(hsv[..., 1] * 1.15, 0, 255)
    hsv[..., 2] = np.clip((hsv[..., 2] - 128) * 1.12 + 132, 0, 255)
    rgb = np.array(Image.fromarray(hsv.astype(np.uint8), "HSV").convert("RGB"))
    out = np.zeros((*alpha.shape, 4), np.uint8)
    out[..., :3] = rgb
    out[..., 3] = alpha * 255
    return outline(Image.fromarray(out))


def canvas(w, h):
    return np.zeros((h, w, 4), np.uint8)


def put(a, x, y, c):
    if 0 <= y < a.shape[0] and 0 <= x < a.shape[1]:
        a[y, x] = (*c, 255)


def ring(metal, gem, hi):
    """Yandan bakan yüzük: kalın halka + üstte taş."""
    a = canvas(22, 22)
    cx, cy, r0, r1 = 11, 13, 5.2, 7.6
    for y in range(22):
        for x in range(22):
            d = ((x - cx) ** 2 + ((y - cy) * 1.15) ** 2) ** 0.5
            if r0 <= d <= r1:
                shade = 1.0 + 0.35 * (-(x - cx) - (y - cy)) / r1  # sol üstten ışık
                put(a, x, y, tuple(int(min(255, c * shade)) for c in metal))
    # taş yuvası + taş
    for y in range(2, 8):
        for x in range(8, 15):
            if abs(x - 11) + abs(y - 5) <= 3:
                put(a, x, y, gem)
    put(a, 10, 4, hi)
    put(a, 10, 3, hi)
    for x in range(8, 15):
        put(a, x, 8, tuple(int(c * 0.7) for c in metal))
    return outline(Image.fromarray(a))


def necklace(cord, pend, hi, shape):
    a = canvas(24, 26)
    # V biçimli zincir/ip
    for t in np.linspace(0, 1, 60):
        x = 3 + t * 9
        y = 2 + (t ** 1.4) * 13
        put(a, int(round(x)), int(round(y)), cord)
        put(a, int(round(23 - x - 2)), int(round(y)), cord)
    # kolye ucu
    cx, cy = 11, 19
    for y in range(14, 26):
        for x in range(4, 19):
            dx, dy = x - cx, y - cy
            inside = (shape == "tooth" and abs(dx) <= 3 - max(0, dy) * 0.5 and -4 <= dy <= 5) or \
                     (shape == "round" and dx * dx + dy * dy <= 13) or \
                     (shape == "shard" and abs(dx) * 1.6 + abs(dy) <= 6)
            if inside:
                k = 1.0 + 0.3 * (-dx - dy) / 6
                put(a, x, y, tuple(int(min(255, c * k)) for c in pend))
    put(a, cx - 1, cy - 2, hi)
    put(a, cx - 1, cy - 1, hi)
    return outline(Image.fromarray(a))


def tint(im, hue, sat, val):
    a = np.array(im.convert("RGBA"))
    out = a.copy()
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            r, g, b, al = a[y, x]
            if al == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.25:  # yalnız renkli gövde (siyah kontur ve metal köşeler kalır)
                h, s, v = hue, min(1, s * sat), min(1, v * val)
            rr, gg, bb = colorsys.hsv_to_rgb(h, s, v)
            out[y, x, :3] = (int(rr * 255), int(gg * 255), int(bb * 255))
    return Image.fromarray(out)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in ["helm_basic", "helm_police", "helm_military", "helm_titan", "jacket_basic", "jacket_police", "jacket_military",
                 "jacket_titan", "boots_basic", "boots_police", "boots_military", "boots_titan", "cloak"]:
        p = os.path.join(SRC, name + ".png")
        if os.path.exists(p):
            pixelize(p, 30 if not name.startswith("boots") else 28).save(os.path.join(OUT, name + ".png"))
    ring((190, 110, 60), (60, 160, 90), (220, 255, 220)).save(os.path.join(OUT, "ring_copper.png"))
    ring((170, 175, 185), (60, 120, 220), (210, 230, 255)).save(os.path.join(OUT, "ring_hunter.png"))
    ring((70, 60, 80), (230, 70, 40), (255, 220, 160)).save(os.path.join(OUT, "ring_titan.png"))
    necklace((140, 110, 80), (225, 215, 190), (255, 255, 240), "tooth").save(os.path.join(OUT, "neck_bone.png"))
    necklace((180, 160, 90), (70, 170, 150), (210, 255, 240), "round").save(os.path.join(OUT, "neck_amulet.png"))
    necklace((90, 80, 110), (120, 60, 190), (230, 190, 255), "shard").save(os.path.join(OUT, "neck_dark.png"))
    base = Image.open(os.path.join(OUT, "chest.png"))
    tint(base, 0.075, 0.9, 0.85).save(os.path.join(OUT, "chest_wood.png"))
    tint(base, 0.58, 0.18, 0.95).save(os.path.join(OUT, "chest_iron.png"))
    tint(base, 0.2, 0.75, 0.7).save(os.path.join(OUT, "chest_military.png"))


if __name__ == "__main__":
    main()
