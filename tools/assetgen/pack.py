"""Blender çıktılarını oyun atlaslarına paketler.

Adımlar: alfa kırpma → renk düzenleme (kontrast/doygunluk) → keskinleştirme → aktörlere ince kontur →
gruplara göre 2048x2048 sayfalara yerleştirme (raf algoritması) → WebP + manifest.json.

Manifest:
  pages:   [dosya, ...]
  groups:  {grup: [sayfa indeksleri]}   (Kotlin grupları tembel yükler)
  sprites: {anahtar: [sayfa, x, y, w, h, ax, ay]}  ax/ay = çapa noktasının kırpılmış görüntüdeki konumu
  attach:  {"hero/kaan": {"walk_3_5": {"rhand": [dx, dy], ...}}}  çapaya göre (px)
  anims:   {"hero": {anim: [kare, fps, döngü]}, "zombie": {...}}

Kullanım: /opt/bvenv/bin/python tools/assetgen/pack.py [render_dizini] [çıktı_dizini]
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

SRC = sys.argv[1] if len(sys.argv) > 1 else "/home/user/zx_render"
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "..", "..", "android", "app", "src", "main",
                                                         "assets", "v2")
SRC3 = os.environ.get("ZX_WORK3", "/home/user/zx_render3")
K3 = 0.0107 / 0.016  # yüksek yoğunluklu aktör renderları → dünya pikseli
PAGE = 2048
PAD = 2

HERO_ANIMS = {"idle": (6, 6, 1), "walk": (8, 10, 1), "run": (8, 14, 1), "punch": (8, 14, 1), "melee": (6, 12, 1),
              "pistol_idle": (2, 3, 1), "pistol_walk": (8, 10, 1), "rifle_idle": (2, 3, 1), "rifle_walk": (8, 10, 1),
              "cast": (6, 12, 0), "hurt": (2, 10, 0), "death": (6, 9, 0), "ride": (1, 1, 1)}
ZOMBIE_ANIMS = {"idle": (4, 4, 1), "walk": (8, 7, 1), "attack": (6, 10, 1), "hurt": (2, 10, 0), "death": (6, 8, 0)}
HEROES = ["kaan", "nova", "rady", "elif"]
ZOMBIES = ["walker", "runner", "dog", "toxic", "dark", "tank", "mystery", "titan"]


def grade(im, actor=False):
    """Hafif kontrast + doygunluk + keskinlik; aktörlere ince koyu kontur."""
    rgb = im.convert("RGB")
    a = im.getchannel("A")
    rgb = ImageEnhance.Contrast(rgb).enhance(1.07)
    rgb = ImageEnhance.Color(rgb).enhance(1.1)
    rgb = rgb.filter(ImageFilter.UnsharpMask(radius=1.2, percent=70, threshold=2))
    out = Image.merge("RGBA", (*rgb.split(), a))
    if actor:
        arr = np.array(a, float) / 255
        solid = (arr > 0.6).astype(np.uint8) * 255
        dil = Image.fromarray(solid).filter(ImageFilter.MaxFilter(3))
        ring = np.clip(np.array(dil, float) / 255 - arr, 0, 1) * 0.55
        ol = np.zeros((im.height, im.width, 4), np.uint8)
        ol[..., 0], ol[..., 1], ol[..., 2] = 18, 14, 12
        ol[..., 3] = (ring * 255).astype(np.uint8)
        out = Image.alpha_composite(Image.fromarray(ol), out)
    return out


def trim(im):
    a = np.array(im.getchannel("A"))
    ys, xs = np.nonzero(a > 3)
    if len(xs) == 0:
        return im.crop((0, 0, 1, 1)), 0, 0
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    x0, y0 = max(0, x0 - 1), max(0, y0 - 1)
    x1, y1 = min(im.width, x1 + 1), min(im.height, y1 + 1)
    return im.crop((x0, y0, x1, y1)), x0, y0


class Packer:
    def __init__(self):
        self.pages = []  # [Image]
        self.groups = {}
        self.sprites = {}

    def pack_group(self, name, items, k=1.0):
        """items: [(anahtar, Image, ax, ay)] — büyükten küçüğe raf yerleşimi."""
        items = sorted(items, key=lambda t: (-t[1].height, -t[1].width))
        page_ids = []
        page = None
        x = y = shelf = 0
        for key, im, ax, ay in items:
            w, h = im.width + PAD, im.height + PAD
            if w > PAGE or h > PAGE:
                im = im.resize((min(im.width, PAGE - PAD), min(im.height, PAGE - PAD)))
                w, h = im.width + PAD, im.height + PAD
            if page is not None and x + w > PAGE:
                x = 0
                y += shelf
                shelf = 0
            if page is None or y + h > PAGE:
                page = Image.new("RGBA", (PAGE, PAGE), (0, 0, 0, 0))
                self.pages.append(page)
                page_ids.append(len(self.pages) - 1)
                x = y = shelf = 0
            page.paste(im, (x, y))
            self.sprites[key] = [len(self.pages) - 1, int(x), int(y), int(im.width), int(im.height), round(float(ax), 1), round(float(ay), 1)]
            if k != 1.0:
                self.sprites[key].append(round(k, 5))
            x += w
            shelf = max(shelf, h)
        self.groups[name] = page_ids

    def save(self, out):
        os.makedirs(out, exist_ok=True)
        for f in os.listdir(out):
            if f.startswith("page_"):
                os.remove(os.path.join(out, f))
        names = []
        for i, p in enumerate(self.pages):
            # son sayfayı gereksiz boşluktan kırp (yükseklik)
            bb = p.getbbox()
            if bb:
                h = min(PAGE, ((bb[3] + 63) // 64) * 64)
                w = min(PAGE, ((bb[2] + 63) // 64) * 64)
                p = p.crop((0, 0, w, h))
            fn = f"page_{i:02d}.webp"
            p.save(os.path.join(out, fn), "WEBP", quality=92, method=6)
            names.append(fn)
        return names


def load(path, anchor, actor=False, scale=1.0):
    im = Image.open(path).convert("RGBA")
    if scale != 1.0:
        im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.LANCZOS)
        anchor = (anchor[0] * scale, anchor[1] * scale)
    im = grade(im, actor)
    t, x0, y0 = trim(im)
    return t, anchor[0] - x0, anchor[1] - y0


def jload(p):
    try:
        return json.load(open(p))
    except Exception:
        return None


def collect_static(dirname, prefix, out, actor=False, root=None):
    d = os.path.join(root or SRC, dirname)
    if not os.path.isdir(d):
        return
    for f in sorted(os.listdir(d)):
        if not f.endswith(".png"):
            continue
        meta = jload(os.path.join(d, f[:-4] + ".json"))
        if not meta:
            continue
        im, ax, ay = load(os.path.join(d, f), meta["anchor"], actor)
        out.append((f"{prefix}/{f[:-4]}", im, ax, ay))


LIFT_PX = 1.0 / 0.016 * 0.8191520  # render_sdf lift=1 m → çapa zeminde; model kökeni bu kadar yukarıda


def collect_dirs(dirname, prefix, out, actor=False, lifted=False, root=None):
    """dirname/<isim>/<yön>.png + .json  (lifted: ekipman/silah — çapa model kökenine taşınır)"""
    d = os.path.join(root or SRC, dirname)
    if not os.path.isdir(d):
        return
    for name in sorted(os.listdir(d)):
        sub = os.path.join(d, name)
        if not os.path.isdir(sub):
            continue
        for k in range(8):
            p = os.path.join(sub, f"{k}.png")
            meta = jload(p[:-4] + ".json")
            if meta and os.path.exists(p):
                a = meta["anchor"]
                if lifted:
                    a = (a[0], a[1] - LIFT_PX)
                im, ax, ay = load(p, a, actor)
                out.append((f"{prefix}/{name}/{k}", im, ax, ay))


def collect_actor(group_dir, kind, prefix, anims, attach_out, items, fixed_anchor=None, arms=False, scale=1.0, root=None, k=1.0):
    d = os.path.join(root or SRC, group_dir, kind)
    files = set(os.listdir(d)) if os.path.isdir(d) else set()
    if not os.path.isdir(d):
        return 0
    att = jload(os.path.join(d, "attach.json")) or {}
    rel = {}
    n = 0
    for anim, (frames, fps, loop) in anims.items():
        for dr in range(8):
            for f in range(frames):
                key = f"{anim}_{dr}_{f}"
                p = os.path.join(d, key + ".png")
                if not os.path.exists(p):
                    continue
                a = att.get(key, {}).get("anchor") or fixed_anchor
                if a is None:
                    continue
                im, ax, ay = load(p, a, True, scale)
                items.append((f"{prefix}/{key}", im, ax, ay))
                n += 1
                # silahlı varyantlar: <key>@<silah>.png
                for fn in files:
                    if fn.startswith(key + "@") and fn.endswith(".png"):
                        imw, axw, ayw = load(os.path.join(d, fn), a, True, scale)
                        items.append((f"{prefix}/{fn[:-4]}", imw, axw, ayw))
                if arms and os.path.exists(os.path.join(d, key + "_arms.png")):
                    im2, ax2, ay2 = load(os.path.join(d, key + "_arms.png"), a, False, scale)
                    items.append((f"{prefix}/{key}_a", im2, ax2, ay2))
                if key in att:
                    rel[key] = {kk: [round(float(v[0] - a[0]) * scale * k, 1), round(float(v[1] - a[1]) * scale * k, 1)]
                                for kk, v in att[key].items() if kk != "anchor"}
    if rel:
        attach_out[prefix] = rel
    return n


def has3(*p):
    return os.path.isdir(os.path.join(SRC3, *p))


def main():
    P = Packer()
    attach = {}
    fxmeta = {}
    world = []
    collect_static("nature", "s", world)
    collect_static("buildings", "s", world)
    P.pack_group("world", world)
    print("world", len(world))
    # eşya simgeleri: yeni (yüksek çözünürlük) varsa onu kullan
    items = []
    collect_static("items", "i", items, root=SRC3 if has3("items") else SRC)
    P.pack_group("items", items)
    gear = []
    if has3("equip"):
        collect_dirs("equip", "e", gear, root=SRC3)
        collect_dirs("weapons", "w", gear, root=SRC3)
        P.pack_group("gear", gear, K3)
    else:
        collect_dirs("equip", "e", gear, lifted=True)
        collect_dirs("weapons", "w", gear, lifted=True)
        P.pack_group("gear", gear)
    veh = []
    collect_dirs("vehicles", "v", veh, actor=True)
    P.pack_group("vehicles", veh)
    r3 = SRC3 if has3("npcs") else None
    kk = K3 if r3 else 1.0
    npc = []
    collect_actor("npcs", "soldier", "n/soldier", {"rifle_idle": (2, 3, 1)}, attach, npc, root=r3, k=kk)
    collect_actor("npcs", "merchant", "n/merchant", {"idle": (6, 5, 1)}, attach, npc, root=r3, k=kk)
    P.pack_group("npcs", npc, kk)
    for h in HEROES:
        it = []
        r3 = SRC3 if has3("heroes", h) else None
        kk = K3 if r3 else 1.0
        n = collect_actor("heroes", h, f"h/{h}", HERO_ANIMS, attach, it, arms=r3 is None, root=r3, k=kk)
        if it:
            P.pack_group(f"hero_{h}", it, kk)
        print("hero", h, n)
        lob = []
        d = os.path.join(SRC3 if has3("lobby", h) else SRC, "lobby", h)
        if os.path.isdir(d):
            anc = (180, 400) if d.startswith(SRC3) else (180, 392)
            for f in sorted(os.listdir(d)):
                if f.endswith(".png"):
                    im, ax, ay = load(os.path.join(d, f), anc, True)
                    lob.append((f"l/{h}/{f[:-4]}", im, ax, ay))
        if lob:
            P.pack_group(f"lobby_{h}", lob)
    for z in ZOMBIES:
        it = []
        r3 = SRC3 if has3("zombies", z) else None
        kk = K3 if r3 else 1.0
        fixed = (100, 136) if z == "dog" and r3 is None else None
        n = collect_actor("zombies", z, f"z/{z}", ZOMBIE_ANIMS, attach, it, fixed_anchor=fixed, root=r3, k=kk)
        if it:
            P.pack_group(f"zombie_{z}", it, kk)
        print("zombie", z, n)
    # efektler
    fx = []
    fd = os.path.join(SRC3, "fx")
    if os.path.isdir(fd):
        for name in sorted(os.listdir(fd)):
            meta = jload(os.path.join(fd, name, "meta.json"))
            if not meta:
                continue
            fxmeta[name] = meta
            for i in range(meta["frames"]):
                im = Image.open(os.path.join(fd, name, f"{i}.png")).convert("RGBA")
                fx.append((f"fx/{name}/{i}", im, im.width / 2, im.height / 2))
        P.pack_group("fx", fx)
    pages = P.save(OUT)
    manifest = {"pages": pages, "groups": P.groups, "sprites": P.sprites, "attach": attach, "fx": fxmeta,
                "anims": {"hero": HERO_ANIMS, "zombie": ZOMBIE_ANIMS}}
    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w"), separators=(",", ":"))
    gdir = os.path.join(SRC, "ground")
    if os.path.isdir(gdir):
        gout = os.path.join(OUT, "ground")
        os.makedirs(gout, exist_ok=True)
        for f in os.listdir(gdir):
            if f.endswith(".png"):
                Image.open(os.path.join(gdir, f)).convert("RGB").save(os.path.join(gout, f[:-4] + ".webp"), "WEBP", quality=90)
    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in pages)
    print(f"{len(pages)} sayfa, {total / 1e6:.1f} MB, {len(P.sprites)} sprite")


if __name__ == "__main__":
    main()
