"""V19: kullanıcının yeni görselleri (yeşil zeminli) ve eski görsellerin temizliği.

python3 tools/assetgen/v19_art.py <New klasörü> [önizleme.png]
  - 18 etkinlik eşyası (silah, yüzük, kolye) piksel stilinde i/ev_<etkinlik>_<yuva> olarak değişir
  - gems_5 (küçük açık elmas sandığı), ic_respawn (yeniden doğma simgesi), cloud_tile (sınır bulutu dokusu) → assets/ui
  - kaldırılan eşyaların görselleri atlastan atılır: etkinlik kaskları, pelerin, genel sandık simgesi; dünya sandıkları
    artık kişisel üs sandıklarının görselini kullandığı için s/crate, s/crate_mil, s/supply_crate da atılır
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from atlas_patch import Atlas  # noqa: E402
from v17_art import key_out  # noqa: E402

UI = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../android/app/src/main/assets/ui")
# Event_Items klasörü dosya adı sırasıyla
EVENT = ["base_neck", "base_ring", "base_weapon", "blood_neck", "blood_ring", "blood_weapon", "breath_neck", "breath_ring",
         "breath_weapon", "pack_neck", "pack_ring", "pack_weapon", "scrap_neck", "scrap_ring", "scrap_weapon", "titan_neck",
         "titan_ring", "titan_weapon"]
UI_NEW = {"1793209407412": ("gems_5", 384), "1793209408997": ("ic_respawn", 192)}
CLOUD = "1793209410618"
DROP = {"i/ev_blood_helm", "i/ev_titan_helm", "i/ev_base_helm", "i/ev_scrap_helm", "i/ev_pack_helm", "i/ev_breath_helm",
        "i/cloak", "i/chest", "s/crate", "s/crate_mil", "s/supply_crate"}


def fit(im, n):
    s = n / max(im.size)
    return im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)


def cloud_tile(path):
    """Yeşil kenarı kesilmiş, kenarları yumuşak geçişli (dikişsiz) bulut dokusu"""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.int32)
    notg = (a[:, :, 1] - np.maximum(a[:, :, 0], a[:, :, 2])) < 40
    ys, xs = np.where(notg)
    im = Image.open(path).convert("RGB").crop((xs.min() + 3, ys.min() + 3, xs.max() - 2, ys.max() - 2))
    im = im.resize((256, 256), Image.LANCZOS)
    # dikişsiz: yarım kaydırılmış kopyayla kenarlarda karıştır
    b = np.asarray(im).astype(np.float32)
    sh = np.roll(np.roll(b, 128, 0), 128, 1)
    t = np.linspace(-1, 1, 256)
    w = np.clip(np.maximum(np.abs(t)[None, :], np.abs(t)[:, None]) * 1.6 - 0.6, 0, 1)[..., None]
    out = b * (1 - w) + sh * w
    return Image.fromarray(out.clip(0, 255).astype(np.uint8))


def main():
    src = sys.argv[1]
    A = Atlas()
    sp = A.m["sprites"]
    groups = {i: g for g, ids in A.m["groups"].items() for i in ids}
    ev_dir = os.path.join(src, "Event_Items")
    files = sorted(f for f in os.listdir(ev_dir) if f.lower().endswith((".jpg", ".png")))
    assert len(files) == len(EVENT), len(files)
    adds = {}
    prev = []
    for f, name in zip(files, EVENT):
        key = "i/ev_" + name
        im = key_out(os.path.join(ev_dir, f))
        im.thumbnail((128, 128), Image.LANCZOS)
        old = sp[key]
        ok = old[7] if len(old) > 7 else 1.0
        k = ok * max(old[3], old[4]) / max(im.size)
        adds.setdefault(groups[old[0]], []).append((key, im, round(im.size[0] / 2, 1), round(im.size[1] - 1.0, 1), round(k, 5)))
        prev.append(im)
    touched = set(adds) | {groups[sp[k][0]] for k in DROP if k in sp}
    for g in touched:
        A.patch(g, drop=lambda k: k in DROP, add=adds.get(g, []))
    A.save()
    for stem, (name, n) in UI_NEW.items():
        im = fit(key_out(os.path.join(src, stem + ".jpg")), n)
        im.save(os.path.join(UI, name + ".webp"), quality=90, method=6)
        prev.append(im)
    cloud_tile(os.path.join(src, CLOUD + ".jpg")).save(os.path.join(UI, "cloud_tile.webp"), quality=88)
    if len(sys.argv) > 2:
        S = 160
        sheet = Image.new("RGBA", (S * 7, S * ((len(prev) + 6) // 7)), (60, 70, 60, 255))
        for i, im in enumerate(prev):
            t = im.copy()
            t.thumbnail((S - 6, S - 6))
            sheet.alpha_composite(t, ((i % 7) * S + 3, (i // 7) * S + 3))
        sheet.save(sys.argv[2])


if __name__ == "__main__":
    main()
