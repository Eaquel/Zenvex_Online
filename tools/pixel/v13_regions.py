"""V13 bölge paketini (V13_8_Bolge_Harita_Paketi) oyuna alır.

  2_Zemin_Dokulari  g_<id>_<main|alt|path|void>.png → assets/v2/ground/g_<id>_<rol>.webp (512, dikişsiz)
                     Oyun yalnız bulunulan bölgenin dörtlüsünü yükler (ground::R_MAIN…R_VOID).
  3_Sinir_Susleri   b_<id>.png (2×3)  → s/b_<id>_0..5  (bölge kenarını kapatan büyük parçalar)
  4_Dekor_Sayfalari d_<id>.png (2×4)  → s/d_<id>_0..7  (bölgeye serpilen süsler)
  5_Simge_Yapilar   l_<id>_1/2.png    → s/l_<id>_1/2   (yer işaretleri)
  6_Aldric_Haritasi island_<id>.png, wizard_bg.png → assets/ui (Aldric'in Haritası)
  1_Konsept_Haritalar concept_<id>.png → assets/ui/region_<id>.webp (3:1 bölge kartı afişi)
  7_Esya_Simgeleri  i_workbench.png → i/workbench

python3 tools/pixel/v13_regions.py <paket dizini>
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
from atlas_patch import Atlas  # noqa: E402
from cutout import crop, key, parts, pixelize, to_img  # noqa: E402

ROOT = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "assets")
UI = os.path.join(ROOT, "ui")
GROUND = os.path.join(ROOT, "v2", "ground")
IDS = ["forest", "city", "burnt", "desert", "bswamp", "harbor", "front", "home"]

# sınır parçası genişliği (dünya pikseli; karo 64) ve dekor/yapı boyu: bölge başına elle
BORDER_W = 230.0
DECOR_H = 88.0
LANDMARK_W = {"forest": (300, 330), "city": (380, 360), "burnt": (330, 300), "desert": (380, 360),
              "bswamp": (340, 320), "harbor": (330, 300), "front": (360, 340)}


# ---------------------------------------------------------------- zemin
def seamless(a):
    """512'lik dokuyu dikişsiz yapar: 256 periyodu varsa bir periyot alınıp 2×2 döşenir; yoksa yarım kaydırılmış kopyayla
    kenarlara doğru yumuşak harmanlanır."""
    g = a.mean(-1)
    base = ((g[:, 1:] - g[:, :-1]) ** 2).mean() + 1e-6
    px = ((g[:, 256:] - g[:, :-256]) ** 2).mean() / base
    py = ((g[256:] - g[:-256]) ** 2).mean() / base
    if px < 0.7 and py < 0.7:
        t = a[:256, :256]
        return np.tile(t, (2, 2, 1))
    sh = np.roll(np.roll(a, 256, 0), 256, 1)
    n = a.shape[0]
    d = np.minimum(np.arange(n), n - 1 - np.arange(n)) / (n / 2)  # 0 kenar … 1 orta
    w = np.minimum(d[:, None], d[None, :])
    w = np.clip(w * 2.2, 0, 1)[..., None]
    return a * w + sh * (1 - w)


def grounds(src):
    D = os.path.join(src, "2_Zemin_Dokulari")
    for i in IDS:
        for r in ("main", "alt", "path", "void"):
            fn = os.path.join(D, f"g_{i}_{r}.png")
            if not os.path.exists(fn):
                # kişisel üs: ikinci doku = ana, boşluk = orman boşluğu
                fn = os.path.join(D, f"g_{i}_main.png" if r == "alt" else f"g_forest_{r}.png")
            a = np.asarray(Image.open(fn).convert("RGB").resize((512, 512), Image.LANCZOS)).astype(float)
            out = seamless(a)
            if r == "void":
                out = out * 0.8  # sınırın ötesi biraz daha koyu
            Image.fromarray(out.clip(0, 255).astype(np.uint8)).save(os.path.join(GROUND, f"g_{i}_{r}.webp"), "WEBP", quality=92)
        print("zemin", i)


# ---------------------------------------------------------------- sayfalar
def grid_parts(a, rows, cols):
    """sayfadaki parçalar satır×sütun hücrelerine toplanır (küçük kopuk parçalar komşusuna katılır)"""
    H, W = a.shape[:2]
    cells = {}
    cw, ch = W / cols, H / rows
    boxes = []
    for x0, y0, x1, y1 in parts(a, min_area=150, gap=8):
        # yan yana yapışık çizilmiş nesneler tek parça çıkar: hücre sınırlarından bölünür
        if x1 - x0 > cw * 1.3 or y1 - y0 > ch * 1.3:
            for r in range(rows):
                for c in range(cols):
                    bx0, by0 = max(x0, int(c * cw)), max(y0, int(r * ch))
                    bx1, by1 = min(x1, int((c + 1) * cw)), min(y1, int((r + 1) * ch))
                    if bx1 - bx0 > 20 and by1 - by0 > 20 and (a[by0:by1, bx0:bx1, 3] > 40).sum() > 400:
                        boxes.append((bx0, by0, bx1, by1))
        else:
            boxes.append((x0, y0, x1, y1))
    for x0, y0, x1, y1 in boxes:
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        c = (min(rows - 1, int(cy / ch)), min(cols - 1, int(cx / cw)))
        b = cells.get(c)
        cells[c] = (x0, y0, x1, y1) if b is None else (min(b[0], x0), min(b[1], y0), max(b[2], x1), max(b[3], y1))
    out = []
    for r in range(rows):
        for c in range(cols):
            b = cells.get((r, c))
            if b:
                out.append(crop(a[b[1]:b[3], b[0]:b[2]]))
    return out


def sprite_of(p, s, world_w=None, world_h=None, anchor=(0.5, 0.9)):
    q = crop(pixelize(p, s))
    h, w = q.shape[:2]
    k = world_w / w if world_w else world_h / h
    return to_img(q), w * anchor[0], h * anchor[1], k


def sheets(src, A):
    add = []
    for i in IDS:
        b = key(os.path.join(src, "3_Sinir_Susleri", f"b_{i}.png"))
        for n, p in enumerate(grid_parts(b, 2, 3)):
            img, ax, ay, k = sprite_of(p, 3.0, world_w=BORDER_W)
            add.append((f"s/b_{i}_{n}", img, ax, ay, k))
        d = key(os.path.join(src, "4_Dekor_Sayfalari", f"d_{i}.png"))
        for n, p in enumerate(grid_parts(d, 2, 4)):
            # dekorlar aynı ölçekte çizilmiş: sayfadaki boyları korunur (en büyüğü ~DECOR_H*1.6)
            img, ax, ay, k = sprite_of(p, 3.0, world_h=DECOR_H * max(0.45, p.shape[0] / 300.0))
            add.append((f"s/d_{i}_{n}", img, ax, ay, k))
        if i in LANDMARK_W:
            for n in (1, 2):
                l = crop(key(os.path.join(src, "5_Simge_Yapilar", f"l_{i}_{n}.png")))
                img, ax, ay, k = sprite_of(l, 3.0, world_w=LANDMARK_W[i][n - 1], anchor=(0.5, 0.86))
                add.append((f"s/l_{i}_{n}", img, ax, ay, k))
        print("sayfa", i, len(add))
    A.patch("regions", drop=lambda k: k.startswith(("s/b_", "s/d_", "s/l_")), add=add)


# ---------------------------------------------------------------- arayüz
def ui(src):
    D = os.path.join(src, "6_Aldric_Haritasi_Adalar")
    for i in IDS:
        a = crop(key(os.path.join(D, f"island_{i}.png")))
        a = pixelize(a, max(a.shape[:2]) / 420.0)
        to_img(crop(a)).save(os.path.join(UI, f"island_{i}.png"), optimize=True)
    Image.open(os.path.join(D, "wizard_bg.png")).convert("RGB").save(os.path.join(UI, "wizard_bg.webp"), "WEBP", quality=88)
    old = os.path.join(UI, "wizard_bg.png")
    if os.path.exists(old):
        os.remove(old)
    C = os.path.join(src, "1_Konsept_Haritalar")
    for i in IDS:
        im = Image.open(os.path.join(C, f"concept_{i}.png")).convert("RGB")
        w, h = im.size
        # afiş: haritanın orta şeridi (3:1)
        im.crop((0, int(h * 0.33), w, int(h * 0.33) + w // 3)).resize((900, 300), Image.LANCZOS) \
            .save(os.path.join(UI, f"region_{i}.webp"), "WEBP", quality=88)
    print("arayüz tamam")


def main(src):
    grounds(src)
    ui(src)
    A = Atlas()
    sheets(src, A)
    wb = crop(key(os.path.join(src, "7_Esya_Simgeleri", "i_workbench.png")))
    p = crop(pixelize(wb, max(wb.shape[:2]) / 72.0))
    A.patch("items", add=[("i/workbench", to_img(p), p.shape[1] / 2, p.shape[0] - 1, 40.0 / max(p.shape[:2]))])
    A.save()


if __name__ == "__main__":
    main(sys.argv[1])
