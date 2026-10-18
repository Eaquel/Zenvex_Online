"""Gerçekçi, dikişsiz zemin dokuları (512x512, 1 m = 62.5 px).

Yöntem: spektral sentez (beyaz gürültü → 1/f^β filtre → ters FFT) doğası gereği dikişsizdir.
Üstüne sarmalayan (wrap) çizimle ot yaprakları, çakıl taşları, yapraklar, çatlaklar eklenir;
yükseklik haritasından normal hesaplanıp güneş (sol-üst) ile kabartma gölgesi pişirilir.
Sıra Rust world::ground ile aynı: grass, dark_grass, dry_grass, dirt, gravel, asphalt, sidewalk, concrete, mud

Kullanım: /opt/bvenv/bin/python tools/assetgen/ground_tex.py <çıktı_dizini>
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 512
NAMES = ["grass", "dark_grass", "dry_grass", "dirt", "gravel", "asphalt", "sidewalk", "concrete", "mud"]


def spectral(seed, beta=2.0, fmin=1.0, fmax=None):
    r = np.random.default_rng(seed)
    w = r.standard_normal((S, S))
    F = np.fft.fft2(w)
    fy = np.fft.fftfreq(S)[:, None] * S
    fx = np.fft.fftfreq(S)[None, :] * S
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    amp = 1.0 / f ** (beta / 2)
    amp[f < fmin] = 0
    if fmax:
        amp[f > fmax] = 0
    n = np.real(np.fft.ifft2(F * amp))
    n -= n.mean()
    n /= (n.std() + 1e-9)
    return n


def norm01(n):
    lo, hi = np.percentile(n, 1), np.percentile(n, 99)
    return np.clip((n - lo) / (hi - lo + 1e-9), 0, 1)


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float) / 255


def grad(t, stops):
    """t: [0,1] dizi; stops: [(konum, '#renk'), ...]"""
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,))
    ps = [p for p, _ in stops]
    cs = [hexc(c) for _, c in stops]
    for i in range(len(stops) - 1):
        a, b = ps[i], ps[i + 1]
        m = (t >= a) & (t <= b)
        k = ((t - a) / max(b - a, 1e-6))[m][:, None]
        out[m] = cs[i] * (1 - k) + cs[i + 1] * k
    return out


def shade(col, height, strength=1.0, ao=0.0):
    """Yükseklikten normal → lambert (güneş sol-üst-ön) + boşluk karartması."""
    gx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) * 0.5
    gy = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) * 0.5
    nx, ny, nz = -gx * strength, -gy * strength, np.ones_like(height)
    ln = np.sqrt(nx * nx + ny * ny + nz * nz)
    L = np.array([-0.55, -0.55, 0.63])
    L = L / np.linalg.norm(L)
    lam = (nx * L[0] + ny * L[1] + nz * L[2]) / ln / L[2]
    lam = np.clip(lam, 0.6, 1.3)
    out = col * lam[..., None]
    if ao:
        blur = np.array(Image.fromarray((norm01(height) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6))) / 255
        cav = np.clip((blur - norm01(height)) * 3, 0, 1)
        out *= (1 - ao * cav)[..., None]
    return out


class Wrap:
    """3x3 döşenmiş tuval üzerinde çizip ortayı alarak dikişsiz çizim."""

    def __init__(self, mode="RGBA"):
        self.im = Image.new(mode, (S * 3, S * 3), (0, 0, 0, 0) if mode == "RGBA" else 0)
        self.d = ImageDraw.Draw(self.im)

    def each(self, x, y):
        for ox in (0, S, 2 * S):
            for oy in (0, S, 2 * S):
                yield x + ox, y + oy

    def result(self):
        big = self.im
        # kenarlardan taşanları ortadaki kareye kat
        acc = None
        for ox in (0, S, 2 * S):
            for oy in (0, S, 2 * S):
                part = big.crop((ox, oy, ox + S, oy + S))
                acc = part if acc is None else Image.alpha_composite(acc, part) if big.mode == "RGBA" else Image.fromarray(
                    np.maximum(np.array(acc), np.array(part)))
        return acc


def blades(seed, n, colors, length=(6, 14), width=1, lean=0.35, heightmap=None):
    """Ot yaprakları: ince eğri çizgiler; aynı anda yükseklik haritasına iz bırakır."""
    r = np.random.default_rng(seed)
    w = Wrap()
    hw = Wrap("L") if heightmap is not None else None
    for _ in range(n):
        x, y = r.uniform(0, S, 2) + S
        L = r.uniform(*length)
        a = -math.pi / 2 + r.normal(0, lean)
        bend = r.normal(0, 0.25)
        c = colors[r.integers(len(colors))]
        jitter = r.uniform(0.82, 1.15)
        col = tuple(int(np.clip(v * jitter, 0, 255)) for v in c) + (int(r.uniform(150, 255)),)
        pts = []
        for k in range(5):
            t = k / 4
            aa = a + bend * t
            pts.append((x + math.cos(aa) * L * t, y + math.sin(aa) * L * t * 0.6))
        for dx, dy in w.each(0, 0):
            w.d.line([(px + dx - S, py + dy - S) for px, py in pts], fill=col, width=width)
            if hw:
                hw.d.line([(px + dx - S, py + dy - S) for px, py in pts], fill=int(120 + 120 * r.random()), width=width + 1)
    return w.result(), (hw.result() if hw else None)


def pebbles(seed, n, size, colors, flat=0.7):
    """Gölgeli küçük taşlar: renk + yükseklik."""
    r = np.random.default_rng(seed)
    w = Wrap()
    h = Wrap("L")
    for _ in range(n):
        x, y = r.uniform(0, S, 2) + S
        rx = r.uniform(*size)
        ry = rx * r.uniform(flat, 1.0)
        c = colors[r.integers(len(colors))] * r.uniform(0.8, 1.15)
        for dx, dy in w.each(0, 0):
            bx, by = x + dx - S, y + dy - S
            # gölge
            w.d.ellipse((bx - rx + 1.2, by - ry + 1.6, bx + rx + 1.2, by + ry + 1.6), fill=(20, 16, 12, 90))
            w.d.ellipse((bx - rx, by - ry, bx + rx, by + ry), fill=tuple(int(np.clip(v * 255, 0, 255)) for v in c) + (255,))
            # tepe ışığı
            w.d.ellipse((bx - rx * 0.6, by - ry * 0.75, bx + rx * 0.2, by - ry * 0.05),
                        fill=tuple(int(np.clip(v * 255 * 1.25, 0, 255)) for v in c) + (140,))
            h.d.ellipse((bx - rx, by - ry, bx + rx, by + ry), fill=200)
    return w.result(), h.result()


def leaves(seed, n, colors):
    r = np.random.default_rng(seed)
    w = Wrap()
    for _ in range(n):
        x, y = r.uniform(0, S, 2) + S
        L = r.uniform(3, 6)
        a = r.uniform(0, math.pi)
        c = colors[r.integers(len(colors))]
        for dx, dy in w.each(0, 0):
            bx, by = x + dx - S, y + dy - S
            p = [(bx + math.cos(a) * L, by + math.sin(a) * L * 0.6), (bx + math.cos(a + 1.6) * L * 0.4, by + math.sin(a + 1.6) * L * 0.3),
                 (bx - math.cos(a) * L, by - math.sin(a) * L * 0.6), (bx + math.cos(a - 1.6) * L * 0.4, by + math.sin(a - 1.6) * L * 0.3)]
            w.d.polygon([(px + 1, py + 1.3) for px, py in p], fill=(15, 12, 8, 70))
            w.d.polygon(p, fill=tuple(int(v * r.uniform(0.85, 1.1)) for v in c) + (235,))
    return w.result()


def cracks(seed, n, length=(40, 140), width=1):
    r = np.random.default_rng(seed)
    w = Wrap("L")
    for _ in range(n):
        x, y = r.uniform(0, S, 2) + S
        a = r.uniform(0, math.tau)
        L = r.uniform(*length)
        pts = [(x, y)]
        for _ in range(int(L / 6)):
            a += r.normal(0, 0.45)
            x += math.cos(a) * 6
            y += math.sin(a) * 6
            pts.append((x, y))
            if r.random() < 0.08:
                b = a + r.choice([-1, 1]) * r.uniform(0.6, 1.2)
                bx, by = x, y
                sub = [(bx, by)]
                for _ in range(r.integers(2, 6)):
                    b += r.normal(0, 0.4)
                    bx += math.cos(b) * 5
                    by += math.sin(b) * 5
                    sub.append((bx, by))
                for dx, dy in w.each(0, 0):
                    w.d.line([(px + dx - S, py + dy - S) for px, py in sub], fill=200, width=width)
        for dx, dy in w.each(0, 0):
            w.d.line([(px + dx - S, py + dy - S) for px, py in pts], fill=255, width=width)
    return np.array(w.result(), float) / 255


def over(base, layer):
    """base: float RGB (0-1); layer: PIL RGBA"""
    a = np.array(layer, float) / 255
    al = a[..., 3:4]
    return base * (1 - al) + a[..., :3] * al


def to_img(col):
    return Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8))


# =========================================================================== malzemeler
def grass_tex(seed, stops, blade_cols, dry=0.0, flowers=False):
    big = norm01(spectral(seed, 2.4, 1, 24))
    mid = norm01(spectral(seed + 1, 1.6, 8, 120))
    fine = norm01(spectral(seed + 2, 0.6, 60))
    t = big * 0.55 + mid * 0.3 + fine * 0.15
    col = grad(t, stops)
    # toprak lekeleri (seyrek)
    soil = norm01(spectral(seed + 3, 2.8, 1, 16))
    sm = np.clip((soil - 0.78) * 6, 0, 1)[..., None] * 0.55
    col = col * (1 - sm) + grad(fine, [(0, "#4a3a28"), (1, "#7a6044")]) * sm
    h = t * 2.0
    lay, hl = blades(seed + 10, 9000, blade_cols, (5, 13), 1, 0.4, True)
    col = over(col, lay)
    h = h + np.array(hl, float) / 255 * 1.4
    lay2, hl2 = blades(seed + 11, 3500, [tuple(int(v * 1.25) for v in c) for c in blade_cols], (4, 9), 1, 0.5, True)
    col = over(col, lay2)
    h = h + np.array(hl2, float) / 255 * 1.0
    if dry:
        lay3, _ = blades(seed + 12, int(1500 * dry), [(196, 176, 110), (170, 150, 90), (210, 196, 140)], (5, 11), 1, 0.6)
        col = over(col, lay3)
    if flowers:
        r = np.random.default_rng(seed + 20)
        w = Wrap()
        for _ in range(60):
            x, y = r.uniform(0, S, 2) + S
            c = [(250, 248, 236), (246, 214, 70), (220, 90, 120), (170, 140, 230)][r.integers(4)]
            for dx, dy in w.each(0, 0):
                bx, by = x + dx - S, y + dy - S
                w.d.ellipse((bx - 1.6, by - 1.2, bx + 1.6, by + 1.2), fill=c + (255,))
                w.d.point((bx, by), fill=(240, 190, 40, 255))
        col = over(col, w.result())
    col = shade(col, h, 1.2, ao=0.35)
    return col


def dirt_tex(seed, stops, peb_cols, wet=False):
    big = norm01(spectral(seed, 2.6, 1, 20))
    mid = norm01(spectral(seed + 1, 1.8, 6, 100))
    fine = norm01(spectral(seed + 2, 0.8, 50))
    h = big * 1.0 + mid * 1.6 + fine * 0.8
    col = grad(big * 0.4 + mid * 0.4 + fine * 0.2, stops)
    if wet:
        puddle = np.clip((norm01(spectral(seed + 5, 2.6, 1, 14)) - 0.7) * 5, 0, 1)[..., None]
        col = col * (1 - puddle * 0.55) + np.array([0.16, 0.15, 0.12]) * puddle * 0.55
        h = h * (1 - puddle[..., 0] * 0.8)
    lay, hp = pebbles(seed + 7, 700, (0.8, 2.4), peb_cols)
    col = over(col, lay)
    h = h + np.array(hp, float) / 255 * 1.2
    lay2, _ = blades(seed + 9, 500, [(98, 120, 60), (120, 136, 70)], (4, 8), 1, 0.6)
    col = over(col, lay2)
    col = shade(col, h, 1.6, ao=0.4)
    return col


def gravel_tex(seed):
    base = norm01(spectral(seed, 1.5, 4))
    col = grad(base, [(0, "#5e5a54"), (0.5, "#7a756c"), (1, "#958e82")])
    h = base * 0.5
    cols = [hexc(c) for c in ("#8a857c", "#a49c90", "#6e6a64", "#b4aa98", "#7c7468", "#5a5650")]
    lay, hp = pebbles(seed + 1, 6000, (1.4, 3.4), cols, 0.55)
    col = over(col, lay)
    h = h + np.array(hp, float) / 255 * 2.0
    lay, hp = pebbles(seed + 2, 4000, (1.0, 2.4), cols, 0.6)
    col = over(col, lay)
    h = h + np.array(hp, float) / 255 * 1.6
    col = shade(col, h, 1.4, ao=0.5)
    return col


def asphalt_tex(seed):
    big = norm01(spectral(seed, 2.4, 1, 16))
    fine = norm01(spectral(seed + 1, 0.2, 100))
    col = grad(big * 0.35 + fine * 0.65, [(0, "#2c2d30"), (0.5, "#3a3b3e"), (1, "#4c4c4e")])
    # agrega parlaması
    r = np.random.default_rng(seed + 2)
    spark = r.random((S, S)) > 0.985
    col[spark] = col[spark] * 1.6
    cr = cracks(seed + 3, 10, (60, 200))
    cr = np.array(Image.fromarray((cr * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6)), float) / 255
    col = col * (1 - cr[..., None] * 0.6)
    # yama (tamir) lekeleri
    patch = np.clip((norm01(spectral(seed + 4, 3.0, 1, 10)) - 0.8) * 8, 0, 1)[..., None]
    col = col * (1 - patch * 0.25)
    # yağ lekeleri
    oil = np.clip((norm01(spectral(seed + 5, 2.6, 2, 30)) - 0.86) * 6, 0, 1)[..., None]
    col = col * (1 - oil * 0.35)
    col = shade(col, fine * 0.6 - cr * 1.5, 1.0, ao=0.2)
    return col


def sidewalk_tex(seed):
    """Karo kaldırım taşları (60 cm): derz, karo başına ton farkı, kırık köşeler, aralarda yosun."""
    tile = S // 8
    yy, xx = np.mgrid[0:S, 0:S]
    tx, ty = xx // tile, (yy + (xx // tile % 2) * 0) // tile
    r = np.random.default_rng(seed)
    tone = r.uniform(0.9, 1.08, (9, 9))[ty % 8, tx % 8]
    fine = norm01(spectral(seed + 1, 0.8, 40))
    big = norm01(spectral(seed + 2, 2.4, 1, 12))
    col = grad(fine * 0.6 + big * 0.4, [(0, "#8e8a82"), (0.5, "#a8a49a"), (1, "#bdb8ae")]) * tone[..., None]
    lx, ly = xx % tile, yy % tile
    edge = np.minimum(np.minimum(lx, tile - 1 - lx), np.minimum(ly, tile - 1 - ly)).astype(float)
    joint = np.clip(1 - edge / 2.2, 0, 1)
    col = col * (1 - joint[..., None] * 0.55)
    moss = np.clip((norm01(spectral(seed + 3, 2.2, 2, 40)) - 0.6) * 3, 0, 1) * joint
    col = col * (1 - moss[..., None]) + np.array([0.3, 0.38, 0.18]) * moss[..., None]
    bevel = np.clip(edge / 4, 0, 1)
    cr = cracks(seed + 4, 5, (20, 60))
    col = col * (1 - cr[..., None] * 0.4)
    col = shade(col, bevel * 1.5 + fine * 0.3 - cr, 1.2, ao=0.3)
    return col


def concrete_tex(seed):
    """Beton plakalar (2 m), dökme izleri, lekeler, çatlaklar."""
    big = norm01(spectral(seed, 2.6, 1, 14))
    fine = norm01(spectral(seed + 1, 0.9, 30))
    col = grad(big * 0.5 + fine * 0.5, [(0, "#7c7a74"), (0.5, "#94918a"), (1, "#a8a59c")])
    yy, xx = np.mgrid[0:S, 0:S]
    p = S // 4
    edge = np.minimum(np.minimum(xx % p, p - 1 - xx % p), np.minimum(yy % p, p - 1 - yy % p)).astype(float)
    joint = np.clip(1 - edge / 1.6, 0, 1)
    col = col * (1 - joint[..., None] * 0.5)
    stain = np.clip((norm01(spectral(seed + 2, 2.4, 1, 20)) - 0.7) * 3, 0, 1)
    col = col * (1 - stain[..., None] * 0.22)
    pits = np.random.default_rng(seed + 3).random((S, S)) > 0.992
    col[pits] *= 0.7
    cr = cracks(seed + 4, 7, (40, 160))
    col = col * (1 - cr[..., None] * 0.5)
    col = shade(col, fine * 0.4 - cr - joint * 0.8, 1.0, ao=0.25)
    return col


def make_all(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    G = lambda *c: [tuple(int(v * 255) for v in hexc(x)) for x in c]  # noqa: E731
    texs = {
        "grass": grass_tex(1, [(0, "#3e6a26"), (0.45, "#557f30"), (0.75, "#6a9038"), (1, "#7e9c42")],
                           G("#5a8a30", "#6c9a38", "#4c7a2a", "#7aa040", "#86a848"), dry=0.3, flowers=True),
        "dark_grass": grass_tex(2, [(0, "#24401a"), (0.5, "#33562a"), (1, "#456a30")],
                                G("#2e5222", "#3a6228", "#466e2e", "#28461e"), dry=0.0),
        "dry_grass": grass_tex(3, [(0, "#6a6436"), (0.5, "#8a8248"), (1, "#a49a5a")],
                               G("#a09454", "#b4a862", "#8c8046", "#c2b470", "#7a8a44"), dry=1.5),
        "dirt": dirt_tex(4, [(0, "#4e3c2a"), (0.5, "#6a5238"), (1, "#86694a")],
                         [hexc(c) for c in ("#8a7a66", "#6e6050", "#a09078", "#5a4e42")]),
        "gravel": gravel_tex(5),
        "asphalt": asphalt_tex(6),
        "sidewalk": sidewalk_tex(7),
        "concrete": concrete_tex(8),
        "mud": dirt_tex(9, [(0, "#2e2a1e"), (0.5, "#3e3826"), (1, "#524a32")],
                        [hexc(c) for c in ("#4a443a", "#5a5244", "#3a362e")], wet=True),
    }
    for name in NAMES:
        to_img(texs[name]).save(os.path.join(out_dir, name + ".png"))
        print("doku", name)


if __name__ == "__main__":
    make_all(sys.argv[1] if len(sys.argv) > 1 else "/home/user/zx_render/ground")
