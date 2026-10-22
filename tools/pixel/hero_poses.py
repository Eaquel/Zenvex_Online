"""V10 kahraman poz sayfalarından (art/v10/hero/p_*.png) animasyon kareleri.

Elle çizilmiş anahtar pozlar doğrudan kare olur (vurulma, düşme, kalkma, ölüm) ya da hafif eğilerek iki kareli döngü
verir (odun kesme, kazma). Pozlar sağa bakar; atlastaki ön görünüm sola baktığı için aynalanır.
Kahramanın elindeki odun kütüğü, kaya ve sandık gibi sahne parçaları silinir (oyunda gerçeği yanında durur).

Çapa: en alttaki satırların ortası (ayak / yere değen gövde). Ölçek iki sayfa için ayrı: dik duruş boyu mevcut
kahraman karesine (228 px, k=0.4722) eşitlenir.

python3 tools/pixel/hero_poses.py → art/v10/hero_anim/<anim>_0_<kare>.png + anim.json
"""
import json
import os

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SRC = os.path.join(ROOT, "art", "v10", "hero")
OUT = os.path.join(ROOT, "art", "v10", "hero_anim")

HERO_K = 0.47222  # atlastaki kahraman karesinin ölçeği
HERO_H = 228.0    # atlastaki dik duruş boyu (px)
# sayfa başına dik duruş boyu (p_stand_up ve p_point)
SHEET_H = {"hurt": 175.0, "act": 217.0}
SHEET = {"p_stagger": "hurt", "p_kneel_head": "hurt", "p_fall_back": "hurt", "p_lie_back": "hurt", "p_kneel_up": "hurt",
         "p_stand_up": "hurt", "p_collapse": "hurt", "p_dead": "hurt",
         "p_pickup": "act", "p_chop": "act", "p_mine": "act", "p_open": "act", "p_drink": "act", "p_cheer": "act", "p_point": "act"}

# anim: (fps, döngü, [(poz, eğim derecesi)])  — eğim çapa çevresinde (negatif: geriye)
ANIMS = {
    "hurt": (10, 0, [("p_stagger", 0), ("p_stagger", -3)]),
    "knockdown": (10, 0, [("p_stagger", -4), ("p_fall_back", 0), ("p_fall_back", 0), ("p_lie_back", 0), ("p_lie_back", 0)]),
    "getup": (10, 0, [("p_lie_back", 0), ("p_kneel_up", 0), ("p_kneel_up", 0), ("p_stand_up", 0), ("p_stand_up", 0)]),
    "death": (8, 0, [("p_stagger", -3), ("p_kneel_head", 0), ("p_kneel_head", 0), ("p_collapse", 0), ("p_dead", 0), ("p_dead", 0)]),
    "pickup": (8, 0, [("p_pickup", 0), ("p_pickup", 0)]),
    "chop": (5, 1, [("p_chop", -7), ("p_chop", 0)]),
    "mine": (5, 1, [("p_mine", -7), ("p_mine", 0)]),
    "open": (6, 0, [("p_open", 0)]),
    "drink": (6, 0, [("p_drink", 0)]),
    "cheer": (6, 0, [("p_cheer", 0)]),
    "point": (6, 0, [("p_point", 0)]),
}


def load(name):
    a = np.asarray(Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")).copy()
    al = a[..., 3] > 0
    if name == "p_chop":
        # kütük (sağ alt) ve üstündeki dal; balta ağzı kalır
        a[145:, 124:, 3] = 0
        a[133:, 170:, 3] = 0
    elif name in ("p_mine", "p_open", "p_pickup"):
        # kaya / sandık / taş ayrı parça: yalnız en büyük bileşen (kahraman + elindeki alet)
        lab, n = ndimage.label(al, structure=np.ones((3, 3)))
        sizes = ndimage.sum(al, lab, range(1, n + 1))
        a[lab != (int(np.argmax(sizes)) + 1), 3] = 0
    im = Image.fromarray(a)
    im = im.crop(im.getbbox())
    return im.transpose(Image.FLIP_LEFT_RIGHT)


def anchor(im):
    a = np.asarray(im)[..., 3] > 0
    ys, xs = np.nonzero(a)
    bottom = ys.max()
    low = xs[ys >= bottom - 4]
    return float(low.mean()), float(bottom + 1)


def tilt(im, ax, ay, deg):
    """Çapa çevresinde döndür (en yakın komşu, 4 kat büyütülmüş tuvalde: kenarlar daha düzgün)."""
    if deg == 0:
        return im, ax, ay
    pad = int(max(im.size) * 0.3)
    big = Image.new("RGBA", (im.width + 2 * pad, im.height + 2 * pad), (0, 0, 0, 0))
    big.paste(im, (pad, pad))
    cx, cy = ax + pad, ay + pad
    k = 4
    up = big.resize((big.width * k, big.height * k), Image.NEAREST)
    # aynalanmış görüntüde kahraman sola bakar: geriye eğilmek (baş sağa) saat yönü = PIL'de negatif açı
    up = up.rotate(deg, resample=Image.NEAREST, center=(cx * k, cy * k))
    out = up.resize(big.size, Image.NEAREST)
    bb = out.getbbox()
    return out.crop(bb), cx - bb[0], cy - bb[1]


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))
    poses = {n: load(n) for n in SHEET}
    meta = {"anims": {}, "frames": {}}
    for anim, (fps, loop, frames) in ANIMS.items():
        meta["anims"][anim] = [len(frames), fps, loop]
        for i, (pose, deg) in enumerate(frames):
            im = poses[pose]
            ax, ay = anchor(im)
            im, ax, ay = tilt(im, ax, ay, deg)
            key = f"{anim}_0_{i}"
            im.save(os.path.join(OUT, key + ".png"))
            k = HERO_K * HERO_H / SHEET_H[SHEET[pose]]
            meta["frames"][key] = [round(ax, 1), round(ay, 1), round(k, 5)]
    json.dump(meta, open(os.path.join(OUT, "anim.json"), "w"), indent=1)
    print(len(meta["frames"]), "kare")


if __name__ == "__main__":
    main()
