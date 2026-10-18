"""Eşya modelleri (simgeler + karakter üstü ekipman). Orijin: simgede nesne merkezi; ekipmanda takılma noktası.

build(id) → nesne listesi.   Ekipman çerçevesi: x sağ, -y ileri (kameraya), z yukarı (Blender).
"""
import math
import random

import bpy
from mathutils import Vector

import blib as B
import weapons_b as WB
from weapons_b import box, cyl, profile, M


def sph(loc, r, mat, scale=(1, 1, 1), seg=24):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2, radius=r, location=loc)
    o = bpy.context.active_object
    o.scale = scale
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


def ico(loc, r, mat, sub=1, scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=sub, radius=r, location=loc)
    o = bpy.context.active_object
    o.scale = scale
    o.data.materials.append(mat)
    return o


def torus(loc, R, r, mat, rot=(0, 0, 0), scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_torus_add(major_radius=R, minor_radius=r, location=loc, rotation=rot, major_segments=32, minor_segments=10)
    o = bpy.context.active_object
    o.scale = scale
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


def label(text, loc, size, mat, rot=(math.pi / 2, 0, 0), depth=0.001):
    cu = bpy.data.curves.new("t", "FONT")
    cu.body = text
    try:
        cu.font = bpy.data.fonts.load("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", check_existing=True)
    except Exception:  # noqa: BLE001
        pass
    cu.size = size
    cu.extrude = depth
    cu.align_x = "CENTER"
    cu.align_y = "CENTER"
    o = bpy.data.objects.new("t", cu)
    bpy.context.scene.collection.objects.link(o)
    o.location = loc
    o.rotation_euler = rot
    o.data.materials.append(mat)
    return o


def shell(loc, R1, R2, h, mat, open_front=0.0, thick=0.015, scale=(1, 0.75, 1)):
    """Gövde kabuğu (yelek): koni kesiti + kalınlık; önü açık olabilir."""
    bpy.ops.mesh.primitive_cone_add(vertices=40, radius1=R1, radius2=R2, depth=h, location=loc, end_fill_type="NOTHING")
    o = bpy.context.active_object
    o.scale = scale
    o.data.materials.append(mat)
    m = o.modifiers.new("sol", "SOLIDIFY")
    m.thickness = thick
    o.modifiers.new("sub", "SUBSURF").levels = 1
    bpy.ops.object.shade_smooth()
    return o


# =========================================================================== malzemeler
def wood():
    bark = M("bark", "#5a3c24", 0.8, kind="bark")
    ring = M("ring", "#d8b07a", 0.6, kind="bark")
    out = []
    for i, (x, z) in enumerate(((-0.06, 0), (0.06, 0), (0, 0.1))):
        out.append(cyl((x, 0, z), 0.055, 0.32, bark, axis="X"))
        for sx in (-1, 1):
            out.append(cyl((x + sx * 0.161, 0, z), 0.05, 0.004, ring, axis="X"))
    out.append(torus((0, 0, 0.05), 0.12, 0.008, M("rope", "#b89a6a", 0.9, kind="cloth"), rot=(0, math.pi / 2, 0), scale=(1, 1, 1.2)))
    return out


def stone():
    rk = M("rock", "#8a8680", 0.85, kind="stone")
    rk2 = M("rock2", "#6e6a64", 0.85, kind="stone")
    return [ico((0, 0, 0), 0.11, rk, 2, (1.1, 0.9, 0.8)), ico((0.12, 0.04, -0.03), 0.07, rk2, 2, (1, 0.9, 0.8)),
            ico((-0.1, 0.06, -0.04), 0.06, rk, 1, (1, 1, 0.7))]


def cloth():
    cols = ["#b8b0a0", "#8a6a8a", "#6a8aa8"]
    return [box((0, 0, -0.05 + i * 0.045), (0.26 - i * 0.02, 0.18, 0.04), M("cl" + c, c, 0.9, kind="cloth"), rot=(0, 0, i * 0.12), bevel=0.015)
            for i, c in enumerate(cols)]


def glue():
    body = M("gluet", "#e8e4dc", 0.4)
    cap = M("gluec", "#e04030", 0.4)
    o = [cyl((0, 0, 0), 0.045, 0.22, body, axis="X"), cyl((0.13, 0, 0), 0.03, 0.05, cap, axis="X", r2=0.012),
         box((-0.12, 0, 0), (0.01, 0.1, 0.02), body)]
    o.append(box((0, -0.046, 0), (0.12, 0.001, 0.05), M("gluel", "#3a7ae0", 0.5), bevel=0))
    return o


def resin():
    amb = M("amber", "#e09020", 0.1, emit=0.4)
    return [sph((0, 0, 0), 0.09, amb, (1, 0.9, 1.2)), sph((0.08, 0.02, -0.05), 0.05, amb), sph((-0.07, 0.01, -0.06), 0.04, amb)]


def leather():
    lm = M("leath", "#8a5a34", 0.6, kind="leather")
    return [cyl((0, 0, 0), 0.07, 0.26, lm, axis="X"), torus((0.06, 0, 0), 0.075, 0.008, M("strap", "#3a2a1a", 0.7), rot=(0, math.pi / 2, 0)),
            torus((-0.06, 0, 0), 0.075, 0.008, M("strap", "#3a2a1a", 0.7), rot=(0, math.pi / 2, 0))]


def scrap():
    rust = M("rustm", "#8a5a3a", 0.8, 0.5, kind="stone")
    st = M("scr", "#9a9ea2", 0.35, 0.9)
    gear = [cyl((0.05, 0, 0.04), 0.07, 0.02, st, axis="Y")]
    for k in range(10):
        a = k * math.pi / 5
        gear.append(box((0.05 + math.cos(a) * 0.075, 0, 0.04 + math.sin(a) * 0.075), (0.02, 0.02, 0.02), st, rot=(0, -a, 0)))
    return gear + [box((-0.06, 0.02, -0.03), (0.18, 0.1, 0.008), rust, rot=(0.2, 0.3, 0.4)), cyl((-0.02, -0.05, 0.0), 0.02, 0.2, rust, axis="X")]


def screw():
    st = M("screwm", "#b8bcc0", 0.25, 1.0)
    out = []
    for i, x in enumerate((-0.05, 0.05)):
        out.append(cyl((x, 0, 0), 0.012, 0.16, st, axis="Z"))
        out.append(cyl((x, 0, 0.085), 0.03, 0.014, st, axis="Z"))
        for k in range(7):
            out.append(torus((x, 0, -0.06 + k * 0.018), 0.012, 0.003, st))
    bpy.ops.mesh.primitive_cylinder_add(vertices=6, radius=0.03, depth=0.02, location=(0, 0.08, -0.06))
    nut = bpy.context.active_object
    nut.data.materials.append(st)
    out.append(nut)
    return out


def powder():
    tin = M("tin", "#3a3a3c", 0.4, 0.8)
    return [cyl((0, 0, 0), 0.08, 0.14, tin, axis="Z"), cyl((0, 0, 0.075), 0.082, 0.012, M("tinl", "#5a5a5c", 0.3, 0.9), axis="Z"),
            box((0, -0.08, 0), (0.08, 0.002, 0.07), M("pl", "#c8a020", 0.5), bevel=0),
            label("TNT", (0, -0.083, 0), 0.04, M("plt", "#1a1a1a", 0.5)),
            sph((0.0, 0.0, 0.09), 0.05, M("pwd", "#2a2a2a", 0.9), (1, 1, 0.3))]


def electronics():
    pcb = M("pcb", "#1e7a3a", 0.5)
    chip = M("chip", "#141414", 0.4)
    gold = M("pin", "#e8c050", 0.3, 1.0)
    o = [box((0, 0, 0), (0.26, 0.18, 0.012), pcb, bevel=0.002)]
    for x, y, w, h in ((-0.05, 0.02, 0.07, 0.07), (0.07, -0.03, 0.05, 0.04), (0.06, 0.05, 0.03, 0.03)):
        o.append(box((x, y, 0.012), (w, h, 0.012), chip))
    for k in range(10):
        o.append(box((-0.12 + k * 0.026, -0.085, 0.008), (0.012, 0.012, 0.004), gold, bevel=0))
    o.append(cyl((-0.08, -0.05, 0.03), 0.015, 0.04, M("cap", "#2a4ab0", 0.4), axis="Z"))
    o.append(sph((0.1, 0.06, 0.012), 0.008, M("led", "#ff3030", 0.2, emit=4.0)))
    return o


def tooth():
    t = M("toothm", "#ece4cc", 0.35)
    bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=0.05, radius2=0.0, depth=0.22, location=(0, 0, 0), rotation=(0.3, 0, 0))
    o = bpy.context.active_object
    o.data.materials.append(t)
    o.modifiers.new("s", "SUBSURF").levels = 2
    bpy.ops.object.shade_smooth()
    return [o, sph((0, 0.03, 0.1), 0.055, t, (1, 0.8, 0.6)), torus((0, 0.03, 0.14), 0.04, 0.006, M("cord", "#3a2a1a", 0.8))]


def toxic():
    gl = M("vialg", "#c8f0d0", 0.05, 0.0)
    liq = M("toxl", "#9dff3a", 0.2, emit=3.0)
    return [cyl((0, 0, 0), 0.05, 0.2, gl, axis="Z"), cyl((0, 0, -0.02), 0.045, 0.15, liq, axis="Z"),
            cyl((0, 0, 0.11), 0.03, 0.04, M("cork", "#7a5a3a", 0.8), axis="Z"), label("☣", (0, -0.052, 0.02), 0.05, M("hz", "#1a1a1a", 0.5))]


def crystal(col, emit):
    m = M("cr" + col, col, 0.08, emit=emit)
    out = []
    rnd = random.Random(3)
    for i in range(6):
        h = rnd.uniform(0.12, 0.26)
        bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=0.03, radius2=0.0, depth=h,
                                        location=(rnd.uniform(-0.05, 0.05), rnd.uniform(-0.04, 0.04), h / 2 - 0.05),
                                        rotation=(rnd.uniform(-0.5, 0.5), rnd.uniform(-0.5, 0.5), 0))
        o = bpy.context.active_object
        o.data.materials.append(m)
        out.append(o)
    out.append(ico((0, 0, -0.06), 0.07, M("crb", "#3a3a40", 0.8, kind="stone"), 1, (1.2, 1, 0.4)))
    return out


def titan_shard():
    mt = M("tshard", "#4a3a3a", 0.3, 0.9)
    glow = M("tglow", "#ff4a1a", 0.2, emit=6.0)
    bpy.ops.mesh.primitive_cone_add(vertices=5, radius1=0.08, radius2=0.0, depth=0.3, location=(0, 0, 0), rotation=(0.4, 0.3, 0))
    o = bpy.context.active_object
    o.data.materials.append(mt)
    o2 = box((0, -0.02, -0.02), (0.01, 0.01, 0.2), glow, rot=(0.4, 0.3, 0), bevel=0)
    return [o, o2]


def fuel():
    red = M("jerry", "#c82a20", 0.35, 0.3, )
    o = [box((0, 0, 0), (0.2, 0.09, 0.26), red, bevel=0.02),
         box((0.04, 0, 0.16), (0.1, 0.03, 0.04), red, bevel=0.01),
         cyl((-0.07, 0, 0.15), 0.018, 0.05, M("spout", "#2a2a2a", 0.5), axis="Z"),
         box((0, -0.046, 0), (0.14, 0.002, 0.16), M("emb", "#a82018", 0.4), rot=(0, 0.6, 0), bevel=0)]
    return o


def bandage():
    w = M("band", "#f0ece2", 0.9, kind="cloth")
    return [cyl((0, 0, 0), 0.08, 0.1, w, axis="Y"), cyl((0, 0, 0), 0.025, 0.102, M("core", "#c8b890", 0.6), axis="Y"),
            box((0.0, -0.0, -0.09), (0.09, 0.1, 0.004), w, rot=(0.2, 0, 0))]


def medkit():
    red = M("mkr", "#d8302a", 0.4)
    wh = M("mkw", "#f4f4f0", 0.4)
    return [box((0, 0, 0), (0.26, 0.12, 0.2), red, bevel=0.02), box((0, -0.062, 0), (0.12, 0.002, 0.04), wh, bevel=0),
            box((0, -0.062, 0), (0.04, 0.002, 0.12), wh, bevel=0), box((0, 0, 0.11), (0.1, 0.03, 0.02), M("mkh", "#2a2a2a", 0.5), bevel=0.006)]


def food():
    tin = M("can", "#b8bcc0", 0.3, 0.9)
    lab = M("canl", "#c83a28", 0.5)
    return [cyl((0, 0, 0), 0.07, 0.12, tin, axis="Z"), cyl((0, 0, 0), 0.071, 0.08, lab, axis="Z"),
            label("ET", (0, -0.072, 0), 0.035, M("canlt", "#f4e0a0", 0.5)), torus((0, 0, 0.06), 0.065, 0.004, tin)]


def energy():
    can = M("enc", "#20c0e0", 0.25, 0.6)
    return [cyl((0, 0, 0), 0.04, 0.17, can, axis="Z"), cyl((0, 0, 0.09), 0.035, 0.012, M("enl", "#c8ccd0", 0.3, 1.0), axis="Z"),
            label("⚡", (0, -0.041, 0.01), 0.06, M("enb", "#f8e030", 0.4, emit=1.0))]


def antidote():
    gl = M("antg", "#d0e0f0", 0.05)
    liq = M("antl", "#3a8aff", 0.2, emit=1.5)
    return [cyl((0, 0, 0), 0.025, 0.2, gl, axis="X"), cyl((-0.01, 0, 0), 0.022, 0.15, liq, axis="X"),
            cyl((0.14, 0, 0), 0.004, 0.08, M("needle", "#c8c8c8", 0.2, 1.0), axis="X"),
            cyl((-0.11, 0, 0), 0.03, 0.02, M("plunger", "#2a2a2a", 0.5), axis="X")]


def ammo(kind):
    brass = M("brass", "#d0a040", 0.25, 1.0)
    tip = M("tip", "#b06a3a", 0.3, 1.0)
    boxc = {"ammo_9mm": "#3a4a2a", "ammo_762": "#5a4a2a", "ammo_556": "#2a4a3a", "ammo_308": "#4a2a2a"}.get(kind, "#3a3a3a")
    if kind == "ammo_rocket":
        war = M("war", "#5a6a40", 0.5, 0.3)
        return [cyl((0, 0, 0), 0.03, 0.18, war, axis="X"), cyl((0.13, 0, 0), 0.06, 0.12, war, axis="X", r2=0.015),
                cyl((-0.12, 0, 0), 0.025, 0.08, M("fin", "#3a3a3a", 0.5), axis="X")]
    L = {"ammo_9mm": 0.06, "ammo_762": 0.09, "ammo_556": 0.085, "ammo_308": 0.13}[kind]
    r = {"ammo_9mm": 0.012, "ammo_762": 0.011, "ammo_556": 0.01, "ammo_308": 0.012}[kind]
    o = [box((0, 0.03, 0), (0.22, 0.12, 0.08), M("ab" + kind, boxc, 0.7, kind="cloth"), bevel=0.008),
         label(kind.split("_")[1].upper(), (0, -0.031, 0), 0.03, M("abt", "#e8d8a0", 0.5))]
    for k in range(4):
        x = -0.06 + k * 0.04
        o.append(cyl((x, -0.07, 0.04 + L / 2), r, L, brass, axis="Z"))
        o.append(cyl((x, -0.07, 0.04 + L + 0.012), r, 0.024, tip, axis="Z", r2=0.002))
    return o


def weapon_icon(kind):
    return WB.build(kind)


# =========================================================================== ekipman (takılma noktası orijin)
TIER = {"basic": ("#7a6a52", "#5a4a3a"), "police": ("#1a2028", "#2a3a5a"), "military": ("#5a6240", "#3a4030"), "titan": ("#2a2228", "#ff3a1a")}


def helmet(t):
    c, acc = TIER[t]
    mm = M("h" + t, c, 0.4 if t != "basic" else 0.6, 0.6 if t in ("military", "titan") else 0.1, kind="metal" if t != "basic" else None)
    o = [sph((0, 0.005, 0.03), 0.128, mm, (1, 1.08, 0.88))]
    cut = box((0, -0.02, -0.11), (0.4, 0.4, 0.18), mm, bevel=0)  # alt kesik
    for s_ in o:
        b = s_.modifiers.new("cut", "BOOLEAN")
        b.object = cut
        b.operation = "DIFFERENCE"
    cut.hide_render = True
    cut.hide_viewport = True
    if t == "basic":
        o.append(box((0, -0.12, 0.02), (0.2, 0.06, 0.012), mm, rot=(0.3, 0, 0), bevel=0.006))  # siperlik
        o.append(box((0, 0.0, 0.13), (0.03, 0.22, 0.02), M("hb2", "#d8c040", 0.5), bevel=0.006))
    elif t == "police":
        o.append(sph((0, -0.03, 0.0), 0.135, M("visor", "#90b0d0", 0.03, 0.2), (1.0, 1.05, 0.7)))
        o.append(label("POLİS", (0, 0.13, 0.04), 0.035, M("pw", "#f4f4f0", 0.4), rot=(math.pi / 2, 0, math.pi)))
    elif t == "military":
        o.append(box((0, -0.115, 0.06), (0.06, 0.03, 0.04), M("nvg", "#1a1a1a", 0.4, 0.6), bevel=0.006))
        for sx in (-1, 1):
            o.append(box((sx * 0.13, 0, 0.0), (0.02, 0.1, 0.06), mm, bevel=0.005))
    else:
        glow = M("tg", acc, 0.2, emit=6.0)
        o.append(box((0, -0.125, 0.0), (0.16, 0.01, 0.025), glow, bevel=0.003))
        for sx in (-1, 1):
            bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=0.025, radius2=0.0, depth=0.16, location=(sx * 0.11, 0, 0.13), rotation=(0, sx * 0.5, 0))
            h = bpy.context.active_object
            h.data.materials.append(M("thorn", "#1a1416", 0.4, 0.8))
            o.append(h)
    return o


def jacket(t):
    c, acc = TIER[t]
    mm = M("j" + t, c, 0.7, 0.4 if t == "titan" else 0.0, kind="cloth" if t != "titan" else "metal")
    o = [shell((0, 0, 0), 0.19, 0.2, 0.42, mm, thick=0.025)]
    if t == "police":
        o.append(label("POLİS", (0, -0.16, 0.05), 0.05, M("pw", "#f4f4f0", 0.4)))
        for k in range(3):
            o.append(box((-0.08 + k * 0.08, -0.155, -0.11), (0.06, 0.03, 0.07), mm, bevel=0.008))
    elif t == "military":
        for k in range(3):
            o.append(box((-0.09 + k * 0.09, -0.16, -0.08), (0.07, 0.035, 0.09), M("pouch", "#4a5236", 0.7, kind="cloth"), bevel=0.01))
        o.append(box((0, -0.15, 0.06), (0.26, 0.02, 0.16), M("plate", "#4a5236", 0.6, kind="cloth"), bevel=0.02))
    elif t == "basic":
        for k in range(4):
            o.append(torus((0, 0, -0.15 + k * 0.1), 0.19, 0.008, M("quilt", "#5a4a3a", 0.8, kind="cloth"), scale=(1, 0.75, 1)))
    else:
        glow = M("tg", acc, 0.2, emit=5.0)
        o.append(box((0, -0.155, 0.04), (0.2, 0.02, 0.2), M("tplate", "#3a3036", 0.25, 1.0), bevel=0.02))
        o.append(box((0, -0.168, 0.04), (0.012, 0.004, 0.16), glow, bevel=0))
        o.append(box((0, -0.168, 0.06), (0.14, 0.004, 0.012), glow, bevel=0))
    return o


def boot(t):
    c, acc = TIER[t]
    mm = M("b" + t, c, 0.5, 0.6 if t == "titan" else 0.0, kind="leather" if t != "titan" else "metal")
    sole = M("bsole", "#1a1a1a", 0.8, kind="rubber")
    prof = [(-0.13, 0.0), (-0.135, 0.04), (-0.12, 0.2), (-0.03, 0.2), (0.0, 0.09), (0.1, 0.06), (0.135, 0.03), (0.13, 0.0)]
    o = [profile([(-z, y) for z, y in prof], 0.1, mm, 0.015), profile([(0.145, 0.0), (0.145, 0.022), (-0.145, 0.022), (-0.145, 0.0)], 0.11, sole, 0.005)]
    for k in range(4):
        o.append(box((0, 0.02 - k * 0.022, 0.11 + k * 0.025), (0.06, 0.006, 0.006), M("lace", "#d8c8a0", 0.8), bevel=0))
    if t == "titan":
        o.append(box((0, -0.06, 0.14), (0.11, 0.02, 0.1), M("tgreave", "#3a3036", 0.25, 1.0), bevel=0.01))
    for x in o:
        x.location.z -= 0.0
    return o


def bag(t):
    c, acc = TIER.get(t, TIER["basic"])
    big = {"basic": 1.0, "police": 1.1, "military": 1.3}[t]
    mm = M("bg" + t, c if t != "basic" else "#8a7a5a", 0.8, kind="cloth")
    o = [box((0, 0.1 * big, -0.02), (0.28 * big, 0.14 * big, 0.36 * big), mm, bevel=0.05),
         box((0, 0.18 * big, -0.08), (0.22 * big, 0.04, 0.16 * big), mm, bevel=0.03),
         box((0, 0.09 * big, 0.17 * big), (0.26 * big, 0.12 * big, 0.04), M("bgl" + t, acc, 0.7, kind="cloth"), bevel=0.02)]
    for sx in (-1, 1):
        o.append(box((sx * 0.08, 0.02, 0.0), (0.03, 0.02, 0.34 * big), M("bstrap", "#2a2420", 0.8), bevel=0.006))
    if t == "military":
        o.append(cyl((0, 0.12, 0.25), 0.05, 0.3, M("roll", "#4a5236", 0.8, kind="cloth"), axis="X"))
    return o


def cloak():
    cm = M("cloakm", "#7a1a2a", 0.6, kind="cloth")
    gold = M("cloakg", "#e8c45a", 0.25, 1.0)
    glow = M("cloakr", "#ffd060", 0.2, emit=4.0)
    bpy.ops.mesh.primitive_cone_add(vertices=40, radius1=0.42, radius2=0.18, depth=1.0, location=(0, 0.06, -0.5), end_fill_type="NOTHING")
    o = bpy.context.active_object
    o.scale = (1, 0.5, 1)
    o.data.materials.append(cm)
    o.modifiers.new("sol", "SOLIDIFY").thickness = 0.015
    cut = box((0, -0.3, -0.5), (0.9, 0.5, 1.2), cm, bevel=0)
    b = o.modifiers.new("open", "BOOLEAN")
    b.object = cut
    b.operation = "DIFFERENCE"
    cut.hide_render = True
    cut.hide_viewport = True
    o.modifiers.new("sub", "SUBSURF").levels = 1
    bpy.ops.object.shade_smooth()
    out = [o, torus((0, 0.02, 0.0), 0.17, 0.018, gold, scale=(1, 0.6, 1))]
    for k in range(3):
        out.append(sph((0, 0.27, -0.25 - k * 0.22), 0.025, glow, (1, 0.3, 1)))
    return out


def coin():
    g = M("gold", "#f0c040", 0.2, 1.0)
    return [cyl((0, 0, -0.04 + i * 0.022), 0.08, 0.02, g, axis="Z", verts=40) for i in range(4)] + \
        [cyl((0.06, -0.03, 0.06), 0.08, 0.02, g, axis="Y", verts=40), label("Z", (0.06, -0.041, 0.06), 0.08, M("goldt", "#c89020", 0.3, 1.0))]


def gem():
    m = M("gemm", "#5ad8ff", 0.03, 0.1, emit=1.2)
    bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=0.12, radius2=0.0, depth=0.14, location=(0, 0, -0.04), rotation=(math.pi, 0, 0))
    a = bpy.context.active_object
    a.data.materials.append(m)
    bpy.ops.mesh.primitive_cone_add(vertices=8, radius1=0.12, radius2=0.07, depth=0.05, location=(0, 0, 0.055))
    b = bpy.context.active_object
    b.data.materials.append(m)
    return [a, b]


def build(item_id):
    t = item_id
    simple = {"wood": wood, "stone": stone, "cloth": cloth, "glue": glue, "resin": resin, "leather": leather, "scrap": scrap,
              "screw": screw, "powder": powder, "electronics": electronics, "tooth": tooth, "toxic": toxic,
              "titan_shard": titan_shard, "fuel": fuel, "bandage": bandage, "medkit": medkit, "food": food, "energy": energy,
              "antidote": antidote, "cloak": cloak, "coin": coin, "gem": gem}
    if t in simple:
        return simple[t]()
    if t == "dark_crystal":
        return crystal("#b070ff", 3.0)
    if t.startswith("ammo_"):
        return ammo(t)
    if t.startswith("w_"):
        return weapon_icon(t[2:])
    pre, tier = t.split("_", 1)
    return {"helm": helmet, "jacket": jacket, "boots": boot, "bag": bag}[pre](tier)
