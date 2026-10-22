"""V11 aktör animasyonları: poz sayfalarından (art/v11u/*.jpg) her aktör için ön / yan / sırt kareleri.

Akış (aktör başına):
  1) Poz sayfası ayıklanır (v10_objects ile aynı yöntem) → art/v11/<aktör>/poses/<poz>.png
     Sayfadaki pozlar soldan sağa, satır satır adlandırılır; sağa bakan sayfalar aynalanır (atlasta ön ve yan görünüm sola bakar).
  2) Animasyon tanımı: her kare bir poz + görünüm + işlemler (eğim, kaydırma, nefes). Kare sayısı poz sayısından fazla
     olabilir: tek pozdan nefes alan bekleme, iki pozdan adım döngüsü gibi ek kareler kodla üretilir.
  3) Çıktı: art/v11/<aktör>/anim/<anim>_<yön>_<kare>.png + anim.json (çapa, ölçek, fps) → tools/assetgen/v11_pack.py atlasa yamar.

Yön kodları (oyunla aynı): 0 ön (sola bakar, sağa bakan yönler aynalanır), 6 yan (sola; doğu aynası), 4 sırt.
Ölçek: aktörün dik duruş boyu (STAND_H) dünyada [height_m] metre olur.

python3 tools/pixel/actor_poses.py [aktör,...]
"""
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from hero_poses import anchor, tilt  # noqa: E402
from v10_objects import extract_sheet  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
SRC = os.path.join(ROOT, "art", "v11u")
OUT = os.path.join(ROOT, "art", "v11")
PX_PER_M = 62.5
VIEW = {"front": 0, "side": 6, "back": 4}

# aktör: {"prefix": atlas öneki ("h/kaan", "z/walker", "p/dog0"), "height_m": boy, "stand": dik duruş referans pozu,
#         "sheets": {dosya: (pozlar sola mı bakıyor, [poz adları], birleştirme yarıçapı)}, "flip": [ters bakan pozlar],
#         "anims": {ad: (fps, döngü, [(poz, görünüm, {işlemler})])}}
# İşlemler: tilt (derece, çapa çevresinde; negatif geriye), dx/dy (px), breathe (gövde üstü 1 px aşağı), lift (zıplama px)
ACTORS = {}


def breathe(im, ax, ay, k=1):
    """Bel üstü k piksel aşağı (nefes verme): üst %55 satırlar kaydırılır, ayaklar sabit kalır."""
    a = np.asarray(im).copy()
    cut = int(im.height * 0.55)
    top = a[:cut].copy()
    a[:cut] = 0
    a[k:cut + k] = np.where(top[..., 3:4] > 0, top, a[k:cut + k])
    return Image.fromarray(a), ax, ay


def pose_image(actor, name):
    p = os.path.join(OUT, actor, "poses", name + ".png")
    return Image.open(p).convert("RGBA")


def extract(actor):
    """Aktörün poz sayfalarını ayıklar → art/v11/<aktör>/poses (sağa bakan sayfalar aynalanır)."""
    for sheet, (left, names, radius) in ACTORS[actor]["sheets"].items():
        extract_sheet(f"v11_{sheet}", os.path.join(OUT, actor, "poses"), names, radius, src=os.path.join(SRC, sheet + ".jpg"),
                      erase=(), mirror=not left)
    # sayfanın genel yönüne uymayan tek tek pozlar (ör. sola bakan sayfada sağa bakan bir poz)
    for name in ACTORS[actor].get("flip", ()):
        p = os.path.join(OUT, actor, "poses", name + ".png")
        Image.open(p).transpose(Image.FLIP_LEFT_RIGHT).save(p)


def build(actor):
    cfg = ACTORS[actor]
    extract(actor)
    od = os.path.join(OUT, actor, "anim")
    os.makedirs(od, exist_ok=True)
    for f in os.listdir(od):
        os.remove(os.path.join(od, f))
    ref = pose_image(actor, cfg["stand"])
    k = cfg["height_m"] * PX_PER_M / ref.height
    meta = {"prefix": cfg["prefix"], "anims": {}, "frames": {}}
    cache = {}
    for anim, (fps, loop, frames) in cfg["anims"].items():
        counts = {}
        for pose, view, ops in frames:
            d = VIEW[view]
            i = counts.get(d, 0)
            counts[d] = i + 1
            if pose not in cache:
                cache[pose] = pose_image(actor, pose)
            im = cache[pose]
            ax, ay = anchor(im)
            if ops.get("breathe"):
                im, ax, ay = breathe(im, ax, ay, ops["breathe"])
            if ops.get("tilt"):
                im, ax, ay = tilt(im, ax, ay, ops["tilt"])
            ax -= ops.get("dx", 0)
            ay += ops.get("lift", 0) - ops.get("dy", 0)
            key = f"{anim}_{d}_{i}"
            im.save(os.path.join(od, key + ".png"))
            meta["frames"][key] = [round(ax, 1), round(ay, 1), round(k, 5)]
        n = max(counts.values())
        meta["anims"][anim] = [n, fps, loop]
    json.dump(meta, open(os.path.join(od, "anim.json"), "w"), indent=1)
    print(actor, len(meta["frames"]), "kare")


if __name__ == "__main__":
    for a in (sys.argv[1].split(",") if len(sys.argv) > 1 else ACTORS):
        build(a)
