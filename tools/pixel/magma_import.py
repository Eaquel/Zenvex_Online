"""Magma İskelet Boss paketi → Titan görselleri (z/magma/<anim>_<yön>_<kare>) ve saldırı efektleri (fx/magma_*).

Sayfalar koyu gri, gürültülü zeminli JPG: zemin, büyük ölçekli medyanla tahmin edilen yerel zemin rengine yakın ve
doygunluğu düşük piksellerin kenardan taşma doldurmasıyla silinir (kapalı büyük boşluklar da). Her sayfa 2×4 kare;
kareler sayfa boyunca ortak tuvalle kesilir (titreme yok), çapa ilk karenin ayak tabanı.

  yürüme: 8 yön ayrı çizilmiş (0° ön … 315°)
  bekleme, pençe, yere vurma, ateş püskürtme, hasar, ölüm: ön (0), çapraz (1, 2; sağ-öne çevrilir), arka (3, 4)
  efektler: alev konisi 8, ateş topu 4, pençe izi 4, yere vurma patlaması 4

python3 tools/pixel/magma_import.py <Magma_Boss_2.5D_Tam_Oyun_Paketi dizini>
"""
import glob
import os
import sys

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
from atlas_patch import Atlas  # noqa: E402
from creature_import import dist, feat  # noqa: E402
from cutout import crop, flip, to_img  # noqa: E402

WORLD_H = 270.0  # ayakta boy (dünya pikseli)
TEX = 0.6  # kaynak → doku


def key_dark(path, tol=16, sat_tol=16):
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(int)
    H, W, _ = a.shape
    bgl = np.asarray(im.resize((W // 16, H // 16), Image.BOX).filter(ImageFilter.MedianFilter(7)).resize((W, H), Image.BILINEAR)).astype(int)
    d = np.abs(a - bgl).max(-1)
    sat = a.max(-1) - a.min(-1)
    # zemin + yere düşen gri gölge (zeminden koyu, renksiz)
    cand = ((d < tol) & (sat < sat_tol)) | ((sat < 9) & (a.max(-1) < bgl.max(-1) + 4) & (a.max(-1) > bgl.max(-1) - 30))
    lab, n = ndimage.label(cand)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    sizes = ndimage.sum(np.ones_like(lab), lab, range(n + 1))
    bg = np.isin(lab, list(border)) | (sizes[lab] > 1500) & cand
    al = np.where(bg, 0, 255).astype(np.uint8)
    fg = al > 0
    lab2, n2 = ndimage.label(fg)
    s2 = ndimage.sum(fg, lab2, range(n2 + 1))
    al[(s2[lab2] < 60) & fg] = 0
    return np.dstack([a.astype(np.uint8), al])


def cells(a, rows=2, cols=4):
    H, W = a.shape[:2]
    return [a[int(r * H / rows):int((r + 1) * H / rows), int(c * W / cols):int((c + 1) * W / cols)] for r in range(rows) for c in range(cols)]


def frames(path, rows=2, cols=4):
    """ortak tuvalle kesilmiş kareler ve ilk karenin ayak/merkez çapası"""
    cs = cells(key_dark(path), rows, cols)
    bbs = []
    for c in cs:
        ys, xs = np.nonzero(c[..., 3] > 0)
        if len(xs):
            bbs.append((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    x0 = min(b[0] for b in bbs); y0 = min(b[1] for b in bbs)
    x1 = max(b[2] for b in bbs); y1 = max(b[3] for b in bbs)
    out = [c[y0:y1, x0:x1] for c in cs]
    f0 = out[0][..., 3] > 0
    ys, xs = np.nonzero(f0)
    # ayak tabanı: ilk karenin en alt satırı; merkez: gövde (üst %60) ağırlık merkezi
    body = f0[: int(f0.shape[0] * 0.6)]
    bys, bxs = np.nonzero(body)
    ax = float(bxs.mean()) if len(bxs) else out[0].shape[1] / 2
    return out, ax, float(ys.max())


def scaled(f, ax, ay, s):
    h, w = f.shape[:2]
    im = to_img(f).resize((max(1, round(w * s)), max(1, round(h * s))), Image.BOX)
    q = np.asarray(im).copy()
    q[..., 3] = np.where(q[..., 3] >= 100, 255, 0)
    return to_img(q), ax * s, ay * s


def main(src):
    A = Atlas()
    W = os.path.join(src, "03_Yurume_Animasyonlari_8_Yon")
    walk = sorted(glob.glob(os.path.join(W, "0[1-8]_*.jpg")))
    walk = [p for p in walk if "03b" not in os.path.basename(p)]
    items, table = [], {}
    # ölçek: ön yürüme ilk karesinin boyu → WORLD_H
    fr0, _, ay0 = frames(walk[0])
    stand_h = (fr0[0][..., 3] > 0).any(1).sum()
    k = WORLD_H / (stand_h * TEX)
    ref_se = None
    for d, p in enumerate(walk):
        fr, ax, ay = frames(p)
        if d == 1:
            ref_se = feat(fr[0])
        for i, f in enumerate(fr):
            im, sx, sy = scaled(f, ax, ay, TEX)
            items.append((f"z/magma/walk_{d}_{i}", im, sx, sy, k))
    table["walk"] = [8, 9, 1]
    S = {"idle": ("05", "01_"), "attack": ("04", "01_"), "slam": ("04", "02_"), "breath": ("04", "03_"), "hurt": ("05", "02_"), "death": ("05", "03_")}
    views = {"Tam_On_0_Derece": (0,), "Capraz_ve_Yan_45_90_Derece": (1, 2), "Tam_Arka_180_Derece": (3, 4)}
    for anim, (folder, prefix) in S.items():
        for view, dirs in views.items():
            p = glob.glob(os.path.join(src, folder + "_*", view, prefix + "*.jpg"))[0]
            fr, ax, ay = frames(p)
            if view.startswith("Capraz"):
                # çapraz sayfa sola bakıyorsa sağ-öne çevrilir (yürüme 45° ile karşılaştırılır)
                if dist(feat(flip(fr[0])), ref_se) < dist(feat(fr[0]), ref_se):
                    fr = [flip(f) for f in fr]
                    ax = fr[0].shape[1] - ax
            if anim == "hurt":
                fr = fr[:3]  # yalnız sarsılma (diz çökme kısmı oyunda uzun sürüyor)
            for d in dirs:
                for i, f in enumerate(fr):
                    im, sx, sy = scaled(f, ax, ay, TEX)
                    items.append((f"z/magma/{anim}_{d}_{i}", im, sx, sy, k))
        table[anim] = [3 if anim == "hurt" else 8, {"idle": 7, "attack": 13, "slam": 8, "breath": 7, "hurt": 10, "death": 7}[anim],
                       1 if anim == "idle" else 0]
    A.patch("zombie_magma", drop=lambda key: key.startswith("z/magma/"), add=items)
    A.m["anims"]["z/magma"] = table
    print("magma", table, len(items), "k", round(k, 3))
    # ---------------------------------------------------------------- efektler
    V = os.path.join(src, "06_Bagimsiz_VFX_Efekt_Sablonlari")
    v1 = cells(key_dark(glob.glob(os.path.join(V, "01_*.jpg"))[0]), 3, 4)
    v2 = cells(key_dark(glob.glob(os.path.join(V, "02_*.jpg"))[0]), 2, 4)
    fx_def = {"magma_breath": (v1[:8], 12, (0.08, 0.5)), "magma_ball": (v1[8:], 10, (0.5, 0.6)),
              "magma_claw": (v2[:4], 14, (0.5, 0.5)), "magma_slam": (v2[4:], 10, (0.5, 0.7))}
    fx = []
    for name, (fs, fps, anc) in fx_def.items():
        bbs = [np.nonzero(f[..., 3] > 0) for f in fs]
        x0 = min(b[1].min() for b in bbs if len(b[1])); x1 = max(b[1].max() for b in bbs if len(b[1])) + 1
        y0 = min(b[0].min() for b in bbs if len(b[0])); y1 = max(b[0].max() for b in bbs if len(b[0])) + 1
        for i, f in enumerate(fs):
            c = f[y0:y1, x0:x1]
            im = to_img(c).resize((max(1, round(c.shape[1] * 0.55)), max(1, round(c.shape[0] * 0.55))), Image.BOX)
            fx.append((f"fx/{name}/{i}", im, im.width * anc[0], im.height * anc[1], 1.0))
        A.m["fx"][name] = {"frames": len(fs), "fps": fps, "loop": False, "blend": "normal", "scale": 1.0, "anchor": list(anc), "pixel": True}
    A.patch("fx7", drop=lambda key: key.startswith(tuple(f"fx/{n}/" for n in fx_def)), add=fx)
    A.save()


if __name__ == "__main__":
    main(sys.argv[1])
