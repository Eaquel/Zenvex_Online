"""3D render → el yapımı görünümlü piksel sanat.

Adımlar: (1) 2× renderı alan ortalamasıyla yerel boyuta indir, (2) alfa eşiği (yumuşak kenar yok),
(3) "Zenvex Gece" paletine Lab uzayında kilitle (koyu-orta tonlarda hafif Bayer dithering),
(4) iç kontur: dış kenara komşu pikselleri kendi renginin koyusu yap (sel-out),
(5) dış kontur: 1 px koyu çizgi, (6) tek başına kalan pikselleri temizle.
"""
import numpy as np
from PIL import Image

# 48 renk: gece mavisi/kömür, deri/kahve, kemik/sargı, ten, kan/kor, zehir yeşili, mor
PALETTE_HEX = """
0b0a10 141219 1c1a23 26232e 312d3a 3d3848 4a4457 5a5468 6e687c 878198 a39eb2 c4c0cf
1a1210 2a1c16 3b281e 4e3526 63442f 7a573c 93704f ad8d68 c6ab88 ddc9a8 ece0c6 f6f0e2
3a1414 5a1a18 7e2420 a4352a c9502f e0763a f09a52 f7c06e
14241a 1f3a24 2e5530 467238 6a943f 9cba4a
2a1640 3d1f5c 55307a 7344a0 9a66c8
2d4258 3f5d7c 5a83a6
""".split()
PALETTE = np.array([[int(h[i:i + 2], 16) for i in (0, 2, 4)] for h in PALETTE_HEX], dtype=np.float32)
OUTLINE = np.array([10, 8, 14], dtype=np.uint8)

BAYER4 = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=np.float32) / 16.0 - 0.5


def _lab(rgb):
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ m.T / np.array([0.9505, 1.0, 1.089])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)


PAL_LAB = _lab(PALETTE)


def quantize(rgb, dither=0.35):
    """rgb (H,W,3) float → palet dizini (H,W). Dithering: ilk iki adayın arasında Bayer eşiğiyle seçim."""
    h, w, _ = rgb.shape
    lab = _lab(rgb.reshape(-1, 3)).reshape(h, w, 1, 3)
    d = ((lab - PAL_LAB.reshape(1, 1, -1, 3)) ** 2).sum(-1)
    order = np.argsort(d, axis=-1)
    i0, i1 = order[..., 0], order[..., 1]
    d0 = np.take_along_axis(d, i0[..., None], -1)[..., 0]
    d1 = np.take_along_axis(d, i1[..., None], -1)[..., 0]
    # iki aday arasındaki konum (0 = tam i0)
    t = np.sqrt(d0) / (np.sqrt(d0) + np.sqrt(d1) + 1e-6)
    by = np.tile(BAYER4, (h // 4 + 1, w // 4 + 1))[:h, :w]
    pick1 = t > (0.5 - by * dither * 2) * 1.0
    pick1 &= t > 0.32  # çok yakınsa dithering yapma
    return np.where(pick1, i1, i0)


def despeckle(idx, mask, passes=2):
    """Tek başına kalan renk piksellerini komşuların çoğunluk rengine çevirir (temiz renk kümeleri)."""
    for _ in range(passes):
        nb = [np.roll(np.roll(idx, dy, 0), dx, 1) for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))]
        st = np.stack(nb, -1)
        alone = mask & (st != idx[..., None]).all(-1)
        # komşulardan en az ikisi aynıysa o renk
        best = idx.copy()
        for a in range(4):
            cnt = (st == st[..., a:a + 1]).sum(-1)
            best = np.where(alone & (cnt >= 2), st[..., a], best)
        idx = best
    return idx


def grade(rgb, gamma=0.78, contrast=1.18, sat=1.3):
    """Piksel sanat için ton: orta tonları aç, kontrast ve doygunluk artır."""
    c = (rgb / 255.0) ** gamma
    c = (c - 0.5) * contrast + 0.5
    lum = (c * np.array([0.299, 0.587, 0.114])).sum(-1, keepdims=True)
    c = lum + (c - lum) * sat
    return np.clip(c, 0, 1) * 255.0


def pixelize(img, factor=2, dither=0.0, outline=True, selout=True, alpha_cut=0.5, light=True):
    im = img.convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    h, w = a.shape[0] // factor, a.shape[1] // factor
    a = a[: h * factor, : w * factor].reshape(h, factor, w, factor, 4)
    alpha = a[..., 3].mean(axis=(1, 3)) / 255.0
    # renk: alfa ağırlıklı ortalama (kenarda arka plan karışmasın)
    wgt = a[..., 3:4] / 255.0
    rgb = (a[..., :3] * wgt).sum(axis=(1, 3)) / np.maximum(wgt.sum(axis=(1, 3)), 1e-6)
    mask = alpha >= alpha_cut
    rgb = grade(rgb)
    idx = quantize(rgb, dither)
    idx = despeckle(idx, mask)
    out_rgb = PALETTE[idx].astype(np.uint8)
    # tek kalan pikselleri temizle
    n = np.zeros_like(mask, dtype=np.int32)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        n += np.roll(np.roll(mask, dy, 0), dx, 1)
    mask &= n >= 1
    edge = np.zeros_like(mask)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        edge |= mask & ~np.roll(np.roll(mask, dy, 0), dx, 1)
    def side(dy, dx):
        return mask & ~np.roll(np.roll(mask, dy, 0), dx, 1)
    if selout:
        # gölge tarafı (sağ/alt) iç kenar: kendi renginin bir ton koyusu
        shade = side(-1, 0) | side(0, -1)
        dark = out_rgb.astype(np.float32) * 0.6
        di = quantize(dark, 0.0)
        out_rgb = np.where(shade[..., None], PALETTE[di].astype(np.uint8), out_rgb)
    if light:
        # ışık tarafı (sol/üst) kenar: bir ton açık (ışık sol üstten)
        lit = (side(1, 0) | side(0, 1)) & mask
        bright = np.clip(out_rgb.astype(np.float32) * 1.35 + 18, 0, 255)
        bi = quantize(bright, 0.0)
        out_rgb = np.where(lit[..., None], PALETTE[bi].astype(np.uint8), out_rgb)
    res = np.zeros((h, w, 4), dtype=np.uint8)
    res[..., :3] = out_rgb
    res[..., 3] = np.where(mask, 255, 0)
    if outline:
        ring = np.zeros_like(mask)
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ring |= np.roll(np.roll(mask, dy, 0), dx, 1)
        ring &= ~mask
        res[ring, :3] = OUTLINE
        res[ring, 3] = 255
    return Image.fromarray(res, "RGBA")


if __name__ == "__main__":
    import sys
    src, dst = sys.argv[1], sys.argv[2]
    pixelize(Image.open(src)).save(dst)
