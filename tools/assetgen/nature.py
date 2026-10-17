"""Ağaç, çalı, kaya, kütük modelleri (SDF)."""
import math
import random
import numpy as np
from sdf import (Material, sd_sphere, sd_round_cone, sd_ellipsoid, sd_displace, rot_y, rot_x, v3)

MATERIALS = {
    "leaf": Material("#b6e36b", "#7fc44a", "#4f9a3a", "#1d4428"),
    "leaf_dark": Material("#7fc44a", "#4f9a3a", "#2f6b33", "#163620"),
    "pine": Material("#6fb35a", "#3f8a4a", "#26603c", "#123224"),
    "birch_leaf": Material("#d4ec7a", "#a2d257", "#6fa844", "#2f5a26"),
    "bark": Material("#a0704c", "#8a5a3c", "#66402b", "#2e1c14"),
    "bark_dead": Material("#a89a8a", "#857767", "#5e5348", "#2e2924"),
    "birch": Material("#ffffff", "#e6e6dc", "#b8b8ae", "#4a4a44"),
    "stone": Material("#c9c9c9", "#9a9aa6", "#6c6c7c", "#2b2d3c"),
    "moss": Material("#9fd05a", "#6fa844", "#4f7a3a", "#26402a"),
    "wood_cut": Material("#f0c88a", "#d9a066", "#b07848", "#4f3322"),
    "berry": Material("#ff6a6a", "#e04848", "#a02c3c", "#5e1a2a"),
    "blossom": Material("#ffd0e8", "#f49ac1", "#c86a9a", "#6a2a4a"),
}


def blob_canopy(rng, center, radius, count, spread, mat, disp=0.06, freq=9.0):
    sc = []
    cx, cy, cz = center
    for i in range(count):
        a = rng.uniform(0, math.tau)
        el = rng.uniform(-0.6, 0.9)
        d = spread * rng.uniform(0.3, 1.0)
        p = (cx + math.cos(a) * math.cos(el) * d,
             cy + math.sin(el) * d * 0.7,
             cz + math.sin(a) * math.cos(el) * d)
        r = radius * rng.uniform(0.6, 1.0)
        sc.append((mat, sd_displace(sd_sphere(p, r), disp, freq, seed=rng.uniform(0, 9))))
    return sc


def oak(seed):
    rng = random.Random(seed)
    h = rng.uniform(2.3, 2.8)
    lean = rng.uniform(-0.15, 0.15)
    top = v3(lean, h, rng.uniform(-0.1, 0.1))
    sc = [("bark", sd_round_cone(v3(0, 0, 0), top, 0.24, 0.13))]
    # kökler
    for k in range(4):
        a = k * math.tau / 4 + rng.uniform(-0.3, 0.3)
        sc.append(("bark", sd_round_cone(v3(0, 0.25, 0), v3(math.cos(a) * 0.4, 0.0, math.sin(a) * 0.4), 0.11, 0.05)))
    # dallar
    for k in range(3):
        a = rng.uniform(0, math.tau)
        sc.append(("bark", sd_round_cone(v3(0, h * 0.7, 0), top + v3(math.cos(a) * 0.8, 0.4, math.sin(a) * 0.8), 0.09, 0.05)))
    n_trunk = len(sc)
    sc += blob_canopy(rng, (lean, h + 0.55, 0), 0.85, 10, 1.0, "leaf", 0.09, 6.0)
    return sc, n_trunk, h + 0.45


def canopy_tone(base_y, step=0.45, trunk_parts=0):
    """Alt kısımlar daha koyu (ortam gölgesi) + yaprak benekleri."""
    def f(p, n, tn, mid):
        leafy = n[:, 1] > -2  # hepsi
        dark = (p[:, 1] < base_y).astype(int) + (p[:, 1] < base_y - step).astype(int)
        speck = (np.sin(p[:, 0] * 41 + p[:, 2] * 37) * np.sin(p[:, 1] * 43 + p[:, 0] * 13) > 0.72).astype(int)
        return np.where(mid < trunk_parts, tn, np.clip(tn + dark + speck, 0, 2))
    return f


def pine(seed):
    rng = random.Random(seed)
    h = rng.uniform(3.6, 4.4)
    sc = [("bark", sd_round_cone(v3(0, 0, 0), v3(0, h, 0), 0.15, 0.05))]
    tiers = 5
    for i in range(tiers):
        y = 0.8 + i * (h - 0.8) / tiers
        r = 1.05 * (1 - i / tiers) + 0.3
        # her kat: geniş tabanlı koni + gürültü
        sc.append(("pine", sd_displace(sd_round_cone(v3(0, y, 0), v3(0, y + 1.0, 0), r, 0.04), 0.05, 9.0, seed=i * 1.3 + seed)))
    return sc, (0.8, (h - 0.8) / tiers)


def pine_tone(y0, dy):
    def f(p, n, tn, mid):
        frac = ((p[:, 1] - y0) / dy) % 1.0
        dark = (frac < 0.35).astype(int) + (frac < 0.15).astype(int)
        speck = (np.sin(p[:, 0] * 47 + p[:, 2] * 31) * np.sin(p[:, 1] * 53) > 0.75).astype(int)
        res = np.clip(tn + dark + speck, 0, 2)
        return np.where(mid == 0, tn, res)
    return f


def birch(seed):
    rng = random.Random(seed)
    h = rng.uniform(2.8, 3.3)
    top = v3(rng.uniform(-0.2, 0.2), h, 0)
    sc = [("birch", sd_round_cone(v3(0, 0, 0), top, 0.12, 0.07))]
    sc += blob_canopy(rng, (top[0], h + 0.2, 0), 0.6, 8, 0.75, "birch_leaf", 0.05, 10.0)
    return sc


def birch_tone(p, n, tn, mid):
    # huş kabuğundaki siyah çizgiler
    stripes = (np.sin(p[:, 1] * 23.0) > 0.85) & (np.cos(p[:, 1] * 7.0 + p[:, 0] * 9) > 0.0)
    return np.where(stripes & (mid == 0), 2, tn)


def dead_tree(seed):
    rng = random.Random(seed)
    h = rng.uniform(2.2, 2.7)
    top = v3(rng.uniform(-0.2, 0.2), h, 0)
    sc = [("bark_dead", sd_round_cone(v3(0, 0, 0), top, 0.17, 0.05))]

    def branch(a, b, r, depth):
        sc.append(("bark_dead", sd_round_cone(a, b, r, r * 0.6)))
        if depth == 0:
            return
        for _ in range(2):
            d = b - a
            ang = rng.uniform(0, math.tau)
            nb = b + d * 0.55 + v3(math.cos(ang) * 0.35, rng.uniform(0.1, 0.35), math.sin(ang) * 0.35)
            branch(b, nb, r * 0.6, depth - 1)
    for k in range(3):
        ang = rng.uniform(0, math.tau)
        start = v3(0, h * rng.uniform(0.5, 0.85), 0)
        branch(start, start + v3(math.cos(ang) * 0.6, 0.45, math.sin(ang) * 0.6), 0.07, 2)
    return sc


def bush(seed, kind="plain"):
    rng = random.Random(seed)
    w = 0.55 if kind != "wide" else 0.9
    sc = blob_canopy(rng, (0, 0.35, 0), 0.38, 7, w, "leaf_dark", 0.04, 12.0)
    if kind == "berry":
        for _ in range(9):
            a = rng.uniform(0, math.tau)
            sc.append(("berry", sd_sphere(v3(math.cos(a) * 0.5, rng.uniform(0.25, 0.75), math.sin(a) * 0.5 + 0.1), 0.06)))
    if kind == "flower":
        for _ in range(9):
            a = rng.uniform(0, math.tau)
            sc.append(("blossom", sd_sphere(v3(math.cos(a) * 0.48, rng.uniform(0.3, 0.8), math.sin(a) * 0.48 + 0.1), 0.08)))
    return sc


def rock(seed, size):
    rng = random.Random(seed)
    sc = []
    for i in range(3):
        c = v3(rng.uniform(-0.3, 0.3) * size, size * 0.35, rng.uniform(-0.2, 0.2) * size)
        R = rot_y(rng.uniform(0, 3)) @ rot_x(rng.uniform(-0.3, 0.3))
        sc.append(("stone", sd_displace(sd_ellipsoid(c, R, (size * 0.6, size * 0.45, size * 0.5)), 0.02 * size + 0.01, 7.0 / size, seed=i + seed)))
    if size > 0.7:
        sc.append(("moss", sd_displace(sd_ellipsoid(v3(0, size * 0.72, 0), np.eye(3), (size * 0.45, size * 0.12, size * 0.35)), 0.03, 12.0)))
    return sc


def stump():
    from sdf import sd_cut
    body = sd_cut(sd_round_cone(v3(0, 0, 0), v3(0, 0.5, 0), 0.26, 0.23), (0, -1, 0), -0.32)
    return [("bark", body),
            ("wood_cut", sd_cut(sd_round_cone(v3(0, 0, 0), v3(0, 0.5, 0), 0.215, 0.19), (0, -1, 0), -0.335))]


def stump_tone(p, n, tn, mid):
    # yıllık halkalar
    r = np.sqrt(p[:, 0] ** 2 + p[:, 2] ** 2)
    ring = (np.sin(r * 60) > 0.6) & (mid == 1) & (n[:, 1] > 0.8)
    top = (mid == 1) & (n[:, 1] > 0.8)
    return np.where(ring, 2, np.where(top, 0, tn))


def log_fallen():
    return [("bark", sd_round_cone(v3(-0.9, 0.2, 0), v3(0.9, 0.2, 0.1), 0.2, 0.18)),
            ("moss", sd_displace(sd_ellipsoid(v3(0.2, 0.36, 0.0), np.eye(3), (0.4, 0.06, 0.14)), 0.02, 14.0))]
