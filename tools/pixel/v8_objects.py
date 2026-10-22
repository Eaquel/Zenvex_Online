"""V8 kullanıcı görsellerini (art/v8u/*.jpg) oyun parçalarına ayırır → art/v8/<grup>/<ad>.png

Her görsel ai_clean ile gerçek piksel ızgarasına indirilir, arka planı (yeşil / macenta) silinir; alfa üzerindeki
bağlı bileşenler (yakın parçalar birleştirilerek) satır satır, soldan sağa sıralanıp verilen adlarla kırpılır.
Yinelenen ikinci satırlar (aynı seti iki kez çizen görseller) atılır.

python3 tools/pixel/v8_objects.py [ad,...]
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from ai_clean import clean  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
SRC = os.path.join(ROOT, "art", "v8u")
OUT = os.path.join(ROOT, "art", "v8")
TMP = os.environ.get("ZX_TMP", "/tmp/zx_v8_clean")

# ham görsel: (arka plan, grup, [adlar], satır sınırı (None = hepsi), birleştirme yarıçapı)
RECIPES = {
    "hud_pass": ("green", "ui", ["btn_pass"], None, 3),
    "hud_enter": ("green", "ui", ["btn_drive"], None, 3),
    "hud_exit": ("green", "ui", ["btn_exit"], None, 3),
    "u1_markers": ("green", "ui", ["map_gov", "map_clan", "map_war", "map_unknown"], 1, 2),
    "u2_bags": ("green", "ui", ["bagtab_0", "bagtab_1", "bagtab_2", "bagtab_3"], 1, 2),
    "u3_frame": ("green", "ui", ["panel_base"], None, 3),
    "s7_equip": ("green", "ui", ["slot_helmet", "slot_jacket", "slot_pants", "slot_boots", "slot_bag", "slot_ring", "slot_neck",
                                 "slot_neck2"], None, 2),
    "v1_moto": ("green", "veh", ["moto"], None, 3),
    "v2_car": ("green", "veh", ["car"], None, 3),
    "v3_wheel": ("green", "veh", ["wheel"], None, 3),
    "a1_soldier": ("green", "npc", ["soldier_front", "soldier_side", "soldier_back"], None, 3),
    "a2_sniper": ("green", "npc", ["sniper_front", "sniper_side", "sniper_back"], ("cuts", [0, 1], [[0, 0.415, 0.595, 1]]), 3),
    "a3_guns": ("green", "npc", ["gun_m4", "gun_sniper"], None, 3),
    "a4_soldier_fall": ("green", "npc", ["soldier_fall"], None, 30),
    "b1_wizard": ("green", "npc", ["wizard"], None, 4),
    "h1_heli": ("green", "props", ["heli"], None, 4),
    "u1_bunker": ("green", "props", ["bunker"], None, 4),
    "u2_tower": ("green", "props", ["watchtower"], None, 4),
    "u4_helipad": ("green", "props", ["helipad"], None, 4),
    "u6_supply": ("green", "props", ["supply_crate"], None, 4),
    "r5_hangar": ("green", "props", ["hangar"], None, 4),
    "k1_chests": ("green", "props", ["chest_wood", "chest_iron", "chest_mil"], None, 2),
    "k2_chests_open": ("green", "props", ["chest_wood_open", "chest_iron_open", "chest_mil_open"], ("cuts", [0, 1], [[0, 0.267, 0.57, 1]]), 2),
    "k3_furniture": ("green", "props", ["bed", "stove", "fence_wood", "gate", "weapon_rack", "rain_barrel"], ("cuts", [0, 0.47, 1], [[0, 0.36, 0.64, 1], [0, 0.41, 0.79, 1]]), 5),
    "r1_containers": ("green", "props", ["harbor_containers"], None, 6),
    "r2_tree": ("green", "props", ["burnt_tree", "burnt_stump"], None, 3),
    "r3_wreck": ("green", "props", ["desert_wreck"], None, 6),
    "r4_shack": ("magenta", "props", ["swamp_shack"], None, 6),
}


# yarı harmoniğe kayan (2.93) görseller: hepsi aynı piksel boyunda kalsın
FORCE_S = {"r5_hangar", "r1_containers", "r2_tree", "r3_wreck", "r4_shack"}


def cleaned(name, bg):
    os.makedirs(TMP, exist_ok=True)
    p = os.path.join(TMP, name + ".png")
    if not os.path.exists(p):
        clean(os.path.join(SRC, name + ".jpg"), p, bg, 5.86 if name in FORCE_S else None)
    return Image.open(p).convert("RGBA")


def components(a, radius):
    """Alfa bileşenleri: yakın parçalar (radius piksel) birleşir; çok küçük kırıntılar en yakın büyük parçaya katılır."""
    solid = a[..., 3] > 0
    grown = ndimage.binary_dilation(solid, iterations=radius) if radius > 0 else solid
    lab, n = ndimage.label(grown)
    lab = np.where(solid, lab, 0)
    boxes = []
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        if sl is None:
            continue
        area = int((lab[sl] == i).sum())
        boxes.append([sl[0].start, sl[1].start, sl[0].stop, sl[1].stop, area, i])
    if not boxes:
        return []
    big = max(b[4] for b in boxes)
    keep = [b for b in boxes if b[4] >= big * 0.04]
    return keep


def order(boxes):
    """Satırlara grupla (dikey merkez yakınlığı), satır içinde soldan sağa."""
    boxes = sorted(boxes, key=lambda b: (b[0] + b[2]) / 2)
    rows = []
    for b in boxes:
        cy = (b[0] + b[2]) / 2
        h = b[2] - b[0]
        if rows and abs(cy - rows[-1][0]) < max(h, rows[-1][1]) * 0.5:
            rows[-1][2].append(b)
        else:
            rows.append([cy, h, [b]])
    return [sorted(r[2], key=lambda b: b[1]) for r in rows]


def build(name):
    bg, group, names, max_rows, radius = RECIPES[name]
    im = cleaned(name, bg)
    a = np.asarray(im)
    if isinstance(max_rows, tuple):
        # elle verilen kesimler (parçalar birbirine değiyorsa): her hücrede yalnız ana parça (ve ona yakın olanlar)
        _, rcuts, ccuts = max_rows
        H, W = a.shape[:2]
        flat = []
        masks = []
        for r in range(len(rcuts) - 1):
            for c in range(len(ccuts[r]) - 1):
                y0, y1 = int(rcuts[r] * H), int(rcuts[r + 1] * H)
                x0, x1 = int(ccuts[r][c] * W), int(ccuts[r][c + 1] * W)
                cell = np.zeros(a.shape[:2], bool)
                cell[y0:y1, x0:x1] = a[y0:y1, x0:x1, 3] > 0
                grown = ndimage.binary_dilation(cell, iterations=radius)
                lab, n = ndimage.label(grown)
                lab = np.where(cell, lab, 0)
                areas = ndimage.sum(cell, lab, range(1, n + 1))
                keep = np.isin(lab, [i + 1 for i, ar in enumerate(areas) if ar >= areas.max() * 0.12])
                ys, xs = np.nonzero(keep)
                flat.append([ys.min(), xs.min(), ys.max() + 1, xs.max() + 1, len(ys), 0])
                masks.append(keep)
        rows = [flat]
        max_rows = None
    else:
        rows = order(components(a, radius))
    if max_rows:
        rows = rows[:max_rows]
    flat = [b for r in rows for b in r]
    if len(flat) != len(names):
        # beklenenden fazla parça: en büyük len(names) parçayı al, sırayı koru
        flat2 = sorted(flat, key=lambda b: -b[4])[:len(names)]
        flat = [b for b in flat if b in flat2]
        if len(flat) != len(names):
            print(f"! {name}: {len(flat)} parça bulundu, {len(names)} bekleniyordu")
    od = os.path.join(OUT, group)
    os.makedirs(od, exist_ok=True)
    for k, (nm, (y0, x0, y1, x1, _, _)) in enumerate(zip(names, flat)):
        if isinstance(RECIPES[name][3], tuple):
            # hücrede tutulmayan (komşuya ait) pikselleri sil
            src = im.copy()
            src.putalpha(Image.fromarray(np.where(masks[k], np.asarray(im)[..., 3], 0).astype(np.uint8)))
            crop = src.crop((x0, y0, x1, y1))
        else:
            crop = im.crop((x0, y0, x1, y1))
        crop.save(os.path.join(od, nm + ".png"))
        print(f"{group}/{nm}: {crop.size}")


if __name__ == "__main__":
    which = sys.argv[1].split(",") if len(sys.argv) > 1 else list(RECIPES)
    for w in which:
        build(w)
