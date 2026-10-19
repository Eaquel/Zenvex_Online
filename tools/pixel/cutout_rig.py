"""Tek piksel sanat görselinden animasyonlu kahraman (cut-out rig).

Kaynak görsel olduğu gibi korunur (renkler/pikseller elle çizildiği gibi kalır); hareket parçaları
oynatarak verilir: kol ayrı katman (omuz çevresinde döner), bacaklar kalça çevresinde salınır,
üst gövde nefes/sallanma, düşme/takla tüm figürün döndürülmesiyle. Döndürme RotSprite benzeri:
8× büyütülmüş görselde en yakın örnekleme (merdiven kenarlar korunur).

python3 tools/pixel/cutout_rig.py art/v5/warrior_base.png /home/user/zx_pix/hero
Çıktı: <anim>_<yön>_<kare>.png (yön 0-4; 5-7 paketleyicide aynalanır), meta.json, attach.json
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

# --- karakter rig'i (kaynak görsel koordinatları, 94×258) ---
ARM = [(14, 44), (30, 46), (34, 58), (30, 78), (24, 92), (21, 110), (21, 128), (20, 145), (0, 147), (0, 96), (4, 72), (8, 56)]
SHOULDER = (26.0, 52.0)
FIST = (10.0, 136.0)
HIP_Y = 150.0
LEG_A = (32.0, 150.0)  # izleyiciye göre sol bacak (karakterin sağı)
LEG_B = (60.0, 152.0)
SPLIT_X = 46.0
FACE = (34, 4, 62, 50)  # maske + atkı (sırt görünümünde kapüşonla kapanır)
POINTS = {"head": (48, 22), "neck": (48, 40), "back": (56, 62), "chest": (44, 72), "lhand": (72, 140), "lfoot": (72, 244), "rfoot": (24, 252)}

CW, CH = 560, 340
ANCHOR = (280, 320)
MPP = 0.0066  # yerel piksel (m): 258 px ≈ 1.7 m

ANIMS = {"idle": 6, "walk": 8, "run": 8, "punch": 8, "roll": 6, "hurt": 2, "knockdown": 5, "getup": 5, "death": 6}


def rot(v, a):
    c, s = math.cos(a), math.sin(a)
    return (c * v[0] - s * v[1], s * v[0] + c * v[1])


def smooth(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


def scale2x(a):
    """EPX/Scale2x: kenarları yumuşatmadan 2× büyütme (RotSprite'ın ilk adımı)."""
    def eq(x, y):
        return (x == y).all(-1)
    P = np.pad(a, ((1, 1), (1, 1), (0, 0)), mode="edge")
    A, B, C, D = P[:-2, 1:-1], P[1:-1, :-2], P[1:-1, 2:], P[2:, 1:-1]
    E = a
    e0 = np.where((eq(B, A) & ~eq(A, C) & ~eq(B, D))[..., None], A, E)
    e1 = np.where((eq(A, C) & ~eq(A, B) & ~eq(C, D))[..., None], C, E)
    e2 = np.where((eq(B, D) & ~eq(A, B) & ~eq(C, D))[..., None], B, E)
    e3 = np.where((eq(C, D) & ~eq(A, C) & ~eq(B, D))[..., None], D, E)
    h, w = a.shape[:2]
    out = np.zeros((h * 2, w * 2, a.shape[2]), a.dtype)
    out[0::2, 0::2], out[0::2, 1::2], out[1::2, 0::2], out[1::2, 1::2] = e0, e1, e2, e3
    return out


_UP = {}


def upscaled(src):
    k = id(src)
    if k not in _UP or _UP[k][0] is not src:
        _UP[k] = (src, scale2x(scale2x(scale2x(src))))
    return _UP[k][1]


def sample(src, sx, sy, up=8):
    """src (H,W,4); sx, sy kaynak koordinatları (piksel merkezi = +0.5). Scale2x³ ile 8× ızgarada en yakın örnek."""
    big = upscaled(src)
    h, w = big.shape[:2]
    ix = np.floor(sx * up).astype(int)
    iy = np.floor(sy * up).astype(int)
    ok = (ix >= 0) & (ix < w) & (iy >= 0) & (iy < h)
    out = np.zeros(sx.shape + (4,), np.uint8)
    out[ok] = big[iy[ok], ix[ok]]
    return out


def clean(img, min_px=12):
    """Ana figürden kopuk küçük parçaları siler."""
    from scipy import ndimage
    lab, n = ndimage.label(img[..., 3] > 0)
    if n <= 1:
        return img
    sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
    keep = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= min_px])
    img = img.copy()
    img[~keep] = 0
    return img


def rotate_layer(src, ang, pivot, shift=(0.0, 0.0), sy_scale=1.0):
    """Katmanı pivot çevresinde döndürür (+açı = ekranda saat yönü), sonra kaydırır; ters eşleme."""
    h, w = src.shape[:2]
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    px, py = xs + 0.5 - pivot[0] - shift[0], ys + 0.5 - pivot[1] - shift[1]
    c, s = math.cos(-ang), math.sin(-ang)
    qx, qy = c * px - s * py, s * px + c * py
    qy = qy / sy_scale
    return sample(src, qx + pivot[0], qy + pivot[1])


def over(dst, src):
    m = src[..., 3] > 0
    dst[m] = src[m]
    return dst


PAD = 110  # kol/eğilme taşmaları için kenar boşluğu


def _pad_consts():
    g = globals()
    g["ARM"] = [(x + PAD, y + PAD) for x, y in ARM]
    for k in ("SHOULDER", "FIST", "LEG_A", "LEG_B"):
        g[k] = (g[k][0] + PAD, g[k][1] + PAD)
    g["HIP_Y"] += PAD
    g["SPLIT_X"] += PAD
    x0, y0, x1, y1 = FACE
    g["FACE"] = (x0 + PAD, y0 + PAD, x1 + PAD, y1 + PAD)
    g["POINTS"] = {k: (x + PAD, y + PAD) for k, (x, y) in POINTS.items()}


class Rig:
    def __init__(self, base):
        a = np.pad(np.asarray(base.convert("RGBA")), ((PAD, PAD), (PAD, PAD), (0, 0)))
        self.foot_y = PAD + base.size[1]
        self.h, self.w = a.shape[:2]
        m = Image.new("L", (self.w, self.h), 0)
        ImageDraw.Draw(m).polygon(ARM, fill=255)
        arm_m = (np.asarray(m) > 0) & (a[..., 3] > 0)
        self.arm = np.where(arm_m[..., None], a, 0).astype(np.uint8)
        body = np.where(arm_m[..., None], 0, a).astype(np.uint8)
        # kolun arkasında kalan gövde: kolun gövde içindeki kısmını komşu gövde renkleriyle doldur
        fill = arm_m.copy()
        fill[:, :18 + PAD] = False
        fill[:44 + PAD] = False
        body = self._inpaint(body, fill)
        self.body = body

    @staticmethod
    def _inpaint(img, region):
        img = img.copy()
        todo = region & (img[..., 3] == 0)
        for _ in range(40):
            if not todo.any():
                break
            new = img.copy()
            done = np.zeros_like(todo)
            for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                sh = np.roll(np.roll(img, dy, 0), dx, 1)
                take = todo & ~done & (sh[..., 3] > 0)
                new[take] = sh[take]
                done |= take
            img = new
            todo &= ~done
        return img

    def back_view(self):
        """Sırt görünümü: maske/atkı bölgesini kapüşon tonlarıyla kapat (komşu koyu piksellerden doldur)."""
        b = self.body.copy()
        x0, y0, x1, y1 = FACE
        reg = np.zeros(b.shape[:2], bool)
        reg[y0:y1, x0:x1] = True
        lum = b[..., :3].astype(int).sum(-1)
        red = ((b[..., 0].astype(int) - b[..., 2]) > 22) & ((b[..., 0].astype(int) - b[..., 1]) > 22)
        face = reg & (b[..., 3] > 0) & ((lum > 240) | red)
        b[face, 3] = 0
        b = self._inpaint(b, face)
        r = Rig.__new__(Rig)
        r.h, r.w, r.arm, r.body, r.foot_y = self.h, self.w, self.arm, b, self.foot_y
        return r

    def layers(self):
        """Üst gövde / ön bacak (A) / arka bacak (B) katmanları; bacak katmanları ek yerinde boşluk kalmasın diye
        kalçanın biraz üstünden başlar (üst gövde üstte çizilir)."""
        if getattr(self, "_layers", None) is None:
            h, w = self.h, self.w
            ys, xs = np.mgrid[0:h, 0:w]
            U = ys < HIP_Y
            A = (ys >= HIP_Y - 8) & (xs < SPLIT_X)
            Bm = (ys >= HIP_Y - 8) & (xs >= SPLIT_X)
            self._layers = [np.where(m[..., None], self.body, 0).astype(np.uint8) for m in (U, A, Bm)]
        return self._layers

    def warp_body(self, legA=0.0, legB=0.0, bob=0.0, lean=0.0):
        """Gövde: üst kısım kalça çevresinde eğilir + iner; bacaklar kalça çevresinde salınır."""
        U, A, Bl = self.layers()
        hx = (LEG_A[0] + LEG_B[0]) / 2
        out = rotate_layer(Bl, legB, LEG_B, shift=(0, bob * 0.5))
        out = over(out, rotate_layer(A, legA, LEG_A, shift=(0, bob * 0.5)))
        return over(out, rotate_layer(U, lean, (hx, HIP_Y), shift=(0, bob)))

    def shoulder_pos(self, bob, lean):
        hx, hy = (LEG_A[0] + LEG_B[0]) / 2, HIP_Y
        v = rot((SHOULDER[0] - hx, SHOULDER[1] - hy), lean)
        return (hx + v[0], hy + v[1] + bob)

    def frame(self, legA=0.0, legB=0.0, bob=0.0, lean=0.0, arm=0.0, arm_len=1.0):
        body = self.warp_body(legA, legB, bob, lean)
        sp = self.shoulder_pos(bob, lean)
        armL = rotate_layer(self.arm, arm + lean, SHOULDER, shift=(sp[0] - SHOULDER[0], sp[1] - SHOULDER[1]))
        img = over(body, armL)
        fv = rot((FIST[0] - SHOULDER[0], FIST[1] - SHOULDER[1]), arm + lean)
        pts = {"rhand": (sp[0] + fv[0], sp[1] + fv[1])}
        hx, hy = (LEG_A[0] + LEG_B[0]) / 2, HIP_Y
        for k, p in POINTS.items():
            if p[1] < HIP_Y:
                v = rot((p[0] - hx, p[1] - hy), lean)
                pts[k] = (hx + v[0], hy + v[1] + bob)
            else:
                piv, ang = (LEG_A, legA) if p[0] < SPLIT_X else (LEG_B, legB)
                v = rot((p[0] - piv[0], p[1] - piv[1]), ang)
                pts[k] = (piv[0] + v[0], piv[1] + v[1])
        return img, pts


def place(img, pts, foot_y, ang=0.0, pivot=None, dx=0.0, dy=0.0, sy=1.0):
    """Kareyi tuvale yerleştirir (ayak ortası = ANCHOR); isteğe bağlı tüm figür döndürme/basma."""
    h, w = img.shape[:2]
    canvas = np.zeros((CH, CW, 4), np.uint8)
    ox, oy = ANCHOR[0] - (LEG_A[0] + LEG_B[0]) / 2, ANCHOR[1] - foot_y
    x0, y0 = int(round(ox)), int(round(oy))
    canvas_img = np.zeros_like(canvas)
    # tuvale kırparak yerleştir
    sx0, sy0 = max(0, -x0), max(0, -y0)
    dx0, dy0 = max(0, x0), max(0, y0)
    ww, hh = min(w - sx0, CW - dx0), min(h - sy0, CH - dy0)
    canvas_img[dy0:dy0 + hh, dx0:dx0 + ww] = img[sy0:sy0 + hh, sx0:sx0 + ww]
    piv = pivot if pivot is not None else ANCHOR
    if ang or dx or dy or sy != 1.0:
        canvas_img = rotate_layer(canvas_img, ang, piv, shift=(dx, dy), sy_scale=sy)
    out = {}
    for k, (x, y) in pts.items():
        X, Y = x + x0, y + y0
        Y = piv[1] + (Y - piv[1]) * sy
        v = rot((X - piv[0], Y - piv[1]), ang)
        out[k] = [round(piv[0] + v[0] + dx, 1), round(piv[1] + v[1] + dy, 1)]
    return clean(over(canvas, canvas_img)), out


def anim_frames(rig, anim, n):
    D = math.radians
    for f in range(n):
        t = f / n
        ph = 2 * math.pi * t
        g = dict(ang=0.0, dx=0.0, dy=0.0, sy=1.0, pivot=None)
        if anim == "idle":
            k = dict(bob=round(1.0 * (1 - math.cos(ph)) / 2 + 0.2), arm=D(2) * math.sin(ph))
        elif anim == "walk":
            k = dict(legA=D(16) * math.sin(ph), legB=-D(16) * math.sin(ph), bob=round(1.6 * abs(math.sin(ph))),
                     arm=-D(14) * math.sin(ph), lean=-D(2))
        elif anim == "run":
            k = dict(legA=D(28) * math.sin(ph), legB=-D(28) * math.sin(ph), bob=round(3.0 * abs(math.sin(ph))),
                     arm=-D(30) * math.sin(ph) + D(10), lean=-D(7))
        elif anim == "punch":
            arm = [0, -14, -20, 35, 82, 84, 50, 14][f]
            lean = [0, 2, 3, -4, -8, -8, -4, -1][f]
            k = dict(arm=D(arm), lean=D(lean), legA=D([0, -3, -4, 6, 10, 10, 6, 2][f]), legB=D([0, 2, 3, -4, -6, -6, -4, -1][f]))
            g["dx"] = [0, 1, 2, -3, -6, -6, -3, -1][f]
        elif anim == "roll":
            k = dict(legA=D(12), legB=-D(6), arm=D(30), lean=-D(6))
            g.update(ang=-2 * math.pi * t, sy=0.72, dx=-18 * t, pivot=(ANCHOR[0], ANCHOR[1] - 80))
            g["dy"] = 0
        elif anim == "hurt":
            k = dict(lean=D([6, 11][f]), arm=-D([10, 18][f]), bob=1)
            g["dx"] = [2, 3][f]
        elif anim == "knockdown":
            k = dict(lean=D(8), arm=-D(25), legA=-D(8))
            g["ang"] = D([12, 32, 58, 82, 90][f])
        elif anim == "getup":
            k = dict(lean=-D([0, 10, 12, 6, 0][f]), arm=D([0, 20, 30, 15, 0][f]))
            g["ang"] = D([90, 68, 42, 18, 0][f])
        elif anim == "death":
            k = dict(lean=D(10), arm=-D(30), legA=-D(10))
            g["ang"] = D([14, 40, 68, 88, 90, 90][f])
        else:
            k = {}
        img, pts = rig.frame(**k)
        yield place(img, pts, rig.foot_y, g["ang"], g["pivot"], g["dx"], g["dy"], g["sy"])


def main(src, out):
    os.makedirs(out, exist_ok=True)
    for f in os.listdir(out):
        if f.endswith(".png"):
            os.remove(os.path.join(out, f))
    _pad_consts()
    front = Rig(Image.open(src))
    back = front.back_view()
    att = {}
    for anim, n in ANIMS.items():
        for vi, (rig, mirror) in enumerate(((front, False), (back, False))):
            for f, (img, pts) in enumerate(anim_frames(rig, anim, n)):
                im = Image.fromarray(img)
                ptsm = {k: [CW - v[0], v[1]] for k, v in pts.items()}
                imm = im.transpose(Image.FLIP_LEFT_RIGHT)
                # yön eşlemesi: 0 (güney) = ön, 1-2 = ön ayna, 3 = sırt ayna, 4 (kuzey) = sırt
                dirs = {0: (im, pts), 1: (imm, ptsm), 2: (imm, ptsm)} if vi == 0 else {3: (imm, ptsm), 4: (im, pts)}
                for d, (I, P) in dirs.items():
                    I.save(os.path.join(out, f"{anim}_{d}_{f}.png"))
                    att[f"{anim}_{d}_{f}"] = P
    json.dump(att, open(os.path.join(out, "attach.json"), "w"))
    json.dump({"anchor": list(ANCHOR), "mpp": MPP, "frames": ANIMS}, open(os.path.join(out, "meta.json"), "w"))
    print("kareler:", len(att))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
