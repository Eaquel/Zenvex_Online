"""Yapay zekâ ile üretilmiş profil çerçevesini oyuna alır: assets/ui/frame_<id>.png

1) ai_clean ile gerçek piksel ızgarasına indirilir, yeşil (#00FF00) arka plan ve orta boşluk şeffaf yapılır,
2) orta boşluk merkezden bulunur ve hizalanır: boşluk = kutunun %80'i (oyundaki resim alanı),
3) tuval her yanda %12.5 taşma payıyla kare kırpılır (taç, alev, diken gibi dışa taşan süsler kesilmez).

Oyun bu görseli avatar kutusunun 1.25 katına ortalayarak çizer; animasyonlu efektler
(dolaşan ışık, alevler, kıvılcım, kan damlası, yarasalar) ve "10/25/50/S1" rozeti üstüne yine kodla çizilir.

python3 tools/pixel/frame_import.py giriş.png lv10|lv25|lv50|season1|blood|iron [piksel_boyu|hd]

"hd": piksel sanat olmayan, ince detaylı (boyalı) çerçeve; ızgaraya indirilmez, yeşil silinir ve 320 px'e küçültülür
(oyun bunu yumuşak ölçekleyerek çizer).
"""
import os
import sys
import tempfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from ai_clean import clean  # noqa: E402

IDS = {"lv10", "lv25", "lv50", "season1", "blood", "iron"}
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "android", "app", "src", "main", "assets", "ui")


def key_hd(src):
    """ince detaylı görsel: yeşil zemin ve yeşile kayan kenar sızıntısı silinir"""
    im = Image.open(src).convert("RGB")
    a = np.asarray(im).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    key = (g > 140) & (g > r + 60) & (g > b + 60)
    spill = ~key & (g > r + 25) & (g > b + 25)
    out = a.copy()
    out[..., 1] = np.where(spill, np.maximum(r, b), g)  # sızıntıda yeşil bastırılır
    alpha = np.where(key, 0, 255)
    return np.dstack([out, alpha]).astype(np.uint8)


def main(src, fid, s=None):
    if fid not in IDS:
        sys.exit(f"bilinmeyen çerçeve: {fid} ({', '.join(sorted(IDS))})")
    hd = s == "hd"
    if hd:
        a = key_hd(src)
    else:
        with tempfile.TemporaryDirectory() as d:
            tmp = os.path.join(d, "f.png")
            clean(src, tmp, "green", float(s) if s else None)
            a = np.asarray(Image.open(tmp).convert("RGBA")).copy()
    # orta boşluk: merkezden şeffaf bölgeyi doldurarak bulunur; çerçevenin dış kenarı boşluk / 0.8 (resim kutunun %80'i)
    H, W = a.shape[:2]
    from collections import deque
    hole = np.zeros((H, W), bool)
    q = deque([(H // 2, W // 2)])
    if a[H // 2, W // 2, 3]:
        sys.exit("orta boşluk bulunamadı: görselin ortası düz #00FF00 olmalı")
    hole[H // 2, W // 2] = True
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            Y, X = y + dy, x + dx
            if 0 <= Y < H and 0 <= X < W and not hole[Y, X] and a[Y, X, 3] == 0:
                hole[Y, X] = True
                q.append((Y, X))
    ys, xs = np.nonzero(hole)
    if xs.min() == 0 or ys.min() == 0 or xs.max() == W - 1 or ys.max() == H - 1:
        sys.exit("orta boşluk dışarıya açık: çerçeve dört kenarda da kapalı olmalı")
    cx, cy = (xs.min() + xs.max() + 1) / 2, (ys.min() + ys.max() + 1) / 2
    inner = ((xs.max() + 1 - xs.min()) + (ys.max() + 1 - ys.min())) / 2
    # tuval: çerçeve (boşluk / 0.8) + her yanda %12.5 taşma payı (taç, alev, diken) → oyunda kutunun 1.25 katı çizilir
    side = int(round(inner / 0.8 * 1.25))
    sq = np.zeros((side, side, 4), np.uint8)
    sx0, sy0 = int(round(cx - side / 2)), int(round(cy - side / 2))
    for y in range(side):
        for x in range(side):
            X, Y = sx0 + x, sy0 + y
            if 0 <= X < W and 0 <= Y < H:
                sq[y, x] = a[Y, X]
    # boşluk her zaman tam şeffaf (yeşil artık kalmasın)
    hy, hx = np.nonzero(hole)
    for y, x in zip(hy - sy0, hx - sx0):
        if 0 <= y < side and 0 <= x < side:
            sq[y, x] = 0
    os.makedirs(OUT, exist_ok=True)
    dst = os.path.join(OUT, "frame_" + fid + ".png")
    img = Image.fromarray(sq, "RGBA")
    if hd and side > 320:
        img = img.resize((320, 320), Image.LANCZOS)
    img.save(dst)
    print(f"{dst}: {img.size[0]}×{img.size[1]}")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
