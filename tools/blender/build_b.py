"""Detaylı binalar ve şehir yapıları (doğrudan bpy, prosedürel shader dokuları).

Koordinatlar Blender'dadır: x sağ, -y kameraya doğru (ön cephe), z yukarı. Orijin = taban ortası.
Ayak izleri world.rs çarpışma şekilleriyle aynıdır (yarı genişlik W, yarı derinlik D).
"""
import math
import random

import bpy
import bmesh
from mathutils import Vector

import blib as B

FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
_font = None
_mats = {}


def font():
    global _font
    if _font is None:
        try:
            _font = bpy.data.fonts.load(FONT_PATH)
        except Exception:
            _font = bpy.data.fonts["Bfont"] if "Bfont" in bpy.data.fonts else None
    return _font


def reset_cache():
    global _font
    _mats.clear()
    _font = None


# =========================================================================== malzemeler
def _new(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    return m, nt.nodes, nt.links, nt.nodes["Principled BSDF"]


def _uv(n, L, side):
    """Duvar düzlemine göre 2B doku koordinatı: ön (x,z), yan (y,z), üst (x,y)."""
    tc = n.new("ShaderNodeTexCoord")
    sep = n.new("ShaderNodeSeparateXYZ")
    L.new(tc.outputs["Object"], sep.inputs[0])
    comb = n.new("ShaderNodeCombineXYZ")
    a, b = {"front": (0, 2), "side": (1, 2), "top": (0, 1)}[side]
    L.new(sep.outputs[a], comb.inputs[0])
    L.new(sep.outputs[b], comb.inputs[1])
    L.new(sep.outputs[3 - a - b], comb.inputs[2])
    return comb.outputs[0], sep


def _mix(n, L, a, b, fac, blend="MIX"):
    m = n.new("ShaderNodeMix")
    m.data_type = "RGBA"
    m.blend_type = blend
    if isinstance(fac, float):
        m.inputs["Factor"].default_value = fac
    else:
        L.new(fac, m.inputs["Factor"])
    for idx, v in ((6, a), (7, b)):
        if isinstance(v, tuple):
            m.inputs[idx].default_value = (*v, 1) if len(v) == 3 else v
        else:
            L.new(v, m.inputs[idx])
    return m.outputs[2]


def _grime(n, L, col, sep, strength=0.35, height=1.2):
    """Tabana yakın kirlenme + dikey akıntı lekeleri + köşelerde AO."""
    # z küçükse koyu
    mr = n.new("ShaderNodeMapRange")
    L.new(sep.outputs[2], mr.inputs["Value"])
    mr.inputs["From Min"].default_value = 0.0
    mr.inputs["From Max"].default_value = height
    mr.inputs["To Min"].default_value = strength
    mr.inputs["To Max"].default_value = 0.0
    # akıntı lekeleri: x boyunca gerilmiş gürültü
    tc = n.new("ShaderNodeTexCoord")
    mp = n.new("ShaderNodeMapping")
    mp.inputs["Scale"].default_value = (6.0, 6.0, 0.35)
    L.new(tc.outputs["Object"], mp.inputs["Vector"])
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 3.0
    nz.inputs["Detail"].default_value = 6
    L.new(mp.outputs[0], nz.inputs["Vector"])
    cr = n.new("ShaderNodeMapRange")
    L.new(nz.outputs["Fac"], cr.inputs["Value"])
    cr.inputs["From Min"].default_value = 0.55
    cr.inputs["From Max"].default_value = 0.75
    cr.inputs["To Min"].default_value = 0.0
    cr.inputs["To Max"].default_value = strength * 0.22
    add = n.new("ShaderNodeMath")
    add.operation = "ADD"
    add.use_clamp = True
    L.new(mr.outputs[0], add.inputs[0])
    L.new(cr.outputs[0], add.inputs[1])
    dirt = _mix(n, L, col, (0.12, 0.1, 0.08), add.outputs[0], "MULTIPLY")
    # AO
    ao = n.new("ShaderNodeAmbientOcclusion")
    ao.inputs["Distance"].default_value = 0.35
    L.new(dirt, ao.inputs["Color"])
    aom = n.new("ShaderNodeMath")
    aom.operation = "MULTIPLY_ADD"
    L.new(ao.outputs["AO"], aom.inputs[0])
    aom.inputs[1].default_value = 0.45
    aom.inputs[2].default_value = 0.55
    return _mul_scalar(n, L, dirt, aom.outputs[0])


def _mul_scalar(n, L, col, s):
    m = n.new("ShaderNodeMix")
    m.data_type = "RGBA"
    m.blend_type = "MULTIPLY"
    m.inputs["Factor"].default_value = 1.0
    L.new(col, m.inputs[6])
    comb = n.new("ShaderNodeCombineColor")
    for i in range(3):
        L.new(s, comb.inputs[i])
    L.new(comb.outputs[0], m.inputs[7])
    return m.outputs[2]


def _bump(n, L, bsdf, h, strength=0.4, dist=0.01):
    bp = n.new("ShaderNodeBump")
    bp.inputs["Strength"].default_value = strength
    bp.inputs["Distance"].default_value = dist
    L.new(h, bp.inputs["Height"])
    L.new(bp.outputs["Normal"], bsdf.inputs["Normal"])


def brick(color="#a0523a", side="front", mortar="#cfc6b6", scale=1.0, grime=0.35):
    key = ("brick", color, side, mortar, scale)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("brick")
    uv, sep = _uv(n, L, side)
    br = n.new("ShaderNodeTexBrick")
    br.offset = 0.5
    br.inputs["Scale"].default_value = 2.5 / scale
    br.inputs["Mortar Size"].default_value = 0.018
    br.inputs["Mortar Smooth"].default_value = 0.25
    br.inputs["Brick Width"].default_value = 0.55
    br.inputs["Row Height"].default_value = 0.16
    br.inputs["Bias"].default_value = 0.0
    c = B.hexrgb(color)
    br.inputs["Color1"].default_value = (*c, 1)
    br.inputs["Color2"].default_value = (c[0] * 0.72, c[1] * 0.7, c[2] * 0.7, 1)
    br.inputs["Mortar"].default_value = (*B.hexrgb(mortar), 1)
    L.new(uv, br.inputs["Vector"])
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 40
    nz.inputs["Detail"].default_value = 8
    L.new(uv, nz.inputs["Vector"])
    col = _mix(n, L, br.outputs["Color"], nz.outputs["Fac"], 0.18, "OVERLAY")
    L.new(_grime(n, L, col, sep, grime), bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.85
    inv = n.new("ShaderNodeMath")
    inv.operation = "SUBTRACT"
    inv.inputs[0].default_value = 1.0
    L.new(br.outputs["Fac"], inv.inputs[1])
    hsum = n.new("ShaderNodeMath")
    hsum.operation = "MULTIPLY_ADD"
    L.new(nz.outputs["Fac"], hsum.inputs[0])
    hsum.inputs[1].default_value = 0.15
    L.new(inv.outputs[0], hsum.inputs[2])
    _bump(n, L, bsdf, hsum.outputs[0], 0.6, 0.02)
    _mats[key] = m
    return m


def plaster(color="#e6dcc4", side="front", grime=0.4, rough=0.9):
    key = ("plaster", color, side, grime)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("plaster")
    uv, sep = _uv(n, L, side)
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 60
    nz.inputs["Detail"].default_value = 10
    nz.inputs["Roughness"].default_value = 0.7
    L.new(uv, nz.inputs["Vector"])
    big = n.new("ShaderNodeTexNoise")
    big.inputs["Scale"].default_value = 1.6
    big.inputs["Detail"].default_value = 4
    L.new(uv, big.inputs["Vector"])
    c = B.hexrgb(color)
    col = _mix(n, L, c, big.outputs["Fac"], 0.22, "OVERLAY")
    col = _mix(n, L, col, nz.outputs["Fac"], 0.12, "OVERLAY")
    L.new(_grime(n, L, col, sep, grime), bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = rough
    _bump(n, L, bsdf, nz.outputs["Fac"], 0.25, 0.01)
    _mats[key] = m
    return m


def concrete(color="#9c9a94", side="front", grime=0.45, panels=0.0):
    key = ("concrete", color, side, grime, panels)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("concrete")
    uv, sep = _uv(n, L, side)
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 25
    nz.inputs["Detail"].default_value = 12
    L.new(uv, nz.inputs["Vector"])
    vor = n.new("ShaderNodeTexVoronoi")
    vor.inputs["Scale"].default_value = 6
    vor.feature = "DISTANCE_TO_EDGE"
    L.new(uv, vor.inputs["Vector"])
    crack = n.new("ShaderNodeMapRange")
    L.new(vor.outputs["Distance"], crack.inputs["Value"])
    crack.inputs["From Min"].default_value = 0.0
    crack.inputs["From Max"].default_value = 0.025
    crack.inputs["To Min"].default_value = 0.45
    crack.inputs["To Max"].default_value = 0.0
    c = B.hexrgb(color)
    col = _mix(n, L, c, nz.outputs["Fac"], 0.25, "OVERLAY")
    col = _mix(n, L, col, (0.2, 0.2, 0.2), crack.outputs[0], "MIX")
    h = nz.outputs["Fac"]
    if panels > 0:
        br = n.new("ShaderNodeTexBrick")
        br.offset = 0.0
        br.inputs["Scale"].default_value = 1.0 / panels
        br.inputs["Mortar Size"].default_value = 0.012
        br.inputs["Brick Width"].default_value = 1.0
        br.inputs["Row Height"].default_value = 0.6
        br.inputs["Color1"].default_value = (1, 1, 1, 1)
        br.inputs["Color2"].default_value = (0.94, 0.94, 0.94, 1)
        br.inputs["Mortar"].default_value = (0.35, 0.35, 0.35, 1)
        L.new(uv, br.inputs["Vector"])
        col = _mix(n, L, col, br.outputs["Color"], 1.0, "MULTIPLY")
        h = br.outputs["Fac"]
    L.new(_grime(n, L, col, sep, grime), bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.92
    _bump(n, L, bsdf, h, 0.35, 0.015)
    _mats[key] = m
    return m


def siding(color="#7d9cb0", side="front", grime=0.3):
    """Yatay ahşap kaplama tahtaları."""
    key = ("siding", color, side)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("siding")
    uv, sep = _uv(n, L, side)
    wv = n.new("ShaderNodeTexWave")
    wv.wave_type = "BANDS"
    wv.bands_direction = "Y"
    wv.wave_profile = "SAW"
    wv.inputs["Scale"].default_value = 3.6
    wv.inputs["Distortion"].default_value = 0.0
    L.new(uv, wv.inputs["Vector"])
    grain = n.new("ShaderNodeTexNoise")
    grain.inputs["Scale"].default_value = 30
    mp = n.new("ShaderNodeMapping")
    mp.inputs["Scale"].default_value = (0.15, 6.0, 1.0)
    L.new(uv, mp.inputs["Vector"])
    L.new(mp.outputs[0], grain.inputs["Vector"])
    c = B.hexrgb(color)
    shade = _mix(n, L, (c[0] * 0.7, c[1] * 0.7, c[2] * 0.7), c, wv.outputs["Fac"])
    col = _mix(n, L, shade, grain.outputs["Fac"], 0.12, "OVERLAY")
    L.new(_grime(n, L, col, sep, grime), bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.75
    _bump(n, L, bsdf, wv.outputs["Fac"], 0.5, 0.02)
    _mats[key] = m
    return m


def roof_tiles(color="#a8452e", kind="clay"):
    key = ("roof", color, kind)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("roof")
    tc = n.new("ShaderNodeTexCoord")
    sep = n.new("ShaderNodeSeparateXYZ")
    L.new(tc.outputs["Object"], sep.inputs[0])
    comb = n.new("ShaderNodeCombineXYZ")
    L.new(sep.outputs[0], comb.inputs[0])
    zz = n.new("ShaderNodeMath")
    zz.operation = "MULTIPLY"
    zz.inputs[1].default_value = 1.25
    L.new(sep.outputs[2], zz.inputs[0])
    L.new(zz.outputs[0], comb.inputs[1])
    uv = comb.outputs[0]
    br = n.new("ShaderNodeTexBrick")
    br.offset = 0.5
    br.inputs["Scale"].default_value = 3.0
    br.inputs["Mortar Size"].default_value = 0.01
    br.inputs["Brick Width"].default_value = 0.34 if kind == "clay" else 0.5
    br.inputs["Row Height"].default_value = 0.11
    c = B.hexrgb(color)
    br.inputs["Color1"].default_value = (*c, 1)
    br.inputs["Color2"].default_value = (c[0] * 0.78, c[1] * 0.74, c[2] * 0.72, 1)
    br.inputs["Mortar"].default_value = (c[0] * 0.3, c[1] * 0.28, c[2] * 0.26, 1)
    L.new(uv, br.inputs["Vector"])
    # her sıra: üstte açık, altta gölgeli (üst üste binme)
    wv = n.new("ShaderNodeTexWave")
    wv.wave_type = "BANDS"
    wv.bands_direction = "Y"
    wv.wave_profile = "SAW"
    wv.inputs["Scale"].default_value = 3.0 / 0.11 / 6.2832 * 6.2832 / 1.0
    wv.inputs["Distortion"].default_value = 0.0
    L.new(uv, wv.inputs["Vector"])
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 18
    nz.inputs["Detail"].default_value = 8
    L.new(uv, nz.inputs["Vector"])
    lit = n.new("ShaderNodeMapRange")
    L.new(wv.outputs["Fac"], lit.inputs["Value"])
    lit.inputs["To Min"].default_value = 0.62
    lit.inputs["To Max"].default_value = 1.0
    col = _mul_scalar(n, L, br.outputs["Color"], lit.outputs[0])
    col = _mix(n, L, col, nz.outputs["Fac"], 0.22, "OVERLAY")
    # yosun/kir lekeleri
    moss = n.new("ShaderNodeTexNoise")
    moss.inputs["Scale"].default_value = 2.5
    moss.inputs["Detail"].default_value = 6
    L.new(uv, moss.inputs["Vector"])
    mm = n.new("ShaderNodeMapRange")
    L.new(moss.outputs["Fac"], mm.inputs["Value"])
    mm.inputs["From Min"].default_value = 0.55
    mm.inputs["From Max"].default_value = 0.72
    mm.inputs["To Max"].default_value = 0.2
    col = _mix(n, L, col, (0.18, 0.2, 0.12), mm.outputs[0])
    L.new(col, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.6
    hs = n.new("ShaderNodeMath")
    hs.operation = "MULTIPLY"
    L.new(wv.outputs["Fac"], hs.inputs[0])
    L.new(br.outputs["Fac"], hs.inputs[1])
    _bump(n, L, bsdf, hs.outputs[0], 0.8, 0.03)
    _mats[key] = m
    return m


def solid(name, color, rough=0.5, metal=0.0, emit=0.0, coat=0.0, noise=0.08, aniso=0.0):
    key = ("solid", name, color, rough, metal, emit)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new(name)
    c = B.hexrgb(color)
    tc = n.new("ShaderNodeTexCoord")
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 30
    nz.inputs["Detail"].default_value = 6
    L.new(tc.outputs["Object"], nz.inputs["Vector"])
    col = _mix(n, L, c, nz.outputs["Fac"], noise, "OVERLAY")
    L.new(col, bsdf.inputs["Base Color"])
    rr = n.new("ShaderNodeMapRange")
    L.new(nz.outputs["Fac"], rr.inputs["Value"])
    rr.inputs["To Min"].default_value = max(0.02, rough - 0.12)
    rr.inputs["To Max"].default_value = min(1.0, rough + 0.12)
    L.new(rr.outputs[0], bsdf.inputs["Roughness"])
    bsdf.inputs["Metallic"].default_value = metal
    if coat:
        bsdf.inputs["Coat Weight"].default_value = coat
    if aniso:
        bsdf.inputs["Anisotropic"].default_value = aniso
    if emit:
        bsdf.inputs["Emission Color"].default_value = (*c, 1)
        bsdf.inputs["Emission Strength"].default_value = emit
    _mats[key] = m
    return m


def glass(lit_chance=0.25, tint="#2a3a4a"):
    """Pencere camı: gökyüzü yansıması + rastgele bazıları içeriden aydınlık (Object Info → Random)."""
    key = ("glass", lit_chance, tint)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("glass")
    oi = n.new("ShaderNodeObjectInfo")
    ramp = n.new("ShaderNodeValToRGB")
    ramp.color_ramp.interpolation = "CONSTANT"
    e = ramp.color_ramp.elements
    e[0].position = 0.0
    e[0].color = (0, 0, 0, 1)
    e[1].position = 1.0 - lit_chance
    e[1].color = (1, 1, 1, 1)
    L.new(oi.outputs["Random"], ramp.inputs["Fac"])
    # dikey yansıma gradyanı
    tc = n.new("ShaderNodeTexCoord")
    sep = n.new("ShaderNodeSeparateXYZ")
    L.new(tc.outputs["Generated"], sep.inputs[0])
    grad = n.new("ShaderNodeMapRange")
    L.new(sep.outputs[2], grad.inputs["Value"])
    grad.inputs["To Min"].default_value = 0.35
    grad.inputs["To Max"].default_value = 1.0
    t = B.hexrgb(tint)
    refl = _mul_scalar(n, L, _mix(n, L, t, (0.55, 0.65, 0.78), 0.35), grad.outputs[0])
    warm = (1.0, 0.72, 0.38)
    col = _mix(n, L, refl, (0.25, 0.18, 0.1), ramp.outputs["Color"])
    L.new(col, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.04
    bsdf.inputs["Metallic"].default_value = 0.35
    bsdf.inputs["Coat Weight"].default_value = 1.0
    bsdf.inputs["Emission Color"].default_value = (*warm, 1)
    em = n.new("ShaderNodeMath")
    em.operation = "MULTIPLY"
    em.inputs[1].default_value = 1.6
    sepc = n.new("ShaderNodeSeparateColor")
    L.new(ramp.outputs["Color"], sepc.inputs[0])
    L.new(sepc.outputs[0], em.inputs[0])
    L.new(em.outputs[0], bsdf.inputs["Emission Strength"])
    _mats[key] = m
    return m


def stripes(c1="#d83a32", c2="#f2efe6", scale=6.0, axis="X"):
    key = ("stripes", c1, c2, scale, axis)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("stripes")
    tc = n.new("ShaderNodeTexCoord")
    wv = n.new("ShaderNodeTexWave")
    wv.wave_type = "BANDS"
    wv.bands_direction = axis
    wv.inputs["Scale"].default_value = scale
    wv.inputs["Distortion"].default_value = 0.0
    L.new(tc.outputs["Object"], wv.inputs["Vector"])
    gt = n.new("ShaderNodeMath")
    gt.operation = "GREATER_THAN"
    gt.inputs[1].default_value = 0.5
    L.new(wv.outputs["Fac"], gt.inputs[0])
    col = _mix(n, L, B.hexrgb(c1), B.hexrgb(c2), gt.outputs[0])
    L.new(col, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.7
    bsdf.inputs["Sheen Weight"].default_value = 0.4
    _mats[key] = m
    return m


def wood(color="#8a6440", side="front"):
    key = ("wood", color, side)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("wood")
    uv, sep = _uv(n, L, side)
    mp = n.new("ShaderNodeMapping")
    mp.inputs["Scale"].default_value = (0.4, 8.0, 1.0)
    L.new(uv, mp.inputs["Vector"])
    wv = n.new("ShaderNodeTexWave")
    wv.inputs["Scale"].default_value = 2.0
    wv.inputs["Distortion"].default_value = 6.0
    wv.inputs["Detail"].default_value = 4
    L.new(mp.outputs[0], wv.inputs["Vector"])
    c = B.hexrgb(color)
    col = _mix(n, L, c, (c[0] * 0.6, c[1] * 0.55, c[2] * 0.5), wv.outputs["Fac"])
    L.new(_grime(n, L, col, sep, 0.25, 0.6), bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.7
    _bump(n, L, bsdf, wv.outputs["Fac"], 0.3, 0.01)
    _mats[key] = m
    return m


# =========================================================================== geometri
def box(c, h, mat, bevel=0.0, name="box"):
    """c: merkez (x, y, z); h: yarı boyutlar."""
    bpy.ops.mesh.primitive_cube_add(size=2, location=c)
    ob = bpy.context.active_object
    ob.name = name
    ob.scale = h
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel > 0:
        md = ob.modifiers.new("bev", "BEVEL")
        md.width = bevel
        md.segments = 2
        md.limit_method = "ANGLE"
    ob.data.materials.append(mat)
    return ob


def cyl(a, b, r, mat, verts=20, name="cyl", cap=True):
    a, b = Vector(a), Vector(b)
    d = b - a
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=d.length, location=(a + b) / 2,
                                        end_fill_type="NGON" if cap else "NOTHING")
    ob = bpy.context.active_object
    ob.name = name
    ob.rotation_mode = "QUATERNION"
    ob.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
    ob.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return ob


def sphere(c, r, mat, name="sph"):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=20, ring_count=10, radius=r, location=c)
    ob = bpy.context.active_object
    ob.name = name
    ob.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return ob


def mesh_from(verts, faces, mat, name="mesh", smooth=False):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(mat)
    if smooth:
        for p in me.polygons:
            p.use_smooth = True
    return ob


def text(body, c, size, mat, depth=0.02, face="front", align="CENTER"):
    cu = bpy.data.curves.new("txt", "FONT")
    cu.body = body
    f = font()
    if f:
        cu.font = f
    cu.size = size
    cu.extrude = depth
    cu.align_x = align
    cu.align_y = "CENTER"
    ob = bpy.data.objects.new("txt", cu)
    bpy.context.scene.collection.objects.link(ob)
    ob.location = c
    ob.rotation_euler = (math.pi / 2, 0, 0) if face == "front" else (math.pi / 2, 0, -math.pi / 2)
    ob.data.materials.append(mat)
    return ob


def gable_roof(W, D, z0, rise, over, mat, ridge_axis="x", thick=0.12):
    """Beşik çatı: sırt x ekseni boyunca; saçaklar `over` kadar taşar."""
    w, d = W + over, D + over
    t = thick
    if ridge_axis == "x":
        v = [(-w, -d, z0), (w, -d, z0), (w, 0, z0 + rise), (-w, 0, z0 + rise),
             (-w, d, z0), (w, d, z0),
             (-w, -d, z0 + t), (w, -d, z0 + t), (w, 0, z0 + rise + t), (-w, 0, z0 + rise + t), (-w, d, z0 + t), (w, d, z0 + t)]
        f = [(6, 7, 8, 9), (9, 8, 11, 10), (0, 3, 2, 1), (3, 4, 5, 2), (0, 1, 7, 6), (4, 10, 11, 5),
             (0, 6, 9, 3), (3, 9, 10, 4), (1, 2, 8, 7), (2, 5, 11, 8)]
    else:
        v = [(-w, -d, z0), (0, -d, z0 + rise), (w, -d, z0), (-w, d, z0), (0, d, z0 + rise), (w, d, z0),
             (-w, -d, z0 + t), (0, -d, z0 + rise + t), (w, -d, z0 + t), (-w, d, z0 + t), (0, d, z0 + rise + t), (w, d, z0 + t)]
        f = [(6, 9, 10, 7), (7, 10, 11, 8), (0, 1, 4, 3), (1, 2, 5, 4), (0, 6, 7, 1), (1, 7, 8, 2),
             (3, 4, 10, 9), (4, 5, 11, 10), (0, 3, 9, 6), (2, 8, 11, 5)]
    return mesh_from(v, f, mat, "roof")


def gable_wall(W, D, z0, rise, mat, ridge_axis="x"):
    """Çatı altındaki üçgen duvarlar (alınlık)."""
    if ridge_axis == "x":
        v = [(-W, -D, z0), (-W, D, z0), (-W, 0, z0 + rise), (W, -D, z0), (W, D, z0), (W, 0, z0 + rise)]
        f = [(0, 1, 2), (3, 5, 4), (0, 2, 5, 3), (1, 4, 5, 2)]
    else:
        v = [(-W, -D, z0), (W, -D, z0), (0, -D, z0 + rise), (-W, D, z0), (W, D, z0), (0, D, z0 + rise)]
        f = [(0, 2, 1), (3, 4, 5), (0, 3, 5, 2), (1, 2, 5, 4)]
    return mesh_from(v, f, mat, "gable")


def window(x, z, w, h, y_face, frame_mat, glass_mat, sill_mat=None, cross=True, shutters=None, side=False, depth=0.06):
    """Ön cephe (side=False → düzlem y=y_face, -y yönüne bakar) veya yan cephe (x=y_face, +x yönüne bakar)."""
    obs = []
    fw = 0.055

    def B_(cx, cz, hx, hz, dy, hy, mat, bev=0.0):
        if side:
            obs.append(box((y_face + dy, cx, cz), (hy, hx, hz), mat, bev))
        else:
            obs.append(box((cx, y_face - dy, cz), (hx, hy, hz), mat, bev))

    B_(x, z, w / 2 - fw, h / 2 - fw, -depth * 0.5, 0.01, glass_mat)  # cam (girintili)
    B_(x, z + h / 2 - fw / 2, w / 2, fw / 2, 0.0, depth * 0.6, frame_mat, 0.008)
    B_(x, z - h / 2 + fw / 2, w / 2, fw / 2, 0.0, depth * 0.6, frame_mat, 0.008)
    B_(x - w / 2 + fw / 2, z, fw / 2, h / 2, 0.0, depth * 0.6, frame_mat, 0.008)
    B_(x + w / 2 - fw / 2, z, fw / 2, h / 2, 0.0, depth * 0.6, frame_mat, 0.008)
    if cross:
        B_(x, z, 0.022, h / 2 - fw, 0.0, depth * 0.4, frame_mat)
        B_(x, z + h * 0.12, w / 2 - fw, 0.022, 0.0, depth * 0.4, frame_mat)
    if sill_mat:
        B_(x, z - h / 2 - 0.035, w / 2 + 0.07, 0.035, 0.06, 0.09, sill_mat, 0.01)
        B_(x, z + h / 2 + 0.05, w / 2 + 0.05, 0.05, 0.03, 0.07, sill_mat, 0.01)
    if shutters:
        for s in (-1, 1):
            B_(x + s * (w / 2 + 0.17), z, 0.16, h / 2, 0.02, 0.02, shutters, 0.01)
    return obs


def door(x, z0, w, h, y_face, mat, frame_mat, knob_mat, step_mat=None, side=False, glass_mat=None):
    obs = []

    def B_(cx, cz, hx, hz, dy, hy, m, bev=0.0):
        if side:
            obs.append(box((y_face + dy, cx, cz), (hy, hx, hz), m, bev))
        else:
            obs.append(box((cx, y_face - dy, cz), (hx, hy, hz), m, bev))

    B_(x, z0 + h / 2, w / 2, h / 2, -0.03, 0.03, mat, 0.01)
    # panel çıkıntıları
    for k, (pz, ph) in enumerate(((z0 + h * 0.72, h * 0.16), (z0 + h * 0.3, h * 0.2))):
        if glass_mat is not None and k == 0:
            B_(x, pz, w * 0.32, ph, 0.0, 0.01, glass_mat)
        else:
            B_(x, pz, w * 0.32, ph, 0.0, 0.012, mat, 0.01)
    B_(x, z0 + h + 0.05, w / 2 + 0.08, 0.05, 0.0, 0.06, frame_mat, 0.01)
    B_(x - w / 2 - 0.04, z0 + h / 2, 0.04, h / 2, 0.0, 0.06, frame_mat, 0.01)
    B_(x + w / 2 + 0.04, z0 + h / 2, 0.04, h / 2, 0.0, 0.06, frame_mat, 0.01)
    if side:
        obs.append(sphere((y_face + 0.04, x + w * 0.35, z0 + h * 0.48), 0.035, knob_mat))
    else:
        obs.append(sphere((x + w * 0.35, y_face - 0.04, z0 + h * 0.48), 0.035, knob_mat))
    if step_mat:
        B_(x, z0 + 0.07, w / 2 + 0.25, 0.07, 0.2, 0.22, step_mat, 0.01)
    return obs


def ac_unit(c, face="front"):
    body = solid("ac", "#d8d8d2", 0.4)
    grill = solid("grill", "#55585a", 0.5, 0.6)
    x, y, z = c
    if face == "front":
        box((x, y - 0.18, z), (0.38, 0.18, 0.26), body, 0.03)
        cyl((x - 0.08, y - 0.37, z), (x - 0.08, y - 0.35, z), 0.18, grill, 24)
    else:
        box((x + 0.18, y, z), (0.18, 0.38, 0.26), body, 0.03)
        cyl((x + 0.37, y - 0.08, z), (x + 0.35, y - 0.08, z), 0.18, grill, 24)


def gutter(x0, x1, y, z, mat):
    cyl((x0, y, z), (x1, y, z), 0.05, mat, 12)


def downpipe(x, y, z0, z1, mat):
    cyl((x, y, z0), (x, y, z1), 0.04, mat, 12)


def railing(x0, x1, y, z, h, mat, n=None, axis="x"):
    L = abs(x1 - x0)
    n = n or max(3, int(L / 0.14))
    for i in range(n + 1):
        t = x0 + (x1 - x0) * i / n
        p0 = (t, y, z) if axis == "x" else (y, t, z)
        p1 = (t, y, z + h) if axis == "x" else (y, t, z + h)
        cyl(p0, p1, 0.012, mat, 8)
    a = (x0, y, z + h) if axis == "x" else (y, x0, z + h)
    b = (x1, y, z + h) if axis == "x" else (y, x1, z + h)
    cyl(a, b, 0.025, mat, 10)


def plants_box(x, y, z, w):
    soil = solid("pbox", "#6a4a32", 0.8)
    box((x, y, z), (w, 0.1, 0.08), soil, 0.01)
    rng = random.Random(int(x * 100 + z * 10))
    for i in range(int(w * 14)):
        px = x - w + 2 * w * rng.random()
        col = rng.choice(["#d83a5a", "#f2cc3a", "#e86aa8", "#f2f0e6", "#a05ae8"])
        sphere((px, y - 0.02 + rng.uniform(-0.04, 0.04), z + 0.1 + rng.uniform(0, 0.06)), 0.035, solid("pl" + col, col, 0.6))
        sphere((px + 0.02, y, z + 0.06), 0.045, solid("leafg", "#3e7a2e", 0.7))


def gravel(color="#77736c"):
    key = ("gravel", color)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("gravel")
    uv, sep = _uv(n, L, "top")
    vor = n.new("ShaderNodeTexVoronoi")
    vor.inputs["Scale"].default_value = 70
    L.new(uv, vor.inputs["Vector"])
    nz = n.new("ShaderNodeTexNoise")
    nz.inputs["Scale"].default_value = 2.0
    nz.inputs["Detail"].default_value = 6
    L.new(uv, nz.inputs["Vector"])
    c = B.hexrgb(color)
    col = _mix(n, L, c, vor.outputs["Color"], 0.25, "OVERLAY")
    col = _mix(n, L, col, nz.outputs["Fac"], 0.3, "OVERLAY")
    ao = n.new("ShaderNodeAmbientOcclusion")
    ao.inputs["Distance"].default_value = 0.5
    L.new(col, ao.inputs["Color"])
    aom = n.new("ShaderNodeMath")
    aom.operation = "MULTIPLY_ADD"
    L.new(ao.outputs["AO"], aom.inputs[0])
    aom.inputs[1].default_value = 0.5
    aom.inputs[2].default_value = 0.5
    L.new(_mul_scalar(n, L, ao.outputs["Color"], aom.outputs[0]), bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.95
    _bump(n, L, bsdf, vor.outputs["Distance"], 0.6, 0.01)
    _mats[key] = m
    return m


def interior(seed=0):
    """Vitrin arkası: rafta renkli ürünler (aydınlık)."""
    key = ("interior", seed)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("interior")
    uv, sep = _uv(n, L, "front")
    br = n.new("ShaderNodeTexBrick")
    br.offset = 0.3
    br.inputs["Scale"].default_value = 6.0
    br.inputs["Mortar Size"].default_value = 0.03
    br.inputs["Brick Width"].default_value = 0.18
    br.inputs["Row Height"].default_value = 0.12
    br.inputs["Color1"].default_value = (0.8, 0.2, 0.1, 1)
    br.inputs["Color2"].default_value = (0.1, 0.4, 0.8, 1)
    br.inputs["Mortar"].default_value = (0.85, 0.82, 0.75, 1)
    L.new(uv, br.inputs["Vector"])
    hue = n.new("ShaderNodeHueSaturation")
    vor = n.new("ShaderNodeTexVoronoi")
    vor.inputs["Scale"].default_value = 12 + seed
    L.new(uv, vor.inputs["Vector"])
    L.new(br.outputs["Color"], hue.inputs["Color"])
    sepc = n.new("ShaderNodeSeparateColor")
    L.new(vor.outputs["Color"], sepc.inputs[0])
    L.new(sepc.outputs[0], hue.inputs["Hue"])
    L.new(hue.outputs[0], bsdf.inputs["Base Color"])
    bsdf.inputs["Emission Color"].default_value = (1.0, 0.95, 0.85, 1)
    L.new(hue.outputs[0], bsdf.inputs["Emission Color"])
    bsdf.inputs["Emission Strength"].default_value = 0.9
    _mats[key] = m
    return m


def shop_glass():
    key = ("shopglass",)
    if key in _mats:
        return _mats[key]
    m, n, L, bsdf = _new("shopglass")
    bsdf.inputs["Base Color"].default_value = (0.6, 0.7, 0.8, 1)
    bsdf.inputs["Roughness"].default_value = 0.03
    bsdf.inputs["Alpha"].default_value = 0.22
    bsdf.inputs["Coat Weight"].default_value = 1.0
    _mats[key] = m
    return m


def flat_roof(W, D, z, rng, parapet_mat, solar=0, ac=2, dish=True, hatch=True, vents=3, tank=False):
    """Düz çatı: parapet, çakıl, klima dış üniteleri (üstten fan), güneş paneli, çanak anten, havalandırma."""
    box((0, 0, z + 0.03), (W - 0.12, D - 0.12, 0.03), gravel())
    for sx in (-1, 1):
        box((sx * (W - 0.06), 0, z + 0.28), (0.1, D + 0.04, 0.28), parapet_mat, 0.01)
    for sy in (-1, 1):
        box((0, sy * (D - 0.06), z + 0.28), (W + 0.04, 0.1, 0.28), parapet_mat, 0.01)
    cap = concrete("#bab6ae", "top", 0.2)
    for sx in (-1, 1):
        box((sx * (W - 0.06), 0, z + 0.58), (0.13, D + 0.07, 0.025), cap, 0.005)
    for sy in (-1, 1):
        box((0, sy * (D - 0.06), z + 0.58), (W + 0.07, 0.13, 0.025), cap, 0.005)
    body = solid("acb", "#cfcfc8", 0.45)
    fan = solid("fan", "#2a2c2e", 0.5, 0.6)
    used = []

    def spot(hw, hd):
        for _ in range(40):
            x = rng.uniform(-W + 0.4 + hw, W - 0.4 - hw)
            y = rng.uniform(-D + 0.4 + hd, D - 0.4 - hd)
            if all(abs(x - ux) > hw + uw + 0.15 or abs(y - uy) > hd + ud + 0.15 for ux, uy, uw, ud in used):
                used.append((x, y, hw, hd))
                return x, y
        return None

    if tank:
        pt = spot(0.6, 0.6)
        if pt:
            cyl((pt[0], pt[1], z + 0.06), (pt[0], pt[1], z + 1.2), 0.55, solid("tank", "#d8d4cc", 0.4, 0.3), 32)
            cyl((pt[0], pt[1], z + 1.2), (pt[0], pt[1], z + 1.3), 0.4, solid("tankc", "#b8b4ac", 0.4, 0.3), 32)
    for _ in range(solar):
        pt = spot(0.9, 0.55)
        if not pt:
            continue
        x, y = pt
        pv = solid("pv", "#1a2a4a", 0.12, 0.3, coat=1.0)
        pf = solid("pvf", "#b0b4b8", 0.3, 0.9)
        ob = box((x, y, z + 0.45), (0.85, 0.5, 0.02), pv)
        ob.rotation_euler = (-0.35, 0, 0)
        for k in range(1, 4):
            o2 = box((x - 0.85 + k * 0.425, y, z + 0.47), (0.008, 0.5, 0.025), pf)
            o2.rotation_euler = (-0.35, 0, 0)
        o3 = box((x, y, z + 0.47), (0.85, 0.008, 0.025), pf)
        o3.rotation_euler = (-0.35, 0, 0)
        for sx in (-0.7, 0.7):
            cyl((x + sx, y + 0.3, z + 0.06), (x + sx, y + 0.3, z + 0.62), 0.02, pf, 6)
    for _ in range(ac):
        pt = spot(0.45, 0.35)
        if not pt:
            continue
        x, y = pt
        box((x, y, z + 0.38), (0.45, 0.35, 0.32), body, 0.03)
        cyl((x, y, z + 0.7), (x, y, z + 0.72), 0.27, fan, 28)
        for k in range(5):
            o = box((x, y, z + 0.725), (0.27, 0.006, 0.006), solid("grl", "#8a8e90", 0.3, 0.9))
            o.rotation_euler = (0, 0, k * math.pi / 5)
    if hatch:
        pt = spot(0.45, 0.45)
        if pt:
            box((pt[0], pt[1], z + 0.2), (0.45, 0.45, 0.16), concrete("#8a8680", "front", 0.3), 0.02)
            box((pt[0], pt[1], z + 0.38), (0.38, 0.38, 0.03), solid("hatch", "#5a6a72", 0.4, 0.6), 0.01)
    for _ in range(vents):
        pt = spot(0.12, 0.12)
        if pt:
            cyl((pt[0], pt[1], z + 0.06), (pt[0], pt[1], z + 0.55), 0.07, solid("vent", "#9a9ea2", 0.35, 0.85), 14)
            cyl((pt[0], pt[1], z + 0.55), (pt[0], pt[1], z + 0.6), 0.13, solid("vent", "#9a9ea2", 0.35, 0.85), 14)
    if dish:
        pt = spot(0.35, 0.35)
        if pt:
            x, y = pt
            cyl((x, y, z + 0.06), (x, y, z + 0.6), 0.025, solid("dpole", "#7a7e80", 0.4, 0.8), 8)
            d = sphere((x, y - 0.05, z + 0.75), 0.3, solid("dish", "#e8e8e4", 0.35))
            d.scale = (1, 0.25, 1)
            d.rotation_euler = (0.6, 0, 0.4)
    # ince su boruları
    pipe = solid("pipe", "#6a6e70", 0.4, 0.7)
    y = rng.uniform(-D * 0.5, D * 0.5)
    cyl((-W + 0.3, y, z + 0.12), (W - 0.3, y, z + 0.12), 0.035, pipe, 10)


# =========================================================================== binalar
def house(kind=0):
    W, D, H = 3.4, 2.8, 2.7
    rng = random.Random(10 + kind)
    walls = [("plaster", "#e8dcc0"), ("brick", "#a4553c"), ("siding", "#7f9fb2")][kind % 3]
    roof_col = ["#a8452e", "#4a4f57", "#3f6a46"][kind % 3]
    trim = solid("trim", "#f4f0e6", 0.5)
    frame = solid("frame", ["#f4f0e6", "#3b3b3b", "#f4f0e6"][kind % 3], 0.45)
    shut = solid("shutter", ["#3f6a46", "#7a2e22", "#2e3f5a"][kind % 3], 0.6)
    gl = glass(0.3)
    metal = solid("gutter", "#8a8e90", 0.35, 0.8)

    def wm(side):
        k, c = walls
        return {"plaster": plaster, "brick": brick, "siding": siding}[k](c, side)

    # temel / kaide
    box((0, 0, 0.18), (W + 0.06, D + 0.06, 0.18), concrete("#8a8680", "front", 0.5), 0.02)
    # duvarlar: ön/arka ve yanlar ayrı malzemeyle (doku yönü)
    box((0, 0, 0.36 + (H - 0.36) / 2), (W, D - 0.02, (H - 0.36) / 2), wm("front"))
    box((0, 0, 0.36 + (H - 0.36) / 2), (W - 0.02, D, (H - 0.36) / 2), wm("side"))
    # köşe pervazları
    for sx in (-1, 1):
        box((sx * W, -D, H / 2 + 0.18), (0.07, 0.07, H / 2 - 0.18), trim, 0.01)
    rise = 1.7
    gable_wall(W, D, H, rise, wm("side"), "x")
    gable_roof(W, D, H, rise, 0.35, roof_tiles(roof_col, "clay" if kind != 1 else "slate"), "x")
    # sırt mahyası
    cyl((-W - 0.35, 0, H + rise + 0.14), (W + 0.35, 0, H + rise + 0.14), 0.07, solid("ridge", roof_col, 0.6), 14)
    # baca
    box((W * 0.55, D * 0.3, H + rise * 0.8), (0.28, 0.28, 0.9), brick("#8a4a36", "front"), 0.01)
    box((W * 0.55, D * 0.3, H + rise * 0.8 + 0.92), (0.33, 0.33, 0.06), concrete("#77736d"), 0.01)
    # oluk ve iniş borusu
    gutter(-W - 0.35, W + 0.35, -D - 0.38, H + 0.02, metal)
    downpipe(W + 0.25, -D - 0.38, 0.1, H + 0.02, metal)
    # kapı + saçak + lamba
    dx = 0.9 if kind != 2 else -0.6
    door(dx, 0.36, 0.95, 2.0, -D, solid("door", ["#6a3a22", "#2e4a6a", "#7a2e22"][kind % 3], 0.5), trim,
         solid("brass", "#c8a050", 0.3, 1.0), concrete("#9a968e"), glass_mat=gl)
    box((dx, -D - 0.45, 2.55), (0.75, 0.45, 0.04), roof_tiles(roof_col), 0.01)
    for s in (-1, 1):
        cyl((dx + s * 0.68, -D - 0.85, 0.36), (dx + s * 0.68, -D - 0.85, 2.52), 0.04, trim, 12)
    sphere((dx - 0.62, -D - 0.06, 2.2), 0.07, solid("lamp", "#ffd890", 0.3, emit=6.0))
    # pencereler
    wx = -1.4 if kind != 2 else 1.3
    window(wx, 1.55, 1.25, 1.15, -D, frame, gl, trim, True, shut)
    plants_box(wx, -D - 0.12, 0.88, 0.62)
    # yan cephe pencereleri
    for y in (-1.2, 1.2):
        window(y, 1.55, 1.0, 1.05, W, frame, gl, trim, True, None, side=True)
    # çatı penceresi (kind 0)
    if kind == 0:
        box((-1.2, -0.9, H + 0.95), (0.5, 0.45, 0.45), plaster("#e8dcc0"), 0.01)
        gable_roof(0.5, 0.45, H + 1.4, 0.4, 0.1, roof_tiles(roof_col), "y").location = (-1.2, -0.9, 0)
        window(-1.2, H + 0.95, 0.55, 0.55, -1.36, frame, gl, None, True)
    if kind == 1:
        ac_unit((W, 1.2, 1.2), "side")
    # bahçe çiti parçası ve çalı
    shrub = solid("shrub", "#3e6e2e", 0.8)
    for i in range(3):
        sphere((-W + 0.4 + i * 0.5, -D - 0.35, 0.3), 0.32 + 0.05 * rng.random(), shrub)
    return {"half": (W, D)}


def apartment(kind=0, floors=3):
    W, D = 5.0, 3.6
    fh = 2.8
    H = floors * fh
    base = [("plaster", "#d9cdb4"), ("brick", "#8e4a34"), ("plaster", "#b9c4c8")][kind % 3]
    gl = glass(0.28)
    frame = solid("frame", "#ececec", 0.4)
    dark = concrete("#6e6c68", "front", 0.4)
    trim = concrete("#c8c4bc", "front", 0.3)
    rail = solid("rail", "#2e3236", 0.4, 0.8)

    def wm(side):
        return plaster(base[1], side, 0.45) if base[0] == "plaster" else brick(base[1], side)

    # zemin kat: koyu taş kaplama
    box((0, 0, fh / 2), (W, D - 0.02, fh / 2), concrete("#7a7670", "front", 0.4, panels=0.9))
    box((0, 0, fh / 2), (W - 0.02, D, fh / 2), concrete("#7a7670", "side", 0.4, panels=0.9))
    box((0, 0, fh + (H - fh) / 2), (W, D - 0.02, (H - fh) / 2), wm("front"))
    box((0, 0, fh + (H - fh) / 2), (W - 0.02, D, (H - fh) / 2), wm("side"))
    # kat silmeleri
    for f in range(1, floors + 1):
        box((0, -0.0, f * fh), (W + 0.08, D + 0.08, 0.07), trim, 0.01)
    # çatı
    rng = random.Random(30 + kind)
    flat_roof(W, D, H, rng, trim, solar=2 if kind == 0 else 1, ac=2, dish=True, hatch=True, vents=3, tank=True)
    box((1.0, 1.6, H + 0.45), (0.9, 0.6, 0.45), concrete("#a8a49c", "front", 0.4), 0.02)  # merdiven kulübesi
    box((1.0, 1.6, H + 0.93), (1.0, 0.7, 0.04), concrete("#8a8680", "top", 0.2), 0.01)
    # giriş: cam kapı + kanopi + numara
    door(0, 0.0, 1.5, 2.3, -D, solid("door_al", "#6e7478", 0.3, 0.8), frame, solid("brass", "#c8a050", 0.3, 1.0),
         concrete("#9a968e"), glass_mat=gl)
    box((0, -D - 0.6, 2.62), (1.3, 0.6, 0.06), solid("canopy", "#2e3236", 0.4, 0.6), 0.01)
    sphere((0, -D - 0.4, 2.52), 0.08, solid("lamp", "#ffe2a8", 0.3, emit=8.0))
    text(str(12 + kind * 7), (1.05, -D - 0.04, 2.35), 0.32, solid("num", "#f4f0e6", 0.4))
    # zemin kat dükkân camları
    for sx in (-1, 1):
        window(sx * 3.2, 1.3, 2.2, 1.6, -D, solid("alu", "#4a4e52", 0.35, 0.8), glass(0.55, "#3a4a58"), None, True)
    # üst katlar: pencereler + balkonlar
    cols = 4
    for f in range(1, floors):
        z = f * fh + 1.35
        for c in range(cols):
            x = -W + (c + 0.5) * (2 * W / cols)
            balcony = (c in (1, 2)) and kind != 1
            if balcony:
                # balkon kapısı + döşeme + korkuluk
                window(x, z - 0.25, 1.1, 2.0, -D, frame, gl, None, True)
                box((x, -D - 0.55, f * fh + 0.08), (1.0, 0.55, 0.08), trim, 0.01)
                railing(x - 0.95, x + 0.95, -D - 1.05, f * fh + 0.16, 0.85, rail)
                for s in (-1, 1):
                    railing(-D - 0.08, -D - 1.05, x + s * 0.95, f * fh + 0.16, 0.85, rail, 6, axis="y")
                if (f + c) % 2 == 0:
                    plants_box(x - 0.5, -D - 0.95, f * fh + 0.25, 0.3)
            else:
                window(x, z, 1.05, 1.35, -D, frame, gl, trim, True)
                if (f + c + kind) % 3 == 0:
                    ac_unit((x + 0.75, -D, z - 0.75), "front")
        for c in range(3):
            y = -D + (c + 0.5) * (2 * D / 3)
            window(y, z, 0.95, 1.3, W, frame, gl, trim, True, side=True)
    # iniş boruları
    metal = solid("gutter", "#7a7e80", 0.35, 0.8)
    for sx in (-1, 1):
        downpipe(sx * (W - 0.12), -D - 0.06, 0.1, H + 0.5, metal)
    # yanda yangın merdiveni (kind 1)
    if kind == 1:
        for f in range(1, floors):
            box((W + 0.5, 0.0, f * fh + 0.05), (0.5, 1.4, 0.04), rail)
            railing(-1.4, 1.4, W + 1.0, f * fh + 0.1, 0.9, rail, 10, axis="y")
    return {"half": (W, D)}


def shop(kind=0):
    W, D, H = 3.8, 3.0, 3.2
    names = ["MARKET", "ECZANE", "BAKKAL"]
    sign_col = ["#e8b830", "#2e9a4a", "#c8382e"][kind % 3]
    wall = [plaster("#e6e0d2", "front"), brick("#9a5038", "front"), plaster("#d6dee0", "front")][kind % 3]
    walls = [plaster("#e6e0d2", "side"), brick("#9a5038", "side"), plaster("#d6dee0", "side")][kind % 3]
    alu = solid("alu", "#3e4246", 0.3, 0.85)
    box((0, 0, H / 2), (W, D - 0.02, H / 2), wall)
    box((0, 0, H / 2), (W - 0.02, D, H / 2), walls)
    flat_roof(W, D, H, random.Random(50 + kind), concrete("#a8a49c", "front", 0.3), solar=0, ac=2, dish=kind == 0, hatch=True, vents=2)
    # vitrin: içeride ışıklı raflar, önde yarı saydam cam
    box((-0.9, -D + 0.12, 1.25), (2.25, 0.02, 0.92), interior(kind))
    window(-0.9, 1.25, 4.6, 1.9, -D, alu, shop_glass(), None, True, depth=0.05)
    door(2.6, 0.0, 1.0, 2.2, -D, alu, alu, solid("chrome", "#d0d0d0", 0.2, 1.0), concrete("#9a968e"), glass_mat=interior(kind + 3))
    # tabela (ışıklı) + yazı
    box((0, -D - 0.12, 2.75), (W - 0.1, 0.1, 0.32), solid("signb", sign_col, 0.4, emit=0.4), 0.02)
    text(names[kind % 3], (0, -D - 0.23, 2.75), 0.42, solid("signt", "#ffffff", 0.3, emit=3.0), 0.02)
    # çizgili tente
    v = [(-W, -D, 2.35), (W, -D, 2.35), (W, -D - 1.0, 1.95), (-W, -D - 1.0, 1.95)]
    mesh_from(v, [(0, 1, 2, 3)], stripes(sign_col, "#f4f0e6", 3.0, "X"), "awning").modifiers.new("s", "SOLIDIFY").thickness = 0.03
    # kapı önü: kasalar, tabela
    crate_m = wood("#9a7046")
    for i in range(2):
        box((-2.6 + i * 0.7, -D - 0.6, 0.22), (0.3, 0.22, 0.22), crate_m, 0.01)
        for j in range(5):
            sphere((-2.75 + i * 0.7 + j * 0.08, -D - 0.6, 0.47), 0.06, solid("fruit%d" % i, ["#d83a2e", "#f2a030"][i], 0.4))
    ac_unit((W, 0.5, 2.2), "side")
    downpipe(-W + 0.1, -D - 0.06, 0.1, H, solid("gutter", "#7a7e80", 0.35, 0.8))
    return {"half": (W, D)}


def police_station():
    W, D, H = 3.8, 3.0, 3.4
    wall = plaster("#e4e6ea", "front", 0.3)
    walls = plaster("#e4e6ea", "side", 0.3)
    blue = solid("pblue", "#1e4e9a", 0.4)
    alu = solid("alu", "#3e4246", 0.3, 0.85)
    gl = glass(0.6, "#2a3a50")
    box((0, 0, H / 2), (W, D - 0.02, H / 2), wall)
    box((0, 0, H / 2), (W - 0.02, D, H / 2), walls)
    box((0, -D - 0.005, 0.45), (W + 0.005, 0.02, 0.1), blue)
    box((W + 0.005, 0, 0.45), (0.02, D, 0.1), blue)
    flat_roof(W, D, H, random.Random(77), concrete("#c4c6ca", "front", 0.2), solar=1, ac=1, dish=False, hatch=True, vents=2)
    for x in (-2.5, -0.9):
        window(x, 1.75, 1.2, 1.3, -D, alu, gl, concrete("#bdbab4"), True)
    for y in (-1.5, 0.6, 2.0):
        window(y, 1.75, 1.0, 1.3, W, alu, gl, concrete("#bdbab4"), True, side=True)
    door(1.8, 0.0, 1.4, 2.3, -D, alu, alu, solid("chrome", "#d0d0d0", 0.2, 1.0), concrete("#9a968e"), glass_mat=gl)
    box((1.8, -D - 0.6, 2.62), (1.0, 0.6, 0.05), blue, 0.01)
    box((0, -D - 0.1, 3.0), (2.4, 0.08, 0.3), blue, 0.02)
    text("POLİS", (0, -D - 0.2, 3.0), 0.42, solid("pt", "#ffffff", 0.3, emit=3.5))
    # çatıda ışık bar ve anten
    box((0, -D + 0.5, H + 0.7), (0.7, 0.18, 0.12), solid("lb", "#2e3236", 0.4, 0.6), 0.03)
    sphere((-0.4, -D + 0.5, H + 0.85), 0.14, solid("lr", "#ff3030", 0.2, emit=8.0))
    sphere((0.4, -D + 0.5, H + 0.85), 0.14, solid("lbl", "#3060ff", 0.2, emit=8.0))
    cyl((-3.0, 2.0, H + 0.3), (-3.0, 2.0, H + 2.8), 0.03, alu, 8)
    # bayrak direği
    cyl((-3.4, -D - 0.8, 0), (-3.4, -D - 0.8, 4.6), 0.04, solid("chrome", "#d0d0d0", 0.2, 1.0), 12)
    box((-3.0, -D - 0.8, 4.2), (0.4, 0.01, 0.26), solid("flag", "#e30a17", 0.6))
    sphere((-3.05, -D - 0.81, 4.2), 0.1, solid("flagw", "#ffffff", 0.6))
    # bariyerler
    for i in range(2):
        box((-2.0 + i * 1.6, -D - 1.1, 0.4), (0.6, 0.05, 0.08), stripes("#e8e8e8", "#c82020", 8.0, "X"), 0.01)
        for s in (-1, 1):
            cyl((-2.0 + i * 1.6 + s * 0.5, -D - 1.1, 0), (-2.0 + i * 1.6 + s * 0.5, -D - 1.1, 0.4), 0.025, alu, 8)
    return {"half": (W, D)}


def gas_station():
    canopy = solid("canopy", "#f4f4f2", 0.4)
    red = solid("gred", "#c8281e", 0.35, coat=0.6)
    alu = solid("alu", "#9a9ea2", 0.3, 0.85)
    box((0, 0, 3.25), (3.6, 2.6, 0.25), canopy, 0.03)
    box((0, 0, 3.505), (3.45, 2.45, 0.01), concrete("#d8d6d0", "top", 0.15, panels=1.2))
    for sx in (-1, 1):
        box((sx * 3.5, 0, 3.55), (0.1, 2.6, 0.05), red)
    for sy in (-1, 1):
        box((0, sy * 2.5, 3.55), (3.6, 0.1, 0.05), red)
    cyl((0, 0.2, 3.5), (0, 0.2, 3.53), 0.9, red, 40)
    text("Z", (0, 0.2, 3.54), 1.2, solid("logo", "#ffffff", 0.3), 0.01).rotation_euler = (0, 0, 0)
    box((0, -2.6 - 0.005, 3.25), (3.6, 0.02, 0.12), red)
    box((3.6 + 0.005, 0, 3.25), (0.02, 2.6, 0.12), red)
    # alttan ışıklar
    for x in (-2.4, 0, 2.4):
        for y in (-1.3, 1.3):
            box((x, y, 2.995), (0.4, 0.15, 0.01), solid("clight", "#fff4dc", 0.3, emit=10.0))
    for x in (-3.2, 3.2):
        for y in (-2.2, 2.2):
            box((x, y, 1.5), (0.14, 0.14, 1.5), red, 0.02)
    # ada + pompalar
    for x in (-1.3, 1.3):
        box((x, 0, 0.08), (0.5, 1.4, 0.08), concrete("#a8a49c", "top"), 0.02)
        box((x, 0, 0.85), (0.32, 0.22, 0.7), red, 0.03)
        box((x, -0.225, 1.1), (0.2, 0.01, 0.14), solid("disp", "#203040", 0.2, emit=0.6))
        text("88.40", (x, -0.24, 1.12), 0.1, solid("dg", "#40ff80", 0.3, emit=4.0), 0.005)
        box((x, 0, 1.6), (0.3, 0.2, 0.08), solid("ptop", "#f4f4f2", 0.4), 0.02)
        for s in (-1, 1):
            cyl((x + s * 0.34, -0.1, 0.9), (x + s * 0.36, -0.1, 0.45), 0.025, solid("hose", "#1a1a1a", 0.6), 8)
            box((x + s * 0.36, -0.1, 0.42), (0.04, 0.06, 0.06), alu, 0.01)
    # tabela direği
    cyl((3.0, -2.6, 0), (3.0, -2.6, 3.0), 0.08, alu, 12)
    box((0, -2.62, 3.7), (2.2, 0.08, 0.3), red, 0.03)
    text("BENZİN", (0, -2.71, 3.7), 0.4, solid("gt", "#ffffff", 0.3, emit=3.0))
    # yağ kutuları / yangın tüpü
    cyl((-2.4, 1.4, 0), (-2.4, 1.4, 0.55), 0.12, red, 16)
    cyl((-2.4, 1.4, 0.55), (-2.4, 1.4, 0.65), 0.05, solid("blk", "#1a1a1a", 0.5), 10)
    return {"half": (3.6, 2.6)}


def wall_segment(length=4.0, axis="x"):
    L = length / 2
    con = concrete("#9a968e", "front" if axis == "x" else "side", 0.5, panels=1.0)
    cap = concrete("#7e7a74", "front", 0.3)
    wire = solid("wire", "#6a6e70", 0.35, 0.9)
    if axis == "x":
        box((0, 0, 1.1), (L, 0.25, 1.1), con)
        box((0, 0, 2.25), (L + 0.03, 0.3, 0.06), cap, 0.01)
        for x in (-L + 0.2, L - 0.2):
            box((x, -0.3, 1.1), (0.15, 0.08, 1.1), cap, 0.01)
        # dikenli tel bobini
        for i in range(int(length / 0.18)):
            t = -L + i * 0.18
            bpy.ops.mesh.primitive_torus_add(major_radius=0.17, minor_radius=0.008, location=(t, 0, 2.48),
                                             rotation=(0, math.pi / 2 + 0.25, 0), major_segments=16, minor_segments=4)
            bpy.context.active_object.data.materials.append(wire)
        for x in (-L + 0.4, 0, L - 0.4):
            cyl((x, 0, 2.3), (x, 0, 2.7), 0.02, wire, 6)
    else:
        box((0, 0, 1.1), (0.25, L, 1.1), con)
        box((0, 0, 2.25), (0.3, L + 0.03, 0.06), cap, 0.01)
        for i in range(int(length / 0.18)):
            t = -L + i * 0.18
            bpy.ops.mesh.primitive_torus_add(major_radius=0.17, minor_radius=0.008, location=(0, t, 2.48),
                                             rotation=(math.pi / 2 + 0.25, 0, 0), major_segments=16, minor_segments=4)
            bpy.context.active_object.data.materials.append(wire)
    return {}


def gate_barrier():
    con = concrete("#8e8a84", "front", 0.4)
    yel = stripes("#f2c418", "#1a1a1a", 4.0, "X")
    for s in (-1, 1):
        box((s * 2.2, 0, 0.6), (0.3, 0.3, 0.6), con, 0.03)
    box((-1.75, 0, 0.62), (0.18, 0.18, 0.62), solid("post", "#d8d4cc", 0.4), 0.02)
    box((0.1, 0, 1.05), (2.0, 0.05, 0.06), stripes("#f4f4f2", "#d02020", 2.2, "X"), 0.02)
    sphere((-1.75, 0, 1.3), 0.08, solid("glamp", "#ff4020", 0.3, emit=6.0))
    # kum torbaları
    bag = solid("sandbag", "#a89a72", 0.9)
    for i in range(5):
        for j in range(2):
            sphere((-1.2 + i * 0.6 + j * 0.3, 0.75, 0.15 + j * 0.25), 0.28, bag).scale = (1.0, 0.6, 0.45)
    return {}


def watchtower():
    wd = wood("#7a5a3a", "front")
    wds = wood("#6a4a2e", "side")
    metal = solid("tmetal", "#4a4e50", 0.45, 0.7)
    H = 4.6
    for sx in (-1, 1):
        for sy in (-1, 1):
            cyl((sx * 1.05, sy * 1.05, 0), (sx * 0.85, sy * 0.85, H), 0.09, wd, 10)
    for z in (1.2, 2.6, 3.8):
        for s in (-1, 1):
            cyl((-1.0, s * 1.0, z - 0.6), (1.0, s * 1.0, z + 0.6), 0.04, wds, 8)
            cyl((s * 1.0, -1.0, z - 0.6), (s * 1.0, 1.0, z + 0.6), 0.04, wds, 8)
    box((0, 0, H), (1.25, 1.25, 0.08), wd, 0.01)
    # siper kum torbaları + korkuluk
    bag = solid("sandbag", "#a89a72", 0.9)
    for i in range(6):
        sphere((-1.0 + i * 0.4, -1.15, H + 0.22), 0.24, bag).scale = (1.0, 0.6, 0.5)
    for s in (-1, 1):
        railing(-1.2, 1.2, s * 1.2, H + 0.08, 0.9, wds, 8, axis="y")
    railing(-1.2, 1.2, 1.2, H + 0.08, 0.9, wds, 8)
    for sx in (-1, 1):
        for sy in (-1, 1):
            cyl((sx * 1.15, sy * 1.15, H), (sx * 1.15, sy * 1.15, H + 2.0), 0.05, wd, 8)
    v = [(-1.5, -1.5, H + 2.0), (1.5, -1.5, H + 2.0), (1.5, 1.5, H + 2.0), (-1.5, 1.5, H + 2.0), (0, 0, H + 2.6)]
    mesh_from(v, [(0, 1, 4), (1, 2, 4), (2, 3, 4), (3, 0, 4)], solid("troof", "#4a5a3a", 0.7, 0.3)).modifiers.new("s", "SOLIDIFY").thickness = 0.05
    # projektör
    cyl((0.8, -1.2, H + 1.0), (0.8, -1.45, H + 0.95), 0.12, metal, 16)
    sphere((0.8, -1.46, H + 0.95), 0.1, solid("spot", "#fff8e0", 0.2, emit=12.0)).scale = (1, 0.3, 1)
    # merdiven
    for s in (-0.25, 0.25):
        cyl((s, -1.35, 0), (s, -1.2, H), 0.03, metal, 8)
    for k in range(14):
        z = 0.3 + k * 0.31
        cyl((-0.25, -1.35 + z / H * 0.15, z), (0.25, -1.35 + z / H * 0.15, z), 0.018, metal, 6)
    return {}


def streetlamp():
    metal = solid("lmetal", "#3a3e42", 0.35, 0.8)
    cyl((0, 0, 0), (0, 0, 0.3), 0.14, metal, 16)
    cyl((0, 0, 0.3), (0, 0, 4.6), 0.07, metal, 16)
    cyl((0, 0, 4.6), (0, -0.9, 4.75), 0.045, metal, 12)
    box((0, -1.0, 4.7), (0.18, 0.28, 0.07), metal, 0.03)
    box((0, -1.0, 4.62), (0.14, 0.22, 0.015), solid("lglow", "#fff0c8", 0.2, emit=14.0))
    return {}


def stall():
    wd = wood("#8a6440")
    for sx in (-1, 1):
        for sy in (-1, 1):
            cyl((sx * 1.3, sy * 0.7, 0), (sx * 1.3, sy * 0.7, 2.2 if sy < 0 else 2.5), 0.05, wd, 8)
    box((0, -0.3, 0.9), (1.35, 0.5, 0.06), wd, 0.01)
    box((0, -0.3, 0.45), (1.3, 0.45, 0.42), wood("#6e4e30"), 0.01)
    v = [(-1.5, -1.0, 2.2), (1.5, -1.0, 2.2), (1.5, 0.9, 2.55), (-1.5, 0.9, 2.55)]
    mesh_from(v, [(0, 1, 2, 3)], stripes("#2e7ac8", "#f4f0e6", 3.0, "X")).modifiers.new("s", "SOLIDIFY").thickness = 0.03
    rng = random.Random(5)
    for i in range(14):
        col = rng.choice(["#d83a2e", "#f2a030", "#e8d040", "#4a9a3a"])
        sphere((-1.1 + i * 0.16, -0.45 + rng.uniform(-0.1, 0.1), 1.0), 0.07, solid("f" + col, col, 0.4))
    box((0.8, -0.2, 1.1), (0.25, 0.18, 0.15), wood("#9a7046"), 0.01)
    return {}


BUILDERS = {
    "house0": lambda: house(0), "house1": lambda: house(1), "house2": lambda: house(2),
    "apartment0": lambda: apartment(0), "apartment1": lambda: apartment(1),
    "shop0": lambda: shop(0), "shop1": lambda: shop(2), "police": police_station, "gas": gas_station,
    "wall_x": lambda: wall_segment(4.0, "x"), "wall_z": lambda: wall_segment(4.0, "z"), "gate": gate_barrier,
    "tower": watchtower, "lamp": streetlamp, "stall": stall,
}
