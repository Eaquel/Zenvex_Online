"""V10 kullanıcı görsellerini (art/v10u/*.jpg) oyun parçalarına ayırır → art/v10/<grup>/<ad>.png

- Nesne sayfaları (yeşil zemin): ai_clean ile gerçek piksel ızgarası + arka plan silme; alfa bileşenleri satır satır,
  soldan sağa sıralanıp adlarla kırpılır (v8_objects ile aynı yöntem). Yazılı etiketler (poz adları) önce silinir.
- Sahneler (etkinlik afişi, kasa odası, bilet arka planı, uygulama simgesi): ızgaraya indirilir, saydamlık yok.

python3 tools/pixel/v10_objects.py [ad,...]
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from ai_clean import clean, fit_offset, pixel_size  # noqa: E402
from v8_objects import components, order  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
SRC = os.path.join(ROOT, "art", "v10u")
OUT = os.path.join(ROOT, "art", "v10")
TMP = os.environ.get("ZX_TMP", "/tmp/zx_v10_clean")

# 512 px genişliğe küçültülmüş kaynaklar: 1408 px'lik sayfalarla aynı 240'lık ızgaraya indirilir
P512 = 512 / 240

# nesne sayfası: (arka plan, grup, [adlar], birleştirme yarıçapı, piksel boyu (None = bul), silinecek dikdörtgenler (oran))
# adlar sırası: satır satır, soldan sağa. "-" ile başlayan ad atılır.
SHEETS = {
    # kahraman: K8 vurulma/düşme/ölüm ve K10 etkileşim pozları
    "hero_hurt": ("green", "hero", ["p_stagger", "p_kneel_head", "p_fall_back", "p_lie_back", "p_kneel_up", "p_stand_up",
                                    "p_collapse", "p_dead"], 4, 2.0, []),
    "hero_actions": ("green", "hero", ["p_pickup", "p_chop", "p_mine", "p_open", "p_drink", "p_cheer", "p_point"], 4, 2.0, []),
    # askerler (yan görünüm, sağa bakar); alttaki yazılar silinir
    "soldier_poses": ("green", "npc", ["soldier_walk1", "soldier_walk2", "soldier_aim_s", "soldier_fire", "soldier_crouch",
                                       "soldier_reload", "soldier_grenade", "soldier_hit", "soldier_falling", "soldier_dead"], 3, 2.93,
                      [(0, 0.44, 1, 0.52), (0, 0.94, 1, 1)]),
    "sniper_poses": ("green", "npc", ["sniper_prone", "sniper_kneel", "sniper_aim_s", "sniper_bolt", "sniper_recoil",
                                      "sniper_scan", "sniper_hit", "sniper_dead"], 3, 2.93,
                     [(0, 0.45, 1, 0.53), (0, 0.93, 1, 1)]),
    # sıralama
    "lb_medals": ("green", "ui", ["medal_1", "medal_2", "medal_3"], 3, 5.87, []),
    "lb_top10": ("green", "ui", ["badge_top10"], 3, 5.87, []),
    "lb_nameplates": ("green", "ui", ["plate_1", "plate_2", "plate_3", "plate_n"], 4, 2.93, []),
    "lb_icons": ("green", "ui", ["lb_kills", "lb_pvp", "lb_titan", "lb_gold", "lb_bases", "lb_dog", "lb_crafted", "lb_time"], 1, 5.87, []),
    # klan amblemleri (seviye 1-9)
    **{f"clan_{i}": ("green", "ui", [f"clan_{i}"], 6, None, []) for i in range(1, 10)},
    # 3. amblemin iki yanında beyaz geçişli şerit var
    "clan_3": ("green", "ui", ["clan_3"], 6, 5.87, [(0, 0, 0.25, 1), (0.75, 0, 1, 1)]),
    # elmas sandığı ve hediye kutuları
    "crate_a": ("green", "ui", ["crate_closed_a", "gift_closed", "gift_open"], 2, P512, []),
    "crate_b": ("green", "ui", ["crate_closed_b", "gift_closed_b"], 2, P512, []),
    "crate_c": ("green", "ui", ["crate_closed", "crate_open"], 2, P512, []),
    # sezon bileti
    "pass_cards": ("green", "ui", ["card_free", "card_premium", "card_locked"], 1, P512, []),
    "pass_ui": ("green", "ui", ["pass_bar", "pass_node", "pass_check", "pass_lock"], 1, P512, []),
    "pass_quests": ("green", "ui", ["q_daily", "q_weekly", "q_season"], 6, P512, []),
    # bölge dekorları
    "z_harbor": ("auto", "props", ["snow_pine", "ice_boulder", "frozen_boat", "anchor_snow", "snow_barrels"], 3, P512, []),
    "z_burnt": ("auto", "props", ["burnt_pine", "smolder_log", "charred_log", "deer_skeleton", "ember_rocks"], 3, P512, []),
    "z_desert": ("auto", "props", ["cactus", "cow_skull", "road_sign", "cone_pile", "gas_can"], 3, P512, []),
    "z_swamp": ("auto", "props", ["moss_tree", "mushroom_log", "rowboat", "reeds", "toxic_puddle"], 3, P512, []),
    "z_military": ("auto", "props", ["mil_tent", "jeep_wreck", "radar_dish", "ammo_stack", "jeep_wreck2", "sandbag_nest"], 3, P512, []),
    "z_city": ("auto", "props", ["street_lamp", "bus_stop", "burnt_car", "dumpster", "billboard"], 3, P512, []),
    "z_tents": ("auto", "props", ["tent_patched", "tent_olive", "tent_tarp"], 3, P512, []),
    # bölge eşyaları (yazılar silinir)
    "region_items": ("green", "items", ["frozen_fish", "ice_shard", "scorched_resin", "charcoal", "scorpion_venom", "dried_hide",
                                        "swamp_moss", "bog_iron", "circuit_board", "powder_pouch", "gold_watch", "rusty_key"], 1, P512,
                     [(0, 0.40, 1, 0.50), (0, 0.85, 1, 1)]),
    # arayüz çerçeveleri
    "ui_panel": ("green", "ui", ["frame_panel"], 4, P512, []),
    "ui_bars": ("green", "ui", ["btn_red", "btn_red_down", "btn_off"], 2, P512, []),
    "ui_frames": ("green", "ui", ["tab_on", "tab_off", "slot_common", "slot_epic", "slot_legend", "btn_close", "btn_skull"], 2, P512, []),
}

# sahneler: (grup, piksel boyu (None = bul), kırpma (oran x0, y0, x1, y1), çıktı biçimi)
SCENES = {
    "app_icon": ("icon", None, (0, 0, 1, 1), "png"),
    "ev_blood": ("ui", None, (0.008, 0.018, 0.992, 0.835), "webp"),
    "ev_base": ("ui", None, (0.008, 0.018, 0.992, 0.835), "webp"),
    "ev_pack": ("ui", None, (0.008, 0.018, 0.992, 0.835), "webp"),
    "ev_breath": ("ui", None, (0.008, 0.018, 0.992, 0.835), "webp"),
    "ev_titan": ("ui", 1.0, (0.0, 0.0, 1.0, 0.85), "webp"),
    "ev_scrap": ("ui", 1.0, (0.0, 0.0, 1.0, 0.85), "webp"),
    "crate_vault": ("ui", 1.0, (0, 0, 1, 1), "webp"),
    "pass_bg": ("ui", 1.0, (0, 0, 1, 1), "webp"),
}


def cleaned(name, bg, px, src=None, erase=None):
    """Izgaraya indirilmiş, arka planı silinmiş sayfa (önbellekli). [src] verilmezse art/v10u/<ad>.jpg."""
    os.makedirs(TMP, exist_ok=True)
    p = os.path.join(TMP, name + ".png")
    if not os.path.exists(p):
        erase = SHEETS[name][5] if erase is None else erase
        src = src or os.path.join(SRC, name + ".jpg")
        if erase:
            # yazılı etiketleri arka plan rengiyle boya (ızgara bulunmadan önce)
            im = Image.open(src).convert("RGB")
            a = np.asarray(im).copy()
            H, W, _ = a.shape
            bgc = np.median(np.concatenate([a[:4, :4].reshape(-1, 3), a[:4, -4:].reshape(-1, 3)]), 0).astype(np.uint8)
            for x0, y0, x1, y1 in erase:
                a[int(y0 * H):int(y1 * H), int(x0 * W):int(x1 * W)] = bgc
            src = os.path.join(TMP, name + "_src.png")
            Image.fromarray(a).save(src)
        clean(src, p, bg, px)
    return Image.open(p).convert("RGBA")


# kızıl ışıltılı kenarı yeşil zeminle karışıp koyu zeytine dönen görseller (elmas sandığı ve hediye kutuları)
OLIVE = {"crate_a", "crate_b", "crate_c"}
# gerçekten parlak yeşil parçası olan sayfalar (zehir birikintisi): yalnız dış kenar hâlesi silinir
KEEP_GREEN = {"z_swamp"}


def despill(c, olive=False, keep_green=False):
    """Kenarda kalan yeşil hâle ve kapalı boşluklardaki yeşil. [olive]: dış halkadaki koyu zeytin tonları da silinir
    (yeşil kırmızıya yakın, mavi düşük, parlaklık orta altı: altın sarısı ve gerçek yeşil nesneler etkilenmez)."""
    r, g, b = (c[..., i].astype(int) for i in range(3))
    op = c[..., 3] > 0
    if not keep_green:
        c[op & (g > r + 50) & (g > b + 50) & (g > 120), 3] = 0
    for _ in range(4 if olive else 2):
        op = c[..., 3] > 0
        out = np.zeros_like(op)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            out |= ~np.roll(np.roll(op, dy, 0), dx, 1)
        halo = op & out & (g > np.maximum(r, b) + 25) & ~(keep_green & (g > 150))
        if olive:
            halo |= op & out & (g * 100 >= r * 85) & (b * 10 < g * 7) & (r + g + b < 480) & (g > 40)
        c[halo, 3] = 0
    return c


def build_sheet(name):
    bg, group, names, radius, px, erase = SHEETS[name]
    extract_sheet(name, os.path.join(OUT, group), names, radius, px, bg=bg, erase=erase,
                  olive=name in OLIVE, keep_green=name in KEEP_GREEN)


def extract_sheet(name, out_dir, names, radius=3, px=None, bg="green", erase=(), src=None, olive=False, keep_green=False, mirror=False):
    """Sayfayı parçalara ayırır: satır satır soldan sağa [names] ("-" ile başlayan atılır). [mirror]: parçalar aynalanır."""
    im = cleaned(name, bg, px, src, erase)
    a = despill(np.asarray(im).copy(), olive, keep_green)
    rows = order(components(a, radius))
    # parça etiketleri (components ile aynı büyütme): kırpılan kutuya komşu parçaların pikselleri girmesin
    solid = a[..., 3] > 0
    lab, _ = ndimage.label(ndimage.binary_dilation(solid, iterations=radius) if radius > 0 else solid)
    lab = np.where(solid, lab, 0)
    flat = [b for r in rows for b in r]
    if len(flat) != len(names):
        print(f"! {name}: {len(flat)} parça bulundu, {len(names)} bekleniyordu")
        flat2 = sorted(flat, key=lambda b: -b[4])[:len(names)]
        flat = [b for b in flat if b in flat2]
    os.makedirs(out_dir, exist_ok=True)
    for nm, (y0, x0, y1, x1, _, lab_id) in zip(names, flat):
        if nm.startswith("-"):
            continue
        part = a.copy()
        # başka adlı parçaya ait pikseller atılır (kutudaki küçük süsler, ışınlar kalır)
        others = [f[5] for f in flat if f[5] != lab_id]
        part[np.isin(lab, others), 3] = 0
        crop = Image.fromarray(part).crop((x0, y0, x1, y1))
        if mirror:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        crop.save(os.path.join(out_dir, nm + ".png"))
        print(f"{os.path.basename(out_dir)}/{nm}: {crop.size}")


def build_scene(name):
    group, px, (cx0, cy0, cx1, cy1), fmt = SCENES[name]
    a = np.asarray(Image.open(os.path.join(SRC, name + ".jpg")).convert("RGB")).astype(float)
    H, W, _ = a.shape
    a = a[int(cy0 * H):int(cy1 * H), int(cx0 * W):int(cx1 * W)]
    s = px or pixel_size(a, 2.0, 12.0)
    ox, oy = fit_offset(a, s)
    H, W, _ = a.shape
    nw, nh = int((W - ox) // s), int((H - oy) // s)
    out = np.zeros((nh, nw, 3))
    for j in range(nh):
        y0, y1 = int(round(oy + j * s + 1)), int(round(oy + (j + 1) * s - 1))
        for i in range(nw):
            x0, x1 = int(round(ox + i * s + 1)), int(round(ox + (i + 1) * s - 1))
            out[j, i] = np.median(a[y0:max(y1, y0 + 1), x0:max(x1, x0 + 1)].reshape(-1, 3), 0)
    img = Image.fromarray(out.clip(0, 255).astype(np.uint8))
    od = os.path.join(OUT, group)
    os.makedirs(od, exist_ok=True)
    p = os.path.join(od, f"{name}.{fmt}")
    if fmt == "webp":
        img.save(p, "WEBP", lossless=True)
    else:
        img.save(p)
    print(f"{group}/{name}: piksel {s:.2f} → {img.size}")


if __name__ == "__main__":
    which = sys.argv[1].split(",") if len(sys.argv) > 1 else list(SHEETS) + list(SCENES)
    for w in which:
        if w in SHEETS:
            build_sheet(w)
        else:
            build_scene(w)
