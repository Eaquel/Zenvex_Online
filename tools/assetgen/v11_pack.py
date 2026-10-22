"""V11 aktör animasyonlarını (art/v11/<aktör>/anim) mevcut atlasa yamar: aktörün yeniden çizilen animasyonlarının eski
kareleri (tüm yönler ve silahlı varyantlar) atılır, yenileri eklenir; manifest'e aktöre özel animasyon tablosu yazılır
(kare sayısı türden türe değişebilir; oyun Atlas.anims(önek) ile okur).

python3 tools/assetgen/v11_pack.py
"""
import v10_assets as V
from atlas_patch import Atlas


def main():
    A = Atlas()
    an = A.m["anims"]
    for pre, items, anims in V.v11_actors():
        old = tuple(f"{pre}/{a}_" for a in anims)
        A.patch(V.actor_group(pre), drop=lambda k, old=old: k.startswith(old), add=items)
        base = an["hero"] if pre.startswith("h/") else an["zombie"]
        table = dict(an.get(pre, base))
        table.update(anims)
        an[pre] = table
    A.save()


if __name__ == "__main__":
    main()
