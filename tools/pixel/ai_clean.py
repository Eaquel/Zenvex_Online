"""Yapay zekâ ile üretilmiş "piksel sanat" görselini gerçek piksel ızgarasına indirir ve düz arka planı siler.

1) Kenar profilinin Fourier analiziyle piksel boyu bulunur (ör. 1408 px genişlik / 240 = 5.87),
2) ızgara kayması hücre içi sapmayı en aza indiren değerle seçilir,
3) her hücre ortanca renge indirilir (JPEG gürültüsü gider),
4) arka plan (yeşil #00FF00 ya da macenta #FF00FF) ve kenardaki renk sızıntısı şeffaf yapılır.

python3 tools/pixel/ai_clean.py giriş.jpg çıkış.png green|magenta [piksel_boyu]
"""
import sys

import numpy as np
from PIL import Image


def pixel_size(a, lo=2.0, hi=12.0):
    g = a.mean(-1)
    best = (0, lo)
    for ax in (0, 1):
        e = np.abs(np.diff(g, axis=ax)).sum(axis=1 - ax)
        e = e - e.mean()
        n = np.arange(len(e))
        for s in np.arange(lo, hi, 0.01):
            v = abs((e * np.exp(-2j * np.pi * n / s)).sum())
            if v > best[0]:
                best = (v, s)
    return best[1]


def fit_offset(a, s):
    H, W, _ = a.shape
    best = None
    for ox in np.arange(0, s, 0.5):
        for oy in np.arange(0, s, 0.5):
            xs = ((np.arange(W) - ox) // s).astype(int)
            ys = ((np.arange(H) - oy) // s).astype(int)
            _, inv, cnt = np.unique((ys[:, None] * 10000 + xs[None, :]).ravel(), return_inverse=True, return_counts=True)
            v = 0.0
            for c in range(3):
                ch = a[..., c].ravel()
                v += ((ch - (np.bincount(inv, ch) / cnt)[inv]) ** 2).mean()
            if best is None or v < best[0]:
                best = (v, ox, oy)
    return best[1], best[2]


def clean(src, dst, bg="green", s=None):
    a = np.asarray(Image.open(src).convert("RGB")).astype(float)
    s = s or pixel_size(a)
    ox, oy = fit_offset(a, s)
    H, W, _ = a.shape
    nw, nh = int((W - ox) // s), int((H - oy) // s)
    out = np.zeros((nh, nw, 3))
    for j in range(nh):
        y0, y1 = int(round(oy + j * s + 1)), int(round(oy + (j + 1) * s - 1))
        for i in range(nw):
            x0, x1 = int(round(ox + i * s + 1)), int(round(ox + (i + 1) * s - 1))
            out[j, i] = np.median(a[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1)].reshape(-1, 3), 0)
    r, g, b = out[..., 0], out[..., 1], out[..., 2]
    if bg == "magenta":
        key = (r > 150) & (b > 150) & (g < 120)
        spill = (r > g + 60) & (b > g + 60)
    else:
        key = (g > 140) & (r < 120) & (b < 120)
        spill = (g > r + 30) & (g > b + 30)
    alpha = np.where(key | spill, 0, 255).astype(np.uint8)
    img = Image.fromarray(np.dstack([out.clip(0, 255).astype(np.uint8), alpha]), "RGBA")
    img.save(dst)  # kırpılmaz: rig koordinatları bu tuvale göre
    print(f"{src}: piksel {s:.2f}, kayma ({ox}, {oy}) → {img.size}")


if __name__ == "__main__":
    clean(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "green",
          float(sys.argv[4]) if len(sys.argv) > 4 else None)
