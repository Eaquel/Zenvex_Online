"""V18: kullanıcının ürettiği mağaza görsellerini (yeşil zeminli JPG) assets/ui altına webp olarak koyar.

Görseller dosya adına göre sıralanır; ilk 44'ü yapı/eşya (v17_art.py), sonrası docs/25 sırasıyla mağaza görselleri:
sekme simgeleri (yıldız ters), biletler, kartlar, paketler, elmas yığınları ve mağaza arka planı.
python3 tools/assetgen/v18_shop.py <klasör>
"""
import os
import sys

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../android/app/src/main/assets/ui")
# sıralı dosya (44'ten sonra) → ad
NAMES = ["shoptab_5", "shoptab_4", "shoptab_3", "shoptab_2", "shoptab_1", "shoptab_0", "shop_bg", "ticket_premium", "ticket_vip",
         "card_month", "card_week", "pack_war", "pack_growth", "pack_start", "gems_6", "gems_4", "gems_3", "gems_2", "gems_1"]
SIZE = {"shoptab": 128, "gems": 384, "pack": 384, "card": 384, "ticket": 384}


def key_out(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    bg = np.median(border, axis=0)
    d = np.linalg.norm(a - bg, axis=2)
    green = a[:, :, 1] - np.maximum(a[:, :, 0], a[:, :, 2])
    alpha = np.clip((d - 60.0) / 55.0, 0, 1)
    spill = (alpha < 1) | (green > 70)
    g = a[:, :, 1]
    a[:, :, 1] = np.where(spill, np.minimum(g, np.maximum(a[:, :, 0], a[:, :, 2]) + 8), g)
    im = Image.fromarray(np.dstack([np.clip(a, 0, 255), alpha * 255]).astype(np.uint8), "RGBA")
    return im.crop(im.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox())


def fit(im, n):
    s = n / max(im.size)
    return im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)


def main():
    src = sys.argv[1]
    files = sorted(f for f in os.listdir(src) if f.lower().endswith((".jpg", ".png", ".webp")))[44:]
    assert len(files) == len(NAMES), len(files)
    made = {}
    for f, n in zip(files, NAMES):
        p = os.path.join(src, f)
        if n == "shop_bg":
            # yeşil çerçeveyi kes: yeşil olmayan en büyük dikdörtgen
            a = np.asarray(Image.open(p).convert("RGB")).astype(np.int32)
            notg = (a[:, :, 1] - np.maximum(a[:, :, 0], a[:, :, 2])) < 60
            ys, xs = np.where(notg)
            im = Image.open(p).convert("RGB").crop((xs.min() + 2, ys.min() + 2, xs.max() - 1, ys.max() - 1))
            im = im.resize((im.width * 4, im.height * 4), Image.LANCZOS)
            im.save(os.path.join(OUT, n + ".webp"), quality=82)
            continue
        im = fit(key_out(p), SIZE[n.split("_")[0]])
        made[n] = im
        im.save(os.path.join(OUT, n + ".webp"), quality=90, method=6)
    # gems_5 (açık demir sandık) gelmedi: gems_4 sandığı + önünde yığın ile geçici birleşim
    if "gems_5" not in made and "gems_4" in made:
        c = Image.new("RGBA", (384, 384), (0, 0, 0, 0))
        b = made["gems_4"].resize((int(made["gems_4"].width * 0.95), int(made["gems_4"].height * 0.95)), Image.LANCZOS)
        c.alpha_composite(b, ((384 - b.width) // 2, 20))
        f = made["gems_2"].resize((int(made["gems_2"].width * 0.62), int(made["gems_2"].height * 0.62)), Image.LANCZOS)
        c.alpha_composite(f, ((384 - f.width) // 2 + 70, 384 - f.height - 4))
        c.save(os.path.join(OUT, "gems_5.webp"), quality=90, method=6)
    print("tamam:", len(NAMES), "dosya")


if __name__ == "__main__":
    main()
