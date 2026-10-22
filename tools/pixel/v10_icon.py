"""V10 uygulama simgesi (art/v10/icon/app_icon.png, 128 piksel) → mipmap-*/ic_launcher(.png, _fg.png)

Uyarlanabilir simge: ön katman 108dp tuvalin ortasında 84dp sahne; maskeler en fazla 72dp gösterdiği için sahnenin
kenarı hiç görünmez (görünen kısım sahnenin ~%86'sı). Eski tip simge: sahnenin tamamı, köşeler yuvarlak.
Piksel görsel önce tam katla büyütülür, sonra hedefe yumuşak küçültülür (eşit olmayan piksel boyları olmaz).

python3 tools/pixel/v10_icon.py
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SRC = os.path.join(ROOT, "art", "v10", "icon", "app_icon.png")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def scaled(im, n):
    big = im.resize((im.width * 8, im.height * 8), Image.NEAREST)
    return big.resize((n, n), Image.LANCZOS)


def main():
    im = Image.open(SRC).convert("RGBA")
    for d, k in DENS.items():
        fg_n = int(108 * k)
        scene = int(round(84 * k))
        fg = Image.new("RGBA", (fg_n, fg_n), (0, 0, 0, 0))
        o = (fg_n - scene) // 2
        fg.paste(scaled(im, scene), (o, o))
        fg.save(os.path.join(RES, f"mipmap-{d}", "ic_launcher_fg.png"))
        n = int(48 * k)
        leg = scaled(im, n)
        mask = Image.new("L", (n, n), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * 0.18), fill=255)
        leg.putalpha(mask)
        leg.save(os.path.join(RES, f"mipmap-{d}", "ic_launcher.png"))
        print(d, fg.size, leg.size)


if __name__ == "__main__":
    main()
