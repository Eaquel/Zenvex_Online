"""Yapay zekâ görsellerini oyuna almak için ortak araçlar (V13 paketleri).

key(yol, zemin)       → RGBA numpy dizisi: yeşil/macenta zemin silinir, kenardaki renk sızıntısı bastırılır;
                         "black" zeminde (efekt sayfaları) parlaklıktan saydamlık çıkarılır
parts(rgba, ...)      → bağlı bileşenler (ayrı duran figürler) satır-sütun sırasıyla [(x0, y0, x1, y1)]
cells(rgba, n)        → tek satırlı eşit kareli sayfa
pixelize(img, s)      → görsel s piksellik bloklarla çizilmişse gerçek ızgaraya indirir (kenar keskin kalır)
"""
import numpy as np
from PIL import Image


def key(path, bg="green"):
    im = Image.open(path)
    a = np.asarray(im.convert("RGB")).astype(np.int32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    if bg == "black":
        m = a.max(-1).astype(float)
        al = np.clip((m - 10) * 1.6, 0, 255)
        rgb = np.where(al[..., None] > 0, np.clip(a * 255.0 / np.maximum(m, 1)[..., None], 0, 255), 0)
        return np.dstack([rgb, al]).astype(np.uint8)
    if bg == "magenta":
        k = (r > 140) & (b > 140) & (g < 120) & (np.abs(r - b) < 90)
        spill = ~k & (r > g + 50) & (b > g + 50)
        out = a.copy()
        out[..., 0] = np.where(spill, np.minimum(r, g + 40), r)
        out[..., 2] = np.where(spill, np.minimum(b, g + 40), b)
    else:
        mx = np.maximum(r, b)
        k = (g - mx > 55) & (g > 100)
        spill = ~k & (g - mx > 18)
        out = a.copy()
        out[..., 1] = np.where(spill, mx + (g - mx) // 4, g)
    # tek tük kalan zemin pikselleri: 4 komşusunun 3'ü zemin olan pikseller de silinir
    kk = k.copy()
    n = np.zeros(k.shape, np.int32)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n += np.roll(np.roll(kk, dy, 0), dx, 1)
    k = kk | (n >= 3) & spill
    al = np.where(k, 0, 255)
    return np.dstack([out, al]).astype(np.uint8)


def to_img(a):
    return Image.fromarray(a, "RGBA")


def bbox(a, thr=8):
    ys, xs = np.nonzero(a[..., 3] > thr)
    if len(xs) == 0:
        return None
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def crop(a, thr=8, pad=0):
    bb = bbox(a, thr)
    if bb is None:
        return a[:1, :1]
    x0, y0, x1, y1 = bb
    return a[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad]


def parts(a, min_area=400, gap=6, thr=40):
    """ayrık figürler: saydamlık maskesi gap kadar genişletilip bileşenlere ayrılır; okuma sırası (satır, sonra x)"""
    from scipy import ndimage
    m = a[..., 3] > thr
    if gap:
        m2 = ndimage.binary_dilation(m, iterations=gap)
    else:
        m2 = m
    lab, n = ndimage.label(m2)
    out = []
    for i, sl in enumerate(ndimage.find_objects(lab)):
        if sl is None:
            continue
        ys, xs = sl
        area = (m[ys, xs] & (lab[ys, xs] == i + 1)).sum()
        if area < min_area:
            continue
        out.append((xs.start, ys.start, xs.stop, ys.stop))
    if not out:
        return out
    hs = np.median([b[3] - b[1] for b in out])
    out.sort(key=lambda b: ((b[1] + b[3]) / 2, b[0]))
    rows = []
    for b in out:
        cy = (b[1] + b[3]) / 2
        if rows and abs(cy - rows[-1][0]) < hs * 0.5:
            rows[-1][1].append(b)
        else:
            rows.append([cy, [b]])
    res = []
    for _, r in rows:
        res += sorted(r, key=lambda b: b[0])
    return res


def cells(a, n):
    w = a.shape[1] / n
    return [a[:, int(round(i * w)):int(round((i + 1) * w))] for i in range(n)]


def pixelize(a, s, hard=True):
    """s piksellik bloklardan gerçek ızgaraya: önceden çarpılmış alfa ile kutu süzgeci; hard=True ise alfa 0/255"""
    a = a.astype(np.float32)
    h, w = a.shape[:2]
    nw, nh = max(1, int(round(w / s))), max(1, int(round(h / s)))
    al = a[..., 3:4] / 255.0
    pm = np.concatenate([a[..., :3] * al, a[..., 3:4]], -1)
    chans = [np.asarray(Image.fromarray(pm[..., c]).resize((nw, nh), Image.BOX)) for c in range(4)]
    o = np.dstack(chans)
    A = o[..., 3:4]
    rgb = np.where(A > 0, o[..., :3] * 255.0 / np.maximum(A, 1e-3), 0)
    alpha = np.where(A[..., 0] >= 110, 255, 0) if hard else A[..., 0]
    return np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8)


def flip(a):
    return a[:, ::-1].copy()


def key_soft(path, lo=38.0, hi=95.0, spill=1.0):
    """Parlayan (yarı saydam ışıltılı) nesneler için yeşil zemin: zemin rengi köşelerden ölçülür. Saydamlık "yeşil
    baskınlığından" (g − max(r, b), zemininkine oranla) çıkarılır, zeminle karışmış pikseller ayrıştırılır
    (c = (piksel − (1−a)·zemin) / a) ve yarı saydam piksellerde yeşil max(r, b)·[spill] ile sınırlanır. Böylece camgöbeği
    ve mor ışıltılar yeşile kaymaz; asit yeşili vurgular için spill > 1 verilir."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    h, w = a.shape[:2]
    s = max(4, min(h, w) // 40)
    corners = np.concatenate([a[:s, :s].reshape(-1, 3), a[:s, -s:].reshape(-1, 3), a[-s:, :s].reshape(-1, 3), a[-s:, -s:].reshape(-1, 3)])
    bg = np.median(corners, 0)
    bgg = max(1.0, bg[1] - max(bg[0], bg[2]))
    d = np.sqrt(((a - bg) ** 2).sum(-1))
    gr = a[..., 1] - np.maximum(a[..., 0], a[..., 2])
    al = np.clip(1.0 - gr / bgg, 0, 1)
    al = np.where(d < lo, 0, al)
    al = np.where((d < hi) & (al > 0.98), np.clip((d - lo) / (hi - lo), 0, 1), al)
    rgb = (a - (1 - al[..., None]) * bg) / np.maximum(al[..., None], 1e-3)
    mx = np.maximum(rgb[..., 0], rgb[..., 2])
    g = rgb[..., 1]
    rgb[..., 1] = np.where(al < 0.98, np.minimum(g, mx * spill), g)
    al = np.where(al < 0.06, 0, al)
    return np.dstack([np.clip(rgb, 0, 255), al * 255]).astype(np.uint8)
