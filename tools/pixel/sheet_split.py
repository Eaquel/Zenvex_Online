"""Yapay zekâ ızgara tablosunu (ör. 4×4 eşya simgesi) hücrelere böler.

ai_clean ile gerçek piksel ızgarasına indirilmiş ve arka planı silinmiş görselde, alfa izdüşümündeki boşluk bantlarından
satır/sütun sınırları bulunur (yapay zekâ hücreleri tam hizalamaz; sabit ızgara yerine boşluk aranır).
Her hücre kırpılır; hücrede birden çok parça varsa (ör. sandığın etrafına saçılmış aletler) hepsi birlikte kalır.

python3 tools/pixel/sheet_split.py giriş.jpg çıkış_dizini ad1,ad2,... [satır sütun] [arka plan] [piksel boyu|-] [yeşil kalacaklar]
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ai_clean import clean  # noqa: E402


def bands(profile, n):
    """İzdüşümdeki dolu bölgeleri n banda indirir: en geniş n-1 boşluktan kesilir."""
    filled = profile > 0
    idx = np.where(filled)[0]
    if len(idx) == 0:
        return []
    gaps = []
    run = None
    for i in range(idx[0], idx[-1] + 1):
        if not filled[i]:
            run = (run[0], i) if run else (i, i)
        elif run:
            gaps.append(run)
            run = None
    gaps.sort(key=lambda g: g[1] - g[0], reverse=True)
    cuts = sorted((g[0] + g[1]) // 2 for g in gaps[: n - 1])
    edges = [idx[0]] + cuts + [idx[-1] + 1]
    return [(edges[i], edges[i + 1]) for i in range(len(edges) - 1)]


def despill(c, keep_green=False):
    """Kapalı boşluklarda kalan yeşil arka plan ve kenardaki yeşile kaymış hâle (sızıntı) temizliği.
    keep_green: gerçekten yeşil olan eşyalar (zehir şişesi, devre kartı) — yalnızca dış kenar hâlesi silinir."""
    r, g, b = (c[..., i].astype(int) for i in range(3))
    op = c[..., 3] > 0
    if not keep_green:
        c[op & (g > r + 50) & (g > b + 50) & (g > 120), 3] = 0
    for _ in range(2):
        op = c[..., 3] > 0
        out = np.zeros_like(op)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            out |= ~np.roll(np.roll(op, dy, 0), dx, 1)
        halo = op & out & (g > np.maximum(r, b) + 25)
        c[halo, 3] = 0
    return c


def split(src, out_dir, names, rows=4, cols=4, bg="auto", px=None, keep_green=()):
    os.makedirs(out_dir, exist_ok=True)
    tmp = os.path.join(out_dir, "_sheet.png")
    clean(src, tmp, bg, px)
    a = np.asarray(Image.open(tmp))
    alpha = a[..., 3] > 0
    rb = bands(alpha.sum(1), rows)
    out = []
    for r, (y0, y1) in enumerate(rb):
        cb = bands(alpha[y0:y1].sum(0), cols)
        for c, (x0, x1) in enumerate(cb):
            k = r * cols + c
            if k >= len(names):
                continue
            cell = Image.fromarray(despill(a[y0:y1, x0:x1].copy(), names[k] in keep_green))
            bb = cell.getbbox()
            if bb is None:
                continue
            cell = cell.crop(bb)
            p = os.path.join(out_dir, names[k] + ".png")
            cell.save(p)
            out.append((names[k], cell.size))
    os.remove(tmp)
    return out


if __name__ == "__main__":
    rows = int(sys.argv[4]) if len(sys.argv) > 4 else 4
    cols = int(sys.argv[5]) if len(sys.argv) > 5 else 4
    bg = sys.argv[6] if len(sys.argv) > 6 else "auto"
    px = float(sys.argv[7]) if len(sys.argv) > 7 and sys.argv[7] != "-" else None
    keep = sys.argv[8].split(",") if len(sys.argv) > 8 else ()
    for n, s in split(sys.argv[1], sys.argv[2], sys.argv[3].split(","), rows, cols, bg, px, keep):
        print(n, s)
