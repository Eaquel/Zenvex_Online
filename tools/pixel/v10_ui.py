"""V10 arayüz görselleri: art/v10/ui → android/app/src/main/assets/ui (Compose ekranları UiArt ile yükler).

Ayrıca kasa nadirlik ışınları (art/v10u/crate_beams.jpg: 5 nadirlik × 7 kare, siyah zemin) her nadirlik için
yatay şerit olur: ui/beam_<0-4>.png (kareler eşit genişlikte, ışık alfası en parlak kanaldan).

python3 tools/pixel/v10_ui.py
"""
import os
import shutil

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SRC = os.path.join(ROOT, "art", "v10", "ui")
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "ui")

USED = [
    # pencere, tuş, sekme, yuva, kapat
    "frame_panel", "btn_red", "btn_red_down", "btn_off", "tab_on", "tab_off", "slot_common", "slot_epic", "slot_legend", "btn_close",
    # sıralama
    "medal_1", "medal_2", "medal_3", "badge_top10", "plate_1", "plate_2", "plate_3", "plate_n",
    "lb_kills", "lb_pvp", "lb_titan", "lb_gold", "lb_bases", "lb_dog", "lb_crafted", "lb_time",
    # klan seviyeleri
    *[f"clan_{i}" for i in range(1, 10)],
    # elmas kasası
    "crate_closed", "crate_open", "gift_closed", "gift_open", "crate_vault",
    # sezon bileti
    "pass_bg", "card_free", "card_premium", "card_locked", "pass_bar", "pass_node", "pass_check", "pass_lock",
    "q_daily", "q_weekly", "q_season",
    # etkinlik afişleri
    "ev_blood", "ev_titan", "ev_base", "ev_scrap", "ev_pack", "ev_breath",
]


def beams():
    a = np.asarray(Image.open(os.path.join(ROOT, "art", "v10u", "crate_beams.jpg")).convert("RGB")).astype(float)
    H, W, _ = a.shape
    rows, cols = 5, 7
    cells = [[a[int(r * H / rows):int((r + 1) * H / rows), int(c * W / cols):int((c + 1) * W / cols)] for c in range(cols)] for r in range(rows)]
    ch = min(int(H / rows), *(cl.shape[0] for row in cells for cl in row))
    cw = min(cl.shape[1] for row in cells for cl in row)
    frames = []
    for row in cells:
        fr = []
        for c in row:
            c = c[:ch, :cw]
            al = c.max(-1)
            al = np.where(al < 16, 0, al)
            col = np.where(al[..., None] > 0, c * 255.0 / np.maximum(al[..., None], 1), 0)
            fr.append(np.dstack([np.clip(col, 0, 255), al]).astype(np.uint8))
        frames.append(fr)
    # tüm kareler için ortak sınır kutusu (nadirlikler aynı boyda)
    m = np.zeros((ch, cw), bool)
    for fr in frames:
        for f in fr:
            m |= f[..., 3] > 0
    ys, xs = np.nonzero(m)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    for r, fr in enumerate(frames):
        strip = np.concatenate([f[y0:y1, x0:x1] for f in fr], axis=1)
        Image.fromarray(strip).save(os.path.join(OUT, f"beam_{r}.png"))
    print("beam", rows, "×", cols, (x1 - x0, y1 - y0))


def main():
    for n in USED:
        for ext in ("png", "webp"):
            p = os.path.join(SRC, f"{n}.{ext}")
            if os.path.exists(p):
                shutil.copyfile(p, os.path.join(OUT, f"{n}.{ext}"))
                break
        else:
            print("! yok:", n)
    beams()


if __name__ == "__main__":
    main()
