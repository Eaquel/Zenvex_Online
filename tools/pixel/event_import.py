"""Etkinlik eşyaları (docs/20 A1-A3) → oyun.

  i_ev_<etkinlik>_<ring|neck|weapon>.*  → atlas i/ev_<etkinlik>_<yuva> (simge; ışıltılı, yumuşak zemin silme)
  art_ev_<etkinlik>_<yuva>.*            → assets/ui/art_ev_<etkinlik>_<yuva>.webp (ödül ekranındaki tanıtım afişi)
  w_ev_<etkinlik>.*                     → atlas w/ev_<etkinlik> (elde tutulan piksel silah; çapa = kabza)

Elde tutma: kahramanın katana / bıçak karelerinden (anahtar@katana, anahtar@knife) silahın kabza ve uç noktası çıkarılır
(çıplak kare ile silahlı kare arasındaki fark) ve manifest "attach"ına yazılır: attach["h/kaan"][kare]["wgrip"|"wtip"]
(çapaya göre doku pikseli). Oyun etkinlik silahını çıplak karenin üstüne bu doğru boyunca döndürüp çizer.

python3 tools/pixel/event_import.py <kaynak dizini>
"""
import glob
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
from atlas_patch import Atlas  # noqa: E402
from cutout import crop, key_soft, pixelize, to_img  # noqa: E402

UI = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "assets", "ui")
EVENTS = ["blood", "titan", "base", "scrap", "pack", "breath"]
ICON_PX = 160
# simgenin dünyadaki boyu (eski simgelerle aynı)
ICON_WORLD = {"ring": 28.0, "neck": 39.0, "weapon": 40.0}
# elde silah: kabzadan uca uzunluk, katana boyunun katı; kabza silahın sap ucundan ne kadar içeride
WEAPON_LEN = {"blood": 1.45, "titan": 1.15, "base": 0.85, "scrap": 1.0, "pack": 0.9, "breath": 1.45}
GRIP_AT = {"blood": 0.12, "titan": 0.12, "base": 0.16, "scrap": 0.1, "pack": 0.18, "breath": 0.12}
WEAPON_TEX = 120  # piksel silahın en uzun kenarı (doku pikseli)


def find(src, stem):
    for ext in (".png", ".jpg", ".jpeg", ".webp"):
        p = os.path.join(src, stem + ext)
        if os.path.exists(p):
            return p
    return None


def icons(src):
    add = []
    for e in EVENTS:
        for slot in ("ring", "neck", "weapon"):
            p = find(src, f"i_ev_{e}_{slot}")
            if not p:
                print("eksik simge", e, slot)
                continue
            a = crop(key_soft(p, spill=1.8 if e == "scrap" else 1.0), thr=24)
            im = to_img(a)
            s = ICON_PX / max(im.size)
            im = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)
            add.append((f"i/ev_{e}_{slot}", im, im.width / 2, im.height / 2, ICON_WORLD[slot] / max(im.size)))
    return add


def posters(src):
    for e in EVENTS:
        for slot in ("ring", "neck", "weapon"):
            p = find(src, f"art_ev_{e}_{slot}")
            if not p:
                print("eksik afiş", e, slot)
                continue
            im = Image.open(p).convert("RGB")
            s = 720 / im.width
            if s < 1:
                im = im.resize((720, round(im.height * s)), Image.LANCZOS)
            im.save(os.path.join(UI, f"art_ev_{e}_{slot}.webp"), "WEBP", quality=86, method=6)


def block_size(a):
    """piksel sanatının blok boyu: yatay aynı renk koşularının ortancası"""
    rgb = a[..., :3].astype(int)
    m = a[..., 3] > 128
    runs = []
    for y in range(0, a.shape[0], 3):
        row, mk = rgb[y], m[y]
        n = 1
        for x in range(1, a.shape[1]):
            if mk[x] and mk[x - 1] and np.abs(row[x] - row[x - 1]).sum() < 24:
                n += 1
            else:
                if n > 1 and mk[x - 1]:
                    runs.append(n)
                n = 1
    return max(1, int(np.median(runs))) if runs else 1


def weapons(src, A):
    add, meta = [], {}
    for e in EVENTS:
        p = find(src, f"w_ev_{e}")
        if not p:
            print("eksik silah", e)
            continue
        a = key_soft(p, lo=30, hi=80, spill=1.8 if e == "scrap" else 1.0)
        a[..., 3] = np.where(a[..., 3] >= 128, 255, 0)
        a = crop(a)
        s = block_size(a)
        q = crop(pixelize(a, s)) if s > 1 else a
        if max(q.shape[:2]) > WEAPON_TEX:
            im = to_img(q)
            k = WEAPON_TEX / max(im.size)
            im = im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)
            q = np.asarray(im).copy()
            q[..., 3] = np.where(q[..., 3] >= 120, 255, 0)
            q = crop(q)
        # sap → baş ekseni: yatay görsellerde soldan sağa, Kan Ayı orağı sol alttan sağ üste
        ys, xs = np.nonzero(q[..., 3] > 0)
        pts = np.stack([xs, ys], 1).astype(float)
        mean = pts.mean(0)
        u, sv, vt = np.linalg.svd(pts - mean, full_matrices=False)
        ax = vt[0]
        want = np.array([1.0, -1.0]) if e == "blood" else np.array([1.0, 0.0])
        if ax @ want < 0:
            ax = -ax
        t = (pts - mean) @ ax
        lo, hi = t.min(), t.max()
        grip = mean + ax * (lo + (hi - lo) * GRIP_AT[e])
        tip = mean + ax * hi
        meta[e] = {"grip": grip.tolist(), "tip": tip.tolist(), "len": WEAPON_LEN[e]}
        add.append((f"w/ev_{e}", to_img(q), float(grip[0]), float(grip[1]), 1.0))
        print("silah", e, q.shape, "blok", s)
    return add, meta


def segments(A):
    """kahraman katana / bıçak karelerinden kabza ve uç (çapaya göre doku pikseli)"""
    S = A.m["sprites"]
    out = {}

    def spr(k):
        v = S[k]
        img = np.asarray(A.page(v[0]).crop((v[1], v[2], v[1] + v[3], v[2] + v[4]))).astype(int)
        return img, v

    for wk in sorted(k for k in S if k.startswith("h/kaan/") and (k.endswith("@katana") or k.endswith("@knife"))):
        base, wpn = wk.split("@")
        if base not in S:
            continue
        a, va = spr(base)
        b, vb = spr(wk)
        dx, dy = round(vb[5] - va[5]), round(vb[6] - va[6])
        under = np.zeros(b.shape[:2], bool)
        same = np.zeros(b.shape[:2], bool)
        y0, x0 = max(0, dy), max(0, dx)
        y1, x1 = min(b.shape[0], dy + a.shape[0]), min(b.shape[1], dx + a.shape[1])
        if y1 > y0 and x1 > x0:
            sa = a[y0 - dy:y1 - dy, x0 - dx:x1 - dx]
            sb = b[y0:y1, x0:x1]
            under[y0:y1, x0:x1] = sa[..., 3] > 0
            same[y0:y1, x0:x1] = (sa[..., 3] > 0) & (np.abs(sa - sb).sum(-1) < 40)
        m = (b[..., 3] > 0) & ~same
        m = ndimage.binary_opening(m, iterations=1)
        lab, n = ndimage.label(m)
        if n == 0:
            continue
        sizes = ndimage.sum(m, lab, range(1, n + 1))
        m = lab == (1 + int(np.argmax(sizes)))
        ys, xs = np.nonzero(m)
        if len(xs) < 25:
            continue
        pts = np.stack([xs, ys], 1).astype(float)
        mean = pts.mean(0)
        _, _, vt = np.linalg.svd(pts - mean, full_matrices=False)
        ax = vt[0]
        t = (pts - mean) @ ax
        p0, p1 = mean + ax * t.min(), mean + ax * t.max()
        # kabza: gövdenin (çıplak karenin) ağırlık merkezine yakın uç
        bys, bxs = np.nonzero(under)
        body = np.array([bxs.mean(), bys.mean()]) if len(bxs) else np.array([vb[5], vb[6] * 0.6])
        if np.linalg.norm(p1 - body) < np.linalg.norm(p0 - body):
            p0, p1 = p1, p0
        rel = lambda p: [round(float(p[0] - vb[5]), 1), round(float(p[1] - vb[6]), 1)]
        out.setdefault(base.split("/", 2)[2], {})[wpn] = rel(p0) + rel(p1)
    return out


def main(src):
    posters(src)
    A = Atlas()
    A.patch("items", add=icons(src))
    wadd, wmeta = weapons(src, A)
    A.patch("items", add=wadd)
    seg = segments(A)
    att = A.m.setdefault("attach", {})
    hk = att.setdefault("h/kaan", {})
    for k, v in seg.items():
        d = hk.setdefault(k, {})
        for wpn, s in v.items():
            d["w_" + wpn] = s
    att["w/ev"] = {e: v for e, v in wmeta.items()}
    print("kare", len(seg))
    A.save()


if __name__ == "__main__":
    main(sys.argv[1])
