"""V2 doğa: gerçek ölçekli ağaçlar, çalılar, kayalar, otlar, çiçekler (sdf2 gölgelendirme)."""
import math
import random
import numpy as np
from sdf2 import Mat, sd_sphere, sd_round_cone, sd_ellipsoid, sd_displace, sd_cut, rot_x, rot_y, rot_z, v3

NMATS = {
    "leaf": Mat("#5f9e3e", "leaf", hue_shift=0.06, spread=0.5),
    "leaf2": Mat("#4f8a3a", "leaf", hue_shift=0.06, spread=0.5),
    "leaf_au": Mat("#b8a03a", "leaf", hue_shift=0.05, spread=0.5),
    "pine": Mat("#2f6b48", "leaf", hue_shift=0.06, spread=0.45),
    "birch_leaf": Mat("#8cbc4a", "leaf", hue_shift=0.06, spread=0.5),
    "bark": Mat("#6e4a32", "bark"),
    "bark_pine": Mat("#7a4a32", "bark"),
    "birch": Mat("#e6e2d6", "bark", spread=0.3),
    "dead": Mat("#7a6e62", "bark"),
    "bush": Mat("#4a7e38", "leaf", hue_shift=0.06, spread=0.5),
    "berry": Mat("#d8343c", spec=0.6),
    "blossom": Mat("#f2a2c8", "cloth"),
    "rock": Mat("#8e8e96", "stone"),
    "rock_dark": Mat("#6c6a70", "stone"),
    "moss": Mat("#6a9a3a", "leaf"),
    "wood_cut": Mat("#d8a870", "bark"),
    "grass": Mat("#6aa040", "leaf", spread=0.5),
    "petal_w": Mat("#f2f0e6"), "petal_y": Mat("#f2cc3a"), "petal_r": Mat("#d83a32"),
    "petal_b": Mat("#5a8ae8"), "petal_p": Mat("#a07ad8"), "core": Mat("#e8b03a"),
    "mush": Mat("#c8463a", spec=0.3), "mush_s": Mat("#eee6d6"),
    "reed": Mat("#8a9a4a", "leaf"),
}


def canopy(rng, center, radius, count, spread, mat, disp=0.07, freq=7.0, flat=0.75):
    sc = []
    cx, cy, cz = center
    for i in range(count):
        a = rng.uniform(0, math.tau)
        el = rng.uniform(-0.5, 1.0)
        d = spread * rng.uniform(0.25, 1.0)
        p = (cx + math.cos(a) * math.cos(el) * d, cy + math.sin(el) * d * flat, cz + math.sin(a) * math.cos(el) * d)
        r = radius * rng.uniform(0.55, 1.0)
        sc.append((mat, sd_displace(sd_sphere(p, r), disp, freq, seed=rng.uniform(0, 9))))
    return sc


def oak(seed, autumn=False):
    rng = random.Random(seed)
    h = rng.uniform(3.0, 3.6)
    lean = rng.uniform(-0.2, 0.2)
    top = v3(lean, h, rng.uniform(-0.1, 0.1))
    sc = [("bark", sd_round_cone(v3(0, 0, 0), top, 0.3, 0.16))]
    for k in range(5):
        a = k * math.tau / 5 + rng.uniform(-0.3, 0.3)
        sc.append(("bark", sd_round_cone(v3(0, 0.3, 0), v3(math.cos(a) * 0.55, 0.0, math.sin(a) * 0.55), 0.14, 0.05)))
    for k in range(4):
        a = rng.uniform(0, math.tau)
        sc.append(("bark", sd_round_cone(v3(0, h * 0.65, 0), top + v3(math.cos(a) * 1.1, 0.5, math.sin(a) * 1.1), 0.11, 0.05)))
    n_trunk = len(sc)
    m = "leaf_au" if autumn else ("leaf" if seed % 2 else "leaf2")
    sc += canopy(rng, (lean, h + 0.8, 0), 1.0, 16, 1.5, m, 0.09, 5.5)
    return sc, n_trunk


def pine(seed):
    rng = random.Random(seed)
    h = rng.uniform(5.2, 6.4)
    sc = [("bark_pine", sd_round_cone(v3(0, 0, 0), v3(0, h, 0), 0.2, 0.05))]
    tiers = 6
    for i in range(tiers):
        y = 1.1 + i * (h - 1.1) / tiers
        r = 1.5 * (1 - i / tiers) + 0.35
        sc.append(("pine", sd_displace(sd_round_cone(v3(0, y, 0), v3(0, y + 1.25, 0), r, 0.05), 0.06, 8.0, seed=i * 1.3 + seed)))
    return sc, 1


def birch(seed):
    rng = random.Random(seed)
    h = rng.uniform(3.6, 4.2)
    top = v3(rng.uniform(-0.25, 0.25), h, 0)
    sc = [("birch", sd_round_cone(v3(0, 0, 0), top, 0.15, 0.08))]
    sc += canopy(rng, (top[0], h + 0.4, 0), 0.7, 12, 0.95, "birch_leaf", 0.06, 8.0)
    return sc, 1


def dead_tree(seed):
    rng = random.Random(seed)
    h = rng.uniform(2.8, 3.4)
    top = v3(rng.uniform(-0.2, 0.2), h, 0)
    sc = [("dead", sd_round_cone(v3(0, 0, 0), top, 0.2, 0.05))]

    def branch(a, b, r, depth):
        sc.append(("dead", sd_round_cone(a, b, r, r * 0.6)))
        if depth == 0:
            return
        for _ in range(2):
            d = b - a
            ang = rng.uniform(0, math.tau)
            nb = b + d * 0.5 + v3(math.cos(ang) * 0.4, rng.uniform(0.1, 0.4), math.sin(ang) * 0.4)
            branch(b, nb, r * 0.6, depth - 1)
    for _ in range(3):
        ang = rng.uniform(0, math.tau)
        st = v3(0, h * rng.uniform(0.5, 0.85), 0)
        branch(st, st + v3(math.cos(ang) * 0.7, 0.5, math.sin(ang) * 0.7), 0.08, 2)
    return sc, len(sc)


def bush(seed, kind="plain"):
    rng = random.Random(seed)
    w = 0.45 if kind != "wide" else 0.75
    sc = canopy(rng, (0, 0.32, 0), 0.32, 9, w, "bush", 0.04, 11.0, 0.6)
    if kind == "berry":
        for _ in range(12):
            a = rng.uniform(0, math.tau)
            sc.append(("berry", sd_sphere(v3(math.cos(a) * 0.42, rng.uniform(0.2, 0.62), math.sin(a) * 0.42 + 0.1), 0.035)))
    if kind == "flower":
        for _ in range(12):
            a = rng.uniform(0, math.tau)
            sc.append(("blossom", sd_sphere(v3(math.cos(a) * 0.4, rng.uniform(0.25, 0.66), math.sin(a) * 0.4 + 0.1), 0.05)))
    return sc


def rock(seed, size):
    rng = random.Random(seed)
    sc = []
    for i in range(3):
        c = v3(rng.uniform(-0.3, 0.3) * size, size * 0.33, rng.uniform(-0.2, 0.2) * size)
        R = rot_y(rng.uniform(0, 3)) @ rot_x(rng.uniform(-0.3, 0.3))
        m = "rock" if i else "rock_dark"
        sc.append((m, sd_displace(sd_ellipsoid(c, R, (size * 0.6, size * 0.42, size * 0.5)), 0.02 * size + 0.008, 7.0 / size, seed=i + seed)))
    if size > 0.6:
        sc.append(("moss", sd_displace(sd_ellipsoid(v3(0, size * 0.68, 0), np.eye(3), (size * 0.42, size * 0.1, size * 0.32)), 0.025, 12.0)))
    return sc


def stump():
    body = sd_cut(sd_round_cone(v3(0, 0, 0), v3(0, 0.6, 0), 0.3, 0.27), (0, -1, 0), -0.38)
    top = sd_cut(sd_round_cone(v3(0, 0, 0), v3(0, 0.6, 0), 0.26, 0.23), (0, -1, 0), -0.395)
    return [("bark", body), ("wood_cut", top)]


def log_fallen():
    return [("bark", sd_round_cone(v3(-1.0, 0.22, 0), v3(1.0, 0.22, 0.1), 0.22, 0.2)),
            ("moss", sd_displace(sd_ellipsoid(v3(0.2, 0.4, 0.0), np.eye(3), (0.45, 0.06, 0.15)), 0.02, 14.0))]


def grass_tuft(seed):
    rng = random.Random(seed)
    sc = []
    for _ in range(rng.randint(6, 10)):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(0, 0.06)
        base = v3(math.cos(a) * r, 0, math.sin(a) * r)
        tip = base + v3(rng.uniform(-0.08, 0.08), rng.uniform(0.18, 0.32), rng.uniform(-0.06, 0.06))
        sc.append(("grass", sd_round_cone(base, tip, 0.016, 0.004)))
    return sc


def flower(kind, seed=0):
    rng = random.Random(seed)
    pm = {"daisy": "petal_w", "butter": "petal_y", "poppy": "petal_r", "corn": "petal_b", "lavender": "petal_p"}[kind]
    sc = []
    for i in range(3):
        a = rng.uniform(0, math.tau)
        base = v3(math.cos(a) * 0.05, 0, math.sin(a) * 0.05)
        top = base + v3(rng.uniform(-0.03, 0.03), rng.uniform(0.12, 0.18), 0)
        sc.append(("grass", sd_round_cone(base, top, 0.008, 0.006)))
        sc.append((pm, sd_ellipsoid(top + v3(0, 0.01, 0), np.eye(3), (0.03, 0.012, 0.03))))
        sc.append(("core", sd_sphere(top + v3(0, 0.02, 0), 0.01)))
    return sc


def mushrooms(seed):
    rng = random.Random(seed)
    sc = []
    for _ in range(3):
        x, z = rng.uniform(-0.08, 0.08), rng.uniform(-0.06, 0.06)
        h = rng.uniform(0.05, 0.1)
        sc.append(("mush_s", sd_round_cone(v3(x, 0, z), v3(x, h, z), 0.012, 0.01)))
        sc.append(("mush", sd_cut(sd_sphere(v3(x, h, z), 0.035), (0, 1, 0), h - 0.005)))
    return sc


def reeds(seed):
    rng = random.Random(seed)
    sc = []
    for _ in range(8):
        a = rng.uniform(0, math.tau)
        base = v3(math.cos(a) * 0.1, 0, math.sin(a) * 0.1)
        tip = base + v3(rng.uniform(-0.1, 0.1), rng.uniform(0.6, 0.9), 0)
        sc.append(("reed", sd_round_cone(base, tip, 0.012, 0.006)))
        sc.append(("bark", sd_round_cone(tip - v3(0, 0.15, 0), tip - v3(0, 0.02, 0), 0.02, 0.02)))
    return sc
