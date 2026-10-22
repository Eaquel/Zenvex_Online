"""V10 görsellerini mevcut atlasa yamar (ham render dizinleri gerekmez): kahraman pozları, askerler, bölge dekorları,
bölge eşyaları ve efektler. Yalnız ilgili piksel grupları yeniden paketlenir.

python3 tools/assetgen/v10_pack.py
"""
import v10_assets as V
from atlas_patch import Atlas


def main():
    A = Atlas()
    sp = A.m["sprites"]

    hero, anims = V.hero()
    old = tuple(f"h/kaan/{a}_" for a in V.HERO_REPLACED)
    A.patch("hero_kaan", drop=lambda k: k.startswith(old), add=hero)
    A.m["anims"]["hero"].update(anims)

    A.patch("npc6", add=V.npcs())
    A.patch("props", add=V.props())
    A.patch("items", add=V.items())

    frames, meta = V.fx()
    names = tuple(f"fx/{n}/" for n in V.FX)
    # eski 3D efektin (WebP "fx" grubu) kareleri: sayfa aynı kalır, anahtarlar silinir
    fx_old = set(A.m["groups"].get("fx", []))
    for k in [k for k, v in sp.items() if k.startswith(names) and v[0] in fx_old]:
        del sp[k]
    A.patch("fx7", drop=lambda k: k.startswith(names), add=frames)
    A.m["fx"].update(meta)
    A.save()


if __name__ == "__main__":
    main()
