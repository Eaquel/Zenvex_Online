"""Gerçekçi karakterler: MakeHuman/MPFB2 (CC0 varlıklar) + bizim prosedürel animasyon iskeletimiz.

Kurulum (bir kez): tools/blender/setup_mpfb.sh  → /home/user/mh altında eklenti + CC0 varlık paketi.
Karakter bir kez kurulur; her karede sadece hedef noktalar (empty) taşınır, kemikler DAMPED_TRACK ile
bizim eklemlerimize yönelir. Silah gerçek model olarak sağ ele bağlanır (ayrı kol katmanına gerek yok).
"""
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

MH = os.environ.get("MH_ROOT", "/home/user/mh")
sys.path.insert(0, os.path.join(MH, "pkgs"))
D = os.path.join(MH, "sysassets")

import blib as B  # noqa: E402
from blib import g2b  # noqa: E402

_enabled = False


def enable():
    global _enabled
    if _enabled:
        return
    import addon_utils
    addon_utils.enable("mpfb", default_set=True)
    _enabled = True


# =========================================================================== görünümler
# top_tint: açık/düşük doygunluklu bölgeleri (tişört) boyar; hsv: tüm giysiye ton/doygunluk/parlaklık
LOOKS = {
    "kaan": dict(gender=1.0, muscle=0.8, weight=0.55, height=0.6, age=0.4, skin="young_caucasian_male", hair="short04",
                 suit="male_casualsuit06", shoes="shoes05", top_tint="#c8500c", wraps=True, brows="eyebrow001"),
    "nova": dict(gender=0.0, muscle=0.55, weight=0.45, height=0.55, age=0.35, skin="young_caucasian_female", hair="bob02",
                 suit="female_sportsuit01", shoes="shoes03", hsv=(0.5, 0.15, 0.35), neon="#3fe0ff", goggles=True, brows="eyebrow010"),
    "rady": dict(gender=1.0, muscle=0.6, weight=0.6, height=0.65, age=0.6, skin="middleage_asian_male", hair="short01",
                 suit="male_casualsuit05", shoes="shoes03", tint_all="#7a5ad8", coat="#3a2f7a",
                 trim="#e8c45a", rune="#7ff0ff", brows="eyebrow006"),
    "elif": dict(gender=0.0, muscle=0.5, weight=0.45, height=0.5, age=0.35, skin="young_caucasian_female2", hair="ponytail01",
                 suit="female_casualsuit01", shoes="shoes05", hsv=(None, 0.08, 1.9), medic="#d8343c", brows="eyebrow010"),
    "soldier": dict(gender=1.0, muscle=0.75, weight=0.6, height=0.6, age=0.5, skin="young_african_male", hair="short01",
                    suit="male_worksuit01", shoes="shoes03", hsv=(0.1, 0.8, 0.32), helmet="#4d5a36", vest="#3c4430", brows="eyebrow003"),
    "merchant": dict(gender=1.0, muscle=0.4, weight=0.75, height=0.5, age=0.7, skin="middleage_caucasian_male", hair="short03",
                     suit="male_casualsuit03", shoes="shoes01", apron="#7a4e30", cap="#a03028", brows="eyebrow002"),
    # zombiler
    "walker": dict(gender=1.0, muscle=0.4, weight=0.5, height=0.55, age=0.5, skin="middleage_caucasian_male", hair="short02",
                   suit="male_casualsuit02", shoes="shoes02", zombie=(0.7, 0.5, 0.72), dirt=0.7, eye="#ffe066", brows="eyebrow004"),
    "runner": dict(gender=0.0, muscle=0.5, weight=0.35, height=0.55, age=0.3, skin="young_asian_female", hair="long01",
                   suit="female_casualsuit02", shoes="shoes06", zombie=(0.5, 0.15, 0.95), dirt=0.6, eye="#ff3a2a", brows="eyebrow010"),
    "dark": dict(gender=1.0, muscle=0.5, weight=0.4, height=0.65, age=0.5, skin="middleage_african_male", hair=None,
                 suit="male_casualsuit05", shoes="shoes03", hsv=(0.75, 0.8, 0.45), zombie=(0.22, 0.35, 1.25), dirt=0.3, eye="#c45cff"),
    "toxic": dict(gender=1.0, muscle=0.5, weight=0.7, height=0.55, age=0.5, skin="middleage_caucasian_male", hair=None,
                  suit="male_worksuit01", shoes="shoes03", hsv=(0.14, 1.4, 1.1), zombie=(0.72, 0.8, 0.8), dirt=0.5, eye="#c6ff3a",
                  gasmask=True, pustules=True),
    "tank": dict(gender=1.0, muscle=1.0, weight=1.0, height=0.8, age=0.5, skin="old_caucasian_male", hair=None,
                 suit="male_worksuit01", shoes="shoes03", hsv=(0.08, 0.6, 0.5), zombie=(0.68, 0.45, 0.68), dirt=0.8, eye="#ff3a2a", chains=True),
    "mystery": dict(gender=1.0, muscle=0.5, weight=0.4, height=0.75, age=0.5, skin="young_caucasian_male",
                    suit="male_elegantsuit01", shoes="shoes04", zombie=(0.5, 0.05, 0.95), mask=True, eye="#ff3a2a", hair="short04"),
    "titan": dict(gender=1.0, muscle=1.0, weight=0.9, height=1.0, age=0.5, skin="old_african_male", hair=None,
                  suit="male_worksuit01", shoes="shoes03", hsv=(0.05, 0.4, 0.35), zombie=(0.3, 0.2, 0.5), dirt=0.6, eye="#ff3a2a",
                  armor=True, horns=True),
}
SCALE = {"tank": 1.22, "titan": 2.3, "mystery": 1.04}


def _hex(h):
    return B.hexrgb(h)


def _insert_color_fx(mat, hsv=None, tint=None, zombie=None, dirt=0.0, emission=None, tint_all=None):
    """Malzemenin Base Color girdisine ton/doygunluk, tişört boyama, kir/kan lekesi ekler."""
    if mat is None or not mat.use_nodes:
        return
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
    if bsdf is None:
        return
    inp = bsdf.inputs["Base Color"]
    if not inp.links:
        return
    src = inp.links[0].from_socket
    L = nt.links
    cur = src
    if hsv is not None or zombie is not None:
        h = nt.nodes.new("ShaderNodeHueSaturation")
        if zombie is not None:
            h.inputs["Hue"].default_value = zombie[0] if zombie[0] is not None else 0.5
            h.inputs["Saturation"].default_value = zombie[1]
            h.inputs["Value"].default_value = zombie[2]
        else:
            h.inputs["Hue"].default_value = hsv[0] if hsv[0] is not None else 0.5
            h.inputs["Saturation"].default_value = hsv[1]
            h.inputs["Value"].default_value = hsv[2]
        L.new(cur, h.inputs["Color"])
        cur = h.outputs["Color"]
    if tint is not None:
        # açık ve doygunluğu düşük bölgeler → tint (beyaz tişört boyanır, kot korunur)
        sep = nt.nodes.new("ShaderNodeSeparateColor")
        sep.mode = "HSV"
        L.new(cur, sep.inputs[0])
        lowsat = nt.nodes.new("ShaderNodeMapRange")
        L.new(sep.outputs[1], lowsat.inputs["Value"])
        lowsat.inputs["From Min"].default_value = 0.12
        lowsat.inputs["From Max"].default_value = 0.3
        lowsat.inputs["To Min"].default_value = 1.0
        lowsat.inputs["To Max"].default_value = 0.0
        bright = nt.nodes.new("ShaderNodeMapRange")
        L.new(sep.outputs[2], bright.inputs["Value"])
        bright.inputs["From Min"].default_value = 0.45
        bright.inputs["From Max"].default_value = 0.7
        mask = nt.nodes.new("ShaderNodeMath")
        mask.operation = "MULTIPLY"
        mask.use_clamp = True
        L.new(lowsat.outputs[0], mask.inputs[0])
        L.new(bright.outputs[0], mask.inputs[1])
        lowsat.inputs["From Min"].default_value = 0.25
        lowsat.inputs["From Max"].default_value = 0.45
        bright.inputs["From Min"].default_value = 0.35
        bright.inputs["From Max"].default_value = 0.5
        # boyanmış renk = tint × parlaklık (kumaş gölgeleri korunur)
        val = nt.nodes.new("ShaderNodeMath")
        val.operation = "MULTIPLY"
        L.new(sep.outputs[2], val.inputs[0])
        val.inputs[1].default_value = 1.25
        comb = nt.nodes.new("ShaderNodeCombineColor")
        for i in range(3):
            L.new(val.outputs[0], comb.inputs[i])
        mul = nt.nodes.new("ShaderNodeMix")
        mul.data_type = "RGBA"
        mul.blend_type = "MULTIPLY"
        mul.inputs["Factor"].default_value = 1.0
        L.new(comb.outputs[0], mul.inputs[6])
        mul.inputs[7].default_value = (*_hex(tint), 1)
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        L.new(mask.outputs[0], mix.inputs["Factor"])
        L.new(cur, mix.inputs[6])
        L.new(mul.outputs[2], mix.inputs[7])
        cur = mix.outputs[2]
    if tint_all is not None:
        ta = nt.nodes.new("ShaderNodeMix")
        ta.data_type = "RGBA"
        ta.blend_type = "MULTIPLY"
        ta.inputs["Factor"].default_value = 1.0
        L.new(cur, ta.inputs[6])
        ta.inputs[7].default_value = (*_hex(tint_all), 1)
        bri = nt.nodes.new("ShaderNodeBrightContrast")
        bri.inputs["Bright"].default_value = 0.08
        L.new(ta.outputs[2], bri.inputs["Color"])
        cur = bri.outputs["Color"]
    if dirt > 0:
        tc = nt.nodes.new("ShaderNodeTexCoord")
        nz = nt.nodes.new("ShaderNodeTexNoise")
        nz.inputs["Scale"].default_value = 9.0
        nz.inputs["Detail"].default_value = 8
        L.new(tc.outputs["Object"], nz.inputs["Vector"])
        mr = nt.nodes.new("ShaderNodeMapRange")
        L.new(nz.outputs["Fac"], mr.inputs["Value"])
        mr.inputs["From Min"].default_value = 0.52
        mr.inputs["From Max"].default_value = 0.62
        mr.inputs["To Max"].default_value = 0.85 * dirt
        blood = nt.nodes.new("ShaderNodeMix")
        blood.data_type = "RGBA"
        L.new(mr.outputs[0], blood.inputs["Factor"])
        L.new(cur, blood.inputs[6])
        blood.inputs[7].default_value = (0.18, 0.015, 0.012, 1)
        # genel kir
        nz2 = nt.nodes.new("ShaderNodeTexNoise")
        nz2.inputs["Scale"].default_value = 3.0
        L.new(tc.outputs["Object"], nz2.inputs["Vector"])
        dm = nt.nodes.new("ShaderNodeMix")
        dm.data_type = "RGBA"
        dm.blend_type = "MULTIPLY"
        L.new(nz2.outputs["Fac"], dm.inputs["Factor"])
        L.new(blood.outputs[2], dm.inputs[6])
        dm.inputs[7].default_value = (0.35, 0.3, 0.22, 1)
        cur = dm.outputs[2]
    L.new(cur, inp)
    if emission is not None:
        bsdf.inputs["Emission Color"].default_value = (*_hex(emission), 1)
        bsdf.inputs["Emission Strength"].default_value = 6.0


# =========================================================================== aksesuarlar (kemiğe bağlı)
def _bone_child(ob, rig, bone):
    """Nesneyi kemiğe bağlar; nesne dünya konumunda kalır."""
    mw = ob.matrix_world.copy()
    ob.parent = rig
    ob.parent_type = "BONE"
    ob.parent_bone = bone
    bpy.context.view_layer.update()
    ob.matrix_world = mw


def _prim(kind, mat, loc, size, rot=(0, 0, 0), bevel=0.0, **kw):
    if kind == "box":
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
        o = bpy.context.active_object
        o.scale = size
    elif kind == "sphere":
        bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, radius=1, location=loc, rotation=rot)
        o = bpy.context.active_object
        o.scale = size
    elif kind == "cyl":
        bpy.ops.mesh.primitive_cylinder_add(vertices=20, radius=size[0], depth=size[1], location=loc, rotation=rot)
        o = bpy.context.active_object
    elif kind == "cone":
        bpy.ops.mesh.primitive_cone_add(vertices=20, radius1=size[0], radius2=size[1], depth=size[2], location=loc, rotation=rot)
        o = bpy.context.active_object
    elif kind == "torus":
        bpy.ops.mesh.primitive_torus_add(major_radius=size[0], minor_radius=size[1], location=loc, rotation=rot, major_segments=28, minor_segments=8)
        o = bpy.context.active_object
    if bevel:
        m = o.modifiers.new("b", "BEVEL")
        m.width = bevel
        m.segments = 2
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


def _bw(rig, bone, at="head"):
    pb = rig.pose.bones[bone]
    return rig.matrix_world @ (pb.head if at == "head" else pb.tail)


def _accessories(rig, L):
    m = lambda name, col, rough=0.6, metal=0.0, emit=0.0, kind=None: B.material(name, col, rough, metal=metal, emit=emit, kind=kind)
    head = _bw(rig, "head")
    htop = _bw(rig, "head", "tail")
    hc = (head + htop) / 2
    neck = _bw(rig, "neck_01")
    s3 = _bw(rig, "spine_03")
    s3t = _bw(rig, "spine_03", "tail")
    pelvis = _bw(rig, "pelvis")
    out = []
    if L.get("wraps"):
        for side in ("l", "r"):
            h = _bw(rig, "hand_" + side)
            o = _prim("torus", m("wrap", "#f2f2ea", 0.9, kind="cloth"), h, (0.034, 0.014), rot=(math.pi / 2, 0, math.pi / 2))
            _bone_child(o, rig, "hand_" + side)
    if L.get("goggles"):
        o = _prim("box", m("gog", "#2a2d36", 0.3, 0.8), hc + Vector((0, -0.075, 0.035)), (0.17, 0.03, 0.045), bevel=0.008)
        _bone_child(o, rig, "head")
        for sx in (-1, 1):
            o = _prim("sphere", m("lens", "#5ef0ff", 0.05, emit=3.0), hc + Vector((sx * 0.04, -0.093, 0.04)), (0.024, 0.012, 0.022))
            _bone_child(o, rig, "head")
    if L.get("neon"):
        nm = m("neon", L["neon"], 0.3, emit=6.0)
        for side in ("l", "r"):
            a, b = _bw(rig, "upperarm_" + side), _bw(rig, "upperarm_" + side, "tail")
            d = b - a
            o = _prim("cyl", nm, (a + b) / 2 + Vector((0, -0.045, 0)), (0.007, d.length * 0.85))
            o.rotation_mode = "QUATERNION"
            o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
            _bone_child(o, rig, "upperarm_" + side)
    if L.get("coat"):
        cm = m("coat", L["coat"], 0.85, kind="cloth")
        o = _prim("cone", cm, pelvis + Vector((0, 0.03, -0.3)), (0.26, 0.19, 0.66))
        o.scale = (1.0, 0.8, 1.0)
        o.modifiers.new("sol", "SOLIDIFY").thickness = 0.012
        cut = _prim("box", cm, pelvis + Vector((0, -0.25, -0.3)), (0.16, 0.25, 0.8))
        bo = o.modifiers.new("open", "BOOLEAN")
        bo.object = cut
        bo.operation = "DIFFERENCE"
        cut.hide_render = True
        cut.hide_viewport = True
        _bone_child(o, rig, "pelvis")
        _bone_child(cut, rig, "pelvis")
        if L.get("trim"):
            tm = m("trim", L["trim"], 0.25, 1.0)
            o = _prim("box", tm, s3 + Vector((0, -0.152, 0.0)), (0.014, 0.004, 0.3))
            _bone_child(o, rig, "spine_03")
        if L.get("rune"):
            o = _prim("sphere", m("rune", L["rune"], 0.2, emit=7.0), s3 + Vector((0.06, -0.155, 0.08)), (0.022, 0.01, 0.022))
            _bone_child(o, rig, "spine_03")
    if L.get("hood"):
        hm = m("hood", L.get("coat", "#222222"), 0.85, kind="cloth")
        o = _prim("sphere", hm, hc + Vector((0, 0.025, 0.015)), (0.118, 0.135, 0.14))
        o.modifiers.new("sol", "SOLIDIFY").thickness = 0.01
        # yüz açıklığı
        cut = _prim("sphere", hm, hc + Vector((0, -0.12, -0.02)), (0.075, 0.1, 0.1))
        bo = o.modifiers.new("cut", "BOOLEAN")
        bo.object = cut
        bo.operation = "DIFFERENCE"
        cut.hide_render = True
        cut.hide_viewport = True
        _bone_child(o, rig, "head")
        _bone_child(cut, rig, "head")
    if L.get("beard"):
        bm = m("beard", L["beard"], 0.5, kind="hair")
        o = _prim("sphere", bm, head + Vector((0, -0.085, 0.01)), (0.06, 0.04, 0.05))
        _bone_child(o, rig, "head")
    if L.get("medic"):
        rm = m("medic", L["medic"], 0.6, kind="cloth")
        for dy, sgn in ((-0.13, 1), (0.13, -1)):
            for sz in ((0.06, 0.004, 0.016), (0.016, 0.004, 0.06)):
                o = _prim("box", rm, s3 + Vector((0.05 * sgn, dy, 0.07)), sz)
                _bone_child(o, rig, "spine_03")
        a, b = _bw(rig, "upperarm_l"), _bw(rig, "upperarm_l", "tail")
        o = _prim("cyl", rm, a * 0.45 + b * 0.55, (0.05, 0.05))
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference((b - a).normalized())
        _bone_child(o, rig, "upperarm_l")
    if L.get("helmet"):
        o = _prim("sphere", m("helm", L["helmet"], 0.5, kind="metal"), hc + Vector((0, 0.005, 0.03)), (0.125, 0.135, 0.11))
        _bone_child(o, rig, "head")
    if L.get("vest"):
        vm = m("vest", L["vest"], 0.8, kind="cloth")
        o = _prim("box", vm, s3 + Vector((0, 0, -0.04)), (0.36, 0.27, 0.36), bevel=0.05)
        _bone_child(o, rig, "spine_03")
    if L.get("apron"):
        o = _prim("box", m("apron", L["apron"], 0.7, kind="leather"), pelvis + Vector((0, -0.14, 0.05)), (0.3, 0.01, 0.6), bevel=0.01)
        _bone_child(o, rig, "spine_01")
    if L.get("cap"):
        cmm = m("cap", L["cap"], 0.8, kind="cloth")
        o = _prim("sphere", cmm, hc + Vector((0, 0.0, 0.05)), (0.105, 0.115, 0.07))
        _bone_child(o, rig, "head")
        o = _prim("box", cmm, hc + Vector((0, -0.12, 0.04)), (0.15, 0.09, 0.012), rot=(0.15, 0, 0), bevel=0.005)
        _bone_child(o, rig, "head")
    if L.get("gasmask"):
        gm = m("gasm", "#2a2a2a", 0.5, kind="rubber")
        o = _prim("sphere", gm, head + Vector((0, -0.085, 0.03)), (0.075, 0.055, 0.08))
        _bone_child(o, rig, "head")
        o = _prim("cyl", m("gmc", "#6a6a6a", 0.3, 0.8), head + Vector((0, -0.15, 0.0)), (0.035, 0.05), rot=(math.pi / 2, 0, 0))
        _bone_child(o, rig, "head")
        for sx in (-1, 1):
            o = _prim("sphere", m("gml", "#b8ff4a", 0.05, emit=2.5), hc + Vector((sx * 0.037, -0.1, 0.0)), (0.022, 0.01, 0.022))
            _bone_child(o, rig, "head")
    if L.get("mask"):
        o = _prim("sphere", m("mask", "#ece6dc", 0.25), hc + Vector((0, -0.07, -0.02)), (0.085, 0.05, 0.11))
        _bone_child(o, rig, "head")
        for sx in (-1, 1):
            o = _prim("sphere", m("me", L["eye"], 0.2, emit=8.0), hc + Vector((sx * 0.032, -0.115, 0.0)), (0.012, 0.006, 0.01))
            _bone_child(o, rig, "head")
    if L.get("pustules"):
        pm = m("pus", "#c6ff3a", 0.2, emit=2.5)
        for off in ((0.08, -0.13, 0.05), (-0.1, -0.12, -0.05), (0.12, 0.05, 0.08)):
            o = _prim("sphere", pm, s3 + Vector(off), (0.03, 0.03, 0.03))
            _bone_child(o, rig, "spine_03")
    if L.get("chains"):
        cm2 = m("chain", "#9a9ca6", 0.2, 1.0)
        o = _prim("torus", cm2, s3 + Vector((0, 0, -0.05)), (0.2, 0.012), rot=(0.5, 0.3, 0))
        _bone_child(o, rig, "spine_03")
    if L.get("armor"):
        am = m("armor", "#5e6070", 0.28, 1.0, kind="metal")
        for side, sx in (("l", 1), ("r", -1)):
            sh = _bw(rig, "upperarm_" + side)
            o = _prim("sphere", am, sh + Vector((sx * 0.03, 0, 0.05)), (0.11, 0.11, 0.09))
            _bone_child(o, rig, "clavicle_" + side)
            for k in range(3):
                o = _prim("cone", am, sh + Vector((sx * (0.0 + k * 0.045), 0, 0.15)), (0.018, 0.0, 0.1))
                _bone_child(o, rig, "clavicle_" + side)
        o = _prim("box", am, s3 + Vector((0, -0.13, -0.02)), (0.34, 0.05, 0.28), bevel=0.03)
        _bone_child(o, rig, "spine_03")
    if L.get("horns"):
        hm2 = m("horn", "#3a3430", 0.4)
        for sx in (-1, 1):
            o = _prim("cone", hm2, hc + Vector((sx * 0.07, 0, 0.1)), (0.02, 0.0, 0.14), rot=(0, sx * 0.5, 0))
            _bone_child(o, rig, "head")
    return out


# =========================================================================== kurulum
FINGERS = ["index", "middle", "ring", "pinky"]


def build(kind, scale=1.0):
    """Sahneye karakteri kurar; {"rig", "meshes", "targets"} döndürür."""
    enable()
    from mpfb.services.humanservice import HumanService
    from mpfb.services.targetservice import TargetService
    L = LOOKS[kind]
    macro = TargetService.get_default_macro_info_dict()
    for k in ("gender", "muscle", "weight", "height", "age"):
        macro[k] = L[k]
    bm = HumanService.create_human(macro_detail_dict=macro, scale=0.1)
    HumanService.set_character_skin(f"{D}/skins/{L['skin']}/{L['skin']}.mhmat", bm,
                                    skin_type="MAKESKIN" if L.get("zombie") else "ENHANCED_SSS")
    # önce iskelet: sonra eklenen giysi/saç otomatik ağırlık ve armature alır
    rig = HumanService.add_builtin_rig(bm, "game_engine")
    assets = [("Eyes", f"{D}/eyes/high-poly/high-poly.mhclo"), ("Eyelashes", f"{D}/eyelashes/eyelashes01/eyelashes01.mhclo")]
    if L.get("brows"):
        assets.append(("Eyebrows", f"{D}/eyebrows/{L['brows']}/{L['brows']}.mhclo"))
    if L.get("hair") and not L.get("hood"):
        assets.append(("Hair", f"{D}/hair/{L['hair']}/{L['hair']}.mhclo"))
    assets.append(("Clothes", f"{D}/clothes/{L['suit']}/{L['suit']}.mhclo"))
    assets.append(("Clothes", f"{D}/clothes/{L['shoes']}/{L['shoes']}.mhclo"))
    for typ, path in assets:
        if os.path.exists(path):
            try:
                HumanService.add_mhclo_asset(path, bm, asset_type=typ, subdiv_levels=1)
            except Exception as e:  # noqa: BLE001
                print("varlık hatası", typ, e)
        else:
            print("eksik", path)
    meshes = [o for o in bpy.data.objects if o.type == "MESH" and o.name not in ("Ground",)]
    # renkler
    for o in meshes:
        n = o.name.lower()
        for slot in o.material_slots:
            mat = slot.material
            if mat is None:
                continue
            mn = mat.name.lower()
            if L.get("zombie") and o == bm and mn.endswith(".body"):
                _insert_color_fx(mat, zombie=L["zombie"], dirt=L.get("dirt", 0) * 0.6)
            elif "eye" in mn and "lash" not in mn and "brow" not in mn and L.get("eye"):
                _insert_color_fx(mat, emission=L["eye"])
            elif L["suit"].lower() in n or L["suit"].lower() in mn:
                _insert_color_fx(mat, hsv=L.get("hsv"), tint=L.get("top_tint"), dirt=L.get("dirt", 0), tint_all=L.get("tint_all"))
    # bütün ölçek
    rig.scale = (scale, scale, scale)
    bpy.context.view_layer.update()
    _accessories(rig, L)
    # hedefler ve kısıtlar
    targets = {}

    def tgt(name):
        if name not in targets:
            e = bpy.data.objects.new("T_" + name, None)
            bpy.context.scene.collection.objects.link(e)
            targets[name] = e
        return targets[name]

    chain = {
        "pelvis": "chest", "spine_01": "spine_mid", "spine_02": "chest", "spine_03": "neck", "neck_01": "head", "head": "head_top",
        "clavicle_l": "l_shoulder", "upperarm_l": "l_elbow", "lowerarm_l": "l_wrist", "hand_l": "l_hand_tip",
        "clavicle_r": "r_shoulder", "upperarm_r": "r_elbow", "lowerarm_r": "r_wrist", "hand_r": "r_hand_tip",
        "thigh_l": "l_knee", "calf_l": "l_ankle", "foot_l": "l_toe", "thigh_r": "r_knee", "calf_r": "r_ankle", "foot_r": "r_toe",
    }
    for bone, t in chain.items():
        if bone not in rig.pose.bones:
            continue
        c = rig.pose.bones[bone].constraints.new("DAMPED_TRACK")
        c.target = tgt(t)
    # omuz çizgisi → gövde dönüşü
    if "spine_03" in rig.pose.bones:
        c = rig.pose.bones["spine_03"].constraints.new("LOCKED_TRACK")
        c.target = tgt("l_shoulder")
        c.lock_axis = "LOCK_Y"
        c.track_axis = "TRACK_X"
        c.influence = 0.6
    # yumruk: parmakları kıvır
    for side in ("l", "r"):
        for f in FINGERS:
            for k in (1, 2, 3):
                nm = f"{f}_0{k}_{side}"
                if nm in rig.pose.bones:
                    pb = rig.pose.bones[nm]
                    pb.rotation_mode = "XYZ"
                    pb.rotation_euler = (math.radians(70 if k == 1 else 85), 0, 0)
        for k in (1, 2):
            nm = f"thumb_0{k}_{side}"
            if nm in rig.pose.bones:
                pb = rig.pose.bones[nm]
                pb.rotation_mode = "XYZ"
                pb.rotation_euler = (math.radians(30), 0, math.radians(-25 if side == "l" else 25))
    return {"rig": rig, "meshes": meshes, "targets": targets, "basemesh": bm, "scale": scale}


def pose(actor, J, s=1.0):
    """Eklem sözlüğüne (oyun koordinatı) göre hedefleri taşır, kökü yerleştirir."""
    rig, T = actor["rig"], actor["targets"]
    P = lambda p: g2b(np.asarray(p, float) * s)
    pts = {
        "chest": J["chest"], "neck": J["neck"], "head": J["head"], "spine_mid": (J["pelvis"] + J["chest"]) / 2,
        "head_top": J["head"] + J["H"] @ np.array([0, 0.15, 0.0]),
    }
    for pre in ("l", "r"):
        pts[pre + "_shoulder"] = J[pre + "_shoulder"]
        pts[pre + "_elbow"] = J[pre + "_elbow"]
        pts[pre + "_wrist"] = J[pre + "_wrist"]
        pts[pre + "_hand_tip"] = J[pre + "_fist"] + np.asarray(J[pre + "_fdir"]) * 0.06
        pts[pre + "_knee"] = J[pre + "_knee"]
        pts[pre + "_ankle"] = J[pre + "_ankle"]
        pts[pre + "_toe"] = J[pre + "_toe"]
    for k, e in T.items():
        if k in pts:
            e.location = P(pts[k])
    # gövde yönü: oyun ileri (+z) → blender
    f = np.asarray(J["B"], float) @ np.array([0, 0, 1.0])
    fb = g2b(f)
    rig.rotation_euler = (0, 0, math.atan2(fb.x, -fb.y))
    bpy.context.view_layer.update()
    # leğeni eklemine taşı
    pel = rig.matrix_world @ rig.pose.bones["pelvis"].head
    want = P(J["pelvis"])
    rig.location += Vector((want.x - pel.x, want.y - pel.y, want.z - pel.z))
    bpy.context.view_layer.update()
    # en alçak nokta zemine
    low = min((rig.matrix_world @ getattr(rig.pose.bones[b], at)).z for b in
              ("foot_l", "foot_r", "ball_l", "ball_r", "calf_l", "calf_r", "head", "hand_l", "hand_r", "pelvis")
              if b in rig.pose.bones for at in ("head", "tail"))
    rig.location.z += 0.035 * s - low
    bpy.context.view_layer.update()


def attach_points(actor):
    rig = actor["rig"]

    def gp(bone, at="head"):
        v = rig.matrix_world @ getattr(rig.pose.bones[bone], at)
        return [v.x, v.z, -v.y]  # blender → oyun

    hd = rig.pose.bones["head"]
    head_c = rig.matrix_world @ ((hd.head + hd.tail) / 2)
    return {
        "rhand": gp("hand_r"), "lhand": gp("hand_l"), "head": [head_c.x, head_c.z, -head_c.y],
        "back": gp("spine_03"), "chest": gp("spine_02", "tail"), "neck": gp("neck_01"),
        "lfoot": gp("foot_l"), "rfoot": gp("foot_r"),
    }


def attach_weapon(actor, weapon_objs):
    """Silah nesnelerini sağ ele bağla (kabzası avuçta, namlu el yönünde)."""
    rig = actor["rig"]
    for o in weapon_objs:
        o.parent = rig
        o.parent_type = "BONE"
        o.parent_bone = "hand_r"
