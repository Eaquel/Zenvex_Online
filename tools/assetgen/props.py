"""V2 nesneler: silahlar, kask/çanta/yelek/pelerin, araçlar, binalar, sahne objeleri, eşya modelleri.

Tüm modeller "yerel" kurulur: +z ileri, +y yukarı. Yön (yaw) uygulanması render tarafında yapılır.
"""
import math
import numpy as np
from sdf2 import (Mat, sd_sphere, sd_round_cone, sd_round_box, sd_ellipsoid, sd_cut, sd_displace, sd_cylinder,
                  sd_torus, sd_shell, rot_x, rot_y, rot_z, v3)

I3 = np.eye(3)

PMATS = {
    "gun_black": Mat("#2e3036", "metal", spec=0.7),
    "gun_dark": Mat("#44464e", "metal", spec=0.6),
    "gun_wood": Mat("#8a5232", "bark", spec=0.2),
    "gun_olive": Mat("#5b6a3e", "metal", spec=0.4),
    "gun_tan": Mat("#b8996a", "metal", spec=0.3),
    "warhead": Mat("#6c7a40", "metal", spec=0.5),
    "lens": Mat("#5ec8ff", emissive=True),
    "wood": Mat("#a87448", "bark"),
    "steel": Mat("#9aa0aa", "metal", spec=0.85),
    "rubber": Mat("#2a2a2e", "leather"),
    # zırh
    "helm_basic": Mat("#8a8c84", "metal", spec=0.4),
    "helm_police": Mat("#24304a", "metal", spec=0.6),
    "visor": Mat("#8ad0ff", "glass", spec=0.9),
    "helm_mil": Mat("#56643a", "metal", spec=0.3),
    "titan": Mat("#4a4c56", "metal", spec=0.8),
    "titan_glow": Mat("#ff3a2a", emissive=True),
    "titan_gold": Mat("#c99a3a", "metal", spec=0.8),
    "bag_canvas": Mat("#8a6a44", "cloth"),
    "bag_strap": Mat("#4f3a26", "leather"),
    "bag_black": Mat("#2a2c34", "cloth"),
    "police_yel": Mat("#e8c43a", "cloth"),
    "bag_olive": Mat("#56643a", "cloth"),
    "vest_basic": Mat("#6a5a44", "cloth"),
    "vest_police": Mat("#28324a", "cloth"),
    "cloak": Mat("#f2e6c4", "cloth"),
    "cloak_in": Mat("#8a2a3a", "cloth"),
    "cloak_glow": Mat("#ffd25e", emissive=True),
    # araçlar
    "car_red": Mat("#c8282c", "metal", spec=0.9),
    "car_white": Mat("#e6e8ea", "metal", spec=0.9),
    "car_black": Mat("#26282e", "metal", spec=0.8),
    "glass": Mat("#4a6a8a", "glass", spec=1.0),
    "chrome": Mat("#c8ccd4", "metal", spec=1.0),
    "tire": Mat("#202024", "leather"),
    "light_y": Mat("#fff2a8", emissive=True),
    "light_r": Mat("#ff3a3a", emissive=True),
    "bike_blue": Mat("#2a6ac8", "metal", spec=0.8),
    "moto_orange": Mat("#e86a1a", "metal", spec=0.9),
    "suv_green": Mat("#3e5a44", "metal", spec=0.8),
    # binalar
    "brick": Mat("#a8584a", "stone"),
    "brick2": Mat("#8a6a5a", "stone"),
    "plaster": Mat("#d8ccb4", "stone"),
    "plaster2": Mat("#b4c4c8", "stone"),
    "concrete": Mat("#9a9a96", "stone"),
    "concrete_d": Mat("#6c6c6a", "stone"),
    "roof_red": Mat("#9a3a2a", "stone"),
    "roof_grey": Mat("#5a5e66", "stone"),
    "roof_green": Mat("#4a6a4a", "stone"),
    "window": Mat("#3a5a7a", "glass", spec=0.9),
    "window_lit": Mat("#ffd88a", emissive=True),
    "door": Mat("#6a4a2e", "bark"),
    "metal_wall": Mat("#7a7e86", "metal", spec=0.4),
    "rust": Mat("#8a5a3a", "rust"),
    "sandbag": Mat("#b8a070", "cloth"),
    "police_blue": Mat("#2a4a8a", "metal", spec=0.5),
    "sign_y": Mat("#e8c43a", "metal", spec=0.4),
    "barrel_r": Mat("#b8342a", "metal", spec=0.5),
    "barrel_tox": Mat("#d8c43a", "metal", spec=0.5),
    "tox_glow": Mat("#9cff3a", emissive=True),
    "fire": Mat("#ffb03a", emissive=True),
    "stone": Mat("#8a8a90", "stone"),
    "tent": Mat("#5a7a5a", "cloth"),
    "crate": Mat("#a07a4a", "bark"),
    "crate_mil": Mat("#5a6a40", "bark"),
    "leaf_d": Mat("#3e7a3a", "leaf"),
    # eşyalar
    "cloth_i": Mat("#c8b496", "cloth"),
    "glue": Mat("#e8e0d0", "metal", spec=0.5),
    "glue_cap": Mat("#e84a3a", "metal", spec=0.5),
    "resin": Mat("#e8a030", "glass", spec=0.9),
    "leather": Mat("#8a5a3a", "leather"),
    "scrap": Mat("#8a8e96", "rust", spec=0.4),
    "powder": Mat("#3a3a3e", "cloth"),
    "pcb": Mat("#2e7a3e", "metal", spec=0.4),
    "chip": Mat("#2a2a2e", "metal", spec=0.6),
    "gold": Mat("#f2c23a", "metal", spec=0.9),
    "tooth": Mat("#eeeadc", "skin"),
    "vial": Mat("#c8e8f0", "glass", spec=0.9),
    "tox": Mat("#8cff3a", emissive=True),
    "dark_c": Mat("#9a4aff", emissive=True),
    "blue_l": Mat("#4ac8ff", emissive=True),
    "bandage": Mat("#f2f0ea", "cloth"),
    "med_red": Mat("#d8343c", "metal", spec=0.4),
    "white": Mat("#f2f2f2", "metal", spec=0.3),
    "can": Mat("#c8ccd4", "metal", spec=0.9),
    "label_r": Mat("#c83a2a", "cloth"),
    "label_g": Mat("#3ac83a", "cloth"),
    "ammo_box": Mat("#5a6a40", "metal", spec=0.3),
    "brass": Mat("#d8a83a", "metal", spec=0.9),
    "jerry": Mat("#c8342a", "metal", spec=0.5),
    "gem": Mat("#5ae8ff", "glass", spec=1.0),
    "log": Mat("#8a5a3c", "bark"),
    "log_end": Mat("#e0b07a", "bark"),
    "rock": Mat("#9a9aa2", "stone"),
}


def box(c, half, r=0.01, R=I3):
    return sd_round_box(v3(*c), R, half, r)


def cyl(a, b, r1, r2=None):
    return sd_round_cone(v3(*a), v3(*b), r1, r1 if r2 is None else r2)


# ------------------------------------------------------------------ silahlar (kabza başlangıçta, +z namlu)
def weapon(kind):
    s = []
    if kind == "glock":
        s += [("gun_black", box((0, 0.045, 0.06), (0.018, 0.022, 0.095), 0.006)),
              ("gun_dark", box((0, 0.0, 0.0), (0.016, 0.05, 0.022), 0.006, rot_x(-0.25))),
              ("gun_dark", box((0, 0.015, 0.05), (0.015, 0.01, 0.03), 0.004))]
    elif kind == "ak47":
        s += [("gun_dark", box((0, 0.03, 0.12), (0.022, 0.035, 0.16), 0.008)),
              ("gun_wood", box((0, 0.02, -0.17), (0.02, 0.04, 0.13), 0.012, rot_x(0.12))),
              ("gun_wood", box((0, 0.02, 0.33), (0.022, 0.028, 0.08), 0.01)),
              ("gun_black", cyl((0, 0.045, 0.4), (0, 0.045, 0.62), 0.009)),
              ("gun_dark", box((0, -0.06, 0.16), (0.016, 0.07, 0.025), 0.008, rot_x(-0.35))),
              ("gun_wood", box((0, -0.04, 0.0), (0.015, 0.045, 0.02), 0.006, rot_x(-0.3)))]
    elif kind == "m4":
        s += [("gun_black", box((0, 0.035, 0.1), (0.022, 0.035, 0.16), 0.006)),
              ("gun_black", box((0, 0.03, -0.15), (0.018, 0.035, 0.1), 0.01)),
              ("gun_dark", box((0, 0.04, 0.33), (0.026, 0.028, 0.09), 0.008)),
              ("gun_black", cyl((0, 0.04, 0.4), (0, 0.04, 0.6), 0.008)),
              ("gun_black", box((0, -0.05, 0.15), (0.016, 0.06, 0.022), 0.006, rot_x(-0.15))),
              ("gun_dark", box((0, 0.09, 0.1), (0.012, 0.015, 0.07), 0.005)),
              ("lens", sd_sphere(v3(0, 0.09, 0.17), 0.008)),
              ("gun_dark", box((0, -0.035, 0.0), (0.015, 0.045, 0.02), 0.006, rot_x(-0.3)))]
    elif kind == "sniper":
        s += [("gun_olive", box((0, 0.03, 0.1), (0.024, 0.04, 0.2), 0.01)),
              ("gun_olive", box((0, 0.02, -0.2), (0.02, 0.045, 0.13), 0.014, rot_x(0.1))),
              ("gun_black", cyl((0, 0.05, 0.3), (0, 0.05, 0.85), 0.01)),
              ("gun_black", cyl((0, 0.11, -0.02), (0, 0.11, 0.24), 0.02)),
              ("lens", sd_sphere(v3(0, 0.11, 0.25), 0.014)),
              ("gun_black", box((0, -0.04, 0.12), (0.014, 0.04, 0.02), 0.006))]
    elif kind == "rpg":
        s += [("gun_olive", cyl((0, 0.06, -0.35), (0, 0.06, 0.55), 0.04)),
              ("warhead", cyl((0, 0.06, 0.6), (0, 0.06, 0.78), 0.06, 0.012)),
              ("gun_wood", box((0, 0.0, 0.0), (0.015, 0.05, 0.02), 0.006, rot_x(-0.2))),
              ("gun_black", box((0, 0.11, 0.1), (0.01, 0.025, 0.03), 0.005))]
    elif kind == "bat":
        s += [("wood", cyl((0, 0.0, -0.08), (0, 0.04, 0.62), 0.016, 0.034))]
    elif kind == "baton":
        s += [("rubber", cyl((0, 0.0, -0.08), (0, 0.02, 0.48), 0.016, 0.018)),
              ("rubber", cyl((0, 0.0, 0.03), (0.0, -0.08, 0.05), 0.012))]
    return s


# ------------------------------------------------------------------ giyilebilirler (bağlantı noktasında, +z karakterin önü)
def helmet(kind):
    cut_y = -0.02
    if kind == "basic":
        return [("helm_basic", sd_cut(sd_ellipsoid(v3(0, 0.03, -0.005), I3, (0.128, 0.12, 0.135)), v3(0, 1, 0), cut_y)),
                ("helm_basic", box((0, -0.005, 0.0), (0.13, 0.008, 0.14), 0.006))]
    if kind == "police":
        return [("helm_police", sd_cut(sd_ellipsoid(v3(0, 0.03, -0.01), I3, (0.13, 0.125, 0.14)), v3(0, 1, 0), cut_y - 0.03)),
                ("visor", sd_cut(sd_ellipsoid(v3(0, 0.0, 0.03), I3, (0.12, 0.08, 0.12)), v3(0, 0, 1), 0.05)),
                ("police_yel", box((0, 0.06, -0.13), (0.06, 0.012, 0.01), 0.004))]
    if kind == "military":
        return [("helm_mil", sd_cut(sd_ellipsoid(v3(0, 0.035, -0.005), I3, (0.135, 0.125, 0.14)), v3(0, 1, 0), cut_y - 0.01)),
                ("bag_strap", box((0, 0.1, 0.05), (0.03, 0.02, 0.05), 0.01))]
    if kind == "titan":
        return [("titan", sd_cut(sd_ellipsoid(v3(0, 0.03, 0), I3, (0.14, 0.14, 0.145)), v3(0, 1, 0), -0.12)),
                ("titan_glow", box((0, 0.01, 0.13), (0.07, 0.01, 0.01), 0.005)),
                ("titan_gold", cyl((0.1, 0.1, 0), (0.17, 0.2, -0.02), 0.025, 0.006)),
                ("titan_gold", cyl((-0.1, 0.1, 0), (-0.17, 0.2, -0.02), 0.025, 0.006))]
    return []


def bag(kind):
    if kind == "basic":
        return [("bag_canvas", box((0, 0, -0.05), (0.12, 0.15, 0.06), 0.04)),
                ("bag_strap", box((0, -0.06, -0.11), (0.09, 0.05, 0.02), 0.02))]
    if kind == "police":
        return [("bag_black", box((0, 0, -0.06), (0.13, 0.17, 0.07), 0.035)),
                ("police_yel", box((0, 0.05, -0.135), (0.1, 0.015, 0.006), 0.004))]
    if kind == "military":
        return [("bag_olive", box((0, 0.02, -0.07), (0.15, 0.2, 0.085), 0.04)),
                ("bag_olive", box((0, -0.12, -0.16), (0.1, 0.06, 0.03), 0.02)),
                ("bag_strap", box((0, 0.2, -0.07), (0.14, 0.03, 0.05), 0.02))]
    return []


def vest(kind):
    m = {"basic": "vest_basic", "police": "vest_police", "titan": "titan"}[kind]
    s = [(m, sd_round_box(v3(0, 0, 0), I3, (0.175, 0.2, 0.115), 0.07))]
    if kind == "police":
        s.append(("police_yel", box((0, 0.08, 0.12), (0.1, 0.02, 0.005), 0.004)))
    if kind == "titan":
        s += [("titan_glow", box((0, 0.05, 0.12), (0.03, 0.03, 0.005), 0.01)),
              ("titan_gold", box((0, -0.12, 0.0), (0.18, 0.02, 0.12), 0.01))]
    return s


def cloak():
    outer = sd_round_cone(v3(0, 0.0, -0.06), v3(0, -0.95, -0.16), 0.16, 0.28)
    shell = sd_shell(outer, 0.012)
    cape = sd_cut(sd_cut(shell, v3(0, 0.05, -1.0), 0.03), v3(0, -1, 0), -0.02)
    return [("cloak", cape), ("cloak_glow", sd_torus(v3(0, -0.01, -0.06), (0, 1, 0), 0.15, 0.018))]


# ------------------------------------------------------------------ araçlar (merkez yerde)
def wheel(c, r, w=0.06, axis="x"):
    a = np.array(c, float)
    ax = np.array([1.0, 0, 0]) if axis == "x" else np.array([0, 0, 1.0])
    tr = min(w, r * 0.35)
    return [("tire", sd_torus(a, ax, r - tr, tr)),
            ("chrome", sd_cylinder(a - ax * w * 0.6, a + ax * w * 0.6, r * 0.62, 0.01))]


def vehicle(kind):
    s = []
    if kind == "bike":
        s += wheel((0, 0.33, 0.5), 0.33, 0.025) + wheel((0, 0.33, -0.5), 0.33, 0.025)
        s += [("bike_blue", cyl((0, 0.36, -0.5), (0, 0.75, -0.05), 0.022)),
              ("bike_blue", cyl((0, 0.75, -0.05), (0, 0.36, 0.0), 0.022)),
              ("bike_blue", cyl((0, 0.36, 0.0), (0, 0.33, -0.5), 0.02)),
              ("bike_blue", cyl((0, 0.75, -0.05), (0, 0.85, 0.42), 0.022)),
              ("bike_blue", cyl((0, 0.85, 0.42), (0, 0.33, 0.5), 0.02)),
              ("rubber", box((0, 0.82, -0.1), (0.06, 0.02, 0.1), 0.02)),
              ("chrome", cyl((-0.25, 0.98, 0.42), (0.25, 0.98, 0.42), 0.015))]
    elif kind == "moto":
        s += wheel((0, 0.32, 0.72), 0.32, 0.06) + wheel((0, 0.32, -0.7), 0.32, 0.07)
        s += [("moto_orange", sd_ellipsoid(v3(0, 0.75, 0.15), I3, (0.17, 0.14, 0.32))),
              ("car_black", box((0, 0.6, -0.15), (0.13, 0.12, 0.35), 0.06)),
              ("rubber", box((0, 0.82, -0.3), (0.15, 0.05, 0.3), 0.04)),
              ("chrome", cyl((0, 0.6, 0.55), (0, 0.95, 0.42), 0.03)),
              ("chrome", cyl((-0.32, 1.0, 0.42), (0.32, 1.0, 0.42), 0.018)),
              ("light_y", sd_sphere(v3(0, 0.88, 0.62), 0.06)),
              ("chrome", cyl((0.12, 0.38, -0.2), (0.16, 0.45, -0.75), 0.04))]
    elif kind in ("car", "suv"):
        long = 2.1 if kind == "car" else 2.3
        width = 0.85 if kind == "car" else 0.95
        hb = 0.36 if kind == "car" else 0.48
        col = "car_red" if kind == "car" else "suv_green"
        y0 = 0.42 if kind == "car" else 0.55
        for x in (-width, width):
            for z in (-long * 0.62, long * 0.62):
                s += wheel((x * 0.98, 0.34 if kind == "car" else 0.4, z), 0.34 if kind == "car" else 0.4, 0.12)
        cab_z = -0.15 if kind == "car" else -0.25
        cab_l = long * (0.45 if kind == "car" else 0.62)
        s += [(col, box((0, y0, 0), (width, hb * 0.6, long), 0.18)),
              ("glass", box((0, y0 + hb * 0.85, cab_z), (width * 0.84, hb * 0.5, cab_l), 0.14)),
              (col, box((0, y0 + hb * 1.32, cab_z - 0.05), (width * 0.8, 0.05, cab_l * 0.82), 0.04)),
              ("light_y", box((width * 0.65, y0 + 0.05, long + 0.02), (0.18, 0.06, 0.03), 0.02)),
              ("light_y", box((-width * 0.65, y0 + 0.05, long + 0.02), (0.18, 0.06, 0.03), 0.02)),
              ("light_r", box((width * 0.7, y0 + 0.08, -long - 0.02), (0.16, 0.05, 0.03), 0.02)),
              ("light_r", box((-width * 0.7, y0 + 0.08, -long - 0.02), (0.16, 0.05, 0.03), 0.02)),
              ("chrome", box((0, y0 - 0.12, long + 0.05), (width * 0.95, 0.05, 0.05), 0.03))]
        if kind == "suv":
            s.append(("car_black", box((0, y0 + hb * 1.5, -0.25), (width * 0.75, 0.03, long * 0.5), 0.02)))
    return s


# ------------------------------------------------------------------ binalar ve sahne objeleri (tek görünüş)
def window_grid(cx, cz, face_z, w, h, cols, rows, y0, dy, lit_seed=0, side="z"):
    rng = np.random.default_rng(lit_seed)
    out = []
    for r in range(rows):
        for c in range(cols):
            x = cx - w / 2 + (c + 0.5) * w / cols
            y = y0 + r * dy
            m = "window_lit" if rng.random() < 0.22 else "window"
            if side == "z":
                out.append((m, box((x, y, face_z), (w / cols * 0.3, 0.42, 0.04), 0.02)))
            else:
                out.append((m, box((face_z, y, x), (0.04, 0.42, w / cols * 0.3), 0.02)))
    return out


def house(kind=0):
    W, D, Hh = 3.4, 2.8, 2.7
    wall = ["plaster", "brick", "plaster2"][kind % 3]
    roof = ["roof_red", "roof_grey", "roof_green"][kind % 3]
    s = [(wall, box((0, Hh / 2, 0), (W, Hh / 2, D), 0.05))]
    # beşik çatı: iki eğik kutu
    for sgn in (-1, 1):
        R = rot_x(sgn * 0.62)
        s.append((roof, sd_round_box(v3(0, Hh + 0.85, sgn * D * 0.48), R, (W + 0.25, 0.09, D * 0.62), 0.03)))
    s.append((wall, sd_cut(sd_cut(box((0, Hh + 0.5, 0), (W - 0.05, 0.9, D - 0.1), 0.02), v3(0, -0.85, 0.53), -Hh * 0.85 - 0.95), v3(0, -0.85, -0.53), -Hh * 0.85 - 0.95)))
    s.append(("door", box((0.8, 1.0, D + 0.02), (0.45, 1.0, 0.05), 0.03)))
    s += window_grid(-1.2, 0, D + 0.03, 2.0, 1, 2, 1, 1.5, 0, kind)
    s.append(("concrete_d", box((1.9, Hh + 1.3, -0.5), (0.25, 0.6, 0.25), 0.02)))  # baca
    return s


def apartment(kind=0, floors=5):
    W, D = 5.0, 3.6
    fh = 2.8
    Hh = floors * fh
    wall = ["concrete", "brick2", "plaster2"][kind % 3]
    s = [(wall, box((0, Hh / 2, 0), (W, Hh / 2, D), 0.05)),
         ("concrete_d", box((0, Hh + 0.15, 0), (W + 0.1, 0.15, D + 0.1), 0.03)),
         ("concrete_d", box((-2.5, Hh + 0.8, -1.2), (0.6, 0.5, 0.6), 0.03)),
         ("metal_wall", box((2.6, Hh + 0.5, 0.8), (0.4, 0.35, 0.4), 0.05))]
    for f in range(floors):
        s += window_grid(0, 0, D + 0.03, 9.0, 1, 6, 1, f * fh + 1.5, 0, kind * 10 + f)
        s += window_grid(0, 0, W + 0.03, 6.4, 1, 4, 1, f * fh + 1.5, 0, kind * 20 + f, side="x")
        s.append(("concrete_d", box((0, f * fh + 0.05, D + 0.05), (W + 0.05, 0.06, 0.06), 0.02)))
    s.append(("door", box((0, 1.1, D + 0.04), (0.7, 1.1, 0.05), 0.03)))
    return s


def shop(kind=0):
    W, D, Hh = 3.8, 3.0, 3.2
    s = [("plaster", box((0, Hh / 2, 0), (W, Hh / 2, D), 0.05)),
         ("concrete_d", box((0, Hh + 0.1, 0), (W + 0.1, 0.12, D + 0.1), 0.03)),
         ("window", box((-1.2, 1.4, D + 0.03), (1.6, 1.0, 0.04), 0.02)),
         ("door", box((1.9, 1.1, D + 0.03), (0.6, 1.1, 0.05), 0.03)),
         (["sign_y", "police_blue", "label_r"][kind % 3], box((0, 2.85, D + 0.2), (W * 0.9, 0.32, 0.12), 0.04)),
         ("label_r", sd_cut(box((0, 2.35, D + 0.45), (W, 0.05, 0.5), 0.02), v3(0, 0, 1), -10))]
    return s


def police_station():
    s = shop(1)
    s += [("police_blue", box((0, 3.6, 0), (2.0, 0.4, 2.0), 0.05)), ("light_r", sd_sphere(v3(-0.6, 4.1, 0), 0.15)),
          ("blue_l", sd_sphere(v3(0.6, 4.1, 0), 0.15))]
    return s


def gas_station():
    s = [("concrete", box((0, 3.2, 0), (3.6, 0.2, 2.6), 0.05))]
    for x in (-3.2, 3.2):
        for z in (-2.2, 2.2):
            s.append(("concrete_d", cyl((x, 0, z), (x, 3.1, z), 0.14)))
    for x in (-1.2, 1.2):
        s += [("label_r", box((x, 0.75, 0), (0.35, 0.75, 0.25), 0.05)), ("window_lit", box((x, 1.1, 0.27), (0.22, 0.15, 0.02), 0.01))]
    s.append(("sign_y", box((0, 3.6, 2.4), (2.0, 0.25, 0.06), 0.03)))
    return s


def wall_segment(length=4.0, axis="x"):
    if axis == "x":
        return [("concrete", box((0, 1.1, 0), (length / 2, 1.1, 0.25), 0.04)),
                ("concrete_d", box((0, 2.25, 0), (length / 2 + 0.05, 0.08, 0.3), 0.02))]
    return [("concrete", box((0, 1.1, 0), (0.25, 1.1, length / 2), 0.04)),
            ("concrete_d", box((0, 2.25, 0), (0.3, 0.08, length / 2 + 0.05), 0.02))]


def gate_barrier():
    return [("concrete_d", box((-2.2, 0.6, 0), (0.3, 0.6, 0.3), 0.04)), ("concrete_d", box((2.2, 0.6, 0), (0.3, 0.6, 0.3), 0.04)),
            ("sign_y", box((0, 1.0, 0), (2.0, 0.1, 0.08), 0.03)), ("label_r", box((-1.0, 1.0, 0.0), (0.25, 0.11, 0.09), 0.02)),
            ("label_r", box((1.0, 1.0, 0.0), (0.25, 0.11, 0.09), 0.02)), ("sandbag", box((0, 0.3, 0.6), (1.5, 0.3, 0.3), 0.15))]


def watchtower():
    s = []
    for x in (-0.9, 0.9):
        for z in (-0.9, 0.9):
            s.append(("wood", cyl((x, 0, z), (x * 0.8, 4.0, z * 0.8), 0.08)))
    s += [("wood", box((0, 4.1, 0), (1.2, 0.1, 1.2), 0.02)), ("sandbag", box((0, 4.5, 1.1), (1.2, 0.35, 0.15), 0.1)),
          ("sandbag", box((1.1, 4.5, 0), (0.15, 0.35, 1.2), 0.1)), ("sandbag", box((-1.1, 4.5, 0), (0.15, 0.35, 1.2), 0.1)),
          ("roof_grey", box((0, 6.0, 0), (1.4, 0.08, 1.4), 0.02))]
    for x in (-1.1, 1.1):
        for z in (-1.1, 1.1):
            s.append(("wood", cyl((x, 4.2, z), (x, 5.95, z), 0.05)))
    return s


def streetlamp():
    return [("concrete_d", cyl((0, 0, 0), (0, 3.6, 0), 0.06, 0.04)), ("concrete_d", cyl((0, 3.6, 0), (0, 3.7, 0.6), 0.04)),
            ("light_y", box((0, 3.6, 0.65), (0.12, 0.06, 0.2), 0.04))]


def car_wreck(kind=0):
    s = vehicle("car" if kind == 0 else "suv")
    out = []
    for m, f in s:
        if m in ("car_red", "suv_green"):
            m = "rust"
        if m in ("light_y", "light_r"):
            m = "concrete_d"
        if m == "glass":
            continue
        out.append((m, f))
    return out


def crate(mil=False):
    m = "crate_mil" if mil else "crate"
    return [(m, box((0, 0.35, 0), (0.4, 0.35, 0.35), 0.03)), ("steel", box((0, 0.35, 0.36), (0.42, 0.04, 0.02), 0.01)),
            ("steel", box((0, 0.6, 0.36), (0.42, 0.04, 0.02), 0.01))]


def barrel(toxic=False):
    m = "barrel_tox" if toxic else "barrel_r"
    s = [(m, cyl((0, 0.0, 0), (0, 0.9, 0), 0.3)), ("concrete_d", cyl((0, 0.3, 0), (0, 0.32, 0), 0.31)),
         ("concrete_d", cyl((0, 0.62, 0), (0, 0.64, 0), 0.31))]
    if toxic:
        s.append(("tox_glow", sd_ellipsoid(v3(0, 0.91, 0), I3, (0.24, 0.02, 0.24))))
    return s


def workbench():
    return [("wood", box((0, 0.85, 0), (0.9, 0.06, 0.45), 0.02)), ("wood", cyl((-0.8, 0, -0.35), (-0.8, 0.8, -0.35), 0.05)),
            ("wood", cyl((0.8, 0, -0.35), (0.8, 0.8, -0.35), 0.05)), ("wood", cyl((-0.8, 0, 0.35), (-0.8, 0.8, 0.35), 0.05)),
            ("wood", cyl((0.8, 0, 0.35), (0.8, 0.8, 0.35), 0.05)), ("steel", box((-0.4, 0.98, 0.1), (0.15, 0.07, 0.1), 0.02)),
            ("steel", cyl((0.3, 0.93, -0.1), (0.6, 0.93, 0.05), 0.02)), ("wood", cyl((0.3, 0.93, -0.1), (0.15, 0.93, -0.2), 0.025))]


def campfire():
    s = []
    for i in range(8):
        a = i * math.tau / 8
        s.append(("stone", sd_displace(sd_sphere(v3(math.cos(a) * 0.45, 0.08, math.sin(a) * 0.45), 0.12), 0.01, 20)))
    for i in range(3):
        a = i * math.tau / 3
        s.append(("log", cyl((math.cos(a) * 0.35, 0.05, math.sin(a) * 0.35), (0, 0.35, 0), 0.06)))
    return s


def tent():
    s = []
    for sgn in (-1, 1):
        s.append(("tent", sd_round_box(v3(sgn * 0.55, 0.6, 0), rot_z(sgn * 0.75), (0.08, 0.85, 1.2), 0.03)))
    s.append(("tent", sd_cut(box((0, 0.5, -1.15), (1.1, 0.6, 0.04), 0.01), v3(0, -1, 0), -1.2)))
    return s


def stall():
    return [("wood", box((0, 0.5, 0), (1.2, 0.5, 0.5), 0.03)), ("label_r", box((0, 2.0, 0.2), (1.4, 0.05, 0.8), 0.03)),
            ("wood", cyl((-1.2, 0, 0.6), (-1.2, 2.0, 0.6), 0.05)), ("wood", cyl((1.2, 0, 0.6), (1.2, 2.0, 0.6), 0.05)),
            ("crate", box((-0.6, 1.1, 0.1), (0.25, 0.12, 0.2), 0.02)), ("gold", sd_sphere(v3(0.5, 1.08, 0.1), 0.1))]


def sandbags():
    return [("sandbag", box((0, 0.25, 0), (1.4, 0.25, 0.3), 0.15)), ("sandbag", box((0, 0.7, 0), (1.2, 0.22, 0.28), 0.14))]


def fence(length=3.0):
    s = [("wood", box((0, 0.55, 0), (length / 2, 0.05, 0.03), 0.01)), ("wood", box((0, 0.95, 0), (length / 2, 0.05, 0.03), 0.01))]
    for x in np.linspace(-length / 2, length / 2, 5):
        s.append(("wood", box((x, 0.6, 0), (0.06, 0.6, 0.05), 0.01)))
    return s


def trash():
    return [("bag_black", sd_displace(sd_ellipsoid(v3(0, 0.25, 0), I3, (0.3, 0.25, 0.28)), 0.02, 15)),
            ("bag_black", sd_displace(sd_ellipsoid(v3(0.35, 0.18, 0.2), I3, (0.22, 0.18, 0.2)), 0.02, 15))]


def cone():
    return [("barrel_r", cyl((0, 0, 0), (0, 0.6, 0), 0.18, 0.02)), ("white", cyl((0, 0.25, 0), (0, 0.33, 0), 0.13, 0.11)),
            ("rubber", box((0, 0.02, 0), (0.22, 0.02, 0.22), 0.01))]


# ------------------------------------------------------------------ eşya modelleri (ikon + yerdeki eşya)
def item_model(name):
    if name == "wood":
        return [("log", cyl((-0.2, 0.08, 0), (0.2, 0.08, 0.05), 0.07)), ("log", cyl((-0.15, 0.2, 0.05), (0.22, 0.2, -0.02), 0.06)),
                ("log_end", sd_sphere(v3(0.22, 0.08, 0.05), 0.065))]
    if name == "stone":
        return [("rock", sd_displace(sd_ellipsoid(v3(0, 0.12, 0), I3, (0.2, 0.13, 0.17)), 0.015, 18))]
    if name == "cloth":
        return [("cloth_i", box((0, 0.06, 0), (0.18, 0.05, 0.14), 0.04)), ("label_r", box((0, 0.06, 0), (0.185, 0.015, 0.145), 0.01)),
                ("cloth_i", box((0.02, 0.15, 0.0), (0.16, 0.04, 0.12), 0.035))]
    if name == "glue":
        return [("glue", cyl((0, 0.0, 0), (0, 0.25, 0), 0.08, 0.07)), ("glue_cap", cyl((0, 0.25, 0), (0, 0.36, 0), 0.04, 0.015)),
                ("label_r", cyl((0, 0.08, 0), (0, 0.17, 0), 0.083))]
    if name == "resin":
        return [("resin", sd_ellipsoid(v3(0, 0.12, 0), I3, (0.12, 0.13, 0.1))), ("resin", sd_sphere(v3(0.1, 0.06, 0.06), 0.06))]
    if name == "leather":
        return [("leather", box((0, 0.04, 0), (0.2, 0.025, 0.15), 0.03)), ("bag_strap", box((0.05, 0.08, 0.03), (0.15, 0.02, 0.11), 0.03))]
    if name == "scrap":
        return [("scrap", box((0, 0.06, 0), (0.18, 0.03, 0.1), 0.01, rot_y(0.5))), ("scrap", box((0.05, 0.12, 0.05), (0.12, 0.02, 0.08), 0.01, rot_z(0.4))),
                ("steel", cyl((-0.15, 0.04, 0.1), (0.1, 0.18, -0.1), 0.02))]
    if name == "screw":
        return [("steel", cyl((0, 0.0, 0), (0, 0.22, 0), 0.025, 0.015)), ("steel", cyl((0, 0.22, 0), (0, 0.27, 0), 0.06)),
                ("steel", cyl((0.12, 0.0, 0.05), (0.12, 0.18, 0.05), 0.022, 0.012)), ("steel", cyl((0.12, 0.18, 0.05), (0.12, 0.22, 0.05), 0.05))]
    if name == "powder":
        return [("powder", sd_ellipsoid(v3(0, 0.13, 0), I3, (0.16, 0.14, 0.14))), ("bag_strap", cyl((0, 0.24, 0), (0, 0.3, 0), 0.05, 0.03)),
                ("label_r", box((0, 0.14, 0.13), (0.06, 0.06, 0.01), 0.01))]
    if name == "electronics":
        return [("pcb", box((0, 0.03, 0), (0.2, 0.02, 0.15), 0.01)), ("chip", box((-0.05, 0.06, 0), (0.06, 0.02, 0.06), 0.005)),
                ("gold", box((0.1, 0.055, 0.06), (0.03, 0.015, 0.03), 0.005)), ("chip", cyl((0.1, 0.05, -0.07), (0.1, 0.14, -0.07), 0.03))]
    if name == "tooth":
        return [("tooth", cyl((0, 0.0, 0), (0, 0.28, 0.04), 0.07, 0.015))]
    if name == "toxic":
        return [("vial", cyl((0, 0.0, 0), (0, 0.24, 0), 0.075, 0.07)), ("tox", cyl((0, 0.02, 0), (0, 0.15, 0), 0.06)),
                ("rubber", cyl((0, 0.24, 0), (0, 0.3, 0), 0.045))]
    if name == "dark_crystal":
        return [("dark_c", sd_round_box(v3(0, 0.18, 0), rot_z(0.2), (0.06, 0.18, 0.06), 0.02)),
                ("dark_c", sd_round_box(v3(0.1, 0.1, 0.05), rot_z(-0.5), (0.04, 0.1, 0.04), 0.015))]
    if name == "titan_shard":
        return [("titan", sd_round_box(v3(0, 0.15, 0), rot_z(0.35) @ rot_y(0.4), (0.07, 0.17, 0.03), 0.01)),
                ("titan_glow", box((0, 0.15, 0.032), (0.012, 0.1, 0.004), 0.002, rot_z(0.35) @ rot_y(0.4)))]
    if name == "fuel":
        return [("jerry", box((0, 0.2, 0), (0.15, 0.2, 0.07), 0.03)), ("concrete_d", cyl((0.1, 0.4, 0), (0.12, 0.47, 0), 0.03)),
                ("jerry", box((-0.05, 0.42, 0), (0.06, 0.02, 0.03), 0.01))]
    if name == "bandage":
        return [("bandage", cyl((-0.12, 0.1, 0), (0.12, 0.1, 0), 0.1)), ("label_r", cyl((-0.125, 0.1, 0), (0.125, 0.1, 0), 0.04))]
    if name == "medkit":
        return [("med_red", box((0, 0.13, 0), (0.2, 0.13, 0.09), 0.04)), ("white", box((0, 0.13, 0.092), (0.08, 0.025, 0.005), 0.005)),
                ("white", box((0, 0.13, 0.092), (0.025, 0.08, 0.005), 0.005)), ("concrete_d", box((0, 0.27, 0), (0.06, 0.02, 0.03), 0.01))]
    if name == "food":
        return [("can", cyl((0, 0, 0), (0, 0.24, 0), 0.1)), ("label_r", cyl((0, 0.05, 0), (0, 0.19, 0), 0.103))]
    if name == "energy":
        return [("can", cyl((0, 0, 0), (0, 0.3, 0), 0.07)), ("label_g", cyl((0, 0.04, 0), (0, 0.26, 0), 0.073))]
    if name == "antidote":
        return [("vial", cyl((0, 0.0, 0), (0, 0.24, 0), 0.075, 0.07)), ("blue_l", cyl((0, 0.02, 0), (0, 0.15, 0), 0.06)),
                ("white", cyl((0, 0.24, 0), (0, 0.3, 0), 0.045))]
    if name.startswith("ammo_"):
        col = {"ammo_9mm": "brass", "ammo_762": "brass", "ammo_556": "brass", "ammo_308": "brass"}.get(name, "brass")
        n = {"ammo_9mm": 0.035, "ammo_762": 0.05, "ammo_556": 0.045, "ammo_308": 0.07}[name] if name != "ammo_rocket" else 0
        if name == "ammo_rocket":
            return [("warhead", cyl((0, 0.08, -0.15), (0, 0.08, 0.15), 0.06, 0.06)), ("warhead", cyl((0, 0.08, 0.15), (0, 0.08, 0.3), 0.06, 0.01)),
                    ("gun_olive", cyl((0, 0.08, -0.3), (0, 0.08, -0.15), 0.03))]
        s = [("ammo_box", box((0, 0.07, 0), (0.17, 0.07, 0.1), 0.015))]
        for i in range(3):
            x = -0.08 + i * 0.08
            s.append((col, cyl((x, 0.14, 0), (x, 0.14 + n * 2.2, 0), 0.016, 0.009)))
        return s
    if name.startswith("w_"):
        return [(m, (lambda f: (lambda p: f((p - v3(0, 0.12, 0)) @ rot_y(-math.pi / 2).T)))(f)) for m, f in weapon(name[2:])]
    if name.startswith("helm_"):
        return [(m, (lambda f: (lambda p: f(p - v3(0, 0.05, 0))))(f)) for m, f in helmet(name[5:])]
    if name.startswith("bag_"):
        return [(m, (lambda f: (lambda p: f((p - v3(0, 0.2, 0)) @ rot_y(math.pi).T)))(f)) for m, f in bag(name[4:])]
    if name.startswith("vest_"):
        return [(m, (lambda f: (lambda p: f(p - v3(0, 0.22, 0))))(f)) for m, f in vest(name[5:])]
    if name.startswith("jacket_"):
        return [(m, (lambda f: (lambda p: f(p - v3(0, 0.25, 0))))(f)) for m, f in jacket(name[7:])]
    if name.startswith("boots_"):
        k = name[6:]
        a = [(m, (lambda f: (lambda p: f((p - v3(-0.07, 0, 0)) @ rot_y(0.3).T)))(f)) for m, f in boot(k)]
        b = [(m, (lambda f: (lambda p: f((p - v3(0.07, 0, 0.03)) @ rot_y(0.3).T)))(f)) for m, f in boot(k)]
        return a + b
    if name == "cloak":
        return [(m, (lambda f: (lambda p: f((p - v3(0, 0.9, 0)) @ rot_y(math.pi).T)))(f)) for m, f in cloak()]
    if name == "coin":
        return [("gold", cyl((0, 0.02, 0), (0, 0.05, 0), 0.12)), ("gold", cyl((0.1, 0.05, 0.08), (0.1, 0.08, 0.08), 0.1))]
    if name == "gem":
        return [("gem", sd_round_box(v3(0, 0.14, 0), rot_y(0.785) @ rot_x(0.6), (0.1, 0.1, 0.1), 0.01))]
    return [("concrete", box((0, 0.1, 0), (0.1, 0.1, 0.1), 0.02))]


def boot(kind):
    """Tek bot (ayak merkezi orijinde, +z ileri). Ayakkabının üstüne giyilir."""
    m = {"basic": "bag_strap", "police": "gun_black", "military": "gun_tan", "titan": "titan"}[kind]
    s = [(m, box((0, 0.045, 0.01), (0.058, 0.045, 0.115), 0.03)),
         (m, cyl((0, 0.05, -0.02), (0, 0.24, -0.01), 0.058, 0.052)),
         ("rubber", box((0, 0.008, 0.01), (0.06, 0.01, 0.118), 0.008))]
    if kind == "police":
        s.append(("chrome", box((0.0, 0.2, 0.05), (0.04, 0.01, 0.01), 0.004)))
    if kind == "titan":
        s.append(("titan_glow", box((0, 0.18, 0.055), (0.02, 0.03, 0.004), 0.004)))
        s.append(("titan_gold", box((0, 0.1, 0.12), (0.05, 0.02, 0.01), 0.005)))
    return s


def jacket(kind):
    """Ceket/yelek: göğüs noktasında, gövdeyi saran kabuk."""
    m = {"basic": "vest_basic", "police": "vest_police", "military": "helm_mil", "titan": "titan"}[kind]
    s = [(m, sd_round_box(v3(0, -0.02, 0), I3, (0.178, 0.22, 0.118), 0.075))]
    if kind == "police":
        s += [("police_yel", box((0, 0.06, 0.122), (0.1, 0.018, 0.004), 0.003)), ("police_yel", box((0, 0.04, -0.122), (0.12, 0.03, 0.004), 0.003))]
    if kind == "military":
        for x in (-0.08, 0.08):
            s.append(("bag_olive", box((x, -0.08, 0.12), (0.05, 0.05, 0.02), 0.01)))
        s.append(("bag_strap", box((0, 0.14, 0.0), (0.18, 0.02, 0.12), 0.01)))
    if kind == "titan":
        s += [("titan_glow", box((0, 0.05, 0.122), (0.03, 0.03, 0.004), 0.008)), ("titan_gold", box((0, -0.15, 0.0), (0.18, 0.02, 0.12), 0.01)),
              ("titan", sd_sphere(v3(0.17, 0.16, 0), 0.07)), ("titan", sd_sphere(v3(-0.17, 0.16, 0), 0.07))]
    return s
