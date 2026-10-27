"""3B silah modellerinden eşya simgeleri: rust/zenvex_r3d `icons` örneğiyle çizer, atlasın "items" grubuna yazar.

Kullanım (depo kökünden):
    python3 tools/model3d/icons.py

Silah eşyaları (oyundaki görünümleriyle aynı model): bıçak, katana, sopa, tabanca, AK-47, keskin nişancı, RPG-7, Uzi,
el baltası, savaş baltası, lanetli taş tokmak ve altı etkinlik silahı. Eski piksel simgelerinin yerine geçer.
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "assetgen"))
from atlas_patch import Atlas  # noqa: E402

# eşya → model
ICONS = {
    "w_knife": "knife",
    "w_katana": "katana",
    "w_bat": "club",
    "w_glock": "pistol",
    "w_ak47": "ak47",
    "w_sniper": "sniper",
    "w_rpg": "rpg",
    "w_uzi": "uzi",
    "w_hatchet": "hatchet",
    "w_handaxe": "handaxe",
    "w_effigy": "effigy",
    "ev_blood_weapon": "blood_scythe",
    "ev_titan_weapon": "magma_hammer",
    "ev_base_weapon": "storm_axe",
    "ev_scrap_weapon": "chainsaw",
    "ev_pack_weapon": "void_claw",
    "ev_breath_weapon": "ice_scythe",
}
# simge kutusu (piksel) ve atlas ölçeği: eski simgeler 160 px × 0,25 = 40 birim
BOX = 192
UNITS = 40.0


def card_icon(m3d, tmp):
    """Karakter Kartı simgesi: mor (destansı) kart, altın çerçeve, önde Kael, arkada Lyra, Vex ve Morvath."""
    from PIL import ImageDraw, ImageFilter
    heroes = {}
    for c in ("kael", "lyra", "vex", "morvath"):
        out = os.path.join(tmp, f"card_{c}.png")
        subprocess.run([os.path.join(ROOT, "rust", "target", "release", "examples", "anims"), m3d, out, c, "-", "idle", "20", "1"],
                       check=True, stderr=subprocess.DEVNULL)
        im = Image.open(out).convert("RGBA")
        px = im.load()
        # önizleme zemini ve zemin çizgisi saydam
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = px[x, y]
                if (r, g, b) in ((72, 92, 72), (40, 52, 40)):
                    px[x, y] = (0, 0, 0, 0)
        heroes[c] = im.crop(im.getbbox())
    W, H = 150, 192
    card = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bg = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(bg)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=(int(70 - 40 * t), int(30 - 16 * t), int(110 - 60 * t), 255))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, H - 1], 14, fill=255)
    card.paste(bg, (0, 0), mask)
    # arkadaki üç kahraman (karartılmış), öndeki Kael
    for c, cx, s, dim in (("vex", 0.5, 0.64, 0.6), ("lyra", 0.2, 0.66, 0.8), ("morvath", 0.8, 0.66, 0.8), ("kael", 0.5, 0.74, 1.0)):
        im = heroes[c]
        k = H * s / im.height
        im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)
        if dim < 1.0:
            r, g, b, a = im.split()
            im = Image.merge("RGBA", [ch.point(lambda v: int(v * dim)) for ch in (r, g, b)] + [a])
        card.alpha_composite(im, (int(W * cx - im.width / 2), int(H * (0.95 if c == "kael" else 0.84) - im.height)))
    card.putalpha(Image.composite(card.getchannel("A"), Image.new("L", (W, H), 0), mask))
    ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(ring).rounded_rectangle([2, 2, W - 3, H - 3], 13, outline=(232, 190, 90, 255), width=5)
    ImageDraw.Draw(ring).rounded_rectangle([8, 8, W - 9, H - 9], 9, outline=(255, 232, 160, 140), width=1)
    card.alpha_composite(ring)
    return card


def main():
    m3d = os.path.join(ROOT, "android", "app", "src", "main", "assets", "m3d")
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["cargo", "run", "-q", "-p", "zenvex_r3d", "--example", "icons", "--release", "--", m3d, tmp],
                       cwd=os.path.join(ROOT, "rust"), check=True)
        add = []
        for item, model in ICONS.items():
            im = Image.open(os.path.join(tmp, model + ".png")).convert("RGBA")
            im.load()
            im.thumbnail((BOX, BOX), Image.LANCZOS)
            add.append(("i/" + item, im.copy(), im.width / 2, im.height / 2, UNITS / BOX))
        card = card_icon(m3d, tmp)
        card.thumbnail((BOX, BOX), Image.LANCZOS)
        add.append(("i/char_card", card, card.width / 2, card.height / 2, UNITS / BOX))
    A = Atlas()
    A.patch("items", add=add)
    A.save()


if __name__ == "__main__":
    main()
