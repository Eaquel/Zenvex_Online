#!/usr/bin/env python3
"""Lobi galerisi için oyun karelerinden kırpılmış küçük görseller: assets/ui/shot_<ad>.webp (780x360).
Kaynak: android/app/build/promo_hud/<klip>/f%04d.png (gerçek oyun arayüzüyle; bkz. docs/23_TANITIM.md). Çalıştırma: python3 tools/promo/stills.py"""
import os
from PIL import Image, ImageEnhance

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PROMO = os.path.join(ROOT, "android/app/build/promo_hud")
OUT = os.path.join(ROOT, "android/app/src/main/assets/ui")
# ad: (klip, kare, merkez kaydırma x/y piksel, yakınlaştırma, parlaklık)
PICKS = {
    "scythe": ("horde", 120, (0, 0), 1.7, 1.0),
    "rpg": ("rocket", 100, (0, 0), 1.6, 1.0),
    "moto": ("chase", 150, (0, 0), 1.7, 1.0),
    "magma": ("titan", 110, (0, 0), 1.6, 1.0),
    "ice": ("frost", 185, (0, 0), 1.7, 1.0),
    "rune": ("titan", 20, (0, 0), 1.5, 1.0),
}


def main():
    for name, (clip, fr, (dx, dy), z, br) in PICKS.items():
        p = os.path.join(PROMO, clip, f"f{fr:04d}.png")
        if not os.path.exists(p):
            print("yok:", p)
            continue
        im = Image.open(p).convert("RGB")
        w, h = im.size
        cw, ch = int(w / z), int(h / z)
        cx, cy = w // 2 + dx, h // 2 + dy
        box = (cx - cw // 2, cy - ch // 2, cx + cw // 2, cy + ch // 2)
        im = im.crop(box).resize((780, 360), Image.LANCZOS)
        if br != 1.0:
            im = ImageEnhance.Brightness(im).enhance(br)
        dst = os.path.join(OUT, f"shot_{name}.webp")
        im.save(dst, "WEBP", quality=82, method=6)
        print(dst, os.path.getsize(dst) // 1024, "KB")


if __name__ == "__main__":
    main()
