"""V2 render motoru: SDF ışın yürütme + boyacı gölgelendirme.

- 5 tonlu, renk kaydırmalı rampa (gölgeler soğuk/mor, ışıklar sıcak)
- ortam gölgesi (AO), arkadan kenar ışığı (rim), metal parlaması, ışıyan (emissive) malzemeler
- malzeme dokuları: kumaş, kot, deri, kabuk, yaprak, taş, metal
- sıralı titreşim (dither) ile yumuşak ton geçişleri
- seçici dış çizgi (ışık tarafında açık), derinlik çizgileri
- hızlı: önce 1/4 çözünürlükte doluluk maskesi, sonra sadece gerekli pikseller
"""
import colorsys
import math
import numpy as np
from sdf import (hexc, length, sd_sphere, sd_round_cone, sd_round_box, sd_ellipsoid, sd_cut,
                 sd_displace, rot_x, rot_y, rot_z, v3)  # noqa: F401 (yeniden dışa aktarım)

PITCH = math.radians(35.0)
MPP = 0.024  # metre / piksel (V2: 1,5x detay)
LIGHT = np.array([-0.55, 0.78, 0.40]); LIGHT /= np.linalg.norm(LIGHT)
FWD = np.array([0.0, -math.sin(PITCH), -math.cos(PITCH)])
RIGHT = np.array([1.0, 0.0, 0.0])
UP = np.array([0.0, math.cos(PITCH), -math.sin(PITCH)])
VIEW = -FWD

BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16.0


def ramp(base_hex, n=5, hue_shift=0.045, spread=0.42):
    """Taban renkten n tonluk rampa: [en açık ... en koyu]. Gölgeler maviye/mora, ışıklar sarıya kayar."""
    r, g, b = [c / 255 for c in hexc(base_hex)]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    out = []
    mid = (n - 1) / 2
    for i in range(n):
        k = (mid - i) / mid  # +1 en açık, -1 en koyu
        # sıcak (0.12 ≈ turuncu-sarı) veya soğuk (0.7 ≈ mavi-mor) yöne kaydır
        target = 0.13 if k > 0 else 0.72
        dh = ((target - h + 0.5) % 1.0) - 0.5
        hh = (h + dh * hue_shift * abs(k) * 2.2) % 1.0
        ss = min(1.0, max(0.0, s * (1 - 0.25 * k) + (0.08 if k < 0 else -0.02) * abs(k)))
        vv = min(1.0, max(0.0, v * (1 + spread * k * (0.9 if k > 0 else 1.15))))
        rr, gg, bb = colorsys.hsv_to_rgb(hh, ss, vv)
        out.append((int(rr * 255), int(gg * 255), int(bb * 255)))
    return out


class Mat:
    """texture: None|'cloth'|'denim'|'leather'|'bark'|'leaf'|'stone'|'metal'|'skin'|'hair'|'glass'
    spec: parlama gücü (0..1), emissive: True ise ışıktan bağımsız parlak."""

    def __init__(self, base, texture=None, spec=0.0, emissive=False, outline=None, hue_shift=0.045, spread=0.42):
        self.tones = ramp(base, 5, hue_shift, spread)
        self.texture = texture
        self.spec = spec
        self.emissive = emissive
        d = self.tones[4]
        self.outline = hexc(outline) if outline else (int(d[0] * 0.55), int(d[1] * 0.5), int(d[2] * 0.6))
        self.outline_lit = self.tones[4]


def _noise3(p, f, seed=0.0):
    x, y, z = p[:, 0] * f, p[:, 1] * f, p[:, 2] * f
    return (np.sin(x * 1.7 + seed) * np.sin(y * 1.9 + 1.3 * seed) * np.sin(z * 2.1 + 0.4)
            + 0.5 * np.sin(x * 3.3 + z * 2.1 + seed) * np.sin(y * 3.7 - x * 1.3))


def _texture(kind, p, n):
    """Doku katkısı: aydınlık değerine eklenecek küçük sapma (-0.2..0.2)."""
    if kind is None:
        return 0.0
    if kind == "cloth":
        return 0.022 * np.sign(np.sin(p[:, 1] * 260) * np.sin(p[:, 0] * 260 + p[:, 2] * 260)) + 0.045 * _noise3(p, 18)
    if kind == "denim":
        return 0.035 * np.sin((p[:, 1] + p[:, 0] + p[:, 2]) * 300) + 0.04 * _noise3(p, 20)
    if kind == "leather":
        return 0.06 * _noise3(p, 60) - 0.03
    if kind == "bark":
        return 0.16 * np.sign(np.sin(p[:, 0] * 70 + np.sin(p[:, 1] * 9) * 2.5 + p[:, 2] * 70)) * (0.5 + 0.5 * _noise3(p, 8))
    if kind == "leaf":
        return 0.22 * _noise3(p, 22) + 0.1 * np.sign(_noise3(p, 55, 2.0))
    if kind == "stone":
        return 0.12 * _noise3(p, 12) + 0.06 * _noise3(p, 45, 1.0)
    if kind == "metal":
        return 0.04 * _noise3(p, 80)
    if kind == "skin":
        return 0.02 * _noise3(p, 50)
    if kind == "hair":
        return 0.12 * np.sin(p[:, 0] * 140 + p[:, 2] * 90 + np.sin(p[:, 1] * 40) * 2.0)
    if kind == "fur":
        return 0.14 * np.sin(p[:, 0] * 120 + p[:, 1] * 160 + _noise3(p, 20) * 3)
    if kind == "rust":
        return 0.14 * _noise3(p, 25) - 0.04
    return 0.0


def render(scene, width, height, anchor, materials, decals=(), mpp=MPP, steps=80, max_t=14.0,
           outline=True, inner_lines=True, ao_strength=0.55, rim=0.35, light=None, tone_bias=None,
           return_depth=False):
    """scene: [(mat_adı, sdf)] · anchor: dünya orijininin pikseli · decals: [(nokta, hex, normal|None)]."""
    Lv = LIGHT if light is None else light
    names = [m for m, _ in scene]
    sdfs = [s for _, s in scene]

    def eval_scene(p):
        ds = np.stack([s(p) for s in sdfs], axis=0)
        return ds.min(axis=0), ds.argmin(axis=0)

    def ray_origins(xs, ys):
        sx = (xs + 0.5 - anchor[0]) * mpp
        sy = (anchor[1] - (ys + 0.5)) * mpp
        return sx[:, None] * RIGHT + sy[:, None] * UP - FWD * (max_t * 0.5)

    def march(org, n_steps):
        n = org.shape[0]
        t = np.zeros(n)
        hit = np.zeros(n, bool)
        alive = np.ones(n, bool)
        idx = np.arange(n)
        for _ in range(n_steps):
            act = idx[alive]
            if act.size == 0:
                break
            d, _ = eval_scene(org[act] + t[act, None] * FWD)
            newly = d < 0.0015
            hit[act[newly]] = True
            t[act] += np.maximum(d, 0.0) * 0.92
            alive[act[newly | (t[act] > max_t)]] = False
        return t, hit

    # 1) kaba maske (4x4 blok merkezleri, biraz büyütülmüş eşik)
    bs = 4
    gy, gx = np.mgrid[0:height:bs, 0:width:bs]
    gxs = gx.ravel().astype(float) + bs / 2 - 0.5
    gys = gy.ravel().astype(float) + bs / 2 - 0.5
    org = ray_origins(gxs, gys)
    n = org.shape[0]
    t = np.zeros(n); near = np.zeros(n, bool); alive = np.ones(n, bool); idx = np.arange(n)
    thr = mpp * bs * 1.6
    for _ in range(steps):
        act = idx[alive]
        if act.size == 0:
            break
        d, _ = eval_scene(org[act] + t[act, None] * FWD)
        newly = d < thr
        near[act[newly]] = True
        t[act] += np.maximum(d, 0.0) * 0.92
        alive[act[newly | (t[act] > max_t)]] = False
    coarse = near.reshape(gy.shape)
    # komşulara yay
    c2 = coarse.copy()
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        c2 |= np.roll(np.roll(coarse, dy, 0), dx, 1)
    mask = np.kron(c2, np.ones((bs, bs), bool))[:height, :width]

    ys, xs = np.nonzero(mask)
    out = np.zeros((height, width, 4), np.uint8)
    depth = np.full((height, width), np.inf)
    matid = np.full((height, width), -1)
    lit_side = np.zeros((height, width), bool)
    if ys.size:
        org = ray_origins(xs.astype(float), ys.astype(float))
        t, hit = march(org, steps)
        hy, hx = ys[hit], xs[hit]
        p = org[hit] + t[hit, None] * FWD
        _, mid = eval_scene(p)
        e = 0.003
        nrm = np.zeros_like(p)
        for k in range(3):
            dv = np.zeros(3); dv[k] = e
            nrm[:, k] = eval_scene(p + dv)[0] - eval_scene(p - dv)[0]
        nrm /= np.maximum(length(nrm), 1e-9)[:, None]
        # aydınlatma
        lam = nrm @ Lv
        wrap = np.clip((lam + 0.25) / 1.25, 0, 1)
        k1, k2 = 0.035, 0.09
        ao = np.clip(0.5 * (eval_scene(p + nrm * k1)[0] / k1) + 0.5 * (eval_scene(p + nrm * k2)[0] / k2), 0, 1)
        ao = 1 - ao_strength * (1 - ao)
        rimv = np.clip(1 - (nrm @ VIEW), 0, 1) ** 3 * np.clip(-(nrm @ Lv) + 0.6, 0, 1) * rim
        val = 0.12 + 0.88 * wrap * ao + rimv
        # doku + parlama
        spec_add = np.zeros_like(val)
        emis = np.zeros(val.shape, bool)
        for i, mname in enumerate(names):
            sel = mid == i
            if not sel.any():
                continue
            m = materials[mname]
            val[sel] += _texture(m.texture, p[sel], nrm[sel])
            if m.spec > 0:
                rv = 2 * (nrm[sel] @ Lv)[:, None] * nrm[sel] - Lv
                sp = np.clip(rv @ VIEW, 0, 1) ** 24 * m.spec
                spec_add[sel] = sp
            if m.emissive:
                emis[sel] = True
        if tone_bias is not None:
            val += tone_bias(p, nrm, mid)
        # titreşimli 5 ton
        dith = BAYER4[hy % 4, hx % 4] - 0.5
        q = np.clip(val + dith * 0.11, 0, 1.0)
        tone = np.clip((4 - np.floor(q * 4.999)).astype(int), 0, 4)
        tone = np.where(spec_add > 0.45, 0, tone)
        tone = np.where(emis, np.where(val > 0.55, 0, 1), tone)
        cols = np.zeros((p.shape[0], 3), np.uint8)
        for i, mname in enumerate(names):
            sel = mid == i
            if sel.any():
                tones = np.array(materials[mname].tones, np.uint8)
                cols[sel] = tones[tone[sel]]
        sw = spec_add > 0.8
        cols[sw] = (np.clip(cols[sw].astype(int) + 60, 0, 255)).astype(np.uint8)
        out[hy, hx, :3] = cols
        out[hy, hx, 3] = 255
        depth[hy, hx] = t[hit]
        matid[hy, hx] = mid
        lit_side[hy, hx] = (nrm @ Lv) > 0.25

    # tek piksel işaretler
    for pt, col, nvec in decals:
        pt = np.asarray(pt, float)
        if nvec is not None and np.dot(nvec, VIEW) < 0.12:
            continue
        px = pt @ RIGHT / mpp + anchor[0]
        py = anchor[1] - pt @ UP / mpp
        dz = pt @ FWD + max_t * 0.5
        ix, iy = int(math.floor(px)), int(math.floor(py))
        if 0 <= ix < width and 0 <= iy < height and depth[iy, ix] + 0.04 > dz:
            out[iy, ix, :3] = hexc(col)
            out[iy, ix, 3] = 255

    filled = out[..., 3] > 0
    if inner_lines:
        res = out.copy()
        with np.errstate(invalid="ignore"):
            for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                nd = np.roll(np.roll(depth, dy, 0), dx, 1)
                m = filled & np.isfinite(nd) & (depth - nd > 0.16)
                for yy, xx in zip(*np.nonzero(m)):
                    mm = materials[names[matid[yy, xx]]]
                    res[yy, xx, :3] = mm.tones[4]
        out = res
    if outline:
        res = out.copy()
        ring = np.zeros_like(filled)
        for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            ring |= np.roll(np.roll(filled, dy, 0), dx, 1)
        ring &= ~filled
        for yy, xx in zip(*np.nonzero(ring)):
            best = None
            for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                y2, x2 = yy + dy, xx + dx
                if 0 <= y2 < height and 0 <= x2 < width and filled[y2, x2]:
                    if best is None or depth[y2, x2] < depth[best]:
                        best = (y2, x2)
            if best is not None and matid[best] >= 0:
                mm = materials[names[matid[best]]]
                res[yy, xx, :3] = mm.outline_lit if lit_side[best] else mm.outline
                res[yy, xx, 3] = 255
        out = res
    if return_depth:
        return out, depth
    return out


def project(pt, anchor, mpp=MPP):
    pt = np.asarray(pt, float)
    return (float(pt @ RIGHT / mpp + anchor[0]), float(anchor[1] - pt @ UP / mpp))


def depth_of(pt):
    """Kameraya uzaklık (küçük = önde)."""
    return float(np.asarray(pt, float) @ FWD)
