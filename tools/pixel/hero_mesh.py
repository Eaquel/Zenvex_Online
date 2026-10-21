"""Kahraman (V6 kapüşonlu savaşçı, art/v6/hero_s.png) için iskeletli mesh animasyonları.

Parçalar: gövde (kapüşon + üst gövde), iki bacak (pantolon + bot), iki kol ve iki el (el ayrı parça: silah sapı
avuçla parmaklar arasında kalsın diye silah kol ile el arasında çizilir). Bıçak ve katana ayrı sprite; tutma
noktası el kemiğine bağlı, yönü bileğin 3B dönüşünden gelir (öne uzanan kılıç kısalır). Silahlı kareler
<anim>_0_<kare>@knife / @katana olarak ayrıca pişirilir (oyun bunları otomatik seçer).

python3 tools/pixel/hero_mesh.py art/v6/hero_s.png /home/user/zx_pix/hero [önizleme_dizini]
Çıktı: <anim>_0_<kare>[@silah].png, attach.json, meta.json (çapa, mpp, kareler)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mesh_rig import Part, Skeleton, bone_xform, dist_weights, ease, finish, rot2, splat, sstep  # noqa: E402

CW, CH = 600, 500
ANCHOR = (300, 470)
GROUND = 260.0  # kaynakta taban çizgisi
MPP = 1.70 / (GROUND - 35.0)
WEAPONS = os.environ.get("ZX_WEAPONS", "/home/user/zx_pix/weapons")

# --- eklemler (kaynak koordinatları; ebeveyn önce) ---
J = {
    "pelvis": (None, (86, 148)),
    "pelvis_lo": ("pelvis", (86, 176)),
    "waist": ("pelvis", (86, 146)),
    "chest": ("waist", (86, 106)),
    "neck": ("chest", (86, 78)),
    "head": ("neck", (85, 38)),
    "shL": ("chest", (54, 95)), "elL": ("shL", (48, 127)), "wrL": ("elL", (49, 151)), "hdL": ("wrL", (50, 171)),
    "shR": ("chest", (107, 97)), "elR": ("shR", (113, 128)), "wrR": ("elR", (116, 152)), "hdR": ("wrR", (118, 171)),
    "hipL": ("pelvis", (72, 152)), "knL": ("hipL", (67, 212)), "anL": ("knL", (65, 249)),
    "toL": ("anL", (59, 259)), "heL": ("anL", (68, 260)),
    "hipR": ("pelvis", (100, 152)), "knR": ("hipR", (101, 212)), "anR": ("knR", (101, 249)),
    "toR": ("anR", (110, 258)), "heR": ("anR", (96, 260)),
}
BONES = {
    "pelvis": ("pelvis", "pelvis_lo"), "spine": ("waist", "chest"), "chest": ("chest", "neck"), "head": ("neck", "head"),
    "uaL": ("shL", "elL"), "faL": ("elL", "wrL"), "hL": ("wrL", "hdL"),
    "uaR": ("shR", "elR"), "faR": ("elR", "wrR"), "hR": ("wrR", "hdR"),
    "thL": ("hipL", "knL"), "snL": ("knL", "anL"), "ftL": ("anL", "toL"),
    "thR": ("hipR", "knR"), "snR": ("knR", "anR"), "ftR": ("anR", "toR"),
}
ARM_L = [(50, 85), (60, 87), (64, 100), (64, 118), (62, 130), (60, 140), (57, 147), (56, 160), (57, 176), (40, 176), (37, 150),
         (37, 120), (40, 102), (45, 90)]
ARM_R = [(100, 88), (110, 90), (117, 102), (119, 120), (123, 148), (126, 166), (124, 176), (106, 176), (106, 156), (104, 134),
         (103, 120), (99, 104)]
WRIST_Y = 150.0
YAW, KZ = 15.0, 0.32  # bakış: kameraya göre 15° sola; derinlik → ekran aşağı katsayısı


def poly_mask(shape, poly):
    m = Image.new("L", (shape[1], shape[0]), 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return np.asarray(m) > 0


def inpaint(img, region, iters=40):
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


class Hero:
    def __init__(self, src):
        a = np.asarray(Image.open(src).convert("RGBA"))
        self.sk = Skeleton(J, YAW, KZ)
        P = lambda n: J[n][1]  # noqa: E731
        opaque = a[..., 3] > 0
        ys, xs = np.mgrid[0:a.shape[0], 0:a.shape[1]].astype(float)
        armL = poly_mask(a.shape, ARM_L) & opaque
        armR = poly_mask(a.shape, ARM_R) & opaque
        arms = armL | armR
        legs = opaque & ~arms & (ys >= 150)
        legL = legs & (xs < 88)
        legR = legs & (xs >= 88)
        torso_m = opaque & ~arms & ~legs
        # kolların arkasında kalan gövde: omuz/yan kısımlar komşu renklerle dolar (kol savrulunca delik kalmasın)
        tex = a.copy()
        tex[arms] = 0
        inside = np.zeros_like(opaque)
        for y in range(a.shape[0]):
            row = torso_m[y]
            if row.any():
                xs_on = np.nonzero(row)[0]
                inside[y, xs_on.min():xs_on.max() + 1] = True
        fill = arms & inside & (ys < 150) & (ys > 84)
        tex = inpaint(tex, fill)
        torso_m = (torso_m | fill) & (tex[..., 3] > 0)

        def w_torso(px, py):
            head = 1 - sstep(70, 80, py)
            pel = sstep(128, 146, py)
            up = (1 - head) * (1 - pel)
            sp = sstep(96, 124, py)
            return ["head", "chest", "spine", "pelvis"], [head, up * (1 - sp), up * sp, pel]

        def w_leg(side):
            th, sn, ft = "th" + side, "sn" + side, "ft" + side

            def f(px, py):
                segs = [(P("hip" + side), P("kn" + side)), (P("kn" + side), P("an" + side)), (P("an" + side), P("to" + side))]
                Dw = dist_weights(px, py, segs, 5.0)
                # kalça üstü: leğen; aşağı indikçe uyluk (kese/kafatası leğenle sallanır)
                pel = 1 - sstep(158, 182, py)
                return ["pelvis", th, sn, ft], [pel, (1 - pel) * Dw[0], (1 - pel) * Dw[1], (1 - pel) * Dw[2]]
            return f

        def w_arm(side):
            ua, fa, h = "ua" + side, "fa" + side, "h" + side

            def f(px, py):
                segs = [(P("sh" + side), P("el" + side)), (P("el" + side), P("wr" + side)), (P("wr" + side), P("hd" + side))]
                Dw = dist_weights(px, py, segs, 4.5)
                ch = 1 - sstep(88, 100, py)
                return ["chest", ua, fa, h], [ch, (1 - ch) * Dw[0], (1 - ch) * Dw[1], (1 - ch) * Dw[2]]
            return f

        self.parts = {
            "torso": Part("torso", tex, torso_m, w_torso),
            "legL": Part("legL", a, legL, w_leg("L")),
            "legR": Part("legR", a, legR, w_leg("R")),
            "armL": Part("armL", a, armL & (ys < WRIST_Y + 2), w_arm("L")),
            "armR": Part("armR", a, armR & (ys < WRIST_Y + 2), w_arm("R")),
            "handL": Part("handL", a, armL & (ys >= WRIST_Y), w_arm("L")),
            "handR": Part("handR", a, armR & (ys >= WRIST_Y), w_arm("R")),
        }
        self.weapons = {}
        meta = os.path.join(WEAPONS, "weapons.json")
        if os.path.exists(meta):
            wm = json.load(open(meta))
            for name, m in wm.items():
                img = np.asarray(Image.open(os.path.join(WEAPONS, name + ".png")).convert("RGBA"))
                g, t = m["grip"], m["tip"]
                part = Part(name, img, np.ones(img.shape[:2], bool), lambda px, py: (["w"], [np.ones_like(px)]))
                self.weapons[name] = (part, np.array(g, float), np.array(t, float))

    # ------------------------------------------------------------------ poz → kare
    def render(self, pose, weapon=None):
        sk = self.sk
        R = {}
        for j, v in pose.get("rot", {}).items():
            v = tuple(v) + (0,) * (3 - len(v))
            if j.endswith("R"):  # iki yan aynalı: +abd = dışa, +twist = dışa (kol yatayda dışa açılır)
                v = (v[0], -v[1], v[2])
            elif j.endswith("L"):
                v = (v[0], v[1], -v[2])
            R[j] = sk.rot(*v)
        # kök: leğen dönüşü (gövde eğilmesi/burulması) + öteleme
        R["pelvis"] = sk.rot(*pose.get("root", (0, 0, 0)))
        pos, G = sk.fk(R, (0, 0, 0))
        # yer teması: en alçak taban noktası zemine (pozda kapalı değilse)
        lift = pose.get("lift", 0.0)
        if pose.get("ground", True):
            low = max(pos[n][1] for n in ("toL", "heL", "toR", "heR"))
            dy = GROUND - low - lift
        else:
            dy = pose.get("dy", 0.0)
        dx = pose.get("dx", 0.0)
        shift = np.array([dx, dy, 0.0])
        pos = {n: p + shift for n, p in pos.items()}
        p2 = {n: sk.proj(p) for n, p in pos.items()}
        ang = math.radians(pose.get("screen", 0.0))
        piv = np.array(pose.get("pivot", (86, 200)), float)
        if ang:
            p2 = {n: rot2(p, ang, piv) for n, p in p2.items()}
        T = {}
        for b, (ja, jb) in BONES.items():
            T[b] = bone_xform(J[ja][1], J[jb][1], p2[ja], p2[jb])
        # silah: el kemiğinde tutma noktası; yön = bileğin küresel dönüşü × elde dinlenme yönü
        wl = None
        if weapon and weapon in self.weapons:
            part, g, t = self.weapons[weapon]
            gp3 = pos["wrL"] + G["wrL"] @ (sk.rest["hdL"] - sk.rest["wrL"]) * 0.55
            d_rest = pose.get("wdir", None)
            if d_rest is None:
                d_rest = sk.F * math.cos(math.radians(30)) + sk.Y * math.sin(math.radians(30))
            d3 = np.asarray(pose["wworld"], float) if "wworld" in pose else G["wrL"] @ np.asarray(d_rest, float)
            d3 /= np.linalg.norm(d3)
            Lw = np.linalg.norm(t - g)
            gp2 = sk.proj(gp3)
            tp2 = sk.proj(gp3 + d3 * Lw)
            if ang:
                gp2, tp2 = rot2(gp2, ang, piv), rot2(tp2, ang, piv)
            T["w"] = bone_xform(g, t, gp2, tp2)
            wl = (part, (gp3 + d3 * Lw * 0.5)[2])
        # derinlik: kollar (el z), bacaklar (ayak bileği z)
        zt = pos["chest"][2]
        layers = []
        for s in ("L", "R"):
            layers.append((pos["an" + s][2] - 30, "leg" + s))
        layers.append((zt, "torso"))
        for s in ("L", "R"):
            hz = (pos["wr" + s][2] + pos["el" + s][2]) / 2
            z = hz + (8 if hz > -12 else -40)
            layers.append((z, "arm" + s))
            layers.append((z + 0.1, "hand" + s))
        if wl is not None:
            layers.append((pose.get("wz", wl[1] + 6), "weapon"))
            # silah eldeki kolun önünde/arkasında: el her zaman silahın üstünde (parmaklar sapı kavrar)
            zl = [z for z, n in layers if n == "handL"][0]
            layers = [(max(z, layers[-1][0] + 0.2) if n == "handL" else z, n) for z, n in layers]
            if zl < zt - 20:
                pass
        layers.sort(key=lambda v: v[0])
        canvas = np.zeros((CH, CW, 4), np.uint8)
        ox, oy = ANCHOR[0] - J["pelvis"][1][0], ANCHOR[1] - GROUND
        ox, oy = round(ox), round(oy)
        for _, n in layers:
            if n == "weapon":
                part = wl[0]
                X, Y = part.deform(T)
            else:
                part = self.parts[n]
                X, Y = part.deform(T)
            splat(canvas, X, Y, part.col, ox, oy)
        img = finish(canvas, 9)
        pts = {"head": p2["head"] * 0.3 + p2["neck"] * 0.7, "neck": p2["neck"], "chest": p2["chest"], "back": p2["chest"],
               "rhand": (p2["wrL"] + p2["hdL"]) / 2, "lhand": (p2["wrR"] + p2["hdR"]) / 2,
               "lfoot": p2["anR"], "rfoot": p2["anL"], "seat": p2["pelvis"]}
        att = {k: [round(float(v[0] + ox), 1), round(float(v[1] + oy), 1)] for k, v in pts.items()}
        if pose.get("ground_px"):
            rows = np.where(img[..., 3].any(1))[0]
            if len(rows):
                sh = ANCHOR[1] - 1 - rows[-1]
                img = np.roll(img, sh, 0)
                for v in att.values():
                    v[1] = round(v[1] + sh, 1)
        return img, att


# ---------------------------------------------------------------------- animasyonlar
FRAMES = {"idle": 6, "walk": 8, "run": 8, "punch": 8, "melee": 6, "roll": 6, "hurt": 2, "knockdown": 5, "getup": 5,
          "death": 6, "ride": 4}


def arm_ready(weapon):
    """Silahlı bekleme/yürüme kol pozu (sağ el = izleyicinin solu)."""
    if weapon == "katana":
        return dict(shL=(28, 10, 0), elL=(40, 0, 0), wrL=(0, 0, 0)), (0, 0.55, 0.85)
    if weapon == "knife":
        return dict(shL=(18, 6, 0), elL=(55, 0, 0)), None
    return None, None


def pose_for(anim, f, n, weapon):
    t = f / n
    P = {"rot": {}}
    rot = P["rot"]
    if anim == "idle":
        b = math.sin(2 * math.pi * t)
        rot["waist"] = (-1.5 * b, 0, 0)
        rot["neck"] = (1.0 * b, 0, 0)
        rot["shL"] = (2 * b, 1 + 1.5 * b, 0)
        rot["shR"] = (-2 * b, 1 + 1.5 * b, 0)
        rot["elL"] = (6 + 3 * b, 0, 0)
        rot["elR"] = (6 + 3 * b, 0, 0)
        rot["hipL"] = (0, 2, 0)
        rot["hipR"] = (0, 2, 0)
        P["root"] = (0, 0, 2 * math.sin(2 * math.pi * t + 0.5))
        P["lift"] = 0.0
        P["breath"] = b
    elif anim in ("walk", "run"):
        if anim == "walk":
            hip = [20, 15, 3, -8, -13, -6, 12, 21]
            knee = [4, 14, 6, 4, 12, 36, 46, 16]
            ank = [12, 0, -2, -8, -20, -10, 6, 12]
            arm_amp, el0, lean, twist, roll = 18, 14, 3, 6, 2
            flight = [0] * 8
        else:
            hip = [32, 16, 0, -20, -34, -12, 30, 46]
            knee = [16, 38, 22, 10, 30, 92, 108, 52]
            ank = [10, -6, -12, -28, -30, 0, 16, 16]
            arm_amp, el0, lean, twist, roll = 34, 78, 12, 10, 3
            flight = [0, 0, 0, 4, 0, 0, 0, 4]
        hl = ease(hip, t)
        hr = ease(hip, t + 0.5)
        rot["hipL"] = (hl, 2, 0)
        rot["hipR"] = (hr, 2, 0)
        kl, kr = ease(knee, t), ease(knee, t + 0.5)
        rot["knL"] = (-kl, 0, 0)
        rot["knR"] = (-kr, 0, 0)
        # ayak açısı dünyaya göre (yerdeki bot düz kalır): bilek = istenen − (kalça − diz)
        rot["anL"] = (ease(ank, t) - (hl - kl), 0, 0)
        rot["anR"] = (ease(ank, t + 0.5) - (hr - kr), 0, 0)
        c = math.cos(2 * math.pi * t)
        # leğen öne atılan bacak tarafına döner; göğüs ters döner (omuz-kalça karşı dönüşü)
        P["root"] = (-lean * 0.4, roll * math.sin(2 * math.pi * (t + 0.25)) * 0, twist * c)
        rot["waist"] = (-lean, roll * math.sin(2 * math.pi * t) * 0.5, -twist * 1.8 * c)
        rot["neck"] = (lean * 0.6 + 1.5 * math.sin(4 * math.pi * t), 0, twist * 0.8 * c)
        lag = 0.05
        # kollar karşı bacakla: sağ kol (izleyicinin solu) ↔ sol bacak (izleyicinin sağı)
        al = arm_amp * ease([1, 0.75, 0.2, -0.5, -1, -0.75, -0.2, 0.5], t + 0.5 - lag)
        ar = arm_amp * ease([1, 0.75, 0.2, -0.5, -1, -0.75, -0.2, 0.5], t - lag)
        rot["shL"] = (al, 4, 0)
        rot["shR"] = (ar, 4, 0)
        rot["elL"] = (el0 + max(0, al) * 0.6, 0, 0)
        rot["elR"] = (el0 + max(0, ar) * 0.6, 0, 0)
        P["lift"] = ease(flight, t)
    elif anim == "punch":
        # gard → sol direkt (izleyicinin sağındaki kol) → gard → sağ kroşe/direkt (gövde dönüşüyle)
        # yumruklar ekranda sola (bakış yönü) uzanır: öndeki kol (izleyicinin solu) dışa, arkadaki kol göğsün önünden
        gL, gR, ge = (52, 0, -26), (52, 0, -18), (104, 0, 0)
        keysL = [gL, (88, 0, 56), (90, 0, 58), (66, 0, 10), gL, (46, 0, -30), (46, 0, -30), gL]
        elL = [ge, (8, 0, 0), (4, 0, 0), (60, 0, 0), ge, (110, 0, 0), (110, 0, 0), ge]
        keysR = [gR, (46, 0, -24), (46, 0, -24), gR, (60, 0, -40), (88, 0, -78), (90, 0, -82), (64, 0, -40)]
        elR = [ge, (110, 0, 0), (110, 0, 0), ge, (80, 0, 0), (8, 0, 0), (4, 0, 0), (60, 0, 0)]
        tw = [0, 8, 10, 4, -4, -18, -22, -8][f]
        rot["shR"], rot["elR"] = keysR[f], elR[f]
        rot["shL"], rot["elL"] = keysL[f], elL[f]
        rot["waist"] = (-6 - (4 if f in (1, 2, 5, 6) else 0), 0, tw)
        rot["neck"] = (4, 0, -tw * 0.5)
        P["root"] = (-2, 0, tw * 0.4)
        rot["hipL"] = (14 + (6 if f in (5, 6) else 0), 6, 0)
        rot["knL"] = (-18, 0, 0)
        rot["hipR"] = (-12, 6, 0)
        rot["knR"] = (-14, 0, 0)
        rot["anR"] = (-14 if f in (5, 6) else -4, 0, 0)
        P["dx"] = [0, -2, -3, -1, 0, -3, -4, -1][f]
    elif anim == "melee":
        rot["hipL"] = (16, 6, 0)
        rot["knL"] = (-20, 0, 0)
        rot["hipR"] = (-14, 6, 0)
        rot["knR"] = (-16, 0, 0)
        if weapon == "katana" or weapon is None:
            # geniş yatay yay: kılıç gövdenin önünden sağa kurulur → sola doğru savrulur (vuruş) → takip → toparlan
            sh = [(62, 0, -62), (76, 0, -26), (86, 0, 40), (66, 0, 74), (36, 10, 40), (28, 10, 0)]
            el = [(72, 0, 0), (36, 0, 0), (4, 0, 0), (8, 0, 0), (30, 0, 0), (40, 0, 0)]
            wr = [(0, 0, 0)] * 6
            tw = [-22, -12, 14, 26, 16, 4]
            lean = [2, -2, -10, -14, -8, -2]
            rot["shL"], rot["elL"], rot["wrL"] = sh[f], el[f], wr[f]
            rot["shR"], rot["elR"] = [(30, 20, 0), (40, 30, 0), (10, 26, 0), (-20, 30, 0), (-10, 20, 0), (10, 8, 0)][f], (30, 0, 0)
            P["dx"] = [2, 3, -3, -8, -6, -2][f]
        else:
            # bıçak: kısa geri çekiş → ileri saplama → kesik → toparlan
            sh = [(30, 6, 0), (44, 0, -34), (88, 0, 50), (84, 0, 66), (50, 0, 30), (24, 4, 0)]
            el = [(70, 0, 0), (122, 0, 0), (6, 0, 0), (12, 0, 0), (50, 0, 0), (62, 0, 0)]
            wr = [(0, 0, 0), (10, 0, 0), (-20, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 0)]
            tw = [-4, -12, 14, 20, 10, 0]
            lean = [-2, 0, -10, -12, -6, -2]
            rot["shL"], rot["elL"], rot["wrL"] = sh[f], el[f], wr[f]
            rot["shR"], rot["elR"] = (36, 10, 0), (100, 0, 0)
            P["dx"] = [0, 2, -5, -6, -3, -1][f]
        rot["waist"] = (lean[f], 0, tw[f])
        rot["neck"] = (2, 0, -tw[f] * 0.4)
        P["root"] = (0, 0, tw[f] * 0.35)
    elif anim == "roll":
        tuck = dict(waist=(-46, 0, 0), neck=(-24, 0, 0), hipL=(104, 4, 0), hipR=(100, 4, 0), knL=(-128, 0, 0), knR=(-124, 0, 0),
                    anL=(-20, 0, 0), anR=(-20, 0, 0), shL=(60, 4, 0), shR=(60, 4, 0), elL=(96, 0, 0), elR=(96, 0, 0))
        crouch = dict(waist=(-34, 0, 0), neck=(-12, 0, 0), hipL=(70, 6, 0), hipR=(64, 6, 0), knL=(-96, 0, 0), knR=(-92, 0, 0),
                      anL=(14, 0, 0), anR=(14, 0, 0), shL=(40, 8, 0), shR=(40, 8, 0), elL=(50, 0, 0), elR=(50, 0, 0))
        rise = dict(waist=(-10, 0, 0), hipL=(22, 4, 0), hipR=(14, 4, 0), knL=(-30, 0, 0), knR=(-26, 0, 0), shL=(16, 6, 0),
                    shR=(16, 6, 0), elL=(30, 0, 0), elR=(30, 0, 0))
        rot.update([crouch, tuck, tuck, tuck, crouch, rise][f])
        P["screen"] = [0, -95, -185, -275, -350, -360][f]
        P["pivot"] = (86, 196)
        P["ground"] = f in (0, 4, 5)
        P["ground_px"] = f in (1, 2, 3)
        P["dy"] = 0
    elif anim == "hurt":
        rot["waist"] = ([10, 16][f], 0, [6, 10][f])
        rot["neck"] = ([12, 18][f], 0, 0)
        rot["shL"] = (-14, [20, 30][f], 0)
        rot["shR"] = (-10, [22, 32][f], 0)
        rot["elL"] = (30, 0, 0)
        rot["elR"] = (30, 0, 0)
        rot["hipL"] = (-4, 4, 0)
        rot["hipR"] = (8, 4, 0)
        rot["knR"] = (-14, 0, 0)
        P["dx"] = [3, 5][f]
    elif anim in ("knockdown", "death", "getup"):
        # sırtüstü (knockdown) / yüzüstü (death) düşüş; kalkış knockdown'un tersi + el ile itiş
        if anim == "death":
            seq = [
                dict(rot=dict(waist=(12, 0, 8), neck=(16, 0, 0), shL=(-10, 24, 0), shR=(-8, 24, 0), elL=(30, 0, 0), elR=(30, 0, 0)), screen=0, dx=3),
                dict(rot=dict(waist=(-10, 0, 0), neck=(-6, 0, 0), hipL=(60, 6, 0), hipR=(56, 6, 0), knL=(-100, 0, 0), knR=(-96, 0, 0),
                              shL=(10, 20, 0), shR=(10, 20, 0), elL=(20, 0, 0), elR=(20, 0, 0)), screen=0),
                dict(rot=dict(waist=(-24, 0, 0), neck=(-14, 0, 0), hipL=(80, 6, 0), hipR=(76, 6, 0), knL=(-120, 0, 0), knR=(-116, 0, 0),
                              shL=(40, 30, 0), shR=(40, 30, 0), elL=(20, 0, 0), elR=(20, 0, 0)), screen=-28),
                dict(rot=dict(waist=(-14, 0, 0), neck=(-8, 0, 0), hipL=(30, 6, 0), hipR=(26, 6, 0), knL=(-40, 0, 0), knR=(-36, 0, 0),
                              shL=(120, 30, 0), shR=(110, 30, 0), elL=(20, 0, 0), elR=(20, 0, 0)), screen=-68),
                dict(rot=dict(waist=(-4, 0, 0), neck=(4, 0, 0), hipL=(6, 6, 0), hipR=(4, 6, 0), knL=(-10, 0, 0), knR=(-8, 0, 0),
                              shL=(150, 30, 0), shR=(140, 30, 0), elL=(20, 0, 0), elR=(20, 0, 0)), screen=-92),
                dict(rot=dict(waist=(0, 0, 0), neck=(8, 0, 0), hipL=(4, 6, 0), hipR=(2, 6, 0), knL=(-8, 0, 0), knR=(-6, 0, 0),
                              shL=(160, 34, 0), shR=(150, 34, 0), elL=(14, 0, 0), elR=(14, 0, 0)), screen=-90),
            ]
        else:
            seq = [
                dict(rot=dict(waist=(14, 0, 6), neck=(20, 0, 0), shL=(-20, 30, 0), shR=(-16, 30, 0), elL=(30, 0, 0), elR=(30, 0, 0),
                              hipL=(-4, 4, 0), hipR=(10, 4, 0)), screen=6, dx=4),
                dict(rot=dict(waist=(20, 0, 0), neck=(24, 0, 0), shL=(-30, 50, 0), shR=(-30, 50, 0), elL=(40, 0, 0), elR=(40, 0, 0),
                              hipL=(30, 8, 0), hipR=(40, 8, 0), knL=(-40, 0, 0), knR=(-30, 0, 0)), screen=30, dx=6),
                dict(rot=dict(waist=(16, 0, 0), neck=(20, 0, 0), shL=(-40, 60, 0), shR=(-40, 60, 0), elL=(30, 0, 0), elR=(30, 0, 0),
                              hipL=(44, 8, 0), hipR=(56, 8, 0), knL=(-30, 0, 0), knR=(-20, 0, 0)), screen=62, dx=6),
                dict(rot=dict(waist=(8, 0, 0), neck=(10, 0, 0), shL=(-60, 60, 0), shR=(-60, 60, 0), elL=(20, 0, 0), elR=(20, 0, 0),
                              hipL=(8, 8, 0), hipR=(12, 8, 0), knL=(-10, 0, 0), knR=(-8, 0, 0)), screen=86, dx=6),
                dict(rot=dict(waist=(4, 0, 0), neck=(6, 0, 0), shL=(-70, 50, 0), shR=(-70, 50, 0), elL=(14, 0, 0), elR=(14, 0, 0),
                              hipL=(2, 8, 0), hipR=(4, 8, 0), knL=(-6, 0, 0), knR=(-4, 0, 0)), screen=90, dx=6),
            ]
            if anim == "getup":
                seq = [
                    seq[4],
                    dict(rot=dict(waist=(-20, 0, 0), neck=(-10, 0, 0), shL=(-40, 40, 0), shR=(-30, 50, 0), elL=(10, 0, 0), elR=(10, 0, 0),
                                  hipL=(70, 8, 0), hipR=(80, 8, 0), knL=(-110, 0, 0), knR=(-100, 0, 0)), screen=62, dx=4),
                    dict(rot=dict(waist=(-40, 0, 0), neck=(-16, 0, 0), shL=(-10, 30, 0), shR=(10, 30, 0), elL=(10, 0, 0), elR=(10, 0, 0),
                                  hipL=(96, 6, 0), hipR=(90, 6, 0), knL=(-130, 0, 0), knR=(-124, 0, 0)), screen=24, dx=2),
                    dict(rot=dict(waist=(-26, 0, 0), neck=(-8, 0, 0), shL=(20, 14, 0), shR=(20, 14, 0), elL=(40, 0, 0), elR=(40, 0, 0),
                                  hipL=(60, 6, 0), hipR=(50, 6, 0), knL=(-80, 0, 0), knR=(-70, 0, 0)), screen=0),
                    dict(rot=dict(waist=(-6, 0, 0), shL=(4, 4, 0), shR=(4, 4, 0), elL=(12, 0, 0), elR=(12, 0, 0), hipL=(8, 3, 0),
                                  hipR=(6, 3, 0), knL=(-10, 0, 0), knR=(-8, 0, 0)), screen=0),
                ]
        k = seq[f]
        rot.update(k["rot"])
        P["screen"] = k.get("screen", 0)
        P["dx"] = k.get("dx", 0)
        P["pivot"] = (86, 230)
        P["ground"] = P["screen"] == 0
        P["ground_px"] = P["screen"] != 0
    elif anim == "ride":
        # motosiklet: oturur, dizler iki yana açık, eller gidonda; motor titreşimi
        b = [0, 1, 0, -1][f]
        rot["hipL"] = (78, 18, 0)
        rot["hipR"] = (78, 18, 0)
        rot["knL"] = (-82, 0, 0)
        rot["knR"] = (-82, 0, 0)
        rot["anL"] = (10, 0, 0)
        rot["anR"] = (10, 0, 0)
        rot["waist"] = (-14, 0, 0)
        rot["neck"] = (8 + b, 0, 0)
        rot["shL"] = (58, -6, 0)
        rot["shR"] = (58, -6, 0)
        rot["elL"] = (34, 0, 0)
        rot["elR"] = (34, 0, 0)
        P["ground"] = False
        P["dy"] = 0.5 * b
    # silahlı bekleme kolu (yürüme/koşu/bekleme/hasar)
    if weapon and anim in ("idle", "walk", "run", "hurt"):
        arm, wd = arm_ready(weapon)
        if arm:
            base = rot.get("shL", (0, 0, 0))
            for j, v in arm.items():
                if j == "shL":
                    rot[j] = (v[0] + base[0] * 0.35, v[1], v[2])
                else:
                    rot[j] = v
    # silah yönü (elde dinlenme yönü): katana bileğin önünde yukarı-ileri, bıçak ileri
    if weapon:
        P["wworld"] = weapon_dir(weapon, anim, f, n, rot.get("shL", (0, 0, 0))[0])
    return P


def _dir(fwd, down, side=0.0):
    a = math.radians(YAW)
    F = np.array([-math.sin(a), 0, math.cos(a)])
    S = np.array([math.cos(a), 0, math.sin(a)])
    v = F * fwd + np.array([0, 1.0, 0]) * down + S * side
    return v / np.linalg.norm(v)


def weapon_dir(weapon, anim, f, n, arm_flex):
    """Silahın dünya yönü (sap → uç). Ekran: −S sol, S sağ, −Y yukarı, F kameraya/sola."""
    a = math.radians(YAW)
    F = np.array([-math.sin(a), 0, math.cos(a)])
    S = np.array([math.cos(a), 0, math.sin(a)])
    Y = np.array([0, 1.0, 0])

    def v(x):
        x = np.asarray(x, float)
        return x / np.linalg.norm(x)
    if anim == "melee":
        if weapon == "katana":
            return v([S * 0.9 - Y * 0.5, -Y + S * 0.35, -S + F * 0.35 - Y * 0.08, -S * 0.75 + Y * 0.45 - F * 0.25,
                      -S * 0.35 + Y * 0.9, F * 0.6 + Y * 0.7 - S * 0.2][f])
        return v([F * 0.8 + Y * 0.3, -Y + S * 0.4, -S + F * 0.3, -S * 0.7 + Y * 0.6,
                  F * 0.6 + Y * 0.6 - S * 0.3, F * 0.8 + Y * 0.4][f])
    if anim in ("roll",):
        return v(-S * 0.2 + Y)  # takla: bıçak gövdeye yakın, aşağı
    if anim in ("knockdown", "death", "getup"):
        return v(-S + Y * 0.2)
    # alçak hazır duruş: uç öne-aşağı, kol salınımıyla hafif sallanır
    sw = math.radians(arm_flex * 0.5)
    base = (F * 0.75 + Y * 0.65 - S * 0.25) if weapon == "katana" else (F * 0.9 + Y * 0.3 - S * 0.3)
    return v(base * math.cos(sw) + (-Y) * math.sin(sw) * 0.6)


def main(src, out, prev=None):
    os.makedirs(out, exist_ok=True)
    for fn in os.listdir(out) if not os.environ.get("ZX_ONLY") else []:
        if fn.endswith(".png"):
            os.remove(os.path.join(out, fn))
    hero = Hero(src)
    att = {}
    variants = [None] + sorted(hero.weapons)
    sheets = {}
    only = [a for a in os.environ.get("ZX_ONLY", "").split(",") if a]
    for anim, n in FRAMES.items():
        if only and anim not in only:
            continue
        for w in variants:
            if w is not None and anim in ("punch", "ride"):
                continue  # yumruk silahsız; sürüşte silah kında
            for f in range(n):
                pose = pose_for(anim, f, n, w)
                img, pts = hero.render(pose, w)
                name = f"{anim}_0_{f}" + (f"@{w}" if w else "")
                Image.fromarray(img).save(os.path.join(out, name + ".png"))
                if w is None:
                    att[f"{anim}_0_{f}"] = pts
                sheets.setdefault((anim, w), []).append(img)
    json.dump(att, open(os.path.join(out, "attach.json"), "w"))
    json.dump({"anchor": list(ANCHOR), "mpp": MPP, "frames": FRAMES}, open(os.path.join(out, "meta.json"), "w"))
    if prev:
        os.makedirs(prev, exist_ok=True)
        for (anim, w), imgs in sheets.items():
            contact(imgs, os.path.join(prev, f"{anim}{'_' + w if w else ''}.png"))
    print("kareler:", sum(len(v) for v in sheets.values()))


def contact(imgs, path, scale=2):
    boxes = [np.nonzero(i[..., 3].any(0))[0] for i in imgs]
    rows = [np.nonzero(i[..., 3].any(1))[0] for i in imgs]
    x0 = min(b.min() for b in boxes if len(b)) - 4
    x1 = max(b.max() for b in boxes if len(b)) + 5
    y0 = min(r.min() for r in rows if len(r)) - 4
    y1 = ANCHOR[1] + 6
    w, h = x1 - x0, y1 - y0
    sheet = Image.new("RGBA", (w * len(imgs), h), (96, 120, 96, 255))
    d = ImageDraw.Draw(sheet)
    for i, im in enumerate(imgs):
        sheet.alpha_composite(Image.fromarray(im[y0:y1, x0:x1]), (i * w, 0))
        d.line([(i * w, ANCHOR[1] - y0), (i * w + w, ANCHOR[1] - y0)], fill=(60, 80, 60, 255))
        d.line([(ANCHOR[0] - x0 + i * w, h - 4), (ANCHOR[0] - x0 + i * w, h)], fill=(255, 255, 0, 255))
    sheet.resize((sheet.width * scale, sheet.height * scale), Image.NEAREST).save(path)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
