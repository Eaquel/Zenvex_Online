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
              "cast": (6, 12, 0), "hurt": (2, 10, 0), "death": (6, 9, 0), "ride": (4, 8, 1),
              "roll": (6, 18, 0), "knockdown": (5, 10, 0), "getup": (5, 10, 0)}
PIX = os.environ.get("ZX_PIX", "/home/user/zx_pix")
ZOMBIE_ANIMS = {"idle": (4, 4, 1), "walk": (8, 7, 1), "attack": (6, 10, 1), "hurt": (2, 10, 0), "death": (6, 8, 0)}
DROPPED_ITEMS = ("w_bat", "w_baton", "w_glock", "w_ak47", "w_m4", "w_sniper", "w_rpg", "ammo_", "helm_", "jacket_", "boots_",
                 "bag_", "cloak", "powder", "screw")  # altın ve elmas simgeleri ödül ekranlarında kalır
KEEP_GEAR = False  # eski 3D silah/ekipman katmanları; V6'da silahlar (bıçak, katana) yeni piksel görsellerle gelecek
HEROES = ["kaan"]  # V4: tek kahraman
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
    ys, xs = np.nonzero(a > 12)
    if len(xs) == 0:
        return im.crop((0, 0, 1, 1)), 0, 0
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    x0, y0 = max(0, x0 - 1), max(0, y0 - 1)
    x1, y1 = min(im.width, x1 + 1), min(im.height, y1 + 1)
    return im.crop((x0, y0, x1, y1)), x0, y0


class Packer:
    def __init__(self):
        self.pages = []  # [Image]
        self.pixel_pages = set()  # piksel sanat sayfaları: kayıpsız paletli PNG-8
        self.groups = {}
        self.sprites = {}

    def pack_group(self, name, items, k=1.0, pixel=False):
        """items: [(anahtar, Image, ax, ay[, k])] — büyükten küçüğe raf yerleşimi; k öğe başına ölçek (yoksa grup k'si)."""
        items = sorted(items, key=lambda t: (-t[1].height, -t[1].width))
        page_ids = []
        page = None
        x = y = shelf = 0
        gk = k
        for key, im, ax, ay, *kk in items:
            k = kk[0] if kk else gk
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
                if pixel:
                    self.pixel_pages.add(len(self.pages) - 1)
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
        jobs = [(i, p, out, i in self.pixel_pages) for i, p in enumerate(self.pages)]
        from multiprocessing import Pool
        with Pool(os.cpu_count() or 2) as pool:
            names = pool.map(_save_page, jobs)
        return names


def _save_page(job):
    i, p, out, pixel = job
    # son sayfayı gereksiz boşluktan kırp
    bb = p.getbbox()
    if bb:
        h = min(PAGE, ((bb[3] + 63) // 64) * 64)
        w = min(PAGE, ((bb[2] + 63) // 64) * 64)
        p = p.crop((0, 0, w, h))
    if pixel:
        # piksel sanat: 256 renkli palet + kayıpsız PNG (WebP'den küçük, kenarlarda leke yok)
        fn = f"page_{i:02d}.png"
        p.quantize(colors=256, method=Image.FASTOCTREE).save(os.path.join(out, fn), "PNG", optimize=True)
        return fn
    fn = f"page_{i:02d}.webp"
    p.save(os.path.join(out, fn), "WEBP", quality=90, method=4)
    return fn


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
                variants = [fn for fn in files if fn.startswith(key + "@") and fn.endswith(".png")]
                if not os.path.exists(p) and not variants:
                    continue
                a = att.get(key, {}).get("anchor") or fixed_anchor
                if a is None:
                    continue
                if os.path.exists(p):
                    im, ax, ay = load(p, a, True, scale)
                    items.append((f"{prefix}/{key}", im, ax, ay))
                n += 1
                # silahlı varyantlar: <key>@<silah>.png
                for fn in variants:
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


def pixel_k(sub):
    """Piksel aktörün yerel piksel → dünya ölçeği (meta.json mpp)."""
    return (jload(os.path.join(PIX, sub, "meta.json")) or {}).get("mpp", 0.0107) / 0.016


def collect_pixel_hero(prefix, items, attach_out=None, sub="hero"):
    """Piksel aktör (kahraman, köpekler): <anim>_<yön>_<kare>.png (yön 0-4), 5-7 aynadan. Renk/kontur dokunulmaz."""
    d = os.path.join(PIX, sub)
    meta = jload(os.path.join(d, "meta.json")) or {}
    ax0, ay0 = meta.get("anchor", [150, 262])
    kp = meta.get("mpp", 0.0107) / 0.016  # yerel piksel → dünya pikseli
    att = jload(os.path.join(d, "attach.json")) or {}
    rel = {}
    n = 0
    # V9: sırt görünümü (yukarı yürürken) <sub>_back dizininde, yön 4; aynı çapa/ölçek
    back = os.path.join(PIX, sub + "_back")
    files = [(d, f) for f in sorted(os.listdir(d))]
    if os.path.isdir(back):
        files += [(back, f) for f in sorted(os.listdir(back))]
        att.update(jload(os.path.join(back, "attach.json")) or {})
    for fd, f in files:
        if not f.endswith(".png"):
            continue
        anim, dr, fr = f[:-4].rsplit("_", 2)
        if dr not in ("0", "4"):
            continue  # ön görünüm tek (sağa bakan yönler aynalanır) + sırt görünümü
        im = Image.open(os.path.join(fd, f)).convert("RGBA")
        variants = [(int(dr), im, ax0)]
        for dd, img, ax in variants:
            t, x0, y0 = trim(img)
            items.append((f"{prefix}/{anim}_{dd}_{fr}", t, ax - x0, ay0 - y0))
            pts = att.get(f"{anim}_{dr}_{fr}")
            if pts:
                mir = dd != int(dr)
                rel[f"{anim}_{dd}_{fr}"] = {k: [round(((ax0 - v[0]) if mir else (v[0] - ax0)) * kp, 1), round((v[1] - ay0) * kp, 1)]
                                           for k, v in pts.items()}
            n += 1
    if attach_out is not None and rel:
        attach_out[prefix] = rel
    return n


V6 = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "art", "v6")
PX_PER_M = 62.5
# kamp nesneleri: (genişlik m) — piksel görsel bu dünya genişliğine ölçeklenir, çapa alt orta
V6_PROPS = {"crate": ("crate", 0.9), "crate_mil": ("milbox", 1.0), "campfire": ("campfire", 1.3), "workbench": ("workbench", 1.7),
            "quest_board": ("quest_board", 1.5), "med_station": ("med_station", 0.95), "kennel": ("kennel", 1.3),
            "fuel_pump": ("fuel_pump", 0.75), "charger": ("charger", 0.8), "mailbox": ("mailbox", 0.55),
            "anvil": ("anvil", 0.8), "barricade": ("barricade", 2.0),
            "portal": ("portal", 2.2)}  # V8 kişisel üs dönüş kapısı
# NPC'ler: (görsel, boy m)
# V7 piksel yer işaretleri: (görsel, genişlik m, çapa x/y oranı)
V7 = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "art", "v7")
V7_PROPS = {"med_tent": ("med_tent", 3.4, (0.5, 0.93)), "mystery_tree": ("mystery_tree", 6.0, (0.53, 0.97)),
            "gas_station": ("gas_station", 4.6, (0.56, 0.92))}
V6_NPCS = {"merchant": 1.85, "soldier": 1.75, "quest": 1.72, "doctor": 1.75, "mechanic": 1.78, "trainer": 1.8}
# V8 kullanıcı görselleri (tools/pixel/v8_objects.py → art/v8): (dosya, genişlik m, çapa x/y oranı)
V8 = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "art", "v8")
V8_PROPS = {"burnt_tree": ("burnt_tree", 3.4, (0.52, 0.97)), "burnt_stump": ("burnt_stump", 1.0, (0.5, 0.88)),
            "desert_wreck": ("desert_wreck", 5.2, (0.5, 0.86)), "swamp_shack": ("swamp_shack", 5.0, (0.5, 0.96)),
            "harbor_containers": ("harbor_containers", 7.2, (0.5, 0.9)), "hangar": ("hangar", 9.0, (0.5, 0.95)),
            "bunker": ("bunker", 6.2, (0.52, 0.93)), "watchtower": ("watchtower", 3.6, (0.5, 0.985)),
            "helipad": ("helipad", 6.6, (0.5, 0.5)), "supply_crate": ("supply_crate", 1.7, (0.5, 0.9)),
            "bed": ("bed", 2.0, (0.5, 0.9)), "stove": ("stove", 1.4, (0.45, 0.95)), "fence_wood": ("fence_wood", 2.2, (0.5, 0.95)),
            "base_gate": ("gate", 2.4, (0.5, 0.96)), "weapon_rack": ("weapon_rack", 2.0, (0.5, 0.95)),
            "rain_barrel": ("rain_barrel", 0.8, (0.5, 0.95))}
# NPC'ler: (dosya, boy m) — asker kareleri aynı ölçekte (ön/arka/nişan/düşüş)
V8_NPCS = {"soldier_aim": ("soldier_front", 1.8), "soldier_idle": ("soldier_side", 1.8), "soldier_back": ("soldier_back", 1.8),
           "sniper_aim": ("sniper_front", 1.8), "sniper_idle": ("sniper_side", 1.8), "sniper_back": ("sniper_back", 1.8),
           "wizard": ("wizard", 1.85)}
# kişisel üs sandıkları: kapalı / açık aynı ölçekte (genişlik m kapalıdan)
V8_CHESTS = {"chest_wood": 0.9, "chest_iron": 1.05, "chest_mil": 1.25}
# etkinlik ödülleri: ekipman simgesinden bölge renginde (yüzük / kolye / kask)
EV_TINT = {"blood": (215, 50, 45), "titan": (230, 140, 60), "base": (80, 150, 230), "scrap": (130, 190, 70),
           "pack": (170, 110, 220), "breath": (90, 210, 220)}
PANTS_TINT = {"pants_basic": None, "pants_police": (70, 100, 160), "pants_military": (110, 120, 70), "pants_titan": (210, 130, 60)}


def v8_img(*p):
    return Image.open(os.path.join(V8, *p)).convert("RGBA")


def tinted(im, rgb, k=0.55):
    """Simgeyi renge çek: parlaklık korunur, ton verilen renge kayar (alfa aynen)."""
    a = np.asarray(im).astype(float)
    lum = a[..., :3] @ np.array([0.3, 0.59, 0.11]) / 255.0
    col = np.array(rgb, float)[None, None, :] * (0.35 + lum[..., None] * 1.1)
    out = a.copy()
    out[..., :3] = np.clip(a[..., :3] * (1 - k) + col * k, 0, 255)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def v6_img(*p):
    return Image.open(os.path.join(V6, *p)).convert("RGBA")


def has3(*p):
    return os.path.isdir(os.path.join(SRC3, *p))


def main():
    P = Packer()
    attach = {}
    fxmeta = {}
    world = []
    collect_static("nature", "s", world)
    collect_static("buildings", "s", world)
    # V6 piksel kamp nesneleri eskilerin yerine (ayrı paletli grup)
    props = []
    for key, (fn, w_m) in V6_PROPS.items():
        f = os.path.join(V6, "objects", fn + ".png")
        if os.path.exists(f):
            im = v6_img("objects", fn + ".png")
            props.append((f"s/{key}", im, im.width / 2, im.height - 1, w_m * PX_PER_M / im.width))
    for key, (fn, w_m, (fx_, fy_)) in V7_PROPS.items():
        f = os.path.join(V7, "props", fn + ".png")
        if os.path.exists(f):
            im = Image.open(f).convert("RGBA")
            props.append((f"s/{key}", im, im.width * fx_, im.height * fy_, w_m * PX_PER_M / im.width))
    for key, (fn, w_m, (fx_, fy_)) in V8_PROPS.items():
        f = os.path.join(V8, "props", fn + ".png")
        if os.path.exists(f):
            im = v8_img("props", fn + ".png")
            props.append((f"s/{key}", im, im.width * fx_, im.height * fy_, w_m * PX_PER_M / im.width))
    for key, w_m in V8_CHESTS.items():
        f = os.path.join(V8, "props", key + ".png")
        if os.path.exists(f):
            im = v8_img("props", key + ".png")
            k = w_m * PX_PER_M / im.width
            props.append((f"s/{key}", im, im.width / 2, im.height * 0.95, k))
            fo = os.path.join(V8, "props", key + "_open.png")
            if os.path.exists(fo):
                io = v8_img("props", key + "_open.png")
                props.append((f"s/{key}_open", io, io.width / 2, io.height * 0.95, k))
    keys = {t[0] for t in props}
    world = [t for t in world if t[0] not in keys]
    P.pack_group("world", world)
    print("world", len(world))
    P.pack_group("props", props, pixel=True)
    print("props", len(props))
    # V6 NPC'ler (tek görünüm; oyun nefes/sallanma hareketini çizerken verir)
    npcs6 = []
    for name, h_m in V6_NPCS.items():
        f = os.path.join(V6, "npc", name + ".png")
        if os.path.exists(f):
            im = v6_img("npc", name + ".png")
            npcs6.append((f"npc/{name}", im, im.width / 2, im.height - 1, h_m * PX_PER_M / im.height))
    # V8 askerler (ön nişan / bekleme / arka / düşüş), büyücü; silahlar ve helikopter gövdesi, araçlar
    sk = None
    for key, (fn, h_m) in V8_NPCS.items():
        f = os.path.join(V8, "npc", fn + ".png")
        if os.path.exists(f):
            im = v8_img("npc", fn + ".png")
            k = h_m * PX_PER_M / im.height
            if key == "soldier_aim":
                sk = k
            npcs6.append((f"npc/{key}", im, im.width / 2, im.height - 1, k))
    if sk and os.path.exists(os.path.join(V8, "npc", "soldier_fall.png")):
        im = v8_img("npc", "soldier_fall.png")
        npcs6.append(("npc/soldier_fall", im, im.width * 0.45, im.height * 0.9, sk))
    for key, (fn, w_m, (fx_, fy_)) in {"x/gun_m4": ("npc/gun_m4", 0.9, (0.3, 0.5)), "x/gun_sniper": ("npc/gun_sniper", 1.25, (0.3, 0.5)),
                                       "x/heli": ("props/heli", 7.5, (0.48, 0.62)), "x/moto": ("veh/moto", 2.1, (0.5, 0.92)),
                                       "x/car": ("veh/car", 4.2, (0.5, 0.92)), "x/wheel": ("veh/wheel", 0.7, (0.5, 0.5))}.items():
        f = os.path.join(V8, fn + ".png")
        if os.path.exists(f):
            im = Image.open(f).convert("RGBA")
            npcs6.append((key, im, im.width * fx_, im.height * fy_, w_m * PX_PER_M / im.width))
    P.pack_group("npc6", npcs6, pixel=True)
    # eşya simgeleri: yeni (yüksek çözünürlük) varsa onu kullan
    items = []
    collect_static("items", "i", items, root=SRC3 if has3("items") else SRC)
    # oyundan kaldırılan eşyalar (ateşli silahlar, mermiler, setler, düşen para) atlasa girmez
    items = [t for t in items if not t[0].split("/", 1)[1].startswith(DROPPED_ITEMS)]
    # V6 piksel simgeler eskilerin yerine
    d6 = os.path.join(V6, "items")
    if os.path.isdir(d6):
        new = {}
        for f in sorted(os.listdir(d6)):
            if f.endswith(".png"):
                im = v6_img("items", f)
                new[f"i/{f[:-4]}"] = (f"i/{f[:-4]}", im, im.width / 2, im.height - 1)
        items = [t for t in items if t[0] not in new] + list(new.values())
    # V8: pantolon ve etkinlik ödülü simgeleri ekipman setinden (renklendirilmiş)
    du = os.path.join(V8, "ui")
    if os.path.isdir(du):
        extra = {}
        pants = Image.open(os.path.join(du, "slot_pants.png")).convert("RGBA")
        for key, rgb in PANTS_TINT.items():
            im = pants if rgb is None else tinted(pants, rgb)
            extra[f"i/{key}"] = (f"i/{key}", im, im.width / 2, im.height - 1)
        for ev, rgb in EV_TINT.items():
            for part, fn in (("ring", "slot_ring"), ("neck", "slot_neck"), ("helm", "slot_helmet")):
                im = tinted(Image.open(os.path.join(du, fn + ".png")).convert("RGBA"), rgb, 0.6)
                extra[f"i/ev_{ev}_{part}"] = (f"i/ev_{ev}_{part}", im, im.width / 2, im.height - 1)
        items = [t for t in items if t[0] not in extra] + list(extra.values())
    P.pack_group("items", items, pixel=os.path.isdir(d6))
    gear = []
    if not KEEP_GEAR:
        print("gear: atlandı (silah/set parçaları kullanılmıyor)")
    if KEEP_GEAR and has3("equip"):
        collect_dirs("equip", "e", gear, root=SRC3)
        collect_dirs("weapons", "w", gear, root=SRC3)
        P.pack_group("gear", gear, K3)
    elif KEEP_GEAR:
        collect_dirs("equip", "e", gear, lifted=True)
        collect_dirs("weapons", "w", gear, lifted=True)
        P.pack_group("gear", gear)
    veh = []
    collect_dirs("vehicles", "v", veh, actor=True)
    veh = [t for t in veh if not t[0].startswith("v/bike/")]  # bisiklet oyundan kaldırıldı
    P.pack_group("vehicles", veh)
    r3 = SRC3 if has3("npcs") else None
    old_npcs = not os.path.isdir(os.path.join(V6, "npc"))  # V6 piksel NPC'ler varsa eski 3D kareler atlasa girmez
    kk = K3 if r3 else 1.0
    npc = []
    if old_npcs:
        collect_actor("npcs", "soldier", "n/soldier", {"rifle_idle": (2, 3, 1)}, attach, npc, root=r3, k=kk)
        collect_actor("npcs", "merchant", "n/merchant", {"idle": (6, 5, 1)}, attach, npc, root=r3, k=kk)
        P.pack_group("npcs", npc, kk)
    for h in HEROES:
        it = []
        r3 = SRC3 if has3("heroes", h) else None
        kk = K3 if r3 else 1.0
        if h == "kaan" and os.path.isdir(os.path.join(PIX, "hero")):
            kk = pixel_k("hero")
            n = collect_pixel_hero(f"h/{h}", it, attach)
        else:
            n = collect_actor("heroes", h, f"h/{h}", HERO_ANIMS, attach, it, arms=r3 is None, root=r3, k=kk)
        if it:
            P.pack_group(f"hero_{h}", it, kk, pixel=h == "kaan" and os.path.isdir(os.path.join(PIX, "hero")))
        print("hero", h, n)
        lob = []
        d = os.path.join(SRC3 if has3("lobby", h) else SRC, "lobby", h)
        if h == "kaan" and os.path.isdir(os.path.join(PIX, "hero")):
            # lobi: piksel idle kareleri (tek yön; arayüz sağa bakışı aynalar)
            for key, im, ax, ay in it:
                a = key.split("/")[-1]
                if a.startswith("idle_") and "@" not in a:  # silahlı kareler lobide yok
                    _, dd, ff = a.split("_")
                    lob.append((f"l/{h}/{dd}_{ff}", im, ax, ay))
            d = ""
        if d and os.path.isdir(d):
            anc = (180, 400) if d.startswith(SRC3) else (180, 392)
            for f in sorted(os.listdir(d)):
                if f.endswith(".png"):
                    im, ax, ay = load(os.path.join(d, f), anc, True)
                    lob.append((f"l/{h}/{f[:-4]}", im, ax, ay))
        if lob:
            P.pack_group(f"lobby_{h}", lob, pixel=not d)
    for z in ZOMBIES:
        it = []
        r3 = SRC3 if has3("zombies", z) else None
        kk = K3 if r3 else 1.0
        fixed = (100, 136) if z == "dog" and r3 is None else None
        px = False
        if z == "dog" and os.path.isdir(os.path.join(PIX, "zdog")):
            kk = pixel_k("zdog")
            n = collect_pixel_hero("z/dog", it, None, sub="zdog")
            px = True
        elif os.path.isdir(os.path.join(PIX, f"z_{z}")):
            # V7: tek piksel görselden örgü iskeletli (tools/pixel/zombie_rig.py) kareler; eski 3D kareler atlasa girmez
            kk = pixel_k(f"z_{z}")
            n = collect_pixel_hero(f"z/{z}", it, None, sub=f"z_{z}")
            px = True
        else:
            n = collect_actor("zombies", z, f"z/{z}", ZOMBIE_ANIMS, attach, it, fixed_anchor=fixed, root=r3, k=kk)
        if it:
            P.pack_group(f"zombie_{z}", it, kk, pixel=px)
        print("zombie", z, n)
    # evcil köpek: seviye görünümleri (1-4, 5-9, 10)
    for sub in ("dog0", "dog5", "dog10"):
        if os.path.isdir(os.path.join(PIX, sub)):
            it = []
            n = collect_pixel_hero(f"p/{sub}", it, None, sub=sub)
            P.pack_group(f"pet_{sub}", it, pixel_k(sub), pixel=True)
            print("pet", sub, n)
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
    # V7 piksel efektler (tools/pixel/fx_sheet.py): aynı adlı eskilerin yerine geçer
    fx7 = []
    d7 = os.path.join(V7, "fx")
    if os.path.isdir(d7):
        for name in sorted(os.listdir(d7)):
            meta = jload(os.path.join(d7, name, "meta.json"))
            if not meta:
                continue
            meta["pixel"] = True
            fxmeta[name] = meta
            ax, ay = meta.get("anchor", (0.5, 0.5))
            for i in range(meta["frames"]):
                im = Image.open(os.path.join(d7, name, f"{i}.png")).convert("RGBA")
                fx7.append((f"fx/{name}/{i}", im, im.width * ax, im.height * ay))
        new = {t[0].split("/")[1] for t in fx7}
        fx = [t for t in fx if t[0].split("/")[1] not in new]
        P.pack_group("fx7", fx7, pixel=True)
    if fx:
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
