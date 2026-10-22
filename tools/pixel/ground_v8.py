"""V8 bölge zemin dokuları (art/v8u/*_ground.jpg) → android/.../assets/v2/ground/<ad>.webp (512×512, dikişsiz).

Yapay zekâ dokuyu 2×2 tekrarla çiziyor: gerçek piksel ızgarasına indirilir, bir tekrar periyodu bulunup kesilir,
dünya ölçeğine (~2 dünya pikseli / sanat pikseli) en yakın-komşu büyütülüp 512'yi tam bölecek şekilde döşenir.
Periyodu olmayan (liman) doku aynalanarak dikişsiz yapılır.

python3 tools/pixel/ground_v8.py
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from ai_clean import clean  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "v2", "ground")
TMP = "/tmp/zx_v8_clean"
# kaynak → (çıktı adı, dikişsiz yöntem)
GROUNDS = {"r1_ground": ("snow_dock", "mirror"), "r2_ground": ("ash", "period"), "r3_ground": ("desert_road", "period"),
           "r4_ground": ("swamp", "period"), "k4_ground": ("base_dirt", "period")}


def period(g, axis, lo, hi):
    best = None
    for p in range(lo, hi):
        d = ((g[:, p:] - g[:, :-p]) ** 2).mean() if axis == 1 else ((g[p:] - g[:-p]) ** 2).mean()
        if best is None or d < best[0]:
            best = (d, p)
    return best[1]


def build(src, name, mode):
    p = os.path.join(TMP, src + ".png")
    if not os.path.exists(p):
        os.makedirs(TMP, exist_ok=True)
        clean(os.path.join(ROOT, "art", "v8u", src + ".jpg"), p, "green", 5.86)
    a = np.asarray(Image.open(p).convert("RGB"))[5:-5, 5:-5]
    g = a.mean(-1) - a.mean()
    H, W = g.shape
    if mode == "period":
        px, py = period(g, 1, 80, W - 30), period(g, 0, 40, H - 20)
        tile = Image.fromarray(a[4:4 + py, 4:4 + px])
    else:
        # yarım kaydırılmış kopya kenarlarda, asıl görsel ortada; geçiş bölgesi pikselleri rastgele seçilir
        # (harmanlama yok: piksel sanat renkleri saf kalır)
        t = a.copy()
        sh = np.roll(np.roll(t, t.shape[0] // 2, 0), t.shape[1] // 2, 1)
        hh, ww = t.shape[:2]
        yy, xx = np.mgrid[0:hh, 0:ww]
        m = np.minimum(np.minimum(xx, ww - 1 - xx) / (ww * 0.22), np.minimum(yy, hh - 1 - yy) / (hh * 0.22)).clip(0, 1)
        rnd = np.random.default_rng(7).random((hh, ww))
        tile = Image.fromarray(np.where((m > rnd)[..., None], t, sh))
        px, py = ww, hh
    # ~2 dünya pikseli / sanat pikseli: 512'yi tam bölen karo boyu
    nx = max(1, round(512 / (px * 2.1)))
    ny = max(1, round(512 / (py * 2.1)))
    tw, th = 512 // nx, 512 // ny
    tile = tile.resize((tw, th), Image.NEAREST)
    out = Image.new("RGB", (512, 512))
    for j in range(ny):
        for i in range(nx):
            out.paste(tile, (i * tw, j * th))
    out.save(os.path.join(OUT, name + ".webp"), lossless=True)
    print(name, "periyot", (px, py), "karo", (tw, th), "×", (nx, ny))


if __name__ == "__main__":
    for s, (n, m) in GROUNDS.items():
        build(s, n, m)
