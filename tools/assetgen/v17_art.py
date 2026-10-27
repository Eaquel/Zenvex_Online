"""V17: kullanıcının ürettiği yeni yapı ve eşya görsellerini (yeşil zeminli JPG) atlasa koyar, eskilerini siler.

Görseller dosya adına göre sıralanır; sıra docs/22 §3 (yapılar) ve docs/24 §1-3 (setler, kolye, yüzük) ile aynıdır.
  - zemin rengi kenarlardan ölçülür (bazı görsellerde koyu yeşil), uzaklığa göre yumuşak saydamlık + yeşil taşma temizliği
  - yapılar: dünyadaki boyut ve çapa (ayak noktası) eski görselden oranla alınır; görsel yüksek çözünürlükte kalır (k)
  - eşyalar: 128 px simge, dünyada eski boyutta (k)

/home/user/zx_venv3d/bin/python tools/assetgen/v17_art.py <klasör> [önizleme.png]
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from atlas_patch import Atlas  # noqa: E402

BUILD = ["apartment0", "apartment1", "police", "shop0", "shop1", "house0", "house1", "house2", "gas", "tower", "wreck0", "wreck1",
         "gate", "wall_x", "fence", "tent", "sandbags", "barrel_tox", "cone", "trash", "log", "billboard"]
ITEMS = ["helm_basic", "jacket_basic", "pants_basic", "boots_basic", "helm_police", "jacket_police", "pants_police", "boots_police",
         "helm_military", "jacket_military", "pants_military", "boots_military", "helm_titan", "jacket_titan", "pants_titan", "boots_titan",
         "neck_bone", "ring_copper", "neck_dark", "ring_titan", "neck_amulet", "ring_hunter"]


def key_out(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    bg = np.median(border, axis=0)
    d = np.linalg.norm(a - bg, axis=2)
    green = a[:, :, 1] - np.maximum(a[:, :, 0], a[:, :, 2])
    alpha = np.clip((d - 60.0) / 55.0, 0, 1)
    # yeşil taşma: yarı saydam kenarda ve parlak yeşilde yeşil kanal diğerlerinin üstüne çıkamaz
    spill = (alpha < 1) | (green > 70)
    g = a[:, :, 1]
    a[:, :, 1] = np.where(spill, np.minimum(g, np.maximum(a[:, :, 0], a[:, :, 2]) + 8), g)
    rgba = np.dstack([np.clip(a, 0, 255), alpha * 255]).astype(np.uint8)
    im = Image.fromarray(rgba, "RGBA")
    bb = im.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox()
    return im.crop(bb)


def main():
    src = sys.argv[1]
    files = sorted(f for f in os.listdir(src) if f.lower().endswith((".jpg", ".png", ".webp")))
    assert len(files) == len(BUILD) + len(ITEMS), len(files)
    A = Atlas()
    sp = A.m["sprites"]
    groups = {}
    for gname, ids in A.m["groups"].items():
        for i in ids:
            groups[i] = gname
    adds = {}
    prev = []
    for f, key in zip(files, ["s/" + b for b in BUILD] + ["i/" + i for i in ITEMS]):
        im = key_out(os.path.join(src, f))
        old = sp[key]
        _, _, _, ow, oh, oax, oay = old[:7]
        ok = old[7] if len(old) > 7 else 1.0
        if key.startswith("i/"):
            im.thumbnail((128, 128), Image.LANCZOS)
            k = ok * max(ow, oh) / max(im.size)
            ax, ay = im.size[0] / 2, im.size[1] - 1.0
        else:
            k = ok * ow / im.size[0]
            ax, ay = oax / ow * im.size[0], oay / oh * im.size[1]
        adds.setdefault(groups[old[0]], []).append((key, im, round(ax, 1), round(ay, 1), round(k, 5)))
        prev.append((key, im))
        print(f"{key}: {im.size} k={k:.3f}")
    for g, items in adds.items():
        A.patch(g, add=items)
    A.save()
    if len(sys.argv) > 2:
        S = 200
        sheet = Image.new("RGBA", (S * 8, S * ((len(prev) + 7) // 8)), (60, 80, 60, 255))
        for i, (_, im) in enumerate(prev):
            t = im.copy()
            t.thumbnail((S - 6, S - 6))
            sheet.alpha_composite(t, ((i % 8) * S + 3, (i // 8) * S + 3))
        sheet.save(sys.argv[2])


if __name__ == "__main__":
    main()
