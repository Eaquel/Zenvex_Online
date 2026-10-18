"""Detaylı araçlar (8 yön) ve enkazlar: /opt/bvenv/bin/python tools/blender/render_vehicles.py [isim ...] [--out DIR]"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bpy  # noqa: E402

import blib as B  # noqa: E402
import build_b as BB  # noqa: E402
import sdfmesh as SM  # noqa: E402
import vehicles_b as VB  # noqa: E402

MPP = 0.016
SAMPLES = int(os.environ.get("ZX_SAMPLES", "40"))
args = sys.argv[1:]
root = "/home/user/zx_render"
if "--out" in args:
    root = args[args.index("--out") + 1]
    args = [a for a in args if a not in ("--out", root)]
names = args or list(VB.VEHICLES) + list(VB.WRECKS)


def build(fn, yaw):
    sc = B.reset_scene((64, 64), samples=SAMPLES, mpp=MPP)
    BB.reset_cache()
    sc.cycles.max_bounces = 6
    fn()
    bpy.context.view_layer.update()
    pivot = bpy.data.objects.new("pivot", None)
    sc.collection.objects.link(pivot)
    objs = [o for o in bpy.data.objects if o.type in ("MESH", "FONT") and o.name not in ("Ground",) and o.parent is None]
    for o in objs:
        o.parent = pivot
    pivot.rotation_euler = (0, 0, yaw)
    bpy.context.view_layer.update()
    vis = [o for o in objs if o.type == "MESH" and not o.hide_render]
    return sc, vis


for name in names:
    if name in VB.VEHICLES:
        for d in range(8):
            p = os.path.join(root, "vehicles", name, f"{d}.png")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            sc, vis = build(VB.VEHICLES[name], d * math.pi / 4)
            res, anc = SM.frame_objects(sc, vis, MPP, shadow=0.45)
            B.render_to(p)
            json.dump({"anchor": list(anc), "res": res}, open(p[:-4] + ".json", "w"))
            print("araç", name, d, res, flush=True)
    else:
        p = os.path.join(root, "buildings", name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        sc, vis = build(VB.WRECKS[name], 0.5 if name == "wreck0" else -0.7)
        res, anc = SM.frame_objects(sc, vis, MPP, shadow=0.45)
        B.render_to(p)
        json.dump({"anchor": list(anc), "res": res}, open(p[:-4] + ".json", "w"))
        print("enkaz", name, res, flush=True)
