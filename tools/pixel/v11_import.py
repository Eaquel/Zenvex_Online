"""V11 görsel paketini (V11_Gorseller_Etkinlik_Haric) oyuna alır: yeşil/siyah zemin silinir, gerçek piksel ızgarasına
indirilir, eski görselle aynı dünya boyuna ölçeklenir ve atlasa yamanır; arayüz parçaları assets/ui'ye yazılır.

  B ağaç/çalı/kaya   → s/oak0 … s/rock_small2 (aynı anahtar, aynı dünya boyu)
  C alet/araç parçası → i/t_axe_stone … i/veh_car
  D araç 8 yön        → x8/car/0-7, x8/moto/0-7 (yapay zekâ yönleri karıştırdığı için kareler elle eşlendi, eksikler aynalı)
  E cephe/karargâh    → s/front_*, s/hq_*
  F eğitim üssü       → s/air_bomb, s/bomber_shadow, s/train_*; ui/tut_hand.png
  G dünya sınırı      → s/quarantine_*, s/border_*
  H arayüz            → ui/tab_*.png, ui/bg_garage.webp, ui/card_order.png, ui/commander_portrait.png, ui/event_frame*.png,
                         ui/mail_badge.png, ui/ping_0..3.png
  I efektler          → fx/<ad>/<kare> (+ manifest fx tablosu)

python3 tools/pixel/v11_import.py <paket dizini>
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "assetgen"))
from ai_clean import pixel_size  # noqa: E402
from atlas_patch import Atlas  # noqa: E402
from cutout import bbox, crop, cells, flip, key, parts, pixelize, to_img  # noqa: E402

UI = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "assets", "ui")


def grid_of(a, lo=3.0, hi=12.0, fallback=6.0):
    """piksel bloğu boyu (zemin hariç bölgede ölçülür)"""
    c = crop(a)
    s = pixel_size(c[..., :3].astype(float), lo, hi)
    if s <= lo + 0.05 or s >= hi - 0.05:
        s = fallback
    return s


def sprite(a, s=None, hard=True):
    c = crop(a)
    s = s or grid_of(c)
    return crop(pixelize(c, s, hard))


def tree_anchor(p):
    """gövde dibi: en alttaki %8'lik şeritte opak piksellerin ortası"""
    h = p.shape[0]
    al = p[..., 3] > 0
    band = al[int(h * 0.92):]
    xs = np.nonzero(band.any(0))[0]
    ax = (xs.min() + xs.max() + 1) / 2 if len(xs) else p.shape[1] / 2
    return ax, h - 1.0


def main(src):
    A = Atlas()
    sp = A.m["sprites"]

    def old_world(key_, axis=1):
        v = sp[key_]
        return v[3 + axis] * (v[7] if len(v) > 7 else 1.0)

    # ---------------------------------------------------------------- B: ağaçlar, çalılar, kayalar
    B = os.path.join(src, "B_Agaclar_Calilar_Kayalar")
    trees = {"oak0", "oak1", "oak2", "oak_autumn", "pine0", "pine1", "pine2", "birch0", "birch1", "dead0", "dead1"}
    world_add = []
    for n in sorted(trees | {"bush0", "bush1", "bush2", "bush3", "bush4", "rock_big0", "rock_big1", "rock_big2",
                             "rock_small0", "rock_small1", "rock_small2", "stump", "log"}):
        p = sprite(key(os.path.join(B, n + ".png")))
        h, w = p.shape[:2]
        # dünya boyu korunur (çarpışma şekli Rust'ta aynı); ağaçlar biraz daha iri
        wh = old_world("s/" + n) * (1.08 if n in trees else 1.0)
        k = wh / h
        if n in trees:
            ax, ay = tree_anchor(p)
        else:
            ax, ay = w / 2, h * 0.86
        world_add.append(("s/" + n, to_img(p), ax, ay, k))
        print(f"s/{n}: {w}x{h} k={k:.2f}")
    A.patch("world", add=world_add)

    # ---------------------------------------------------------------- E, F, G: sahne nesneleri (dünya pikseli boyları)
    props = []

    def prop(path, name, world_w=None, world_h=None, anchor=(0.5, 0.88), s=None):
        p = sprite(key(path), s)
        h, w = p.shape[:2]
        k = world_w / w if world_w else world_h / h
        props.append(("s/" + name, to_img(p), w * anchor[0], h * anchor[1], k))
        print(f"s/{name}: {w}x{h} k={k:.2f}")

    E = os.path.join(src, "E_Cephe_Hatti_ve_Karargah")
    prop(f"{E}/front_trench.png", "front_trench", world_w=300, anchor=(0.5, 0.7))
    prop(f"{E}/front_crater.png", "front_crater", world_w=250, anchor=(0.5, 0.5))
    prop(f"{E}/front_barbed.png", "front_barbed", world_w=230, anchor=(0.5, 0.8))
    prop(f"{E}/front_tank_wreck.png", "front_tank_wreck", world_w=360, anchor=(0.5, 0.8))
    prop(f"{E}/hq_command_tent.png", "hq_command_tent", world_w=330, anchor=(0.5, 0.86))
    prop(f"{E}/hq_radio_mast.png", "hq_radio_mast", world_h=420, anchor=(0.5, 0.95))
    prop(f"{E}/hq_flag.png", "hq_flag", world_h=270, anchor=(0.5, 0.94))
    prop(f"{E}/hq_sign.png", "hq_sign", world_h=170, anchor=(0.5, 0.96))
    F = os.path.join(src, "F_Ogretici_ve_Egitim_Ussu")
    prop(f"{F}/air_bomb.png", "air_bomb", world_h=120, anchor=(0.5, 1.0))
    prop(f"{F}/bomber_shadow.png", "bomber_shadow", world_w=520, anchor=(0.5, 0.5))
    prop(f"{F}/train_dummy.png", "train_dummy", world_h=135, anchor=(0.5, 0.96))
    prop(f"{F}/train_sign.png", "train_sign", world_w=170, anchor=(0.5, 0.95))
    prop(f"{F}/train_target.png", "train_target", world_w=210, anchor=(0.5, 0.5))
    G = os.path.join(src, "G_Dunya_Siniri_Susleri")
    prop(f"{G}/quarantine_wall.png", "quarantine_wall", world_w=4 * 64 + 8, anchor=(0.5, 0.8))
    prop(f"{G}/quarantine_post.png", "quarantine_post", world_h=150, anchor=(0.5, 0.92))
    prop(f"{G}/quarantine_sign.png", "quarantine_sign", world_h=130, anchor=(0.5, 0.96))
    prop(f"{G}/border_searchlight.png", "border_searchlight", world_h=140, anchor=(0.5, 0.94))
    prop(f"{G}/border_bones.png", "border_bones", world_w=150, anchor=(0.5, 0.6))
    A.patch("props", add=props)

    # ---------------------------------------------------------------- C: alet ve araç parçası simgeleri (arayüzde kutuya sığar)
    C = os.path.join(src, "C_Aletler_ve_Arac_Parcalari")
    icons = []
    for n in ["t_axe_stone", "t_axe_iron", "t_pick_stone", "t_pick_iron", "v_wheel", "v_engine", "v_frame_car",
              "v_frame_moto", "veh_car", "veh_moto"]:
        a = crop(key(os.path.join(C, n + ".png")))
        p = crop(pixelize(a, max(a.shape[:2]) / 72.0))
        h, w = p.shape[:2]
        icons.append(("i/" + n, to_img(p), w / 2, h - 1, 40.0 / max(w, h)))
    A.patch("items", add=icons)

    # ---------------------------------------------------------------- D: araçlar 8 yön (0 G, 1 GD, 2 D, 3 KD, 4 K, 5 KB, 6 B, 7 GB)
    D = os.path.join(src, "D_Araclar_8Yon")
    car = key(os.path.join(D, "car_8dir_alt_2row.png"))
    cp = parts(car)
    cf = [crop(car[y0:y1, x0:x1]) for x0, y0, x1, y1 in cp]
    # alt sayfa (2 satır): 0 ön, 1 sol-ön, 2 sol yan, 3 üst-arka, 4 sağ-ön, 5 arka, 6 sağ-ön, 7 sol-arka, 8 sağ yan, 9 sağ-ön
    car8 = [cf[0], cf[4], cf[8], flip(cf[7]), cf[5], cf[7], cf[2], cf[1]]
    moto = key(os.path.join(D, "moto_8dir.png"))
    mf = [crop(c) for c in cells(moto, 8)]
    # tek satır: 0 ön, 1-3 sola bakan yan, 4 arka, 5-6 sağa bakan yan, 7 sola bakan yan
    moto8 = [mf[0], mf[6], mf[5], flip(mf[3]), mf[4], mf[3], mf[2], mf[1]]
    veh = []
    for name, frames, s, side_w in (("car", car8, 2.0, 270.0), ("moto", moto8, 2.6, 150.0)):
        ps = [crop(pixelize(f, s)) for f in frames]
        k = side_w / ps[2].shape[1]
        for d, p in enumerate(ps):
            h, w = p.shape[:2]
            veh.append((f"x8/{name}/{d}", to_img(p), w / 2, h * 0.9, k))
        print(f"x8/{name}: k={k:.2f}", [p.shape[:2] for p in ps])
    A.patch("vehicles", add=veh)

    # ---------------------------------------------------------------- I: efektler
    I = os.path.join(src, "I_Efektler")
    fx_def = {  # ad: (kare, fps, döngü, karışım, çapa)
        "wood_chips": (6, 16, False, "normal", (0.5, 0.6)),
        "rock_chips": (6, 16, False, "normal", (0.5, 0.6)),
        "axe_swing": (6, 20, False, "add", (0.5, 0.5)),
        "exhaust": (6, 12, False, "normal", (0.5, 0.6)),
        "heli_dust": (8, 12, False, "normal", (0.5, 0.55)),
        "teleport": (8, 14, False, "add", (0.5, 0.85)),
        "bomb_blast": (8, 12, False, "normal", (0.5, 0.78)),
        "bomb_whistle": (6, 18, True, "add", (0.5, 0.5)),
        "level_ring": (8, 12, False, "add", (0.5, 0.8)),
    }
    fx_add = []
    for n, (fr, fps, loop, blend, anc) in fx_def.items():
        a = key(os.path.join(I, f"fx_{n}.png"), "black")
        cs = cells(a, fr)
        # tüm karelere ortak çerçeve (titreme olmasın)
        bbs = [bbox(c, 20) for c in cs]
        bbs = [b for b in bbs if b]
        x0 = min(b[0] for b in bbs); y0 = min(b[1] for b in bbs)
        x1 = max(b[2] for b in bbs); y1 = max(b[3] for b in bbs)
        for i, c in enumerate(cs):
            p = pixelize(c[y0:y1, x0:x1], 2.6, hard=False)
            h, w = p.shape[:2]
            fx_add.append((f"fx/{n}/{i}", to_img(p), w * anc[0], h * anc[1], 1.0))
        A.m["fx"][n] = {"frames": fr, "fps": fps, "loop": loop, "blend": blend, "scale": 1.0, "anchor": list(anc), "pixel": True}
        print(f"fx/{n}: {fr} kare {x1 - x0}x{y1 - y0}")
    A.patch("fx7", drop=lambda k: k.startswith(tuple(f"fx/{n}/" for n in fx_def)), add=fx_add)
    A.save()

    # ---------------------------------------------------------------- F/H: arayüz dosyaları
    H = os.path.join(src, "H_Arayuz")

    def ui(path, name, size=None, bg="green", s=None, hard=True):
        a = crop(key(path, bg))
        if size:
            s = max(a.shape[:2]) / size
        p = pixelize(a, s, hard) if s else a
        to_img(crop(p) if hard else p).save(os.path.join(UI, name + ".png"), optimize=True)
        print("ui/" + name, p.shape[:2])

    for n in ["tab_heal", "tab_potion", "tab_weapon", "tab_gear", "tab_jewel", "tab_material", "tab_vehicle", "tab_base"]:
        ui(f"{H}/{n}.png", n, 96)
    ui(f"{H}/mail_badge.png", "mail_badge", 96)
    ui(f"{H}/commander_portrait.png", "commander_portrait", 192)
    ui(f"{H}/card_order.png", "card_order", 600)
    ui(f"{F}/tut_hand.png", "tut_hand", 96)
    # etkinlik çerçevesi: ortası da yeşil → saydam
    ui(f"{H}/event_frame.png", "event_frame", 1032)
    ui(f"{H}/event_frame_glow.png", "event_frame_glow", 1032, bg="black", hard=False)
    # sinyal çubukları: 4 durum yan yana
    pb = crop(key(f"{H}/ping_bars.png", "magenta"))
    for i, c in enumerate(cells(pb, 4)):
        c = crop(c)
        p = crop(pixelize(c, max(c.shape[:2]) / 48.0))
        to_img(p).save(os.path.join(UI, f"ping_{i}.png"), optimize=True)
    Image.open(f"{H}/bg_garage.png").convert("RGB").save(os.path.join(UI, "bg_garage.webp"), "WEBP", quality=88)


if __name__ == "__main__":
    main(sys.argv[1])
