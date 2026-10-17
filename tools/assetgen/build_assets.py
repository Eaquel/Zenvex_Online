#!/usr/bin/env python3
"""Tüm oyun görsellerini üretir -> android/app/src/main/assets/

Kullanım: python3 tools/assetgen/build_assets.py [--quick]
"""
import json
import math
import os
import sys
import time
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from sdf import render, METERS_PER_PX  # noqa: E402
import character as C  # noqa: E402
import nature as N  # noqa: E402
import flat as F  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets")
SPR = os.path.join(OUT, "sprites")
PREVIEW = os.path.join(ROOT, "art", "preview")
QUICK = "--quick" in sys.argv

FW, FH, AX, AY = 96, 80, 48, 66        # karakter karesi + ayak noktası
DIRS = 8


def save(img, *parts):
    path = os.path.join(SPR, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    return "/".join(parts)


def render_actor(kind, anims, posef):
    meta = {"frame": [FW, FH], "anchor": [AX, AY], "dirs": DIRS, "anims": {}}
    for anim, (frames, fps, loop) in anims.items():
        rows = []
        for d in range(DIRS):
            row = []
            for f in range(frames):
                P = posef(anim, f / frames)
                P["yaw"] = d * math.pi / 4
                J = C.skeleton(P)
                row.append(render(C.human_scene(J, kind), FW, FH, (AX, AY), C.MATERIALS,
                                  C.human_decals(J, kind)))
            rows.append(np.concatenate(row, 1))
        sheet = Image.fromarray(np.concatenate(rows, 0), "RGBA")
        f = save(sheet, f"{kind}_{anim}.png")
        meta["anims"][anim] = {"file": f, "frames": frames, "fps": fps, "loop": loop}
        print(f"  {kind}/{anim}: {frames}x{DIRS}")
    return meta


def render_lobby_player():
    """Lobi için 2x detaylı karakter: 8 yön idle (döndürülebilir)."""
    w, h = 128, 128
    rows = []
    for d in range(DIRS):
        row = []
        for f in range(4):
            P = C.player_pose("idle", f / 4)
            P["yaw"] = d * math.pi / 4
            J = C.skeleton(P)
            row.append(render(C.human_scene(J, "player"), w, h, (64, 118), C.MATERIALS,
                              [], mpp=METERS_PER_PX / 2))
        rows.append(np.concatenate(row, 1))
    img = Image.fromarray(np.concatenate(rows, 0), "RGBA")
    # 2x çözünürlükte gözler 2 piksel: ayrıca çiz
    return save(img, "lobby_player.png"), {"frame": [w, h], "anchor": [64, 118], "frames": 4, "fps": 5}


def render_nature():
    meta = {}
    specs = []
    for i in range(3):
        sc, nt, by = N.oak(i + 1)
        specs.append((f"oak{i}", sc, 128, 168, 20, N.canopy_tone(by, 0.45, nt), "tree"))
    for i in range(3):
        sc, (y0, dy) = N.pine(i + 11)
        specs.append((f"pine{i}", sc, 112, 192, 26, N.pine_tone(y0, dy), "tree"))
    for i in range(2):
        specs.append((f"birch{i}", N.birch(i + 21), 96, 160, 14, N.birch_tone, "tree"))
    for i in range(2):
        specs.append((f"dead{i}", N.dead_tree(i + 31), 112, 144, 12, None, "tree"))
    for i, kind in enumerate(["plain", "plain", "wide", "berry", "flower"]):
        specs.append((f"bush{i}", N.bush(i + 41, kind), 80 if kind == "wide" else 64, 60, 14,
                      N.canopy_tone(0.3, 0.2) if kind in ("plain", "wide") else None, "bush"))
    for i in range(3):
        specs.append((f"rock_big{i}", N.rock(i + 51, 1.0), 80, 64, 12, None, "rock"))
    for i in range(3):
        specs.append((f"rock_small{i}", N.rock(i + 61, 0.45), 48, 40, 10, None, "deco"))
    specs.append(("stump", N.stump(), 40, 40, 10, N.stump_tone, "stump"))
    specs.append(("log", N.log_fallen(), 96, 48, 12, None, "rock"))
    for name, sc, w, h, bottom, tf, cat in specs:
        img = render(sc, w, h, (w // 2, h - bottom), N.MATERIALS, tone_fn=tf, max_t=18.0, steps=96)
        f = save(Image.fromarray(img, "RGBA"), "nature", name + ".png")
        meta[name] = {"file": f, "size": [w, h], "anchor": [w // 2, h - bottom], "type": cat}
        print("  doğa:", name)
    return meta


def build_flat():
    meta = {}
    ts, names = F.tileset()
    meta["tileset"] = {"file": save(ts, "tiles", "ground.png"), "tile": 32, "cols": 8, "names": names}
    deco = {}
    for i in range(3):
        deco[f"dirt_patch{i}"] = save(F.dirt_patch(70 + i, 64 + 16 * i, 40 + 8 * i), "tiles", f"dirt_patch{i}.png")
    for i in range(5):
        deco[f"tuft{i}"] = save(F.outline(F.grass_tuft(80 + i), "#1d4428"), "deco", f"tuft{i}.png")
    for k in ["daisy", "poppy", "corn", "butter", "lavender", "marigold"]:
        deco[f"flower_{k}"] = save(F.flower(k), "deco", f"flower_{k}.png")
    meta["deco"] = deco
    meta["shadow"] = save(F.shadow(28, 10), "fx", "shadow.png")
    meta["shadow_big"] = save(F.shadow(64, 20), "fx", "shadow_big.png")
    ui = {}
    ui["joy_base"] = save(F.ring(140, 69, 52, "#23365a", "#1b1c26", 150, ticks=8), "ui", "joy_base.png")
    ui["joy_knob"] = save(F.disc(60, "#ffe9a8", "#e8a93a", "#b9772a", "#7a4a1f"), "ui", "joy_knob.png")
    ui["atk_base"] = save(F.ring(140, 69, 52, "#5e1a2a", "#1b1c26", 150, ticks=8), "ui", "atk_base.png")
    knob = F.disc(60, "#ff9a9a", "#e04848", "#a02c3c", "#5e1a2a")
    fist = F.icon("fist").resize((32, 32), Image.NEAREST)
    knob.alpha_composite(fist, (14, 14))
    ui["atk_knob"] = save(knob, "ui", "atk_knob.png")
    ui["panel"] = save(F.nine_patch(48, 48, "#23365a", "#33507e", "#172238", "#0e1424", 4), "ui", "panel.png")
    ui["btn_primary"] = save(F.nine_patch(48, 32, "#e8a93a", "#ffd25e", "#b9772a", "#4a2a10", 3), "ui", "btn_primary.png")
    ui["btn_secondary"] = save(F.nine_patch(48, 32, "#33507e", "#4a6fa5", "#23365a", "#0e1424", 3), "ui", "btn_secondary.png")
    ui["btn_danger"] = save(F.nine_patch(48, 32, "#a02c3c", "#e04848", "#5e1a2a", "#2a0a10", 3), "ui", "btn_danger.png")
    ui["slot"] = save(F.nine_patch(24, 24, "#172238", "#23365a", "#0e1424", "#3e4157", 2), "ui", "slot.png")
    icons = {}
    for name in F.ICONS:
        icons[name] = save(F.icon(name), "icons", name + ".png")
    meta["ui"] = ui
    meta["icons"] = icons
    return meta


def build_backgrounds():
    out = {}
    concept = os.path.join(ROOT, "art", "concept")
    for name in ("lobby_bg", "title_bg"):
        src = os.path.join(concept, name + ".jpg")
        if os.path.exists(src):
            dst = os.path.join(SPR, "bg", name + ".png")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            F.pixelate_bg(src, dst)
            out[name] = "bg/" + name + ".png"
    return out


def contact_sheet(manifest):
    """Önizleme: art/preview/*.png (4x büyütülmüş, çim zeminde)."""
    os.makedirs(PREVIEW, exist_ok=True)
    for kind in ("player", "zombie"):
        for anim, m in manifest[kind]["anims"].items():
            im = Image.open(os.path.join(SPR, m["file"]))
            bg = Image.new("RGBA", im.size, (127, 196, 74, 255))
            bg.alpha_composite(im)
            bg.resize((im.width * 3, im.height * 3), Image.NEAREST).save(os.path.join(PREVIEW, f"{kind}_{anim}.png"))
    # doğa
    items = [Image.open(os.path.join(SPR, v["file"])) for v in manifest["nature"].values()]
    W = sum(i.width for i in items[:10])
    W2 = sum(i.width for i in items[10:])
    H = 200
    canvas = Image.new("RGBA", (max(W, W2), H * 2), (127, 196, 74, 255))
    x = 0
    for i in items[:10]:
        canvas.alpha_composite(i, (x, H - i.height)); x += i.width
    x = 0
    for i in items[10:]:
        canvas.alpha_composite(i, (x, 2 * H - i.height)); x += i.width
    canvas.resize((canvas.width * 2, canvas.height * 2), Image.NEAREST).save(os.path.join(PREVIEW, "nature.png"))


def build_launcher_icon():
    """Uygulama ikonu: lobi karakterinin üst gövdesi, yeşil zemin, hardal çerçeve."""
    sheet = Image.open(os.path.join(SPR, "lobby_player.png"))
    frame = sheet.crop((0, 128, 128, 256))  # güneydoğu yönü, 1. kare
    bust = frame.crop((24, 24, 104, 104))
    res = os.path.join(ROOT, "android", "app", "src", "main", "res")
    base = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    a = np.zeros((96, 96, 4), np.uint8)
    y, x = np.mgrid[0:96, 0:96]
    inside = (np.minimum(np.minimum(x, 95 - x), np.minimum(y, 95 - y)) >= 2)
    corner = ((np.minimum(x, 95 - x) + np.minimum(y, 95 - y)) < 14)
    body = inside & ~corner
    a[body] = (79, 154, 58, 255)
    a[body & (y < 40)] = (127, 196, 74, 255)
    edge = (~body) & (np.minimum(np.minimum(x, 95 - x), np.minimum(y, 95 - y)) >= 0) & ((np.minimum(x, 95 - x) + np.minimum(y, 95 - y)) >= 10)
    a[edge] = (232, 169, 58, 255)
    base = Image.fromarray(a, "RGBA")
    base.alpha_composite(bust, (8, 12))
    for name, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        d = os.path.join(res, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        base.resize((size, size), Image.NEAREST).save(os.path.join(d, "ic_launcher.png"))
    base.resize((512, 512), Image.NEAREST).save(os.path.join(PREVIEW, "icon_512.png"))


def main():
    t0 = time.time()
    manifest = {"version": 1, "meters_per_px": METERS_PER_PX}
    print("arayüz/zemin...")
    manifest.update(build_flat())
    manifest["bg"] = build_backgrounds()
    print("doğa...")
    manifest["nature"] = render_nature()
    print("karakter...")
    anims = C.ANIMS if not QUICK else {"idle": C.ANIMS["idle"]}
    manifest["player"] = render_actor("player", anims, C.player_pose)
    print("zombi...")
    zanims = C.ZOMBIE_ANIMS if not QUICK else {"idle": C.ZOMBIE_ANIMS["idle"]}
    manifest["zombie"] = render_actor("zombie", zanims, C.zombie_pose)
    f, m = render_lobby_player()
    manifest["lobby_player"] = dict(file=f, **m)
    with open(os.path.join(SPR, "manifest.json"), "w") as fh:
        json.dump(manifest, fh, indent=1, ensure_ascii=False)
    contact_sheet(manifest)
    build_launcher_icon()
    print(f"bitti: {time.time() - t0:.1f} sn")


if __name__ == "__main__":
    main()
