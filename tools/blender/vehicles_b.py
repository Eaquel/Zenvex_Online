"""Detaylı araç modelleri (bpy). Ön taraf -Y (oyunda güney / kameraya doğru), orijin = taban ortası.

Gövde: yan profil poligonu genişlik boyunca çıkarılır, köşeleri yuvarlatılır (bevel), teker yuvaları boolean ile oyulur.
"""
import math

import bpy

import blib as B
from build_b import box, cyl, sphere, mesh_from, text, solid, _new, _mix, _bump, _mats

# --------------------------------------------------------------------------- malzemeler


def paint(color, metallic=0.55, rough=0.28):
    key = ("paint", color, metallic, rough)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("paint")
    c = B.hexrgb(color)
    lw = n.new("ShaderNodeLayerWeight")
    lw.inputs["Blend"].default_value = 0.35
    col = _mix(n, L, c, (c[0] * 0.45, c[1] * 0.45, c[2] * 0.5), lw.outputs["Facing"])
    L.new(col, bsdf.inputs["Base Color"])
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Coat Weight"].default_value = 1.0
    bsdf.inputs["Coat Roughness"].default_value = 0.03
    _mats[key] = m
    return m


def rust(base="#6a4a3a", burnt=False):
    key = ("rust", base, burnt)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("rust")
    tc = n.new("ShaderNodeTexCoord")
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 4.0
    nz.inputs["Detail"].default_value = 12
    nz.inputs["Roughness"].default_value = 0.65
    L.new(tc.outputs["Object"], nz.inputs["Vector"])
    ramp = n.new("ShaderNodeValToRGB")
    e = ramp.color_ramp.elements
    if burnt:
        e[0].color = (0.02, 0.02, 0.02, 1)
        e[1].color = (0.22, 0.12, 0.07, 1)
        e[0].position, e[1].position = 0.35, 0.7
        mid = ramp.color_ramp.elements.new(0.55)
        mid.color = (0.08, 0.06, 0.05, 1)
    else:
        c = B.hexrgb(base)
        e[0].color = (*c, 1)
        e[1].color = (0.42, 0.18, 0.06, 1)
        e[0].position, e[1].position = 0.42, 0.62
        mid = ramp.color_ramp.elements.new(0.75)
        mid.color = (0.25, 0.1, 0.04, 1)
    L.new(nz.outputs["Fac"], ramp.inputs["Fac"])
    L.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.85
    bsdf.inputs["Metallic"].default_value = 0.3
    _bump(n, L, bsdf, nz.outputs["Fac"], 0.6, 0.02)
    _mats[key] = m
    return m


def tire_mat():
    key = ("tire",)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("tire")
    tc = n.new("ShaderNodeTexCoord")
    wv = n.new("ShaderNodeTexWave")
    wv.wave_type = "RINGS"
    wv.inputs["Scale"].default_value = 0.0
    br = n.new("ShaderNodeTexBrick")
    br.inputs["Scale"].default_value = 20
    br.inputs["Mortar Size"].default_value = 0.08
    br.inputs["Color1"].default_value = (0.03, 0.03, 0.03, 1)
    br.inputs["Color2"].default_value = (0.04, 0.04, 0.04, 1)
    br.inputs["Mortar"].default_value = (0.01, 0.01, 0.01, 1)
    L.new(tc.outputs["Object"], br.inputs["Vector"])
    L.new(br.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.8
    _bump(n, L, bsdf, br.outputs["Fac"], 0.5, 0.01)
    _mats[key] = m
    return m


def car_glass():
    key = ("carglass",)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("carglass")
    lw = n.new("ShaderNodeLayerWeight")
    lw.inputs["Blend"].default_value = 0.4
    col = _mix(n, L, (0.02, 0.03, 0.05), (0.35, 0.45, 0.6), lw.outputs["Facing"])
    L.new(col, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.02
    bsdf.inputs["Metallic"].default_value = 0.5
    bsdf.inputs["Coat Weight"].default_value = 1.0
    _mats[key] = m
    return m


CHROME = lambda: solid("chrome", "#d8dadc", 0.12, 1.0)  # noqa: E731
PLASTIC = lambda: solid("plastic", "#1c1d1f", 0.55)  # noqa: E731


# --------------------------------------------------------------------------- parçalar
def extrude_profile(profile, half_w, mat, bevel=0.08, name="body"):
    """profile: [(y, z), ...] saat yönü tersine; x ekseninde ±half_w çıkarılır."""
    n = len(profile)
    v = [(-half_w, y, z) for y, z in profile] + [(half_w, y, z) for y, z in profile]
    f = [tuple(range(n))[::-1], tuple(range(n, 2 * n))]
    for i in range(n):
        j = (i + 1) % n
        f.append((i, j, n + j, n + i))
    ob = mesh_from(v, f, mat, name)
    for p in ob.data.polygons:
        p.use_smooth = True
    return ob


def finish_body(ob, bevel):
    """Boolean'lardan sonra çağrılır: yuvarlatma + ağırlıklı normaller."""
    md = ob.modifiers.new("bev", "BEVEL")
    md.width = bevel
    md.segments = 5
    md.limit_method = "ANGLE"
    md.angle_limit = math.radians(25)
    ob.modifiers.new("wn", "WEIGHTED_NORMAL")


def loft_cabin(bot_y, bot_z, top_y, top_z, bw, tw, glass_m, roof_m, pillar_m, bevel=0.03):
    """Kabin: alt (ön/arka y, z) ve üst (ön/arka y, z) çizgileri arasında; yanlar cam, tavan boya."""
    (by0, by1), (bz0, bz1) = bot_y, bot_z
    (ty0, ty1), (tz0, tz1) = top_y, top_z
    v = [(-bw, by0, bz0), (bw, by0, bz0), (bw, by1, bz1), (-bw, by1, bz1),
         (-tw, ty0, tz0), (tw, ty0, tz0), (tw, ty1, tz1), (-tw, ty1, tz1)]
    me = bpy.data.meshes.new("cabin")
    faces = [(0, 1, 5, 4), (2, 3, 7, 6), (1, 2, 6, 5), (3, 0, 4, 7), (4, 5, 6, 7), (0, 3, 2, 1)]
    me.from_pydata(v, [], faces)
    me.update()
    ob = bpy.data.objects.new("cabin", me)
    bpy.context.scene.collection.objects.link(ob)
    for mm in (glass_m, roof_m):
        ob.data.materials.append(mm)
    for i, p in enumerate(me.polygons):
        p.material_index = 1 if i in (4, 5) else 0
        p.use_smooth = True
    md = ob.modifiers.new("bev", "BEVEL")
    md.width = bevel
    md.segments = 3
    # direkler (A/B/C)
    for side in (-1, 1):
        for (ya, za), (yb, zb) in (((by0, bz0), (ty0, tz0)), ((by1, bz1), (ty1, tz1))):
            cyl((side * bw * 0.99, ya, za), (side * tw * 0.99, yb, zb), 0.045, pillar_m, 10)
        ym_b = (by0 + by1) / 2 + 0.1
        ym_t = (ty0 + ty1) / 2 + 0.05
        cyl((side * bw * 0.99, ym_b, (bz0 + bz1) / 2), (side * tw * 0.99, ym_t, (tz0 + tz1) / 2), 0.035, pillar_m, 10)
    return ob


def wheel(x, y, r, w, rim_col="#c8ccd0", spokes=5, side=1):
    tire = cyl((x - w / 2, y, r), (x + w / 2, y, r), r, tire_mat(), 32, "tire")
    md = tire.modifiers.new("bev", "BEVEL")
    md.width = min(0.06, w * 0.3)
    md.segments = 4
    rim = solid("rim" + rim_col, rim_col, 0.2, 1.0)
    if side == 0:
        for sd in (-1, 1):
            _rim(x, y, r, w, rim_col, spokes, sd)
        return tire
    _rim(x, y, r, w, rim_col, spokes, side)
    return tire


def _rim(x, y, r, w, rim_col, spokes, side):
    rim = solid("rim" + rim_col, rim_col, 0.2, 1.0)
    face = x + side * (w / 2 + 0.003)
    cyl((face - side * 0.02, y, r), (face, y, r), r * 0.66, rim, 28, "rim")
    cyl((face, y, r), (face + side * 0.012, y, r), r * 0.2, solid("hub", "#9a9ea2", 0.25, 1.0), 16)
    dark = solid("rimdark", "#202224", 0.4, 0.6)
    cyl((face - side * 0.015, y, r), (face - side * 0.005, y, r), r * 0.58, dark, 28)
    for k in range(spokes):
        a = k * 2 * math.pi / spokes
        c = (face + side * 0.004, y + math.cos(a) * r * 0.36, r + math.sin(a) * r * 0.36)
        ob = box(c, (0.008, r * 0.26, 0.03), rim)
        ob.rotation_euler = (a, 0, 0)


def arch_cutter(x_half, y, r, body):
    ob = cyl((-x_half - 0.2, y, r * 0.95), (x_half + 0.2, y, r * 0.95), r * 1.18, solid("cut", "#000000"), 32, "cutter")
    md = body.modifiers.new("arch", "BOOLEAN")
    md.operation = "DIFFERENCE"
    md.object = ob
    md.solver = "EXACT"
    ob.hide_render = True
    ob.hide_viewport = True
    ob.name = "CUTTER"
    return ob


def lights(hw, y_front, y_back, z, head_w=0.22, tail_w=0.24, h=0.09):
    head = solid("head", "#fff6e0", 0.1, emit=5.0)
    tail = solid("tail", "#ff2010", 0.2, emit=3.0)
    for s in (-1, 1):
        box((s * (hw - head_w * 0.75), y_front - 0.005, z), (head_w / 2, 0.03, h / 2), head, 0.02)
        box((s * (hw - tail_w * 0.7), y_back + 0.005, z + 0.02), (tail_w / 2, 0.03, h / 2.2), tail, 0.015)


def plate(y, z, front=True):
    m = solid("plate", "#f4f4f0", 0.4)
    box((0, y, z), (0.26, 0.012, 0.06), m, 0.005)
    box((-0.22, y - (0.013 if front else -0.013), z), (0.035, 0.002, 0.055), solid("plateb", "#2040b0", 0.4))
    t = text("34 ZX 01", (0.03, y - (0.016 if front else -0.016), z), 0.075, solid("platet", "#111111", 0.5), 0.002)
    if not front:
        t.rotation_euler = (math.pi / 2, 0, math.pi)


def mirrors(hw, y, z, mat):
    for s in (-1, 1):
        cyl((s * hw, y, z), (s * (hw + 0.14), y + 0.02, z + 0.02), 0.015, PLASTIC(), 6)
        o = sphere((s * (hw + 0.16), y + 0.02, z + 0.03), 0.07, mat)
        o.scale = (0.9, 0.5, 0.65)


# --------------------------------------------------------------------------- araçlar
def car(color="#b81e1e", wreck=None):
    """2 kişilik spor araba (4.3 m)."""
    hw = 0.9
    pm = rust(burnt=wreck == "burnt") if wreck else paint(color)
    prof = [(-2.12, 0.26), (-2.2, 0.5), (-2.08, 0.68), (-1.0, 0.84), (-0.7, 0.88), (1.5, 0.95), (2.08, 0.9),
            (2.18, 0.62), (2.12, 0.26)]
    body = extrude_profile(prof, hw, pm)
    for y in (-1.35, 1.38):
        arch_cutter(hw, y, 0.34, body)
    finish_body(body, 0.1)
    glass_m = rust(burnt=True) if wreck else car_glass()
    loft_cabin((-0.72, 1.48), (0.87, 0.94), (-0.05, 0.85), (1.27, 1.28), hw - 0.08, hw - 0.24, glass_m, pm, pm)
    # tamponlar, ızgara, etek
    box((0, -2.16, 0.34), (hw - 0.05, 0.07, 0.1), PLASTIC(), 0.04)
    box((0, 2.14, 0.34), (hw - 0.05, 0.07, 0.1), PLASTIC(), 0.04)
    box((0, -2.19, 0.52), (0.42, 0.02, 0.07), solid("grille", "#101112", 0.5, 0.5), 0.01)
    for k in range(5):
        box((0, -2.205, 0.47 + k * 0.025), (0.4, 0.004, 0.004), CHROME())
    for s in (-1, 1):
        box((s * (hw + 0.005), 0.05, 0.32), (0.012, 1.0, 0.04), PLASTIC(), 0.005)
        # kapı çizgileri ve kolları
        box((s * (hw + 0.004), -0.62, 0.6), (0.004, 0.006, 0.26), solid("seam", "#101010", 0.6))
        box((s * (hw + 0.004), 0.62, 0.6), (0.004, 0.006, 0.26), solid("seam", "#101010", 0.6))
        box((s * (hw + 0.012), 0.38, 0.74), (0.01, 0.07, 0.015), CHROME() if not wreck else pm, 0.005)
    # kaput çizgileri, spoiler
    for s in (-1, 1):
        box((s * 0.35, -1.5, 0.78), (0.01, 0.45, 0.004), solid("seam", "#101010", 0.6)).rotation_euler = (-0.14, 0, 0)
    box((0, 1.95, 1.02), (hw - 0.05, 0.12, 0.02), pm, 0.01)
    for s in (-1, 1):
        box((s * 0.6, 1.95, 0.96), (0.03, 0.05, 0.06), PLASTIC())
    if not wreck:
        lights(hw, -2.19, 2.17, 0.62)
        plate(-2.235, 0.36)
        plate(2.215, 0.36, front=False)
        mirrors(hw - 0.05, -0.62, 0.92, pm)
        for y in (-1.35, 1.38):
            for s in (-1, 1):
                wheel(s * (hw - 0.1), y, 0.33, 0.24, side=s)
        # egzoz
        for s in (-1, 1):
            cyl((s * 0.45, 2.1, 0.22), (s * 0.45, 2.25, 0.22), 0.045, CHROME(), 14)
    else:
        for y, s in ((-1.35, 1), (1.38, -1)):
            wheel(s * (hw - 0.1), y, 0.31, 0.22, rim_col="#5a4a3a", side=s)
    return body


def suv(color="#25472f", wreck=None):
    hw = 0.96
    pm = rust(burnt=wreck == "burnt") if wreck else paint(color, 0.4, 0.35)
    prof = [(-2.3, 0.36), (-2.36, 0.62), (-2.26, 0.95), (-1.25, 1.06), (2.25, 1.1), (2.36, 0.92), (2.36, 0.36)]
    body = extrude_profile(prof, hw, pm)
    for y in (-1.5, 1.52):
        arch_cutter(hw, y, 0.4, body)
    finish_body(body, 0.09)
    glass_m = rust(burnt=True) if wreck else car_glass()
    loft_cabin((-1.2, 2.3), (1.06, 1.1), (-0.78, 2.22), (1.72, 1.74), hw - 0.05, hw - 0.12, glass_m, pm, pm, 0.04)
    box((0, -2.37, 0.45), (hw, 0.08, 0.13), PLASTIC(), 0.04)
    box((0, 2.38, 0.45), (hw, 0.08, 0.13), PLASTIC(), 0.04)
    box((0, -2.38, 0.8), (0.55, 0.02, 0.13), solid("grille", "#101112", 0.5, 0.5), 0.01)
    for k in range(4):
        box((0, -2.395, 0.71 + k * 0.06), (0.53, 0.005, 0.008), CHROME())
    # tavan rayları
    for s in (-1, 1):
        cyl((s * 0.72, -0.65, 1.8), (s * 0.72, 2.1, 1.8), 0.025, CHROME() if not wreck else pm, 10)
        for y in (-0.6, 2.05):
            cyl((s * 0.72, y, 1.74), (s * 0.72, y, 1.8), 0.025, PLASTIC(), 8)
        box((s * (hw + 0.005), 0.0, 0.42), (0.03, 1.1, 0.06), PLASTIC(), 0.01)
        box((s * (hw + 0.004), -0.45, 0.8), (0.004, 0.006, 0.3), solid("seam", "#101010", 0.6))
        box((s * (hw + 0.004), 0.9, 0.8), (0.004, 0.006, 0.3), solid("seam", "#101010", 0.6))
        box((s * (hw + 0.012), -0.2, 0.92), (0.01, 0.08, 0.016), CHROME() if not wreck else pm, 0.005)
        box((s * (hw + 0.012), 1.15, 0.92), (0.01, 0.08, 0.016), CHROME() if not wreck else pm, 0.005)
    if not wreck:
        lights(hw, -2.37, 2.37, 0.9, 0.24, 0.2, 0.12)
        plate(-2.46, 0.48)
        plate(2.46, 0.48, front=False)
        mirrors(hw - 0.04, -1.05, 1.12, pm)
        for y in (-1.5, 1.52):
            for s in (-1, 1):
                wheel(s * (hw - 0.12), y, 0.39, 0.28, rim_col="#9aa0a4", spokes=6, side=s)
        # yedek lastik
        cyl((0, 2.38, 0.95), (0, 2.6, 0.95), 0.34, tire_mat(), 28)
        cyl((0, 2.6, 0.95), (0, 2.62, 0.95), 0.22, solid("rim#9aa0a4", "#9aa0a4", 0.2, 1.0), 20)
    else:
        wheel(hw - 0.12, -1.5, 0.37, 0.26, rim_col="#3a3430", side=1)
    return body


def moto(color="#d05a10"):
    pm = paint(color, 0.5, 0.25)
    blk = solid("mblk", "#141516", 0.5, 0.3)
    r = 0.31
    for y in (-0.72, 0.72):
        wheel(0, y, r, 0.13, rim_col="#2a2c2e", spokes=6, side=0)
    # çatal ve gidon
    for s in (-1, 1):
        cyl((s * 0.09, -0.72, r), (s * 0.09, -0.48, 1.0), 0.028, CHROME(), 12)
    cyl((-0.38, -0.44, 1.06), (0.38, -0.44, 1.06), 0.018, CHROME(), 10)
    for s in (-1, 1):
        cyl((s * 0.3, -0.44, 1.06), (s * 0.4, -0.44, 1.06), 0.026, blk, 10)
        sphere((s * 0.3, -0.52, 1.1), 0.03, CHROME())  # aynalar
    sphere((0, -0.6, 0.88), 0.11, CHROME()).scale = (1, 0.7, 1)
    box((0, -0.67, 0.88), (0.07, 0.01, 0.07), solid("head", "#fff6e0", 0.1, emit=5.0), 0.03)
    # çamurluk
    box((0, -0.72, r + 0.36), (0.08, 0.28, 0.02), pm, 0.02).rotation_euler = (0.25, 0, 0)
    box((0, 0.82, r + 0.38), (0.1, 0.3, 0.02), pm, 0.02).rotation_euler = (-0.35, 0, 0)
    # depo, sele, motor bloğu
    t = sphere((0, -0.18, 0.86), 0.24, pm)
    t.scale = (0.75, 1.25, 0.65)
    box((0, 0.32, 0.82), (0.15, 0.32, 0.05), solid("seat", "#1a1412", 0.6), 0.04)
    box((0, 0.68, 0.82), (0.1, 0.12, 0.06), pm, 0.03)
    box((0, 0.8, 0.82), (0.06, 0.01, 0.03), solid("tail", "#ff2010", 0.2, emit=3.0), 0.01)
    box((0, -0.05, 0.5), (0.16, 0.22, 0.17), solid("engine", "#5a5e62", 0.35, 0.8), 0.03)
    for k in range(4):
        box((0, -0.05, 0.42 + k * 0.05), (0.18, 0.2, 0.008), solid("fin", "#8a8e92", 0.3, 0.9))
    cyl((0, -0.32, 0.86), (0, 0.6, 0.52), 0.03, blk, 10)  # şasi
    cyl((0, -0.32, 0.86), (0, -0.2, 0.36), 0.03, blk, 10)
    cyl((0, 0.15, 0.4), (0, 0.72, r), 0.03, blk, 10)  # salıncak
    # egzoz
    cyl((0.14, -0.15, 0.42), (0.18, 0.3, 0.3), 0.035, CHROME(), 12)
    cyl((0.18, 0.3, 0.3), (0.2, 0.85, 0.42), 0.055, CHROME(), 16)
    return t


def bike(color="#2a6ad0"):
    fr = paint(color, 0.4, 0.3)
    r = 0.34
    for y in (-0.55, 0.55):
        tire = cyl((-0.02, y, r), (0.02, y, r), r, tire_mat(), 36)
        tire.modifiers.new("bev", "BEVEL").width = 0.015
        cyl((-0.025, y, r), (0.025, y, r), r - 0.035, solid("bwh", "#3a3c3e", 0.4, 0.6), 36)
        cyl((-0.03, y, r), (0.03, y, r), r - 0.05, solid("bwh2", "#0c0c0c", 0.5), 36)
        for k in range(16):
            a = k * math.pi / 8
            cyl((0, y, r), (0.01, y + math.cos(a) * (r - 0.05), r + math.sin(a) * (r - 0.05)), 0.004, CHROME(), 4)
        cyl((-0.035, y, r), (0.035, y, r), 0.03, CHROME(), 12)
    seat_t = (0, 0.22, 0.78)
    head_t = (0, -0.38, 0.86)
    bb = (0, 0.02, 0.3)
    for a, b in ((bb, seat_t), (bb, head_t), (seat_t, head_t), (bb, (0, 0.55, r)), (seat_t, (0, 0.55, r)),
                 (head_t, (0, -0.55, r))):
        cyl(a, b, 0.022, fr, 10)
    cyl(seat_t, (0, 0.25, 0.9), 0.016, CHROME(), 8)
    box((0, 0.27, 0.93), (0.07, 0.12, 0.03), solid("saddle", "#1a1412", 0.6), 0.025)
    cyl(head_t, (0, -0.4, 1.0), 0.018, CHROME(), 8)
    cyl((-0.27, -0.4, 1.0), (0.27, -0.4, 1.0), 0.014, CHROME(), 8)
    for s in (-1, 1):
        cyl((s * 0.22, -0.4, 1.0), (s * 0.3, -0.4, 1.0), 0.02, solid("grip", "#141414", 0.6), 8)
    # pedal kolu ve zincir dişlisi
    cyl((0.05, 0.02, 0.3), (0.06, 0.02, 0.3), 0.09, CHROME(), 24)
    cyl((0.07, 0.02, 0.3), (0.08, 0.13, 0.17), 0.012, CHROME(), 6)
    cyl((-0.07, 0.02, 0.3), (-0.08, -0.09, 0.43), 0.012, CHROME(), 6)
    box((0.13, 0.13, 0.17), (0.05, 0.03, 0.01), PLASTIC(), 0.005)
    box((-0.13, -0.09, 0.43), (0.05, 0.03, 0.01), PLASTIC(), 0.005)
    cyl((0.05, 0.02, 0.38), (0.05, 0.55, 0.4), 0.006, solid("chain", "#444444", 0.4, 0.9), 4)
    sphere((0, -0.48, 0.8), 0.04, solid("head", "#fff6e0", 0.1, emit=3.0))
    return None


VEHICLES = {
    "bike": lambda: bike(),
    "moto": lambda: moto(),
    "car": lambda: car(),
    "suv": lambda: suv(),
}
WRECKS = {
    "wreck0": lambda: car(wreck="rust"),
    "wreck1": lambda: suv(wreck="burnt"),
}
