"""V19: ağaç ve kayalar bölgelerine uysun.

python3 tools/assetgen/v19_nature.py [önizleme.png]
  - kayalar ağaçların yanında çakıl taşı gibi kalıyordu: büyük kayalar 1,6, küçükler 1,4 kat büyür
  - bölge kaya varyantları: çölde kumtaşı (rock_sand0/1), limanda karlı kaya (rock_snow0)
  - huş ve çamın gövde dibindeki bordo/kırmızı lekeler koyu yeşile döner (yeşil ormanda göze batıyordu)
"""
import colorsys
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from atlas_patch import Atlas  # noqa: E402

BIG = ("s/rock_big0", "s/rock_big1", "s/rock_big2")
SMALL = ("s/rock_small0", "s/rock_small1", "s/rock_small2")
RED_TREES = ("s/birch0", "s/pine2", "s/oak_autumn")


def hsv_map(im, fn):
    a = np.asarray(im.convert("RGBA")).astype(np.float32) / 255.0
    rgb = a[..., :3]
    mx = rgb.max(-1); mn = rgb.min(-1)
    v = mx
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    d = np.maximum(mx - mn, 1e-6)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) / 6.0
    h, s, v = fn(h, s, v, a[..., 3])
    i = np.floor(h * 6) % 6
    f = h * 6 - np.floor(h * 6)
    p = v * (1 - s); q = v * (1 - f * s); t = v * (1 - (1 - f) * s)
    out = np.zeros_like(rgb)
    for k, (cr, cg, cb) in enumerate([(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)]):
        m = i == k
        out[..., 0] = np.where(m, cr, out[..., 0]); out[..., 1] = np.where(m, cg, out[..., 1]); out[..., 2] = np.where(m, cb, out[..., 2])
    res = np.dstack([out, a[..., 3:4]])
    return Image.fromarray((res * 255).clip(0, 255).astype(np.uint8), "RGBA")


def sandstone(im):
    # gri taş → sıcak kumtaşı: ton turuncu-bej, doygunluk orta, ışık biraz artar
    return hsv_map(im, lambda h, s, v, a: (np.full_like(h, 0.075), np.clip(s * 0.6 + 0.32, 0, 0.62), np.clip(v * 1.08 + 0.04, 0, 1)))


def snowy(im):
    a = np.asarray(im.convert("RGBA")).astype(np.float32)
    h, w = a.shape[:2]
    al = a[..., 3] > 30
    # üste bakan yüzeyler (yukarısı saydam ya da bir üst piksel çok daha koyu değil) ve üst yarı karla kaplanır
    up = np.zeros_like(al)
    up[1:] = ~al[:-1]
    ys = np.arange(h)[:, None].repeat(w, 1)
    top = np.nonzero(al)[0].min() if al.any() else 0
    bot = np.nonzero(al)[0].max() if al.any() else h
    upper = ys < top + (bot - top) * 0.55
    lum = a[..., :3].mean(-1)
    cap = al & (up | (upper & (lum > np.percentile(lum[al], 45) if al.any() else 0)))
    # kar kapağını 2 piksel aşağı uzat
    for _ in range(2):
        cap[1:] |= cap[:-1] & al[1:]
    out = a.copy()
    snow = np.array([226, 236, 246], dtype=np.float32)
    out[cap, :3] = out[cap, :3] * 0.25 + snow * 0.75
    # gövde soğuk tona
    rest = al & ~cap
    out[rest, :3] = out[rest, :3] * 0.8 + np.array([70, 86, 104], dtype=np.float32) * 0.2
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")


def no_red(im):
    # kırmızı-bordo (ton < 0,04 ya da > 0,88) ve doygun pikseller koyu yeşile (gövdenin kahvesi ton ~0,06-0,1 kalır)
    def fn(h, s, v, a):
        red = ((h < 0.035) | (h > 0.88)) & (s > 0.25)
        return np.where(red, 0.30, h), np.where(red, np.clip(s * 0.8, 0, 0.7), s), np.where(red, v * 0.8, v)
    return hsv_map(im, fn)


def main():
    A = Atlas()
    sp = A.m["sprites"]
    groups = {i: g for g, ids in A.m["groups"].items() for i in ids}
    adds = {}
    prev = []

    def get(key):
        v = sp[key]
        p, x, y, w, h, ax, ay = v[:7]
        k = v[7] if len(v) > 7 else 1.0
        return A.page(p).crop((x, y, x + w, y + h)), ax, ay, k, groups[p]

    for key in BIG + SMALL:
        im, ax, ay, k, g = get(key)
        f = 1.6 if key in BIG else 1.4
        adds.setdefault(g, []).append((key, im, ax, ay, round(k * f, 5)))
        if key in ("s/rock_big0", "s/rock_big1"):
            n = key[-1]
            s1 = sandstone(im)
            adds[g].append(("s/rock_sand" + n, s1, ax, ay, round(k * f, 5)))
            prev += [im, s1]
            # karlı varyant yalnız iri kaya için (yassı kayrak taşında kar lekeli görünüyordu)
            if n == "0":
                s2 = snowy(im)
                adds[g].append(("s/rock_snow0", s2, ax, ay, round(k * f, 5)))
                prev.append(s2)
    for key in RED_TREES:
        if key not in sp:
            continue
        im, ax, ay, k, g = get(key)
        if key == "s/oak_autumn":
            continue  # sonbahar meşesi bilerek kızıl
        r = no_red(im)
        adds.setdefault(g, []).append((key, r, ax, ay, k))
        prev += [im, r]
    if not os.environ.get("DRY"):
        for g, items in adds.items():
            A.patch(g, add=items)
        A.save()
    if len(sys.argv) > 1:
        S = 200
        sheet = Image.new("RGBA", (S * 3, S * ((len(prev) + 2) // 3)), (90, 110, 70, 255))
        for i, im in enumerate(prev):
            t = im.copy()
            t.thumbnail((S - 8, S - 8))
            sheet.alpha_composite(t, ((i % 3) * S + 4, (i // 3) * S + 4))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
