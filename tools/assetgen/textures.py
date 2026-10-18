"""Dikişsiz zemin dokuları (96x96). Rust bu dokuları piksel piksel harmanlayarak zemin parçalarını üretir."""
import math
import numpy as np
from PIL import Image
from sdf2 import ramp

S = 96
rng = np.random.default_rng(11)


def pnoise(size, seed, octaves=((3, 1.0), (6, 0.55), (12, 0.3), (24, 0.15))):
    r = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size] / size * math.tau
    n = np.zeros((size, size))
    for f, a in octaves:
        for _ in range(4):
            fx, fy = r.integers(-f, f + 1, 2)
            n += a * np.sin(fx * x + fy * y + r.uniform(0, math.tau))
    n -= n.min()
    return n / n.max()


def colorize(n, base, steps=7, spread=0.5, bayer=True):
    tones = ramp(base, steps, 0.05, spread)[::-1]  # koyudan açığa
    pal = np.array(tones, np.uint8)
    if bayer:
        b = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16 - 0.5
        n = n + np.tile(b, (S // 4, S // 4)) * (0.9 / steps)
    idx = np.clip((n * steps).astype(int), 0, steps - 1)
    return pal[idx]


def stamp(img, x, y, col):
    img[y % S, x % S] = col


def grass(seed=1, base="#5f9a3e"):
    n = pnoise(S, seed) * 0.75 + 0.25 * pnoise(S, seed + 50, ((16, 1.0), (32, 0.6)))
    img = colorize(n, base, 7, 0.55)
    r = np.random.default_rng(seed)
    tones = ramp(base, 5, 0.06, 0.6)
    # kısa ot yaprakları: açık uç, koyu dip
    for _ in range(420):
        x, y = r.integers(0, S, 2)
        L = r.integers(2, 5)
        lean = r.choice([-1, 0, 0, 1])
        for k in range(L):
            c = tones[0] if k == L - 1 else (tones[1] if k > L / 2 else tones[3])
            stamp(img, x + (lean if k > L / 2 else 0), y - k, c)
    # yonca/benek
    for _ in range(25):
        x, y = r.integers(0, S, 2)
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            stamp(img, x + dx, y + dy, tones[2])
    return img


def dirt(seed=2, base="#8a6440"):
    n = pnoise(S, seed)
    img = colorize(n, base, 7, 0.45)
    r = np.random.default_rng(seed)
    t = ramp("#8e8a84", 5)
    for _ in range(70):
        x, y = r.integers(0, S, 2)
        stamp(img, x, y, t[1]); stamp(img, x + 1, y, t[2]); stamp(img, x, y + 1, t[3]); stamp(img, x + 1, y + 1, t[4])
    return img


def gravel(seed=3):
    img = dirt(seed, "#9a8462")
    r = np.random.default_rng(seed)
    t = ramp("#a8a49c", 5)
    for _ in range(260):
        x, y = r.integers(0, S, 2)
        stamp(img, x, y, t[r.integers(0, 3)]); stamp(img, x, y + 1, t[4])
    return img


def asphalt(seed=4):
    n = pnoise(S, seed, ((4, 1.0), (16, 0.4), (48, 0.5)))
    img = colorize(n * 0.6 + 0.2, "#4a4c52", 6, 0.35)
    r = np.random.default_rng(seed)
    t = ramp("#4a4c52", 5)
    for _ in range(300):
        x, y = r.integers(0, S, 2)
        stamp(img, x, y, t[r.choice([0, 1, 3])])
    # çatlaklar
    for _ in range(3):
        x, y = r.integers(0, S, 2)
        for k in range(r.integers(10, 30)):
            x += r.integers(-1, 2); y += r.integers(0, 2)
            stamp(img, x, y, t[4])
    return img


def sidewalk(seed=5):
    n = pnoise(S, seed, ((8, 1.0), (32, 0.5)))
    img = colorize(n * 0.5 + 0.3, "#a8a49c", 5, 0.25)
    t = ramp("#a8a49c", 5)
    for i in range(S):
        for k in (0, 32, 64):
            img[k, i] = t[4]; img[i, k] = t[4]
            img[(k + 1) % S, i] = t[1]; img[i, (k + 1) % S] = t[1]
    return img


def concrete(seed=6):
    n = pnoise(S, seed, ((4, 1.0), (24, 0.6), (48, 0.3)))
    img = colorize(n * 0.5 + 0.25, "#8e8e8a", 6, 0.3)
    r = np.random.default_rng(seed)
    t = ramp("#8e8e8a", 5)
    for _ in range(40):
        x, y = r.integers(0, S, 2)
        stamp(img, x, y, t[4])
    return img


def mud(seed=7):
    n = pnoise(S, seed)
    img = colorize(n, "#4e5a36", 7, 0.45)
    r = np.random.default_rng(seed)
    t = ramp("#5a7a5a", 5)
    for _ in range(30):  # su birikintileri parıltısı
        x, y = r.integers(0, S, 2)
        for dx in range(r.integers(2, 6)):
            stamp(img, x + dx, y, t[0])
    return img


def dark_grass(seed=8):
    return grass(seed, "#477e36")


def dry_grass(seed=9):
    return grass(seed, "#8a9a46")


TEXTURES = [
    # sıra Rust'taki zemin malzemesi kimlikleriyle aynı olmalı
    ("grass", grass), ("dark_grass", dark_grass), ("dry_grass", dry_grass), ("dirt", dirt), ("gravel", gravel),
    ("asphalt", asphalt), ("sidewalk", sidewalk), ("concrete", concrete), ("mud", mud),
]


def build_all():
    """Tek bir atlas: 96 piksel yüksek, malzemeler yan yana."""
    imgs = [f() for _, f in TEXTURES]
    atlas = np.concatenate(imgs, axis=1)
    return Image.fromarray(atlas, "RGB"), [n for n, _ in TEXTURES]
