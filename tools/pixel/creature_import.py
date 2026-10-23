"""V13 bölge yaratıkları (z_<tür>_{dirs,idle,walk,attack,hurt,death}.png) → atlas z/<tür>/<anim>_<yön>_<kare>.

Yapay zekâ sayfaları tarife tam uymuyor: satır sayısı, kare sayısı ve satırın sağa mı sola mı baktığı sayfadan sayfaya
değişiyor. Bu yüzden:
  1) yeşil zemin silinir, ayrık figürler bulunur (yapışık iki kare genişliğe bakılarak bölünür), satırlara toplanır;
  2) yön sayfasındaki 5 görünüm (G, GD, D, KD, K) ve yan görünümlerin aynası referans alınır; 5 satırlı sayfalarda satırlar
     yönlere en iyi eşleşmeyle (Macar yöntemi) atanır, düzensiz sayfalarda her kare tek tek sınıflanır; sola bakan satır
     aynalanır;
  3) her sayfanın ölçeği yön sayfasına eşitlenir (aynı yaratık her animasyonda aynı boy), ayak tabanı çapa;
  4) eksik yön/kare komşu yönden doldurulur. Ölüm yalnız G ve D (oyun diğer yönleri bunlardan aynalar).
Oyun 5-7 yönlerini 3-1'in aynası olarak çizer.

python3 tools/pixel/creature_import.py <1_Yaratiklar dizini> [tür …]
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
from atlas_patch import Atlas  # noqa: E402
from cutout import crop, flip, key, parts, to_img  # noqa: E402

# tür: (önden boy, dünya pikseli; animasyon hızları)
SPECIES = {
    "lumberjack": 116, "wolf": 72, "shroom": 118,
    "office": 110, "riot": 120, "crawler": 70,
    "charred": 112, "emberhound": 78, "ashwraith": 122,
    "mummy": 114, "scorpion": 62, "biker": 116,
    "bloater": 128, "boggiant": 150, "witch": 120,
    "sailor": 116, "icecrab": 56, "dockbrute": 142,
    "shieldbot": 116, "flamebot": 116,
}
# animasyon: (beklenen satır, kare, fps, döngü)
ANIMS = {"idle": (5, 4, 5, 1), "walk": (5, 6, 10, 1), "attack": (5, 6, 12, 0), "hurt": (5, 2, 10, 0), "death": (2, 6, 8, 0)}
TEXEL = 1.15  # dünya pikseli / doku pikseli


def frames_of(a):
    """figürler: [(x0, y0, x1, y1)], yapışık kareler bölünmüş, satırlara toplanmış"""
    ps = parts(a, min_area=300, gap=4)
    if not ps:
        return []
    # üst üste yapışık kareler (sıkışık satırlar): en boş satırdan ikiye
    hs0 = np.median([p[3] - p[1] for p in ps])
    ps2 = []
    for x0, y0, x1, y1 in ps:
        h = y1 - y0
        n = int(round(h / hs0)) if h > hs0 * 1.6 else 1
        if n <= 1:
            ps2.append((x0, y0, x1, y1))
            continue
        row = (a[y0:y1, x0:x1, 3] > 40).sum(1).astype(float)
        cuts = [y0]
        for k in range(1, n):
            c = int(h * k / n)
            lo, hi = max(1, c - int(hs0 * 0.3)), min(h - 1, c + int(hs0 * 0.3))
            cuts.append(y0 + lo + int(np.argmin(row[lo:hi])))
        cuts.append(y1)
        for u, v in zip(cuts, cuts[1:]):
            sub = a[u:v, x0:x1]
            xs = np.nonzero((sub[..., 3] > 40).any(0))[0]
            if len(xs):
                ps2.append((x0 + xs.min(), u, x0 + xs.max() + 1, v))
    ps = ps2
    ws = np.median([p[2] - p[0] for p in ps])
    out = []
    for x0, y0, x1, y1 in ps:
        w = x1 - x0
        n = int(round(w / ws)) if w > ws * 1.6 else 1
        if n <= 1:
            out.append((x0, y0, x1, y1))
            continue
        # en az dolu sütunlardan böl
        col = (a[y0:y1, x0:x1, 3] > 40).sum(0).astype(float)
        cuts = [x0]
        for k in range(1, n):
            c = int(w * k / n)
            lo, hi = max(1, c - int(ws * 0.35)), min(w - 1, c + int(ws * 0.35))
            cuts.append(x0 + lo + int(np.argmin(col[lo:hi])))
        cuts.append(x1)
        for u, v in zip(cuts, cuts[1:]):
            sub = a[y0:y1, u:v]
            ys = np.nonzero((sub[..., 3] > 40).any(1))[0]
            if len(ys):
                out.append((u, y0 + ys.min(), v, y0 + ys.max() + 1))
    # satırlar
    out.sort(key=lambda b: (b[1] + b[3]) / 2)
    hs = np.median([b[3] - b[1] for b in out])
    rows = []
    for b in out:
        cy = (b[1] + b[3]) / 2
        if rows and abs(cy - np.mean([(r[1] + r[3]) / 2 for r in rows[-1]])) < hs * 0.55:
            rows[-1].append(b)
        else:
            rows.append([b])
    return [sorted(r, key=lambda b: b[0]) for r in rows]


def cut(a, b):
    """kare: yalnız kendi figürü (komşu karenin taşan parçası silinir)"""
    x0, y0, x1, y1 = b
    return crop(a[y0:y1, x0:x1].copy())


def feat(img, n=(24, 40)):
    """karşılaştırma özelliği: tabana oturtulmuş, sabit kutuya sığdırılmış renk × alfa"""
    h, w = img.shape[:2]
    s = min(n[0] / w, n[1] / h)
    im = to_img(img).resize((max(1, int(w * s)), max(1, int(h * s))), Image.BOX)
    box = Image.new("RGBA", n, (0, 0, 0, 0))
    box.paste(im, ((n[0] - im.width) // 2, n[1] - im.height))
    f = np.asarray(box).astype(float) / 255.0
    return np.concatenate([f[..., :3] * f[..., 3:], f[..., 3:] * 1.5], -1)


def dist(a, b):
    return np.abs(a - b).mean()


def refs(dirs_img):
    rows = frames_of(dirs_img)
    views = [cut(dirs_img, b) for r in rows for b in r]
    views = sorted(views, key=lambda v: 0)[:5]
    if len(rows) > 1:  # bazı yön sayfaları iki satır: ilk satır
        views = [cut(dirs_img, b) for b in rows[0]][:5]
    return views


def classify(views):
    """aday görünümler: (yön, ayna?) → özellik"""
    c = {}
    for d, v in enumerate(views):
        c[(d, False)] = feat(v)
        if d in (1, 2, 3):
            c[(d, True)] = feat(flip(v))
    return c


def best(cands, f, dirs=None):
    items = [(dist(f, cf), k) for k, cf in cands.items() if dirs is None or k[0] in dirs]
    return min(items)


def build(species, src_dir, anims=None, pattern="z_{sp}_{n}.png", prefix="z", world_h=None):
    ANIMS = {k: v for k, v in globals()["ANIMS"].items() if anims is None or k in anims}
    sheets = {n: key(os.path.join(src_dir, pattern.format(sp=species, n=n))) for n in ["dirs"] + list(ANIMS)}
    views = refs(sheets["dirs"])
    if len(views) < 5:
        raise RuntimeError(f"{species}: yön sayfasında {len(views)} görünüm")
    cands = classify(views)
    ref_h = np.median([v.shape[0] for v in views])
    s_h = views[0].shape[0]
    world_h = world_h or SPECIES[species]
    texel_h = world_h / TEXEL
    out = {}  # anim → {yön: [kare dizisi (ölçeklenmemiş)]}, ölçek
    for anim, (nrow, nfr, fps, loop) in ANIMS.items():
        a = sheets[anim]
        rows = frames_of(a)
        fr = [[cut(a, b) for b in r] for r in rows]
        fr = [r for r in fr if r]
        dirs = {}
        if anim == "death":
            # ölüm: ilk satır güney, ikinci doğu (aynası daha iyi uyuyorsa aynalanır)
            for di, d in enumerate((0, 2)):
                if di >= len(fr):
                    break
                row = fr[di]
                if d == 2:
                    m, k = best(cands, feat(row[0]), dirs={2})
                    if k[1]:
                        row = [flip(x) for x in row]
                dirs[d] = row
            stand = [r[0].shape[0] for r in fr[:2]]
            scale = s_h / max(1.0, float(np.median(stand)))
        else:
            if len(fr) == nrow:
                # tarif sırası (G, GD, D, KD, K) korunur; yan satırlar sola bakıyorsa aynalanır
                for d, row in enumerate(fr):
                    if d in (1, 2, 3):
                        fs = [feat(x) for x in (row[:3] if anim != "attack" else [row[0], row[-1]])]
                        dn = np.mean([dist(f, cands[(d, False)]) for f in fs])
                        dm = np.mean([dist(f, cands[(d, True)]) for f in fs])
                        row = [flip(x) for x in row] if dm < dn else row
                    dirs[d] = row
            else:
                for row in fr:
                    for x in row:
                        _, (d, m) = best(cands, feat(x))
                        dirs.setdefault(d, []).append(flip(x) if m else x)
            allh = [x.shape[0] for d in (0, 4) for x in dirs.get(d, [])] or [x.shape[0] for v in dirs.values() for x in v]
            scale = ref_h / max(1.0, float(np.median(allh))) * (s_h / ref_h)
        # eksik yönler komşudan
        need = (0, 2) if anim == "death" else range(5)
        for d in need:
            if d not in dirs or not dirs[d]:
                for alt in {0: (1, 4, 2), 1: (0, 2, 3), 2: (1, 3, 0), 3: (2, 4, 1), 4: (3, 0, 2)}[d]:
                    if dirs.get(alt):
                        dirs[d] = dirs[alt]
                        break
        out[anim] = (dirs, scale, fps, loop)
    # doku: ölçek → doku pikseli (yön sayfasının güney boyu = texel_h)
    k_tex = texel_h / s_h
    items = []
    table = {}
    for anim, (dirs, scale, fps, loop) in out.items():
        n = max(len(v) for v in dirs.values())
        if anim != "death":
            n = min(n, 3 if anim == "hurt" else 8)
        table[anim] = [n, fps, loop]
        for d, frames in dirs.items():
            for f in range(n):
                x = frames[min(f, len(frames) - 1)] if f < len(frames) or anim in ("death", "attack", "hurt") else frames[f % len(frames)]
                z = scale * k_tex
                h, w = x.shape[:2]
                im = to_img(x).resize((max(1, round(w * z)), max(1, round(h * z))), Image.BOX)
                q = np.asarray(im).copy()
                q[..., 3] = np.where(q[..., 3] >= 110, 255, 0)
                q = crop(q)
                al = q[..., 3] > 0
                ys, xs = np.nonzero(al)
                ax = float(xs.mean()) if len(xs) else q.shape[1] / 2
                if anim == "death":
                    ax = q.shape[1] / 2
                items.append((f"{prefix}/{species}/{anim}_{d}_{f}", to_img(q), ax, q.shape[0] - 1.0, TEXEL))
    return items, table


def main(root, only=None):
    A = Atlas()
    an = A.m["anims"]
    for d in sorted(glob.glob(os.path.join(root, "*"))):
        for f in sorted(glob.glob(os.path.join(d, "z_*_dirs.png"))):
            sp = os.path.basename(f)[2:-9]
            if sp not in SPECIES or (only and sp not in only):
                continue
            items, table = build(sp, d)
            A.patch(f"zombie_{sp}", drop=lambda k, sp=sp: k.startswith(f"z/{sp}/"), add=items)
            an[f"z/{sp}"] = table
            print(sp, table, len(items))
    A.save()


if __name__ == "__main__":
    main(sys.argv[1], set(sys.argv[2:]) or None)
