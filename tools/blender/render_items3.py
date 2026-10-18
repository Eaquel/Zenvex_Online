"""Eşya simgeleri (yüksek çözünürlük) + karakter üstü ekipman (8 yön). Kaldığı yerden devam eder.

/opt/bvenv/bin/python tools/blender/render_items3.py [icons] [equip]
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils import Matrix, Vector  # noqa: E402

import blib as B  # noqa: E402
import items_b as IB  # noqa: E402
import sdfmesh as SM  # noqa: E402
from character2 import rot_y  # noqa: E402

WORK = os.environ.get("ZX_WORK3", "/home/user/zx_render3")
ICON_PX = 224
ITEMS = ["wood", "stone", "cloth", "glue", "resin", "leather", "scrap", "screw", "powder", "electronics", "tooth", "toxic",
         "dark_crystal", "titan_shard", "fuel", "bandage", "medkit", "food", "energy", "antidote", "ammo_9mm", "ammo_762",
         "ammo_556", "ammo_308", "ammo_rocket", "w_bat", "w_baton", "w_glock", "w_ak47", "w_m4", "w_sniper", "w_rpg",
         "helm_basic", "helm_police", "helm_military", "helm_titan", "jacket_basic", "jacket_police", "jacket_military",
         "jacket_titan", "boots_basic", "boots_police", "boots_military", "boots_titan", "bag_basic", "bag_police", "bag_military",
         "cloak", "coin", "gem"]
EQUIP = ["helm_basic", "helm_police", "helm_military", "helm_titan", "bag_basic", "bag_police", "bag_military", "cloak"]
MPP_ACT = 0.0107


def out(*p):
    path = os.path.join(WORK, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def pivot_with(objs):
    piv = bpy.data.objects.new("PIV", None)
    bpy.context.scene.collection.objects.link(piv)
    for o in objs:
        if o.parent is None:
            o.parent = piv
    return piv


def visible(objs):
    return [o for o in objs if o.type == "MESH" and not o.hide_render]


def icons():
    for it in ITEMS:
        p = out("items", it + ".png")
        if os.path.exists(p):
            continue
        sc = B.reset_scene((64, 64), samples=40, mpp=0.003, shadow_catcher=False)
        sc.cycles.max_bounces = 6
        objs = IB.build(it)
        piv = pivot_with(objs)
        if it.startswith("w_"):
            piv.rotation_euler = (0, 0, -math.pi / 2 + 0.25)  # namlu sağa
        elif it.startswith("boots"):
            piv.rotation_euler = (0, 0, -1.0)
        else:
            piv.rotation_euler = (0, 0, 0.55)
        bpy.context.view_layer.update()
        vis = visible(objs)
        # sığdır: önce kaba ölçüm, sonra mpp ayarı
        res, anc = SM.frame_objects(sc, vis, 0.002, pad=4, shadow=0.0)
        mpp = 0.002 * max(res) / ICON_PX
        res, anc = SM.frame_objects(sc, vis, mpp, pad=8, shadow=0.0)
        B.render_to(p)
        json.dump({"anchor": [res[0] / 2, res[1] / 2], "res": res}, open(p[:-4] + ".json", "w"))
        print("simge", it, res, flush=True)


def equip():
    for it in EQUIP:
        for d in range(8):
            p = out("equip", it, f"{d}.png")
            if os.path.exists(p):
                continue
            sc = B.reset_scene((64, 64), samples=24, mpp=MPP_ACT, shadow_catcher=False)
            objs = IB.build(it)
            piv = pivot_with(objs)
            f = rot_y(d * math.pi / 4) @ np.array([0, 0, 1.0])
            fb = B.g2b(f)
            piv.rotation_euler = (0, 0, math.atan2(fb.x, -fb.y))
            piv.location = (0, 0, 1.0)
            bpy.context.view_layer.update()
            vis = visible(objs)
            res, anc = SM.frame_objects(sc, vis, MPP_ACT, pad=4, shadow=0.0)
            B.render_to(p)
            px = B.world_to_px((0.0, 1.0, 0.0), res, anc, MPP_ACT)
            json.dump({"anchor": list(px), "res": res}, open(p[:-4] + ".json", "w"))
        print("ekipman", it, flush=True)


if __name__ == "__main__":
    for g in sys.argv[1:] or ["icons", "equip"]:
        {"icons": icons, "equip": equip}[g]()
