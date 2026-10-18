"""V3 insan modeli: ilkel şekillerin birleşimi → voksel yeniden örgüleme → yumuşatma = heykel gibi temiz gövde.

Metaball yerine: her parça (deri, saç, üst giysi, alt giysi) ayrı birleşik ağ; ayakkabılar profil çıkarımı;
yüz (göz akı + iris, kaş, burun, kulak, dudak), giysi detayları (fermuar, cep, manşet, dikiş, kapüşon),
zombilerde yırtık/kan/pençe/parlayan göz. build_human(J, L, s) → {"body": [...], "arms": [...]}
"""
import math

import bpy
import numpy as np
from mathutils import Matrix, Vector

import blib as B
from blib import g2b, material
from character2 import v3, unit

CONV = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float)  # oyun → blender


def _rotq(R):
    Rb = CONV @ np.asarray(R, float) @ CONV.T
    return Matrix(Rb.tolist()).to_quaternion()


class Part:
    """Aynı malzemeden, birleşik yüzeye dönüşecek ilkel şekiller."""

    def __init__(self, name, mat, voxel=0.0055, smooth=0.55, iters=4):
        self.name, self.mat, self.voxel, self.smooth, self.iters = name, mat, voxel, smooth, iters
        self.objs = []

    def ell(self, c, R, r):
        bpy.ops.mesh.primitive_uv_sphere_add(segments=20, ring_count=12, radius=1.0, location=g2b(c))
        o = bpy.context.active_object
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = _rotq(R)
        o.scale = (r[0], r[2], r[1])  # blender yerel: x, y(=-oyun z), z(=oyun y)
        self.objs.append(o)
        return o

    def cap(self, a, b, r1, r2=None, ends=True):
        r2 = r1 if r2 is None else r2
        A, Bb = g2b(a), g2b(b)
        d = Bb - A
        L = d.length
        if L > 1e-5:
            bpy.ops.mesh.primitive_cone_add(vertices=20, radius1=r1, radius2=r2, depth=L, location=(A + Bb) / 2)
            o = bpy.context.active_object
            o.rotation_mode = "QUATERNION"
            o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
            self.objs.append(o)
        if ends:
            for p, r in ((A, r1), (Bb, r2)):
                bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=r, location=p)
                self.objs.append(bpy.context.active_object)

    def box(self, c, R, h, bevel=0.0):
        bpy.ops.mesh.primitive_cube_add(size=2, location=g2b(c))
        o = bpy.context.active_object
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = _rotq(R)
        o.scale = (h[0], h[2], h[1])
        self.objs.append(o)
        return o

    def fuse(self):
        if not self.objs:
            return None
        bpy.ops.object.select_all(action="DESELECT")
        for o in self.objs:
            o.select_set(True)
        bpy.context.view_layer.objects.active = self.objs[0]
        if len(self.objs) > 1:
            bpy.ops.object.join()
        ob = bpy.context.active_object
        ob.name = self.name
        md = ob.modifiers.new("rm", "REMESH")
        md.mode = "VOXEL"
        md.voxel_size = self.voxel
        md.use_smooth_shade = True
        if self.smooth > 0:
            sm = ob.modifiers.new("sm", "SMOOTH")
            sm.factor = self.smooth
            sm.iterations = self.iters
        ob.data.materials.clear()
        ob.data.materials.append(self.mat)
        return ob


def solid_obj(name, mat, prim, **kw):
    """Yumuşatılmayan keskin parça (göz, toka, fermuar...)."""
    if prim == "sphere":
        bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=kw.get("r", 0.01), location=g2b(kw["c"]))
        o = bpy.context.active_object
        if "scale" in kw:
            o.scale = kw["scale"]
        if "R" in kw:
            o.rotation_mode = "QUATERNION"
            o.rotation_quaternion = _rotq(kw["R"])
    elif prim == "box":
        bpy.ops.mesh.primitive_cube_add(size=2, location=g2b(kw["c"]))
        o = bpy.context.active_object
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = _rotq(kw["R"])
        h = kw["h"]
        o.scale = (h[0], h[2], h[1])
        if kw.get("bevel"):
            m = o.modifiers.new("b", "BEVEL")
            m.width = kw["bevel"]
            m.segments = 2
    elif prim == "cyl":
        a, b = g2b(kw["a"]), g2b(kw["b"])
        d = b - a
        bpy.ops.mesh.primitive_cylinder_add(vertices=14, radius=kw["r"], depth=max(d.length, 1e-4), location=(a + b) / 2)
        o = bpy.context.active_object
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized() if d.length > 1e-6 else Vector((0, 0, 1)))
    elif prim == "torus":
        bpy.ops.mesh.primitive_torus_add(major_radius=kw["R0"], minor_radius=kw["r"], location=g2b(kw["c"]), major_segments=24, minor_segments=8)
        o = bpy.context.active_object
        o.rotation_mode = "QUATERNION"
        o.rotation_quaternion = _rotq(kw["R"])
        if "scale" in kw:
            o.scale = kw["scale"]
    o.name = name
    o.data.materials.append(mat)
    bpy.ops.object.shade_smooth()
    return o


def frame_from(z_axis, up):
    z = unit(np.asarray(z_axis, float))
    x = unit(np.cross(np.asarray(up, float), z))
    y = np.cross(z, x)
    return np.stack([x, y, z], axis=1)


def shoe(pre, J, s, upper_m, sole_m, accent_m=None, boot=False, lace_m=None):
    """Profil çıkarımlı sneaker / bot (ayak çerçevesinde)."""
    fwd = J[pre + "_toe"] - J[pre + "_heel"]
    R = frame_from(fwd, J["B"] @ v3(0, 1, 0))
    c = (J[pre + "_toe"] + J[pre + "_heel"]) / 2 + R @ v3(0, -0.01, 0.0)
    L, hw = 0.135, 0.048
    top = 0.17 if boot else 0.085
    prof = [(-L, 0.0), (-L - 0.008, 0.035), (-L + 0.01, top), (-0.02, top * (0.95 if boot else 0.85)), (0.05, 0.07 if boot else 0.06),
            (L - 0.02, 0.05), (L + 0.005, 0.03), (L, 0.0)]
    objs = []

    def extr(profile, w, mat, y0=0.0, bev=0.012, name="shoe"):
        n = len(profile)
        verts = []
        for side in (-1, 1):
            for z, y in profile:
                p = c + R @ v3(side * w, y + y0, z) * s
                verts.append(tuple(g2b(p)))
        faces = [tuple(range(n))[::-1], tuple(range(n, 2 * n))]
        for i in range(n):
            j = (i + 1) % n
            faces.append((i, j, n + j, n + i))
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        me.update()
        ob = bpy.data.objects.new(name, me)
        bpy.context.scene.collection.objects.link(ob)
        ob.data.materials.append(mat)
        md = ob.modifiers.new("b", "BEVEL")
        md.width = bev * s
        md.segments = 3
        md.limit_method = "ANGLE"
        sub = ob.modifiers.new("s", "SUBSURF")
        sub.levels = 1
        sub.render_levels = 1
        for p in me.polygons:
            p.use_smooth = True
        return ob

    objs.append(extr(prof, hw, upper_m, 0.012))
    objs.append(extr([(-L - 0.012, 0.0), (-L - 0.012, 0.022), (L + 0.012, 0.022), (L + 0.012, 0.0)], hw + 0.006, sole_m, 0.0, 0.006, "sole"))
    if accent_m is not None:
        objs.append(extr([(-0.06, 0.03), (0.0, 0.07), (0.04, 0.06), (-0.02, 0.025)], hw + 0.002, accent_m, 0.012, 0.004, "swoosh"))
    if lace_m is not None:
        for k in range(3 if not boot else 4):
            z = 0.02 + k * 0.022 - (0.03 if boot else 0.0)
            yy = (0.075 if not boot else 0.12) - k * 0.008
            objs.append(solid_obj("lace", lace_m, "box", c=c + R @ v3(0, yy + 0.012, z) * s, R=R, h=(0.03 * s, 0.004 * s, 0.005 * s)))
    return objs


# =========================================================================== görünümler
LOOKS = {
    "kaan": dict(skin="#d9a07a", top=("#e8952a", "cloth"), bottom=("#2f4c7a", "denim"), hair="#2e1e14", hair_style="fade",
                 hoodie=True, strings="#f2f2ea", wraps="#f0f0ea", shoe="#f2f2ee", sole="#e8e6e0", accent="#d0302a", brow="#24180f"),
    "nova": dict(skin="#e8b898", top=("#23262e", "cloth"), bottom=("#3e434e", "cloth"), hair="#b8c4dc", hair_style="bob",
                 female=True, neon="#3fe0ff", goggles=True, cargo=True, shoe="#2a2a2e", sole="#3a3a40", boot=True, collar=True),
    "rady": dict(skin="#c88e6a", top=("#3c3482", "cloth"), bottom=("#26252f", "cloth"), hair="#211812", hair_style="short",
                 coat=True, hood_up=True, beard="#2a1e16", trim="#e8c45a", rune="#7ff0ff", shoe="#4a3426", sole="#2a2420", boot=True),
    "elif": dict(skin="#efc2a2", top=("#f4f4f6", "cloth"), bottom=("#4c7a56", "cloth"), hair="#8a3420", hair_style="ponytail",
                 female=True, medic="#d8343c", shoe="#f2f2ee", sole="#e0ded8", accent="#d8343c", collar=True),
    "soldier": dict(skin="#c89a74", top=("#5a6a3c", "cloth"), bottom=("#5a6a3c", "cloth"), hair="#2a2018", hair_style="short",
                    vest="#3c4430", helmet="#4d5a36", shoe="#3a2c22", sole="#1e1a16", boot=True, cargo=True),
    "merchant": dict(skin="#d09a70", top=("#c8b088", "cloth"), bottom=("#5a5a66", "cloth"), hair="#3a2a1c", hair_style="short",
                     beard="#3a2a1c", apron="#8c5a3c", cap="#a03028", shoe="#4a3426", sole="#2a2420"),
    # zombiler
    "walker": dict(skin="#8fa878", top=("#6e5a8a", "cloth"), bottom=("#5e5e6c", "cloth"), hair="#34342c", hair_style="messy",
                   zombie=True, torn=True, short_sleeve=True, eye="#ffe066", shoe="#3a3a40", sole="#22222a", barefoot_r=True),
    "runner": dict(skin="#bdb4a4", top=("#a8342c", "cloth"), bottom=("#3e5276", "denim"), hair="#2a2a24", hair_style="messy",
                   zombie=True, torn=True, short_sleeve=True, eye="#ff3a2a", thin=True, shoe="#e6e6e0", sole="#c8c8c0"),
    "dark": dict(skin="#9a96aa", top=("#3a3550", "cloth"), bottom=("#2e2a3e", "cloth"), hair=None, hood_up=True, zombie=True,
                 eye="#c45cff", shoe="#26242e", sole="#18161e", veins="#c45cff"),
    "toxic": dict(skin="#9cc044", top=("#d8b63a", "cloth"), bottom=("#c8a632", "cloth"), hair=None, zombie=True, gasmask=True,
                  pustules=True, eye="#c6ff3a", shoe="#3a3a2a", sole="#22221a", boot=True, hazmat=True),
    "tank": dict(skin="#84a06e", top=None, bottom=("#3e5276", "denim"), hair=None, zombie=True, eye="#ff3a2a", bulk=1.55,
                 scars=True, shoe="#3a2c22", sole="#1e1a16", boot=True, chains=True),
    "mystery": dict(skin="#cfc8ba", top=("#17151e", "cloth"), bottom=("#17151e", "cloth"), hair=None, coat=True, long_coat=True,
                    hood_up=True, zombie=True, mask=True, eye="#ff3a2a", shoe="#17151e", sole="#0e0e0e", boot=True),
    "titan": dict(skin="#8a8698", top=None, bottom=("#5a3a22", "leather"), hair=None, zombie=True, eye="#ff3a2a", bulk=1.6,
                  armor=True, shoe="#2a2a2e", sole="#111111", boot=True, horns=True),
}
SCALE = {"tank": 1.28, "titan": 2.3, "mystery": 1.05}


# =========================================================================== yapıcı
def build_human(J, L, scale=1.0):
    s = scale
    S, H = J["S"], J["H"]
    fem = L.get("female", False)
    b = L.get("bulk", 1.0) * (0.85 if L.get("thin") else 1.0)
    P = lambda p: np.asarray(p, float) * s
    zombie = L.get("zombie", False)
    before = set(bpy.data.objects.keys())

    skin_m = material("skin" + L["skin"], L["skin"], 0.5, kind="skin", sss=0.3, bump=0.15)
    skin = Part("Skin", skin_m, 0.0045 * s, 0.5, 4)
    head = J["head"]
    hp = lambda x, y, z: P(head + H @ v3(x, y, z))
    sp = lambda x, y, z: P(J["pelvis"] + S @ v3(x, y, z))

    # ---- baş: kafatası + çene + burun + kulaklar + boyun
    skin.ell(hp(0, 0.01, -0.005), H, (0.088 * s, 0.108 * s, 0.1 * s))
    skin.ell(hp(0, -0.055, 0.03), H, ((0.066 if fem else 0.074) * s, 0.05 * s, 0.068 * s))
    skin.ell(hp(0, -0.008, 0.094), H, (0.014 * s, 0.024 * s, 0.018 * s))  # burun
    for sx in (-1, 1):
        skin.ell(hp(sx * 0.088, -0.005, -0.005), H, (0.012 * s, 0.026 * s, 0.018 * s))  # kulak
        skin.ell(hp(sx * 0.045, -0.045, 0.06), H, (0.03 * s, 0.022 * s, 0.025 * s))  # elmacık
    skin.cap(P(J["neck"] + S @ v3(0, -0.07, 0)), hp(0, -0.07, -0.01), 0.048 * s * min(b, 1.35))
    # ---- gövde (giysi altında da var: kollar arası ve boyun dibi için)
    sw = (0.125 if fem else 0.15) * b
    skin.ell(sp(0, 0.36, -0.005), S, ((sw + 0.035) * s, 0.13 * s, 0.1 * s * b))
    skin.ell(sp(0, 0.18, 0), S, ((sw - 0.015) * s, 0.14 * s, 0.09 * s * b))
    skin.ell(sp(0, 0.02, 0), S, ((sw + (0.025 if fem else 0.0)) * s, 0.1 * s, 0.095 * s * b))
    if fem:
        for sx in (-1, 1):
            skin.ell(sp(sx * 0.06, 0.33, 0.07), S, (0.055 * s, 0.05 * s, 0.045 * s))
    # ---- bacaklar
    for pre in ("l", "r"):
        skin.cap(P(J[pre + "_hip"]), P(J[pre + "_knee"]), 0.082 * s * b, 0.058 * s * b)
        skin.cap(P(J[pre + "_knee"]), P(J[pre + "_ankle"]), 0.056 * s * b, 0.038 * s * b)
        if L.get("barefoot_r") and pre == "r":
            fwd = J["r_toe"] - J["r_heel"]
            Rf = frame_from(fwd, J["B"] @ v3(0, 1, 0))
            skin.ell(P((J["r_toe"] + J["r_heel"]) / 2 + Rf @ v3(0, 0.02, 0.02)), Rf, (0.045 * s, 0.028 * s, 0.11 * s))
    if L.get("pustules"):
        for off, r in (((0.08, 0.38, 0.1), 0.03), ((-0.1, 0.2, 0.1), 0.026), ((0.12, 0.3, 0.02), 0.024)):
            skin.ell(sp(*off), S, (r * s, r * s, r * s))
    skin.fuse()

    # ---- üst giysi (gövde kabuğu)
    top_m = None
    if L.get("top"):
        tc, tk = L["top"]
        top_m = material("top" + tc, tc, 0.85, kind=tk)
        top = Part("Top", top_m, 0.0055 * s, 0.6, 3)
        top.ell(sp(0, 0.37, -0.005), S, ((sw + 0.05) * s, 0.15 * s, 0.115 * s * b))
        top.ell(sp(0, 0.2, 0), S, ((sw + 0.005) * s, 0.15 * s, 0.105 * s * b))
        top.ell(sp(0, 0.06, 0), S, ((sw + 0.012) * s, 0.09 * s, 0.105 * s * b))
        if L.get("coat"):
            end = -0.82 if L.get("long_coat") else -0.42
            top.cap(sp(0, 0.05, -0.005), sp(0, end, -0.02), 0.17 * s * b, (0.27 if L.get("long_coat") else 0.21) * s * b, ends=False)
        if L.get("hoodie"):
            top.ell(sp(0, 0.47, -0.09), S, (0.13 * s, 0.07 * s, 0.08 * s))  # kapüşon (arkada)
            top.box(sp(0, 0.12, 0.105 * b), S, (0.11 * s, 0.05 * s, 0.012 * s))  # kanguru cep
        if L.get("collar"):
            top.ell(P(J["neck"] + S @ v3(0, -0.02, 0)), S, (0.07 * s, 0.035 * s, 0.065 * s))
        if L.get("hazmat"):
            top.ell(sp(0, 0.0, 0), S, ((sw + 0.03) * s, 0.12 * s, 0.11 * s))
        top.fuse()
    # ---- alt giysi
    bc, bk = L["bottom"]
    bot_m = material("bot" + bc, bc, 0.8, kind=bk)
    bot = Part("Bottom", bot_m, 0.005 * s, 0.6, 3)
    bot.ell(sp(0, 0.0, 0), S, ((0.15 + (0.02 if fem else 0)) * s * b, 0.09 * s, 0.105 * s * b))
    for pre in ("l", "r"):
        bot.cap(P(J[pre + "_hip"]), P(J[pre + "_knee"]), 0.09 * s * b, 0.068 * s * b)
        bot.cap(P(J[pre + "_knee"]), P(J[pre + "_ankle"] + (J[pre + "_knee"] - J[pre + "_ankle"]) * (0.0 if not L.get("shorts") else 0.6)),
                0.066 * s * b, (0.052 if L.get("boot") else 0.056) * s * b)
        if L.get("cargo"):
            side = 1 if pre == "l" else -1
            mid = J[pre + "_hip"] * 0.45 + J[pre + "_knee"] * 0.55
            bot.box(P(mid + S @ v3(side * 0.085, 0, 0)), S, (0.02 * s, 0.065 * s, 0.05 * s))
    bot.fuse()

    # ---- ayakkabılar
    sole_m = material("sole" + L["sole"], L["sole"], 0.8, kind="rubber")
    up_m = material("shoe" + L["shoe"], L["shoe"], 0.55, kind="leather")
    acc_m = material("acc" + L["accent"], L["accent"], 0.5, kind="leather") if L.get("accent") else None
    lace_m = material("lace", "#f4f4f0", 0.8)
    for pre in ("l", "r"):
        if L.get("barefoot_r") and pre == "r":
            continue
        shoe(pre, J, s, up_m, sole_m, acc_m, L.get("boot", False), lace_m if not zombie else None)

    # ---- saç
    hs = L.get("hair_style") if L.get("hair") else None
    if hs and not L.get("hood_up"):
        hair_m = material("hair" + L["hair"], L["hair"], 0.42, kind="hair", bump=0.8)
        hr = Part("Hair", hair_m, 0.004 * s, 0.35, 2)
        if hs == "fade":
            hr.ell(hp(0, 0.045, -0.012), H, (0.09 * s, 0.075 * s, 0.098 * s))
            for k in range(6):
                hr.ell(hp(-0.05 + k * 0.02, 0.105, 0.04 + (k % 2) * 0.01), H, (0.022 * s, 0.018 * s, 0.03 * s))
        elif hs == "short":
            hr.ell(hp(0, 0.04, -0.015), H, (0.093 * s, 0.08 * s, 0.1 * s))
            hr.ell(hp(0, 0.085, 0.06), H, (0.07 * s, 0.03 * s, 0.03 * s))
        elif hs == "bob":
            hr.ell(hp(0, 0.03, -0.02), H, (0.1 * s, 0.09 * s, 0.105 * s))
            for sx in (-1, 1):
                hr.ell(hp(sx * 0.085, -0.04, -0.01), H, (0.03 * s, 0.07 * s, 0.07 * s))
            hr.ell(hp(0.02, 0.07, 0.075), H, (0.075 * s, 0.035 * s, 0.03 * s))
        elif hs == "ponytail":
            hr.ell(hp(0, 0.035, -0.015), H, (0.095 * s, 0.085 * s, 0.103 * s))
            hr.ell(hp(0, 0.08, 0.07), H, (0.08 * s, 0.03 * s, 0.03 * s))
            hr.cap(hp(0, 0.04, -0.1), hp(0, -0.1, -0.15), 0.035 * s, 0.022 * s)
            hr.cap(hp(0, -0.1, -0.15), hp(0, -0.2, -0.13), 0.022 * s, 0.012 * s)
        elif hs == "messy":
            hr.ell(hp(0, 0.035, -0.015), H, (0.09 * s, 0.075 * s, 0.098 * s))
            for k in range(7):
                a = k * 0.9
                hr.ell(hp(math.cos(a) * 0.06, 0.08 + 0.02 * math.sin(a * 2), math.sin(a) * 0.06), H, (0.025 * s, 0.03 * s, 0.025 * s))
        hr.fuse()
    if L.get("beard"):
        bm = material("beard" + L["beard"], L["beard"], 0.5, kind="hair", bump=0.8)
        bd = Part("Beard", bm, 0.004 * s, 0.4, 2)
        bd.ell(hp(0, -0.075, 0.05), H, (0.068 * s, 0.045 * s, 0.05 * s))
        bd.ell(hp(0, -0.03, 0.085), H, (0.035 * s, 0.01 * s, 0.012 * s))
        bd.fuse()
    if L.get("hood_up"):
        hc = L["top"][0] if L.get("top") else "#222222"
        hdm = material("hood" + hc, hc, 0.85, kind="cloth")
        hd = Part("Hood", hdm, 0.005 * s, 0.5, 3)
        hd.ell(hp(0, 0.02, -0.035), H, (0.112 * s, 0.13 * s, 0.12 * s))
        hd.ell(hp(0, -0.1, -0.06), H, (0.11 * s, 0.06 * s, 0.09 * s))
        hd.fuse()

    # ---- yüz
    eye_col = L.get("eye")
    white = material("eyewhite", "#f2efe8", 0.25, clearcoat=1.0)
    iris = material("iris_" + (eye_col or "dark"), eye_col or "#2a1e16", 0.2, emit=8.0 if eye_col else 0.0, clearcoat=1.0)
    if not L.get("mask") and not L.get("gasmask"):
        for sx in (-1, 1):
            if eye_col:
                solid_obj("eye", iris, "sphere", c=hp(sx * 0.034, 0.012, 0.086), r=0.012 * s)
            else:
                solid_obj("eyew", white, "sphere", c=hp(sx * 0.034, 0.012, 0.083), r=0.0125 * s)
                solid_obj("iris", iris, "sphere", c=hp(sx * 0.034, 0.012, 0.093), r=0.0065 * s)
            browc = L.get("brow") or L.get("hair") or "#2a2a2a"
            if not zombie:
                o = solid_obj("brow", material("brow" + browc, browc, 0.6), "box", c=hp(sx * 0.036, 0.04, 0.092),
                              R=H, h=(0.02 * s, 0.0045 * s, 0.006 * s))
        lip = material("lip", "#7a3a34" if not zombie else "#2a0a0a", 0.4)
        solid_obj("mouth", lip, "box", c=hp(0, -0.05, 0.088), R=H, h=(0.022 * s, (0.004 if not zombie else 0.012) * s, 0.004 * s))
        if zombie:
            solid_obj("teeth", material("teeth", "#d8d0b8", 0.4), "box", c=hp(0, -0.044, 0.09), R=H, h=(0.018 * s, 0.003 * s, 0.003 * s))

    # ---- detaylar
    if L.get("hoodie"):
        sm = material("str" + L["strings"], L["strings"], 0.8)
        for sx in (-0.035, 0.035):
            solid_obj("str", sm, "cyl", a=P(J["neck"] + S @ v3(sx, -0.03, 0.1)), b=P(J["neck"] + S @ v3(sx * 1.1, -0.16, 0.118)), r=0.005 * s)
    if L.get("collar") and top_m is not None:
        solid_obj("zip", material("zip", "#c8c8c8", 0.3, metal=1.0), "box", c=sp(0, 0.28, 0.118 * b), R=S, h=(0.005 * s, 0.17 * s, 0.003 * s))
    if L.get("neon"):
        nm = material("neon" + L["neon"], L["neon"], 0.3, emit=5.0)
        for sx in (-1, 1):
            solid_obj("neon", nm, "box", c=sp(sx * 0.075, 0.3, 0.117 * b), R=S, h=(0.006 * s, 0.12 * s, 0.003 * s))
        solid_obj("neonb", nm, "box", c=sp(0, 0.05, 0.11 * b), R=S, h=(0.15 * s, 0.006 * s, 0.003 * s))
    if L.get("goggles"):
        gm = material("gog", "#2a2d36", 0.3, metal=0.8)
        solid_obj("gogb", gm, "box", c=hp(0, 0.068, 0.05), R=H, h=(0.098 * s, 0.014 * s, 0.03 * s), bevel=0.005 * s)
        for sx in (-1, 1):
            solid_obj("lens", material("lens", "#5ef0ff", 0.05, emit=3.0, clearcoat=1.0), "sphere", c=hp(sx * 0.04, 0.07, 0.08), r=0.017 * s,
                      scale=(1, 0.5, 1))
    if L.get("trim"):
        tm = material("trim", L["trim"], 0.25, metal=1.0)
        solid_obj("trim", tm, "box", c=sp(0, 0.12, 0.118 * b), R=S, h=(0.01 * s, 0.33 * s, 0.004 * s))
        solid_obj("belt", tm, "torus", c=sp(0, 0.04, 0), R=S, R0=0.15 * s * b, r=0.012 * s, scale=(1.0, 0.75, 1.0))
        solid_obj("rune", material("rune", L["rune"], 0.2, emit=6.0), "sphere", c=sp(0.07, 0.38, 0.118 * b), r=0.022 * s)
    if L.get("medic"):
        rm = material("medic", L["medic"], 0.7, kind="cloth")
        for z in (0.12, -0.12):
            solid_obj("cA", rm, "box", c=sp(0.06, 0.36, z * b), R=S, h=(0.03 * s, 0.009 * s, 0.003 * s))
            solid_obj("cB", rm, "box", c=sp(0.06, 0.36, z * b), R=S, h=(0.009 * s, 0.03 * s, 0.003 * s))
    if L.get("vest"):
        vm = material("vest", L["vest"], 0.8, kind="cloth")
        solid_obj("vest", vm, "box", c=sp(0, 0.28, 0), R=S, h=(0.185 * s, 0.17 * s, 0.13 * s), bevel=0.05 * s)
        for k in range(3):
            solid_obj("pouch", vm, "box", c=sp(-0.1 + k * 0.1, 0.18, 0.135), R=S, h=(0.04 * s, 0.04 * s, 0.025 * s), bevel=0.01 * s)
    if L.get("helmet"):
        solid_obj("helm", material("helm", L["helmet"], 0.5, kind="metal"), "sphere", c=hp(0, 0.04, -0.005), r=0.122 * s, scale=(1, 1, 0.85))
    if L.get("apron"):
        solid_obj("apron", material("apron", L["apron"], 0.7, kind="leather"), "box", c=sp(0, 0.05, 0.11 * b), R=S, h=(0.15 * s, 0.3 * s, 0.01 * s), bevel=0.008 * s)
    if L.get("cap"):
        cm = material("cap", L["cap"], 0.8, kind="cloth")
        solid_obj("cap", cm, "sphere", c=hp(0, 0.055, -0.005), r=0.1 * s, scale=(1, 1, 0.7))
        solid_obj("visor", cm, "box", c=hp(0, 0.07, 0.11), R=H, h=(0.075 * s, 0.006 * s, 0.05 * s), bevel=0.004 * s)
    if L.get("torn"):
        blood = material("blood", "#5a1014", 0.35, clearcoat=0.7)
        solid_obj("bl", blood, "box", c=sp(0.06, 0.22, 0.112 * b), R=S, h=(0.05 * s, 0.07 * s, 0.003 * s), bevel=0.002)
        solid_obj("bl2", blood, "box", c=hp(0.02, -0.08, 0.075), R=H, h=(0.025 * s, 0.02 * s, 0.003 * s))
        solid_obj("bl3", blood, "box", c=sp(-0.05, -0.05, 0.11 * b), R=S, h=(0.04 * s, 0.03 * s, 0.003 * s))
    if L.get("veins"):
        vm = material("vein", L["veins"], 0.3, emit=3.0)
        for k in range(3):
            solid_obj("vein", vm, "box", c=hp(-0.03 + k * 0.03, -0.02 - k * 0.01, 0.08), R=H, h=(0.003 * s, 0.025 * s, 0.003 * s))
    if L.get("pustules"):
        pm = material("pus", "#c6ff3a", 0.2, emit=2.5)
        for off, r in (((0.0, 0.3, -0.11), 0.03), ((-0.12, 0.34, 0.06), 0.022)):
            solid_obj("pu", pm, "sphere", c=sp(*off), r=r * s)
    if L.get("gasmask"):
        gm = material("gasm", "#2a2a2a", 0.5, kind="rubber")
        solid_obj("gmface", gm, "sphere", c=hp(0, -0.005, 0.06), r=0.08 * s, scale=(1, 0.7, 1))
        solid_obj("gmf", gm, "cyl", a=hp(0, -0.045, 0.1), b=hp(0, -0.07, 0.15), r=0.032 * s)
        solid_obj("gmc", material("gmc", "#6a6a6a", 0.3, metal=0.8), "cyl", a=hp(0, -0.07, 0.15), b=hp(0, -0.075, 0.165), r=0.036 * s)
        for sx in (-1, 1):
            solid_obj("gl", material("gml", "#b8ff4a", 0.05, emit=2.0, clearcoat=1.0), "sphere", c=hp(sx * 0.038, 0.018, 0.1), r=0.021 * s, scale=(1, 0.5, 1))
    if L.get("mask"):
        mm = material("mask", "#ece6dc", 0.25, clearcoat=0.8)
        solid_obj("mask", mm, "sphere", c=hp(0, 0.0, 0.05), r=0.085 * s, scale=(0.95, 0.6, 1.15))
        for sx in (-1, 1):
            solid_obj("me", iris, "sphere", c=hp(sx * 0.032, 0.015, 0.1), r=0.011 * s)
        solid_obj("msm", material("msm", "#1a1a1a", 0.4), "box", c=hp(0, -0.05, 0.1), R=H, h=(0.03 * s, 0.003 * s, 0.003 * s))
    if L.get("armor"):
        am = material("armor", "#5e6070", 0.28, metal=1.0, kind="metal")
        rust = material("rust", "#7a4a2a", 0.9, kind="stone")
        for pre, side in (("l", 1), ("r", -1)):
            solid_obj("pad", am, "sphere", c=P(J[pre + "_shoulder"] + S @ v3(side * 0.03, 0.05, 0)), r=0.11 * s, scale=(1.1, 1.0, 0.9))
            for k in range(3):
                solid_obj("spike", am, "cyl", a=P(J[pre + "_shoulder"] + S @ v3(side * (0.02 + k * 0.04), 0.13, 0)),
                          b=P(J[pre + "_shoulder"] + S @ v3(side * (0.03 + k * 0.045), 0.22, 0)), r=0.012 * s)
        solid_obj("plate", am, "box", c=sp(0, 0.3, 0.1 * b), R=S, h=(0.17 * s, 0.14 * s, 0.03 * s), bevel=0.03 * s)
        solid_obj("belt", rust, "torus", c=sp(0, 0.03, 0), R=S, R0=0.165 * s * b, r=0.025 * s, scale=(1.0, 0.75, 1.0))
    if L.get("horns"):
        hm = material("horn", "#3a3430", 0.4)
        for sx in (-1, 1):
            solid_obj("horn", hm, "cyl", a=hp(sx * 0.06, 0.08, 0.0), b=hp(sx * 0.12, 0.17, -0.03), r=0.018 * s)
    if L.get("chains"):
        cm = material("chain", "#9a9ca6", 0.2, metal=1.0)
        solid_obj("chain", cm, "cyl", a=P(J["l_shoulder"] + S @ v3(0, 0, 0.12)), b=sp(-0.12, 0.05, 0.13), r=0.012 * s)
    if L.get("scars"):
        solid_obj("sc", material("blood", "#5a1014", 0.35, clearcoat=0.7), "box", c=sp(-0.05, 0.3, 0.11 * b), R=S, h=(0.08 * s, 0.008 * s, 0.003 * s))

    body_objs = [bpy.data.objects[k] for k in bpy.data.objects.keys() if k not in before]

    # =========================================================== kollar (ayrı katman)
    before2 = set(bpy.data.objects.keys())
    arm_skin = Part("ArmSkin", skin_m, 0.0045 * s, 0.5, 3)
    sleeve = Part("Sleeve", top_m, 0.005 * s, 0.55, 3) if top_m is not None else None
    for pre in ("l", "r"):
        sh, el, wr, fi = J[pre + "_shoulder"], J[pre + "_elbow"], J[pre + "_wrist"], J[pre + "_fist"]
        if sleeve is not None and not L.get("short_sleeve"):
            sleeve.cap(P(sh), P(el), 0.075 * s * b, 0.062 * s * b)
            sleeve.cap(P(el), P(wr - (wr - el) * 0.08), 0.06 * s * b, 0.05 * s * b)
        elif sleeve is not None:
            mid = sh * 0.55 + el * 0.45
            sleeve.cap(P(sh), P(mid), 0.078 * s * b, 0.07 * s * b)
            arm_skin.cap(P(mid), P(el), 0.055 * s * b, 0.046 * s * b)
        else:
            arm_skin.cap(P(sh), P(el), 0.068 * s * b, 0.052 * s * b)
        arm_skin.cap(P(el), P(wr), 0.045 * s * b, 0.034 * s * b)
        # yumruk: avuç + parmak boğumları + başparmak
        fd = unit(np.asarray(J[pre + "_fdir"], float))
        Rh = frame_from(fd, S @ v3(0, 1, 0))
        arm_skin.ell(P(fi), Rh, (0.038 * s * b, 0.03 * s * b, 0.045 * s * b))
        side = 1 if pre == "l" else -1
        arm_skin.ell(P(fi + Rh @ v3(side * -0.02, 0.02, 0.0)), Rh, (0.014 * s, 0.014 * s, 0.025 * s))
        if L.get("zombie"):
            claw = material("claw", "#2a2420", 0.3)
            for k in range(3):
                solid_obj("claw", claw, "cyl", a=P(fi + Rh @ v3(-0.02 + k * 0.02, -0.01, 0.04)), b=P(fi + Rh @ v3(-0.02 + k * 0.02, -0.03, 0.065)), r=0.004 * s)
        if L.get("wraps"):
            solid_obj("wrap", material("wrap", L["wraps"], 0.9, kind="cloth"), "torus", c=P(wr + fd * 0.01), R=Rh, R0=0.034 * s, r=0.012 * s)
        if sleeve is not None and not L.get("short_sleeve"):
            solid_obj("cuff", top_m, "torus", c=P(wr - (wr - el) * 0.08), R=frame_from(wr - el, S @ v3(0, 1, 0)), R0=0.046 * s * b, r=0.01 * s)
        if L.get("neon") and pre == "l":
            solid_obj("neonarm", material("neon" + L["neon"], L["neon"], 0.3, emit=5.0), "cyl",
                      a=P(sh + (el - sh) * 0.15 + S @ v3(0.07 * b, 0, 0)), b=P(el + S @ v3(0.062 * b, 0, 0)), r=0.008 * s)
        if L.get("medic") and pre == "l":
            solid_obj("band", material("medic", L["medic"], 0.7, kind="cloth"), "torus", c=P(sh * 0.55 + el * 0.45),
                      R=frame_from(el - sh, S @ v3(0, 1, 0)), R0=0.074 * s, r=0.014 * s)
    arm_skin.fuse()
    if sleeve is not None:
        sleeve.fuse()
    arm_objs = [bpy.data.objects[k] for k in bpy.data.objects.keys() if k not in before2]
    return {"body": body_objs, "arms": arm_objs}
