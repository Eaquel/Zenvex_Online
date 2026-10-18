"""Blender doğa: gerçek yaprak parçacıklı ağaçlar, çalılar, ot ve çiçek kümeleri, kayalar."""
import math
import random

import bpy
import numpy as np
from mathutils import Vector

import blib as B


def _leaf_object(name, color, size=0.09, rough=0.6, shape="oval"):
    """Yaprak: küçük, hafif bükük düzlem; kenar şekli alfa maskesiyle."""
    bpy.ops.mesh.primitive_plane_add(size=size, location=(0, 0, -50))
    ob = bpy.context.active_object
    ob.name = name
    m = bpy.data.materials.new(name + "_m")
    m.use_nodes = True
    nt = m.node_tree
    n = nt.nodes
    L = nt.links
    bsdf = n["Principled BSDF"]
    rgb = B.hexrgb(color)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Subsurface Weight"].default_value = 0.0
    # yaprak başına renk çeşitliliği (nesne rastgelesi)
    info = n.new("ShaderNodeObjectInfo")
    ramp = n.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (rgb[0] * 0.6, rgb[1] * 0.7, rgb[2] * 0.5, 1)
    ramp.color_ramp.elements[1].color = (min(1, rgb[0] * 1.35), min(1, rgb[1] * 1.25), rgb[2] * 0.9, 1)
    L.new(info.outputs["Random"], ramp.inputs["Fac"])
    L.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
    # yaprak şekli
    tc = n.new("ShaderNodeTexCoord")
    sep = n.new("ShaderNodeSeparateXYZ")
    L.new(tc.outputs["UV"], sep.inputs["Vector"])
    # elips maskesi: ((u-.5)/.5)^2*1 + ((v-.5)/.5)^2*k < 1
    mu = n.new("ShaderNodeMath"); mu.operation = "SUBTRACT"; mu.inputs[1].default_value = 0.5
    mv = n.new("ShaderNodeMath"); mv.operation = "SUBTRACT"; mv.inputs[1].default_value = 0.5
    L.new(sep.outputs["X"], mu.inputs[0]); L.new(sep.outputs["Y"], mv.inputs[0])
    su = n.new("ShaderNodeMath"); su.operation = "MULTIPLY"; su.inputs[1].default_value = 4.0 if shape == "needle" else 2.2
    L.new(mu.outputs[0], su.inputs[0])
    du = n.new("ShaderNodeMath"); du.operation = "POWER"; du.inputs[1].default_value = 2
    dv = n.new("ShaderNodeMath"); dv.operation = "POWER"; dv.inputs[1].default_value = 2
    sv = n.new("ShaderNodeMath"); sv.operation = "MULTIPLY"; sv.inputs[1].default_value = 2.0
    L.new(mv.outputs[0], sv.inputs[0])
    L.new(su.outputs[0], du.inputs[0]); L.new(sv.outputs[0], dv.inputs[0])
    ad = n.new("ShaderNodeMath"); ad.operation = "ADD"
    L.new(du.outputs[0], ad.inputs[0]); L.new(dv.outputs[0], ad.inputs[1])
    lt = n.new("ShaderNodeMath"); lt.operation = "LESS_THAN"; lt.inputs[1].default_value = 1.0
    L.new(ad.outputs[0], lt.inputs[0])
    L.new(lt.outputs[0], bsdf.inputs["Alpha"])
    m.blend_method = "HASHED" if hasattr(m, "blend_method") else None
    ob.data.materials.append(m)
    return ob


def _emitter(verts_radii, name="canopy", seed=0, subdiv=2):
    """Taç hacmini oluşturan ikosferler (görünmez yayıcı)."""
    objs = []
    for (c, r) in verts_radii:
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=subdiv, radius=r, location=B.g2b(c))
        objs.append(bpy.context.active_object)
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    em = bpy.context.active_object
    em.name = name
    return em


def _scatter(em, leaf, count, size, size_rand=0.4, seed=0, inner=None):
    mod = em.modifiers.new("leaves", "PARTICLE_SYSTEM")
    ps = mod.particle_system
    st = ps.settings
    st.type = "HAIR"
    st.count = count
    st.hair_length = 1.0
    st.render_type = "OBJECT"
    st.instance_object = leaf
    st.particle_size = size
    st.size_random = size_rand
    st.use_rotations = True
    st.rotation_mode = "NOR"
    st.rotation_factor_random = 0.6
    st.phase_factor_random = 2.0
    st.emit_from = "FACE"
    st.use_emit_random = True
    st.distribution = "RAND"
    ps.seed = seed
    em.show_instancer_for_render = False
    return ps


def _birch_marks(m):
    nt = m.node_tree
    n, L = nt.nodes, nt.links
    bsdf = n["Principled BSDF"]
    tc = n.new("ShaderNodeTexCoord")
    wave = n.new("ShaderNodeTexWave")
    wave.bands_direction = "Z"
    wave.inputs["Scale"].default_value = 6
    wave.inputs["Distortion"].default_value = 6
    L.new(tc.outputs["Object"], wave.inputs["Vector"])
    ramp = n.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.86
    ramp.color_ramp.elements[0].color = (0.85, 0.83, 0.78, 1)
    ramp.color_ramp.elements[1].position = 0.9
    ramp.color_ramp.elements[1].color = (0.05, 0.05, 0.05, 1)
    L.new(wave.outputs["Fac"], ramp.inputs["Fac"])
    L.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])


def _bark_mat(color="#6a4a32"):
    return B.material("bark_" + color, color, 0.9, kind="bark", bump=0.8)


def _branch(a, b, r1, r2, mat):
    ob = B.add_mesh_obj("br", mat, prim="cylinder", a=a, b=b, r=r1)
    # konik: üst ucu incelt
    me = ob.data
    for v in me.vertices:
        if v.co.z > 0:
            v.co.x *= r2 / r1
            v.co.y *= r2 / r1
    return ob


def oak(seed, palette="#5c9a3a", autumn=False):
    rng = random.Random(seed)
    h = rng.uniform(3.0, 3.6)
    lean = rng.uniform(-0.2, 0.2)
    bark = _bark_mat()
    top = np.array([lean, h, rng.uniform(-0.1, 0.1)])
    _branch((0, -0.05, 0), top, 0.3, 0.16, bark)
    for k in range(5):
        a = k * math.tau / 5 + rng.uniform(-0.3, 0.3)
        _branch((0, 0.3, 0), (math.cos(a) * 0.6, -0.02, math.sin(a) * 0.6), 0.13, 0.04, bark)
    blobs = []
    for k in range(5):
        a = rng.uniform(0, math.tau)
        tip = top + np.array([math.cos(a) * 1.1, 0.4 + rng.uniform(0, 0.4), math.sin(a) * 1.1])
        _branch(top * 0.7, tip, 0.11, 0.04, bark)
        blobs.append((tip, rng.uniform(0.6, 0.85)))
    blobs.append((top + np.array([0, 0.9, 0]), 1.0))
    for _ in range(6):
        a = rng.uniform(0, math.tau)
        blobs.append((top + np.array([math.cos(a) * 0.8, rng.uniform(0.2, 1.2), math.sin(a) * 0.8]), rng.uniform(0.55, 0.8)))
    col = "#b89a32" if autumn else palette
    em = _emitter(blobs)
    leaf = _leaf_object("leaf", col, 0.11)
    _scatter(em, leaf, 9500, 1.0, 0.55, seed)
    # iç dolgu: koyu hacim (yapraklar arası boşluk görünmesin)
    inner = _emitter([(c, r * 0.8) for c, r in blobs], "inner")
    inner.data.materials.append(B.material("inner_" + col, "#2a4a1e", 0.9, kind="leaf"))
    return h


def pine(seed):
    rng = random.Random(seed)
    h = rng.uniform(5.2, 6.4)
    bark = _bark_mat("#6e4430")
    _branch((0, -0.05, 0), (0, h, 0), 0.2, 0.04, bark)
    needle = _leaf_object("needle", "#2e6a42", 0.16, shape="needle")
    tiers = 7
    for i in range(tiers):
        y = 1.0 + i * (h - 1.0) / tiers
        r = 1.6 * (1 - i / tiers) + 0.3
        bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=r, radius2=0.05, depth=1.3, location=B.g2b((0, y + 0.55, 0)))
        cone = bpy.context.active_object
        cone.data.materials.append(B.material("pine_in", "#1e4630", 0.9, kind="leaf"))
        _scatter(cone, needle, int(1500 * r), 1.0, 0.5, seed + i)
    return h


def birch(seed):
    rng = random.Random(seed)
    h = rng.uniform(3.6, 4.2)
    top = np.array([rng.uniform(-0.25, 0.25), h, 0])
    bm = B.material("birch", "#e8e4d8", 0.8, kind="bark", bump=0.5)
    _birch_marks(bm)
    _branch((0, -0.05, 0), top, 0.15, 0.07, bm)
    blobs = [(top + np.array([0, 0.45, 0]), 0.7)]
    for _ in range(6):
        a = rng.uniform(0, math.tau)
        blobs.append((top + np.array([math.cos(a) * 0.55, rng.uniform(0.0, 0.9), math.sin(a) * 0.55]), rng.uniform(0.4, 0.6)))
    em = _emitter(blobs)
    leaf = _leaf_object("bleaf", "#8cbc46", 0.09)
    _scatter(em, leaf, 6000, 1.0, 0.5, seed)
    inner = _emitter([(c, r * 0.75) for c, r in blobs], "inner")
    inner.data.materials.append(B.material("inner_b", "#3a5a22", 0.9, kind="leaf"))
    return h


def bush(seed, kind="plain"):
    rng = random.Random(seed)
    w = 0.45 if kind != "wide" else 0.75
    blobs = []
    for _ in range(6):
        a = rng.uniform(0, math.tau)
        d = rng.uniform(0, w)
        blobs.append(((math.cos(a) * d, 0.3 + rng.uniform(0, 0.2), math.sin(a) * d * 0.7), rng.uniform(0.25, 0.38)))
    em = _emitter(blobs)
    leaf = _leaf_object("bleaf", "#4c8a36", 0.075)
    _scatter(em, leaf, 2600 if kind != "wide" else 4000, 1.0, 0.5, seed)
    inner = _emitter([(c, r * 0.8) for c, r in blobs], "inner")
    inner.data.materials.append(B.material("inner_bush", "#24401a", 0.9, kind="leaf"))
    if kind in ("berry", "flower"):
        fm = B.material("berry", "#d8343c", 0.3, clearcoat=0.5) if kind == "berry" else B.material("blossom", "#f2a8cc", 0.6, kind="cloth")
        for _ in range(16):
            c, r = blobs[rng.randrange(len(blobs))]
            a, e = rng.uniform(0, math.tau), rng.uniform(-0.2, 1.2)
            p = np.array(c) + np.array([math.cos(a) * math.cos(e), math.sin(e) * 0.7, math.sin(a) * math.cos(e)]) * r
            B.add_mesh_obj("f", fm, prim="sphere", c=p, r=0.035 if kind == "berry" else 0.05)


def grass_patch(seed, color="#5e9a3a", n=60, radius=0.25, height=0.3):
    rng = random.Random(seed)
    gm = B.material("grassb_" + color, color, 0.7, kind="leaf")
    verts, faces = [], []
    for i in range(n):
        a, d = rng.uniform(0, math.tau), math.sqrt(rng.random()) * radius
        x, z = math.cos(a) * d, math.sin(a) * d
        hh = height * rng.uniform(0.5, 1.0)
        lean = rng.uniform(-0.12, 0.12)
        ang = rng.uniform(0, math.pi)
        dx, dz = math.cos(ang) * 0.012, math.sin(ang) * 0.012
        b = len(verts)
        verts += [(x - dx, -(z - dz), 0), (x + dx, -(z + dz), 0), (x + lean, -z, hh)]
        faces.append((b, b + 1, b + 2))
    me = bpy.data.meshes.new("grass")
    me.from_pydata(verts, [], faces)
    ob = bpy.data.objects.new("grass", me)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(gm)
    return ob


def flowers(seed, color):
    rng = random.Random(seed)
    stem = B.material("stem", "#4e8a34", 0.7)
    petal = B.material("petal_" + color, color, 0.5, sss=0.2)
    core = B.material("core", "#e8b03a", 0.5)
    for _ in range(5):
        a, d = rng.uniform(0, math.tau), rng.uniform(0, 0.12)
        x, z = math.cos(a) * d, math.sin(a) * d
        h = rng.uniform(0.12, 0.2)
        B.add_mesh_obj("st", stem, prim="cylinder", a=(x, 0, z), b=(x, h, z), r=0.005)
        for k in range(5):
            pa = k * math.tau / 5
            B.add_mesh_obj("pt", petal, prim="sphere", c=(x + math.cos(pa) * 0.018, h + 0.004, z + math.sin(pa) * 0.018), r=0.013)
        B.add_mesh_obj("co", core, prim="sphere", c=(x, h + 0.01, z), r=0.009)
