"""Gerçekçi karakter renderları (MakeHuman + prosedürel animasyon). Kaldığı yerden devam eder.

Kullanım: /opt/bvenv/bin/python tools/blender/render_actors3.py [heroes] [zombies] [npcs] [lobby] [weapons]
Çıktı: $ZX_WORK3 (/home/user/zx_render3)/{heroes,zombies,npcs,lobby,weapons}/...
Silahlı animasyonlar her silah için ayrı render edilir: <anim>_<yön>_<kare>@<silah>.png
"""
import json
import math
import os
import sys
import time
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils import Matrix, Vector  # noqa: E402

import blib as B  # noqa: E402
import mh_actor as MA  # noqa: E402
import weapons_b as WB  # noqa: E402
from character2 import HERO_ANIMS, ZOMBIE_ANIMS, hero_pose, skeleton, zombie_pose  # noqa: E402

WORK = os.environ.get("ZX_WORK3", "/home/user/zx_render3")
MPP = 0.0107  # 1.5× yoğunluk (oyunda 0.0107/0.016 ölçekle çizilir)
SAMPLES = int(os.environ.get("ZX_SAMPLES", "12"))
RES = (300, 270)
ANCHOR = (150, 228)
LOG = None
# Paralel render (GitHub Actions matrisi): ZX_SHARD="i/N" → bu makine yalnızca crc32(anahtar) % N == i olanları çizer
_SH = os.environ.get("ZX_SHARD", "")
SHARD = tuple(int(x) for x in _SH.split("/")) if "/" in _SH else (0, 1)


def mine(key):
    return zlib.crc32(key.encode()) % SHARD[1] == SHARD[0]

BASE = ["idle", "walk", "run", "punch", "cast", "hurt", "death", "ride"]
WEAPON_ANIMS = {"pistol_idle": ["glock"], "pistol_walk": ["glock"], "rifle_idle": ["ak47", "m4", "sniper", "rpg"],
                "rifle_walk": ["ak47", "m4", "sniper", "rpg"], "melee": ["bat", "baton"]}


def log(*a):
    msg = " ".join(str(x) for x in a)
    print(msg, flush=True)
    LOG.write(msg + "\n")
    LOG.flush()


def out(*parts):
    p = os.path.join(WORK, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def done(p):
    return os.path.exists(p) and os.path.getsize(p) > 0


def scene(res, anchor, mpp=MPP, samples=None):
    sc = B.reset_scene(res, samples=samples or SAMPLES, mpp=mpp)
    B.set_anchor(sc, anchor[0], anchor[1], res, mpp)
    sc.cycles.max_bounces = 3
    sc.cycles.use_adaptive_sampling = True
    sc.cycles.adaptive_threshold = 0.03
    # hızlandırma: veriyi kareler arasında bellekte tut, sprite boyutuna göre doku sınırı, ışık yolu kısaltma
    sc.render.use_persistent_data = True
    sc.cycles.texture_limit_render = "512"
    sc.cycles.diffuse_bounces = 1
    sc.cycles.glossy_bounces = 1
    sc.cycles.transmission_bounces = 2
    sc.cycles.use_auto_tile = False
    sc.render.film_transparent = True
    return sc


# --------------------------------------------------------------------------- silah yerleştirme
def weapon_rig(kind):
    piv = bpy.data.objects.new("WPIV", None)
    bpy.context.scene.collection.objects.link(piv)
    for o in WB.build(kind):
        o.parent = piv
    return piv


def place_weapon(piv, actor, J, kind, s=1.0):
    rig = actor["rig"]
    hb = rig.pose.bones["hand_r"]
    wrist = rig.matrix_world @ hb.head
    tip = rig.matrix_world @ hb.tail
    fwd = (tip - wrist).normalized()
    up = Vector((0, 0, 1))
    if kind in ("ak47", "m4", "sniper", "rpg"):
        # tüfek: karakterin baktığı yöne, hafif aşağı
        face = rig.matrix_world.to_3x3() @ Vector((0, -1, 0))
        face.z = 0
        fwd = (face.normalized() + Vector((0, 0, -0.05))).normalized()
    elif kind in ("bat", "baton"):
        fwd = (up * 0.75 + fwd * 0.65).normalized()
    side = fwd.cross(up)
    if side.length < 1e-4:
        side = Vector((1, 0, 0))
    side.normalize()
    upv = side.cross(fwd).normalized()
    R = Matrix((side, fwd, upv)).transposed()
    palm = wrist + (tip - wrist) * 0.55
    if kind in ("bat", "baton"):
        palm = wrist + (tip - wrist) * 0.6
    M = R.to_4x4()
    M.translation = palm
    piv.matrix_world = M @ Matrix.Scale(s, 4)


# --------------------------------------------------------------------------- insan render döngüsü
def render_actor(group, kind, anims, posef, scale=1.0, dirs=range(8), weapons=None, res=RES, anchor=ANCHOR, mpp=MPP):
    k = max(1.0, scale * 0.9)
    r = (int(res[0] * k), int(res[1] * k))
    a = (r[0] // 2, r[1] - (res[1] - anchor[1]))
    attach_path = out(group, kind, "attach.json")
    attach = json.load(open(attach_path)) if os.path.exists(attach_path) else {}
    jobs = []
    for anim in anims:
        frames = (HERO_ANIMS.get(anim) or ZOMBIE_ANIMS.get(anim))[0]
        ws = (weapons or {}).get(anim, [None])
        for w in ws:
            for d in dirs:
                for f in range(frames):
                    key = f"{anim}_{d}_{f}" + (f"@{w}" if w else "")
                    if not (done(out(group, kind, key + ".png")) and key.split("@")[0] in attach):
                        jobs.append((anim, w, d, f, frames, key))
    jobs = [j for j in jobs if mine(f"{group}/{kind}/{j[5]}")]
    if not jobs:
        return
    sc = scene(r, a, mpp)
    actor = MA.build(kind, scale)
    piv = {}
    t0 = time.time()
    for n, (anim, w, d, f, frames, key) in enumerate(jobs):
        for wk, p in piv.items():
            for c in p.children:
                c.hide_render = wk != w
        Pz = posef(anim, f / frames)
        Pz["yaw"] = d * math.pi / 4
        fem = MA.LOOKS[kind]["gender"] < 0.5
        J = skeleton(Pz, 0.17 if fem else 0.19)
        MA.pose(actor, J, scale)
        if w:
            if w not in piv:
                piv[w] = weapon_rig(w)
            place_weapon(piv[w], actor, J, w, scale)
            for c in piv[w].children:
                c.hide_render = False
        B.render_to(out(group, kind, key + ".png"))
        pts = MA.attach_points(actor)
        base = key.split("@")[0]
        attach[base] = {kk: B.world_to_px(v, r, a, mpp) for kk, v in pts.items()}
        attach[base]["anchor"] = list(a)
        if n % 20 == 19:
            json.dump(attach, open(attach_path, "w"))
            log(group, kind, f"{n + 1}/{len(jobs)}", f"{(time.time() - t0) / (n + 1):.2f} sn/kare")
    json.dump(attach, open(attach_path, "w"))
    log(group, kind, "tamam", len(jobs))


def heroes():
    for kind in ("kaan", "nova", "rady", "elif"):
        render_actor("heroes", kind, BASE + list(WEAPON_ANIMS), lambda an, t: hero_pose(an, t), weapons=WEAPON_ANIMS)


def zombies():
    for kind in ("walker", "runner", "toxic", "dark", "tank", "mystery", "titan"):
        render_actor("zombies", kind, list(ZOMBIE_ANIMS), lambda an, t, k=kind: zombie_pose(an, t, k), scale=MA.SCALE.get(kind, 1.0))


def npcs():
    render_actor("npcs", "soldier", ["rifle_idle"], lambda an, t: hero_pose(an, t), weapons={"rifle_idle": ["m4"]})
    render_actor("npcs", "merchant", ["idle"], lambda an, t: hero_pose(an, t), dirs=[0, 1, 7])


def lobby():
    r, a, mpp = (360, 420), (180, 400), 0.0055
    for kind in ("kaan", "nova", "rady", "elif"):
        jobs = [(d, f) for d in range(8) for f in range(6) if not done(out("lobby", kind, f"{d}_{f}.png")) and mine(f"lobby/{kind}/{d}_{f}")]
        if not jobs:
            continue
        scene(r, a, mpp, samples=SAMPLES + 12)
        actor = MA.build(kind)
        for d, f in jobs:
            Pz = hero_pose("idle", f / 6)
            Pz["yaw"] = d * math.pi / 4
            J = skeleton(Pz, 0.17 if MA.LOOKS[kind]["gender"] < 0.5 else 0.19)
            MA.pose(actor, J)
            B.render_to(out("lobby", kind, f"{d}_{f}.png"))
        log("lobi", kind, "tamam")


def weapons():
    """Yalnız silah (8 yön): silahsız animasyonlarda ele bindirme için."""
    if SHARD[0] != 0:
        return
    for kind in WB.BUILDERS:
        for d in range(8):
            p = out("weapons", kind, f"{d}.png")
            if done(p):
                continue
            sc = scene((64, 64), (32, 32), MPP)
            piv = weapon_rig(kind)
            bpy.data.objects["Ground"].hide_render = True
            yaw = d * math.pi / 4
            # silah yatay, karakterin baktığı yöne doğru
            fwd = Vector((math.sin(yaw), -math.cos(yaw), 0)) if kind not in ("bat", "baton") else Vector((0.3 * math.sin(yaw), -0.3 * math.cos(yaw), 0.95)).normalized()
            side = fwd.cross(Vector((0, 0, 1)))
            if side.length < 1e-3:
                side = Vector((1, 0, 0))
            side.normalize()
            upv = side.cross(fwd)
            M = Matrix((side, fwd, upv)).transposed().to_4x4()
            M.translation = Vector((0, 0, 1.0))
            piv.matrix_world = M
            bpy.context.view_layer.update()
            import sdfmesh as SM
            objs = [c for c in piv.children]
            res, anc = SM.frame_objects(sc, objs, MPP, shadow=0.0)
            B.render_to(p)
            # çapa: silahın kabza noktası (dünya 0,0,1) → piksel
            px = B.world_to_px((0.0, 1.0, 0.0), res, anc, MPP)
            json.dump({"anchor": list(px), "res": res}, open(p[:-4] + ".json", "w"))
        log("silah", kind, "tamam")


GROUPS = {"heroes": heroes, "zombies": zombies, "npcs": npcs, "lobby": lobby, "weapons": weapons}

if __name__ == "__main__":
    os.makedirs(WORK, exist_ok=True)
    LOG = open(os.path.join(WORK, "log.txt"), "a")
    t0 = time.time()
    for g in sys.argv[1:] or ["lobby", "npcs", "weapons", "heroes", "zombies"]:
        GROUPS[g]()
        log(f"== {g} bitti ({time.time() - t0:.0f} sn)")
    log("TUMU BITTI")
