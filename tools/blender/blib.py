"""Blender (bpy) yardımcıları: sahne, kamera, ışık, malzemeler, metaball gövde, render.

/opt/bvenv/bin/python ile çalışır (pip install bpy).
"""
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector, Matrix

PITCH = math.radians(35.0)
MPP = 0.024  # metre / piksel (oyun içi 1x)


# ------------------------------------------------------------------ sahne
def reset_scene(res=(192, 192), samples=24, mpp=MPP, shadow_catcher=True):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    _mat_cache.clear()
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = samples
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 4
    sc.cycles.diffuse_bounces = 2
    sc.cycles.glossy_bounces = 2
    sc.cycles.transparent_max_bounces = 4
    sc.render.film_transparent = True
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.render.threads_mode = "FIXED"
    sc.render.threads = os.cpu_count() or 4
    # dünya: gökyüzü ortam ışığı
    w = bpy.data.worlds.new("W")
    sc.world = w
    w.use_nodes = True
    bg = w.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.55, 0.62, 0.75, 1)
    bg.inputs[1].default_value = 0.55
    # kamera: ortografik, 35° eğim, +z (oyun güneyi) tarafından
    cam_data = bpy.data.cameras.new("Cam")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = res[0] * mpp
    cam = bpy.data.objects.new("Cam", cam_data)
    sc.collection.objects.link(cam)
    sc.camera = cam
    # Blender: Z yukarı. Oyunun +z (güney/kameraya doğru) ekseni = Blender -Y
    d = 20.0
    cam.location = (0, -d * math.cos(PITCH), d * math.sin(PITCH))
    cam.rotation_euler = (math.pi / 2 - PITCH, 0, 0)
    cam_data.clip_end = 100
    # güneş: sol-üst-ön
    sun_d = bpy.data.lights.new("Sun", "SUN")
    sun_d.energy = 3.2
    sun_d.angle = math.radians(8)
    sun_d.color = (1.0, 0.95, 0.86)
    sun = bpy.data.objects.new("Sun", sun_d)
    sc.collection.objects.link(sun)
    # ışığın gidiş yönü: sağa, biraz kameradan uzağa ve aşağı (yüksek güneş → kısa gölge)
    sun.rotation_euler = Vector((0.42, 0.32, -1.0)).to_track_quat("-Z", "Y").to_euler()
    # dolgu (soğuk) ışık
    fill_d = bpy.data.lights.new("Fill", "SUN")
    fill_d.energy = 0.6
    fill_d.color = (0.7, 0.8, 1.0)
    fill_d.use_shadow = False
    fill = bpy.data.objects.new("Fill", fill_d)
    sc.collection.objects.link(fill)
    fill.rotation_euler = Vector((-0.6, 0.5, -0.6)).to_track_quat("-Z", "Y").to_euler()
    # kenar (rim) ışık arkadan
    rim_d = bpy.data.lights.new("Rim", "SUN")
    rim_d.energy = 1.6
    rim_d.color = (1.0, 0.92, 0.8)
    rim_d.use_shadow = False
    rim = bpy.data.objects.new("Rim", rim_d)
    sc.collection.objects.link(rim)
    rim.rotation_euler = Vector((0.2, -1.0, -0.35)).to_track_quat("-Z", "Y").to_euler()
    if shadow_catcher:
        bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 0, 0))
        plane = bpy.context.active_object
        plane.name = "Ground"
        plane.is_shadow_catcher = True
    return sc


def set_anchor(sc, ax, ay, res, mpp=MPP):
    """Dünya orijininin (ayak noktası) görüntüde (ax, ay) pikseline düşmesi için kamerayı kaydır."""
    cam = sc.camera
    cam.data.shift_x = -(ax - res[0] / 2) / res[0]
    cam.data.shift_y = (ay - res[1] / 2) / res[0]


def world_to_px(p, res, anchor, mpp=MPP):
    """Oyun koordinatı (x sağ, y yukarı, z güney) → piksel."""
    x, y, z = p
    right = np.array([1.0, 0, 0])
    up = np.array([0.0, math.cos(PITCH), -math.sin(PITCH)])
    v = np.array([x, y, z])
    return (float(v @ right / mpp + anchor[0]), float(anchor[1] - v @ up / mpp))


def g2b(p):
    """oyun (x, y↑, z güney) → blender (x, y=-z, z=y)"""
    return Vector((p[0], -p[2], p[1]))


# ------------------------------------------------------------------ malzemeler
_mat_cache = {}


def hexrgb(h):
    h = h.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple((x / 12.92) if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c)


def material(name, color, rough=0.7, metal=0.0, kind=None, emit=0.0, sss=0.0, clearcoat=0.0, bump=0.3, scale=60.0):
    """kind: cloth|denim|leather|skin|hair|metal|bark|leaf|stone|rubber|glass|None"""
    key = (name, color, rough, metal, kind, emit)
    if key in _mat_cache:
        return _mat_cache[key]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    n = nt.nodes
    L = nt.links
    bsdf = n["Principled BSDF"]
    rgb = hexrgb(color)
    bsdf.inputs["Base Color"].default_value = (*rgb, 1)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    if sss > 0:
        bsdf.inputs["Subsurface Weight"].default_value = sss
        bsdf.inputs["Subsurface Radius"].default_value = (0.02, 0.008, 0.004)
    if clearcoat > 0:
        bsdf.inputs["Coat Weight"].default_value = clearcoat
    if emit > 0:
        bsdf.inputs["Emission Color"].default_value = (*rgb, 1)
        bsdf.inputs["Emission Strength"].default_value = emit
    if kind in ("cloth", "denim", "leather", "skin", "hair", "bark", "leaf", "stone", "metal", "rubber"):
        tc = n.new("ShaderNodeTexCoord")
        if kind == "cloth":
            tex = n.new("ShaderNodeTexWave")
            tex.inputs["Scale"].default_value = scale * 2.5
            tex.inputs["Distortion"].default_value = 2.0
            src = tex.outputs["Fac"]
            col_var = 0.1
        elif kind == "denim":
            tex = n.new("ShaderNodeTexWave")
            tex.wave_type = "BANDS"
            tex.bands_direction = "DIAGONAL"
            tex.inputs["Scale"].default_value = scale * 3
            tex.inputs["Distortion"].default_value = 3.0
            src = tex.outputs["Fac"]
            col_var = 0.18
        elif kind == "bark":
            tex = n.new("ShaderNodeTexVoronoi")
            tex.inputs["Scale"].default_value = 18
            tex.feature = "DISTANCE_TO_EDGE"
            src = tex.outputs["Distance"]
            col_var = 0.35
        elif kind == "hair":
            tex = n.new("ShaderNodeTexWave")
            tex.wave_type = "RINGS"
            tex.inputs["Scale"].default_value = 30
            tex.inputs["Distortion"].default_value = 8
            src = tex.outputs["Fac"]
            col_var = 0.25
        else:
            tex = n.new("ShaderNodeTexNoise")
            tex.inputs["Scale"].default_value = {"skin": 80, "leather": 50, "leaf": 25, "stone": 12, "metal": 120, "rubber": 90}[kind]
            tex.inputs["Detail"].default_value = 8
            src = tex.outputs["Fac"]
            col_var = {"skin": 0.06, "leather": 0.18, "leaf": 0.35, "stone": 0.3, "metal": 0.06, "rubber": 0.1}[kind]
        L.new(tc.outputs["Object"], tex.inputs["Vector"])
        # renk çeşitliliği
        mix = n.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        mix.blend_type = "MULTIPLY"
        mix.inputs["Factor"].default_value = col_var
        mix.inputs[6].default_value = (*rgb, 1)
        L.new(src, mix.inputs[7])
        L.new(mix.outputs[2], bsdf.inputs["Base Color"])
        bp = n.new("ShaderNodeBump")
        bp.inputs["Strength"].default_value = bump
        bp.inputs["Distance"].default_value = 0.004
        L.new(src, bp.inputs["Height"])
        L.new(bp.outputs["Normal"], bsdf.inputs["Normal"])
    if kind == "glass":
        bsdf.inputs["Roughness"].default_value = 0.05
        bsdf.inputs["Coat Weight"].default_value = 1.0
    _mat_cache[key] = m
    return m


# ------------------------------------------------------------------ geometri
def meta_family(name, mat, resolution=0.012):
    mb = bpy.data.metaballs.new(name)
    mb.resolution = resolution
    mb.render_resolution = resolution
    mb.threshold = 0.6
    ob = bpy.data.objects.new(name, mb)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(mat)
    return mb


def meta_capsule(mb, a, b, r, stiffness=2.0):
    """a, b oyun koordinatı; r yarıçap."""
    A, B = g2b(a), g2b(b)
    d = B - A
    L = d.length
    el = mb.elements.new()
    if L < 1e-5:
        el.type = "BALL"
        el.co = A
        el.radius = r * 1.7
    else:
        el.type = "CAPSULE"
        el.co = (A + B) / 2
        el.size_x = L / 2
        el.radius = r * 1.7
        q = Vector((1, 0, 0)).rotation_difference(d.normalized())
        el.rotation = q
    el.stiffness = stiffness
    return el


def meta_ellipsoid(mb, c, R, radii, stiffness=2.0):
    el = mb.elements.new()
    el.type = "ELLIPSOID"
    el.co = g2b(c)
    el.size_x, el.size_y, el.size_z = radii[0] * 1.74, radii[2] * 1.74, radii[1] * 1.74
    # oyun yerel eksenlerini blender'a çevir: sütunlar x,y,z(oyun) → blender (x, -z, y)
    Rg = np.asarray(R)
    conv = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float)
    Rb = conv @ Rg @ np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float)
    el.rotation = Matrix(Rb.tolist()).to_quaternion()
    el.radius = 1.0
    el.stiffness = stiffness
    return el


def add_mesh_obj(name, mat, verts_faces=None, prim=None, **kw):
    if prim == "sphere":
        bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, radius=kw.get("r", 0.05), location=g2b(kw["c"]))
    elif prim == "cylinder":
        a, b = g2b(kw["a"]), g2b(kw["b"])
        d = b - a
        bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=kw["r"], depth=d.length, location=(a + b) / 2)
        ob = bpy.context.active_object
        ob.rotation_mode = "QUATERNION"
        ob.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
    elif prim == "cube":
        bpy.ops.mesh.primitive_cube_add(size=1, location=g2b(kw["c"]))
        ob = bpy.context.active_object
        hx, hy, hz = kw["half"]
        ob.scale = (hx * 2, hz * 2, hy * 2)
        if "R" in kw:
            conv = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float)
            Rb = conv @ np.asarray(kw["R"]) @ conv.T
            ob.rotation_mode = "QUATERNION"
            ob.rotation_quaternion = Matrix(Rb.tolist()).to_quaternion()
        if kw.get("bevel", 0) > 0:
            mod = ob.modifiers.new("bev", "BEVEL")
            mod.width = kw["bevel"]
            mod.segments = 3
    ob = bpy.context.active_object
    ob.name = name
    ob.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return ob


def render_to(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def clear_objects(keep=("Cam", "Sun", "Fill", "Rim", "Ground")):
    for ob in list(bpy.data.objects):
        if ob.name not in keep:
            bpy.data.objects.remove(ob, do_unlink=True)
    for mb in list(bpy.data.metaballs):
        if mb.users == 0:
            bpy.data.metaballs.remove(mb)
    for me in list(bpy.data.meshes):
        if me.users == 0:
            bpy.data.meshes.remove(me)
