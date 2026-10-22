"""Yapay zekâ efekt sayfalarını (siyah zemin, kutulu ya da kutusuz) piksel karelere ayırır.

- Kareler: 'grid' (eşit sütun×satır) ya da 'boxes' (koyu kutular; üst/alt kenara değen dış alan atlanır, kutu çizgisi
  içe doğru kırpılır).
- Piksel ızgarası ai_clean ile bulunur; her hücre ortanca renge indirilir (gerçek piksel boyu).
- Saydamlık: 'add' (ışık efektleri) → alfa = en parlak kanal, renk ön-çarpımdan geri açılır (toplamalı çizimde birebir);
  'smoke' (toz) → alfa parlaklıktan, koyu kenarlar yarı saydam; 'magenta' → anahtar renk silinir.
- Tüm kareler ortak sınır kutusuna kırpılır (kareler arası kayma olmaz).

python3 tools/pixel/fx_sheet.py [ad|all]   → art/v7/fx/<ad>/<i>.png + meta.json
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ai_clean import fit_offset, pixel_size  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "art", "v7", "fx")
UP = os.path.join(OUT, "src")

# ad: (kaynak, kesim, saydamlık, fps, döngü, blend, çapa (x, y oranı), [kare sayısı])
SHEETS = {
    "levelup": ("levelup.jpg", ("grid", 8, 1), "add", 12, False, "add", (0.5, 0.86)),
    "fire_ring": ("fire_ring.jpg", ("boxes",), "add", 10, True, "add", (0.5, 0.74)),
    "wind_ring": ("wind_ring.jpg", ("boxes",), "add", 12, True, "add", (0.5, 0.6)),
    "spark": ("spark.jpg", ("grid", 4, 1), "add", 20, False, "add", (0.5, 0.5)),
    "slash": ("slash.jpg", ("boxes",), "add", 22, False, "add", (0.42, 0.5)),
    "bolt": ("bolt.jpg", ("cells", [(25, 184, 245, 572), (255, 184, 472, 572), (483, 184, 698, 572), (709, 184, 924, 572), (934, 184, 1150, 572), (1158, 184, 1376, 572)]), "add", 14, False, "add", (0.5, 0.97)),
    "sparkle": ("sparkle.jpg", ("boxes",), "add", 12, False, "add", (0.5, 0.5)),
    "dust": ("dust.jpg", ("grid", 5, 1), "smoke", 14, False, "normal", (0.5, 0.62)),
    "toxic": ("toxic.jpg", ("grid", 3, 2), "magenta", 6, True, "normal", (0.5, 0.55)),
    # V8 (art/v8u): beyaz zeminli namlu ateşi/mermi izi, siyah zeminli kalkan, ele geçirme halkası, ıska, iniş tozu,
    # ışınlanma (büyücüsüz kareler seçilir), yüzeye göre teker tozu (tekerlek silinir, yalnız toz kalır)
    "muzzle": ("muzzle.jpg", ("grid", 4, 1), "white", 24, False, "normal", (0.12, 0.5)),
    "tracer": ("tracer.jpg", ("grid", 1, 1), "white", 1, True, "normal", (0.0, 0.5)),
    "shield": ("shield.jpg", ("grid", 6, 1), "add70", 10, True, "add", (0.5, 0.6)),
    "capture": ("capture.jpg", ("grid", 6, 1), "add", 6, False, "add", (0.5, 0.5)),
    "miss": ("miss.jpg", ("grid", 4, 1), "keyblack", 16, False, "normal", (0.5, 0.8)),
    "heli_dust": ("heli_dust.jpg", ("grid", 6, 1), "keyblack", 12, False, "normal", (0.5, 0.5)),
    "teleport": ("teleport.jpg", ("grid", 8, 2), "add", 14, False, "add", (0.5, 0.9), [2, 3, 4, 13, 14, 15]),
    "road_sand": ("road_sand.jpg", ("grid", 5, 1), "dustonly", 14, False, "normal", (0.5, 0.85)),
    "road_snow": ("road_snow.jpg", ("grid", 5, 1), "dustonly", 14, False, "normal", (0.5, 0.85)),
    "road_swamp": ("road_swamp.jpg", ("grid", 5, 1), "dustonly", 14, False, "normal", (0.5, 0.85)),
    "road_dirt": ("road_dirt.jpg", ("grid", 5, 1), "dustonly", 14, False, "normal", (0.5, 0.85)),
}


def boxes(a):
    lum = a.mean(-1)
    dark = lum < 40
    lab, n = ndimage.label(dark)
    H, W = dark.shape
    objs = ndimage.find_objects(lab)
    cand = []
    for i, sl in enumerate(objs):
        ys, xs = sl
        if ys.start == 0 or ys.stop == H:
            continue
        area = (ys.stop - ys.start) * (xs.stop - xs.start)
        if area < 8000:
            continue
        cand.append((xs.start, ys.start, xs.stop, ys.stop))
    # iç içe (halka içindeki koyu boşluk) olanları at
    out = [b for b in cand if not any(o != b and o[0] <= b[0] and o[1] <= b[1] and o[2] >= b[2] and o[3] >= b[3] for o in cand)]
    out.sort()
    m = 6
    return [(x0 + m, y0 + m, x1 - m, y1 - m) for x0, y0, x1, y1 in out]


def native(a, s, ox, oy):
    H, W, _ = a.shape
    nw, nh = int((W - ox) // s), int((H - oy) // s)
    out = np.zeros((nh, nw, 3))
    for j in range(nh):
        y0, y1 = int(round(oy + j * s + 1)), int(round(oy + (j + 1) * s - 1))
        for i in range(nw):
            x0, x1 = int(round(ox + i * s + 1)), int(round(ox + (i + 1) * s - 1))
            out[j, i] = np.median(a[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1)].reshape(-1, 3), 0)
    return out


def build(name):
    src, cut, mode, fps, loop, blend, anc, *pick = SHEETS[name]
    a = np.asarray(Image.open(os.path.join(UP, src)).convert("RGB")).astype(float)
    H, W, _ = a.shape
    if cut[0] == "grid":
        cols, rows = cut[1], cut[2]
        cells = [(int(c * W / cols), int(r * H / rows), int((c + 1) * W / cols), int((r + 1) * H / rows))
                 for r in range(rows) for c in range(cols)]
    elif cut[0] == "cells":
        cells = [(x0 + 6, y0 + 6, x1 - 6, y1 - 4) for x0, y0, x1, y1 in cut[1]]
    else:
        cells = boxes(a)
    if pick:
        cells = [cells[i] for i in pick[0]]
    # kutu dışını (beyaz sayfa / çerçeve) karart, sonra piksel ızgarası
    work = a.copy()
    if mode == "white":
        work = 255 - work  # beyaz zemin: ızgara bulmak için ters çevir (renk aşağıda kaynaktan)
    if cut[0] in ("boxes", "cells"):
        keep = np.zeros((H, W), bool)
        for x0, y0, x1, y1 in cells:
            keep[y0:y1, x0:x1] = True
        work[~keep] = 0
    s = pixel_size(work)
    if s < 3.5:  # yarı harmonik
        s *= 2
    ox, oy = fit_offset(work, s)
    nat = native(work, s, ox, oy)
    frames = []
    for x0, y0, x1, y1 in cells:
        X0, Y0 = int(np.ceil((x0 - ox) / s)), int(np.ceil((y0 - oy) / s))
        X1, Y1 = int((x1 - ox) // s), int((y1 - oy) // s)
        c = nat[Y0:Y1, X0:X1]
        if mode == "white":
            c = 255 - c
        r, g, b = c[..., 0], c[..., 1], c[..., 2]
        if mode == "white":
            al = np.where(c.min(-1) > 228, 0, 255)
            col = c
        elif mode == "keyblack":
            al = np.where(c.max(-1) < 22, 0, 255)
            col = c
        elif mode == "dustonly":
            # tekerlek (gri/koyu, doygunluksuz) ve kenar karası silinir; kum/kar/çamur/toprak kalır
            lum = c @ np.array([0.3, 0.59, 0.11])
            sat = c.max(-1) - c.min(-1)
            wheel = ((sat < 20) & (lum < 160)) | (lum < 28)
            al = np.where(wheel, 0, 255)
            col = c
        elif mode == "add70":
            # yalnız parlak kalkan ışıltısı; içindeki koyu figür atılır
            al = c.max(-1)
            al = np.where(al < 95, 0, al)
            col = np.where(al[..., None] > 0, c * 255.0 / np.maximum(al[..., None], 1), 0)
        elif mode == "add":
            al = c.max(-1)
            al = np.where(al < 14, 0, al)
            col = np.where(al[..., None] > 0, c * 255.0 / np.maximum(al[..., None], 1), 0)
        elif mode == "smoke":
            lum = c @ np.array([0.3, 0.59, 0.11])
            al = np.where(lum < 16, 0, np.clip((lum - 10) * 2.4, 0, 255))
            col = np.where(al[..., None] > 0, np.clip(c * 255.0 / np.maximum(al[..., None], 1), 0, 255), 0)
            # toz rengini çok açmasın: parlaklığı kaynağa yakın tut
            col = np.minimum(col, np.maximum(c, 1) * 2.0)
        else:  # magenta
            key = (r > 150) & (b > 150) & (g < 140)
            spill = (r > g + 40) & (b > g + 40)
            al = np.where(key | spill, 0, 255)
            col = c
        f = np.dstack([np.clip(col, 0, 255), al]).astype(np.uint8)
        if name.startswith("road_"):
            # tekerlek dairesi (hücre ortası, görsel yüksekliğinin ~%51'i, yarıçap ~125 kaynak px) tamamen boş
            hh, ww = f.shape[:2]
            yy, xx = np.mgrid[0:hh, 0:ww]
            cy = (390 - (y0 - oy)) / s if False else (0.508 * H - oy) / s - Y0
            disc = (yy - cy) ** 2 + (xx - ww / 2) ** 2 < (128 / s) ** 2
            f[disc, 3] = 0
        if name == "teleport":
            # kenarlardaki büyücü asası kalıntısı
            w = f.shape[1]
            f[:, :int(w * 0.22), 3] = 0
            f[:, int(w * 0.78):, 3] = 0
        frames.append(f)
    # ortak sınır kutusu
    hh = min(f.shape[0] for f in frames)
    ww = min(f.shape[1] for f in frames)
    frames = [f[:hh, :ww] for f in frames]
    m = np.zeros((hh, ww), bool)
    for f in frames:
        m |= f[..., 3] > 0
    ys, xs = np.nonzero(m)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    od = os.path.join(OUT, name)
    os.makedirs(od, exist_ok=True)
    for fn in os.listdir(od):
        os.remove(os.path.join(od, fn))
    for i, f in enumerate(frames):
        Image.fromarray(f[y0:y1, x0:x1]).save(os.path.join(od, f"{i}.png"))
    meta = {"frames": len(frames), "fps": fps, "loop": loop, "blend": blend, "scale": 1.0, "anchor": list(anc)}
    json.dump(meta, open(os.path.join(od, "meta.json"), "w"))
    print(name, f"piksel {s:.2f}", len(frames), "kare", (x1 - x0, y1 - y0))


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    for nm in (SHEETS if which == "all" else which.split(",")):
        build(nm)
