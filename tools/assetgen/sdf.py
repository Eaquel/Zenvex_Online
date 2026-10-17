"""SDF tabanlı 3D -> pixel art render motoru.

Sahne: (malzeme_adı, sdf_fonksiyonu) listesi. Kamera üstten 3/4 ortografik.
Her piksel için ışın yürütülür, isabet eden en yakın primitifin malzemesi alınır,
normal yönüne göre 3 tona indirgenir, sonra renkli dış çizgi eklenir.
"""
import math
import numpy as np

PITCH = math.radians(35.0)          # kamera eğimi (yatayla)
METERS_PER_PX = 0.036               # 1.8 m karakter ≈ 46 px
LIGHT = np.array([-0.55, 0.75, 0.45])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

FWD = np.array([0.0, -math.sin(PITCH), -math.cos(PITCH)])
RIGHT = np.array([1.0, 0.0, 0.0])
UP = np.array([0.0, math.cos(PITCH), -math.sin(PITCH)])


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ---------------------------------------------------------------- SDF yardımcıları
def v3(x, y, z):
    return np.array([x, y, z], dtype=np.float64)


def length(p):
    return np.sqrt(np.einsum("...i,...i->...", p, p))


def sd_sphere(c, r):
    c = np.asarray(c, float)
    return lambda p: length(p - c) - r


def sd_round_cone(a, b, r1, r2):
    """IQ round cone: a ucunda r1, b ucunda r2 yarıçap."""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    ba = b - a
    l2 = float(ba @ ba)
    if l2 < 1e-10:
        return sd_sphere(a, max(r1, r2))
    rr = r1 - r2
    a2 = l2 - rr * rr
    il2 = 1.0 / l2

    def f(p):
        pa = p - a
        y = pa @ ba
        z = y - l2
        xv = pa * l2 - y[..., None] * ba
        x2 = np.einsum("...i,...i->...", xv, xv)
        y2 = y * y * l2
        z2 = z * z * l2
        k = math.copysign(1.0, rr) * rr * rr * x2
        out = (np.sqrt(x2 * a2 * il2) + y * rr) * il2 - r1
        m1 = np.sign(z) * a2 * z2 > k
        m2 = np.sign(y) * a2 * y2 < k
        out = np.where(m1, np.sqrt(x2 + z2) * il2 - r2, out)
        out = np.where(m2 & ~m1, np.sqrt(x2 + y2) * il2 - r1, out)
        return out
    return f


def sd_round_box(c, R, half, r):
    """R: 3x3 yön matrisi (sütunlar yerel eksenler)."""
    c = np.asarray(c, float)
    half = np.asarray(half, float)

    def f(p):
        q = np.abs((p - c) @ R) - half + r
        return length(np.maximum(q, 0.0)) + np.minimum(np.max(q, axis=-1), 0.0) - r
    return f


def sd_ellipsoid(c, R, radii):
    c = np.asarray(c, float)
    radii = np.asarray(radii, float)

    def f(p):
        q = (p - c) @ R
        k0 = length(q / radii)
        k1 = length(q / (radii * radii))
        return k0 * (k0 - 1.0) / np.maximum(k1, 1e-9)
    return f


def sd_cut(f, n, d):
    """f'yi n·p > d yarım uzayıyla keser (sadece o tarafı bırakır)."""
    n = np.asarray(n, float)
    n = n / np.linalg.norm(n)
    return lambda p: np.maximum(f(p), d - p @ n)


def sd_displace(f, amp, freq, seed=0.0):
    """Yaprak/kaya dokusu için sinüs tabanlı gürültü yer değiştirmesi."""
    def g(p):
        x, y, z = p[..., 0] * freq, p[..., 1] * freq, p[..., 2] * freq
        n = (np.sin(x * 1.7 + seed) * np.sin(y * 1.3 + 2.1 * seed) * np.sin(z * 1.9 + 0.7)
             + 0.5 * np.sin(x * 3.1 + y * 2.3 + seed) * np.sin(z * 3.7 - y * 1.1))
        return f(p) + amp * n
    return g


# ---------------------------------------------------------------- rotasyonlar
def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(a):
    """Pozitif açı: +y ekseni +z'ye (öne) eğilir."""
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


# ---------------------------------------------------------------- render
class Material:
    def __init__(self, light, base, shade, dark):
        self.tones = [hexc(light), hexc(base), hexc(shade), hexc(dark)]


def render(scene, width, height, anchor, materials, decals=(), mpp=METERS_PER_PX,
           steps=72, max_t=12.0, light_cut=(0.55, 0.12), outline=True, inner_lines=True,
           tone_fn=None):
    """scene: [(mat, sdf)] ; anchor: dünya orijininin düştüğü piksel (x, y).
    decals: [(dünya_noktası, renk_hex, normal_yönü_veya_None)] tek piksel işaretler.
    Döndürür: RGBA uint8 dizisi (h, w, 4)."""
    ys, xs = np.mgrid[0:height, 0:width]
    sx = (xs + 0.5 - anchor[0]) * mpp
    sy = (anchor[1] - (ys + 0.5)) * mpp
    origin = sx[..., None] * RIGHT + sy[..., None] * UP - FWD * (max_t * 0.5)
    origin = origin.reshape(-1, 3)
    n_pix = origin.shape[0]
    t = np.zeros(n_pix)
    hit = np.zeros(n_pix, bool)
    alive = np.ones(n_pix, bool)
    sdfs = [s for _, s in scene]

    def eval_scene(p):
        ds = np.stack([s(p) for s in sdfs], axis=0)
        return ds.min(axis=0), ds.argmin(axis=0)

    idx = np.arange(n_pix)
    for _ in range(steps):
        act = idx[alive]
        if act.size == 0:
            break
        p = origin[act] + t[act, None] * FWD
        d, _ = eval_scene(p)
        newly = d < 0.002
        hit[act[newly]] = True
        t[act] += np.maximum(d, 0.0) * 0.9
        dead = newly | (t[act] > max_t)
        alive[act[dead]] = False

    out = np.zeros((n_pix, 4), np.uint8)
    depth = np.full(n_pix, np.inf)
    matid = np.full(n_pix, -1)
    tone = np.zeros(n_pix, int)
    hi = idx[hit]
    if hi.size:
        p = origin[hi] + t[hi, None] * FWD
        _, mid = eval_scene(p)
        e = 0.004
        nrm = np.zeros_like(p)
        for k in range(3):
            dv = np.zeros(3)
            dv[k] = e
            nrm[:, k] = eval_scene(p + dv)[0] - eval_scene(p - dv)[0]
        nrm /= np.maximum(length(nrm), 1e-9)[:, None]
        lam = nrm @ LIGHT
        tn = np.where(lam > light_cut[0], 0, np.where(lam > light_cut[1], 1, 2))
        if tone_fn is not None:
            tn = tone_fn(p, nrm, tn, mid)
        depth[hi] = t[hi]
        matid[hi] = mid
        tone[hi] = tn
        for i, (mname, _) in enumerate(scene):
            sel = mid == i
            if not sel.any():
                continue
            tones = np.array(materials[mname].tones, np.uint8)
            out[hi[sel], :3] = tones[tn[sel]]
            out[hi[sel], 3] = 255

    out = out.reshape(height, width, 4)
    depth = depth.reshape(height, width)
    matid = matid.reshape(height, width)

    # tek piksellik işaretler (göz vb.)
    for pt, col, nvec in decals:
        pt = np.asarray(pt, float)
        if nvec is not None and np.dot(nvec, -FWD) < 0.15:
            continue
        rel = pt
        px = rel @ RIGHT / mpp + anchor[0]
        py = anchor[1] - rel @ UP / mpp
        dz = rel @ FWD + max_t * 0.5
        ix, iy = int(math.floor(px)), int(math.floor(py))
        if 0 <= ix < width and 0 <= iy < height and depth[iy, ix] + 0.05 > dz:
            out[iy, ix, :3] = hexc(col)
            out[iy, ix, 3] = 255

    mat_names = [m for m, _ in scene]
    if inner_lines:
        # derinlik sıçramalarında (kol gövdenin önünde vb.) koyu ton çizgisi
        res = out.copy()
        for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            nd = np.roll(np.roll(depth, dy, 0), dx, 1)
            with np.errstate(invalid='ignore'):
                mask = (out[..., 3] > 0) & np.isfinite(nd) & (depth - nd > 0.22)
            for yy, xx in zip(*np.nonzero(mask)):
                m = materials[mat_names[matid[yy, xx]]]
                res[yy, xx, :3] = m.tones[3] if True else m.tones[2]
        out = res
    if outline:
        res = out.copy()
        a = out[..., 3] > 0
        for yy, xx in zip(*np.nonzero(~a)):
            best = None
            for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                y2, x2 = yy + dy, xx + dx
                if 0 <= y2 < height and 0 <= x2 < width and a[y2, x2]:
                    if best is None or depth[y2, x2] < depth[best]:
                        best = (y2, x2)
            if best is not None:
                m = materials[mat_names[matid[best]]]
                res[yy, xx, :3] = m.tones[3]
                res[yy, xx, 3] = 255
        out = res
    return out


def project(pt, anchor, mpp=METERS_PER_PX):
    pt = np.asarray(pt, float)
    return (pt @ RIGHT / mpp + anchor[0], anchor[1] - pt @ UP / mpp)
