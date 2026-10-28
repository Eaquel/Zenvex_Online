"""Oyundaki tüm görsel varlıkları (3B modeller hariç) tek tek PNG olarak dışa aktarır.

python3 tools/export_assets.py <çıktı klasörü>
  sprites/  atlas sprite'ları (zombi, yapı, eşya, pet, NPC, efekt…): her kare ayrı PNG, anahtar yolu klasör düzenidir
  ui/       arayüz görselleri (zaten tek tek; PNG'ye çevrilir)
  ground/   zemin dokuları
  app/      uygulama simgeleri
  sprites_index.csv  anahtar, sayfa, x, y, genişlik, yükseklik, çapa x/y, ölçek
"""
import csv
import json
import os
import re
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../android/app/src/main")
ASSETS = os.path.join(ROOT, "assets")


def safe(name):
    return re.sub(r"[^A-Za-z0-9_.\-/]", "_", name)


def main():
    out = sys.argv[1]
    m = json.load(open(os.path.join(ASSETS, "v2/manifest.json")))
    pages = [Image.open(os.path.join(ASSETS, "v2", p)).convert("RGBA") for p in m["pages"]]
    n = 0
    with open(os.path.join(out, "sprites_index.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["key", "page", "x", "y", "w", "h", "anchor_x", "anchor_y", "scale"])
        for key, v in sorted(m["sprites"].items()):
            pg, x, y, sw, sh = v[:5]
            p = os.path.join(out, "sprites", safe(key) + ".png")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            pages[pg].crop((x, y, x + sw, y + sh)).save(p)
            w.writerow([key, pg, x, y, sw, sh] + list(v[5:8]))
            n += 1
    print("sprite:", n)
    for src, dst in (("ui", "ui"), ("v2/ground", "ground")):
        c = 0
        for fn in sorted(os.listdir(os.path.join(ASSETS, src))):
            try:
                im = Image.open(os.path.join(ASSETS, src, fn)).convert("RGBA")
            except Exception:
                continue
            os.makedirs(os.path.join(out, dst), exist_ok=True)
            im.save(os.path.join(out, dst, os.path.splitext(fn)[0] + ".png"))
            c += 1
        print(dst, c)
    res = os.path.join(ROOT, "res")
    c = 0
    for dp, _, fs in os.walk(res):
        for fn in fs:
            if fn.endswith(".png"):
                rel = os.path.relpath(os.path.join(dp, fn), res).replace("/", "_")
                os.makedirs(os.path.join(out, "app"), exist_ok=True)
                Image.open(os.path.join(dp, fn)).save(os.path.join(out, "app", rel))
                c += 1
    print("app", c)


if __name__ == "__main__":
    main()
