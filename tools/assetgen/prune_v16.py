#!/usr/bin/env python3
"""V16 temizliği: kahraman, araç ve efektler 3B / kodla çizildiği için atlasta kullanılmayan görselleri siler.

Silinenler: eski 2B kahraman kareleri (h/kaan/*), lobi kahramanı (l/kaan/*), eski araç kareleri (v/*, x8/moto, x8/car;
tır x8/truck ve helikopter kalır), tüm atlas efekt sprite'ları (fx7, fx: patlama, iyileşme, ışınlanma… artık Fx3D/FxTex kodu).
Sayfa indeksleri kaymasın diye boşalan sayfalar 64x64 yer tutucuya çevrilir (Atlas.patch).
Çalıştırma: python3 tools/assetgen/prune_v16.py
"""
import os

from atlas_patch import Atlas

HERE = os.path.dirname(os.path.abspath(__file__))
UI = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "assets", "ui")
DEAD_UI = ["btn_drive2.png", "lb_gold.png"]


def main():
    a = Atlas()
    a.patch("hero_kaan", drop=lambda k: True)
    a.patch("lobby_kaan", drop=lambda k: True)
    a.patch("vehicles", drop=lambda k: not k.startswith("x8/truck"))
    a.patch("fx7", drop=lambda k: True)
    a.patch("fx", drop=lambda k: True)
    a.save()
    for f in DEAD_UI:
        p = os.path.join(UI, f)
        if os.path.exists(p):
            os.remove(p)
            print("silindi:", f)


if __name__ == "__main__":
    main()
