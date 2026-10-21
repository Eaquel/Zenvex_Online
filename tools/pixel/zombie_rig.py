"""Zombiler: tek piksel görselden (yandan, sağa bakan; çıktı sola bakar) iskeletli örgü (mesh skinning) animasyonu.

Her kaynak pikseli Scale2x³ ile 8×8 alt örneğe bölünür; her alt örnek kemik ağırlıklarıyla (eklemlerde yumuşak geçiş,
doğrusal karışım — linear blend skinning) yeni konumuna taşınır ve 2× piksel ızgarasına yazılır. Böylece kollar/bacaklar
eklemlerden kırılmadan bükülür, piksel kenarları bulanıklaşmaz (RotSprite benzeri).

Katmanlar: uzak kol/bacak (katman < 0) gövdenin arkasında, yakın kol/bacak (katman > 0) önünde çizilir. Yakın uzvun
örttüğü gövde bölgesi ('fill' çokgenleri) en yakın gövde pikselinin rengiyle (biraz gölgeli) doldurulur; uzuv
oynayınca altta delik kalmaz.

Rig tanımları: art/v7/zombies/rigs.json (kemik: ad, ebeveyn, eklem noktası, çokgen, katman; ayaklar, çapa, stil).

python3 tools/pixel/zombie_rig.py <rig adı|all> <çıktı kökü (ör. /home/user/zx_pix)> [önizleme dizini]
Çıktı: <kök>/z_<ad>/<anim>_0_<kare>.png + meta.json (çapa, mpp) — pack.py collect_pixel_hero ile okunur.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cutout_rig import clean as drop_specks, scale2x  # noqa: E402

ANIMS = {"idle": 4, "walk": 8, "attack": 6, "hurt": 2, "death": 6}
UP = 8   # alt örnekleme (kaynak pikseli başına 8×8)
OUT = 2  # çıktı ızgarası: kaynak pikselinin 2 katı
ART = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "art", "v7", "zombies")


# ---------------------------------------------------------------- matris yardımcıları (3×3 afin)
def T(dx, dy):
    return np.array([[1, 0, dx], [0, 1, dy], [0, 0, 1]], float)


def R(deg, p):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return T(p[0], p[1]) @ np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], float) @ T(-p[0], -p[1])


def S(sx, sy, p):
    return T(p[0], p[1]) @ np.diag([sx, sy, 1.0]) @ T(-p[0], -p[1])


def smooth(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


def poly_mask(w, h, poly):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).polygon([tuple(p) for p in poly], fill=255, outline=255)
    return np.array(m) > 0


# ---------------------------------------------------------------- rig
class Rig:
    def __init__(self, spec):
        img = Image.open(os.path.join(ART, spec["src"])).convert("RGBA")
        if "crop" in spec:
            img = img.crop(spec["crop"])
        a = np.array(img)
        a[a[..., 3] < 128] = 0
        a[..., 3] = np.where(a[..., 3] > 0, 255, 0)
        self.src = a
        self.spec = spec
        h, w = a.shape[:2]
        self.h, self.w = h, w
        bones = spec["bones"]
        self.names = [b[0] for b in bones]
        self.parent = {b[0]: b[1] for b in bones}
        self.pivot = {b[0]: np.array(b[2], float) for b in bones}
        self.layer = {b[0]: b[4] for b in bones}
        B = len(bones)
        bi = {n: i for i, n in enumerate(self.names)}
        solid = a[..., 3] > 0
        ys, xs = np.mgrid[0:h, 0:w]
        # birincil kemik: çokgensizler varsayılan (bel çizgisinin altı kök, üstü göğüs); sonra çokgenler sırayla
        lab = np.where(ys >= spec["waist"], bi["root"], bi.get("chest", bi["root"]))
        for name, _, _, poly, _ in bones:
            if poly:
                lab = np.where(poly_mask(w, h, poly), bi[name], lab)
        # varsayılan (gövde) etiketli ama gövdeden kopuk adacıklar → en yakın çokgen kemiği (uzuv kenarı artıkları)
        dflt = solid & np.isin(lab, [bi["root"], bi.get("chest", bi["root"])])
        cl, cn = ndimage.label(dflt)
        if cn > 1:
            sizes = ndimage.sum(dflt, cl, range(1, cn + 1))
            stray = dflt & (cl != (np.argmax(sizes) + 1))
            limb = solid & ~dflt
            if stray.any() and limb.any():
                _, (iy, ix) = ndimage.distance_transform_edt(~limb, return_indices=True)
                lab = np.where(stray, lab[iy, ix], lab)
        self.lab = lab
        # ağırlıklar: her eklem çevresinde (yarıçap r) çocuk ↔ ebeveyn yumuşak geçiş
        W = np.zeros((h, w, B))
        W[ys, xs, lab] = 1.0
        rad = spec.get("blend", 4.0)
        for name in self.names:
            par = self.parent[name]
            if par is None:
                continue
            c, p = bi[name], bi[par]
            d = np.hypot(xs + 0.5 - self.pivot[name][0], ys + 0.5 - self.pivot[name][1])
            k = 0.5 * smooth(d / rad)
            inc = lab == c
            inp = lab == p
            W[inc, p] = 0.5 - k[inc]
            W[inc, c] = 0.5 + k[inc]
            W[inp, c] = 0.5 - k[inp]
            W[inp, p] = 0.5 + k[inp]
        W /= np.maximum(W.sum(-1, keepdims=True), 1e-6)  # aynı ebeveynin birden çok ekleminde toplam 1'i aşmasın
        # dolgu: yakın uzvun örttüğü gövde bölgesi
        fill_col = np.zeros_like(a)
        fill_lab = np.full((h, w), -1)
        for poly, bone in spec.get("fill", []):
            m = poly_mask(w, h, poly) & solid & np.isin(lab, [bi[n] for n in self.names if self.layer[n] > self.layer[bone]])
            if not m.any():
                continue
            own = solid & (lab == bi[bone])
            _, (iy, ix) = ndimage.distance_transform_edt(~own, return_indices=True)
            col = a[iy, ix].astype(float)
            col[..., :3] *= 0.78
            fill_col[m] = col[m].astype(np.uint8)
            fill_lab[m] = bi[bone]
        # alt örnekler
        up = scale2x(scale2x(scale2x(a)))
        sy, sx = np.nonzero(up[..., 3] > 0)
        py, px = sy // UP, sx // UP
        self.P = np.stack([(sx + 0.5) / UP, (sy + 0.5) / UP], 1)
        self.C = up[sy, sx]
        self.Wt = W[py, px]
        self.L = np.array([self.layer[self.names[i]] for i in range(B)])[lab[py, px]]
        # dolgu örnekleri (blok renk)
        fy, fx = np.nonzero(fill_lab >= 0)
        if len(fy):
            oy, ox = np.mgrid[0:UP, 0:UP]
            oy, ox = oy.ravel(), ox.ravel()
            FY = (fy[:, None] * UP + oy[None]).ravel()
            FX = (fx[:, None] * UP + ox[None]).ravel()
            fby, fbx = FY // UP, FX // UP
            P2 = np.stack([(FX + 0.5) / UP, (FY + 0.5) / UP], 1)
            W2 = np.zeros((len(FY), B))
            W2[np.arange(len(FY)), fill_lab[fby, fbx]] = 1.0
            L2 = np.array([self.layer[self.names[i]] for i in range(B)])[fill_lab[fby, fbx]] - 0.5
            self.P = np.concatenate([P2, self.P])
            self.C = np.concatenate([fill_col[fby, fbx], self.C])
            self.Wt = np.concatenate([W2, self.Wt])
            self.L = np.concatenate([L2, self.L])
        self.anchor = np.array(spec["anchor"], float)
        self.feet = {k: np.array(v, float) for k, v in spec.get("feet", {}).items()}
        self.ground = max([v[1] for v in self.feet.values()] + [self.anchor[1]])

    def mats(self, pose):
        G = T(pose.get("dx", 0.0), pose.get("dy", 0.0)) @ R(pose.get("rot", 0.0), pose.get("rot_p", self.anchor)) \
            @ S(pose.get("sx", 1.0), pose.get("sy", 1.0), self.anchor)
        M = {}
        for n in self.names:  # ebeveynler önce listelenir
            par = self.parent[n]
            base = G if par is None else M[par]
            M[n] = base @ R(pose.get(n, 0.0), self.pivot[n])
        return M

    def render(self, pose, canvas, origin):
        M = self.mats(pose)
        stack = np.stack([M[n] for n in self.names])  # (B,3,3)
        P = self.P
        X = stack[:, 0, 0, None] * P[None, :, 0] + stack[:, 0, 1, None] * P[None, :, 1] + stack[:, 0, 2, None]
        Y = stack[:, 1, 0, None] * P[None, :, 0] + stack[:, 1, 1, None] * P[None, :, 1] + stack[:, 1, 2, None]
        x = (X.T * self.Wt).sum(1)
        y = (Y.T * self.Wt).sum(1)
        if pose.get("lock", True) and self.feet:
            fy = []
            for bone, pt in self.feet.items():
                q = M[bone] @ np.array([pt[0], pt[1], 1.0])
                fy.append(q[1])
            y = y + (self.ground - max(fy))
        if pose.get("floor"):
            y = y + min(0.0, self.ground + 1 - y.max())
        H, Wd = canvas.shape[:2]
        dx = np.floor((x + origin[0]) * OUT).astype(int)
        dy = np.floor((y + origin[1]) * OUT).astype(int)
        ok = (dx >= 0) & (dx < Wd) & (dy >= 0) & (dy < H)
        for L in sorted(set(self.L.tolist())):
            m = ok & (self.L == L)
            canvas[dy[m], dx[m]] = self.C[m]
        return canvas


# ---------------------------------------------------------------- animasyonlar (açı: + ekranda saat yönü; ileri = -)
def anim_pose(style, anim, f, n):
    st = style
    ph = 2 * math.pi * f / n
    p = {}
    kind = st["kind"]
    if anim == "idle":
        b = math.sin(ph)
        p.update(chest=1.5 * b, head=2.5 * math.sin(ph + 0.8), arm_n=-2.5 * b, arm_f=2.0 * b, fore_n=-3 * b, fore_f=2 * b,
                 sy=1.0 + 0.012 * b)
        if kind == "dark":
            p.update(lock=False, dy=-2.0 - 1.5 * math.sin(ph))
    elif anim == "walk":
        A, B = st["stride"], st["knee"]
        s, c = math.sin(ph), math.cos(ph)
        p["thigh_n"] = -A * s + st.get("thigh_n0", 0)
        p["thigh_f"] = A * s + st.get("thigh_f0", 0)
        p["shin_n"] = B * max(0.0, c) ** 1.3
        p["shin_f"] = B * max(0.0, -c) ** 1.3
        sw = st.get("arm_swing", 6)
        p["arm_n"] = sw * s + st.get("arm_n0", 0)
        p["arm_f"] = -sw * s + st.get("arm_f0", 0)
        p["fore_n"] = st.get("fore_swing", 3) * math.sin(ph + 0.9) + st.get("fore_n0", 0)
        p["fore_f"] = -st.get("fore_swing", 3) * math.sin(ph + 0.9) + st.get("fore_f0", 0)
        p["chest"] = st.get("lean", 0) + st.get("roll", 2) * math.sin(2 * ph)
        p["head"] = st.get("head_bob", 4) * math.sin(2 * ph + 1.2) + st.get("head0", 0)
        if kind == "dark":
            p.update(lock=False, dy=-3.0 - 2.0 * math.sin(2 * ph), rot=1.5 * math.sin(ph))
        if kind in ("tank", "titan"):
            # ağır adım: basış anında gövde çöker ve titrer
            imp = max(0.0, math.cos(2 * ph)) ** 4
            p["sy"] = 1.0 - 0.03 * imp
            p["sx"] = 1.0 + 0.015 * imp
    elif anim == "attack":
        # 0-1 kurulma, 2-3 vuruş, 4-5 toparlanma
        k = [0.0, -0.6, -1.0, 1.0, 0.7, 0.25][f]
        if kind in ("tank", "titan"):
            k = [0.0, -0.7, -1.0, 1.0, 0.85, 0.3][f]
            # yarı önden duran iri gövde: iki kol dışa-yukarı kalkar (kükreme), sonra içe doğru yere çakılır
            p["arm_n"] = 78 * max(0.0, -k) - 22 * max(0.0, k) + st.get("arm_n0", 0)
            p["arm_f"] = -85 * max(0.0, -k) + 26 * max(0.0, k) + st.get("arm_f0", 0)
            p["fore_n"] = 35 * max(0.0, -k) - 8 * max(0.0, k)
            p["fore_f"] = -35 * max(0.0, -k) + 8 * max(0.0, k)
            p["chest"] = -10 * max(0.0, -k) + 16 * max(0.0, k) + st.get("lean", 0)
            p["head"] = -6 * max(0.0, -k) + 8 * max(0.0, k)
            p["sy"] = 1.0 - 0.04 * max(0.0, k)
            p["thigh_n"] = -6 * max(0.0, k)
        else:
            p["arm_n"] = -45 * max(0.0, -k) + 30 * max(0.0, k) + st.get("arm_n0", 0)
            p["arm_f"] = -30 * max(0.0, -k) + 24 * max(0.0, k) + st.get("arm_f0", 0)
            p["fore_n"] = -20 * max(0.0, -k) + 18 * max(0.0, k)
            p["fore_f"] = -15 * max(0.0, -k) + 14 * max(0.0, k)
            p["chest"] = -7 * max(0.0, -k) + 13 * max(0.0, k) + st.get("lean", 0)
            p["head"] = -5 * max(0.0, -k) + 10 * max(0.0, k)
            p["dx"] = 4 * max(0.0, k)
            p["thigh_n"] = -10 * max(0.0, k)
            p["thigh_f"] = 6 * max(0.0, k)
        if kind == "dark":
            p.update(lock=False, dy=-3.0)
    elif anim == "hurt":
        k = [1.0, 0.55][f]
        p.update(chest=-11 * k + st.get("lean", 0), head=-14 * k, arm_n=-20 * k, arm_f=-24 * k, fore_n=-15 * k, fore_f=-10 * k,
                 dx=-3 * k, thigh_n=-4 * k, shin_n=6 * k)
        if kind == "dark":
            p.update(lock=False, dy=-3.0)
    elif anim == "death":
        t = [0.0, 0.18, 0.42, 0.7, 0.92, 1.0][f]
        e = t * t * (3 - 2 * t)
        fw = st.get("fall_fwd", False)
        p.update(rot=(78 if fw else -82) * e, rot_p=st["toe"] if fw else st["heel"], lock=False, floor=True,
                 chest=-8 * math.sin(math.pi * t), head=-18 * math.sin(math.pi * min(1.0, t * 1.3)) + 6 * e,
                 arm_n=-60 * math.sin(math.pi * min(1.0, t * 1.2)) - 20 * e, arm_f=-50 * math.sin(math.pi * min(1.0, t * 1.1)) - 25 * e,
                 fore_n=-20 * e, fore_f=-15 * e, thigh_n=-22 * e, shin_n=35 * e, thigh_f=-10 * e, shin_f=20 * e,
                 dx=-6 * e)
        if fw:  # öne kapaklanma: bacaklar gövdeyle düz kalır, kollar öne uzanır
            p.update(chest=10 * math.sin(math.pi * t), head=-10 * e, thigh_n=4 * e, shin_n=12 * e, thigh_f=-4 * e, shin_f=10 * e,
                     arm_n=-35 * e, arm_f=-30 * e, fore_n=-10 * e, fore_f=-10 * e, dx=6 * e)
        if kind == "dark":
            p.update(sy=1.0 - 0.25 * e)
    return p


RIGS = json.load(open(os.path.join(ART, "rigs.json")))


def build(name, out_root, preview=None):
    spec = RIGS[name]
    rig = Rig(spec)
    # tuval: ölüm sırasında gövde yere yatar → sola boy kadar, yukarıda kalkan kollar için boşluk
    hb = rig.ground - min(np.nonzero(rig.src[..., 3])[0])
    pad_l, pad_r, pad_t, pad_b = int(hb * 1.05) + 8, 40, 50, 6
    W = (rig.w + pad_l + pad_r) * OUT
    H = (rig.h + pad_t + pad_b) * OUT
    origin = (pad_l, pad_t)
    od = os.path.join(out_root, "z_" + name)
    os.makedirs(od, exist_ok=True)
    for f in os.listdir(od):
        if f.endswith(".png"):
            os.remove(os.path.join(od, f))
    frames = {}
    for anim, n in ANIMS.items():
        for f in range(n):
            pose = anim_pose(spec["style"], anim, f, n)
            cv = np.zeros((H, W, 4), np.uint8)
            rig.render(pose, cv, origin)
            cv = drop_specks(cv, 10)[:, ::-1].copy()  # oyun kuralı: piksel aktörler sola bakar, sağa bakarken aynalanır
            Image.fromarray(cv).save(os.path.join(od, f"{anim}_0_{f}.png"))
            frames.setdefault(anim, []).append(cv)
    ax = W - (rig.anchor[0] + origin[0]) * OUT
    ay = (rig.ground + origin[1]) * OUT
    local_h = hb * OUT
    mpp = spec["world_h"] * 0.016 / local_h
    json.dump({"anchor": [ax, ay], "mpp": mpp, "frames": ANIMS}, open(os.path.join(od, "meta.json"), "w"))
    if preview:
        os.makedirs(preview, exist_ok=True)
        rows = []
        for anim, fl in frames.items():
            bb = None
            for cv in fl:
                b = Image.fromarray(cv).getbbox()
                if b:
                    bb = b if bb is None else (min(bb[0], b[0]), min(bb[1], b[1]), max(bb[2], b[2]), max(bb[3], b[3]))
            rows.append([Image.fromarray(cv).crop(bb) for cv in fl])
        cw = max(sum(i.width + 6 for i in r) for r in rows)
        ch = sum(r[0].height + 6 for r in rows)
        sheet = Image.new("RGBA", (cw, ch), (58, 66, 84, 255))
        y = 0
        for r in rows:
            x = 0
            for im in r:
                sheet.alpha_composite(im, (x, y))
                x += im.width + 6
            y += r[0].height + 6
        sheet.save(os.path.join(preview, f"sheet_{name}.png"))
        gif = [Image.alpha_composite(Image.new("RGBA", (W, H), (58, 66, 84, 255)), Image.fromarray(cv)).convert("P") for cv in frames["walk"]]
        gif[0].save(os.path.join(preview, f"walk_{name}.gif"), save_all=True, append_images=gif[1:], duration=110, loop=0)
    print(name, "mpp", round(mpp, 5), "canvas", W, H)


def debug_regions(name, path):
    rig = Rig(RIGS[name])
    pal = np.array([[230, 60, 60], [60, 200, 90], [70, 120, 240], [240, 200, 60], [200, 80, 220], [60, 220, 220],
                    [250, 140, 40], [150, 150, 150], [120, 60, 30], [255, 255, 255], [40, 90, 40], [90, 40, 120]], np.uint8)
    a = rig.src.copy()
    m = a[..., 3] > 0
    a[m, :3] = (a[m, :3] * 0.45 + pal[rig.lab[m] % len(pal)] * 0.55).astype(np.uint8)
    im = Image.fromarray(a)
    im = im.resize((im.width * 6, im.height * 6), Image.NEAREST)
    d = ImageDraw.Draw(im)
    for n in rig.names:
        x, y = rig.pivot[n] * 6
        d.ellipse([x - 4, y - 4, x + 4, y + 4], outline=(255, 255, 255), width=2)
        d.text((x + 5, y - 5), n, fill=(255, 255, 255))
    for pt in rig.feet.values():
        x, y = pt * 6
        d.rectangle([x - 3, y - 3, x + 3, y + 3], fill=(255, 0, 0))
    bg = Image.new("RGBA", im.size, (40, 44, 56, 255))
    bg.alpha_composite(im)
    bg.save(path)


if __name__ == "__main__":
    which = sys.argv[1]
    out = sys.argv[2]
    prev = sys.argv[3] if len(sys.argv) > 3 else None
    for nm in (RIGS if which == "all" else which.split(",")):
        if prev:
            debug_regions(nm, os.path.join(prev, f"regions_{nm}.png"))
        build(nm, out, prev)
