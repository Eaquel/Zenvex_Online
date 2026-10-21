"""Köpekler (evcil dog0/dog5/dog10 ve zombi köpek) için iskeletli mesh animasyonları (mesh_rig motoru).

Yandan görünüm (sola bakar): omurga iki kemik (ön/arka gövde; dörtnalda bükülüp açılır), boyun + kafa, iki kemikli
kuyruk (gecikmeli dalga), dört bacak (kalça/omuz → diz/topuk → pati). Uzak bacaklar gövdenin arkasında, yakınlar
önünde çizilir; bacak üstleri gövdeyle yumuşakça kaynaşır. Patiler zemine oturtulur (yer teması).

Animasyonlar: idle 4, walk 8 (tırıs: çapraz çiftler), run 8 (dörtnal: omurga bükülür, havada kalış), attack 6
(çömel → atıl → ısır → geri), hurt 2, death 6 (dizler çöker, yan yatar).

python3 tools/pixel/dog_mesh.py <taban.png> pet|zombie <çıktı dizini> [önizleme.png]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mesh_rig import Part, Skeleton, bone_xform, dist_weights, ease, finish, splat, sstep  # noqa: E402

FRAMES = {"idle": 4, "walk": 8, "run": 8, "attack": 6, "hurt": 2, "death": 6}
MPP = 0.0066
PAD = 40

SPECS = {
    "pet": dict(
        head=[(34, 30), (50, 0), (72, 0), (82, 16), (86, 30), (80, 38), (72, 48), (64, 56), (54, 54), (46, 46), (36, 42)],
        neck=(76, 44), nose=(40, 34), withers=(98, 58), mid=(130, 60), troot=(176, 62), tmid=(196, 64), ttip=(212, 40),
        tail=[(172, 52), (190, 56), (200, 46), (204, 36), (216, 36), (214, 62), (200, 74), (186, 78), (172, 72)],
        belly=82,
        legs=[  # (çokgen, kalça, diz, pati, uzak mı, ön mü)
            ([(74, 78), (90, 78), (90, 90), (89, 112), (88, 118), (84, 126), (70, 126), (72, 116), (78, 112), (78, 92)],
             (84, 76), (83, 106), (78, 123), True, True),
            ([(90, 74), (106, 74), (105, 96), (104, 112), (103, 120), (102, 127), (86, 127), (88, 118), (91, 112), (91, 90)],
             (97, 74), (97, 108), (94, 124), False, True),
            ([(128, 78), (146, 76), (152, 88), (160, 98), (160, 112), (152, 118), (150, 124), (136, 124), (140, 114),
              (146, 108), (146, 100), (136, 90)], (142, 74), (154, 104), (144, 121), True, False),
            ([(150, 58), (176, 56), (182, 80), (182, 104), (180, 127), (166, 127), (168, 110), (166, 98), (158, 92), (152, 84)],
             (165, 62), (176, 100), (172, 124), False, False),
        ]),
    "zombie": dict(
        head=[(6, 50), (20, 14), (40, 6), (56, 10), (62, 26), (64, 46), (60, 62), (48, 72), (34, 80), (22, 76), (10, 62)],
        neck=(60, 44), nose=(10, 52), withers=(86, 40), mid=(130, 44), troot=(190, 46), tmid=(206, 60), ttip=(230, 66),
        tail=[(186, 38), (200, 44), (214, 58), (232, 64), (230, 72), (214, 84), (204, 76), (194, 64), (186, 52)],
        belly=70,
        legs=[
            ([(72, 66), (96, 66), (94, 80), (84, 90), (80, 100), (74, 112), (72, 124), (48, 124), (50, 116), (62, 110),
              (68, 96), (74, 84)], (86, 66), (78, 96), (58, 121), True, True),
            ([(98, 58), (122, 58), (120, 80), (116, 96), (114, 110), (112, 128), (94, 128), (98, 116), (102, 104),
              (104, 88), (100, 72)], (110, 60), (110, 100), (103, 125), False, True),
            ([(144, 58), (166, 58), (172, 80), (180, 96), (178, 110), (172, 118), (168, 124), (150, 124), (156, 114),
              (162, 104), (160, 92), (150, 80)], (156, 58), (172, 98), (160, 122), True, False),
            ([(170, 46), (190, 44), (198, 70), (206, 90), (212, 110), (214, 128), (196, 128), (200, 112), (196, 98),
              (186, 90), (178, 82), (174, 66)], (178, 50), (202, 94), (206, 125), False, False),
        ]),
}


def poly_mask(shape, poly):
    m = Image.new("L", (shape[1], shape[0]), 0)
    ImageDraw.Draw(m).polygon([(x + PAD, y + PAD) for x, y in poly], fill=255)
    return np.asarray(m) > 0


class Dog:
    def __init__(self, src, spec):
        a = np.pad(np.asarray(Image.open(src).convert("RGBA")), ((PAD, PAD), (PAD, PAD), (0, 0)))
        S = spec
        P = lambda p: (p[0] + PAD, p[1] + PAD)  # noqa: E731
        J = {"root": (None, P(S["mid"])), "front": ("root", P(S["mid"])), "rear": ("root", P(S["mid"])),
             "withers": ("front", P(S["withers"])), "neck": ("withers", P(S["neck"])), "nose": ("neck", P(S["nose"])),
             "troot": ("rear", P(S["troot"])), "tmid": ("troot", P(S["tmid"])), "ttip": ("tmid", P(S["ttip"]))}
        for i, (_, hip, knee, paw, far, front) in enumerate(S["legs"]):
            J[f"hip{i}"] = ("withers" if front else "rear", P(hip))
            J[f"kn{i}"] = (f"hip{i}", P(knee))
            J[f"paw{i}"] = (f"kn{i}", P(paw))
        self.J = J
        self.sk = Skeleton(J, yaw_deg=90.0, k=0.35)
        self.B = {"spF": ("front", "withers"), "spR": ("rear", "troot"), "neck": ("withers", "neck"), "head": ("neck", "nose"),
                  "t1": ("troot", "tmid"), "t2": ("tmid", "ttip")}
        for i in range(4):
            self.B[f"th{i}"] = (f"hip{i}", f"kn{i}")
            self.B[f"sh{i}"] = (f"kn{i}", f"paw{i}")
        jp = lambda n: J[n][1]  # noqa: E731
        opaque = a[..., 3] > 0
        ys = np.mgrid[0:a.shape[0], 0:a.shape[1]][0]
        head_m = poly_mask(a.shape, S["head"]) & opaque
        tail_m = poly_mask(a.shape, S["tail"]) & opaque & ~head_m
        belly = S["belly"] + PAD
        near_all = np.zeros_like(opaque)
        for poly, *_r, far, _f in S["legs"]:
            if not far:
                near_all |= poly_mask(a.shape, poly)
        legs_m = []
        for poly, hip, knee, paw, far, front in S["legs"]:
            m = poly_mask(a.shape, poly) & opaque
            if far:
                m &= ~near_all
            legs_m.append(m)
        any_leg_low = np.zeros_like(opaque)
        for m in legs_m:
            any_leg_low |= m & (ys >= belly)
        # karnın altındaki sahipsiz pikseller (pati/tüy kenarı) en yakın bacağa
        from scipy import ndimage
        rest = opaque & ~head_m & ~tail_m & ~any_leg_low & (ys >= belly + 8)
        if rest.any():
            dists = np.stack([ndimage.distance_transform_edt(~m) for m in legs_m])
            own = np.argmin(dists, 0)
            for i in range(4):
                legs_m[i] |= rest & (own == i) & (dists[i] <= 8)
        # kuyruk çokgeninin dışında kalan ince kuyruk kılları da kuyruğa (yoksa yerinde asılı kalır)
        xs_g = np.mgrid[0:a.shape[0], 0:a.shape[1]][1]
        tail_m |= opaque & ~head_m & (xs_g > P(S["troot"])[0] + 6) & (ys < belly + 4)
        for m in legs_m:
            tail_m &= ~m
        body_m = opaque & ~head_m & ~tail_m
        for m in legs_m:
            body_m &= ~(m & (ys >= belly))

        def w_body(px, py):
            D = dist_weights(px, py, [(jp("front"), jp("withers")), (jp("rear"), jp("troot"))], 14.0)
            nk = 1 - sstep(jp("withers")[0] - 22, jp("withers")[0] + 4, px)
            return ["neck", "spF", "spR"], [nk, (1 - nk) * D[0], (1 - nk) * D[1]]

        def w_tail(px, py):
            D = dist_weights(px, py, [(jp("troot"), jp("tmid")), (jp("tmid"), jp("ttip"))], 5.0)
            return ["t1", "t2"], [D[0], D[1]]

        def w_leg(i, front):
            def f(px, py):
                D = dist_weights(px, py, [(jp(f"hip{i}"), jp(f"kn{i}")), (jp(f"kn{i}"), jp(f"paw{i}"))], 5.0)
                b = 1 - sstep(belly - 6, belly + 6, py)
                return ["spF" if front else "spR", f"th{i}", f"sh{i}"], [b, (1 - b) * D[0], (1 - b) * D[1]]
            return f

        self.parts = {"body": Part("body", a, body_m, w_body), "head": Part("head", a, head_m, lambda px, py: (["head"], [np.ones_like(px)])),
                      "tail": Part("tail", a, tail_m, w_tail)}
        for i, (poly, hip, knee, paw, far, front) in enumerate(S["legs"]):
            self.parts[f"leg{i}"] = Part(f"leg{i}", a, legs_m[i], w_leg(i, front))
        self.far = [L[4] for L in S["legs"]]
        self.front = [L[5] for L in S["legs"]]
        self.ground = max(L[3][1] for L in S["legs"]) + PAD + 3
        self.cx = (min(L[1][0] for L in S["legs"]) + max(L[1][0] for L in S["legs"])) / 2 + PAD
        self.shape = a.shape

    def render(self, pose):
        sk = self.sk
        R = {j: sk.rot(*(tuple(v) + (0,) * (3 - len(v)))) for j, v in pose.get("rot", {}).items()}
        pos, G = sk.fk(R)
        if pose.get("ground", True):
            low = max(pos[f"paw{i}"][1] for i in range(4))
            dy = (self.ground - 3) - low - pose.get("lift", 0.0)
        else:
            dy = pose.get("dy", 0.0)
        sh = np.array([pose.get("dx", 0.0), dy, 0.0])
        p2 = {n: sk.proj(p + sh) for n, p in pos.items()}
        T = {b: bone_xform(self.J[ja][1], self.J[jb][1], p2[ja], p2[jb]) for b, (ja, jb) in self.B.items()}
        H, W = self.shape[:2]
        canvas = np.zeros((H + 40, W, 4), np.uint8)
        order = ["tail"] + [f"leg{i}" for i in range(4) if self.far[i]] + ["body"] + \
                [f"leg{i}" for i in range(4) if not self.far[i]] + ["head"]
        for n in order:
            X, Y = self.parts[n].deform(T)
            splat(canvas, X, Y, self.parts[n].col, 0, 20)
        return finish(canvas, 12)


def leg_cycle(t, front, gait):
    """(kalça, diz) derece. Kalça + = pati öne; diz + = alt bacak geriye/yukarı (bükülme)."""
    if gait == "walk":
        hip = [24, 12, -2, -14, -22, -8, 14, 28] if front else [20, 8, -4, -14, -20, -4, 12, 24]
        kn = [2, 4, 6, 8, 18, 56, 46, 10] if front else [0, 4, 6, 10, 20, 50, 40, 6]
    else:  # dörtnal
        hip = [44, 26, 4, -26, -44, -28, 6, 40] if front else [34, 20, 0, -24, -34, -16, 12, 36]
        kn = [6, 8, 16, 26, 50, 92, 70, 20] if front else [4, 8, 14, 30, 56, 84, 50, 14]
    return ease(hip, t), ease(kn, t)


def pose_for(dog, anim, f, n):
    t = f / n
    P = {"rot": {}}
    r = P["rot"]
    if anim == "idle":
        b = math.sin(2 * math.pi * t)
        r["neck"] = (2 * b, 0, 0)
        r["troot"] = (6 * math.sin(2 * math.pi * t), 0, 0)
        r["tmid"] = (8 * math.sin(2 * math.pi * t - 1.0), 0, 0)
        r["front"] = (0.8 * b, 0, 0)
        P["lift"] = 0
    elif anim in ("walk", "run"):
        if anim == "walk":
            ph = [0.5, 0.0, 0.0, 0.5]  # tırıs: yakın ön + uzak arka birlikte
        else:
            ph = [0.06, 0.0, 0.52, 0.42]  # dörtnal: önler ardışık, arkalar ardışık
        for i in range(4):
            hp, kn = leg_cycle(t + ph[i], dog.front[i], anim)
            r[f"hip{i}"] = (hp, 0, 0)
            r[f"kn{i}"] = (-kn if dog.front[i] else -kn, 0, 0)
        if anim == "walk":
            r["front"] = (1.5 * math.sin(4 * math.pi * t), 0, 0)
            r["neck"] = (-3 * math.sin(4 * math.pi * t + 0.6), 0, 0)
            P["lift"] = 1.0 * (1 + math.cos(4 * math.pi * t)) / 2
            wag = 14
        else:
            # omurga: toplanma (bacaklar altta) ↔ uzanma (bacaklar açık)
            s = math.cos(2 * math.pi * (t - 0.15))
            r["front"] = (-7 * s, 0, 0)
            r["rear"] = (7 * s, 0, 0)
            r["neck"] = (6 * s, 0, 0)
            P["lift"] = ease([0, 0, 2, 6, 3, 0, 1, 2], t)
            wag = 8
        r["troot"] = (wag * math.sin(2 * math.pi * t) - (10 if anim == "run" else 0), 0, 0)
        r["tmid"] = (wag * 1.2 * math.sin(2 * math.pi * t - 1.2), 0, 0)
    elif anim == "attack":
        fr = [0, -10, 30, 34, 14, 4][f]
        bk = [0, 14, -26, -28, -10, -2][f]
        for i in range(4):
            r[f"hip{i}"] = ((fr if dog.front[i] else bk), 0, 0)
            r[f"kn{i}"] = (([-10, -30, -4, -4, -10, -4] if dog.front[i] else [-6, -40, -4, -4, -8, -4])[f], 0, 0)
        r["front"] = ([0, 6, -8, -10, -4, 0][f], 0, 0)
        r["neck"] = ([0, 10, -18, -24, -8, 0][f], 0, 0)
        r["troot"] = ([0, 10, -6, -8, 4, 0][f], 0, 0)
        P["dx"] = [0, 3, -10, -12, -6, -2][f]
        P["lift"] = [0, -2, 3, 1, 0, 0][f]
    elif anim == "hurt":
        r["neck"] = ([12, 18][f], 0, 0)
        r["front"] = ([4, 7][f], 0, 0)
        r["troot"] = (-16, 0, 0)
        for i in range(4):
            r[f"kn{i}"] = (-[10, 16][f], 0, 0)
        P["dx"] = [3, 5][f]
    elif anim == "death":
        s = [0.0, 0.3, 0.6, 0.85, 1.0, 1.0][f]
        for i in range(4):
            r[f"hip{i}"] = ((30 if dog.front[i] else -30) * s, 70 * s, 0)
            r[f"kn{i}"] = (-60 * s * (1 - s) * 2, 0, 0)
        r["front"] = (-6 * s, 0, 0)
        r["neck"] = (-24 * s, 0, 0)
        r["troot"] = (-20 * s, 0, 0)
        P["ground"] = False
        P["dy"] = [0, 6, 14, 22, 26, 27][f]
    return P


def main(src, kind, out, prev=None):
    os.makedirs(out, exist_ok=True)
    for fn in os.listdir(out):
        if fn.endswith(".png"):
            os.remove(os.path.join(out, fn))
    dog = Dog(src, SPECS[kind])
    half = int(math.ceil(max(dog.cx, dog.shape[1] - dog.cx)))
    sheet = []
    for anim, n in FRAMES.items():
        row = []
        for f in range(n):
            img = dog.render(pose_for(dog, anim, f, n))
            sym = np.zeros((img.shape[0], 2 * half, 4), np.uint8)
            x0 = half - int(round(dog.cx))
            sym[:, x0:x0 + img.shape[1]] = img
            Image.fromarray(sym).save(os.path.join(out, f"{anim}_0_{f}.png"))
            row.append(sym)
        sheet.append(row)
    json.dump({"anchor": [half, dog.ground + 20 - 3], "mpp": MPP, "frames": FRAMES}, open(os.path.join(out, "meta.json"), "w"))
    if prev:
        h, w = sheet[0][0].shape[:2]
        S = Image.new("RGBA", (w * 8, h * len(sheet)), (96, 120, 96, 255))
        d = ImageDraw.Draw(S)
        for j, row in enumerate(sheet):
            for i, im in enumerate(row):
                S.alpha_composite(Image.fromarray(im), (i * w, j * h))
            d.line([(0, j * h + dog.ground + 17), (w * 8, j * h + dog.ground + 17)], fill=(60, 80, 60, 255))
        S.resize((S.width * 2, S.height * 2), Image.NEAREST).save(prev)
    print(kind, out, sum(FRAMES.values()))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else None)
