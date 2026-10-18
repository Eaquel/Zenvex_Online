"""Uygulama simgesi için yüksek çözünürlüklü karakter renderları (kahraman + zombiler).

/opt/bvenv/bin/python tools/blender/render_icon.py   → $ZX_WORK3/icon/{hero,z1,z2}.png
Sonra: /opt/bvenv/bin/python tools/assetgen/make_icon.py
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
import blib as B  # noqa: E402
import mh_actor as MA  # noqa: E402
import render_actors3 as RA  # noqa: E402
from character2 import hero_pose, skeleton, zombie_pose  # noqa: E402

MPP = 0.0024
RES = (1024, 1024)
ANCHOR = (512, 960)


def shot(name, kind, pose, d, weapon=None, scale=1.0):
    p = RA.out("icon", name + ".png")
    if RA.done(p):
        return
    RA.scene(RES, ANCHOR, MPP, samples=48)
    actor = MA.build(kind, scale)
    Pz = pose
    Pz["yaw"] = d * math.pi / 4
    J = skeleton(Pz, 0.17 if MA.LOOKS[kind]["gender"] < 0.5 else 0.19)
    MA.pose(actor, J, scale)
    if weapon:
        piv = RA.weapon_rig(weapon)
        RA.place_weapon(piv, actor, J, weapon, scale)
    B.render_to(p)
    print("simge", name, flush=True)


if __name__ == "__main__":
    RA.LOG = open(os.devnull, "w")
    shot("hero", "kaan", hero_pose("rifle_idle", 0.0), 1, "ak47")
    shot("z1", "walker", zombie_pose("attack", 0.35, "walker"), 7)
    shot("z2", "toxic", zombie_pose("walk", 0.25, "toxic"), 0)
