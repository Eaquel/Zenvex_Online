"""Animasyonlu efekt kareleri (numpy ile prosedürel): patlama, namlu alevi, duman, kıvılcım, kan, kalkan, şifa,
öfke, ışınlanma, zehir, seviye atlama, şok dalgası, toz, iz.

Çıktı: <dizin>/fx/<ad>/<kare>.png (+ meta.json: kare sayısı, fps, döngü, karışım) — pack.py "fx" grubuna koyar.
Kullanım: /opt/bvenv/bin/python tools/assetgen/fx_gen.py /home/user/zx_render3
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

OUT = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "/home/user/zx_render3", "fx")
rng = np.random.default_rng(7)


def fbm(n, seed, octaves=5, base=4):
    r = np.random.default_rng(seed)
    acc = np.zeros((n, n))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        g = base * 2 ** o
        small = r.random((g + 1, g + 1)).astype(np.float32)
        im = Image.fromarray((small * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
        acc += np.asarray(im, np.float32) / 255 * amp
        tot += amp
        amp *= 0.5
    return acc / tot


def grid(n):
    y, x = np.mgrid[0:n, 0:n] / (n - 1) * 2 - 1
    return x, y, np.sqrt(x * x + y * y), np.arctan2(y, x)


def ramp(t, stops):
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (4,), np.float32)
    ps = [s[0] for s in stops]
    for i in range(len(stops) - 1):
        a, b = stops[i], stops[i + 1]
        m = (t >= a[0]) & (t <= b[0])
        k = ((t - a[0]) / max(b[0] - a[0], 1e-6))[m][:, None]
        ca, cb = np.array(a[1], np.float32), np.array(b[1], np.float32)
        out[m] = ca * (1 - k) + cb * k
    out[t > ps[-1]] = stops[-1][1]
    return out


def save(name, frames, fps, loop=False, blend="add", scale=1.0):
    d = os.path.join(OUT, name)
    os.makedirs(d, exist_ok=True)
    for i, f in enumerate(frames):
        a = np.clip(f, 0, 1)
        Image.fromarray((a * 255).astype(np.uint8), "RGBA").save(os.path.join(d, f"{i}.png"))
    json.dump({"frames": len(frames), "fps": fps, "loop": loop, "blend": blend, "scale": scale}, open(os.path.join(d, "meta.json"), "w"))
    print(" ", name, len(frames))


def rgba(col, a):
    out = np.zeros(a.shape + (4,), np.float32)
    out[..., :3] = col
    out[..., 3] = a
    return out


# =========================================================================== efektler
def explosion(n=256, F=16):
    x, y, r, th = grid(n)
    noise = [fbm(n, 100 + i, 5, 3) for i in range(3)]
    frames = []
    for i in range(F):
        t = i / (F - 1)
        rad = 0.25 + 0.65 * (1 - (1 - t) ** 2.2)
        nz = noise[0] * 0.6 + noise[1] * 0.4
        warp = r + (nz - 0.5) * 0.45
        core = np.clip(1 - warp / rad, 0, 1)
        heat = np.clip(core * (1.6 - t * 1.4) + (nz - 0.5) * 0.4, 0, 1)
        fire = ramp(heat, [(0, (0.2, 0.05, 0.02, 0)), (0.25, (0.6, 0.12, 0.02, 0.8)), (0.5, (1.0, 0.45, 0.05, 1)),
                           (0.75, (1.0, 0.8, 0.3, 1)), (1.0, (1.0, 1.0, 0.85, 1))])
        smoke_a = np.clip(core * 1.5, 0, 1) * np.clip((t - 0.25) * 1.6, 0, 1) * (1 - t * 0.6)
        smoke = rgba((0.16, 0.14, 0.13), smoke_a * (0.6 + 0.4 * noise[2]))
        # birleştir: duman altta, ateş üstte
        a_f = fire[..., 3] * np.clip(1.2 - t * 1.1, 0, 1)
        out = smoke.copy()
        out[..., :3] = smoke[..., :3] * (1 - a_f[..., None]) + fire[..., :3] * a_f[..., None]
        out[..., 3] = np.clip(smoke_a * 0.8 + a_f, 0, 1)
        frames.append(out)
    save("explosion", frames, 20, blend="normal")


def muzzle(n=96):
    x, y, r, th = grid(n)
    frames = []
    for v in range(3):
        k = 5 + v
        rays = np.clip(np.cos(th * k + v) * 0.5 + 0.5, 0, 1) ** 3
        sh = np.clip(1 - r / (0.35 + 0.55 * rays), 0, 1) ** 1.5
        core = np.clip(1 - r / 0.3, 0, 1) ** 2
        a = np.clip(sh + core, 0, 1)
        col = ramp(core * 0.6 + sh * 0.5, [(0, (1, 0.5, 0.1, 1)), (0.5, (1, 0.85, 0.4, 1)), (1, (1, 1, 0.9, 1))])
        col[..., 3] = a
        frames.append(col)
    save("muzzle", frames, 1, blend="add")


def smoke(n=128, F=12, col=(0.55, 0.53, 0.5), name="smoke"):
    x, y, r, th = grid(n)
    nz = fbm(n, 300, 5, 3)
    frames = []
    for i in range(F):
        t = i / (F - 1)
        rad = 0.35 + 0.6 * t
        a = np.clip(1 - (r + (nz - 0.5) * 0.5) / rad, 0, 1) ** 1.3 * (1 - t) ** 1.2 * 0.85
        c = np.array(col) * (0.85 + 0.3 * nz[..., None])
        out = np.zeros((n, n, 4), np.float32)
        out[..., :3] = c
        out[..., 3] = a
        frames.append(out)
    save(name, frames, 14, blend="normal")


def spark(n=96, F=6):
    x, y, r, th = grid(n)
    angles = rng.uniform(0, 2 * math.pi, 14)
    frames = []
    for i in range(F):
        t = i / (F - 1)
        a = np.zeros((n, n), np.float32)
        for ang in angles:
            d = np.abs(np.sin(th - ang)) * r
            along = np.cos(th - ang) * r
            seg = (along > t * 0.5) & (along < 0.25 + t * 0.7)
            a = np.maximum(a, np.clip(1 - d / 0.03, 0, 1) * seg)
        a *= (1 - t)
        a = np.maximum(a, np.clip(1 - r / 0.25, 0, 1) * (1 - t) ** 3)
        out = ramp(a, [(0, (1, 0.6, 0.1, 0)), (0.5, (1, 0.8, 0.3, 1)), (1, (1, 1, 0.9, 1))])
        out[..., 3] = a
        frames.append(out)
    save("spark", frames, 24, blend="add")


def blood(n=96, F=6):
    x, y, r, th = grid(n)
    drops = [(rng.uniform(0, 2 * math.pi), rng.uniform(0.3, 1.0), rng.uniform(0.04, 0.09)) for _ in range(18)]
    frames = []
    for i in range(F):
        t = i / (F - 1)
        a = np.zeros((n, n), np.float32)
        for ang, dist, sz in drops:
            cx, cy = math.cos(ang) * dist * (0.2 + 0.8 * t), math.sin(ang) * dist * (0.2 + 0.8 * t) + t * t * 0.2
            a = np.maximum(a, np.clip(1 - np.hypot(x - cx, y - cy) / (sz * (1 - t * 0.4)), 0, 1))
        a = np.maximum(a, np.clip(1 - r / (0.2 + 0.1 * t), 0, 1) * (1 - t))
        a = np.clip(a * 2, 0, 1) * (1 - t * 0.5)
        frames.append(rgba((0.45, 0.02, 0.03), a))
    save("blood", frames, 18, blend="normal")


def hexpattern(x, y, scale):
    q = (x * 2 / math.sqrt(3)) * scale
    rr = (y - x / math.sqrt(3)) * scale
    def frac(v):
        return v - np.floor(v)
    a, b = frac(q), frac(rr)
    c = frac(-q - rr)
    edge = np.minimum(np.minimum(np.abs(a - 0.5), np.abs(b - 0.5)), np.abs(c - 0.5))
    return np.clip(1 - edge / 0.06, 0, 1)


def shield(n=192, F=12):
    x, y, r, th = grid(n)
    frames = []
    for i in range(F):
        t = i / F
        dome = np.clip(1 - r, 0, 1)
        rim = np.clip(1 - np.abs(r - 0.92) / 0.08, 0, 1)
        z = np.sqrt(np.clip(1 - r * r, 0, 1))
        hexes = hexpattern(x / (z + 0.4), y / (z + 0.4) + t * 0.25, 4.0)
        sweep = np.clip(1 - np.abs(((y + 1) / 2 - (t * 1.4 - 0.2))) / 0.08, 0, 1)
        a = (dome > 0) * (0.1 + rim * 0.9 + hexes * 0.35 * dome + sweep * 0.4 * (r < 1))
        out = rgba((0.4, 0.8, 1.0), np.clip(a, 0, 1) * (r < 1.0))
        out[..., :3] += rim[..., None] * 0.5
        frames.append(out)
    save("shield", frames, 14, loop=True, blend="add")


def heal(n=192, F=12):
    x, y, r, th = grid(n)
    pluses = [(rng.uniform(-0.6, 0.6), rng.uniform(0, 1), rng.uniform(0.05, 0.09)) for _ in range(9)]
    frames = []
    for i in range(F):
        t = i / F
        col_a = np.clip(1 - np.abs(x) / 0.55, 0, 1) ** 1.5 * np.clip((y + 1) / 2, 0, 1) * 0.45
        a = col_a.copy()
        for px, py, sz in pluses:
            yy = 1 - ((py + t) % 1) * 2
            fade = math.sin(((py + t) % 1) * math.pi)
            dx, dy = np.abs(x - px), np.abs(y - yy)
            plus = ((dx < sz) & (dy < sz * 0.3)) | ((dy < sz) & (dx < sz * 0.3))
            a = np.maximum(a, plus * fade)
        ring = np.clip(1 - np.abs(np.hypot(x, (y - 0.85) * 3) - 0.5) / 0.05, 0, 1) * 0.8
        a = np.maximum(a, ring)
        frames.append(rgba((0.45, 1.0, 0.55), np.clip(a, 0, 1)))
    save("heal", frames, 12, loop=True, blend="add")


def rage(n=192, F=12):
    x, y, r, th = grid(n)
    frames = []
    for i in range(F):
        t = i / F
        nz = fbm(n, 500 + i, 4, 3)
        flame = np.clip(1 - np.abs(r - 0.6) / (0.25 + 0.2 * nz), 0, 1)
        up = np.clip((1 - (y + 1) / 2) * 1.3, 0, 1)
        heat = flame * (0.6 + 0.6 * nz) * (0.5 + 0.5 * up)
        out = ramp(heat, [(0, (0.4, 0.0, 0.0, 0)), (0.4, (0.9, 0.15, 0.05, 0.7)), (0.7, (1.0, 0.5, 0.1, 1)), (1, (1, 0.9, 0.5, 1))])
        frames.append(out)
    save("rage", frames, 14, loop=True, blend="add")


def teleport(n=160, F=10):
    x, y, r, th = grid(n)
    frames = []
    for i in range(F):
        t = i / (F - 1)
        spiral = np.clip(np.cos(th * 3 + r * 10 - t * 12) * 0.5 + 0.5, 0, 1) ** 2
        rad = 0.2 + 0.8 * math.sin(t * math.pi)
        a = spiral * np.clip(1 - r / rad, 0, 1) + np.clip(1 - r / (0.3 * (1 - t) + 0.05), 0, 1)
        out = ramp(np.clip(a, 0, 1), [(0, (0.3, 0.1, 0.6, 0)), (0.5, (0.7, 0.4, 1.0, 1)), (1, (1, 0.9, 1, 1))])
        out[..., 3] = np.clip(a, 0, 1) * (1 - t * 0.3)
        frames.append(out)
    save("teleport", frames, 16, blend="add")


def toxic(n=192, F=12):
    x, y, r, th = grid(n)
    frames = []
    for i in range(F):
        t = i / F
        nz = fbm(n, 700 + (i % F), 5, 3) * 0.5 + fbm(n, 900, 5, 3) * 0.5
        a = np.clip(1 - (r + (nz - 0.5) * 0.6) / 0.85, 0, 1) ** 1.2 * (0.55 + 0.45 * nz)
        out = np.zeros((n, n, 4), np.float32)
        out[..., :3] = np.array((0.55, 0.95, 0.2)) * (0.6 + 0.6 * nz[..., None])
        out[..., 3] = np.clip(a, 0, 1) * 0.8
        frames.append(out)
    save("toxic", frames, 8, loop=True, blend="normal")


def levelup(n=256, F=14):
    x, y, r, th = grid(n)
    frames = []
    for i in range(F):
        t = i / (F - 1)
        beam = np.clip(1 - np.abs(x) / (0.25 * (1 - t) + 0.05), 0, 1) ** 1.5 * (1 - t)
        rr = np.hypot(x, (y - 0.7) * 3.5)
        ring = np.clip(1 - np.abs(rr - t * 1.1) / 0.06, 0, 1) * (1 - t)
        stars = (fbm(n, 1100 + i, 2, 16) > 0.82) * np.clip(1 - np.abs(x) / 0.6, 0, 1) * (1 - t)
        a = np.clip(beam + ring + stars, 0, 1)
        out = ramp(a, [(0, (1, 0.7, 0.2, 0)), (0.5, (1, 0.85, 0.4, 1)), (1, (1, 1, 0.9, 1))])
        out[..., 3] = a
        frames.append(out)
    save("levelup", frames, 14, blend="add")


def shockwave(n=256, F=10):
    x, y, r, th = grid(n)
    frames = []
    for i in range(F):
        t = i / (F - 1)
        ring = np.clip(1 - np.abs(r - (0.15 + 0.8 * t)) / (0.08 * (1 - t) + 0.02), 0, 1) * (1 - t)
        out = rgba((1.0, 0.85, 0.65), ring)
        frames.append(out)
    save("shockwave", frames, 18, blend="add")


def trail(n=64):
    """Mermi izi (yatay şerit, uzunluğuna gerilecek)."""
    x, y, r, th = grid(n)
    a = np.clip(1 - np.abs(y) / 0.5, 0, 1) ** 2 * np.clip((x + 1) / 2, 0, 1)
    save("tracer", [rgba((1.0, 0.9, 0.55), a)], 1, blend="add")


def glow(n=128):
    x, y, r, th = grid(n)
    save("glow", [rgba((1, 1, 1), np.clip(1 - r, 0, 1) ** 2)], 1, blend="add")


def skid(n=64):
    x, y, r, th = grid(n)
    nz = fbm(n, 1300, 3, 4)
    a = np.clip(1 - np.abs(y) / 0.7, 0, 1) * (0.5 + 0.5 * nz) * 0.6
    save("skid", [rgba((0.06, 0.06, 0.06), a)], 1, blend="normal")


if __name__ == "__main__":
    print("efektler...")
    explosion()
    muzzle()
    smoke()
    smoke(96, 10, (0.55, 0.45, 0.33), "dust")
    smoke(96, 10, (0.32, 0.32, 0.34), "exhaust")
    spark()
    blood()
    shield()
    heal()
    rage()
    teleport()
    toxic()
    levelup()
    shockwave()
    trail()
    glow()
    skid()
