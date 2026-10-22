"""V10 görselleri (art/v10, art/v7/fx): atlasa girecek parçalar. pack.py (tam paketleme) ve v10_pack.py (atlas yaması)
aynı tanımları kullanır.

Her toplayıcı [(anahtar, Image, ax, ay, k)] döndürür; k: yerel piksel → dünya pikseli.
"""
import json
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
V10 = os.path.join(ROOT, "art", "v10")
FX7 = os.path.join(ROOT, "art", "v7", "fx")
PX_PER_M = 62.5

# kahraman: elle çizilmiş pozlardan kareler (tools/pixel/hero_poses.py); bu animasyonların eski kareleri atılır
HERO_REPLACED = ("hurt", "knockdown", "getup", "death")

# askerler (yan görünüm, sağa bakar): boy 1.8 m ayakta duruştan; çapa ayak ortası, düşen/yatan için gövde ortası
NPC_STAND_H = {"soldier": "soldier_walk1", "sniper": "sniper_scan"}
NPC_CENTER = ("soldier_falling", "soldier_dead", "sniper_dead", "sniper_prone")

# bölge dekorları: (genişlik m, çapa x, y oranı)
PROPS = {
    "snow_pine": (3.0, 0.5, 0.97), "ice_boulder": (1.9, 0.5, 0.86), "frozen_boat": (4.4, 0.5, 0.84),
    "anchor_snow": (1.9, 0.5, 0.84), "snow_barrels": (1.5, 0.5, 0.9),
    "burnt_pine": (2.4, 0.5, 0.97), "smolder_log": (2.0, 0.5, 0.82), "charred_log": (2.6, 0.5, 0.82),
    "deer_skeleton": (2.0, 0.5, 0.78), "ember_rocks": (1.7, 0.5, 0.8),
    "cactus": (1.8, 0.5, 0.93), "cow_skull": (1.3, 0.5, 0.8), "road_sign": (1.2, 0.5, 0.96), "cone_pile": (1.6, 0.5, 0.85),
    "gas_can": (1.0, 0.5, 0.9),
    "moss_tree": (3.6, 0.5, 0.95), "mushroom_log": (2.2, 0.5, 0.8), "rowboat": (2.9, 0.5, 0.8), "reeds": (1.5, 0.5, 0.9),
    "toxic_puddle": (2.3, 0.5, 0.6),
    "mil_tent": (3.6, 0.5, 0.9), "jeep_wreck": (3.2, 0.5, 0.86), "radar_dish": (2.4, 0.5, 0.92), "ammo_stack": (1.9, 0.5, 0.9),
    "jeep_wreck2": (3.4, 0.5, 0.86), "sandbag_nest": (2.8, 0.5, 0.85),
    "street_lamp": (1.7, 0.5, 0.95), "bus_stop": (3.8, 0.5, 0.86), "burnt_car": (4.4, 0.5, 0.82), "dumpster": (2.2, 0.5, 0.88),
    "billboard": (3.8, 0.5, 0.86),
    "tent_patched": (3.2, 0.5, 0.9), "tent_olive": (3.6, 0.5, 0.9), "tent_tarp": (3.2, 0.5, 0.9),
}

# efektler (tools/pixel/fx_sheet.py → art/v7/fx): aynı adlı eski 3D efektlerin yerine geçer
FX = ("blood", "blood_pool", "spark", "muzzle", "fire", "smoke", "shockwave", "explosion", "crit", "heal", "frost", "toxic")


def _img(*p):
    return Image.open(os.path.join(V10, *p)).convert("RGBA")


def hero(prefix="h/kaan"):
    d = os.path.join(V10, "hero_anim")
    meta = json.load(open(os.path.join(d, "anim.json")))
    out = []
    for key, (ax, ay, k) in meta["frames"].items():
        out.append((f"{prefix}/{key}", _img("hero_anim", key + ".png"), ax, ay, k))
    return out, {a: v for a, v in meta["anims"].items()}


def npcs():
    out = []
    d = os.path.join(V10, "npc")
    ks = {who: 1.8 * PX_PER_M / _img("npc", ref + ".png").height for who, ref in NPC_STAND_H.items()}
    for f in sorted(os.listdir(d)):
        if not f.endswith(".png"):
            continue
        name = f[:-4]
        im = _img("npc", f)
        a = np.asarray(im)[..., 3] > 0
        if name in NPC_CENTER:
            ax = im.width / 2
        else:
            ys, xs = np.nonzero(a)
            ax = float(xs[ys >= ys.max() - 3].mean())
        out.append((f"npc/{name}", im, ax, im.height - 1, ks[name.split("_")[0]]))
    return out


def props():
    out = []
    for name, (w_m, fx_, fy_) in PROPS.items():
        p = os.path.join(V10, "props", name + ".png")
        if os.path.exists(p):
            im = _img("props", name + ".png")
            out.append((f"s/{name}", im, im.width * fx_, im.height * fy_, w_m * PX_PER_M / im.width))
    return out


def items():
    d = os.path.join(V10, "items")
    out = []
    for f in sorted(os.listdir(d)):
        if f.endswith(".png"):
            im = _img("items", f)
            out.append((f"i/{f[:-4]}", im, im.width / 2, im.height - 1, 1.0))
    return out


def fx():
    """[(anahtar, Image, ax, ay, k)], {ad: meta}"""
    out, meta = [], {}
    for name in FX:
        d = os.path.join(FX7, name)
        m = json.load(open(os.path.join(d, "meta.json")))
        m["pixel"] = True
        meta[name] = m
        ax, ay = m.get("anchor", (0.5, 0.5))
        for i in range(m["frames"]):
            im = Image.open(os.path.join(d, f"{i}.png")).convert("RGBA")
            out.append((f"fx/{name}/{i}", im, im.width * ax, im.height * ay, 1.0))
    return out, meta
