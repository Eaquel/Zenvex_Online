"""Atlası ham render dizinleri olmadan günceller: seçili piksel gruplarını mevcut sayfalardan geri çıkarır, verilen
görselleri ekler/değiştirir ve yalnız o grupları yeniden paketler. Diğer sayfalar (WebP) dokunulmadan kalır.

pack.py tam paketleme için Blender/piksel çıktılarına (/home/user/zx_render, zx_pix) ihtiyaç duyar; bunlar depoda
yok. Bu araç depodaki sayfaları kaynak kabul eder (piksel sayfaları kayıpsız PNG olduğu için geri çıkarma kayıpsızdır).

Kullanım (kitaplık):
    A = Atlas(dizin)
    A.patch("props", drop=lambda k: k in {...}, add=[(anahtar, Image, ax, ay, k), ...])
    A.save()
"""
import json
import os

from PIL import Image

from pack import Packer, _save_page

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "android", "app", "src", "main", "assets", "v2")


class Atlas:
    def __init__(self, out=OUT):
        self.out = out
        self.m = json.load(open(os.path.join(out, "manifest.json")))
        self._pages = {}
        self.dirty = {}  # sayfa indeksi → (Image, piksel mi)

    def page(self, i):
        if i not in self._pages:
            self._pages[i] = Image.open(os.path.join(self.out, self.m["pages"][i])).convert("RGBA")
        return self._pages[i]

    def group_items(self, group):
        """Grubun mevcut görselleri: [(anahtar, Image, ax, ay, k)]"""
        ids = set(self.m["groups"].get(group, []))
        out = []
        for key, v in self.m["sprites"].items():
            p, x, y, w, h, ax, ay = v[:7]
            if p in ids:
                k = v[7] if len(v) > 7 else 1.0
                out.append((key, self.page(p).crop((x, y, x + w, y + h)), ax, ay, k))
        return out

    def patch(self, group, drop=lambda k: False, add=()):
        """Grubu yeniden paketler: drop(anahtar) doğru olanlar atılır, add eklenir (aynı anahtar varsa yerine geçer)."""
        new_keys = {t[0] for t in add}
        items = [t for t in self.group_items(group) if not drop(t[0]) and t[0] not in new_keys] + list(add)
        old_ids = list(self.m["groups"].get(group, []))
        for key in [k for k, v in self.m["sprites"].items() if v[0] in set(old_ids)]:
            del self.m["sprites"][key]
        # anahtarlar başka grupta da duruyorsa (eski sürüm) oradan da silinir: yeni görsel tek kaynak
        for key in new_keys:
            self.m["sprites"].pop(key, None)
        P = Packer()
        P.pack_group(group, items, k=1.0, pixel=True)
        ids = []
        for local, img in enumerate(P.pages):
            gi = old_ids[local] if local < len(old_ids) else self._new_index()
            ids.append(gi)
            self.dirty[gi] = img
            self._pages[gi] = img
        for gi in old_ids[len(P.pages):]:
            # artık kullanılmayan sayfa: yer tutucu (indeksler kaymasın)
            self.dirty[gi] = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for key, v in P.sprites.items():
            v = list(v)
            v[0] = ids[v[0]]
            self.m["sprites"][key] = v
        self.m["groups"][group] = ids
        print(f"{group}: {len(items)} görsel, {len(ids)} sayfa {ids}")

    def _new_index(self):
        self.m["pages"].append(None)
        return len(self.m["pages"]) - 1

    def save(self):
        for i, img in self.dirty.items():
            old = self.m["pages"][i]
            fn = _save_page((i, img, self.out, True))
            if old and old != fn and os.path.exists(os.path.join(self.out, old)):
                os.remove(os.path.join(self.out, old))
            self.m["pages"][i] = fn
        json.dump(self.m, open(os.path.join(self.out, "manifest.json"), "w"), separators=(",", ":"))
        total = sum(os.path.getsize(os.path.join(self.out, f)) for f in self.m["pages"])
        print(f"{len(self.m['pages'])} sayfa, {total / 1e6:.1f} MB, {len(self.m['sprites'])} sprite")
