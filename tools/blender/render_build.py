"""Detaylı binaları render eder: /opt/bvenv/bin/python tools/blender/render_build.py [isim ...] [--out DIR] [--force]"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bpy  # noqa: E402

import blib as B  # noqa: E402
import build_b as BB  # noqa: E402
import sdfmesh as SM  # noqa: E402

MPP = 0.016
SAMPLES = int(os.environ.get("ZX_SAMPLES", "48"))

args = sys.argv[1:]
out_dir = "/home/user/zx_render/buildings"
force = "--force" in args
if "--out" in args:
    out_dir = args[args.index("--out") + 1]
    args = [a for a in args if a not in ("--out", out_dir)]
args = [a for a in args if a != "--force"]
names = args or list(BB.BUILDERS)
os.makedirs(out_dir, exist_ok=True)
for name in names:
    p = os.path.join(out_dir, name + ".png")
    if os.path.exists(p) and not force and os.path.exists(os.path.join(out_dir, name + ".v2")):
        continue
    sc = B.reset_scene((64, 64), samples=SAMPLES, mpp=MPP)
    BB.reset_cache()
    sc.cycles.max_bounces = 6
    BB.BUILDERS[name]()
    objs = [o for o in bpy.data.objects if o.type in ("MESH", "FONT") and o.name != "Ground"]
    bpy.context.view_layer.update()
    mesh_objs = []
    for o in objs:
        if o.type == "FONT":
            continue
        mesh_objs.append(o)
    res, anc = SM.frame_objects(sc, mesh_objs, MPP, shadow=0.5)
    B.render_to(p)
    json.dump({"anchor": list(anc), "res": res}, open(p[:-4] + ".json", "w"))
    open(os.path.join(out_dir, name + ".v2"), "w").write("1")
    print("bina", name, res, flush=True)
