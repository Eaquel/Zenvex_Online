"""3D modelleri (Meshy GLB) oyunun çalışma anı biçimine (.zxm + doku) çevirir.

Her model için:
  1. Ham GLB okunur (1-2 milyon üçgen, 8192 doku); oyun eksenine çevrilir (Z yukarı, ön = −Y, ayak tabanı z = 0).
  2. Geometri kaynaştırılır ve hedef üçgen sayısına küçültülür (karakter ~14 bin, silah ~2 bin, araç ~10 bin).
  3. xatlas ile yeni, temiz bir UV atlası açılır; renkler orijinal yüksek detaylı yüzeyden yeni atlasa bake edilir
     (her doku pikselinin 3B konumuna en yakın, normali uyuşan orijinal yüzey örneği). Adacık kenarları taşırılır.
  4. Karakterlerde otomatik iskelet: kollar eldeki tohumlardan jeodezik yayılımla, bacaklar yükseklik ve yanla;
     A-pozundaki kollar doğal duruşa indirilir. Her köşe en çok 2 kemiğe bağlanır.
  5. Silahlarda tutma noktası orijin, ileri +X, sırt/üst +Z; ateşli silahlarda şarjör ayrı parça, namlu ucu kaydedilir.
     Araçlarda tekerlekler ve taret ayrı parça (dönme eksenleriyle).

Çıktı: android/app/src/main/assets/m3d/<ad>.zxm ve <ad>.webp; önizleme: <önizleme>/<ad>.png (isteğe bağlı).

/home/user/zx_venv3d/bin/python tools/model3d/build.py <ham_klasör> [ad,...]
"""
import io
import json
import math
import os
import struct
import sys
import time
from collections import deque

import fast_simplification
import numpy as np
import xatlas
from PIL import Image
from scipy import ndimage
from scipy.spatial import cKDTree

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "m3d")

# ad: (kategori, ham dosya, hedef üçgen, bake/kayıt doku boyu, ek ayarlar)
CHARS = {
    # hood: kapüşonun içi (yüz dahil) karanlık boyanır; yüz görünmez
    "kael": ("Characters/Meshy_AI_Ember_Eyed_Shadow_Rog_1026163839_texture.glb", dict(height=1.86, hood=True)),
    "vex": ("Characters/Meshy_AI_Ironclad_Brute_1026170455_texture.glb", dict(height=1.98)),
    "lyra": ("Characters/Meshy_AI_Cybernetic_Outlaw_1026170152_texture.glb", dict(height=1.80)),
    "morvath": ("Characters/Meshy_AI_Plague_Alchemist_1026170129_texture.glb", dict(height=1.86)),
}

# Silah tutma uzayı (paketteki build_3d_weapons_canonical.py ile aynı eksen ve tutma noktaları):
# axis: ham modelin uzun ekseni; grip: ham koordinatta tutma noktası; len: metre cinsinden toplam boy
WEAPONS = {
    "blood_scythe": ("Weapons/Crimson Reaper Staff_Meshy_AI_2026-10-24_39f04a.glb", dict(axis="Y_UP", grip=(0.0, -0.18, 0.0), len=1.50)),
    "handaxe": ("Weapons/Meshy_AI_Battle_Worn_Handaxe_1026170307_texture.glb", dict(axis="Y_UP", grip=(0.04, -0.26, 0.0), len=0.92)),
    "ice_scythe": ("Weapons/Meshy_AI_Frostbone_Reaper_1026170006_texture.glb", dict(axis="Y_UP", grip=(0.04, -0.18, 0.0), len=1.46)),
    "knife": ("Weapons/Meshy_AI_Iron_Fang_1026165923_texture.glb", dict(axis="X_LEFT", grip=(0.28, 0.0, 0.0), len=0.68)),
    "ak47": ("Weapons/Meshy_AI_Ironwood_Kalashnikov_1026165859_texture.glb", dict(axis="X_LEFT", grip=(0.20, -0.06, 0.0), len=1.15, mag=(-0.12, 0.14, -0.40, -0.05))),
    "magma_hammer": ("Weapons/Meshy_AI_Molten_Judgment_1026165950_texture.glb", dict(axis="Y_UP", grip=(0.0, -0.24, 0.0), len=1.35)),
    "chainsaw": ("Weapons/Meshy_AI_Neon_Rust_Reaver_1026165930_texture.glb", dict(axis="X_LEFT", grip=(0.34, -0.02, 0.0), len=1.30)),
    "uzi": ("Weapons/Meshy_AI_Neon_Uzi_1026170547_texture.glb", dict(axis="X_LEFT", grip=(0.06, -0.04, 0.0), len=0.64, mag=(-0.06, 0.18, -0.45, -0.14))),
    "rpg": ("Weapons/Meshy_AI_Rocket_Propelled_Gren_1026165916_texture.glb", dict(axis="X_LEFT", grip=(-0.04, -0.05, 0.0), len=1.36, mag=(-0.65, -0.22, -0.20, 0.20))),
    "storm_axe": ("Weapons/Meshy_AI_Runeblade_Axe_1026165944_texture.glb", dict(axis="Y_UP", grip=(0.02, -0.25, 0.0), len=1.15)),
    "pickaxe": ("Weapons/Meshy_AI_Rustbound_Pickaxe_1026170228_texture.glb", dict(axis="Y_UP", grip=(0.0, -0.25, 0.0), len=1.10)),
    "sniper": ("Weapons/Meshy_AI_Scoped_Sniper_Rifle_1026165908_texture.glb", dict(axis="X_LEFT", grip=(0.22, -0.04, 0.0), len=1.36, mag=(-0.05, 0.18, -0.30, -0.04))),
    "katana": ("Weapons/Meshy_AI_Single_centered_3_4_i_1026165845_texture.glb", dict(axis="Y_UP", grip=(0.0, -0.36, 0.0), len=1.30)),
    "hatchet": ("Weapons/Meshy_AI_Stonebound_Axe_1026170359_texture.glb", dict(axis="Y_UP", grip=(0.02, -0.26, 0.0), len=0.92)),
    "effigy": ("Weapons/Meshy_AI_Stonebound_Effigy_1026170353_texture.glb", dict(axis="Y_UP", grip=(0.0, -0.26, 0.0), len=1.02)),
    "club": ("Weapons/Meshy_AI_Stonefang_Cudgel_1026170012_texture.glb", dict(axis="Y_UP", grip=(0.0, -0.32, 0.0), len=1.06)),
    "pistol": ("Weapons/Meshy_AI_Vintage_Pistol_on_Gre_1026170537_texture.glb", dict(axis="X_LEFT", grip=(0.26, -0.08, 0.0), len=0.48, mag=(0.12, 0.45, -0.45, -0.18))),
    "void_claw": ("Weapons/Meshy_AI_Voidclaw_Gauntlet_1026165937_texture.glb", dict(axis="Z_CLAW", grip=(0.0, 0.08, -0.15), len=0.78)),
}

# NPC'ler: kolları doğal duruşta (elinde asa, anahtar, tüfek); kollar gövdeye bağlı, bacak/gövde/baş canlanır
NPCS = {
    "npc_marco": ("Npcs/Meshy_AI_The_Weathered_Wayfare_1027201037_texture.glb", dict(height=1.84)),
    "npc_silas": ("Npcs/Meshy_AI_The_Ragged_Wanderer_1027201155_texture.glb", dict(height=1.78)),
    "npc_elena": ("Npcs/Meshy_AI_Battlefield_Medic_1027201020_texture.glb", dict(height=1.72)),
    "npc_viktor": ("Npcs/Meshy_AI_Wasteland_Enforcer_1027201008_texture.glb", dict(height=1.92)),
    "npc_jack": ("Npcs/Meshy_AI_Wasteland_Veteran_1027200923_texture.glb", dict(height=1.82)),
    "npc_aldric": ("Npcs/Meshy_AI_Twilight_Wanderer_1027201122_texture.glb", dict(height=1.86)),
    "npc_diana": ("Npcs/Meshy_AI_Armored_Soldier_1027201144_texture.glb", dict(height=1.76)),
    "npc_soldier": ("Npcs/Meshy_AI_Sentinel_in_the_Shado_1027201051_texture.glb", dict(height=1.84)),
    "npc_sniper": ("Npcs/Meshy_AI_Wasteland_Sentinel_1027201132_texture.glb", dict(height=1.84)),
}

# VIP kanatları: sırta takılır; omuz ortası orijin, açıklık metre cinsinden
WINGS = {
    "wings_ash": ("Wings/Meshy_AI_Emberwings_1027201234_texture.glb", dict(span=1.9)),
    "wings_dragon": ("Wings/Meshy_AI_Crimson_Dragon_Wings_1027201244_texture.glb", dict(span=2.0)),
    "wings_titan": ("Wings/Meshy_AI_Emberwing_Ruin_1027201304_texture.glb", dict(span=2.0)),
    "wings_s1": ("Wings/Meshy_AI_Midnight_Valkyrie_Win_1027201332_texture.glb", dict(span=1.8)),
}

CARS = {
    "hellhound": ("Cars/Meshy_AI_Wasteland_Interceptor_1026170510_texture.glb", dict(length=4.4, wheels="car")),
    "nightstalker": ("Cars/Meshy_AI_Wasteland_Marauder_1026170417_texture.glb", dict(length=2.3, wheels="moto")),
}

# V19: modeller orijinalden daha ayrıntılı üretilir (üçgen ~2,4×, kayıt dokusu 1,5-2×); bake 2× büyük çizilip küçültülür
TARGET = {"char": 26000, "npc": 16000, "wing": 7000, "weapon": 5000, "car": 22000}
BAKE = {"char": (3072, 1536), "npc": (1536, 768), "wing": (1536, 768), "weapon": (1024, 512), "car": (3072, 1536)}
# küçük ayrıntı gölgesi gücü: küçültmede kaybolan yüzey kıvrımları (dikiş, oyuk, perçin) dokuya koyulaşma olarak işlenir
CAVITY = {"char": 0.4, "npc": 0.4, "wing": 0.3, "weapon": 0.35, "car": 0.35}
# normal haritası kayıt boyu (teğet uzayı; çizici zenvex_r3d raster::tangents ile aynı çatıyı kurar)
NSIZE = {"char": 1024, "npc": 512, "wing": 512, "weapon": 256, "car": 1024}


# ----------------------------------------------------------------------------- GLB
def load_glb(path):
    data = open(path, "rb").read()
    jlen = struct.unpack_from("<I", data, 12)[0]
    j = json.loads(data[20:20 + jlen].decode("utf-8"))
    blen = struct.unpack_from("<I", data, 20 + jlen)[0]
    b = memoryview(data)[28 + jlen:28 + jlen + blen]

    def acc(idx):
        a = j["accessors"][idx]
        bv = j["bufferViews"][a["bufferView"]]
        off = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
        dt = {5126: np.float32, 5123: np.uint16, 5125: np.uint32, 5121: np.uint8}[a["componentType"]]
        stride = bv.get("byteStride", 0)
        isz = n * np.dtype(dt).itemsize
        if stride and stride != isz:
            raw = np.frombuffer(b, dtype=np.uint8, count=a["count"] * stride, offset=off).reshape(a["count"], stride)
            return np.frombuffer(raw[:, :isz].tobytes(), dtype=dt).reshape(a["count"], n)
        return np.frombuffer(b, dtype=dt, count=a["count"] * n, offset=off).reshape(a["count"], n)

    assert len(j["meshes"]) == 1 and len(j["meshes"][0]["primitives"]) == 1, path
    p = j["meshes"][0]["primitives"][0]
    pos = acc(p["attributes"]["POSITION"]).astype(np.float64)
    uv = acc(p["attributes"]["TEXCOORD_0"]).astype(np.float64)
    tris = acc(p["indices"]).astype(np.int64).reshape(-1, 3)
    mat = j["materials"][p.get("material", 0)]
    t = j["textures"][mat["pbrMetallicRoughness"]["baseColorTexture"]["index"]]
    src = t.get("source", t.get("extensions", {}).get("EXT_texture_webp", {}).get("source"))
    im = j["images"][src]
    bv = j["bufferViews"][im["bufferView"]]
    raw = bytes(b[bv.get("byteOffset", 0):bv.get("byteOffset", 0) + bv["byteLength"]])
    Image.MAX_IMAGE_PIXELS = None
    tex = np.asarray(Image.open(io.BytesIO(raw)).convert("RGB"))
    return pos, uv, tris, tex


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0 + 1e-9), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


# ----------------------------------------------------------------------------- yönlendirme
def orient_char(pos, opt):
    v = np.stack([pos[:, 0], -pos[:, 2], pos[:, 1]], axis=1)
    v[:, 2] -= v[:, 2].min()
    v *= opt["height"] / v[:, 2].max()
    th = opt["height"]
    torso = (v[:, 2] > th * 0.45) & (v[:, 2] < th * 0.75) & (np.abs(v[:, 0]) < th * 0.18)
    v[:, 0] -= np.median(v[torso, 0])
    v[:, 1] -= np.median(v[torso, 1])
    return v


def weapon_norm(pos):
    """Paketteki optimize modeller en uzun eksen 1,25 birim olacak şekilde ölçeklenmiş; tutma noktaları bu ölçekte."""
    return 1.25 / float((pos.max(axis=0) - pos.min(axis=0)).max())


def orient_weapon(pos, opt):
    p = pos * weapon_norm(pos) - np.array(opt["grip"])
    if opt["axis"] == "Y_UP":
        vp = np.stack([p[:, 1], p[:, 2], p[:, 0]], axis=1)
    elif opt["axis"] == "X_LEFT":
        vp = np.stack([-p[:, 0], -p[:, 2], p[:, 1]], axis=1)
    else:
        a = math.radians(-30.0)
        y = p[:, 1] * math.cos(a) - p[:, 2] * math.sin(a)
        z = p[:, 1] * math.sin(a) + p[:, 2] * math.cos(a)
        vp = np.stack([z, p[:, 0], y], axis=1)
    return vp * (opt["len"] / 1.25)


def orient_wing(pos, opt):
    """Kanat: açıklık X, kalınlık Y (sırttan dışa +Y), yukarı Z; iki kanadın birleştiği kök orijinde."""
    v = np.stack([pos[:, 0], -pos[:, 2], pos[:, 1]], axis=1)
    if opt.get("flip"):
        v[:, 0] *= -1
        v[:, 1] *= -1
    v *= opt["span"] / (v[:, 0].max() - v[:, 0].min())
    v[:, 0] -= (v[:, 0].max() + v[:, 0].min()) / 2
    root = np.abs(v[:, 0]) < 0.06 * opt["span"]
    if root.sum() < 20:
        root = np.abs(v[:, 0]) < np.percentile(np.abs(v[:, 0]), 3)
    v[:, 1] -= v[root, 1].min()
    v[:, 2] -= np.median(v[root, 2])
    return v


def orient_car(pos, opt):
    v = np.stack([-pos[:, 2], pos[:, 0], pos[:, 1]], axis=1)
    v[:, 2] -= v[:, 2].min()
    v[:, 0] -= (v[:, 0].max() + v[:, 0].min()) / 2
    v[:, 1] -= (v[:, 1].max() + v[:, 1].min()) / 2
    return v


# ----------------------------------------------------------------------------- küçültme + UV + bake
def weld(v, tris, eps=1e-5):
    key = np.round(v / eps).astype(np.int64)
    _, first, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
    inv = inv.ravel()
    t = inv[tris]
    ok = (t[:, 0] != t[:, 1]) & (t[:, 1] != t[:, 2]) & (t[:, 0] != t[:, 2])
    return v[first], t[ok]


def vertex_normals(v, t):
    fn = np.cross(v[t[:, 1]] - v[t[:, 0]], v[t[:, 2]] - v[t[:, 0]])
    n = np.zeros_like(v)
    for k in range(3):
        np.add.at(n, t[:, k], fn)
    return n / np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-12)


def decimate(v, t, target):
    if len(t) <= target:
        return v, t
    vv, tt = fast_simplification.simplify(v.astype(np.float32), t.astype(np.int32), 1.0 - target / len(t), agg=7)
    vv, tt = vv.astype(np.float64), tt.astype(np.int64)
    used = np.unique(tt)
    remap = np.full(len(vv), -1)
    remap[used] = np.arange(len(used))
    return vv[used], remap[tt]


def surface_samples(v, uv, tris, tex):
    """Orijinal yüzeyden renkli örnekler: her üçgende ağırlık merkezi + 3 iç nokta."""
    a, b, c = v[tris[:, 0]], v[tris[:, 1]], v[tris[:, 2]]
    ua, ub, uc = uv[tris[:, 0]], uv[tris[:, 1]], uv[tris[:, 2]]
    fn = np.cross(b - a, c - a)
    fn /= np.maximum(np.linalg.norm(fn, axis=1, keepdims=True), 1e-12)
    bary = [(1 / 3, 1 / 3, 1 / 3), (2 / 3, 1 / 6, 1 / 6), (1 / 6, 2 / 3, 1 / 6), (1 / 6, 1 / 6, 2 / 3)]
    H, W = tex.shape[:2]
    pts, cols, nrm = [], [], []
    for w0, w1, w2 in bary:
        pts.append(a * w0 + b * w1 + c * w2)
        u = ua * w0 + ub * w1 + uc * w2
        x = np.clip((u[:, 0] % 1.0) * W, 0, W - 1.001)
        y = np.clip((u[:, 1] % 1.0) * H, 0, H - 1.001)
        x0, y0 = x.astype(np.int64), y.astype(np.int64)
        fx, fy = (x - x0)[:, None], (y - y0)[:, None]
        x1, y1 = np.minimum(x0 + 1, W - 1), np.minimum(y0 + 1, H - 1)
        col = (tex[y0, x0] * (1 - fx) * (1 - fy) + tex[y0, x1] * fx * (1 - fy) + tex[y1, x0] * (1 - fx) * fy + tex[y1, x1] * fx * fy)
        cols.append(col.astype(np.float32))
        nrm.append(fn)
    return np.concatenate(pts), np.concatenate(cols), np.concatenate(nrm).astype(np.float32)


def unwrap(v, t, res):
    atlas = xatlas.Atlas()
    atlas.add_mesh(v.astype(np.float32), t.astype(np.uint32))
    co = xatlas.ChartOptions()
    co.max_iterations = 2
    po = xatlas.PackOptions()
    po.resolution = res
    po.padding = 6
    po.bilinear = True
    po.bruteForce = False
    atlas.generate(co, po)
    vmap, idx, uvs = atlas[0]
    return vmap.astype(np.int64), idx.astype(np.int64), uvs.astype(np.float64)


def vertex_convexity(v, t, n):
    """Köşe başına dışbükeylik: komşu köşeler teğet düzlemin altındaysa (+), üstündeyse (−); model boyuna göre ölçekli."""
    e = np.concatenate([t[:, [0, 1]], t[:, [1, 2]], t[:, [2, 0]]])
    e = np.concatenate([e, e[:, ::-1]])
    d = v[e[:, 1]] - v[e[:, 0]]
    ln_ = np.maximum(np.linalg.norm(d, axis=1), 1e-9)
    c = -np.einsum("nd,nd->n", n[e[:, 0]], d) / ln_
    acc = np.zeros(len(v))
    cnt = np.zeros(len(v))
    np.add.at(acc, e[:, 0], c)
    np.add.at(cnt, e[:, 0], 1)
    conv = acc / np.maximum(cnt, 1)
    return conv / max(np.percentile(np.abs(conv), 95), 1e-6)


def smooth_vertex(v, t, x, it=2):
    """Köşe değerini komşularıyla ortalar (it tur)."""
    e = np.concatenate([t[:, [0, 1]], t[:, [1, 2]], t[:, [2, 0]]])
    e = np.concatenate([e, e[:, ::-1]])
    for _ in range(it):
        acc = x.copy()
        cnt = np.ones(len(v))
        np.add.at(acc, e[:, 0], x[e[:, 1]])
        np.add.at(cnt, e[:, 0], 1)
        x = acc / cnt
    return x


def hood_inside(v, t, n, th):
    """Kapüşonun içindeki köşeler (0..1): baş bölgesindeki bir noktadan yukarı, sola, sağa ve çapraz yukarı atılan
    ışınların çoğu modele çarpıyorsa nokta kapalı bir oyuğun (kapüşonun) içindedir. Yüz ve kapüşonun iç yüzü böyle bulunur."""
    cand = np.where(v[:, 2] > 0.76 * th)[0]
    tri = t[(v[t].max(axis=1)[:, 2] > 0.72 * th)]
    a, b, c = v[tri[:, 0]], v[tri[:, 1]], v[tri[:, 2]]
    e1, e2 = b - a, c - a
    dirs = np.array([[0, 0, 1], [-1, 0, 0], [1, 0, 0], [-0.7, 0, 0.7], [0.7, 0, 0.7]], float)
    dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
    hit = np.zeros((len(cand), len(dirs)), bool)
    for di, d in enumerate(dirs):
        pv = np.cross(d, e2)
        det = np.einsum("kd,kd->k", e1, pv)
        ok = np.abs(det) > 1e-12
        inv = np.where(ok, 1.0 / np.where(ok, det, 1.0), 0.0)
        for s0 in range(0, len(cand), 256):
            idx = cand[s0:s0 + 256]
            o = v[idx] + n[idx] * 0.004 * th
            tv = o[:, None, :] - a[None]
            u = np.einsum("mkd,kd->mk", tv, pv) * inv
            qv = np.cross(tv, e1[None])
            w = np.einsum("mkd,d->mk", qv, d) * inv
            dist = np.einsum("mkd,kd->mk", qv, e2) * inv
            h = ok & (u >= 0) & (w >= 0) & (u + w <= 1) & (dist > 0.002 * th) & (dist < 0.35 * th)
            hit[s0:s0 + 256, di] = h.any(axis=1)
    score = hit.mean(axis=1)
    out = np.zeros(len(v))
    out[cand] = smoothstep(0.45, 0.85, score) * smoothstep(0.77 * th, 0.81 * th, v[cand, 2])
    return np.clip(smooth_vertex(v, t, out, 3), 0, 1)


def guided(I, p, r, eps):
    """Kenarı koruyan düzleştirme (guided filter, He vd.): I rehber (gri), p kanal; r yarıçap."""
    f = lambda x: ndimage.uniform_filter(x, 2 * r + 1, mode="nearest")
    mI, mp = f(I), f(p)
    a = (f(I * p) - mI * mp) / (f(I * I) - mI * mI + eps)
    b = mp - a * mI
    return f(a) * I + f(b)


def repaint(img, res, attr):
    """Meshy dokusunun lekeli/çamurlu boyamasını temizler ve elle boyanmış görünüm verir:
    1) büyük yarıçaplı kenar korumalı düzleştirme: aynı malzemedeki kir yamaları ve lekeler gider, malzeme rengi düzgünleşir
    2) ince tane geri eklenir (deri gözeneği, kumaş örgüsü, metal çiziği), orta ölçekli kabartma desenleri yarı yarıya korunur
    3) geometriden boyama: dışbükey kenarlara aşınma ışığı, içbükeylere koyu, yukarıdan aşağı hafif ışık geçişi
    4) yerel kontrast ve doygunluk; kapüşon içi karanlık
    attr: dışbükeylik (-1..1), göreli yükseklik (0..1), karanlık (0..1) haritaları
    """
    x = img.astype(np.float32) / 255.0
    g = x @ np.array([0.3, 0.59, 0.11], np.float32)
    r = max(4, res // 128)
    flat = np.stack([guided(g, x[..., k], r, 0.0025) for k in range(3)], -1)
    # malzeme paleti: dokudaki ana renkler (k-ortalama); iki malzemenin çamur gibi karıştığı ara tonlar en yakın
    # malzemeye yumuşakça çekilir (yumuşak atama: sert bant/posterleşme olmaz), sınırlar temiz boyanmış gibi durur
    rng = np.random.default_rng(7)
    pts = flat.reshape(-1, 3)[rng.choice(res * res, 60000, replace=False)]
    from scipy.cluster.vq import kmeans2
    cent, _ = kmeans2(pts.astype(np.float64), 12, minit="++", seed=7)
    cent = cent.astype(np.float32)
    snap = np.zeros_like(flat)
    for y0 in range(0, res, 512):
        blk = flat[y0:y0 + 512]
        d2 = ((blk[..., None, :] - cent[None, None]) ** 2).sum(-1)
        w = np.exp(-(d2 - d2.min(-1, keepdims=True)) / 0.004)
        w /= w.sum(-1, keepdims=True)
        snap[y0:y0 + 512] = w @ cent
    flat = flat * 0.5 + snap * 0.5
    fine = x - ndimage.gaussian_filter(x, (1.4, 1.4, 0))
    mid = ndimage.gaussian_filter(x, (1.4, 1.4, 0)) - flat
    y = flat + 0.5 * mid + 1.0 * fine
    conv, zrel, dark = attr[..., 0], attr[..., 1], attr[..., 2]
    # aşınmış kenar ışığı ve oyuk gölgesi (geometriden), yükseklik geçişi
    edge = np.clip(conv, 0, 1) ** 1.5
    pit = np.clip(-conv, 0, 1) ** 1.5
    y = y * (1.0 - 0.18 * pit[..., None]) + 0.10 * edge[..., None] * (0.35 + y)
    y = y * (0.90 + 0.14 * zrel[..., None])
    # yerel kontrast (geniş yarıçap) ve doygunluk
    blur = ndimage.gaussian_filter(y, (res / 256, res / 256, 0))
    y = y + 0.22 * (y - blur)
    lum = (y @ np.array([0.3, 0.59, 0.11], np.float32))[..., None]
    y = lum + (y - lum) * 1.10
    # kapüşon içi: neredeyse siyah, hafif soğuk ton (yüz görünmez)
    d = dark[..., None]
    y = y * (1.0 - d) + d * (y * 0.06 + np.array([0.035, 0.035, 0.045], np.float32))
    return np.clip(y * 255.0, 0, 255).astype(np.float32)


def bake(lv, ln, lt, luv, samples, res, cavity=0.0, normals=False, vattr=None):
    """Düşük poligonlu modelin UV uzayında her doku pikselinin 3B konumunu bulup en yakın uygun örneğin rengini alır.

    cavity > 0: orijinal yüzeyin normali (yakın örneklerin ortalaması) düşük poligonlu yüzeyden ne kadar sapıyorsa piksel o
    kadar koyulaşır. Küçültmede kaybolan kıvrım, dikiş, oyuk ve kabartma kenarları böylece dokuda görünür kalır.
    normals: ikinci çıktı teğet uzayı normal haritası (T = dP/du, B = dP/dv, N = düşük poligon normali).
    """
    pts, cols, nrm = samples
    tree = cKDTree(pts)
    img = np.zeros((res, res, 3), np.float32)
    filled = np.zeros((res, res), bool)
    P = luv * res
    allp, alln, allxy, allt, allb, alla = [], [], [], [], [], []
    for tri in lt:
        p0, p1, p2 = P[tri]
        x0 = int(max(math.floor(min(p0[0], p1[0], p2[0])), 0))
        x1 = int(min(math.ceil(max(p0[0], p1[0], p2[0])), res - 1))
        y0 = int(max(math.floor(min(p0[1], p1[1], p2[1])), 0))
        y1 = int(min(math.ceil(max(p0[1], p1[1], p2[1])), res - 1))
        if x1 < x0 or y1 < y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        xs, ys = xs.ravel(), ys.ravel()
        d = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1])
        if abs(d) < 1e-12:
            continue
        w0 = ((p1[1] - p2[1]) * (xs - p2[0]) + (p2[0] - p1[0]) * (ys - p2[1])) / d
        w1 = ((p2[1] - p0[1]) * (xs - p2[0]) + (p0[0] - p2[0]) * (ys - p2[1])) / d
        w2 = 1 - w0 - w1
        m = (w0 >= -0.02) & (w1 >= -0.02) & (w2 >= -0.02)
        if not m.any():
            continue
        w = np.stack([w0[m], w1[m], w2[m]], 1)
        if normals:
            q0, q1, q2 = lv[tri]
            u0, u1, u2 = luv[tri]
            e1, e2 = q1 - q0, q2 - q0
            du1, dv1 = u1 - u0
            du2, dv2 = u2 - u0
            dd = du1 * dv2 - du2 * dv1
            r = 1.0 / dd if abs(dd) > 1e-12 else 0.0
            k = int(m.sum())
            allt.append(np.repeat(((e1 * dv2 - e2 * dv1) * r)[None], k, 0))
            allb.append(np.repeat(((e2 * du1 - e1 * du2) * r)[None], k, 0))
        allp.append(w @ lv[tri])
        alln.append(w @ ln[tri])
        if vattr is not None:
            alla.append(w @ vattr[tri])
        allxy.append(np.stack([xs[m].astype(np.int64), ys[m].astype(np.int64)], 1))
    q = np.concatenate(allp)
    qn = np.concatenate(alln)
    qn /= np.maximum(np.linalg.norm(qn, axis=1, keepdims=True), 1e-12)
    xy = np.concatenate(allxy)
    K = 8
    dist, nb = tree.query(q, k=K, workers=-1)
    agree = np.einsum("nkd,nd->nk", nrm[nb], qn)
    # normali uyuşan en yakın örnek (ince kumaşın arka yüzü seçilmesin); hiçbiri uymuyorsa en yakını
    score = dist + (agree < 0.2) * 1e3
    best = nb[np.arange(len(nb)), np.argmin(score, axis=1)]
    img[xy[:, 1], xy[:, 0]] = cols[best]
    filled[xy[:, 1], xy[:, 0]] = True
    nimg = None
    if cavity > 0 or normals:
        # yakın ve yönü uyuşan örneklerin normal ortalaması (tek üçgenin gürültüsü değil, yüzeyin kıvrımı)
        wgt = (agree >= 0.2) / np.maximum(dist, 1e-5)
        nh = np.einsum("nk,nkd->nd", wgt, nrm[nb])
        nh /= np.maximum(np.linalg.norm(nh, axis=1, keepdims=True), 1e-12)
        bad = np.linalg.norm(nh, axis=1) < 0.5
        nh[bad] = qn[bad]
    if normals:
        T = np.concatenate(allt)
        B = np.concatenate(allb)
        T = T - qn * np.einsum("nd,nd->n", T, qn)[:, None]
        B = B - qn * np.einsum("nd,nd->n", B, qn)[:, None]
        T /= np.maximum(np.linalg.norm(T, axis=1, keepdims=True), 1e-12)
        B /= np.maximum(np.linalg.norm(B, axis=1, keepdims=True), 1e-12)
        tn = np.stack([np.einsum("nd,nd->n", nh, T), np.einsum("nd,nd->n", nh, B), np.einsum("nd,nd->n", nh, qn)], 1)
        tn[:, 2] = np.maximum(tn[:, 2], 0.05)
        tn /= np.maximum(np.linalg.norm(tn, axis=1, keepdims=True), 1e-12)
        nimg = np.zeros((res, res, 3), np.float32)
        nimg[..., 2] = 1.0
        nimg[xy[:, 1], xy[:, 0]] = tn
    if cavity > 0:
        dev = 1.0 - np.clip(np.einsum("nd,nd->n", nh, qn), 0.0, 1.0)
        shade = np.ones((res, res), np.float32)
        shade[xy[:, 1], xy[:, 0]] = np.clip(1.0 - cavity * np.sqrt(dev) * 1.6, 0.55, 1.0)
        # tek piksellik kırıntı olmasın: hafif bulanıklaştırılır (yalnız dolu bölgede anlamlı)
        shade = ndimage.gaussian_filter(shade, 0.8)
        img *= shade[:, :, None]
    # adacık kenarlarını taşır (doku süzgeci kenarda boşluk görmesin)
    _, (iy, ix) = ndimage.distance_transform_edt(~filled, return_indices=True)
    img = img[iy, ix]
    if vattr is not None:
        amap = np.zeros((res, res, vattr.shape[1]), np.float32)
        amap[xy[:, 1], xy[:, 0]] = np.concatenate(alla)
        img = repaint(img, res, amap[iy, ix])
    out = np.clip(img, 0, 255).astype(np.uint8)
    if not normals:
        return out
    nimg = nimg[iy, ix]
    # hafif yumuşatma (tek üçgen gürültüsü), sonra yeniden birim boy
    nimg = ndimage.gaussian_filter(nimg, (0.7, 0.7, 0))
    nimg /= np.maximum(np.linalg.norm(nimg, axis=2, keepdims=True), 1e-12)
    return out, np.clip((nimg * 0.5 + 0.5) * 255.0 + 0.5, 0, 255).astype(np.uint8)


# ----------------------------------------------------------------------------- iskelet
BONES = ["pelvis", "spine", "head", "upper_r", "fore_r", "hand_r", "upper_l", "fore_l", "hand_l", "thigh_r", "shin_r", "thigh_l", "shin_l"]
PARENT = [-1, 0, 1, 1, 3, 4, 1, 6, 7, 0, 9, 0, 11]


def auto_rig(v, t, th, name, arms=True):
    """Kaynaştırılmış düşük poligonlu modelde kemik ağırlıkları + eklem dinlenme konumları; kolları doğal duruşa indirir.

    arms=False (NPC'ler): kollar zaten aşağıda ve elde asa/anahtar/tüfek var; kol kemikleri ağırlıksız, kollar ve eldeki
    eşya göğüsle (spine) birlikte hareket eder. Bacak, gövde ve baş kahramanlardaki gibi canlanır.
    """
    n = len(v)
    adj = [[] for _ in range(n)]
    for a, b, c in t:
        adj[a] += (b, c)
        adj[b] += (a, c)
        adj[c] += (a, b)
    max_x = float(np.percentile(np.abs(v[:, 0]), 99.6))
    W = np.zeros((n, len(BONES)))
    joints = {}
    arm_total = np.zeros(n)
    s186 = th / 1.86
    for side, sgn in (("r", -1.0), ("l", 1.0)):
        if not arms:
            joints["upper_" + side] = np.array([sgn * 0.19 * s186, 0.0, 0.80 * th])
            joints["fore_" + side] = np.array([sgn * 0.24 * s186, 0.0, 0.63 * th])
            joints["hand_" + side] = np.array([sgn * 0.26 * s186, -0.02, 0.50 * th])
            joints["palm_" + side] = np.array([sgn * 0.26 * s186, -0.03, 0.46 * th])
            continue
        ax = v[:, 0] * sgn
        seed = np.where((ax > max_x * 0.80) & (v[:, 2] > 0.36 * th) & (v[:, 2] < 0.80 * th))[0]
        palm = v[seed].mean(axis=0)
        # kol bölgesi: elden içe doğru, gövde kenarına kadar jeodezik yayılım
        dist = np.full(n, -1)
        dist[seed] = 0
        q = deque(seed.tolist())
        lim = max_x * 0.30
        while q:
            c = q.popleft()
            for nb in adj[c]:
                if dist[nb] < 0 and ax[nb] > lim and 0.36 * th < v[nb, 2] < 0.86 * th:
                    dist[nb] = dist[c] + 1
                    q.append(nb)
        in_arm = dist >= 0
        # omuz: kolun gövdeye en yakın kısmı; dirsek: omuz-el doğrusu boyunca orta
        inner = in_arm & (ax < np.percentile(ax[in_arm], 12))
        sh = v[inner].mean(axis=0)
        sh[0] = sgn * max(abs(sh[0]), max_x * 0.34)
        limb = palm - sh
        tl = np.clip(((v - sh) @ limb) / (limb @ limb), -1, 2)
        mid = in_arm & (np.abs(tl - 0.5) < 0.08)
        el = v[mid].mean(axis=0) if mid.sum() > 3 else sh + limb * 0.5
        a_up = in_arm * smoothstep(-0.05, 0.22, tl)
        a_fo = in_arm * smoothstep(0.40, 0.62, tl)
        a_hd = in_arm * smoothstep(0.80, 0.92, tl)
        # ağırlıkları komşulukla yumuşat (eklem yerinde kırık olmasın)
        for _ in range(6):
            for arr in (a_up, a_fo, a_hd):
                s = np.zeros(n)
                cnt = np.zeros(n)
                for k in range(3):
                    np.add.at(s, t[:, k], arr[t[:, (k + 1) % 3]] + arr[t[:, (k + 2) % 3]])
                    np.add.at(cnt, t[:, k], 2)
                arr[:] = np.where(in_arm, 0.5 * arr + 0.5 * s / np.maximum(cnt, 1), 0)
        bi = BONES.index
        W[:, bi("hand_" + side)] += a_hd
        W[:, bi("fore_" + side)] += np.clip(a_fo - a_hd, 0, 1)
        W[:, bi("upper_" + side)] += np.clip(a_up - a_fo, 0, 1)
        arm_total += a_up
        joints["upper_" + side] = sh
        joints["fore_" + side] = el
        joints["hand_" + side] = palm - (palm - el) * 0.25
        joints["palm_" + side] = palm
    sr, sl = joints["upper_r"], joints["upper_l"]
    sx, sy, sz = (abs(sr[0]) + abs(sl[0])) / 2, (sr[1] + sl[1]) / 2, (sr[2] + sl[2]) / 2
    joints["upper_r"], joints["upper_l"] = np.array([-sx, sy, sz]), np.array([sx, sy, sz])
    # bacaklar
    for side, sgn in (("r", -1.0), ("l", 1.0)):
        leg = (v[:, 0] * sgn > 0.04) & (v[:, 2] < 0.40 * th)
        # dizden ölçülen yan konum (palto/etek kalçayı geniş gösterir); kalça eklemi insan oranında içeride
        kn = leg & (np.abs(v[:, 2] - 0.26 * th) < 0.03 * th)
        kx = float(np.median(np.abs(v[kn, 0]))) if kn.any() else 0.11 * th / 1.86
        kx = float(np.clip(kx, 0.08 * th / 1.86, 0.15 * th / 1.86))
        joints["thigh_" + side] = np.array([sgn * min(kx * 0.85, 0.11 * th / 1.86), 0.0, 0.47 * th])
        joints["shin_" + side] = np.array([sgn * kx, float(np.median(v[kn, 1])) if kn.any() else 0.0, 0.26 * th])
        # bacak ağırlığı: yan + yükseklik, ama bacak ekseninden uzak (palto, etek) köşeler pelvisle kalır
        hip, knee = joints["thigh_" + side], joints["shin_" + side]
        ankle = np.array([knee[0], knee[1], 0.04 * th])
        def seg_d(a, b):
            ab = b - a
            t = np.clip(((v - a) @ ab) / (ab @ ab), 0, 1)
            return np.linalg.norm(v - (a + t[:, None] * ab), axis=1)
        d = np.minimum(seg_d(hip, knee), seg_d(knee, ankle))
        rc = 0.085 * th / 1.86
        radial = 1 - smoothstep(rc * 1.15, rc * 2.3, d)
        # yarıçap sınırı yalnız diz üstünde (palto/etek); dizden aşağısı (bot, ayak ucu) tamamen bacakla
        radial = radial + (1 - radial) * (1 - smoothstep(0.26 * th, 0.34 * th, v[:, 2]))
        lg = smoothstep(0.01, 0.07, v[:, 0] * sgn) * (1 - smoothstep(0.38 * th, 0.50 * th, v[:, 2])) * (1 - arm_total)
        lg = lg * radial
        sn = lg * (1 - smoothstep(0.22 * th, 0.30 * th, v[:, 2]))
        W[:, BONES.index("thigh_" + side)] += lg - sn
        W[:, BONES.index("shin_" + side)] += sn
    rest = np.clip(1 - W.sum(axis=1), 0, 1)
    if not arms:
        # gövdenin yanındaki kollar ve eldeki eşya (asa, anahtar) göğüsle; diz altına inen asa ucu kalçayla
        side_w = smoothstep(0.17 * s186, 0.24 * s186, np.abs(v[:, 0])) * smoothstep(0.30 * th, 0.40 * th, v[:, 2]) * rest
        W[:, BONES.index("spine")] += side_w
        rest = rest - side_w
    head = smoothstep(0.78 * th, 0.85 * th, v[:, 2]) * rest
    spine = smoothstep(0.48 * th, 0.62 * th, v[:, 2]) * rest - head
    W[:, BONES.index("head")] += head
    W[:, BONES.index("spine")] += np.clip(spine, 0, 1)
    W[:, BONES.index("pelvis")] += np.clip(rest - head - np.clip(spine, 0, 1), 0, 1)
    joints["pelvis"] = np.array([0.0, 0.0, 0.50 * th])
    joints["spine"] = np.array([0.0, 0.0, 0.58 * th])
    joints["head"] = np.array([0.0, 0.0, 0.83 * th])
    # en güçlü iki kemik
    order = np.argsort(-W, axis=1)
    b0, b1 = order[:, 0], order[:, 1]
    w0 = W[np.arange(n), b0]
    w1 = W[np.arange(n), b1]
    s = np.maximum(w0 + w1, 1e-9)
    w0 = w0 / s
    jp = np.array([joints[b] for b in BONES])
    # A-pozu → doğal duruş: kolları omuzdan aşağı indir (karakterin geometrisi bu pozla kaydedilir)
    v = v.copy()
    for side, sgn in (("r", -1.0), ("l", 1.0)) if arms else ():
        u = BONES.index("upper_" + side)
        sh = jp[u]
        palm = joints["hand_" + side]
        cur = math.degrees(math.atan2(-(palm[2] - sh[2]), abs(palm[0] - sh[0])))  # yataydan aşağı açı
        want = 72.0
        ang = (want - cur) * (-sgn)
        R = rot("y", -ang)
        armw = np.zeros(n)
        for bn in ("upper_", "fore_", "hand_"):
            armw += W[:, BONES.index(bn + side)]
        armw = np.clip(armw, 0, 1)[:, None]
        v = v * (1 - armw) + ((v - sh) @ R.T + sh) * armw
        for bn in ("fore_", "hand_"):
            k = BONES.index(bn + side)
            jp[k] = (jp[k] - sh) @ R.T + sh
        joints["palm_" + side] = (joints["palm_" + side] - sh) @ R.T + sh
    palms = {k: [float(x) for x in joints["palm_" + k]] for k in ("r", "l")}
    return v, b0.astype(np.uint8), b1.astype(np.uint8), np.clip(np.round(w0 * 255), 0, 255).astype(np.uint8), jp, palms


# ----------------------------------------------------------------------------- yazma
def write_zxm(path, meta, v, nrm, uv, tris, b0=None, b1=None, w0=None):
    lo, hi = v.min(axis=0), v.max(axis=0)
    span = np.maximum(hi - lo, 1e-6)
    qp = np.round((v - lo) / span * 65535).astype(np.uint16)
    qn = np.clip(np.round(nrm * 127), -127, 127).astype(np.int8)
    quv = np.clip(np.round(uv * 65535), 0, 65535).astype(np.uint16)
    n = len(v)
    rec = np.zeros(n, dtype=[("p", "<u2", 3), ("n", "i1", 3), ("pad", "i1"), ("uv", "<u2", 2), ("b", "u1", 2), ("w", "u1"), ("pad2", "u1")])
    rec["p"] = qp
    rec["n"] = qn
    rec["uv"] = quv
    if b0 is not None:
        rec["b"][:, 0] = b0
        rec["b"][:, 1] = b1
        rec["w"] = w0
    else:
        rec["w"] = 255
    assert n < 65536
    idx = tris.astype("<u2")
    meta = dict(meta)
    meta.update(nverts=n, ntris=len(tris), lo=[float(x) for x in lo], hi=[float(x) for x in hi], vstride=rec.itemsize)
    js = json.dumps(meta, ensure_ascii=False).encode("utf-8")
    with open(path, "wb") as f:
        f.write(b"ZXM1")
        f.write(struct.pack("<I", len(js)))
        f.write(js)
        f.write(rec.tobytes())
        f.write(idx.tobytes())


def save_tex(path, img, size):
    im = Image.fromarray(img).resize((size, size), Image.LANCZOS)
    im.save(path, "WEBP", quality=92, method=6)


# ----------------------------------------------------------------------------- ana akış
def build(src, name, kind, opt):
    t0 = time.time()
    pos, uv, tris, tex = load_glb(src)
    if kind in ("char", "npc"):
        v = orient_char(pos, opt)
    elif kind == "wing":
        v = orient_wing(pos, opt)
    elif kind == "weapon":
        v = orient_weapon(pos, opt)
    else:
        v = orient_car(pos, opt)
        v *= opt["length"] / (v[:, 1].max() - v[:, 1].min())
    print(f"{name}: {len(tris)} üçgen, doku {tex.shape[1]}", flush=True)
    samples = surface_samples(v, uv, tris, tex)
    wv, wt = weld(v, tris)
    lv, lt = decimate(wv, wt, TARGET[kind])
    meta = {"name": name, "kind": kind}
    b0 = b1 = w0 = None
    parts = []
    if kind in ("char", "npc"):
        th = opt["height"]
        lv, b0, b1, w0, jp, palms = auto_rig(lv, lt, th, name, arms=kind == "char")
        meta["palm_r"], meta["palm_l"] = palms["r"], palms["l"]
        meta["bones"] = [{"name": b, "parent": p, "head": [float(x) for x in jp[i]]} for i, (b, p) in enumerate(zip(BONES, PARENT))]
        meta["height"] = th
    elif kind == "wing":
        # iki kanat ayrı parça (kökte çırpılır): sağ kanat −X, sol +X
        c = lv[lt].mean(axis=1)
        right = c[:, 0] < 0
        lt = np.concatenate([lt[right], lt[~right]])
        nr = int(right.sum())
        rootz = float(np.median(lv[np.abs(lv[:, 0]) < 0.08 * opt["span"], 2])) if (np.abs(lv[:, 0]) < 0.08 * opt["span"]).sum() else 0.0
        parts = [{"name": "wing_r", "start": 0, "count": nr, "pivot": [-0.04, 0.0, rootz]},
                 {"name": "wing_l", "start": nr, "count": int(len(lt) - nr), "pivot": [0.04, 0.0, rootz]}]
        meta["length"] = opt["span"]
    elif kind == "weapon":
        meta["tip"] = float(lv[:, 0].max())
        meta["back"] = float(lv[:, 0].min())
        if "mag" in opt:
            # şarjör üçgenleri ham koordinatta kutu içinde (ağırlık merkezi); düşük poligonda aynı dönüşümün tersiyle
            x0, x1, y0, y1 = opt["mag"]
            raw = from_weapon_space(lv, opt)
            c = raw[lt].mean(axis=1)
            m = (c[:, 0] >= x0) & (c[:, 0] <= x1) & (c[:, 1] >= y0) & (c[:, 1] <= y1)
            if m.sum() > 8:
                lt = np.concatenate([lt[~m], lt[m]])
                parts.append({"name": "mag", "start": int((~m).sum()), "count": int(m.sum()),
                              "pivot": [float(x) for x in lv[np.unique(lt[-int(m.sum()):])].mean(axis=0)]})
        meta["muzzle"] = [float(lv[:, 0].max()), 0.0, float(np.median(lv[lv[:, 0] > lv[:, 0].max() - 0.04, 2]))]
    else:
        L = opt["length"]
        moto = opt["wheels"] == "moto"
        # tekerlekler gerçek geometriden: yere değen köşe kümeleri (ön y<0, arka y>0) merkezin y'sini, yan kesitte en
        # çok köşenin oturduğu çember (lastik sırtı) yarıçapı verir; tekerleğin merkezi yerden yarıçap kadar yukarıda
        zmin = lv[:, 2].min()
        ground = lv[lv[:, 2] < zmin + 0.012 * L]
        xs = np.abs(lv[:, 0])
        outer = xs < 0.11 * L / 2.3 if moto else xs > 0.55 * xs.max()
        cys = []
        for sgn in (-1, 1):
            g = ground[np.sign(ground[:, 1]) == sgn]
            cys.append(float((g[:, 1].min() + g[:, 1].max()) / 2))
        # iki tekerlek aynı yarıçapta: ikisinin lastik sırtı birlikte puanlanır (tek tekerlekte göbek halkası kazanabiliyor)
        best, br = -1.0, 0.1 * L
        for r in np.linspace(0.08 * L, 0.26 * L, 90):
            score = 0.0
            for cy in cys:
                d = np.hypot(lv[:, 1] - cy, lv[:, 2] - zmin - r)
                score += np.sum(outer & (np.abs(d - r) < 0.006 * L)) / r
            if score > best:
                best, br = score, float(r)
        wheels = [("wheel_f", cys[0], br), ("wheel_r", cys[1], br)]
        for wname, cy, r in wheels:
            print(f"  {wname}: y={cy:.2f} r={r:.2f}", flush=True)
        c = lv[lt].mean(axis=1)
        cx = np.abs(c[:, 0])
        sel = []
        taken = np.zeros(len(lt), bool)
        for wname, cy, r in wheels:
            inside = (np.hypot(c[:, 1] - cy, c[:, 2] - zmin - r) < r * 1.01) & ~taken
            inside &= (cx < 0.11 * L / 2.3) if moto else (cx > 0.55 * np.abs(lv[:, 0]).max())
            taken |= inside
            sel.append(inside)
        body = ~taken
        # tekerlekle gövdenin ortak köşeleri tekerlek için kopyalanır: dönen tekerlek gövdeyi esnetmez
        used_body = np.zeros(len(lv), bool)
        used_body[lt[body].ravel()] = True
        extra = []
        for m in sel:
            tri = lt[m]
            shared = np.unique(tri[used_body[tri]])
            remap = {int(v): len(lv) + len(extra) + i for i, v in enumerate(shared)}
            extra.extend(int(v) for v in shared)
            if remap:
                f = np.vectorize(lambda v: remap.get(int(v), int(v)))
                lt[m] = f(tri)
        if extra:
            lv = np.concatenate([lv, lv[np.array(extra)]])
        order = np.concatenate([np.where(body)[0]] + [np.where(m)[0] for m in sel])
        lt = lt[order]
        parts = [{"name": "body", "start": 0, "count": int(body.sum()), "pivot": [0, 0, 0]}]
        start = int(body.sum())
        for (wname, cy, r), m in zip(wheels, sel):
            parts.append({"name": wname, "start": start, "count": int(m.sum()), "pivot": [0.0, cy, float(zmin + r)]})
            start += int(m.sum())
        meta["length"] = L
    ln = vertex_normals(lv, lt)
    res, size = BAKE[kind]
    vmap, idx, nuv = unwrap(lv, lt, res)
    # yeniden boyama için köşe öznitelikleri: dışbükeylik, göreli yükseklik, karanlık (kapüşon içi)
    conv = smooth_vertex(lv, lt, vertex_convexity(lv, lt, ln), 1)
    zrel = (lv[:, 2] - lv[:, 2].min()) / max(np.ptp(lv[:, 2]), 1e-6)
    dark = hood_inside(lv, lt, ln, opt["height"]) if opt.get("hood") else np.zeros(len(lv))
    vattr = np.stack([conv, zrel, dark], 1)
    img, nimg = bake(lv[vmap], ln[vmap], idx, nuv, samples, res, CAVITY[kind], normals=True, vattr=vattr[vmap])
    meta["parts"] = parts
    meta["tex"] = name + ".webp"
    os.makedirs(OUT, exist_ok=True)
    write_zxm(os.path.join(OUT, name + ".zxm"), meta, lv[vmap], ln[vmap], nuv, idx,
              None if b0 is None else b0[vmap], None if b1 is None else b1[vmap], None if w0 is None else w0[vmap])
    save_tex(os.path.join(OUT, name + ".webp"), img, size)
    Image.fromarray(nimg).resize((NSIZE[kind], NSIZE[kind]), Image.LANCZOS).save(os.path.join(OUT, name + "_n.webp"), "WEBP", quality=90, method=6)
    print(f"  → {len(idx)} üçgen, {len(vmap)} köşe, {time.time() - t0:.0f} sn", flush=True)


def from_weapon_space(lv, opt):
    """orient_weapon'ın tersi (şarjör kutusu optimize model ölçeğinde tanımlı)."""
    p = lv / (opt["len"] / 1.25)
    if opt["axis"] == "Y_UP":
        raw = np.stack([p[:, 2], p[:, 0], p[:, 1]], axis=1)
    elif opt["axis"] == "X_LEFT":
        raw = np.stack([-p[:, 0], p[:, 2], -p[:, 1]], axis=1)
    else:
        raw = p
    return raw + np.array(opt["grip"])


if __name__ == "__main__":
    base = sys.argv[1]
    only = sys.argv[2].split(",") if len(sys.argv) > 2 else None
    for kind, table in (("char", CHARS), ("npc", NPCS), ("wing", WINGS), ("weapon", WEAPONS), ("car", CARS)):
        for name, (rel, opt) in table.items():
            if only and name not in only:
                continue
            build(os.path.join(base, rel), name, kind, opt)
