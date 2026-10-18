"""Blender'da insan modelleri (4 kahraman, zombiler, NPC'ler) + köpek. Metaball gövde, mesh detaylar.

build_human(...) → {"body": [nesneler], "arms": [nesneler]}  (kollar ayrı katmanda render edilebilsin diye)
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assetgen"))
import bpy
import numpy as np

import blib as B
from blib import meta_family, meta_capsule, meta_ellipsoid, material, add_mesh_obj
from character2 import v3, unit

# --------------------------------------------------------------------------- görünümler
LOOKS = {
    "kaan": dict(skin="#d9a07a", top=("#e2a032", "cloth"), bottom=("#34507e", "denim"), shoe=("#ecebe6", "leather"),
                 sole="#d6d4ce", hair="#3e2a1c", hood=True, wraps="#efefe8", sleeve_long=True, brow="#2e2016"),
    "nova": dict(skin="#e0b090", top=("#262a33", "cloth"), bottom=("#474b55", "cloth"), shoe=("#2c2c30", "leather"),
                 sole="#55555c", hair="#9aa6c0", female=True, neon="#3fe0ff", goggles=True, sleeve_long=True, pockets=True),
    "rady": dict(skin="#c88e6a", top=("#3a3478", "cloth"), bottom=("#26252f", "cloth"), shoe=("#4a3426", "leather"),
                 sole="#2a2420", hair="#241c18", coat=True, hood_up=True, beard=True, trim="#e8c45a", rune="#7ff0ff", sleeve_long=True),
    "elif": dict(skin="#e8b898", top=("#f0f0f2", "cloth"), bottom=("#4c7652", "cloth"), shoe=("#ecebe6", "leather"),
                 sole="#d6d4ce", hair="#7a2e1c", female=True, ponytail=True, red="#d8343c", sleeve_long=True),
    "soldier": dict(skin="#c89a74", top=("#59683c", "cloth"), bottom=("#59683c", "cloth"), shoe=("#3a2c22", "leather"),
                    sole="#1e1a16", hair="#2a2018", sleeve_long=True, vest="#3c4430", helmet="#4d5a36"),
    "merchant": dict(skin="#d09a70", top=("#c8b088", "cloth"), bottom=("#5a5a66", "cloth"), shoe=("#4a3426", "leather"),
                     sole="#2a2420", hair="#3a2a1c", beard=True, apron="#8c5a3c", cap="#a03028"),
    # zombiler
    "walker": dict(skin="#93b07c", top=("#755a92", "cloth"), bottom=("#686876", "cloth"), shorts=True, shoe=("#3a3a40", "leather"),
                   sole="#22222a", hair="#34342c", barefoot_r=True, short_sleeve=True, claws=True, eye="#ffe066", blood=True),
    "runner": dict(skin="#c4bcae", top=("#a8342c", "cloth"), bottom=("#3e5276", "denim"), shoe=("#e6e6e0", "leather"),
                   sole="#c8c8c0", hair="#2a2a24", short_sleeve=True, claws=True, eye="#ff3a2a", blood=True, thin=True),
    "dark": dict(skin="#a29eb0", top=("#45405a", "cloth"), bottom=("#38344a", "cloth"), shoe=("#26242e", "leather"),
                 sole="#18161e", hair=None, hood_up=True, claws=True, eye="#c45cff", sleeve_long=True, smoke=True),
    "toxic": dict(skin="#a2c446", top=("#d8b63a", "cloth"), bottom=("#c8a632", "cloth"), shoe=("#3a3a2a", "leather"),
                  sole="#22221a", hair=None, claws=True, eye="#c6ff3a", sleeve_long=True, pustules=True, gasmask=True),
    "tank": dict(skin="#8aa874", top=None, bottom=("#3e5276", "denim"), shoe=("#3a2c22", "leather"), sole="#1e1a16",
                 hair=None, claws=True, eye="#ff3a2a", bulk=1.6, scars=True),
    "mystery": dict(skin="#cfc8ba", top=("#1e1b26", "cloth"), bottom=("#1e1b26", "cloth"), shoe=("#1e1b26", "cloth"), sole="#111111",
                    hair=None, coat=True, long_coat=True, hood_up=True, claws=True, eye="#ff3a2a", mask=True, hover=True, sleeve_long=True),
    "titan": dict(skin="#8e8a9a", top=("#5c5e68", "metal"), bottom=("#6a4428", "leather"), shoe=("#2a2a2e", "metal"), sole="#111111",
                  hair=None, claws=True, eye="#ff3a2a", bulk=1.65, armor=True),
}
SCALE = {"tank": 1.28, "titan": 2.3, "mystery": 1.05}


def _arm_families(L, prefix):
    top = L.get("top")
    fam = {}
    if top:
        fam["top"] = meta_family(prefix + "ArmTop", material("top", top[0], 0.85, metal=0.8 if top[1] == "metal" else 0.0, kind=top[1]))
    fam["skin"] = meta_family(prefix + "ArmSkin", material("skin", L["skin"], 0.55, kind="skin", sss=0.25, bump=0.1))
    return fam


def build_human(J, L, scale=1.0):
    S, H = J["S"], J["H"]
    fem = L.get("female", False)
    s = scale
    b = L.get("bulk", 1.0) * (0.86 if L.get("thin") else 1.0)
    P = lambda p: np.asarray(p, float) * s
    skinm = material("skin", L["skin"], 0.55, kind="skin", sss=0.25, bump=0.1)
    before = set(bpy.data.objects.keys())
    sk = meta_family("Skin", skinm)
    top = None
    if L.get("top"):
        tm = L["top"]
        top = meta_family("Top", material("top", tm[0], 0.85 if tm[1] != "metal" else 0.35, metal=0.8 if tm[1] == "metal" else 0.0, kind=tm[1]))
    bot = meta_family("Bottom", material("bot", L["bottom"][0], 0.8, kind=L["bottom"][1]))
    hair = meta_family("Hair", material("hair", L["hair"], 0.45, kind="hair", bump=0.7)) if L.get("hair") else None
    torso = top if top is not None else sk
    # ---- baş, boyun
    meta_ellipsoid(sk, P(J["head"]), H, (0.098 * s, 0.122 * s, 0.108 * s))
    meta_capsule(sk, P(J["neck"] + S @ v3(0, -0.06, 0)), P(J["head"] - H @ v3(0, 0.06, 0)), 0.05 * s * min(b, 1.3))
    meta_capsule(sk, P(J["head"] + H @ v3(0, -0.005, 0.1)), P(J["head"] + H @ v3(0, -0.02, 0.115)), 0.016 * s, 1.0)
    for sx in (-1, 1):
        meta_ellipsoid(sk, P(J["head"] + H @ v3(sx * 0.095, 0.0, 0.0)), H, (0.014 * s, 0.028 * s, 0.02 * s), 1.0)
    # ---- gövde
    sw = (0.13 if fem else 0.155) * b
    meta_ellipsoid(torso, P(J["pelvis"] + S @ v3(0, 0.33, 0)), S, ((sw + 0.03) * s, 0.17 * s, 0.11 * s * b))
    meta_ellipsoid(torso, P(J["pelvis"] + S @ v3(0, 0.14, 0)), S, ((sw - 0.01) * s, 0.16 * s, 0.1 * s * b))
    meta_ellipsoid(bot, P(J["pelvis"]), S, (0.14 * s * b, 0.1 * s, 0.1 * s * b))
    if L.get("hood"):
        meta_ellipsoid(top, P(J["neck"] + S @ v3(0, -0.03, -0.07)), S, (0.12 * s, 0.07 * s, 0.07 * s), 1.5)
    if L.get("coat"):
        end = -0.75 if L.get("long_coat") else -0.32
        meta_capsule(top, P(J["pelvis"] + S @ v3(0, 0.05, 0)), P(J["pelvis"] + S @ v3(0, end, -0.03)), (0.17 if L.get("long_coat") else 0.16) * s, 2.0)
        if L.get("long_coat"):
            meta_capsule(top, P(J["pelvis"] + S @ v3(0, -0.4, 0)), P(J["pelvis"] + S @ v3(0, -0.9, -0.05)), 0.2 * s, 2.0)
    # ---- bacaklar ve ayakkabılar
    for pre in ("l", "r"):
        if L.get("hover"):
            continue
        meta_capsule(bot, P(J[pre + "_hip"]), P(J[pre + "_knee"]), 0.09 * s * b)
        if L.get("shorts"):
            mid = J[pre + "_knee"] * 0.85 + J[pre + "_hip"] * 0.15
            meta_capsule(sk, P(mid), P(J[pre + "_ankle"]), 0.058 * s * b)
        else:
            meta_capsule(bot, P(J[pre + "_knee"]), P(J[pre + "_ankle"]), 0.066 * s * b)
        fwd = J[pre + "_toe"] - J[pre + "_heel"]
        z = unit(fwd); y = J["B"] @ v3(0, 1, 0); x = np.cross(y, z)
        R = np.stack([x, y, z], axis=1)
        c = (J[pre + "_toe"] + J[pre + "_heel"]) / 2
        if L.get("barefoot_r") and pre == "r":
            meta_ellipsoid(sk, P(c), R, (0.045 * s, 0.03 * s, 0.09 * s))
            continue
        add_mesh_obj(pre + "shoe", material("shoe", L["shoe"][0], 0.6, kind=L["shoe"][1]), prim="cube",
                     c=P(c + y * 0.015), half=(0.05 * s * b, 0.035 * s, 0.105 * s), R=R, bevel=0.025 * s)
        add_mesh_obj(pre + "sole", material("sole", L["sole"], 0.8, kind="rubber"), prim="cube",
                     c=P(c - y * 0.022), half=(0.054 * s * b, 0.012 * s, 0.11 * s), R=R, bevel=0.008 * s)
    # ---- saç
    if hair is not None:
        hc = J["head"] + H @ v3(0, 0.03, -0.02)
        meta_ellipsoid(hair, P(hc), H, (0.105 * s, 0.1 * s, 0.112 * s))
        for off, r in (((0.04, 0.09, 0.07), 0.035), ((-0.04, 0.1, 0.06), 0.035), ((0.0, 0.11, 0.03), 0.04),
                       ((0.07, 0.05, 0.02), 0.03), ((-0.07, 0.05, 0.02), 0.03)):
            meta_capsule(hair, P(J["head"] + H @ v3(*off)), P(J["head"] + H @ v3(*off)), r * s, 1.5)
        if L.get("ponytail"):
            meta_capsule(hair, P(J["head"] + H @ v3(0, 0.06, -0.1)), P(J["head"] + H @ v3(0, -0.12, -0.16)), 0.035 * s)
        if L.get("beard"):
            meta_ellipsoid(hair, P(J["head"] + H @ v3(0, -0.07, 0.06)), H, (0.07 * s, 0.045 * s, 0.05 * s), 1.2)
    if L.get("hood_up"):
        hm = meta_family("Hood", material("hood", L["top"][0], 0.85, kind="cloth"))
        meta_ellipsoid(hm, P(J["head"] + H @ v3(0, 0.035, -0.05)), H, (0.118 * s, 0.13 * s, 0.11 * s))
    # ---- yüz
    eye_col = L.get("eye")
    if eye_col:
        em = material("eye_" + eye_col, eye_col, 0.3, emit=6.0)
    else:
        em = material("eye", "#16161c", 0.2, clearcoat=1.0)
    for sx in (-1, 1):
        if not L.get("mask"):
            add_mesh_obj("eye", em, prim="sphere", c=P(J["head"] + H @ v3(sx * 0.038, 0.012, 0.097)), r=0.011 * s)
        if L.get("brow") or (L.get("hair") and not eye_col):
            add_mesh_obj("brow", material("brow", L.get("brow", L.get("hair", "#222")), 0.6), prim="cube",
                         c=P(J["head"] + H @ v3(sx * 0.04, 0.04, 0.098)), half=(0.022 * s, 0.005 * s, 0.006 * s), R=H)
    # ---- ekler
    if L.get("neon"):
        nm = material("neon", L["neon"], 0.3, emit=4.0)
        add_mesh_obj("neonc", nm, prim="cube", c=P(J["pelvis"] + S @ v3(0, 0.36, 0.112)), half=(0.1 * s, 0.006 * s, 0.004 * s), R=S)
    if L.get("pockets"):
        pm = material("top", L["top"][0], 0.85, kind="cloth")
        for pre, side in (("l", 1), ("r", -1)):
            add_mesh_obj("pk", pm, prim="cube", c=P(J[pre + "_hip"] * 0.45 + J[pre + "_knee"] * 0.55 + J["S"] @ v3(side * 0.07, 0, 0)),
                         half=(0.025 * s, 0.06 * s, 0.05 * s), R=J["S"], bevel=0.01 * s)
    if L.get("goggles"):
        add_mesh_obj("gog", material("gog", "#2a2d36", 0.3, metal=0.8), prim="cube", c=P(J["head"] + H @ v3(0, 0.1, 0.05)),
                     half=(0.105 * s, 0.016 * s, 0.03 * s), R=H, bevel=0.01 * s)
        for sx in (-1, 1):
            add_mesh_obj("lens", material("lens", "#5ef0ff", 0.1, emit=3.0), prim="sphere", c=P(J["head"] + H @ v3(sx * 0.042, 0.103, 0.078)), r=0.015 * s)
    if L.get("trim"):
        tmm = material("trim", L["trim"], 0.25, metal=1.0)
        add_mesh_obj("trim", tmm, prim="cube", c=P(J["pelvis"] + S @ v3(0, 0.2, 0.115)), half=(0.012 * s, 0.25 * s, 0.006 * s), R=S)
        add_mesh_obj("belt", tmm, prim="cube", c=P(J["pelvis"] + S @ v3(0, 0.02, 0)), half=(0.16 * s, 0.02 * s, 0.11 * s), R=S, bevel=0.01)
        add_mesh_obj("rune", material("rune", L["rune"], 0.2, emit=5.0), prim="sphere", c=P(J["pelvis"] + S @ v3(0.07, 0.38, 0.115)), r=0.02 * s)
    if L.get("red"):
        rm = material("red", L["red"], 0.7, kind="cloth")
        for (cx, cy, cz) in ((0, 0.34, 0.112), (0, 0.32, -0.112)):
            add_mesh_obj("crossA", rm, prim="cube", c=P(J["pelvis"] + S @ v3(cx, cy, cz)), half=(0.05 * s, 0.014 * s, 0.004 * s), R=S)
            add_mesh_obj("crossB", rm, prim="cube", c=P(J["pelvis"] + S @ v3(cx, cy, cz)), half=(0.014 * s, 0.05 * s, 0.004 * s), R=S)
    if L.get("hood"):
        wm = material("wrap", L.get("wraps", "#eeeeee"), 0.9, kind="cloth")
        for sx in (-0.04, 0.04):
            add_mesh_obj("str", wm, prim="cylinder", a=P(J["neck"] + S @ v3(sx, -0.02, 0.095)), b=P(J["neck"] + S @ v3(sx, -0.14, 0.105)), r=0.006 * s)
    if L.get("vest"):
        add_mesh_obj("vest", material("vest", L["vest"], 0.8, kind="cloth"), prim="cube", c=P(J["pelvis"] + S @ v3(0, 0.27, 0)),
                     half=(0.18 * s, 0.2 * s, 0.12 * s), R=S, bevel=0.06 * s)
    if L.get("helmet"):
        add_mesh_obj("helm", material("helm", L["helmet"], 0.5, kind="metal"), prim="sphere", c=P(J["head"] + H @ v3(0, 0.04, -0.005)), r=0.125 * s)
    if L.get("apron"):
        add_mesh_obj("apron", material("apron", L["apron"], 0.7, kind="leather"), prim="cube", c=P(J["pelvis"] + S @ v3(0, 0.12, 0.105)),
                     half=(0.15 * s, 0.3 * s, 0.012 * s), R=S, bevel=0.01 * s)
    if L.get("cap"):
        cm = material("cap", L["cap"], 0.8, kind="cloth")
        add_mesh_obj("cap", cm, prim="sphere", c=P(J["head"] + H @ v3(0, 0.06, 0)), r=0.112 * s)
        add_mesh_obj("visor", cm, prim="cube", c=P(J["head"] + H @ v3(0, 0.07, 0.11)), half=(0.08 * s, 0.008 * s, 0.05 * s), R=H, bevel=0.005)
    if L.get("blood"):
        bm = material("blood", "#5a1418", 0.4, clearcoat=0.6)
        add_mesh_obj("bl", bm, prim="cube", c=P(J["pelvis"] + S @ v3(0.06, 0.22, 0.106)), half=(0.05 * s, 0.07 * s, 0.003 * s), R=S, bevel=0.002)
        add_mesh_obj("bl2", bm, prim="cube", c=P(J["head"] + H @ v3(0.0, -0.08, 0.09)), half=(0.03 * s, 0.015 * s, 0.003 * s), R=H)
    if L.get("pustules"):
        pmm = material("pus", "#c6ff3a", 0.2, emit=2.5)
        for off, r in (((0.08, 0.36, 0.1), 0.03), ((-0.1, 0.18, 0.1), 0.026), ((0.0, 0.3, -0.11), 0.034), ((0.12, 0.28, 0.0), 0.024)):
            add_mesh_obj("pu", pmm, prim="sphere", c=P(J["pelvis"] + S @ v3(*off)), r=r * s)
        add_mesh_obj("pu", pmm, prim="sphere", c=P(J["head"] + H @ v3(0.07, 0.05, 0.06)), r=0.025 * s)
    if L.get("gasmask"):
        gm = material("gasm", "#2a2a2a", 0.5, kind="rubber")
        add_mesh_obj("gm", gm, prim="cylinder", a=P(J["head"] + H @ v3(0, -0.04, 0.09)), b=P(J["head"] + H @ v3(0, -0.06, 0.15)), r=0.035 * s)
        for sx in (-1, 1):
            add_mesh_obj("gl", material("gml", "#b8ff4a", 0.1, emit=1.5), prim="sphere", c=P(J["head"] + H @ v3(sx * 0.04, 0.015, 0.1)), r=0.02 * s)
    if L.get("mask"):
        add_mesh_obj("mask", material("mask", "#ece6dc", 0.25, clearcoat=0.8), prim="sphere", c=P(J["head"] + H @ v3(0, 0.0, 0.03)), r=0.1 * s)
        for sx in (-1, 1):
            add_mesh_obj("me", em, prim="sphere", c=P(J["head"] + H @ v3(sx * 0.035, 0.015, 0.125)), r=0.012 * s)
    if L.get("armor"):
        am = material("armor", "#6c6e78", 0.3, metal=1.0, kind="metal")
        rust = material("rust", "#7a4a2a", 0.9, kind="stone")
        for pre, side in (("l", 1), ("r", -1)):
            add_mesh_obj("pad", am, prim="sphere", c=P(J[pre + "_shoulder"] + S @ v3(side * 0.03, 0.04, 0)), r=0.105 * s)
        add_mesh_obj("plate", am, prim="cube", c=P(J["pelvis"] + S @ v3(0, 0.3, 0.1)), half=(0.17 * s, 0.14 * s, 0.03 * s), R=S, bevel=0.03 * s)
        add_mesh_obj("chain", material("chain", "#9a9ca6", 0.2, metal=1.0), prim="cylinder",
                     a=P(J["l_shoulder"] + S @ v3(0, 0, 0.12)), b=P(J["pelvis"] + S @ v3(-0.12, 0.05, 0.13)), r=0.012 * s)
        add_mesh_obj("belt", rust, prim="cube", c=P(J["pelvis"] + S @ v3(0, 0.03, 0)), half=(0.16 * s * b, 0.03 * s, 0.11 * s * b), R=S, bevel=0.01 * s)
    if L.get("scars"):
        add_mesh_obj("sc", material("blood", "#5a1418", 0.4, clearcoat=0.6), prim="cube", c=P(J["pelvis"] + S @ v3(-0.05, 0.3, 0.115 * b)),
                     half=(0.08 * s, 0.01 * s, 0.003 * s), R=S @ np.array([[0.8, -0.6, 0], [0.6, 0.8, 0], [0, 0, 1]]))
    body_objs = [bpy.data.objects[k] for k in bpy.data.objects.keys() if k not in before]
    # ---- kollar (ayrı aile → ayrı katman)
    before2 = set(bpy.data.objects.keys())
    af = _arm_families(L, "")
    for pre in ("l", "r"):
        upper = af.get("top", af["skin"])
        if L.get("short_sleeve"):
            mid = J[pre + "_shoulder"] * 0.55 + J[pre + "_elbow"] * 0.45
            meta_capsule(upper, P(J[pre + "_shoulder"]), P(mid), 0.074 * s * b)
            meta_capsule(af["skin"], P(mid), P(J[pre + "_elbow"]), 0.052 * s * b)
        else:
            meta_capsule(upper, P(J[pre + "_shoulder"]), P(J[pre + "_elbow"]), 0.072 * s * b)
        if L.get("sleeve_long") and "top" in af:
            meta_capsule(af["top"], P(J[pre + "_elbow"]), P(J[pre + "_wrist"] - (J[pre + "_wrist"] - J[pre + "_elbow"]) * 0.1), 0.056 * s * b)
        meta_capsule(af["skin"], P(J[pre + "_elbow"]), P(J[pre + "_wrist"]), 0.046 * s * b)
        if L.get("wraps"):
            add_mesh_obj(pre + "hand", material("wrap", L["wraps"], 0.9, kind="cloth"), prim="sphere", c=P(J[pre + "_fist"]), r=0.05 * s)
        elif L.get("claws"):
            meta_capsule(af["skin"], P(J[pre + "_wrist"]), P(J[pre + "_fist"] + J[pre + "_fdir"] * 0.04), 0.04 * s * b)
        else:
            meta_capsule(af["skin"], P(J[pre + "_wrist"]), P(J[pre + "_fist"]), 0.045 * s * b)
        if L.get("neon"):
            out = S @ v3((1 if pre == "l" else -1) * 0.066 * b, 0, 0)
            add_mesh_obj("neon", material("neon", L["neon"], 0.3, emit=4.0), prim="cylinder",
                         a=P(J[pre + "_shoulder"] + out), b=P(J[pre + "_elbow"] + out * 0.9), r=0.009 * s)
        if L.get("red") and pre == "l":
            add_mesh_obj("band", material("red", L["red"], 0.7, kind="cloth"), prim="cylinder",
                         a=P(J["l_shoulder"] * 0.6 + J["l_elbow"] * 0.4), b=P(J["l_shoulder"] * 0.4 + J["l_elbow"] * 0.6), r=0.076 * s)
    arm_objs = [bpy.data.objects[k] for k in bpy.data.objects.keys() if k not in before2]
    return {"body": body_objs, "arms": arm_objs}


# --------------------------------------------------------------------------- köpek
def build_dog(anim, t, yaw):
    s = math.sin
    ph = math.tau * t
    body_y = 0.42
    lean = 0.0
    legs = [0.0] * 4
    head_drop = 0.0
    fall = 0.0
    if anim == "walk":
        legs = [0.6 * s(ph), -0.6 * s(ph), -0.6 * s(ph), 0.6 * s(ph)]
        body_y += 0.025 * abs(math.cos(ph))
    elif anim == "idle":
        body_y += 0.006 * s(ph)
        head_drop = 0.02 * s(ph)
    elif anim == "attack":
        i = min(int(t * 6), 5)
        k = [0.0, -0.3, 0.6, 1.0, 0.5, 0.0][i]
        lean = -0.25 * k
        legs = [0.6 * k, 0.6 * k, -0.3 * k, -0.3 * k]
    elif anim == "hurt":
        lean = 0.2
    elif anim == "death":
        i = min(int(t * 6), 5)
        fall = [0, 0.3, 0.8, 1.3, 1.5, 1.55][i]
    from sdf2 import rot_x, rot_y, rot_z
    R = rot_y(yaw) @ rot_z(fall) @ rot_x(lean)
    w = lambda p: R @ np.asarray(p, float)
    fur = meta_family("Dog", material("fur", "#5a4636", 0.85, kind="hair", bump=0.9))
    ta, tb = w(v3(0, body_y, -0.26)), w(v3(0, body_y + 0.03, 0.22))
    meta_capsule(fur, ta, tb, 0.13)
    neck = w(v3(0, body_y + 0.14, 0.32))
    head = w(v3(0, body_y + 0.2 - head_drop, 0.42))
    meta_capsule(fur, tb, neck, 0.1)
    meta_capsule(fur, head, head, 0.09)
    meta_capsule(fur, head, w(v3(0, body_y + 0.16 - head_drop, 0.55)), 0.055)
    for sx in (-1, 1):
        meta_capsule(fur, head + w(v3(sx * 0.05, 0.06, -0.02)), head + w(v3(sx * 0.07, 0.15, -0.04)), 0.025, 1.2)
    meta_capsule(fur, ta, w(v3(0, body_y + 0.15, -0.45)), 0.035)
    for (hx, hz), a in zip(((-0.09, 0.2), (0.09, 0.2), (-0.09, -0.22), (0.09, -0.22)), legs):
        top = w(v3(hx, body_y - 0.05, hz))
        foot = w(v3(hx, 0.03, hz + 0.25 * math.sin(a)))
        meta_capsule(fur, top, foot, 0.05)
    em = material("eye_ff3a2a", "#ff3a2a", 0.3, emit=6.0)
    for sx in (-1, 1):
        add_mesh_obj("de", em, prim="sphere", c=head + w(v3(sx * 0.04, 0.03, 0.075)), r=0.014)
    add_mesh_obj("nose", material("nose", "#1a1416", 0.3), prim="sphere", c=w(v3(0, body_y + 0.17 - head_drop, 0.59)), r=0.02)
    add_mesh_obj("teeth", material("teeth", "#e8e2d0", 0.4), prim="cube", c=w(v3(0, body_y + 0.12 - head_drop, 0.54)),
                 half=(0.03, 0.006, 0.02), R=R)
    return [o for o in bpy.data.objects if o.name not in ("Cam", "Sun", "Fill", "Rim", "Ground")]


def attach_points(J, scale=1.0):
    P = lambda p: (np.asarray(p, float) * scale).tolist()
    return {"rhand": P(J["r_fist"]), "lhand": P(J["l_fist"]), "head": P(J["head"]), "back": P(J["back"]),
            "chest": P(J["chest"]), "neck": P(J["neck"]), "lfoot": P((J["l_toe"] + J["l_heel"]) / 2),
            "rfoot": P((J["r_toe"] + J["r_heel"]) / 2)}
