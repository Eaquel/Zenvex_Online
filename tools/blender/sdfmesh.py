"""SDF sahnelerini (assetgen/*.py) marching cubes ile Blender mesh'ine çevirir ve otomatik kadrajla render eder."""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assetgen"))
import bpy
import numpy as np
from mathutils import Vector
from skimage.measure import marching_cubes

import blib as B

KIND = {"cloth": "cloth", "denim": "denim", "leather": "leather", "bark": "bark", "leaf": "leaf", "stone": "stone",
        "metal": "metal", "skin": "skin", "hair": "hair", "glass": "glass", "rust": "stone", "fur": "hair"}


def blender_mat(name, m):
    if m.emissive:
        return B.material(name, m.base, 0.3, emit=4.0)
    kind = KIND.get(m.texture)
    metal = 1.0 if (m.texture == "metal" and m.spec >= 0.6) else 0.0
    rough = 0.25 if m.spec >= 0.8 else (0.45 if m.spec >= 0.4 else 0.8)
    if kind == "glass":
        return B.material(name, m.base, 0.05, kind="glass", clearcoat=1.0)
    return B.material(name, m.base, rough, metal=metal, kind=kind, sss=0.2 if kind == "skin" else 0.0)


def _eval_grid(fn, xs, ys, zs, chunk=600000):
    X, Y, Z = np.meshgrid(xs, ys, zs, indexing="ij")
    pts = np.stack([X.ravel(), Y.ravel(), Z.ravel()], axis=1)
    out = np.empty(pts.shape[0], np.float32)
    for i in range(0, pts.shape[0], chunk):
        out[i:i + chunk] = fn(pts[i:i + chunk])
    return out.reshape(X.shape)


def scene_to_objects(scene, mats, box=((-3, 0, -3), (3, 6, 3)), voxel=None, max_cells=200, prefix="m"):
    """scene: [(mat_adı, sdf)] oyun koordinatında. Her malzeme ayrı mesh olur."""
    fns = [f for _, f in scene]

    def scene_min(p):
        return np.min(np.stack([f(p) for f in fns]), axis=0)

    lo = np.array(box[0], float)
    hi = np.array(box[1], float)
    # kaba doluluk taraması
    step = max((hi - lo).max() / 40, 0.02)
    xs, ys, zs = [np.arange(lo[i], hi[i] + step, step) for i in range(3)]
    g = _eval_grid(scene_min, xs, ys, zs)
    occ = np.argwhere(g < step * 1.5)
    if occ.size == 0:
        return []
    mn = np.array([xs[occ[:, 0].min()], ys[occ[:, 1].min()], zs[occ[:, 2].min()]]) - step * 2
    mx = np.array([xs[occ[:, 0].max()], ys[occ[:, 1].max()], zs[occ[:, 2].max()]]) + step * 2
    if voxel is None:
        voxel = max((mx - mn).max() / max_cells, 0.004)
    xs, ys, zs = [np.arange(mn[i], mx[i] + voxel, voxel) for i in range(3)]
    # malzemelere göre grupla
    groups = {}
    for i, (m, _) in enumerate(scene):
        groups.setdefault(m, []).append(i)
    allv = [_eval_grid(f, xs, ys, zs) for f in fns]
    stack = np.stack(allv)
    objs = []
    for m, idx in groups.items():
        own = np.min(stack[idx], axis=0)
        others_idx = [i for i in range(len(fns)) if i not in idx]
        if others_idx:
            oth = np.min(stack[others_idx], axis=0)
            field = np.maximum(own, own - oth)
        else:
            field = own
        if field.min() >= 0 or field.max() <= 0:
            continue
        try:
            verts, faces, _, _ = marching_cubes(field, 0.0, spacing=(voxel, voxel, voxel))
        except Exception:
            continue
        verts = verts + mn
        bverts = [(v[0], -v[2], v[1]) for v in verts]  # oyun → blender
        me = bpy.data.meshes.new(prefix + m)
        me.from_pydata(bverts, [], [tuple(f[::-1]) for f in faces])
        me.update()
        ob = bpy.data.objects.new(prefix + m, me)
        bpy.context.scene.collection.objects.link(ob)
        ob.data.materials.append(blender_mat(m, mats[m]))
        for poly in me.polygons:
            poly.use_smooth = True
        objs.append(ob)
    return objs


def frame_objects(sc, objs, mpp, pad=6, shadow=0.45):
    """Nesneleri kapsayacak çözünürlük ve kamera kaydırmasını ayarlar; orijinin piksel konumunu döndürür."""
    pts = []
    for ob in objs:
        mw = ob.matrix_world
        vs = ob.data.vertices
        n = len(vs)
        stride = max(1, n // 4000)
        for i in range(0, n, stride):
            v = mw @ vs[i].co
            pts.append((v.x, v.z, -v.y))  # blender → oyun
    pts = np.array(pts)
    up = np.array([0.0, math.cos(B.PITCH), -math.sin(B.PITCH)])
    px = pts[:, 0] / mpp
    py = -(pts @ up) / mpp
    x0, x1 = px.min(), px.max()
    y0, y1 = py.min(), py.max()
    h = y1 - y0
    # gölge sağ-aşağı uzar
    x1 += h * shadow * 0.6
    y1 += h * shadow * 0.25
    w = int(math.ceil(x1 - x0)) + pad * 2
    hh = int(math.ceil(y1 - y0)) + pad * 2
    w += w % 2
    hh += hh % 2
    res = (max(w, 8), max(hh, 8))
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.camera.data.ortho_scale = max(res) * mpp
    ax = -x0 + pad
    ay = -y0 + pad
    # ortho_scale büyük kenara göre: kaydırma büyük kenara oranlanır
    big = max(res)
    sc.camera.data.shift_x = -(ax - res[0] / 2) / big
    sc.camera.data.shift_y = (ay - res[1] / 2) / big
    return res, (ax, ay)


def yaw_objects(objs, yaw):
    for ob in objs:
        ob.rotation_euler = (0, 0, yaw)
