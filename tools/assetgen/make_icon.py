"""Uygulama simgesi: kızıl ay + sis + zombi siluetleri + silahlı kahraman (render_icon.py çıktıları).

/opt/bvenv/bin/python tools/assetgen/make_icon.py
Çıktı: android/app/src/main/res/mipmap-*/ic_launcher.png (eski tip, köşeleri yuvarlak)
       android/app/src/main/res/mipmap-*/ic_launcher_fg.png (uyarlanabilir simge ön katmanı, 108dp)
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SRC = os.path.join(os.environ.get("ZX_WORK3", "/home/user/zx_render3"), "icon")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
N = 1024


def load(name):
    im = Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")
    a = np.array(im)
    a[..., 3] = np.where(a[..., 3] < 150, 0, a[..., 3])  # zemin gölgesini at
    # ayak çevresindeki koyu gölge lekesi (alt %10'da çok koyu pikseller)
    h = a.shape[0]
    low = a[int(h * 0.9):]
    dark = low[..., :3].max(axis=2) < 40
    low[..., 3] = np.where(dark, 0, low[..., 3])
    im = Image.fromarray(a)
    return im.crop(im.getbbox())


def fit(im, h):
    return im.resize((int(im.width * h / im.height), h), Image.LANCZOS)


def tint(im, rgb, k):
    """Siluet: rengi rgb'ye doğru k oranında çek (alfa korunur)."""
    a = np.array(im).astype(np.float32)
    for c in range(3):
        a[..., c] = a[..., c] * (1 - k) + rgb[c] * k
    return Image.fromarray(a.clip(0, 255).astype(np.uint8))


def glow_of(im, color, rad):
    a = im.getchannel("A").filter(ImageFilter.GaussianBlur(rad))
    g = Image.new("RGBA", im.size, color)
    g.putalpha(a)
    return g


def background():
    y, x = np.mgrid[0:N, 0:N].astype(np.float32) / N
    # koyu kızıl gökyüzü → aşağıda neredeyse siyah
    top = np.array([120, 18, 22], np.float32); bot = np.array([10, 6, 10], np.float32)
    t = np.clip(y * 1.15, 0, 1)[..., None]
    img = top * (1 - t) + bot * t
    # ay ışığı halesi
    mx, my = 0.5, 0.36
    d = np.sqrt((x - mx) ** 2 + (y - my) ** 2)
    img += (np.exp(-(d / 0.33) ** 2) * 120)[..., None] * np.array([1.0, 0.45, 0.25])
    base = Image.fromarray(img.clip(0, 255).astype(np.uint8)).convert("RGBA")
    # kızıl ay
    r = 205
    cx, cy = int(mx * N), int(my * N)
    moon = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    md = ImageDraw.Draw(moon)
    md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(236, 92, 52, 255))
    for ox, oy, rr in [(-70, -40, 38), (55, 30, 28), (10, 95, 22), (-95, 70, 16), (85, -85, 20)]:
        md.ellipse([cx + ox - rr, cy + oy - rr, cx + ox + rr, cy + oy + rr], fill=(200, 64, 40, 255))
    base.alpha_composite(moon.filter(ImageFilter.GaussianBlur(1.5)))
    # yıkık şehir siluetleri, yanan pencereler
    dr = ImageDraw.Draw(base)
    rng = np.random.default_rng(7)
    x0 = 0
    hz = int(N * 0.70)
    while x0 < N:
        w = int(rng.integers(40, 100)); h = int(rng.integers(40, 150))
        dr.rectangle([x0, hz - h, x0 + w, N], fill=(34, 12, 16, 255))
        for _ in range(int(rng.integers(0, 4))):
            wx = x0 + int(rng.integers(6, max(7, w - 12))); wy = hz - h + int(rng.integers(8, max(9, h - 10)))
            dr.rectangle([wx, wy, wx + 7, wy + 9], fill=(255, 150, 60, 255))
        x0 += w + int(rng.integers(0, 10))
    dr.rectangle([0, hz, N, N], fill=(20, 8, 10, 255))
    return base


def fog_layer():
    y = np.mgrid[0:N, 0:N][0].astype(np.float32) / N
    fog = np.zeros((N, N, 4), np.uint8)
    f = np.clip((y - 0.62) / 0.38, 0, 1)
    fog[..., 0] = 60; fog[..., 1] = 20; fog[..., 2] = 24
    fog[..., 3] = (f ** 1.4 * 200).astype(np.uint8)
    return Image.fromarray(fog)


def compose():
    img = background()
    hero = fit(load("hero"), 760)
    z1 = tint(fit(load("z1"), 660), (24, 6, 10), 0.86)
    z2 = tint(fit(load("z2"), 620), (24, 6, 10), 0.86)
    ground = int(N * 0.97)
    # arkadaki zombiler (karanlık siluet + kırmızı kenar parıltısı)
    for z, x in ((z1, int(N * 0.18)), (z2, int(N * 0.80))):
        px, py = x - z.width // 2, ground - 40 - z.height
        img.alpha_composite(glow_of(z, (255, 50, 30, 190), 9), (px, py))
        img.alpha_composite(z, (px, py))
    img.alpha_composite(fog_layer())
    # kahraman: turuncu kenar ışığı + kendisi
    hx, hy = N // 2 - hero.width // 2, ground - hero.height
    img.alpha_composite(glow_of(hero, (255, 140, 60, 200), 14), (hx, hy))
    img.alpha_composite(hero, (hx, hy))
    # kenar kararması
    y, x = np.mgrid[0:N, 0:N].astype(np.float32) / N
    v = np.clip((np.sqrt((x - 0.5) ** 2 + (y - 0.5) ** 2) - 0.38) / 0.35, 0, 1)
    vig = np.zeros((N, N, 4), np.uint8)
    vig[..., 3] = (v * 170).astype(np.uint8)
    img.alpha_composite(Image.fromarray(vig))
    return img


def main():
    img = compose()
    os.makedirs(SRC, exist_ok=True)
    img.save(os.path.join(SRC, "icon_full.png"))
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for d, k in dens.items():
        out = os.path.join(RES, f"mipmap-{d}")
        os.makedirs(out, exist_ok=True)
        # uyarlanabilir: 108dp tam katman (kenarlar maskede kırpılır, içerik ortadaki 66dp'de)
        fg = int(108 * k)
        img.resize((fg, fg), Image.LANCZOS).save(os.path.join(out, "ic_launcher_fg.png"))
        # eski tip: 48dp, yuvarlak köşe; görüntünün orta %80'i
        s = int(48 * k)
        c = int(N * 0.1)
        leg = img.crop((c, c, N - c, N - c)).resize((s, s), Image.LANCZOS)
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=255)
        leg.putalpha(mask)
        leg.save(os.path.join(out, "ic_launcher.png"))
    print("simge yazıldı")


if __name__ == "__main__":
    main()
