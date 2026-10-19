"""Savaşçı sprite'ları: 2× render → tools/pixel/pixelize.py ile piksel sanat.

/opt/bvenv/bin/python tools/blender/render_warrior.py test          → birkaç örnek kare (kontrol)
/opt/bvenv/bin/python tools/blender/render_warrior.py all           → tüm animasyonlar, 5 yön (B tarafı ayna)
Çıktı: $ZX_PIX/hero/<anim>_<yön>_<kare>.png (+ attach.json)
"""
import json
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
sys.path.insert(0, os.path.join(HERE, "..", "pixel"))
import bpy  # noqa: E402
from PIL import Image  # noqa: E402

import blib as B  # noqa: E402
import mh_actor as MA  # noqa: E402
import render_actors3 as RA  # noqa: E402
import warrior_b as W  # noqa: E402
from character2 import hero_pose, skeleton  # noqa: E402
from pixelize import pixelize  # noqa: E402

OUT = os.environ.get("ZX_PIX", "/home/user/zx_pix")
MPP2 = 0.0045  # 2× render; yerel piksel = 0.009 m (karakter ~190 px)
RES = (600, 600)
ANCHOR = (300, 524)
# animasyon: (kare, hero_pose adı)
ANIMS = {
    "idle": (6, "idle"), "walk": (8, "walk"), "run": (8, "run"), "punch": (8, "punch"), "roll": (6, "run"),
    "hurt": (2, "hurt"), "knockdown": (5, "hurt"), "getup": (5, "idle"), "death": (6, "death"),
}
DIRS = [0, 1, 2, 3, 4]  # G, GD, D, KD, K — 5,6,7 aynalanır


def pixel_lights(sc):
    """Piksel sanat için daha sert ve kontrastlı ışık: sıcak ana ışık, soğuk kenar ışığı, zemin gölgesi yok."""
    for o in bpy.data.objects:
        if o.name == "Ground":
            o.hide_render = True
    L = bpy.data.lights
    if "Sun" in L:
        L["Sun"].energy = 6.0
        L["Sun"].color = (1.0, 0.92, 0.82)
    if "Fill" in L:
        L["Fill"].energy = 0.2
        L["Fill"].color = (0.6, 0.7, 1.0)
    if "Rim" in L:
        L["Rim"].energy = 3.2
        L["Rim"].color = (0.75, 0.85, 1.0)
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    bg = sc.world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs[1].default_value = 0.06


_BASE = {}


def body_rot(rig, anim, t, yaw):
    """Takla (kalça çevresinde, bakılan yöne), yere düşme ve kalkma (ayak çevresinde, geriye)."""
    from mathutils import Matrix, Vector
    import numpy as np
    from character2 import rot_y
    if "m" not in _BASE:
        _BASE["m"] = rig.matrix_world.copy()
    f = B.g2b(rot_y(yaw) @ np.array([0, 0, 1.0]))
    fwd = Vector((f.x, f.y, 0)).normalized()
    axis = fwd.cross(Vector((0, 0, 1))).normalized()
    ang, pivot, lift = 0.0, Vector((0, 0, 0)), 0.0
    if anim == "roll":
        ang = 2 * math.pi * t
        pivot = Vector((0, 0, 0.55))
        lift = -0.3 * math.sin(math.pi * t)  # yere yakın kıvrılarak döner
        pivot += fwd * 0.0
    elif anim == "knockdown":
        ang = -math.radians(82) * min(1.0, t * 1.5)
    elif anim == "getup":
        ang = -math.radians(82) * (1 - t)
    R = Matrix.Rotation(ang, 4, axis)
    T = Matrix.Translation(pivot + Vector((0, 0, lift)) + (fwd * (0.6 * t - 0.3) if anim == "roll" else Vector()))
    rig.matrix_world = T @ R @ Matrix.Translation(-pivot) @ _BASE["m"]


def pose_frame(actor, anim, d, f, frames):
    base = ANIMS[anim][1]
    t = f / frames
    pz = hero_pose(base if anim not in ("getup",) else "idle", t if anim != "knockdown" else 0.0)
    if anim == "roll":
        pz = hero_pose("hurt", 0.5)
    pz["yaw"] = d * math.pi / 4
    MA.pose(actor, skeleton(pz, 0.19), 1.0)
    body_rot(actor["rig"], anim, t, pz["yaw"])
    W.set_cloth(actor, *W.cloth_for(anim, t))


def attach_all(actor):
    """Silah/ekipman bağlama noktaları (yerel piksel, render yok)."""
    out = {}
    for anim, (frames, _) in ANIMS.items():
        for d in DIRS:
            for f in range(frames):
                pose_frame(actor, anim, d, f, frames)
                bpy.context.view_layer.update()
                pts = MA.attach_points(actor)
                out[f"{anim}_{d}_{f}"] = {k: [round(c / 2, 1) for c in B.world_to_px(v, RES, ANCHOR, MPP2)] for k, v in pts.items()}
    json.dump(out, open(os.path.join(OUT, "hero", "attach.json"), "w"))
    print("bağlama noktaları:", len(out))


def render_frame(actor, anim, d, f, frames, path):
    base = ANIMS[anim][1]
    t = f / frames
    pz = hero_pose(base if anim not in ("getup",) else "idle", t if anim != "knockdown" else 0.0)
    if anim == "roll":
        pz = hero_pose("hurt", 0.5)  # kıvrık gövde
    pz["yaw"] = d * math.pi / 4
    J = skeleton(pz, 0.19)
    MA.pose(actor, J, 1.0)
    rig = actor["rig"]
    body_rot(rig, anim, t, pz["yaw"])
    fl, sw = W.cloth_for(anim, t)
    W.set_cloth(actor, fl, sw)
    # 2× ham render saklanır: pikselleştirici ayarı değişince yeniden render gerekmez (tools/pixel/repix.py)
    raw = os.path.join(OUT, "raw", os.path.basename(path))
    os.makedirs(os.path.dirname(raw), exist_ok=True)
    B.render_to(raw)
    pixelize(Image.open(raw), gamma=1.0).save(path)


def main(mode):
    os.makedirs(os.path.join(OUT, "hero"), exist_ok=True)
    sc = RA.scene(RES, ANCHOR, MPP2, samples=24)
    sc.render.film_transparent = True
    pixel_lights(sc)
    actor = W.build()
    if mode == "attach":
        attach_all(actor)
        return
    jobs = []
    if mode == "test":
        jobs = [tuple(int(x) if x.isdigit() else x for x in j.split(":")) for j in os.environ.get("ZX_JOBS", "idle:0:0,idle:1:0,idle:3:0,walk:2:1,run:2:3,punch:1:3").split(",")]
    else:
        for anim, (frames, _) in ANIMS.items():
            for d in DIRS:
                for f in range(frames):
                    jobs.append((anim, d, f))
    t0 = time.time()
    for n, (anim, d, f) in enumerate(jobs):
        p = os.path.join(OUT, "hero", f"{anim}_{d}_{f}.png")
        if mode != "test" and os.path.exists(p):
            continue
        render_frame(actor, anim, d, f, ANIMS[anim][0], p)
        if n % 10 == 0:
            print(f"{n + 1}/{len(jobs)} {(time.time() - t0) / (n + 1):.2f} sn/kare", flush=True)
    json.dump({"anchor": [ANCHOR[0] // 2, ANCHOR[1] // 2], "mpp": MPP2 * 2, "frames": {a: v[0] for a, v in ANIMS.items()}},
              open(os.path.join(OUT, "hero", "meta.json"), "w"))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "test")
