"""Yandan çizilmiş tek piksel köpek görselinden animasyon (dört ayaklı cut-out rig).

Parçalar: gövde, kafa (boyundan döner), kuyruk (kökünden sallanır), dört bacak (omuz/kalçadan + dizden).
Yürüyüş gerçek köpek adım sırasıyla: arka-yakın, ön-yakın, arka-uzak, ön-uzak (çeyrek faz farkı).
Döndürme cutout_rig'deki Scale2x³ örneklemesiyle (piksel kenarları korunur).

python3 tools/pixel/dog_rig.py <taban.png> <rig adı> <çıktı dizini>
Çıktı: <anim>_<yön>_<kare>.png (yön 0-4; 5-7 paketleyicide aynalanır), meta.json
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cutout_rig import clean, over, rot, rotate_layer  # noqa: E402

ANIMS = {"idle": 4, "walk": 8, "attack": 6, "hurt": 2, "death": 6}
MPP = 0.0066  # kahramanla aynı yoğunluk
PAD = 40

# kaynak koordinatları (240×130 yerel ızgara). Bacak: çokgen, omuz/kalça, diz noktası, karın çizgisi (bunun altı
# gövdeden silinir), arka (uzak) bacak mı
RIGS = {
    "pet": dict(
        head=[(34, 30), (50, 0), (72, 0), (82, 16), (86, 30), (80, 38), (72, 48), (64, 56), (54, 54), (46, 46), (36, 42)],
        neck=(76.0, 44.0),
        tail=[(178, 52), (190, 56), (200, 46), (204, 36), (216, 36), (214, 62), (200, 74), (186, 78), (178, 72)],
        tail_root=(180.0, 62.0),
        legs=[
            dict(poly=[(74, 78), (90, 78), (90, 90), (89, 112), (88, 118), (84, 126), (70, 126), (72, 116), (78, 112), (78, 92)],
                 hip=(84.0, 76.0), knee=(83.0, 106.0), belly=84, far=True, ph=1.5),
            dict(poly=[(90, 74), (106, 74), (105, 96), (104, 112), (103, 120), (102, 127), (86, 127), (88, 118), (91, 112), (91, 90)],
                 hip=(97.0, 74.0), knee=(97.0, 108.0), belly=84, far=False, ph=0.5),
            dict(poly=[(128, 78), (146, 76), (152, 88), (160, 98), (160, 112), (152, 118), (150, 124), (136, 124), (140, 114),
                       (146, 108), (146, 100), (136, 90)], hip=(142.0, 74.0), knee=(152.0, 104.0), belly=80, far=True, ph=1.0),
            dict(poly=[(150, 58), (176, 56), (182, 80), (182, 104), (180, 127), (166, 127), (168, 110), (166, 98), (158, 92), (152, 84)],
                 hip=(165.0, 62.0), knee=(174.0, 100.0), belly=80, far=False, ph=0.0),
        ],
    ),
    "zombie": dict(
        head=[(6, 50), (20, 14), (40, 6), (56, 10), (62, 26), (64, 46), (60, 62), (48, 72), (34, 80), (22, 76), (10, 62)],
        neck=(60.0, 44.0),
        tail=[(190, 38), (200, 44), (214, 58), (232, 64), (230, 72), (214, 84), (204, 76), (194, 64), (188, 52)],
        tail_root=(192.0, 46.0),
        legs=[
            dict(poly=[(72, 66), (96, 66), (94, 80), (84, 90), (80, 100), (74, 112), (72, 124), (48, 124), (50, 116), (62, 110),
                       (68, 96), (74, 84)], hip=(86.0, 66.0), knee=(78.0, 96.0), belly=76, far=True, ph=1.5),
            dict(poly=[(98, 58), (122, 58), (120, 80), (116, 96), (114, 110), (112, 128), (94, 128), (98, 116), (102, 104),
                       (104, 88), (100, 72)], hip=(110.0, 60.0), knee=(110.0, 100.0), belly=76, far=False, ph=0.5),
            dict(poly=[(144, 58), (166, 58), (172, 80), (180, 96), (178, 110), (172, 118), (168, 124), (150, 124), (156, 114),
                       (162, 104), (160, 92), (150, 80)], hip=(156.0, 58.0), knee=(170.0, 98.0), belly=66, far=True, ph=1.0),
            dict(poly=[(170, 46), (190, 44), (198, 70), (206, 90), (212, 110), (214, 128), (196, 128), (200, 112), (196, 98),
                       (186, 90), (178, 82), (174, 66)], hip=(178.0, 50.0), knee=(200.0, 94.0), belly=66, far=False, ph=0.0),
        ],
    ),
}


def _mask(size, poly):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return np.asarray(m) > 0


def _inpaint(img, region, iters=30):
    img = img.copy()
    todo = region & (img[..., 3] == 0)
    for _ in range(iters):
        if not todo.any():
            break
        new = img.copy()
        done = np.zeros_like(todo)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sh = np.roll(np.roll(img, dy, 0), dx, 1)
            take = todo & ~done & (sh[..., 3] > 0)
            new[take] = sh[take]
            done |= take
        img = new
        todo &= ~done
    return img


class DogRig:
    def __init__(self, base, spec):
        a = np.pad(np.asarray(base.convert("RGBA")), ((PAD, PAD), (PAD, PAD), (0, 0)))
        self.h, self.w = a.shape[:2]
        P = lambda pts: [(x + PAD, y + PAD) for x, y in pts]  # noqa: E731
        size = (self.w, self.h)
        opaque = a[..., 3] > 0
        ys = np.mgrid[0:self.h, 0:self.w][0]
        self.neck = (spec["neck"][0] + PAD, spec["neck"][1] + PAD)
        self.tail_root = (spec["tail_root"][0] + PAD, spec["tail_root"][1] + PAD)
        head_m = _mask(size, P(spec["head"])) & opaque
        tail_m = _mask(size, P(spec["tail"])) & opaque
        self.head = np.where(head_m[..., None], a, 0).astype(np.uint8)
        self.tail = np.where(tail_m[..., None], a, 0).astype(np.uint8)
        body = a.copy()
        body[head_m | tail_m] = 0
        self.legs = []
        near_all = np.zeros_like(opaque)
        for L in spec["legs"]:
            if not L["far"]:
                near_all |= _mask(size, P(L["poly"]))
        for L in spec["legs"]:
            m = _mask(size, P(L["poly"])) & opaque
            if L["far"]:
                m &= ~near_all  # örtüşen pikseller öndeki (yakın) bacağın
            knee = (L["knee"][0] + PAD, L["knee"][1] + PAD)
            upper = np.where((m & (ys < knee[1] + 2))[..., None], a, 0).astype(np.uint8)
            lower = np.where((m & (ys >= knee[1] - 2))[..., None], a, 0).astype(np.uint8)
            # bacağın karın çizgisinin altındaki serbest kısmı gövdeden silinir; üstü gövdede de kalır (boşluk olmasın)
            body[m & (ys >= L["belly"] + PAD)] = 0
            hip = (L["hip"][0] + PAD, L["hip"][1] + PAD)
            self.legs.append(dict(upper=upper, lower=lower, hip=hip, knee=knee, far=L["far"], ph=L["ph"]))
        # gövdede kalan, karnın epey altındaki sahipsiz pikseller (çokgen dışı pati/tüy kenarları) en yakın bacağa katılır;
        # yoksa bacak hareket edince yerde kopuk parça olarak kalırlar
        from scipy import ndimage
        low = (body[..., 3] > 0) & (ys >= min(L["belly"] for L in spec["legs"]) + PAD + 10)
        if low.any():
            dists = [ndimage.distance_transform_edt(~((L["upper"][..., 3] > 0) | (L["lower"][..., 3] > 0))) for L in self.legs]
            owner = np.argmin(np.stack(dists), 0)
            for i, L in enumerate(self.legs):
                mine = low & (owner == i) & (dists[i] <= 8)
                for part, sel in (("upper", ys < L["knee"][1] + 2), ("lower", ys >= L["knee"][1] - 2)):
                    m2 = mine & sel
                    L[part][m2] = body[m2]
                body[mine] = 0
        # kafa/kuyruk kesilen yerin gövde içi kenarı: komşu renklerle doldur (dönünce boşluk görünmesin)
        near_neck = np.zeros_like(opaque)
        nx, ny = int(self.neck[0]), int(self.neck[1])
        near_neck[ny - 14:ny + 14, nx - 8:nx + 4] = True
        body = _inpaint(body, near_neck & head_m)
        self.body = body
        self.foot_y = int(np.where(opaque.any(1))[0][-1]) + 1
        xs = [L["hip"][0] for L in self.legs]
        self.cx = (min(xs) + max(xs)) / 2

    def _leg(self, L, th, kn, dy):
        kv = rot((L["knee"][0] - L["hip"][0], L["knee"][1] - L["hip"][1]), th)
        nk = (L["hip"][0] + kv[0], L["hip"][1] + kv[1] + dy)
        lower = rotate_layer(L["lower"], th + kn, L["knee"], shift=(nk[0] - L["knee"][0], nk[1] - L["knee"][1]))
        upper = rotate_layer(L["upper"], th, L["hip"], shift=(0, dy))
        return over(lower, upper)

    def frame(self, legs=((0, 0),) * 4, body_dy=0.0, head=0.0, head_dx=0.0, tail=0.0, dx=0.0, leg_dy=None):
        """legs: her bacak için (uyluk açısı, diz açısı); +açı = ekranda saat yönü (sola bakan köpekte ayak öne)."""
        out = np.zeros_like(self.body)
        ldy = body_dy * 0.3 if leg_dy is None else leg_dy
        out = over(out, rotate_layer(self.tail, tail, self.tail_root, shift=(0, body_dy)))
        for L, (th, kn) in zip(self.legs, legs):
            if L["far"]:
                out = over(out, self._leg(L, th, kn, ldy))
        out = over(out, rotate_layer(self.body, 0.0, self.neck, shift=(0, body_dy)))
        for L, (th, kn) in zip(self.legs, legs):
            if not L["far"]:
                out = over(out, self._leg(L, th, kn, ldy))
        out = over(out, rotate_layer(self.head, head, self.neck, shift=(head_dx, body_dy)))
        if dx:
            out = rotate_layer(out, 0.0, (0, 0), shift=(dx, 0))
        return clean(out, 8)


def anim_frames(rig, anim, n):
    D = math.radians
    for f in range(n):
        t = f / n
        ph = 2 * math.pi * t
        g = dict(ang=0.0, dy=0.0)
        if anim == "idle":
            k = dict(body_dy=round(0.8 * (1 - math.cos(ph)) / 2), tail=D(9) * math.sin(ph), head=D(2) * math.sin(ph + 1))
        elif anim == "walk":
            legs = []
            for L in rig.legs:
                p = ph + L["ph"] * math.pi
                front = L["hip"][0] < rig.cx
                legs.append((D(20 if front else 22) * math.sin(p), -D(38 if front else 30) * max(0.0, math.cos(p))))
            k = dict(legs=legs, body_dy=round(1.2 * abs(math.sin(2 * ph))), tail=D(14) * math.sin(ph * 2),
                     head=D(3) * math.sin(2 * ph))
        elif anim == "attack":
            # çömel (kafa geri) → atıl (kafa öne-aşağı, ön bacaklar uzar) → ısır → geri çekil
            dx = [0, 2, -9, -11, -6, -2][f]
            head = [D(6), D(10), -D(16), -D(20), -D(8), 0][f]
            fr = [0, -D(6), D(26), D(28), D(12), D(3)][f]
            bk = [0, D(8), -D(22), -D(24), -D(10), -D(3)][f]
            legs = [(fr, -D(6)), (fr, -D(4)), (bk, 0), (bk, 0)]
            k = dict(legs=legs, head=head, head_dx=[0, 2, -4, -5, -2, 0][f], body_dy=[0, 2, -1, 0, 1, 0][f], dx=dx,
                     tail=D(12) * math.sin(ph * 2))
        elif anim == "hurt":
            k = dict(head=D([10, 16][f]), body_dy=[1, 2][f], dx=[3, 5][f], tail=-D(16))
        elif anim == "death":
            # bacaklar katlanır, gövde yere iner, kafa düşer
            # bacaklar öne/arkaya uzanır, gövde yere iner (yan yatmış gibi), kafa yere düşer
            s = [0.0, 0.3, 0.6, 0.85, 1.0, 1.0][f]
            drop = rig.foot_y - max(L["hip"][1] for L in rig.legs) - 6
            legs = [(D(78) * s, -D(10) * s), (D(84) * s, -D(14) * s), (-D(78) * s, D(8) * s), (-D(84) * s, D(10) * s)]
            k = dict(legs=legs, body_dy=round(drop * s), leg_dy=round(drop * s), head=-D(16) * s, tail=-D(24) * s)
        else:
            k = {}
        yield rig.frame(**k)


def main(src, spec_name, out):
    os.makedirs(out, exist_ok=True)
    for f in os.listdir(out):
        if f.endswith(".png"):
            os.remove(os.path.join(out, f))
    rig = DogRig(Image.open(src), RIGS[spec_name])
    # tuval çapa (gövde ortası) etrafında simetrik: aynalanan yönlerde çapa yerinde kalsın
    half = int(math.ceil(max(rig.cx, rig.w - rig.cx)))
    for anim, n in ANIMS.items():
        for f, img in enumerate(anim_frames(rig, anim, n)):
            sym = np.zeros((rig.h, 2 * half, 4), np.uint8)
            x0 = half - int(round(rig.cx))
            sym[:, x0:x0 + rig.w] = img
            im = Image.fromarray(sym)
            imm = im.transpose(Image.FLIP_LEFT_RIGHT)
            # yön eşlemesi kahramanla aynı: 0 ve 4 sola bakar, 1-3 aynası
            for d, I in {0: im, 1: imm, 2: imm, 3: imm, 4: im}.items():
                I.save(os.path.join(out, f"{anim}_{d}_{f}.png"))
    json.dump({"anchor": [half, rig.foot_y], "mpp": MPP, "frames": ANIMS},
              open(os.path.join(out, "meta.json"), "w"))
    print(spec_name, "kareler:", sum(ANIMS.values()) * 5)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
