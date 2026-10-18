"""Tüm oyun görsellerini Blender (Cycles) ile render eder. Kaldığı yerden devam eder.

Kullanım: /opt/bvenv/bin/python tools/blender/render_all.py [grup ...]
Çıktı: $ZX_WORK (varsayılan /home/user/zx_render) altında ham PNG'ler + attach.json
Gruplar: heroes npcs zombies dog equip weapons vehicles buildings nature items lobby
"""
import json
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
import bpy
import numpy as np

import blib as B
import hero_b as HB
import nature_b as NB
import sdfmesh as SM
import props as P
import nature2 as N2
from character2 import skeleton, hero_pose, zombie_pose, HERO_ANIMS, ZOMBIE_ANIMS

WORK = os.environ.get("ZX_WORK", "/home/user/zx_render")
MPP = 0.016
SAMPLES = int(os.environ.get("ZX_SAMPLES", "20"))
MATS = dict(N2.NMATS)
MATS.update(P.PMATS)
LOG = open(os.path.join(WORK, "log.txt") if os.path.isdir(WORK) else os.devnull, "a")


def log(*a):
    msg = " ".join(str(x) for x in a)
    print(msg, flush=True)
    LOG.write(msg + "\n"); LOG.flush()


def out(*parts):
    path = os.path.join(WORK, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def done(path):
    return os.path.exists(path) and os.path.getsize(path) > 0


# --------------------------------------------------------------------------- karakterler
HERO_RES = (256, 224)
HERO_ANCHOR = (128, 192)


def render_humans(kind_list, anims, posef, scale_of=lambda k: 1.0, arms=False, dirs=range(8), group="heroes",
                  res=HERO_RES, anchor=HERO_ANCHOR):
    for kind in kind_list:
        s = scale_of(kind)
        r = (int(res[0] * max(1.0, s * 0.85)), int(res[1] * max(1.0, s * 0.9)))
        a = (r[0] // 2, r[1] - (res[1] - anchor[1]))
        attach_path = out(group, kind, "attach.json")
        attach = json.load(open(attach_path)) if os.path.exists(attach_path) else {}
        sc = None
        for anim, (frames, fps, loop) in anims.items():
            for d in dirs:
                for f in range(frames):
                    key = f"{anim}_{d}_{f}"
                    p_full = out(group, kind, key + ".png")
                    p_arms = out(group, kind, key + "_arms.png")
                    if done(p_full) and (not arms or done(p_arms)) and key in attach:
                        continue
                    if sc is None:
                        sc = B.reset_scene(r, samples=SAMPLES, mpp=MPP)
                        B.set_anchor(sc, a[0], a[1], r, MPP)
                    B.clear_objects()
                    Pz = posef(kind, anim, f / frames)
                    Pz["yaw"] = d * math.pi / 4
                    fem = HB.LOOKS.get(kind, {}).get("female", False)
                    J = skeleton(Pz, 0.17 if fem else 0.19)
                    g = HB.build_human(J, HB.LOOKS[kind], s)
                    bpy.data.objects["Ground"].hide_render = False
                    B.render_to(p_full)
                    if arms:
                        for o in g["body"]:
                            o.is_holdout = True
                        bpy.data.objects["Ground"].hide_render = True
                        B.render_to(p_arms)
                    pts = HB.attach_points(J, s)
                    attach[key] = {k: B.world_to_px(v, r, a, MPP) for k, v in pts.items()}
                    attach[key]["anchor"] = list(a)
            json.dump(attach, open(attach_path, "w"))
            log(group, kind, anim, "tamam")
        json.dump(attach, open(attach_path, "w"))


def heroes():
    render_humans(["kaan", "nova", "rady", "elif"], HERO_ANIMS, lambda k, a, t: hero_pose(a, t), arms=True)


def npcs():
    render_humans(["soldier"], {"rifle_idle": (2, 3, True)}, lambda k, a, t: hero_pose(a, t), group="npcs")
    render_humans(["merchant"], {"idle": (6, 5, True)}, lambda k, a, t: hero_pose(a, t), dirs=[0, 1, 7], group="npcs")


def zombies():
    kinds = ["walker", "runner", "dark", "toxic", "tank", "mystery", "titan"]
    render_humans(kinds, ZOMBIE_ANIMS, lambda k, a, t: zombie_pose(a, t, k), scale_of=lambda k: HB.SCALE.get(k, 1.0), group="zombies")


def dog():
    r, a = (200, 168), (100, 136)
    sc = None
    for anim, (frames, fps, loop) in ZOMBIE_ANIMS.items():
        for d in range(8):
            for f in range(frames):
                p = out("zombies", "dog", f"{anim}_{d}_{f}.png")
                if done(p):
                    continue
                if sc is None:
                    sc = B.reset_scene(r, samples=SAMPLES, mpp=MPP)
                    B.set_anchor(sc, a[0], a[1], r, MPP)
                B.clear_objects()
                HB.build_dog(anim, f / frames, d * math.pi / 4)
                B.render_to(p)
    log("zombies dog tamam")


# --------------------------------------------------------------------------- tek görünüşlü / 8 yönlü nesneler
def render_sdf(name_path, scene, box, yaw=0.0, mpp=MPP, shadow=True, samples=None, lift=0.0):
    p = out(*name_path)
    meta_p = p[:-4] + ".json"
    if done(p) and os.path.exists(meta_p):
        return
    sc = B.reset_scene((64, 64), samples=samples or SAMPLES, mpp=mpp, shadow_catcher=shadow)
    objs = SM.scene_to_objects(scene, MATS, box)
    if not objs:
        log("BOŞ:", name_path)
        return
    for o in objs:
        o.rotation_euler = (0, 0, yaw)
        o.location = (0, 0, lift)
    res, anc = SM.frame_objects(sc, objs, mpp, shadow=0.45 if shadow else 0.0)
    B.render_to(p)
    json.dump({"anchor": [anc[0], anc[1] + lift / mpp * math.cos(B.PITCH)], "res": res}, open(meta_p, "w"))


def equip():
    items = {}
    for k in ("basic", "police", "military", "titan"):
        items[f"helm_{k}"] = (P.helmet(k), ((-0.3, -0.3, -0.3), (0.3, 0.35, 0.3)))
        items[f"jacket_{k}"] = (P.jacket(k), ((-0.35, -0.4, -0.3), (0.35, 0.35, 0.3)))
        items[f"boots_{k}"] = (P.boot(k), ((-0.15, -0.05, -0.2), (0.15, 0.3, 0.2)))
    for k in ("basic", "police", "military"):
        items[f"bag_{k}"] = (P.bag(k), ((-0.3, -0.35, -0.35), (0.3, 0.35, 0.15)))
    items["cloak"] = (P.cloak(), ((-0.5, -1.1, -0.6), (0.5, 0.2, 0.3)))
    for name, (scn, box) in items.items():
        for d in range(8):
            render_sdf(("equip", name, f"{d}.png"), scn, box, yaw=d * math.pi / 4, shadow=False, lift=1.0)
    log("equip tamam")


def weapons():
    for k in ("glock", "ak47", "m4", "sniper", "rpg", "bat", "baton"):
        for d in range(8):
            render_sdf(("weapons", k, f"{d}.png"), P.weapon(k), ((-0.3, -0.3, -0.5), (0.3, 0.3, 1.0)),
                       yaw=d * math.pi / 4, shadow=False, lift=1.0)
    log("weapons tamam")


def vehicles():
    for k in ("bike", "moto", "car", "suv"):
        for d in range(8):
            render_sdf(("vehicles", k, f"{d}.png"), P.vehicle(k), ((-1.4, -0.1, -3.0), (1.4, 2.2, 3.0)), yaw=d * math.pi / 4)
    log("vehicles tamam")


def buildings():
    jobs = {
        "house0": P.house(0), "house1": P.house(1), "house2": P.house(2),
        "apartment0": P.apartment(0, 3), "apartment1": P.apartment(1, 3),
        "shop0": P.shop(0), "shop1": P.shop(2), "police": P.police_station(), "gas": P.gas_station(),
        "wall_x": P.wall_segment(4.0, "x"), "wall_z": P.wall_segment(4.0, "z"), "gate": P.gate_barrier(),
        "tower": P.watchtower(), "lamp": P.streetlamp(), "wreck0": P.car_wreck(0), "wreck1": P.car_wreck(1),
        "crate": P.crate(), "crate_mil": P.crate(True), "barrel": P.barrel(), "barrel_tox": P.barrel(True),
        "workbench": P.workbench(), "campfire": P.campfire(), "tent": P.tent(), "stall": P.stall(),
        "sandbags": P.sandbags(), "fence": P.fence(), "trash": P.trash(), "cone": P.cone(),
    }
    for name, scn in jobs.items():
        render_sdf(("buildings", name + ".png"), scn, ((-6.5, 0, -5), (6.5, 16, 5)), yaw=0.0)
        log("bina", name)


def nature():
    # yapraklı ağaçlar (Blender parçacık)
    blender_jobs = {
        "oak0": lambda: NB.oak(1), "oak1": lambda: NB.oak(2, "#5a9440"), "oak2": lambda: NB.oak(5, "#66a442"),
        "oak_autumn": lambda: NB.oak(7, autumn=True), "pine0": lambda: NB.pine(3), "pine1": lambda: NB.pine(8),
        "pine2": lambda: NB.pine(13), "birch0": lambda: NB.birch(4), "birch1": lambda: NB.birch(9),
        "bush0": lambda: NB.bush(5), "bush1": lambda: NB.bush(6), "bush2": lambda: NB.bush(7, "wide"),
        "bush3": lambda: NB.bush(8, "berry"), "bush4": lambda: NB.bush(9, "flower"),
        "grass0": lambda: NB.grass_patch(1), "grass1": lambda: NB.grass_patch(2, "#6aa43e"),
        "grass2": lambda: NB.grass_patch(3, "#7aa84a", 40), "grass3": lambda: NB.grass_patch(4, "#86a04a", 30, 0.2, 0.22),
        "flower_daisy": lambda: NB.flowers(1, "#f2f0e6"), "flower_poppy": lambda: NB.flowers(2, "#d83a32"),
        "flower_corn": lambda: NB.flowers(3, "#5a8ae8"), "flower_butter": lambda: NB.flowers(4, "#f2cc3a"),
        "flower_lavender": lambda: NB.flowers(5, "#a07ad8"),
    }
    for name, fn in blender_jobs.items():
        p = out("nature", name + ".png")
        if done(p) and os.path.exists(p[:-4] + ".json"):
            continue
        sc = B.reset_scene((64, 64), samples=SAMPLES + 8, mpp=MPP)
        fn()
        objs = [o for o in bpy.data.objects if o.type == "MESH" and o.name != "Ground" and o.location.z > -10]
        res, anc = SM.frame_objects(sc, objs, MPP, shadow=0.4)
        B.render_to(p)
        json.dump({"anchor": list(anc), "res": res}, open(p[:-4] + ".json", "w"))
        log("doğa", name)
    sdf_jobs = {
        "dead0": N2.dead_tree(31), "dead1": N2.dead_tree(32), "rock_big0": N2.rock(51, 1.0), "rock_big1": N2.rock(52, 1.1),
        "rock_big2": N2.rock(53, 0.9), "rock_small0": N2.rock(61, 0.4), "rock_small1": N2.rock(62, 0.45),
        "rock_small2": N2.rock(63, 0.35), "stump": N2.stump(), "log": N2.log_fallen(), "mushrooms0": N2.mushrooms(1),
        "mushrooms1": N2.mushrooms(2), "reeds0": N2.reeds(1), "reeds1": N2.reeds(2),
    }
    for name, scn in sdf_jobs.items():
        sc_ = scn[0] if isinstance(scn, tuple) else scn
        render_sdf(("nature", name + ".png"), sc_, ((-3, 0, -3), (3, 6, 3)))
        log("doğa", name)


ITEM_IDS = ["wood", "stone", "cloth", "glue", "resin", "leather", "scrap", "screw", "powder", "electronics", "tooth",
            "toxic", "dark_crystal", "titan_shard", "fuel", "bandage", "medkit", "food", "energy", "antidote",
            "ammo_9mm", "ammo_762", "ammo_556", "ammo_308", "ammo_rocket",
            "w_bat", "w_baton", "w_glock", "w_ak47", "w_m4", "w_sniper", "w_rpg",
            "helm_basic", "helm_police", "helm_military", "helm_titan",
            "jacket_basic", "jacket_police", "jacket_military", "jacket_titan",
            "boots_basic", "boots_police", "boots_military", "boots_titan",
            "bag_basic", "bag_police", "bag_military", "cloak", "coin", "gem"]


def items():
    for name in ITEM_IDS:
        scn = P.item_model(name)
        big = name.startswith(("w_sniper", "w_rpg", "cloak"))
        mpp = 0.0065 if big else 0.0045
        yaw = 0.15 if name.startswith("w_") else 0.6
        render_sdf(("items", name + ".png"), scn, ((-1.0, -0.5, -1.0), (1.0, 1.4, 1.0)), yaw=yaw, mpp=mpp, samples=SAMPLES + 12)
    log("items tamam")


def lobby():
    """Lobi için yüksek çözünürlüklü kahraman (8 yön, 6 kare idle)."""
    r, a = (360, 420), (180, 392)
    for kind in ("kaan", "nova", "rady", "elif"):
        sc = None
        for d in range(8):
            for f in range(6):
                p = out("lobby", kind, f"{d}_{f}.png")
                if done(p):
                    continue
                if sc is None:
                    sc = B.reset_scene(r, samples=SAMPLES + 20, mpp=0.0055)
                    B.set_anchor(sc, a[0], a[1], r, 0.0055)
                B.clear_objects()
                Pz = hero_pose("idle", f / 6)
                Pz["yaw"] = d * math.pi / 4
                fem = HB.LOOKS[kind].get("female", False)
                J = skeleton(Pz, 0.17 if fem else 0.19)
                HB.build_human(J, HB.LOOKS[kind])
                B.render_to(p)
        log("lobi", kind)


GROUPS = {"heroes": heroes, "npcs": npcs, "zombies": zombies, "dog": dog, "equip": equip, "weapons": weapons,
          "vehicles": vehicles, "buildings": buildings, "nature": nature, "items": items, "lobby": lobby}

if __name__ == "__main__":
    os.makedirs(WORK, exist_ok=True)
    LOG = open(os.path.join(WORK, "log.txt"), "a")
    names = sys.argv[1:] or ["items", "equip", "weapons", "nature", "vehicles", "buildings", "npcs", "dog", "lobby", "heroes", "zombies"]
    t0 = time.time()
    for g in names:
        GROUPS[g]()
        log(f"== {g} bitti ({time.time() - t0:.0f} sn)")
    log("HEPSİ BİTTİ")
