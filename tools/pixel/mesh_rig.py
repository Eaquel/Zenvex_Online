"""2D iskeletli mesh (skinned mesh) animasyon motoru — kahraman ve köpekler için.

Her piksel bir mesh noktasıdır: görsel parçalara (gövde, kollar, bacaklar, eller…) ayrılır, her parça Scale2x³ ile
8× büyütülür ve 8×8 alt pikselin her biri, yakınındaki kemiklere yumuşak ağırlıklarla bağlanır (doğrusal karışımlı
deri / LBS). Eklem yerlerinde ağırlıklar kaynaşır; dirsek ve diz yırtılmadan bükülür.

İskelet 3B'dir: kemikler karakterin kendi eksenlerinde (ileri F, yan S, aşağı Y) döner, ön-3/4 kamera ile
izdüşürülür (derinlik ekranda aşağı kayar). Böylece öne atılan bacak kısalır ve aşağı iner, kol geriye savrulunca
gövdenin arkasına geçer. Her kemiğin 2B dönüşümü: dinlenme parçası → izdüşümlü parça (dönme + eksen boyunca
ölçek; en/kalınlık korunur).

Çizim: her alt piksel ileri eşlenir, hedef pikselin merkezine en yakın alt piksel kazanır (en yakın örnek; ara
renk yok, piksel sanatı keskin kalır). Parçalar derinliğe göre sıralanır.
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cutout_rig import clean, scale2x  # noqa: E402

UP = 8


def up8(a):
    return scale2x(scale2x(scale2x(a)))


def sstep(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def axis_rot(n, ang):
    n = np.asarray(n, float)
    n = n / np.linalg.norm(n)
    c, s = math.cos(ang), math.sin(ang)
    K = np.array([[0, -n[2], n[1]], [n[2], 0, -n[0]], [-n[1], n[0], 0]])
    return np.eye(3) + s * K + (1 - c) * (K @ K)


def seg_dist(px, py, a, b):
    ax, ay = a
    bx, by = b
    vx, vy = bx - ax, by - ay
    L2 = vx * vx + vy * vy + 1e-9
    t = np.clip(((px - ax) * vx + (py - ay) * vy) / L2, 0, 1)
    return np.hypot(px - ax - t * vx, py - ay - t * vy)


class Part:
    """Görselin bir parçası: alt piksel konumları + renkleri + kemik ağırlıkları."""

    def __init__(self, name, tex, mask, weight_fn):
        self.name = name
        ys, xs = np.nonzero(mask & (tex[..., 3] > 0))
        if len(xs) == 0:
            self.px = np.zeros(0)
            self.py = np.zeros(0)
            self.col = np.zeros((0, 4), np.uint8)
            self.bones, self.W = [], np.zeros((0, 0))
            return
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        sub = np.where(mask[..., None], tex, 0)[y0:y1, x0:x1]
        big = up8(np.pad(sub, ((1, 1), (1, 1), (0, 0))))
        by, bx = np.nonzero(big[..., 3] > 0)
        self.col = big[by, bx]
        self.px = (bx + 0.5) / UP - 1 + x0
        self.py = (by + 0.5) / UP - 1 + y0
        bones, W = weight_fn(self.px, self.py)
        W = np.maximum(np.asarray(W, float), 0)
        W = W / np.maximum(W.sum(0, keepdims=True), 1e-9)
        self.bones, self.W = list(bones), W

    def deform(self, T):
        """T: kemik → (A 2x2, t 2) ; dönen: hedef x, y."""
        X = np.zeros_like(self.px)
        Y = np.zeros_like(self.py)
        for i, b in enumerate(self.bones):
            w = self.W[i]
            if not w.any():
                continue
            A, t = T[b]
            X += w * (A[0, 0] * self.px + A[0, 1] * self.py + t[0])
            Y += w * (A[1, 0] * self.px + A[1, 1] * self.py + t[1])
        return X, Y


def dist_weights(px, py, segs, sigma):
    """Kemik parçalarına uzaklıkla yumuşak ağırlık (en yakına göre normalize Gauss)."""
    D = np.stack([seg_dist(px, py, a, b) for a, b in segs])
    dmin = D.min(0)
    return np.exp(-(D ** 2 - dmin ** 2) / (2 * sigma * sigma))


def splat(canvas, X, Y, col, ox=0.0, oy=0.0):
    """Alt pikselleri tuvale yaz: her hedef pikselde merkezine en yakın alt piksel."""
    H, W = canvas.shape[:2]
    X = X + ox
    Y = Y + oy
    ix = np.floor(X).astype(np.int64)
    iy = np.floor(Y).astype(np.int64)
    ok = (ix >= 0) & (ix < W) & (iy >= 0) & (iy < H)
    if not ok.any():
        return canvas
    ix, iy, X, Y, col = ix[ok], iy[ok], X[ok], Y[ok], col[ok]
    d = (X - ix - 0.5) ** 2 + (Y - iy - 0.5) ** 2
    key = iy * W + ix
    order = np.lexsort((d, key))
    k = key[order]
    first = np.ones(len(k), bool)
    first[1:] = k[1:] != k[:-1]
    sel = order[first]
    canvas[iy[sel], ix[sel]] = col[sel]
    return canvas


def bone_xform(a0, b0, a1, b1):
    """Dinlenme parçası (a0→b0) → poz parçası (a1→b1): eksende ölçek, dikte 1. A p + t."""
    a0, b0, a1, b1 = (np.asarray(v, float) for v in (a0, b0, a1, b1))
    v0, v1 = b0 - a0, b1 - a1
    L0, L1 = np.linalg.norm(v0), np.linalg.norm(v1)
    u0 = v0 / max(L0, 1e-6)
    u1 = v1 / max(L1, 1e-6) if L1 > 1e-6 else u0
    n0 = np.array([-u0[1], u0[0]])
    n1 = np.array([-u1[1], u1[0]])
    s = max(L1 / max(L0, 1e-6), 0.25)
    A = s * np.outer(u1, u0) + np.outer(n1, n0)
    return A, a1 - A @ a0


class Skeleton:
    """Eklemler: ad → (ebeveyn, dinlenme 2B konumu). Dinlenme 3B: (x, y, 0). Kamera: ekran = (X, Y + K·Z)."""

    def __init__(self, joints, yaw_deg=20.0, k=0.45):
        self.joints = joints  # sıralı: ebeveyn önce
        a = math.radians(yaw_deg)
        self.F = np.array([-math.sin(a), 0.0, math.cos(a)])  # ileri (kameraya ve sola)
        self.S = np.array([math.cos(a), 0.0, math.sin(a)])  # yan (izleyicinin sağı)
        self.Y = np.array([0.0, 1.0, 0.0])  # aşağı
        self.k = k
        self.rest = {n: np.array([p[0], p[1], 0.0]) for n, (par, p) in joints.items()}

    def rot(self, flex=0.0, abd=0.0, twist=0.0):
        """Yerel dönüş (derece): flex = uzak uç ileri (F), abd = izleyicinin soluna (−S), twist = dikey eksen."""
        D = math.radians
        return axis_rot(self.Y, D(twist)) @ axis_rot(self.F, D(abd)) @ axis_rot(self.S, D(flex))

    def fk(self, R, root_t=(0, 0, 0)):
        """R: eklem → yerel 3x3. Dönen: eklem → poz 3B konumu, eklem → küresel dönüş."""
        pos, G = {}, {}
        for n, (par, _) in self.joints.items():
            Rl = R.get(n, np.eye(3))
            if par is None:
                pos[n] = self.rest[n] + np.asarray(root_t, float)
                G[n] = Rl
            else:
                pos[n] = pos[par] + G[par] @ (self.rest[n] - self.rest[par])
                G[n] = G[par] @ Rl
        return pos, G

    def proj(self, v):
        return np.array([v[0], v[1] + self.k * v[2]])


def rot2(p, ang, piv):
    c, s = math.cos(ang), math.sin(ang)
    x, y = p[0] - piv[0], p[1] - piv[1]
    return np.array([piv[0] + c * x - s * y, piv[1] + s * x + c * y])


def ease(kf, t, loop=True):
    """Anahtar kare listesinde (eşit aralıklı) Catmull-Rom ara değer; t ∈ [0,1)."""
    n = len(kf)
    if loop:
        x = (t % 1.0) * n
        i = int(math.floor(x)) % n
        f = x - math.floor(x)
        p0, p1, p2, p3 = kf[(i - 1) % n], kf[i], kf[(i + 1) % n], kf[(i + 2) % n]
    else:
        x = min(max(t, 0.0), 1.0) * (n - 1)
        i = min(int(math.floor(x)), n - 2)
        f = x - i
        p0, p1, p2, p3 = kf[max(i - 1, 0)], kf[i], kf[i + 1], kf[min(i + 2, n - 1)]
    return 0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f ** 3)


def finish(canvas, min_px=6):
    return clean(canvas, min_px)
