"""Detaylı silah modelleri (bpy). Yerel çerçeve: kabza avuç noktası orijinde, namlu +Y, üst +Z (metre).

build(kind) → nesne listesi. Hem karakterin eline bağlanır hem envanter simgesi olarak render edilir.
"""
import math

import bpy

import blib as B


def M(name, col, rough=0.4, metal=0.0, kind=None, emit=0.0):
    return B.material(name, col, rough, metal=metal, kind=kind, emit=emit)


def box(loc, size, mat, rot=(0, 0, 0), bevel=0.003):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        m = o.modifiers.new("b", "BEVEL")
        m.width = bevel
        m.segments = 2
        m.limit_method = "ANGLE"
    o.data.materials.append(mat)
    return o


def cyl(loc, r, depth, mat, axis="Y", verts=20, r2=None):
    rot = {"X": (0, math.pi / 2, 0), "Y": (math.pi / 2, 0, 0), "Z": (0, 0, 0)}[axis]
    if r2 is None:
        bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=loc, rotation=rot)
    else:
        bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r, radius2=r2, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


def profile(points, width, mat, bevel=0.002, axis_x=0.0):
    """YZ düzleminde profil → X boyunca kalınlık (kabza, dipçik gibi eğri parçalar)."""
    n = len(points)
    v = [(axis_x - width / 2, y, z) for y, z in points] + [(axis_x + width / 2, y, z) for y, z in points]
    f = [tuple(range(n))[::-1], tuple(range(n, 2 * n))] + [(i, (i + 1) % n, n + (i + 1) % n, n + i) for i in range(n)]
    me = bpy.data.meshes.new("prof")
    me.from_pydata(v, [], f)
    me.update()
    o = bpy.data.objects.new("prof", me)
    bpy.context.scene.collection.objects.link(o)
    o.data.materials.append(mat)
    if bevel:
        m = o.modifiers.new("b", "BEVEL")
        m.width = bevel
        m.segments = 2
    return o


def glock():
    poly = M("polymer", "#1c1d20", 0.55)
    steel = M("steel", "#2c2e32", 0.3, 0.9)
    objs = [
        box((0, 0.07, 0.055), (0.026, 0.19, 0.03), steel),  # sürgü
        box((0, 0.065, 0.032), (0.024, 0.17, 0.02), poly),  # gövde
        profile([(-0.015, 0.03), (0.025, 0.03), (0.01, -0.075), (-0.035, -0.08), (-0.03, 0.0)], 0.028, poly, 0.004),  # kabza
        box((0, 0.012, 0.012), (0.004, 0.03, 0.025), poly),  # tetik korkuluğu
        cyl((0, 0.168, 0.058), 0.006, 0.01, M("bore", "#050505", 0.5)),
        box((0, 0.155, 0.074), (0.004, 0.006, 0.006), steel), box((0, -0.015, 0.074), (0.014, 0.006, 0.006), steel),
    ]
    for k in range(6):
        objs.append(box((0, -0.005 + k * 0.006, 0.055), (0.028, 0.002, 0.024), M("serr", "#141518", 0.5), bevel=0))
    return objs


def ak47():
    wood = M("ak_wood", "#7a3e1c", 0.45, kind="bark")
    steel = M("ak_steel", "#2a2a2c", 0.35, 0.9)
    blk = M("ak_blk", "#121214", 0.5, 0.4)
    objs = [
        box((0, 0.1, 0.04), (0.036, 0.3, 0.05), steel),  # gövde
        box((0, 0.1, 0.07), (0.03, 0.26, 0.012), steel, bevel=0.005),  # kapak
        cyl((0, 0.46, 0.05), 0.009, 0.42, steel),  # namlu
        cyl((0, 0.32, 0.075), 0.01, 0.2, steel),  # gaz borusu
        box((0, 0.3, 0.035), (0.04, 0.16, 0.04), wood, bevel=0.008),  # el kundağı
        box((0, 0.36, 0.075), (0.032, 0.14, 0.022), wood, bevel=0.006),
        profile([(-0.06, 0.04), (-0.38, 0.0), (-0.4, -0.07), (-0.36, -0.08), (-0.08, 0.0)], 0.036, wood, 0.006),  # dipçik
        profile([(-0.01, 0.015), (0.02, 0.015), (0.0, -0.08), (-0.035, -0.085)], 0.03, wood, 0.004),  # kabza
        profile([(0.06, 0.015), (0.1, 0.015), (0.14, -0.13), (0.1, -0.15), (0.075, -0.06)], 0.026, steel, 0.004),  # kavisli şarjör
        box((0, 0.66, 0.072), (0.006, 0.01, 0.025), steel), box((0, 0.66, 0.05), (0.02, 0.025, 0.02), steel),  # arpacık, ağızlık
        box((0, 0.18, 0.082), (0.012, 0.03, 0.01), steel),
    ]
    return objs


def m4():
    blk = M("m4_blk", "#1e1f22", 0.45, 0.5)
    tan = M("m4_tan", "#2c2d30", 0.5, 0.3)
    objs = [
        box((0, 0.1, 0.045), (0.034, 0.26, 0.05), blk),
        box((0, 0.36, 0.05), (0.042, 0.26, 0.045), tan, bevel=0.006),  # ray kızak
        cyl((0, 0.58, 0.05), 0.008, 0.26, blk),
        cyl((0, 0.72, 0.05), 0.012, 0.04, blk),  # alev gizleyici
        box((0, 0.36, 0.078), (0.022, 0.26, 0.008), blk, bevel=0.0),  # pikatini
        box((0, 0.13, 0.09), (0.012, 0.03, 0.03), blk),  # taşıma sapı / gez
        box((0, 0.48, 0.09), (0.008, 0.012, 0.035), blk),  # arpacık
        profile([(-0.06, 0.06), (-0.33, 0.055), (-0.34, -0.03), (-0.3, -0.04), (-0.12, 0.02), (-0.06, 0.02)], 0.034, blk, 0.006),  # dipçik
        cyl((0, -0.12, 0.05), 0.014, 0.14, blk),  # tampon borusu
        profile([(-0.01, 0.02), (0.02, 0.02), (0.0, -0.075), (-0.035, -0.08)], 0.03, blk, 0.004),
        profile([(0.05, 0.02), (0.1, 0.02), (0.105, -0.12), (0.06, -0.12)], 0.028, tan, 0.003),  # düz şarjör
    ]
    # kırmızı nokta nişangah
    objs.append(box((0, 0.24, 0.1), (0.03, 0.05, 0.03), blk, bevel=0.006))
    objs.append(box((0, 0.215, 0.1), (0.022, 0.002, 0.02), M("dot", "#ff3020", 0.2, emit=3.0), bevel=0))
    return objs


def sniper():
    grn = M("sn_green", "#3a4430", 0.55, kind="cloth")
    blk = M("sn_blk", "#1a1b1e", 0.35, 0.8)
    glass = M("sn_glass", "#2a5a8a", 0.05, 0.5, emit=0.3)
    objs = [
        box((0, 0.12, 0.045), (0.036, 0.3, 0.05), blk),
        cyl((0, 0.62, 0.05), 0.011, 0.7, blk),
        cyl((0, 0.98, 0.05), 0.018, 0.06, blk),  # susturucu ucu
        profile([(-0.05, 0.065), (-0.42, 0.06), (-0.44, -0.07), (-0.38, -0.08), (-0.2, -0.02), (-0.06, -0.015)], 0.04, grn, 0.008),
        box((0, 0.36, 0.035), (0.042, 0.26, 0.05), grn, bevel=0.01),
        profile([(-0.01, 0.02), (0.02, 0.02), (0.0, -0.08), (-0.035, -0.085)], 0.032, grn, 0.004),
        box((0, 0.07, -0.0), (0.03, 0.06, 0.05), blk),  # şarjör
        cyl((0, 0.16, 0.12), 0.022, 0.32, blk),  # dürbün
        cyl((0, 0.33, 0.12), 0.03, 0.06, blk, r2=0.022),
        cyl((0, -0.01, 0.12), 0.026, 0.05, blk),
        cyl((0, 0.362, 0.12), 0.026, 0.004, glass),
        box((0, 0.1, 0.09), (0.012, 0.02, 0.03), blk), box((0, 0.24, 0.09), (0.012, 0.02, 0.03), blk),
        box((0.03, 0.04, 0.07), (0.04, 0.008, 0.008), blk),  # sürgü kolu
    ]
    for sx in (-1, 1):  # çatal ayak
        o = cyl((sx * 0.03, 0.6, -0.04), 0.006, 0.18, blk, axis="Z")
        o.rotation_euler = (0.3, sx * 0.3, 0)
    return objs


def rpg():
    grn = M("rpg_green", "#4a5a3a", 0.6, kind="cloth")
    wood = M("rpg_wood", "#8a5a30", 0.5, kind="bark")
    blk = M("rpg_blk", "#1c1c1e", 0.4, 0.7)
    war = M("rpg_war", "#5a6a40", 0.5, 0.3)
    objs = [
        cyl((0, 0.15, 0.08), 0.035, 0.95, grn),  # tüp
        cyl((0, 0.15, 0.08), 0.04, 0.3, wood),  # ahşap kılıf
        cyl((0, -0.34, 0.08), 0.035, 0.08, blk, r2=0.05),  # arka huni
        profile([(-0.01, 0.045), (0.02, 0.045), (0.0, -0.06), (-0.03, -0.065)], 0.03, blk, 0.004),
        profile([(0.14, 0.045), (0.17, 0.045), (0.16, -0.05), (0.13, -0.05)], 0.028, blk, 0.004),
        box((0.045, 0.05, 0.12), (0.02, 0.06, 0.04), blk),  # nişangah
        cyl((0, 0.66, 0.08), 0.03, 0.08, war),  # roket
        cyl((0, 0.78, 0.08), 0.06, 0.16, war, r2=0.02),
        cyl((0, 0.72, 0.08), 0.062, 0.04, war),
    ]
    return objs


def bat():
    wood = M("bat_wood", "#c89a5a", 0.45, kind="bark")
    tape = M("bat_tape", "#202022", 0.7)
    nail = M("nail", "#9a9ca0", 0.3, 1.0)
    objs = [cyl((0, 0.32, 0), 0.016, 0.38, wood, r2=0.034), cyl((0, 0.62, 0), 0.034, 0.24, wood),
            cyl((0, 0.05, 0), 0.016, 0.16, tape), cyl((0, -0.035, 0), 0.022, 0.012, wood)]
    for k in range(6):
        a = k * 1.1
        o = cyl((math.cos(a) * 0.038, 0.55 + k * 0.025, math.sin(a) * 0.038), 0.003, 0.03, nail, axis="X")
        o.rotation_euler = (0, a, math.pi / 2)
    return objs


def baton():
    blk = M("bat_blk", "#141416", 0.4, 0.3)
    grip = M("bat_grip", "#2a2a2c", 0.8, kind="rubber")
    return [cyl((0, 0.3, 0), 0.016, 0.5, blk), cyl((0, 0.04, 0), 0.019, 0.14, grip),
            cyl((0, 0.14, 0.03), 0.012, 0.1, blk, axis="Z"), cyl((0, 0.56, 0), 0.018, 0.02, blk)]


BUILDERS = {"glock": glock, "ak47": ak47, "m4": m4, "sniper": sniper, "rpg": rpg, "bat": bat, "baton": baton}
# ele göre kabza ofseti (metre, silah yerel) ve tutuş açısı düzeltmesi (derece, X ekseni)
GRIP = {"glock": (0.0, 0.0, 0.0, 0), "ak47": (0.0, 0.0, 0.0, 0), "m4": (0.0, 0.0, 0.0, 0), "sniper": (0.0, 0.0, 0.0, 0),
        "rpg": (0.0, -0.1, 0.0, 0), "bat": (0.0, 0.0, 0.0, 0), "baton": (0.0, 0.0, 0.0, 0)}


def build(kind):
    objs = BUILDERS[kind]()
    for o in objs:
        o.name = "W_" + kind
    return objs
