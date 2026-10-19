"""V5 kahramanı: siyah kapüşonlu Savaşçı (MakeHuman gövdesi + kodla modellenen kapüşon, maske, uzun palto, tek omuz pelerin).

Kıyafet iskelete ağırlıklı bağlıdır; ikincil hareket (salınım) şekil anahtarlarıyla verilir:
  flow  → etek/pelerin arkaya ve yukarı savrulur (koşu/takla)
  sway  → etek/pelerin sağa/sola salınır (yürüme fazına gecikmeli)
"""
import math

import bpy
import numpy as np
from mathutils import Vector

import blib as B
import mh_actor as MA

MA.LOOKS["warrior"] = dict(gender=1.0, muscle=0.8, weight=0.42, height=0.7, age=0.35, skin="young_caucasian_male", hair=None,
                           suit="male_elegantsuit01", shoes="shoes04", tint_all="#17161b", wraps=False)

PAL = {
    "coat": "#24222a", "coat_in": "#3a1216", "cape": "#18171d", "hood": "#1b1a20", "leather": "#3a2a20", "belt": "#5a3a24",
    "metal": "#77736c", "bone": "#d8cfb8", "mask": "#efe6cc", "stripe": "#b3211c", "eye": "#ff3a2a", "scarf": "#6e1820",
    "glove": "#141217", "boot": "#36231a", "shadow": "#050407",
}


def _m(name, rough=0.85, emit=0.0, metal=0.0):
    # piksel hattında doku/kabartma gürültü üretir: düz renk
    return B.material("w_" + name, PAL[name], rough, metal=metal, emit=emit)


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


def _clamp(v, a=0.0, b=1.0):
    return min(b, max(a, v))


def _drape(name, mat, mat_in, N, M, ring, skip=None, thick=0.008):
    """ring(h, u) → (x, y, z) dünya noktası (h: 0 üst → 1 etek, u: 0..1 kenar boyunca); skip(i, j) → yüz atla."""
    verts, faces = [], []
    for j in range(M + 1):
        for i in range(N + 1):
            verts.append(ring(j / M, i / N))
    for j in range(M):
        for i in range(N):
            if skip and skip(i, j):
                continue
            a = j * (N + 1) + i
            faces.append((a, a + 1, a + N + 2, a + N + 1))
    ob = _mesh(name, verts, faces, mat)
    ob.data.materials.append(mat_in)
    so = ob.modifiers.new("sol", "SOLIDIFY")
    so.thickness = thick
    so.material_offset = 1
    return ob


def _keys(ob, hfn, flow_v, sway_v):
    """Dinlenme pozunda salınım şekil anahtarları; etkisi eteğe doğru artar."""
    ob.shape_key_add(name="Basis")
    flow = ob.shape_key_add(name="flow")
    sway = ob.shape_key_add(name="sway")
    for i, v in enumerate(ob.data.vertices):
        p = v.co
        k = hfn(ob.matrix_world @ p) ** 1.6
        flow.data[i].co = p + Vector(flow_v) * k
        sway.data[i].co = p + Vector(sway_v) * k
    flow.slider_min, sway.slider_min = -1.0, -1.0


def coat(rig):
    """Belden baldıra uzun palto eteği: önü açık, arkası yırtmaçlı (iki kuyruk), dikey kıvrımlar."""
    pelvis = MA._bw(rig, "pelvis")
    tl, tr = MA._bw(rig, "thigh_l"), MA._bw(rig, "thigh_r")
    knee = (MA._bw(rig, "calf_l") + MA._bw(rig, "calf_r")) / 2
    ankle = (MA._bw(rig, "foot_l") + MA._bw(rig, "foot_r")) / 2
    cx, cy = (tl.x + tr.x) / 2, pelvis.y + 0.01
    top_z = pelvis.z + 0.1
    bot_z = knee.z - (knee.z - ankle.z) * 0.62
    hipw = abs(tl.x - tr.x) / 2 + 0.085
    N, M = 40, 18
    rng = np.random.default_rng(11)
    hem = rng.uniform(0.0, 0.07, N + 1) ** 1.5 * 2.5

    def ring(h, u):
        g = 0.22 + 0.5 * h ** 1.3  # önde açıklık aşağı doğru genişler
        a = -math.pi + g + (2 * math.pi - 2 * g) * u
        rx = hipw + (0.29 - hipw) * h ** 0.9
        ry = 0.125 + 0.115 * h ** 0.9
        fold = 1.0 + 0.09 * h ** 0.7 * math.sin(a * 11.0 + 0.6)
        z = top_z + (bot_z - top_z) * h - (hem[int(round(u * N))] if h >= 1.0 else 0.0)
        return (cx + math.sin(a) * rx * fold, cy + math.cos(a) * ry * fold, z)

    mid = N // 2
    ob = _drape("Coat", _m("coat", 0.7), _m("coat_in"), N, M, ring,
                skip=lambda i, j: (i == mid and j >= M * 0.42) or (j == M - 1 and (i * 7) % 5 == 0))
    sl = 1.0 if tl.x > cx else -1.0

    def hof(p):
        return _clamp((top_z - p.z) / (top_z - bot_z))

    def wfn(p):
        h = hof(p)
        k = 0.7 * h ** 1.1
        s = _clamp(0.5 + sl * (p.x - cx) / 0.1)
        return {"pelvis": 1.0 - k, "thigh_l": k * s, "thigh_r": k * (1.0 - s)}

    _weights(ob, rig, wfn)
    _keys(ob, hof, (0, 0.34, 0.14), (0.11, 0.02, 0.02))
    return ob


def cape(rig):
    """Sol omuzdan sarkan yırtık pelerin: omzu sarar, sırttan dizin altına iner; eteği çapraz ve parçalı."""
    sh_l, sh_r = MA._bw(rig, "upperarm_l"), MA._bw(rig, "upperarm_r")
    neck = MA._bw(rig, "neck_01")
    knee = (MA._bw(rig, "calf_l") + MA._bw(rig, "calf_r")) / 2
    cx, cy = (sh_l.x + sh_r.x) / 2, neck.y + 0.01
    shz = (sh_l.z + sh_r.z) / 2
    half_w = abs(sh_l.x - sh_r.x) / 2
    s = 1.0 if sh_l.x > cx else -1.0
    a_front, a_back = s * (math.pi - 0.8), -s * 0.45
    N, M = 30, 20
    rng = np.random.default_rng(3)
    tear = rng.uniform(0.0, 1.0, N + 1)

    def ring(h, u):
        a = a_front + (a_back - a_front) * u
        c = max(0.0, math.cos(a))  # sırtta yaka yüksekliğine çıkar
        top = shz + 0.05 + (neck.z - 0.02 - shz - 0.05) * c ** 2
        bot = knee.z - 0.2 + 0.26 * u + (0.14 * tear[int(round(u * N))] ** 3 if h >= 1.0 else 0.0)
        z = top + (bot - top) * h
        rx = half_w + 0.075 + (0.35 - half_w - 0.075) * h ** 0.7
        ry = 0.135 + 0.13 * h ** 0.75
        fold = 1.0 + 0.07 * h * math.sin(a * 6.0 + h * 2.5)
        return (cx + math.sin(a) * rx * fold, cy + 0.02 + math.cos(a) * ry * fold, z)

    # etekte yırtıklar: bazı sütunlar kısa kalır
    ob = _drape("Cape", _m("cape", 0.8), _m("coat_in"), N, M, ring,
                skip=lambda i, j: j >= M - 3 + (i * 5 % 3) and (i * 7 + j) % 4 == 0, thick=0.01)
    top_ref, bot_ref = neck.z, knee.z - 0.2

    def hof(p):
        return _clamp((top_ref - p.z) / (top_ref - bot_ref))

    def wfn(p):
        h = hof(p)
        w = {}
        side = _clamp(s * (p.x - cx) / (half_w + 0.05))  # omuz tarafı
        if h < 0.3:
            k = h / 0.3
            w["spine_03"] = 1.0 - k * 0.5
            w["spine_02"] = k * 0.5
            w["upperarm_l"] = 0.3 * side * (1.0 - k)
        elif h < 0.65:
            k = (h - 0.3) / 0.35
            w["spine_02"] = 1.0 - k
            w["spine_01"] = k
        else:
            k = (h - 0.65) / 0.35
            w["spine_01"] = 1.0 - k * 0.6
            w["pelvis"] = k * 0.6
        return w

    _weights(ob, rig, wfn)
    _keys(ob, hof, (0, 0.42, 0.2), (0.15, 0.03, 0.04))
    return ob


def capelet(rig):
    """Omuzları saran kısa, kat kat yırtık pelerinlik (kapüşonun altından üst kola kadar)."""
    sh_l, sh_r = MA._bw(rig, "upperarm_l"), MA._bw(rig, "upperarm_r")
    el = (MA._bw(rig, "lowerarm_l") + MA._bw(rig, "lowerarm_r")) / 2
    neck = MA._bw(rig, "neck_01")
    cx, cy = (sh_l.x + sh_r.x) / 2, neck.y + 0.01
    shz = (sh_l.z + sh_r.z) / 2
    half_w = abs(sh_l.x - sh_r.x) / 2
    top_z, bot_z = neck.z - 0.02, shz - (shz - el.z) * 0.55
    N, M = 48, 10
    rng = np.random.default_rng(8)
    tear = rng.uniform(0.0, 1.0, N + 1)

    def ring(h, u):
        a = -math.pi + 0.18 + (2 * math.pi - 0.36) * u
        rx = half_w * 0.6 + (half_w * 0.45 + 0.06) * h ** 0.75
        ry = 0.105 + 0.065 * h ** 0.75
        fold = 1.0 + 0.045 * h * math.sin(a * 13.0)
        z = top_z + (bot_z - top_z) * h - (0.05 * tear[int(round(u * N))] ** 2 if h >= 1.0 else 0.0)
        z += 0.03 * max(0.0, -math.cos(a)) * h  # önde biraz kısa
        return (cx + math.sin(a) * rx * fold, cy + math.cos(a) * ry * fold, z)

    ob = _drape("Capelet", _m("cape", 0.75), _m("shadow", 1.0), N, M, ring,
                skip=lambda i, j: j >= M - 2 and (i * 5 + j) % 4 == 0, thick=0.012)

    def hof(p):
        return _clamp((top_z - p.z) / (top_z - bot_z))

    def wfn(p):
        h = hof(p)
        side = "l" if (p.x - cx) * (sh_l.x - cx) > 0 else "r"
        out = _clamp((abs(p.x - cx) - half_w * 0.6) / (half_w * 0.5))
        return {"spine_03": 1.0 - 0.6 * out * h, "upperarm_" + side: 0.6 * out * h}

    _weights(ob, rig, wfn)
    _keys(ob, hof, (0, 0.1, 0.05), (0.04, 0.0, 0.0))
    return ob


def hood(rig):
    """Kapüşon + omuzlara inen pelerinlik tek parça; yüzde beyaz maske (kırmızı dikey çizgiler, sarı parlayan gözler),
    alt yüzde koyu kırmızı atkı."""
    head = MA._bw(rig, "head")
    htop = MA._bw(rig, "head", "tail")
    hc = (head + htop) / 2
    sh_l, sh_r = MA._bw(rig, "upperarm_l"), MA._bw(rig, "upperarm_r")
    shz = (sh_l.z + sh_r.z) / 2
    shw = abs(sh_l.x - sh_r.x) / 2 + 0.06
    hm = _m("hood", 0.8)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=44, ring_count=26, radius=1, location=(0, 0, 0))
    o = bpy.context.active_object
    for v in o.data.vertices:
        x, y, z = v.co
        px, py, pz = x * 0.108, y * 0.13 + 0.012, z * 0.108 + 0.012
        if z > 0.2 and y > 0.2:  # arkada sivri uç
            t = (y - 0.2) * (z - 0.2)
            py += 0.07 * t
            pz += 0.015 * t
        if z > 0.55 and y < -0.3:  # önde alnın üstünde hafif sivrilen kenar
            pz += 0.012 * (z - 0.55) * (-y - 0.3) * 6
        if z < 0.0:
            k = -z
            drop = (hc.z + 0.035 - shz + 0.02) * k
            pz = 0.035 - drop - 0.02 * k
            r = 1.0 + (shw / 0.108 - 1.0) * k ** 1.3
            px = x * 0.116 * r
            py = y * (0.165 + 0.05 * k ** 1.6) + 0.02 + 0.02 * k
        v.co = (px, py, pz)
    o.location = hc
    bpy.ops.object.shade_smooth()
    o.data.materials.append(hm)
    o.data.materials.append(_m("shadow", 1.0))
    so = o.modifiers.new("sol", "SOLIDIFY")
    so.thickness = 0.014
    so.material_offset = 1
    cut = MA._prim("sphere", hm, hc + Vector((0, -0.145, -0.035)), (0.085, 0.11, 0.112))
    rim = MA._prim("torus", _m("hood", 0.75), hc + Vector((0, -0.118, -0.03)), (0.084, 0.017), rot=(math.pi / 2 + 0.22, 0, 0))
    rim.scale = (1.0, 1.28, 1.0)
    bo = o.modifiers.new("cut", "BOOLEAN")
    bo.object = cut
    bo.operation = "DIFFERENCE"
    o.modifiers.move(len(o.modifiers) - 1, 0)
    cut.hide_render = cut.hide_viewport = True
    # kapüşon içi karanlık (yüzün arkası)
    dark = _m("shadow", 1.0)
    parts = [MA._prim("sphere", dark, hc + Vector((0, -0.04, -0.01)), (0.1, 0.09, 0.12))]
    # kafatası maske: göz çukurları (kırmızı kor), burun, diş çizgisi
    mc = hc + Vector((0, -0.104, -0.016))
    parts.append(MA._prim("sphere", _m("mask", 0.5), mc, (0.07, 0.055, 0.09)))
    parts.append(MA._prim("sphere", _m("mask", 0.5), mc + Vector((0, -0.012, -0.05)), (0.045, 0.04, 0.03)))  # çene
    for sx in (-1, 1):
        parts.append(MA._prim("sphere", dark, hc + Vector((sx * 0.028, -0.152, -0.002)), (0.025, 0.013, 0.023)))
        parts.append(MA._prim("sphere", _m("eye", 0.3, emit=5.0), hc + Vector((sx * 0.028, -0.163, -0.004)), (0.011, 0.004, 0.009)))
    parts.append(MA._prim("sphere", dark, hc + Vector((0, -0.16, -0.035)), (0.01, 0.006, 0.013)))
    for k in range(5):
        parts.append(MA._prim("box", dark, hc + Vector((-0.022 + k * 0.011, -0.148, -0.075)), (0.0035, 0.008, 0.016)))
    # kalın kırmızı atkı: boyna sarılı + göğse inen uç
    neck = MA._bw(rig, "neck_01")
    sc = MA._prim("torus", _m("scarf", 0.9), neck + Vector((0, -0.01, -0.035)), (0.068, 0.028))
    sc.scale = (1.1, 1.05, 1.3)
    sc2 = MA._prim("sphere", _m("scarf", 0.9), neck + Vector((0.02, -0.085, -0.085)), (0.04, 0.022, 0.06), rot=(0.2, 0, 0.3))
    for ob in [o, cut, rim] + parts:
        MA._bone_child(ob, rig, "head")
    MA._bone_child(sc, rig, "spine_03")
    MA._bone_child(sc2, rig, "spine_03")


def _along(rig, bone, frac, size, mat, kind="cyl"):
    a, b = MA._bw(rig, bone), MA._bw(rig, bone, "tail")
    d = b - a
    o = MA._prim(kind, mat, a + d * frac, size)
    o.rotation_mode = "QUATERNION"
    o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
    MA._bone_child(o, rig, bone)
    return o


def gear(rig):
    s3 = MA._bw(rig, "spine_03")
    pelvis = MA._bw(rig, "pelvis")
    lm = _m("leather", 0.55)
    metal = _m("metal", 0.35, metal=1.0)
    # çapraz göğüs kayışı (sağ omuzdan sol kalçaya) + toka
    sx = 1.0 if MA._bw(rig, "upperarm_l").x > pelvis.x else -1.0
    o = MA._prim("box", lm, s3 + Vector((0, -0.15, -0.06)), (0.045, 0.016, 0.52), rot=(0.08, sx * 0.55, 0), bevel=0.004)
    MA._bone_child(o, rig, "spine_03")
    o = MA._prim("box", metal, s3 + Vector((-sx * 0.04, -0.158, 0.0)), (0.03, 0.01, 0.03), bevel=0.003)
    MA._bone_child(o, rig, "spine_03")
    # geniş kemer + toka + kafatası kesesi + sarkan kemer ucu
    o = MA._prim("torus", _m("belt", 0.55), pelvis + Vector((0, 0.005, 0.085)), (0.155, 0.026))
    o.scale = (1.0, 0.8, 1.0)
    MA._bone_child(o, rig, "pelvis")
    o = MA._prim("box", metal, pelvis + Vector((0, -0.142, 0.085)), (0.06, 0.016, 0.05), bevel=0.005)
    MA._bone_child(o, rig, "pelvis")
    o = MA._prim("box", _m("belt", 0.55), pelvis + Vector((0.035, -0.14, 0.0)), (0.03, 0.01, 0.13), rot=(0, 0.12, 0))
    MA._bone_child(o, rig, "pelvis")
    o = MA._prim("sphere", _m("bone", 0.5), pelvis + Vector((-sx * 0.15, -0.065, -0.01)), (0.046, 0.042, 0.052))
    MA._bone_child(o, rig, "pelvis")
    for ex in (-1, 1):
        e = MA._prim("sphere", _m("shadow", 1.0), pelvis + Vector((-sx * 0.15 + ex * 0.016, -0.103, 0.0)), (0.011, 0.006, 0.012))
        MA._bone_child(e, rig, "pelvis")
    # eldiven (yumruk) + metal halkalı kolçak
    gm = _m("glove", 0.45)
    for side in ("l", "r"):
        fl = (MA._bw(rig, "lowerarm_" + side, "tail") - MA._bw(rig, "lowerarm_" + side)).length
        _along(rig, "lowerarm_" + side, 0.66, (0.047, fl * 0.5), gm)
        _along(rig, "lowerarm_" + side, 0.5, (0.05, 0.012), metal, kind="torus")
        _along(rig, "lowerarm_" + side, 0.82, (0.046, 0.01), metal, kind="torus")
    # uzun bot konçları
    for side in ("l", "r"):
        a, b = MA._bw(rig, "calf_" + side), MA._bw(rig, "calf_" + side, "tail")
        _along(rig, "calf_" + side, 0.66, (0.06, (b - a).length * 0.62), _m("boot", 0.5))
        _along(rig, "calf_" + side, 0.4, (0.062, 0.012), lm, kind="torus")


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
    actor["cloth"] = [coat(rig), capelet(rig)]
    for c in cons:
        c.mute = False
    bpy.context.view_layer.update()
    # yüz maskeyle kapalı: görünen tek ten eller → deri eldiven
    gm = _m("glove", 0.45)
    for sl in actor["basemesh"].material_slots:
        sl.material = gm
    for o in bpy.data.objects:
        if o.type == "MESH" and "shoes04" in o.name.lower():
            for s in o.material_slots:
                s.material = _m("boot", 0.55)
            continue
        if o.type == "MESH" and "elegantsuit" in o.name.lower():
            for s in o.material_slots:
                if s.material and s.material.use_nodes:
                    recolor(s.material, MA.LOOKS["warrior"]["tint_all"], keep=0.22)
        if o.type == "MESH" and "shoes04" in o.name.lower():
            for s in o.material_slots:
                if s.material and s.material.use_nodes:
                    recolor(s.material, PAL["boot"])
    return actor


def set_cloth(actor, flow, sway):
    for ob in actor["cloth"]:
        kb = ob.data.shape_keys.key_blocks
        kb["flow"].value = max(-1.0, min(1.0, flow))
        kb["sway"].value = max(-1.0, min(1.0, sway))


def cloth_for(anim, t):
    """Animasyon ve faza göre kumaş salınımı (faz gecikmeli)."""
    ph = 2 * math.pi * t
    if anim in ("run", "roll"):
        return 0.5 + 0.12 * math.sin(ph * 2 - 1.2), 0.22 * math.sin(ph - 1.0)
    if anim == "walk":
        return 0.2 + 0.08 * math.sin(ph * 2 - 1.2), 0.35 * math.sin(ph - 1.0)
    if anim == "punch":
        return 0.16 + 0.15 * max(0.0, math.sin(ph * 2)), 0.2 * math.sin(ph * 2 - 0.8)
    if anim in ("hurt", "knockdown"):
        return 0.45, 0.1
    if anim == "death":
        return 0.35 * (1 - t), 0.0
    return 0.05 + 0.04 * math.sin(ph), 0.06 * math.sin(ph - 0.7)
