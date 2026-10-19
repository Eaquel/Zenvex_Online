"""V4 kahramanı: siyah kapüşonlu Savaşçı (MakeHuman gövdesi + kodla modellenen kapüşon, yırtık pelerin, kayışlar).

Pelerin iskelete ağırlıklı bağlıdır; ikincil hareket (salınım) şekil anahtarlarıyla verilir:
  flow  → etek arkaya ve yukarı savrulur (koşu/takla)
  sway  → etek sağa/sola salınır (yürüme fazına gecikmeli)
"""
import math

import bpy
import numpy as np
from mathutils import Vector

import blib as B
import mh_actor as MA

MA.LOOKS["warrior"] = dict(gender=1.0, muscle=0.75, weight=0.45, height=0.66, age=0.35, skin="young_caucasian_male", hair=None,
                           suit="male_elegantsuit01", shoes="shoes04", tint_all="#3a281e", wraps=True)

PAL = {
    "cloak": "#2a2833", "cloak_in": "#3a2c34", "hood": "#24222c", "leather": "#4a2f22", "belt": "#5c3a24",
    "metal": "#8a8478", "bone": "#d8cfb8", "bandage": "#cfc5ad", "boot": "#4a3426", "shadow": "#050407", "stitch": "#5a3a7a",
}


def _m(name, kind=None, rough=0.85, emit=0.0):
    # piksel hattında doku/kabartma gürültü üretir: düz renk
    return B.material("w_" + name, PAL[name], rough, kind=None, emit=emit)


def recolor(mat, hex_col, keep=0.45):
    """Malzemenin taban rengini verilen renge boyar; dokunun açık-koyu deseni (kıvrım, dikiş) korunur."""
    if mat is None or not mat.use_nodes:
        return
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
    if bsdf is None:
        return
    inp = bsdf.inputs["Base Color"]
    src = inp.links[0].from_socket if inp.is_linked else None
    col = nt.nodes.new("ShaderNodeRGB")
    col.outputs[0].default_value = (*B.hexrgb(hex_col), 1)
    if src is None:
        nt.links.new(col.outputs[0], inp)
        return
    bw = nt.nodes.new("ShaderNodeRGBToBW")
    nt.links.new(src, bw.inputs[0])
    mr = nt.nodes.new("ShaderNodeMapRange")  # parlaklık → 1-keep .. 1+keep
    mr.inputs["To Min"].default_value = 1.0 - keep
    mr.inputs["To Max"].default_value = 1.0 + keep
    nt.links.new(bw.outputs[0], mr.inputs["Value"])
    mul = nt.nodes.new("ShaderNodeMix")
    mul.data_type = "RGBA"
    mul.blend_type = "MULTIPLY"
    mul.inputs["Factor"].default_value = 1.0
    nt.links.new(col.outputs[0], mul.inputs[6])
    nt.links.new(mr.outputs[0], mul.inputs[7])
    nt.links.new(mul.outputs[2], inp)


def _mesh(name, verts, faces, mat):
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(v) for v in verts], [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    me.materials.append(mat)
    for p in me.polygons:
        p.use_smooth = True
    return ob


def _weights(ob, rig, fn):
    """fn(v_world) → {kemik: ağırlık}; armatür değiştiricisi ekler."""
    groups = {}
    for i, v in enumerate(ob.data.vertices):
        for bone, w in fn(ob.matrix_world @ v.co).items():
            if w <= 0 or bone not in rig.pose.bones:
                continue
            if bone not in groups:
                groups[bone] = ob.vertex_groups.new(name=bone)
            groups[bone].add([i], w, "REPLACE")
    mod = ob.modifiers.new("arm", "ARMATURE")
    mod.object = rig
    ob.modifiers.move(len(ob.modifiers) - 1, 0)
    # örgü dünya koordinatında kuruldu: armatürün nesne dönüşümünü telafi ederek ebeveynle
    ob.parent = rig
    ob.matrix_parent_inverse = rig.matrix_world.inverted()


def cloak(rig, actor):
    """Omuzlardan dizlere açık önlü, yırtık etekli pelerin + salınım şekil anahtarları."""
    sh_l, sh_r = MA._bw(rig, "upperarm_l"), MA._bw(rig, "upperarm_r")
    neck = MA._bw(rig, "neck_01")
    knee = (MA._bw(rig, "calf_l") + MA._bw(rig, "calf_r")) / 2
    cx, cy = (sh_l.x + sh_r.x) / 2, neck.y
    top_z = neck.z - 0.03
    bot_z = knee.z - 0.05
    half_w = abs(sh_l.x - sh_r.x) / 2
    N, M = 34, 16
    gap = math.radians(52)  # önde açıklık (yarım açı)
    rng = np.random.default_rng(5)
    hem_noise = rng.uniform(0.0, 0.12, N + 1)
    verts, faces = [], []
    for j in range(M + 1):
        h = j / M
        z = top_z + (bot_z - top_z) * h
        rx = half_w * 0.95 + (0.34 - half_w * 0.95) * h ** 0.8
        ry = 0.13 + 0.17 * h ** 0.8
        for i in range(N + 1):
            # -Y önde: açı 0 = arka (+Y)
            a = -math.pi + gap + (2 * math.pi - 2 * gap) * i / N
            fold = 1.0 + 0.07 * h * math.sin(a * 9.0 + h * 2.0)  # dikey kıvrımlar (aşağı doğru belirginleşir)
            x = cx + math.sin(a) * rx * fold
            y = cy + 0.03 + math.cos(a) * ry * fold
            zz = z - (hem_noise[i] * h ** 6 if j == M else 0.0)
            verts.append((x, y, zz))
    for j in range(M):
        for i in range(N):
            # yırtıklar: etekte rastgele birkaç sütun kısa kalır
            if j >= M - 2 and (i * 7 + j) % 9 == 0:
                continue
            a = j * (N + 1) + i
            faces.append((a, a + 1, a + N + 2, a + N + 1))
    ob = _mesh("Cloak", verts, faces, _m("cloak"))
    ob.data.materials.append(_m("cloak_in"))
    so = ob.modifiers.new("sol", "SOLIDIFY")
    so.thickness = 0.008
    so.material_offset = 1
    # ağırlıklar: üst → spine_03, orta → spine_01, alt → pelvis + uyluk
    zt, zb = top_z, bot_z

    def wfn(p):
        h = min(1.0, max(0.0, (zt - p.z) / (zt - zb)))
        side = "l" if p.x > cx else "r"
        w = {}
        if h < 0.35:
            w["spine_03"] = 1.0 - h / 0.35
            w["spine_02"] = h / 0.35
        elif h < 0.65:
            k = (h - 0.35) / 0.3
            w["spine_02"] = 1.0 - k
            w["spine_01"] = k
        else:
            k = (h - 0.65) / 0.35
            w["spine_01"] = 1.0 - k
            w["pelvis"] = k * 0.75
            w["thigh_" + side] = k * 0.25 * min(1.0, abs(p.x - cx) / 0.15)
        return w

    _weights(ob, rig, wfn)
    # şekil anahtarları (dinlenme pozunda; armatürden önce uygulanır)
    ob.shape_key_add(name="Basis")
    flow = ob.shape_key_add(name="flow")
    sway = ob.shape_key_add(name="sway")
    for i, v in enumerate(ob.data.vertices):
        p = v.co
        h = min(1.0, max(0.0, (zt - p.z) / (zt - zb)))
        k = h ** 1.6
        flow.data[i].co = p + Vector((0, 0.38 * k, 0.16 * k))
        sway.data[i].co = p + Vector((0.13 * k, 0.02 * k, 0.03 * k))
    flow.slider_min, sway.slider_min = -1.0, -1.0
    return ob


def hood(rig):
    """Kapüşon + omuzlara inen pelerinlik tek parça: küre örgüsünün alt yarısı omuzlara doğru açılır,
    arkada sivri uç, önde yüz açıklığı; yüz gölgede (yalnız çene görünür)."""
    head = MA._bw(rig, "head")
    htop = MA._bw(rig, "head", "tail")
    hc = (head + htop) / 2
    sh_l, sh_r = MA._bw(rig, "upperarm_l"), MA._bw(rig, "upperarm_r")
    shz = (sh_l.z + sh_r.z) / 2
    shw = abs(sh_l.x - sh_r.x) / 2 + 0.07
    hm = _m("hood")
    bpy.ops.mesh.primitive_uv_sphere_add(segments=40, ring_count=24, radius=1, location=(0, 0, 0))
    o = bpy.context.active_object
    for v in o.data.vertices:
        x, y, z = v.co
        # baş kısmı
        px, py, pz = x * 0.135, y * 0.15 + 0.02, z * 0.128 + 0.03
        if z > 0.25 and y > 0.35:  # kısa sivri arka uç
            t = (y - 0.35) * (z - 0.25)
            py += 0.1 * t
            pz += 0.02 * t
        if z < 0.0:
            # alt yarı: omuz hizasına kadar uzayıp genişler (pelerinlik)
            k = -z  # 0..1
            drop = (hc.z + 0.03 - shz + 0.015) * k ** 1.0
            pz = 0.03 - drop - 0.02 * k
            r = 1.0 + (shw / 0.135 - 1.0) * k ** 0.6
            px = x * 0.14 * r
            py = y * (0.16 + 0.05 * k ** 1.6) + 0.02 + 0.02 * k
        v.co = (px, py, pz)
    o.location = hc
    bpy.ops.object.shade_smooth()
    o.data.materials.append(hm)
    o.data.materials.append(_m("cloak_in"))
    so = o.modifiers.new("sol", "SOLIDIFY")
    so.thickness = 0.012
    so.material_offset = 1
    cut = MA._prim("sphere", hm, hc + Vector((0, -0.14, -0.03)), (0.088, 0.11, 0.11))
    rim = MA._prim("torus", B.material("w_rim", "#3c3946", 0.9), hc + Vector((0, -0.112, -0.03)), (0.085, 0.016), rot=(math.pi / 2 + 0.25, 0, 0))
    rim.scale = (1.0, 1.25, 1.0)
    bo = o.modifiers.new("cut", "BOOLEAN")
    bo.object = cut
    bo.operation = "DIFFERENCE"
    o.modifiers.move(len(o.modifiers) - 1, 0)
    cut.hide_render = cut.hide_viewport = True
    # kafatası maskesi (göz çukurları, burun, diş çizgisi) + kapüşonun alına düşen gölgesi
    bone = B.material("w_mask", PAL["bone"], 0.55)
    dark = B.material("w_shadow", PAL["shadow"], 1.0)
    parts = [MA._prim("sphere", bone, hc + Vector((0, -0.1, -0.02)), (0.078, 0.055, 0.095))]
    for sx in (-1, 1):
        parts.append(MA._prim("sphere", dark, hc + Vector((sx * 0.031, -0.148, 0.002)), (0.026, 0.014, 0.024)))
        parts.append(MA._prim("sphere", B.material("w_eye", "#c8342a", 0.3, emit=3.0), hc + Vector((sx * 0.031, -0.158, 0.002)), (0.007, 0.004, 0.007)))
    parts.append(MA._prim("sphere", dark, hc + Vector((0, -0.152, -0.035)), (0.01, 0.007, 0.013)))
    for k in range(5):  # dişler
        parts.append(MA._prim("box", dark, hc + Vector((-0.024 + k * 0.012, -0.143, -0.075)), (0.003, 0.008, 0.014)))
    parts.append(MA._prim("box", dark, hc + Vector((0, -0.125, 0.06)), (0.14, 0.035, 0.03)))  # alın gölgesi
    for ob in [o, cut, rim] + parts:
        MA._bone_child(ob, rig, "head")


def gear(rig):
    s3 = MA._bw(rig, "spine_03")
    pelvis = MA._bw(rig, "pelvis")
    lm = _m("leather", kind="leather", rough=0.6)
    # göğüste çapraz kayışlar
    for sgn in (-1, 1):
        o = MA._prim("box", lm, s3 + Vector((0, -0.15, -0.03)), (0.04, 0.014, 0.44), rot=(0.06, sgn * 0.62, 0), bevel=0.004)
        MA._bone_child(o, rig, "spine_03")
    o = MA._prim("sphere", B.material("w_metal", PAL["metal"], 0.35, metal=1.0), s3 + Vector((0, -0.138, -0.02)), (0.022, 0.01, 0.022))
    MA._bone_child(o, rig, "spine_03")
    # kemer + toka + kafatası kesesi
    o = MA._prim("torus", _m("belt", kind="leather", rough=0.6), pelvis + Vector((0, 0.005, 0.07)), (0.15, 0.018), rot=(0, 0, 0))
    o.scale = (1.0, 0.78, 0.9)
    MA._bone_child(o, rig, "pelvis")
    o = MA._prim("box", B.material("w_metal", PAL["metal"], 0.35, metal=1.0), pelvis + Vector((0, -0.14, 0.07)), (0.07, 0.016, 0.055), bevel=0.005)
    MA._bone_child(o, rig, "pelvis")
    o = MA._prim("sphere", _m("bone", kind=None, rough=0.5), pelvis + Vector((-0.15, -0.06, -0.02)), (0.045, 0.04, 0.05))
    MA._bone_child(o, rig, "pelvis")
    for sx in (-1, 1):  # göz çukurları
        e = MA._prim("sphere", B.material("w_shadow", PAL["shadow"], 1.0), pelvis + Vector((-0.15 + sx * 0.016, -0.098, -0.01)), (0.01, 0.006, 0.012))
        MA._bone_child(e, rig, "pelvis")
    # ön kol sargıları
    bm = _m("bandage")
    for side in ("l", "r"):
        a, b = MA._bw(rig, "lowerarm_" + side), MA._bw(rig, "lowerarm_" + side, "tail")
        d = b - a
        for k in range(4):
            c = a + d * (0.35 + 0.17 * k)
            o = MA._prim("torus", bm, c, (0.036 - 0.003 * k, 0.011))
            o.rotation_mode = "QUATERNION"
            o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
            MA._bone_child(o, rig, "lowerarm_" + side)
    # metal kolluklar
    for side in ("l", "r"):
        a, b = MA._bw(rig, "lowerarm_" + side), MA._bw(rig, "lowerarm_" + side, "tail")
        d = b - a
        o = MA._prim("cyl", B.material("w_bracer", "#6e6a66", 0.35, metal=1.0), a + d * 0.62, (0.046, d.length * 0.42))
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
        MA._bone_child(o, rig, "lowerarm_" + side)
    # bot konçları
    for side in ("l", "r"):
        a, b = MA._bw(rig, "calf_" + side), MA._bw(rig, "calf_" + side, "tail")
        d = b - a
        o = MA._prim("cyl", _m("boot", kind="leather", rough=0.55), a + d * 0.72, (0.058, d.length * 0.5))
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
        MA._bone_child(o, rig, "calf_" + side)


def build():
    actor = MA.build("warrior")
    rig = actor["rig"]
    # kısıtlar hedefleri henüz ayarlanmamışken kemikleri büker: parçaları dinlenme pozunda yerleştir
    cons = [c for pb in rig.pose.bones for c in pb.constraints]
    for c in cons:
        c.mute = True
    bpy.context.view_layer.update()
    hood(rig)
    gear(rig)
    actor["cloak"] = cloak(rig, actor)
    for c in cons:
        c.mute = False
    bpy.context.view_layer.update()
    # takım: kahverengi deri palto tonu; ayakkabı: kahverengi bot
    for o in bpy.data.objects:
        if o.type == "MESH" and "elegantsuit" in o.name.lower():
            for s in o.material_slots:
                if s.material and s.material.use_nodes:
                    recolor(s.material, MA.LOOKS["warrior"]["tint_all"], keep=0.18)
        if o.type == "MESH" and "shoes04" in o.name.lower():
            for s in o.material_slots:
                if s.material and s.material.use_nodes:
                    recolor(s.material, PAL["boot"])
    return actor


def set_cloth(actor, flow, sway):
    kb = actor["cloak"].data.shape_keys.key_blocks
    kb["flow"].value = max(-1.0, min(1.0, flow))
    kb["sway"].value = max(-1.0, min(1.0, sway))


def cloth_for(anim, t):
    """Animasyon ve faza göre pelerin salınımı (faz gecikmeli)."""
    ph = 2 * math.pi * t
    if anim in ("run", "roll"):
        return 0.5 + 0.12 * math.sin(ph * 2 - 1.2), 0.22 * math.sin(ph - 1.0)
    if anim == "walk":
        return 0.22 + 0.08 * math.sin(ph * 2 - 1.2), 0.35 * math.sin(ph - 1.0)
    if anim == "punch":
        return 0.18 + 0.15 * max(0.0, math.sin(ph * 2)), 0.2 * math.sin(ph * 2 - 0.8)
    if anim in ("hurt", "knockdown"):
        return 0.45, 0.1
    if anim == "death":
        return 0.35 * (1 - t), 0.0
    return 0.05 + 0.04 * math.sin(ph), 0.06 * math.sin(ph - 0.7)
