"""V19: görsellerin altına gömülü "zemin plakalarını" (kum, kar, kül, asfalt, yosun tabanı) yumuşak temas gölgesine çevirir.

Bir görsel hangi bölgeye konursa konsun altındaki plaka o bölgenin zemininden farklı renkteydi (çölde arabanın altında
beyaz kum, kaktüsün altında kare kum...). Plaka: görselin alt kısmında, dış kenardan başlayıp plakanın rengine yakın
piksellerle doldurulan bölge. Bu pikseller siyah, yarı saydam gölgeye döner (kenara doğru söner); nesne aynen kalır.

python3 tools/assetgen/v19_plates.py [önizleme.png]
"""
import os
import sys
from collections import deque

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from atlas_patch import Atlas  # noqa: E402

# Nesnesi plakayla aynı renkte olanlar (geyik iskeleti, mantarlı kütük, askerî çadır, cip enkazları) listede yok:
# plaka silinirken nesnenin kendisi de siliniyordu; bunlar kendi bölgelerinin zeminine zaten uyuyor.
# (görsel, plakanın görselin altından itibaren en çok yüksekliği (oran), renk eşiği, plaka rengi (None: kenardan ölçülür))
SNOW = (232, 238, 244)
PLATES = {
    "snow_pine": (0.24, 60, SNOW), "anchor_snow": (0.55, 60, SNOW), "snow_barrels": (0.35, 50, SNOW),
    "harbor_containers": (0.35, 60, SNOW), "smolder_log": (0.5, 60, None), "charred_log": (0.45, 35, None),
    "ember_rocks": (0.45, 55, None), "cactus": (0.28, 90, None), "cow_skull": (0.5, 70, None),
    "road_sign": (0.2, 90, None), "cone_pile": (0.45, 70, None), "gas_can": (0.4, 70, None), "moss_tree": (0.22, 60, None),
    "rowboat": (0.45, 35, None), "reeds": (0.35, 55, None), 
    "radar_dish": (0.32, 50, None), "ammo_stack": (0.4, 45, None), 
    "street_lamp": (0.16, 80, None), "burnt_car": (0.5, 65, None), "desert_wreck": (0.5, 95, None), "burnt_pine": (0.2, 60, None),
}


def to_shadow(im, frac, thr, color=None):
    a = np.asarray(im.convert("RGBA")).astype(np.float32)
    h, w = a.shape[:2]
    al = a[..., 3] > 30
    ys, xs = np.nonzero(al)
    if len(ys) == 0:
        return im, 0
    top, bot = ys.min(), ys.max()
    lim = bot - int((bot - top + 1) * frac)
    # plaka rengi: alt şeridin dış kenar pikselleri (saydamlığa komşu)
    edge = np.zeros_like(al)
    edge[1:-1, 1:-1] = al[1:-1, 1:-1] & ~(al[:-2, 1:-1] & al[2:, 1:-1] & al[1:-1, :-2] & al[1:-1, 2:])
    edge[:lim] = False
    seeds = np.argwhere(edge)
    if len(seeds) < 4:
        return im, 0
    plate = np.array(color, dtype=np.float32) if color is not None else np.median(a[edge][:, :3], axis=0)
    rgb = a[..., :3]
    near = (np.linalg.norm(rgb - plate, axis=2) < thr) & al
    near[:lim] = False
    # dış kenardan dolgu (nesnenin içindeki benzer renk adalarına sızmaz)
    seen = np.zeros_like(al)
    q = deque()
    for y, x in seeds:
        if near[y, x]:
            seen[y, x] = True
            q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not seen[ny, nx] and near[ny, nx]:
                seen[ny, nx] = True
                q.append((ny, nx))
    n = int(seen.sum())
    if n == 0:
        return im, 0
    # plakanın koyu dış çizgisi de gitsin: silinen bölge 2 piksel genişler, ama nesnenin gövdesine (2 piksel içeri
    # aşınmış çekirdeğinin çevresine) girmez
    def grow(mk, r):
        o = mk.copy()
        for _ in range(r):
            g = o.copy()
            g[1:] |= o[:-1]; g[:-1] |= o[1:]; g[:, 1:] |= o[:, :-1]; g[:, :-1] |= o[:, 1:]
            o = g
        return o

    def shrink(mk, r):
        return ~grow(~mk, r)

    core = grow(shrink(al & ~seen, 2), 1)
    band = np.zeros_like(al)
    band[lim:] = True
    seen = seen | (grow(seen, 2) & band & al & ~core)
    n = int(seen.sum())
    # plaka tamamen silinir; nesnenin tabanına küçük, yumuşak elips temas gölgesi (her zeminde aynı doğallıkta)
    out = a.copy()
    out[seen, 3] = 0
    obj = al & ~seen
    oy, ox = np.nonzero(obj)
    if len(oy):
        base = oy.max()
        row = ox[oy >= base - max(2, (base - oy.min()) * 0.12)]
        cx, half = (row.min() + row.max()) / 2.0, max(4.0, (row.max() - row.min()) / 2.0 * 1.15)
        ry = max(2.0, half * 0.32)
        yy, xx = np.mgrid[0:h, 0:w]
        e = ((xx - cx) / half) ** 2 + ((yy - base) / ry) ** 2
        sh = np.clip(1.0 - e, 0.0, 1.0) ** 1.4 * 0.38 * 255.0
        under = out[..., 3] < sh
        out[under, 0:3] = 0
        out[under, 3] = sh[under]
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA"), n


def main():
    A = Atlas()
    sp = A.m["sprites"]
    groups = {i: g for g, ids in A.m["groups"].items() for i in ids}
    adds = {}
    prev = []
    for name, (frac, thr, color) in PLATES.items():
        key = "s/" + name
        if key not in sp:
            print("yok:", key)
            continue
        v = sp[key]
        p, x, y, w, h, ax, ay = v[:7]
        k = v[7] if len(v) > 7 else 1.0
        src = A.page(p).crop((x, y, x + w, y + h))
        im, n = to_shadow(src, frac, thr, color)
        print(f"{name}: {n} piksel gölgeye döndü")
        adds.setdefault(groups[p], []).append((key, im, ax, ay, k))
        prev.append((src, im))
    if not os.environ.get("DRY"):
        for g, items in adds.items():
            A.patch(g, add=items)
        A.save()
    if len(sys.argv) > 1:
        S = 170
        sheet = Image.new("RGBA", (S * 6, S * ((len(prev) + 2) // 3)), (214, 140, 72, 255))
        for i, (a, b) in enumerate(prev):
            for j, im in enumerate((a, b)):
                t = im.copy()
                t.thumbnail((S - 8, S - 8))
                sheet.alpha_composite(t, (((i % 3) * 2 + j) * S + 4, (i // 3) * S + 4))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
