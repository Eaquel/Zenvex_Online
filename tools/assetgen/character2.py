"""V2 karakterler: iskelet, 4 kahraman, zombi türleri, NPC'ler, pozlar ve bağlantı noktaları."""
import math
import numpy as np
from sdf2 import (Mat, sd_sphere, sd_round_cone, sd_round_box, sd_ellipsoid, sd_cut, rot_x, rot_y, rot_z, v3)

TAU = 2 * math.pi


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def limb_dir(pitch, abd, side):
    return np.array([side * math.sin(abd) * math.cos(pitch), -math.cos(pitch) * math.cos(abd), math.sin(pitch)])


def bend(u, ang):
    w = unit([0, 0.3, 1.0])
    h = np.cross(u, w)
    if np.linalg.norm(h) < 1e-6:
        h = np.array([-1.0, 0, 0])
    h = unit(h)
    return u * math.cos(ang) + np.cross(h, u) * math.sin(ang)


DEFAULT_POSE = dict(
    root=(0.0, 0.0, 0.0), pelvis_y=0.95, yaw=0.0, fall=0.0, fall_pivot=0.0,
    lean=0.0, twist=0.0, side_lean=0.0, head_pitch=-0.2, head_yaw=0.0, breathe=0.0,
    l_hip=0.0, l_hip_abd=0.06, l_knee=0.0, r_hip=0.0, r_hip_abd=0.06, r_knee=0.0,
    l_sh=0.0, l_sh_abd=0.12, l_elbow=0.1, r_sh=0.0, r_sh_abd=0.12, r_elbow=0.1,
    l_sh_yaw=0.0, r_sh_yaw=0.0,
)


def skeleton(pose, shoulder_w=0.19):
    P = dict(DEFAULT_POSE)
    P.update(pose)
    B = rot_y(P["yaw"]) @ rot_x(P["fall"])
    root = np.array(P["root"], float)
    pivot = np.array([0.0, 0.0, P["fall_pivot"]])

    def body(p):
        return root + B @ (np.asarray(p, float) - pivot) + rot_y(P["yaw"]) @ pivot

    pelvis_l = v3(0, P["pelvis_y"], 0)
    S = rot_y(P["twist"]) @ rot_x(P["lean"]) @ rot_z(P["side_lean"])

    def spine(p):
        return pelvis_l + S @ np.asarray(p, float)

    J = {"B": B, "S": B @ S}
    J["pelvis"] = body(pelvis_l)
    J["chest"] = body(spine([0, 0.32 + P["breathe"], 0]))
    J["neck"] = body(spine([0, 0.50 + P["breathe"], 0]))
    J["back"] = body(spine([0, 0.30 + P["breathe"], -0.13]))
    H = S @ rot_x(P["head_pitch"]) @ rot_y(P["head_yaw"])
    J["head"] = body(spine([0, 0.50 + P["breathe"], 0]) + H @ v3(0, 0.13, 0.015))
    J["H"] = B @ H
    for side, pre in ((1, "l"), (-1, "r")):
        sh_l = spine([side * shoulder_w, 0.44 + P["breathe"], -0.01])
        yaw = rot_y(P[pre + "_sh_yaw"] * side)
        u = S @ yaw @ limb_dir(P[pre + "_sh"], P[pre + "_sh_abd"], side)
        f = S @ yaw @ bend(limb_dir(P[pre + "_sh"], P[pre + "_sh_abd"], side), P[pre + "_elbow"])
        el_l = sh_l + u * 0.29
        wr_l = el_l + f * 0.26
        J[pre + "_shoulder"] = body(sh_l)
        J[pre + "_elbow"] = body(el_l)
        J[pre + "_wrist"] = body(wr_l)
        J[pre + "_fist"] = body(wr_l + f * 0.05)
        J[pre + "_fdir"] = B @ f
        hip_l = pelvis_l + v3(side * 0.095, -0.03, 0)
        tu = limb_dir(P[pre + "_hip"], P[pre + "_hip_abd"], side)
        tl = bend(tu, -P[pre + "_knee"])
        kn_l = hip_l + tu * 0.44
        an_l = kn_l + tl * 0.42
        J[pre + "_hip"] = body(hip_l)
        J[pre + "_knee"] = body(kn_l)
        J[pre + "_ankle"] = body(an_l)
        J[pre + "_toe"] = body(an_l + v3(0, -0.04, 0.13))
        J[pre + "_heel"] = body(an_l + v3(0, -0.05, -0.03))
    return J


def scaled(scene, s):
    """Sahneyi orijin (ayak) etrafında ölçekler."""
    if s == 1.0:
        return scene
    return [(m, (lambda f: (lambda p: f(p / s) * s))(f)) for m, f in scene]


# ------------------------------------------------------------------ malzemeler
MATS = {
    # ortak
    "skin": Mat("#d99a74", "skin", spread=0.36),
    "skin_dark": Mat("#9c6644", "skin", spread=0.36),
    "eye": Mat("#1b1c26"),
    "sole": Mat("#d8d8d0", "leather"),
    "shoe_w": Mat("#e6e6e0", "leather"),
    "shoe_b": Mat("#3a3a40", "leather"),
    "boot": Mat("#4a3426", "leather", spec=0.15),
    # Kaan
    "k_hood": Mat("#e8a93a", "cloth"),
    "k_jeans": Mat("#3b5a8c", "denim"),
    "k_hair": Mat("#4f3322", "hair"),
    "k_wrap": Mat("#eef2f0", "cloth"),
    # Nova
    "n_jacket": Mat("#2a2d36", "cloth"),
    "n_neon": Mat("#3fe0ff", emissive=True),
    "n_pants": Mat("#4a4e58", "cloth"),
    "n_hair": Mat("#8e9ab4", "hair"),
    "n_goggle": Mat("#2a2d36", "metal", spec=0.8),
    "n_lens": Mat("#5ef0ff", emissive=True),
    # Rady
    "r_coat": Mat("#3b3577", "cloth"),
    "r_trim": Mat("#e8c45a", "metal", spec=0.6),
    "r_rune": Mat("#7ff0ff", emissive=True),
    "r_pants": Mat("#2b2a3a", "cloth"),
    "r_hair": Mat("#2b2320", "hair"),
    # Elif
    "e_jacket": Mat("#eef0f2", "cloth"),
    "e_red": Mat("#d8343c", "cloth"),
    "e_pants": Mat("#4f7a54", "cloth"),
    "e_hair": Mat("#8c3a24", "hair"),
    # Asker / tüccar
    "s_camo": Mat("#5b6a3e", "cloth"),
    "s_vest": Mat("#3c4430", "cloth"),
    "s_helmet": Mat("#4d5a36", "metal", spec=0.3),
    "m_apron": Mat("#8c5a3c", "leather"),
    "m_shirt": Mat("#c9b08a", "cloth"),
    "m_cap": Mat("#a03028", "cloth"),
    # zombiler
    "z_skin": Mat("#9cb986", "skin", spread=0.36),
    "z_skin_grey": Mat("#a8a4b4", "skin", spread=0.36),
    "z_skin_tox": Mat("#a8c84a", "skin", spread=0.4),
    "z_skin_pale": Mat("#c9c2b4", "skin", spread=0.3),
    "z_shirt": Mat("#7a5f96", "cloth"),
    "z_shorts": Mat("#6c6c7c", "cloth"),
    "z_red": Mat("#a8342c", "cloth"),
    "z_jeans": Mat("#41557a", "denim"),
    "z_black": Mat("#4a4458", "cloth"),
    "z_hazmat": Mat("#d8b83a", "cloth"),
    "z_hair": Mat("#35352e", "hair"),
    "z_eye_y": Mat("#ffe066", emissive=True),
    "z_eye_p": Mat("#c45cff", emissive=True),
    "z_eye_r": Mat("#ff3a2a", emissive=True),
    "z_pus": Mat("#c6ff3a", emissive=True),
    "z_blood": Mat("#6e1a22", "cloth"),
    "z_mask": Mat("#ece6dc", "metal", spec=0.4),
    "z_cloak": Mat("#1e1b26", "cloth"),
    "t_armor": Mat("#6c6e78", "metal", spec=0.7),
    "t_rust": Mat("#7a4a2a", "rust"),
    "t_chain": Mat("#8a8c96", "metal", spec=0.9),
    "dog_fur": Mat("#5a4636", "fur"),
    "dog_skin": Mat("#8c5a4a", "skin"),
}


# ------------------------------------------------------------------ insan modeli
def human(J, look, scale=1.0, bulk=1.0):
    """look: dict(kind=..., materials...) → SDF sahnesi."""
    L = look
    S, H = J["S"], J["H"]
    sc = []
    skin = L["skin"]
    top = L["top"]
    bottom = L["bottom"]
    shoe = L.get("shoe", "shoe_w")
    fem = L.get("female", False)
    b = bulk
    # bacaklar
    for pre in ("l", "r"):
        sc.append((bottom, sd_round_cone(J[pre + "_hip"], J[pre + "_knee"], 0.096 * b, 0.07 * b)))
        if L.get("shorts"):
            mid = J[pre + "_knee"] * 0.85 + J[pre + "_hip"] * 0.15
            sc[-1] = (bottom, sd_round_cone(J[pre + "_hip"], mid, 0.09 * b, 0.07 * b))
            sc.append((skin, sd_round_cone(mid, J[pre + "_ankle"], 0.058 * b, 0.042 * b)))
        else:
            sc.append((bottom, sd_round_cone(J[pre + "_knee"], J[pre + "_ankle"], 0.07 * b, 0.054 * b)))
        fwd = J[pre + "_toe"] - J[pre + "_heel"]
        c = (J[pre + "_toe"] + J[pre + "_heel"]) / 2
        z = unit(fwd)
        y = J["B"] @ v3(0, 1, 0)
        x = np.cross(y, z)
        R = np.stack([x, y, z], axis=1)
        if L.get("barefoot_r") and pre == "r":
            sc.append((skin, sd_round_box(c, R, (0.045, 0.03, 0.09), 0.025)))
        elif not L.get("hover"):
            sc.append((shoe, sd_round_box(c + y * 0.012, R, (0.05, 0.03, 0.1), 0.025)))
            sc.append(("sole", sd_round_box(c - y * 0.02, R, (0.052, 0.012, 0.104), 0.01)))
    # kalça ve gövde
    sw = 0.13 if fem else 0.155
    sc.append((bottom, sd_round_box(J["pelvis"] + S @ v3(0, 0.02, 0), S, (0.13 * b, 0.08, 0.085 * b), 0.05)))
    sc.append((top, sd_round_box(J["pelvis"] + S @ v3(0, 0.24, 0), S, (sw * b, 0.27, 0.095 * b), 0.08)))
    if L.get("coat"):  # uzun palto / pelerin eteği
        sc.append((L["coat"], sd_round_cone(J["pelvis"] + S @ v3(0, 0.1, 0), J["pelvis"] + S @ v3(0, -0.3, -0.03), 0.15 * b, 0.165 * b)))
    if L.get("hood"):
        sc.append((top, sd_ellipsoid(J["neck"] + S @ v3(0, -0.02, -0.07), S, (0.13, 0.07, 0.07))))
        # kapüşon ipleri
        for sx in (-0.04, 0.04):
            sc.append(("k_wrap", sd_round_cone(J["neck"] + S @ v3(sx, -0.02, 0.09), J["neck"] + S @ v3(sx, -0.14, 0.1), 0.007, 0.007)))
    for part in L.get("extras", []):
        sc.append(part(J))
    # boyun, kafa, saç
    sc.append((skin, sd_round_cone(J["neck"] + S @ v3(0, -0.06, 0), J["head"] - H @ v3(0, 0.05, 0), 0.05, 0.048)))
    sc.append((skin, sd_ellipsoid(J["head"], H, (0.098, 0.12, 0.108))))
    # burun ve kulaklar
    sc.append((skin, sd_sphere(J["head"] + H @ v3(0, -0.005, 0.105), 0.018)))
    for sx in (-1, 1):
        sc.append((skin, sd_ellipsoid(J["head"] + H @ v3(sx * 0.097, 0.0, 0.0), H, (0.015, 0.03, 0.022))))
    hair = L.get("hair")
    if hair:
        hc = J["head"] + H @ v3(0, 0.024, -0.014)
        hf = sd_ellipsoid(hc, H, (0.11, 0.122, 0.12))
        n = H @ unit([0, 1.0, -0.75])
        sc.append((hair, sd_cut(hf, n, float(n @ J["head"]) - L.get("hairline", 0.01))))
        for off, r in L.get("tufts", []):
            sc.append((hair, sd_sphere(J["head"] + H @ v3(*off), r)))
        if L.get("ponytail"):
            sc.append((hair, sd_round_cone(J["head"] + H @ v3(0, 0.06, -0.11), J["head"] + H @ v3(0, -0.12, -0.17), 0.04, 0.025)))
    if L.get("hood_up"):
        hf = sd_ellipsoid(J["head"] + H @ v3(0, 0.02, -0.02), H, (0.125, 0.14, 0.13))
        n = H @ unit([0, 0.4, -1.0])
        sc.append((L["hood_up"], sd_cut(hf, n, float(n @ J["head"]) - 0.04)))
    if L.get("beard"):
        sc.append((L["beard"], sd_cut(sd_ellipsoid(J["head"] + H @ v3(0, -0.05, 0.03), H, (0.085, 0.07, 0.085)), H @ v3(0, -1, 0.3), float((H @ v3(0, -1, 0.3)) @ J["head"]) + 0.02)))
    # kollar
    for pre in ("l", "r"):
        upper_mat = L.get("sleeve", top)
        if L.get("short_sleeve"):
            mid = (J[pre + "_shoulder"] * 0.55 + J[pre + "_elbow"] * 0.45)
            sc.append((upper_mat, sd_round_cone(J[pre + "_shoulder"], mid, 0.07 * b, 0.062 * b)))
            sc.append((skin, sd_round_cone(mid, J[pre + "_elbow"], 0.052 * b, 0.047 * b)))
        else:
            sc.append((upper_mat, sd_round_cone(J[pre + "_shoulder"], J[pre + "_elbow"], 0.077 * b, 0.066 * b)))
        fore = L.get("fore", skin)
        sc.append((fore, sd_round_cone(J[pre + "_elbow"], J[pre + "_wrist"], 0.056 * b, 0.045 * b)))
        if L.get("claws"):
            sc.append((skin, sd_round_cone(J[pre + "_wrist"], J[pre + "_fist"] + J[pre + "_fdir"] * 0.04, 0.042 * b, 0.026 * b)))
        else:
            sc.append((L.get("hand", skin), sd_sphere(J[pre + "_fist"], 0.05 * b)))
    sc = scaled(sc, scale)
    return sc


def human_decals(J, look, scale=1.0):
    H = J["H"]
    fwd = H @ v3(0, 0, 1)
    col = look.get("eyes", "#1b1c26")
    d = []
    for sx in (1, -1):
        d.append((scale * (J["head"] + H @ v3(sx * 0.04, 0.018, 0.1)), col, fwd))
    if look.get("brows"):
        for sx in (1, -1):
            d.append((scale * (J["head"] + H @ v3(sx * 0.042, 0.045, 0.098)), look["brows"], fwd))
    return d


# --- ek parça yardımcıları (gövdeye yapışık)
def chest_box(mat, off, half, r=0.01):
    return lambda J: (mat, sd_round_box(J["pelvis"] + J["S"] @ v3(*off), J["S"], half, r))


def arm_stripe(mat, side):
    """Kolun dış yüzünde ince neon çizgi."""
    pre = "l" if side > 0 else "r"

    def f(J):
        out = J["S"] @ v3(side * 0.062, 0.0, 0.0)
        a = J[pre + "_shoulder"] + (J[pre + "_elbow"] - J[pre + "_shoulder"]) * 0.1 + out
        b = J[pre + "_elbow"] + out * 0.85
        return (mat, sd_round_cone(a, b, 0.013, 0.012))
    return f


def thigh_pocket(mat, side):
    pre = "l" if side > 0 else "r"
    return lambda J: (mat, sd_sphere(J[pre + "_hip"] * 0.45 + J[pre + "_knee"] * 0.55 + J["S"] @ v3(side * 0.06, 0, 0.0), 0.06))


def pustule(off, r):
    return lambda J: ("z_pus", sd_sphere(J["pelvis"] + J["S"] @ v3(*off), r))


LOOKS = {
    "kaan": dict(skin="skin", top="k_hood", bottom="k_jeans", shoe="shoe_w", hair="k_hair", hand="k_wrap",
                 hood=True, tufts=[((0.035, 0.09, 0.07), 0.026), ((-0.03, 0.1, 0.05), 0.03)], brows="#3a2418"),
    "nova": dict(skin="skin", top="n_jacket", bottom="n_pants", shoe="shoe_b", hair="n_hair", female=True,
                 hairline=0.025, tufts=[((0.05, 0.08, 0.07), 0.035), ((-0.06, 0.06, 0.06), 0.03)], brows="#6c7480",
                 extras=[chest_box("n_neon", (0.0, 0.33, 0.1), (0.1, 0.008, 0.004)), arm_stripe("n_neon", 1),
                         arm_stripe("n_neon", -1), thigh_pocket("n_jacket", 1), thigh_pocket("n_jacket", -1),
                         lambda J: ("n_goggle", sd_round_box(J["head"] + J["H"] @ v3(0, 0.1, 0.045), J["H"], (0.105, 0.016, 0.03), 0.012)),
                         lambda J: ("n_lens", sd_sphere(J["head"] + J["H"] @ v3(0.042, 0.105, 0.075), 0.014)),
                         lambda J: ("n_lens", sd_sphere(J["head"] + J["H"] @ v3(-0.042, 0.105, 0.075), 0.014))]),
    "rady": dict(skin="skin", top="r_coat", bottom="r_pants", shoe="boot", hair="r_hair", coat="r_coat",
                 hood_up="r_coat", beard="r_hair", brows="#2b2320",
                 extras=[chest_box("r_trim", (0.0, 0.2, 0.1), (0.012, 0.25, 0.006)),
                         lambda J: ("r_rune", sd_sphere(J["pelvis"] + J["S"] @ v3(0.06, 0.38, 0.1), 0.022)),
                         chest_box("r_trim", (0.0, 0.02, 0.0), (0.16, 0.025, 0.105))]),
    "elif": dict(skin="skin", top="e_jacket", bottom="e_pants", shoe="shoe_w", hair="e_hair", female=True,
                 ponytail=True, hairline=0.02, tufts=[((0.04, 0.085, 0.07), 0.03)], brows="#5a2416",
                 extras=[chest_box("e_red", (0.0, 0.33, 0.1), (0.05, 0.014, 0.004)),
                         chest_box("e_red", (0.0, 0.33, 0.1), (0.014, 0.05, 0.004)),
                         chest_box("e_red", (0.0, 0.3, -0.1), (0.07, 0.02, 0.004)),
                         chest_box("e_red", (0.0, 0.3, -0.1), (0.02, 0.07, 0.004)),
                         lambda J: ("e_red", sd_round_cone(J["l_shoulder"] * 0.6 + J["l_elbow"] * 0.4, J["l_shoulder"] * 0.4 + J["l_elbow"] * 0.6, 0.074, 0.07))]),
    "soldier": dict(skin="skin", top="s_camo", bottom="s_camo", shoe="boot", hair=None, brows="#3a2418",
                    extras=[chest_box("s_vest", (0.0, 0.26, 0.0), (0.17, 0.2, 0.11), 0.05),
                            lambda J: ("s_helmet", sd_cut(sd_ellipsoid(J["head"] + J["H"] @ v3(0, 0.035, -0.005), J["H"], (0.125, 0.115, 0.13)), J["H"] @ v3(0, 1, 0), float((J["H"] @ v3(0, 1, 0)) @ J["head"]) - 0.0))]),
    "merchant": dict(skin="skin", top="m_shirt", bottom="z_shorts", shoe="boot", hair="k_hair", brows="#3a2418",
                     beard="k_hair", extras=[chest_box("m_apron", (0.0, 0.12, 0.1), (0.15, 0.3, 0.012), 0.01),
                                             lambda J: ("m_cap", sd_cut(sd_ellipsoid(J["head"] + J["H"] @ v3(0, 0.05, 0), J["H"], (0.115, 0.1, 0.12)), J["H"] @ v3(0, 1, 0), float((J["H"] @ v3(0, 1, 0)) @ J["head"]) + 0.03)),
                                             lambda J: ("m_cap", sd_round_box(J["head"] + J["H"] @ v3(0, 0.07, 0.11), J["H"], (0.08, 0.008, 0.05), 0.005))]),
    # zombiler
    "walker": dict(skin="z_skin", top="z_shirt", bottom="z_shorts", shorts=True, shoe="shoe_b", hair="z_hair",
                   barefoot_r=True, short_sleeve=True, claws=True, eyes="#ffe066", hairline=0.035,
                   extras=[chest_box("z_blood", (0.06, 0.2, 0.1), (0.05, 0.06, 0.004))]),
    "runner": dict(skin="z_skin_pale", top="z_red", bottom="z_jeans", shoe="shoe_w", hair="z_hair", short_sleeve=True,
                   claws=True, eyes="#ff3a2a", hairline=0.03),
    "dark": dict(skin="z_skin_grey", top="z_black", bottom="z_black", shoe="shoe_b", hood_up="z_black", claws=True,
                 eyes="#c45cff", fore="z_black"),
    "toxic": dict(skin="z_skin_tox", top="z_hazmat", bottom="z_hazmat", shoe="boot", hair=None, claws=True, eyes="#c6ff3a",
                  extras=[pustule((0.08, 0.36, 0.08), 0.03), pustule((-0.1, 0.18, 0.09), 0.025),
                          pustule((0.0, 0.3, -0.1), 0.035),
                          lambda J: ("z_pus", sd_sphere(J["head"] + J["H"] @ v3(0.07, 0.05, 0.06), 0.025))]),
    "tank": dict(skin="z_skin", top="z_skin", bottom="z_jeans", shoe="boot", hair=None, claws=True, eyes="#ff3a2a",
                 extras=[chest_box("z_blood", (-0.05, 0.3, 0.1), (0.08, 0.02, 0.004))]),
    "mystery": dict(skin="z_skin_pale", top="z_cloak", bottom="z_cloak", coat="z_cloak", hood_up="z_cloak", hover=True,
                    claws=True, eyes="#ff3a2a",
                    extras=[lambda J: ("z_mask", sd_cut(sd_ellipsoid(J["head"] + J["H"] @ v3(0, 0.0, 0.03), J["H"], (0.092, 0.115, 0.1)), J["H"] @ v3(0, 0, 1), float((J["H"] @ v3(0, 0, 1)) @ J["head"]) + 0.01))]),
    "titan": dict(skin="z_skin_grey", top="t_armor", bottom="t_rust", shoe="boot", hair=None, claws=True, eyes="#ff3a2a",
                  extras=[chest_box("t_chain", (0.0, 0.25, 0.11), (0.17, 0.02, 0.02), 0.015),
                          chest_box("t_rust", (0.0, 0.42, 0.0), (0.21, 0.05, 0.13), 0.04),
                          lambda J: ("t_armor", sd_sphere(J["l_shoulder"] + J["S"] @ v3(0.03, 0.04, 0), 0.1)),
                          lambda J: ("t_armor", sd_sphere(J["r_shoulder"] + J["S"] @ v3(-0.03, 0.04, 0), 0.1))]),
}

SCALE = {"tank": 1.3, "titan": 2.3, "mystery": 1.05}
BULK = {"tank": 1.55, "titan": 1.6, "runner": 0.85}


# ------------------------------------------------------------------ pozlar
def natural_arms(P, ph, amp=0.38):
    s = math.sin
    P.update(l_sh=-amp * s(ph) + 0.05, r_sh=amp * s(ph) + 0.05,
             l_elbow=0.3 + 0.3 * max(0.0, -s(ph)), r_elbow=0.3 + 0.3 * max(0.0, s(ph)), l_sh_abd=0.1, r_sh_abd=0.1)


def legs_walk(P, ph, amp=0.42):
    s = math.sin
    P.update(l_hip=amp * s(ph), r_hip=-amp * s(ph),
             l_knee=0.16 + 0.6 * max(0.0, -s(ph + 0.6)), r_knee=0.16 + 0.6 * max(0.0, s(ph + 0.6)),
             pelvis_y=0.94 + 0.018 * abs(math.cos(ph)), twist=0.08 * s(ph))


def legs_idle(P, t):
    b = math.sin(TAU * t)
    P.update(breathe=0.008 * b, pelvis_y=0.935 + 0.004 * b, l_hip=0.1, r_hip=-0.06, l_knee=0.12, r_knee=0.1)


def guard(P):
    P.update(l_sh=0.75, l_sh_abd=-0.08, l_elbow=2.2, r_sh=0.65, r_sh_abd=-0.08, r_elbow=2.3)


def pistol_arms(P):
    # iki elle ileri nişan
    P.update(r_sh=1.45, r_sh_abd=-0.22, r_elbow=0.12, l_sh=1.35, l_sh_abd=-0.42, l_elbow=0.35)


def rifle_arms(P):
    # sağ el göğüste (kabza), sol el ileride (namlu altı)
    P.update(r_sh=0.75, r_sh_abd=-0.15, r_elbow=1.55, l_sh=1.25, l_sh_abd=-0.5, l_elbow=0.45, twist=-0.18)


def hero_pose(anim, t):
    s = math.sin
    P = {}
    ph = TAU * t
    if anim == "idle":
        legs_idle(P, t)
        natural_arms(P, 0.0, 0)
        b = s(TAU * t)
        P["l_elbow"] = 0.25 + 0.04 * b
        P["r_elbow"] = 0.25 - 0.04 * b
    elif anim == "walk":
        legs_walk(P, ph)
        natural_arms(P, ph)
    elif anim == "run":
        P.update(l_hip=0.75 * s(ph) + 0.1, r_hip=-0.75 * s(ph) + 0.1,
                 l_knee=0.25 + 1.1 * max(0.0, -s(ph + 0.8)), r_knee=0.25 + 1.1 * max(0.0, s(ph + 0.8)),
                 pelvis_y=0.9 + 0.04 * abs(math.cos(ph)), lean=0.22, twist=0.14 * s(ph),
                 l_sh=-0.7 * s(ph) + 0.2, l_elbow=1.6, l_sh_abd=0.15, r_sh=0.7 * s(ph) + 0.2, r_elbow=1.6, r_sh_abd=0.15)
    elif anim == "punch":
        guard(P)
        P.update(l_hip=0.22, r_hip=-0.16, l_knee=0.22, r_knee=0.16, pelvis_y=0.92, lean=0.08)
        lt = (t * 2) % 1.0
        ext = [0.25, 1.0, 1.0, 0.45][int(lt * 4)]
        if t < 0.5:
            P.update(twist=-0.15 - 0.3 * ext, l_sh=0.75 + 0.75 * ext, l_elbow=2.2 - 2.1 * ext, l_sh_abd=-0.05 - 0.1 * ext)
        else:
            P.update(twist=-0.15 + 0.55 * ext, r_sh=0.65 + 0.85 * ext, r_elbow=2.3 - 2.2 * ext,
                     r_sh_abd=-0.05 - 0.15 * ext, r_hip=-0.26, lean=0.08 + 0.1 * ext)
    elif anim == "melee":
        # sağ elle yatay savuruş
        i = min(int(t * 6), 5)
        k = [0.0, -0.3, 1.0, 1.0, 0.6, 0.2][i]
        P.update(l_hip=0.2, r_hip=-0.15, l_knee=0.2, r_knee=0.15, pelvis_y=0.92,
                 twist=-0.6 * (1 - k) + 0.7 * k, r_sh=1.2, r_sh_abd=0.4 - 0.9 * k, r_elbow=0.5,
                 r_sh_yaw=0.6 - 1.4 * k, l_sh=0.5, l_elbow=1.6, lean=0.1 + 0.08 * k)
    elif anim in ("pistol_idle", "pistol_walk"):
        if anim == "pistol_walk":
            legs_walk(P, ph, 0.36)
        else:
            legs_idle(P, t)
        pistol_arms(P)
    elif anim in ("rifle_idle", "rifle_walk"):
        if anim == "rifle_walk":
            legs_walk(P, ph, 0.36)
        else:
            legs_idle(P, t)
        rifle_arms(P)
    elif anim == "cast":
        i = min(int(t * 6), 5)
        k = [0.2, 0.6, 1.0, 1.0, 0.7, 0.3][i]
        legs_idle(P, 0)
        P.update(l_sh=0.4 + 1.9 * k, r_sh=0.4 + 1.9 * k, l_sh_abd=0.5, r_sh_abd=0.5, l_elbow=0.3, r_elbow=0.3,
                 head_pitch=-0.35 * k, lean=-0.1 * k)
    elif anim == "hurt":
        k = [1.0, 0.6][int(t * 2)]
        P.update(lean=-0.25 * k, head_pitch=-0.4 * k, l_knee=0.25, r_knee=0.25, pelvis_y=0.92,
                 l_sh=0.3, r_sh=0.3, l_elbow=1.0, r_elbow=1.0)
    elif anim == "death":
        i = min(int(t * 6), 5)
        fall = [0.0, -0.25, -0.65, -1.15, -1.45, -1.52][i]
        P.update(fall=fall, fall_pivot=-0.15, lean=[-0.2, -0.35, -0.3, -0.2, 0.0, 0.0][i], head_pitch=-0.3,
                 l_knee=[0.4, 0.8, 0.6, 0.3, 0.1, 0.1][i], r_knee=[0.4, 0.9, 0.7, 0.4, 0.2, 0.15][i],
                 l_sh=[0.3, 0.6, 0.9, 1.0, 1.0, 1.0][i], r_sh=[0.3, 0.5, 0.8, 0.9, 0.9, 0.9][i],
                 l_sh_abd=[0.3, 0.6, 0.9, 1.1, 1.2, 1.2][i], r_sh_abd=[0.3, 0.7, 1.0, 1.2, 1.3, 1.3][i],
                 l_elbow=0.5, r_elbow=0.4)
        P["root"] = (0.0, [0, 0.0, 0.02, 0.06, 0.1, 0.1][i], 0.0)
    elif anim == "ride":
        P.update(pelvis_y=0.78, l_hip=1.3, r_hip=1.3, l_knee=1.4, r_knee=1.4, lean=0.3,
                 l_sh=1.0, r_sh=1.0, l_sh_abd=-0.1, r_sh_abd=-0.1, l_elbow=0.4, r_elbow=0.4)
    return P


def zombie_pose(anim, t, kind="walker"):
    s = math.sin
    hunch = 0.38 if kind not in ("mystery",) else 0.05
    P = dict(lean=hunch, head_pitch=-0.25, side_lean=0.08, head_yaw=0.1,
             l_sh=1.35, l_sh_abd=0.05, l_elbow=0.25, r_sh=0.25, r_sh_abd=0.15, r_elbow=0.3,
             pelvis_y=0.9, l_knee=0.25, r_knee=0.3)
    if kind in ("tank", "titan"):
        P.update(l_sh=0.4, r_sh=0.4, l_sh_abd=0.35, r_sh_abd=0.35, l_elbow=0.6, r_elbow=0.6, lean=0.25)
    if kind == "mystery":
        P.update(l_sh=0.9, r_sh=0.9, l_elbow=0.2, r_elbow=0.2, pelvis_y=1.0, l_knee=0.4, r_knee=0.4)
    if anim == "idle":
        b = s(TAU * t)
        P.update(side_lean=0.08 + 0.05 * b, head_yaw=0.15 * b, breathe=0.006 * b)
        if kind == "mystery":
            P["root"] = (0, 0.03 * b, 0)
    elif anim == "walk":
        ph = TAU * t
        sp = 1.3 if kind == "runner" else 1.0
        P.update(l_hip=0.35 * sp * s(ph), r_hip=-0.22 * sp * s(ph),
                 l_knee=0.25 + 0.5 * max(0.0, -s(ph + 0.6)), r_knee=0.3 + 0.25 * max(0.0, s(ph + 0.6)),
                 pelvis_y=P["pelvis_y"] - 0.01 + 0.03 * abs(math.cos(ph)), side_lean=0.08 + 0.08 * s(ph),
                 twist=0.12 * s(ph))
        if kind not in ("tank", "titan", "mystery"):
            P.update(l_sh=1.3 + 0.12 * s(ph), r_sh=0.25 - 0.25 * s(ph))
        if kind == "runner":
            P.update(lean=0.5, l_sh=-0.6 * s(ph) + 0.4, r_sh=0.6 * s(ph) + 0.4, l_elbow=1.3, r_elbow=1.3)
        if kind == "mystery":
            P.update(l_hip=0.1, r_hip=0.1, root=(0, 0.05 * s(ph * 2), 0))
    elif anim == "attack":
        i = min(int(t * 6), 5)
        r = [0.4, 1.0, 1.0, 0.2, -0.3, 0.0][i]
        if kind in ("tank", "titan"):
            P.update(l_sh=0.4 + 2.2 * max(r, 0), r_sh=0.4 + 2.2 * max(r, 0), l_elbow=0.3, r_elbow=0.3,
                     lean=0.25 + [0.0, -0.25, -0.3, 0.45, 0.5, 0.2][i], l_knee=0.4, r_knee=0.4)
        else:
            P.update(l_sh=1.35 + 1.0 * r, r_sh=0.9 + 1.2 * r, r_sh_abd=0.1, r_elbow=0.3,
                     lean=hunch + [0.0, -0.15, -0.15, 0.25, 0.35, 0.1][i], l_hip=0.25, r_hip=-0.1)
    elif anim == "hurt":
        k = [1.0, 0.6][int(t * 2)]
        P.update(lean=hunch - 0.5 * k, head_pitch=-0.5 * k)
    elif anim == "death":
        i = min(int(t * 6), 5)
        fall = [0.0, 0.25, 0.65, 1.1, 1.42, 1.5][i]
        P.update(fall=fall, fall_pivot=0.2, lean=hunch - 0.06 * i,
                 l_sh=[1.35, 1.8, 2.3, 2.7, 2.9, 2.9][i], r_sh=[0.3, 0.9, 1.6, 2.2, 2.6, 2.7][i],
                 l_knee=[0.3, 0.6, 0.5, 0.3, 0.1, 0.1][i], r_knee=[0.3, 0.7, 0.6, 0.3, 0.1, 0.1][i])
        P["root"] = (0.0, [0, 0.0, 0.02, 0.06, 0.1, 0.1][i], 0.0)
    return P


HERO_ANIMS = {
    # ad: (kare, fps, döngü)
    "idle": (6, 6, True), "walk": (8, 10, True), "run": (8, 14, True), "punch": (8, 14, True),
    "melee": (6, 12, True), "pistol_idle": (2, 3, True), "pistol_walk": (8, 10, True),
    "rifle_idle": (2, 3, True), "rifle_walk": (8, 10, True), "cast": (6, 12, False),
    "hurt": (2, 10, False), "death": (6, 9, False), "ride": (1, 1, True),
}
ZOMBIE_ANIMS = {"idle": (4, 4, True), "walk": (8, 7, True), "attack": (6, 10, False), "hurt": (2, 10, False), "death": (6, 8, False)}
NPC_ANIMS = {"soldier": {"rifle_idle": (2, 3, True)}, "merchant": {"idle": (6, 5, True)}}


def attach_points(J, scale=1.0):
    """Ek parçalar için dünya noktaları (ölçekli)."""
    return {
        "rhand": scale * J["r_fist"], "lhand": scale * J["l_fist"], "head": scale * J["head"],
        "back": scale * J["back"], "chest": scale * J["chest"], "neck": scale * J["neck"],
    }


# ------------------------------------------------------------------ köpek
def dog_skeleton(anim, t, yaw):
    """Dört ayaklı basit iskelet → SDF sahnesi."""
    s = math.sin
    ph = TAU * t
    body_y = 0.42
    lean = 0.0
    legs = [0.0, 0.0, 0.0, 0.0]
    head_drop = 0.0
    fall = 0.0
    if anim == "walk":
        legs = [0.5 * s(ph), -0.5 * s(ph), -0.5 * s(ph), 0.5 * s(ph)]
        body_y += 0.02 * abs(math.cos(ph))
    elif anim == "idle":
        body_y += 0.005 * s(ph)
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
    R = rot_y(yaw) @ rot_z(fall) @ rot_x(lean)
    def w(p):
        return R @ np.asarray(p, float)
    sc = []
    torso_a, torso_b = w(v3(0, body_y, -0.26)), w(v3(0, body_y + 0.03, 0.22))
    sc.append(("dog_fur", sd_round_cone(torso_a, torso_b, 0.12, 0.14)))
    neck = w(v3(0, body_y + 0.14, 0.32))
    head = w(v3(0, body_y + 0.2 - head_drop, 0.42))
    sc.append(("dog_fur", sd_round_cone(torso_b, neck, 0.11, 0.08)))
    sc.append(("dog_fur", sd_sphere(head, 0.09)))
    sc.append(("dog_skin", sd_round_cone(head, w(v3(0, body_y + 0.16 - head_drop, 0.55)), 0.06, 0.04)))
    for sx in (-1, 1):
        sc.append(("dog_fur", sd_round_cone(head + w(v3(sx * 0.05, 0.06, -0.02)), head + w(v3(sx * 0.07, 0.15, -0.04)), 0.03, 0.01)))
    sc.append(("dog_fur", sd_round_cone(torso_a, w(v3(0, body_y + 0.15, -0.45)), 0.04, 0.02)))
    hips = [(-0.09, 0.2), (0.09, 0.2), (-0.09, -0.22), (0.09, -0.22)]
    for (hx, hz), a in zip(hips, legs):
        top = w(v3(hx, body_y - 0.05, hz))
        foot = w(v3(hx, 0.02, hz + 0.25 * math.sin(a)))
        sc.append(("dog_fur", sd_round_cone(top, foot, 0.055, 0.03)))
    eyes = [(head + w(v3(sx * 0.04, 0.03, 0.07)), "#ff3a2a", w(v3(0, 0, 1))) for sx in (-1, 1)]
    return sc, eyes
