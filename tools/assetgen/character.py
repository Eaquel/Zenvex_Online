"""İnsan iskeleti + karakter/zombi modelleri + animasyon pozları."""
import math
import numpy as np
from sdf import (Material, sd_sphere, sd_round_cone, sd_round_box, sd_ellipsoid, sd_cut,
                 rot_x, rot_y, rot_z, v3)

TAU = 2 * math.pi

MATERIALS = {
    "skin": Material("#f2c6a0", "#d99a74", "#a8684a", "#6e3f2e"),
    "mustard": Material("#ffd25e", "#e8a93a", "#b9772a", "#7a4a1f"),
    "jeans": Material("#4a6fa5", "#33507e", "#23365a", "#172238"),
    "hair": Material("#6e4a35", "#4f3322", "#3a2418", "#1b1c26"),
    "wrap": Material("#ffffff", "#eef2f0", "#c7d3cf", "#6c6c7c"),
    "shoe": Material("#eef2f0", "#c7d3cf", "#9a9aa6", "#45465a"),
    "pack": Material("#b07848", "#8a5a3c", "#66402b", "#45291d"),
    "zskin": Material("#b5cf95", "#8daa74", "#5f7c55", "#34463a"),
    "zshirt": Material("#8a6fa0", "#6a5385", "#4a3a62", "#241c33"),
    "zshorts": Material("#8a8a96", "#6c6c7c", "#4d4e5e", "#2b2d3c"),
    "zhair": Material("#4a4a40", "#35352e", "#26261f", "#141410"),
    "zshoe": Material("#7d5233", "#5e3d27", "#45291d", "#2a1810"),
}


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def limb_dir(pitch, abd, side):
    """Aşağı sarkan uzvu öne (pitch) ve yana (abd) kaldırır. side: +1 sol, -1 sağ."""
    return np.array([side * math.sin(abd) * math.cos(pitch),
                     -math.cos(pitch) * math.cos(abd),
                     math.sin(pitch)])


def bend(u, ang):
    """Dirsek/diz: u'yu ön-üst yönüne doğru ang kadar büker (negatif: geriye)."""
    w = unit([0, 0.3, 1.0])
    h = np.cross(u, w)
    if np.linalg.norm(h) < 1e-6:
        h = np.array([-1.0, 0, 0])
    h = unit(h)
    return u * math.cos(ang) + np.cross(h, u) * math.sin(ang)


DEFAULT_POSE = dict(
    root=(0.0, 0.0, 0.0), pelvis_y=0.95, yaw=0.0, fall=0.0, fall_pivot=0.0,
    lean=0.0, twist=0.0, side_lean=0.0, head_pitch=0.0, head_yaw=0.0, breathe=0.0,
    l_hip=0.0, l_hip_abd=0.06, l_knee=0.0, r_hip=0.0, r_hip_abd=0.06, r_knee=0.0,
    l_sh=0.0, l_sh_abd=0.12, l_elbow=0.1, r_sh=0.0, r_sh_abd=0.12, r_elbow=0.1,
)


def skeleton(pose):
    P = dict(DEFAULT_POSE)
    P.update(pose)
    # gövde (body) matrisi: yön + düşme
    B = rot_y(P["yaw"]) @ rot_x(P["fall"])
    root = np.array(P["root"], float)
    pivot = np.array([0.0, 0.0, P["fall_pivot"]])

    def body(p):  # gövde yerel -> dünya
        return root + B @ (np.asarray(p, float) - pivot) + rot_y(P["yaw"]) @ pivot

    pelvis_l = v3(0, P["pelvis_y"], 0)
    S = rot_y(P["twist"]) @ rot_x(P["lean"]) @ rot_z(P["side_lean"])  # omurga

    def spine(p):  # pelvis'e göre omurga yerel -> gövde yerel
        return pelvis_l + S @ np.asarray(p, float)

    J = {}
    J["pelvis"] = body(pelvis_l)
    J["chest"] = body(spine([0, 0.30 + P["breathe"], 0]))
    J["neck"] = body(spine([0, 0.50 + P["breathe"], 0.0]))
    H = S @ rot_x(P["head_pitch"]) @ rot_y(P["head_yaw"])
    head_l = spine([0, 0.50 + P["breathe"], 0]) + H @ v3(0, 0.13, 0.015)
    J["head"] = body(head_l)
    J["H"] = B @ H
    J["S"] = B @ S
    J["B"] = B
    for side, pre in ((1, "l"), (-1, "r")):
        sh_l = spine([side * 0.19, 0.44 + P["breathe"], -0.01])
        u = S @ limb_dir(P[pre + "_sh"], P[pre + "_sh_abd"], side)
        f = S @ bend(limb_dir(P[pre + "_sh"], P[pre + "_sh_abd"], side), P[pre + "_elbow"])
        el_l = sh_l + u * 0.29
        wr_l = el_l + f * 0.26
        J[pre + "_shoulder"] = body(sh_l)
        J[pre + "_elbow"] = body(el_l)
        J[pre + "_wrist"] = body(wr_l)
        J[pre + "_fist"] = body(wr_l + f * 0.05)
        hip_l = pelvis_l + v3(side * 0.095, -0.03, 0)
        tu = limb_dir(P[pre + "_hip"], P[pre + "_hip_abd"], side)
        tl = bend(tu, -P[pre + "_knee"])
        kn_l = hip_l + tu * 0.44
        an_l = kn_l + tl * 0.42
        J[pre + "_hip"] = body(hip_l)
        J[pre + "_knee"] = body(kn_l)
        J[pre + "_ankle"] = body(an_l)
        # ayak her zaman gövde yönünde, zemine paralel
        J[pre + "_toe"] = body(an_l + v3(0, -0.04, 0.13))
        J[pre + "_heel"] = body(an_l + v3(0, -0.05, -0.03))
    return J


def frame_from(R):
    return R


def human_scene(J, kind="player"):
    """İskeletten SDF sahnesi kurar."""
    zombie = kind == "zombie"
    skin = "zskin" if zombie else "skin"
    top = "zshirt" if zombie else "mustard"
    bottom = "zshorts" if zombie else "jeans"
    hair = "zhair" if zombie else "hair"
    shoe = "zshoe" if zombie else "shoe"
    S, H = J["S"], J["H"]
    sc = []
    # bacaklar
    for pre in ("l", "r"):
        sc.append((bottom, sd_round_cone(J[pre + "_hip"], J[pre + "_knee"], 0.085, 0.062)))
        if zombie:
            mid = J[pre + "_knee"] * 0.75 + J[pre + "_ankle"] * 0.25
            sc.append((bottom, sd_round_cone(J[pre + "_knee"], mid, 0.066, 0.06)))
            sc.append((skin, sd_round_cone(mid, J[pre + "_ankle"], 0.048, 0.04)))
        else:
            sc.append((bottom, sd_round_cone(J[pre + "_knee"], J[pre + "_ankle"], 0.062, 0.05)))
        fwd = J[pre + "_toe"] - J[pre + "_heel"]
        c = (J[pre + "_toe"] + J[pre + "_heel"]) / 2
        z = fwd / np.linalg.norm(fwd)
        y = J["B"] @ v3(0, 1, 0)
        x = np.cross(y, z)
        R = np.stack([x, y, z], axis=1)
        if zombie and pre == "r":
            sc.append((skin, sd_round_box(c, R, (0.045, 0.03, 0.09), 0.025)))  # çıplak ayak
        else:
            sc.append((shoe, sd_round_box(c, R, (0.05, 0.035, 0.1), 0.025)))
    # kalça + gövde
    sc.append((bottom, sd_round_box(J["pelvis"] + S @ v3(0, 0.02, 0), S, (0.13, 0.08, 0.085), 0.05)))
    sc.append((top, sd_round_box(J["pelvis"] + S @ v3(0, 0.24, 0), S, (0.155, 0.27, 0.095), 0.08)))
    if not zombie:
        # kapüşon (ense) + sırt çantası
        sc.append((top, sd_ellipsoid(J["neck"] + S @ v3(0, -0.03, -0.07), S, (0.13, 0.07, 0.07))))
        sc.append(("pack", sd_round_box(J["pelvis"] + S @ v3(0, 0.29, -0.14), S, (0.12, 0.15, 0.06), 0.04)))
    # boyun + kafa + saç
    sc.append((skin, sd_round_cone(J["neck"] + S @ v3(0, -0.06, 0), J["head"] - H @ v3(0, 0.05, 0), 0.05, 0.048)))
    sc.append((skin, sd_ellipsoid(J["head"], H, (0.104, 0.125, 0.114))))
    hair_c = J["head"] + H @ v3(0, 0.022, -0.012)
    hair_f = sd_ellipsoid(hair_c, H, (0.115, 0.126, 0.125))
    n = H @ unit([0, 1.0, -0.75])
    sc.append((hair, sd_cut(hair_f, n, float(n @ J["head"]) - 0.035)))
    if not zombie:
        # alna düşen tutam
        sc.append((hair, sd_sphere(J["head"] + H @ v3(0.03, 0.075, 0.075), 0.03)))
    # kollar
    for pre in ("l", "r"):
        if zombie:
            sc.append((top, sd_round_cone(J[pre + "_shoulder"], (J[pre + "_shoulder"] + J[pre + "_elbow"]) / 2, 0.065, 0.058)))
            sc.append((skin, sd_round_cone((J[pre + "_shoulder"] + J[pre + "_elbow"]) / 2, J[pre + "_elbow"], 0.048, 0.045)))
            sc.append((skin, sd_round_cone(J[pre + "_elbow"], J[pre + "_wrist"], 0.044, 0.036)))
            sc.append((skin, sd_round_cone(J[pre + "_wrist"], J[pre + "_fist"] + (J[pre + "_fist"] - J[pre + "_wrist"]) * 0.6, 0.04, 0.028)))
        else:
            sc.append((top, sd_round_cone(J[pre + "_shoulder"], J[pre + "_elbow"], 0.068, 0.058)))
            sc.append((skin, sd_round_cone(J[pre + "_elbow"], J[pre + "_wrist"], 0.046, 0.038)))
            sc.append(("wrap", sd_sphere(J[pre + "_fist"], 0.052)))
    return sc


def human_decals(J, kind="player"):
    H = J["H"]
    fwd = H @ v3(0, 0, 1)
    col = "#ffe066" if kind == "zombie" else "#1b1c26"
    col2 = "#e8f0e0" if kind == "zombie" else "#1b1c26"
    d = []
    for side, c in ((1, col), (-1, col2)):
        d.append((J["head"] + H @ v3(side * 0.038, 0.012, 0.1), c, fwd))
    return d


# ---------------------------------------------------------------- animasyonlar
def guard(P):
    P.update(l_sh=0.75, l_sh_abd=-0.08, l_elbow=2.2, r_sh=0.65, r_sh_abd=-0.08, r_elbow=2.3)
    return P


def player_pose(anim, t):
    s = math.sin
    P = {}
    if anim == "idle":
        guard(P)
        b = s(TAU * t)
        P.update(breathe=0.008 * b, pelvis_y=0.93 + 0.004 * b,
                 l_hip=0.16, r_hip=-0.08, l_knee=0.2, r_knee=0.14, lean=0.04, twist=-0.15)
        P["l_elbow"] += 0.05 * b
        P["r_elbow"] -= 0.05 * b
    elif anim == "walk":
        ph = TAU * t
        P.update(l_sh=-0.38 * s(ph) + 0.05, r_sh=0.38 * s(ph) + 0.05,
                 l_elbow=0.35 + 0.25 * max(0.0, -s(ph)), r_elbow=0.35 + 0.25 * max(0.0, s(ph)),
                 l_sh_abd=0.1, r_sh_abd=0.1)
        P.update(l_hip=0.42 * s(ph), r_hip=-0.42 * s(ph),
                 l_knee=0.08 + 0.55 * max(0.0, -s(ph + 0.6)) + 0.1,
                 r_knee=0.08 + 0.55 * max(0.0, s(ph + 0.6)) + 0.1,
                 pelvis_y=0.94 + 0.018 * abs(math.cos(ph)), lean=0.05,
                 twist=0.08 * s(ph))
    elif anim == "run":
        ph = TAU * t
        P.update(l_hip=0.75 * s(ph) + 0.1, r_hip=-0.75 * s(ph) + 0.1,
                 l_knee=0.25 + 1.1 * max(0.0, -s(ph + 0.8)),
                 r_knee=0.25 + 1.1 * max(0.0, s(ph + 0.8)),
                 pelvis_y=0.9 + 0.04 * abs(math.cos(ph)), lean=0.22,
                 twist=0.14 * s(ph),
                 l_sh=-0.7 * s(ph) + 0.2, l_elbow=1.6, l_sh_abd=0.15,
                 r_sh=0.7 * s(ph) + 0.2, r_elbow=1.6, r_sh_abd=0.15)
    elif anim == "punch":
        # 0-0.5: sol jab, 0.5-1: sağ düz yumruk
        guard(P)
        P.update(l_hip=0.22, r_hip=-0.16, l_knee=0.22, r_knee=0.16, pelvis_y=0.92, lean=0.08)
        lt = (t * 2) % 1.0
        ext = [0.25, 1.0, 1.0, 0.45][int(lt * 4)]
        if t < 0.5:
            P.update(twist=-0.15 - 0.3 * ext, l_sh=0.75 + 0.75 * ext, l_elbow=2.2 - 2.1 * ext,
                     l_sh_abd=-0.05 - 0.1 * ext)
        else:
            P.update(twist=-0.15 + 0.55 * ext, r_sh=0.65 + 0.85 * ext, r_elbow=2.3 - 2.2 * ext,
                     r_sh_abd=-0.05 - 0.15 * ext, r_hip=-0.16 - 0.1 * ext, lean=0.08 + 0.1 * ext)
    elif anim == "hurt":
        guard(P)
        k = [1.0, 0.6][int(t * 2)]
        P.update(lean=-0.25 * k, head_pitch=-0.35 * k, l_knee=0.25, r_knee=0.25,
                 pelvis_y=0.92, l_sh=0.2, r_sh=0.2, l_elbow=1.2, r_elbow=1.2)
    elif anim == "death":
        i = min(int(t * 6), 5)
        fall = [0.0, -0.25, -0.65, -1.15, -1.45, -1.52][i]
        P.update(fall=fall, fall_pivot=-0.15, lean=[-0.2, -0.35, -0.3, -0.2, 0.0, 0.0][i],
                 head_pitch=-0.3, l_knee=[0.4, 0.8, 0.6, 0.3, 0.1, 0.1][i],
                 r_knee=[0.4, 0.9, 0.7, 0.4, 0.2, 0.15][i], pelvis_y=0.95 - [0.05, 0.25, 0.4, 0.55, 0.62, 0.62][i] * 0.0,
                 l_sh=[0.3, 0.6, 0.9, 1.0, 1.0, 1.0][i], r_sh=[0.3, 0.5, 0.8, 0.9, 0.9, 0.9][i],
                 l_sh_abd=[0.3, 0.6, 0.9, 1.1, 1.2, 1.2][i], r_sh_abd=[0.3, 0.7, 1.0, 1.2, 1.3, 1.3][i],
                 l_elbow=0.5, r_elbow=0.4)
        P["root"] = (0.0, [0, 0.0, 0.02, 0.06, 0.1, 0.1][i], 0.0)
    return P


def zombie_pose(anim, t):
    s = math.sin
    P = dict(lean=0.38, head_pitch=-0.15, side_lean=0.08, head_yaw=0.1,
             l_sh=1.35, l_sh_abd=0.05, l_elbow=0.25, r_sh=0.25, r_sh_abd=0.15, r_elbow=0.3,
             pelvis_y=0.9, l_knee=0.25, r_knee=0.3)
    if anim == "idle":
        b = s(TAU * t)
        P.update(side_lean=0.08 + 0.05 * b, head_yaw=0.15 * b, l_sh=1.3 + 0.08 * b, breathe=0.006 * b)
    elif anim == "walk":
        ph = TAU * t
        P.update(l_hip=0.35 * s(ph), r_hip=-0.22 * s(ph),
                 l_knee=0.25 + 0.5 * max(0.0, -s(ph + 0.6)),
                 r_knee=0.3 + 0.2 * max(0.0, s(ph + 0.6)),
                 pelvis_y=0.89 + 0.03 * abs(math.cos(ph)), side_lean=0.08 + 0.08 * s(ph),
                 twist=0.12 * s(ph), l_sh=1.3 + 0.12 * s(ph), r_sh=0.25 - 0.25 * s(ph))
    elif anim == "attack":
        i = min(int(t * 6), 5)
        raise_ = [0.4, 1.0, 1.0, 0.2, -0.3, 0.0][i]
        P.update(l_sh=1.35 + 1.0 * raise_, r_sh=0.9 + 1.2 * raise_, r_sh_abd=0.1,
                 r_elbow=0.3, lean=0.38 + [0.0, -0.15, -0.15, 0.25, 0.35, 0.1][i],
                 l_hip=0.25, r_hip=-0.1)
    elif anim == "hurt":
        k = [1.0, 0.6][int(t * 2)]
        P.update(lean=0.38 - 0.5 * k, head_pitch=-0.5 * k, l_sh=0.9, r_sh=0.3)
    elif anim == "death":
        i = min(int(t * 6), 5)
        fall = [0.0, 0.25, 0.65, 1.1, 1.42, 1.5][i]
        P.update(fall=fall, fall_pivot=0.2, lean=0.38 - 0.06 * i,
                 l_sh=[1.35, 1.8, 2.3, 2.7, 2.9, 2.9][i], r_sh=[0.3, 0.9, 1.6, 2.2, 2.6, 2.7][i],
                 l_knee=[0.3, 0.6, 0.5, 0.3, 0.1, 0.1][i], r_knee=[0.3, 0.7, 0.6, 0.3, 0.1, 0.1][i])
        P["root"] = (0.0, [0, 0.0, 0.02, 0.06, 0.1, 0.1][i], 0.0)
    return P


ANIMS = {
    # ad: (kare sayısı, fps, döngü)
    "idle": (4, 5, True),
    "walk": (8, 10, True),
    "run": (8, 14, True),
    "punch": (8, 14, True),
    "hurt": (2, 10, False),
    "death": (6, 9, False),
}
ZOMBIE_ANIMS = {
    "idle": (4, 4, True),
    "walk": (8, 7, True),
    "attack": (6, 10, False),
    "hurt": (2, 10, False),
    "death": (6, 8, False),
}
