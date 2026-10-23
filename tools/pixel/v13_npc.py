"""V13 Gezgin Tüccar Marco (n/marco/{idle,walk}_<yön>_<kare>), kamyoneti (x8/truck/0-7) ve eksik eşya simgeleri (i/<eşya>).

python3 tools/pixel/v13_npc.py <V13_Yaratik_Esya_NPC_Paketi dizini>
"""
import glob
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
from atlas_patch import Atlas  # noqa: E402
from creature_import import build  # noqa: E402
from cutout import crop, flip, key, parts, pixelize, to_img  # noqa: E402


def main(src):
    A = Atlas()
    N = os.path.join(src, "3_NPC_Marco_ve_Kamyonet")
    items, table = build("marco", N, anims={"idle", "walk"}, pattern="npc_{sp}_{n}.png", prefix="n", world_h=118)
    A.patch("npc_marco", drop=lambda k: k.startswith("n/marco/"), add=items)
    A.m["anims"]["n/marco"] = table
    print("marco", table, len(items))
    # kamyonet: 2 satır; 0 ön, 1 sağ-ön, 2 sağ yan, 3 sol-ön, 4-5 arka, 6 sol-ön, 7 sol yan, 8 sol-arka
    t = key(os.path.join(N, "x8_truck.png"))
    fr = [crop(t[y0:y1, x0:x1]) for x0, y0, x1, y1 in parts(t, gap=6)]
    by_dir = [fr[0], fr[1], fr[2], flip(fr[8]), fr[4], fr[8], fr[7], fr[3]]
    ps = [crop(pixelize(f, 2.2)) for f in by_dir]
    k = 320.0 / ps[2].shape[1]
    veh = [(f"x8/truck/{d}", to_img(p), p.shape[1] / 2, p.shape[0] * 0.9, k) for d, p in enumerate(ps)]
    A.patch("vehicles", drop=lambda k: k.startswith("x8/truck/"), add=veh)
    # eşya simgeleri (V11 alet/parça simgelerinin yerine; etkinlik silahları yeni)
    icons = []
    for f in sorted(glob.glob(os.path.join(src, "2_Eksik_Esya_Simgeleri", "i_*.png"))):
        a = crop(key(f))
        p = crop(pixelize(a, max(a.shape[:2]) / 72.0))
        icons.append(("i/" + os.path.basename(f)[2:-4], to_img(p), p.shape[1] / 2, p.shape[0] - 1, 40.0 / max(p.shape[:2])))
    A.patch("items", add=icons)
    print("simge", len(icons))
    A.save()


if __name__ == "__main__":
    main(sys.argv[1])
