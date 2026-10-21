"""Bıçak ve katana: kullanıcının görsellerinden envanter simgesi + elde tutulan (yatay) piksel sprite.

Simge: görsel temizlenir (ai_clean), tsubadaki arka plan delikleri koyu metalle kapanır, kalite korunarak
simge boyuna indirilir (BOX + alfa eşiği + koyu kontur).
Elde: kahramanla aynı piksel yoğunluğunda (MPP), yatay; sap kullanıcının görselinden, katananın kını yerine
çelik bıçak çizilir (hafif eğrilik, sırt/ağız tonları, uç). Tutma noktası ve uç weapons.json'a yazılır.

python3 tools/pixel/weapons_v8.py <çıktı dizini (zx_pix/weapons)>
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
sys.path.insert(0, HERE)
from ai_clean import clean as ai_clean  # noqa: E402

MPP = 0.0075  # kahraman: 226 px ≈ 1.70 m


def load_clean(name, tmp):
    p = os.path.join(tmp, name + "_raw.png")
    if not os.path.exists(p):
        ai_clean(os.path.join(ROOT, "art", "v6", "src", name + ".jpg"), p, "auto")
    a = np.asarray(Image.open(p).convert("RGBA")).copy()
    # anahtarlanmamış kalan parlak yeşil (tsuba delikleri) → koyu metal
    g = a[..., :3].astype(int)
    hole = (g[..., 1] > 150) & (g[..., 0] < 120) & (g[..., 2] < 120) & (a[..., 3] > 0)
    a[hole, :3] = (34, 30, 28)
    ys, xs = np.nonzero(a[..., 3])
    return a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def shrink(a, k):
    """Piksel sanatı küçültme: önçarpımlı BOX ortalama, alfa eşiği, dış kenara koyu kontur."""
    im = Image.fromarray(a)
    w, h = max(1, round(im.width * k)), max(1, round(im.height * k))
    f = a.astype(float)
    pre = f.copy()
    pre[..., :3] *= f[..., 3:] / 255
    s = np.asarray(Image.fromarray(pre.clip(0, 255).astype(np.uint8)).resize((w, h), Image.BOX)).astype(float)
    al = np.asarray(Image.fromarray(a[..., 3]).resize((w, h), Image.BOX)).astype(float)
    rgb = np.where(al[..., None] > 0, s[..., :3] * 255 / np.maximum(al[..., None], 1), 0)
    out = np.zeros((h, w, 4), np.uint8)
    on = al > 110
    out[..., :3] = rgb.clip(0, 255)
    out[..., 3] = np.where(on, 255, 0)
    return outline(out)


def outline(out):
    on = out[..., 3] > 0
    pad = np.pad(on, 1)
    edge = on & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    out = out.copy()
    out[edge, :3] = (out[edge, :3] * 0.45).astype(np.uint8)
    return out


def rotate(a, deg):
    """Döndür (en yakın örnek, 4× büyütüp küçülterek merdiven kenarı az)."""
    im = Image.fromarray(a)
    big = im.resize((im.width * 4, im.height * 4), Image.NEAREST).rotate(deg, Image.NEAREST, expand=True)
    return np.asarray(big.resize((big.width // 4, big.height // 4), Image.NEAREST))


def icon(a, size):
    k = size / max(a.shape[:2])
    return shrink(a, k)


def knife_hand(src):
    """Yatay bıçak: sap solda, uç sağda; 0.30 m."""
    L = 0.30 / MPP
    k = L / src.shape[1]
    out = shrink(src, k)
    h, w = out.shape[:2]
    # tutma noktası: sapın ortası (sap ≈ uzunluğun %45'i)
    return out, (w * 0.22, h / 2), (w - 1.0, h / 2)


def katana_hand(src):
    """Yatay katana: kaynaktaki sap + tsuba (kınlı görsel 27.7° eğik) yatay çevrilip küçültülür, kın yerine
    çelik bıçak çizilir. Toplam ≈ 1.0 m."""
    ang = math.degrees(math.atan2(31, 50))  # topuz → tsuba ekseni
    r = rotate(src, ang)
    # sap + tsuba: döndürülmüş görselin sol ucundan tsubanın biraz sağına kadar
    on = r[..., 3] > 0
    cols = np.nonzero(on.any(0))[0]
    x0 = cols[0]
    handle_len = math.hypot(50, 31)  # topuz → tsuba merkezi (kaynak px)
    cut = int(x0 + handle_len + 9)
    rows = np.nonzero(on[:, x0:x0 + 20].any(1))[0]
    cy = (rows[0] + rows[-1]) / 2
    piece = r[int(cy - 16):int(cy + 17), x0:cut].copy()
    k = (0.27 / MPP) / (handle_len + 9)
    hnd = shrink(piece, k)
    hh, hw = hnd.shape[:2]
    blade_len = int(round(0.72 / MPP))
    W, H = hw + blade_len + 2, max(hh, 12) + 8
    out = np.zeros((H, W, 4), np.uint8)
    oy = (H - hh) // 2
    out[oy:oy + hh, :hw] = np.where(hnd[..., 3:] > 0, hnd, out[oy:oy + hh, :hw])
    mid = oy + hh / 2.0
    # bıçak: habaki (pirinç) + çelik; sırt üstte, ağız altta; eğrilik (sori) uca doğru yukarı
    x_start = hw - 1
    col = {"ol": (20, 20, 24), "spine": (70, 76, 86), "body": (150, 158, 170), "hamon": (205, 212, 222),
           "edge": (238, 242, 248), "hab": (186, 146, 64)}
    for i in range(blade_len):
        x = x_start + i
        t = i / blade_len
        curve = 3.2 * t * t  # yukarı eğrilik
        wdt = 3.6 - 1.0 * t
        top = mid - wdt / 2 - curve
        if t > 0.9:  # kissaki: alt kenar uca doğru yükselir
            top += 0.0
        bot = mid + wdt / 2 - curve - (max(0.0, t - 0.88) / 0.12) * (wdt - 0.6)
        y0, y1 = int(math.floor(top)), int(math.ceil(bot))
        for y in range(y0 - 1, y1 + 1):
            if not (0 <= y < H):
                continue
            if y == y0 - 1 or y == y1:
                c = col["ol"]
            elif i < 3:
                c = col["hab"]
            else:
                rel = (y - y0) / max(1, (y1 - y0 - 1))
                c = col["spine"] if rel < 0.25 else col["body"] if rel < 0.55 else col["hamon"] if rel < 0.85 else col["edge"]
            out[y, x] = (*c, 255)
    # parıltı: bıçak boyunca seyrek açık pikseller
    for i in range(8, blade_len - 6, 13):
        x = x_start + i
        t = i / blade_len
        y = int(round(mid - 3.2 * t * t))
        if 0 <= y < H and out[y, x, 3]:
            out[y, x, :3] = (250, 252, 255)
    on = out[..., 3] > 0
    ys, xs = np.nonzero(on)
    out = out[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    oyy = ys.min()
    grip = (hw * 0.62, mid - oyy)  # tek el, tsubanın hemen altı
    tip = (out.shape[1] - 1.0, mid - oyy - 3.2)
    return out, grip, tip


def main(out):
    tmp = "/home/user/zx_v8"
    os.makedirs(tmp, exist_ok=True)
    os.makedirs(out, exist_ok=True)
    kn = load_clean("w_knife", tmp)
    ka = load_clean("w_katana", tmp)
    items = os.path.join(ROOT, "art", "v6", "items")
    Image.fromarray(icon(rotate(kn, 35), 40)).save(os.path.join(items, "w_knife.png"))
    Image.fromarray(icon(ka, 46)).save(os.path.join(items, "w_katana.png"))
    meta = {}
    for name, (img, grip, tip) in (("knife", knife_hand(kn)), ("katana", katana_hand(ka))):
        Image.fromarray(img).save(os.path.join(out, name + ".png"))
        meta[name] = {"grip": [round(grip[0], 1), round(grip[1], 1)], "tip": [round(tip[0], 1), round(tip[1], 1)]}
    json.dump(meta, open(os.path.join(out, "weapons.json"), "w"))
    print(meta)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/home/user/zx_pix/weapons")
