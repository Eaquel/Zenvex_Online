"""V11 arayüz simgeleri: elle tanımlanmış şekillerden piksel sanat (keskin piksel, otomatik koyu dış çizgi, üst-sol ışık).

Çıktı: android/app/src/main/assets/ui/ic_<ad>.png (Compose Icon() ve GameView varsa bunları çizer, yoksa vektör simge).
Sıralama sekmeleri için ayrıca lb_level, lb_soldiers, lb_clan.

python3 tools/pixel/icons_v11.py [önizleme.png]
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "ui")
OUTLINE = (20, 15, 12, 255)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


class Px:
    def __init__(self, n):
        self.n = n
        self.p = [[None] * n for _ in range(n)]

    def set(self, x, y, c):
        x, y = int(x), int(y)
        if 0 <= x < self.n and 0 <= y < self.n:
            self.p[y][x] = c if isinstance(c, tuple) else hexc(c)

    def get(self, x, y):
        if 0 <= x < self.n and 0 <= y < self.n:
            return self.p[y][x]
        return None

    def each(self, inside, color):
        """inside(x+0.5, y+0.5) doğruysa boya; color sabit ya da (x, y) → renk"""
        for y in range(self.n):
            for x in range(self.n):
                if inside(x + 0.5, y + 0.5):
                    c = color(x, y) if callable(color) else color
                    if c is not None:
                        self.set(x, y, c)

    def poly(self, pts, color):
        def inside(px, py):
            n = len(pts)
            j = n - 1
            r = False
            for i in range(n):
                xi, yi = pts[i]
                xj, yj = pts[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi:
                    r = not r
                j = i
            return r
        self.each(inside, color)

    def ellipse(self, cx, cy, rx, ry, color):
        self.each(lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0, color)

    def rect(self, x0, y0, x1, y1, color):
        self.each(lambda x, y: x0 <= x <= x1 and y0 <= y <= y1, color)

    def line(self, x0, y0, x1, y1, color, w=1):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(steps + 1):
            t = i / steps
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            for dx in range(w):
                for dy in range(w):
                    self.set(round(x - (w - 1) / 2 + dx - 0.5), round(y - (w - 1) / 2 + dy - 0.5), color)

    def outline(self, color=OUTLINE):
        add = []
        for y in range(self.n):
            for x in range(self.n):
                if self.p[y][x] is None and any(self.get(x + dx, y + dy) is not None and self.get(x + dx, y + dy) != color
                                                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    add.append((x, y))
        for x, y in add:
            self.p[y][x] = color

    def image(self):
        im = Image.new("RGBA", (self.n, self.n), (0, 0, 0, 0))
        for y in range(self.n):
            for x in range(self.n):
                if self.p[y][x] is not None:
                    im.putpixel((x, y), self.p[y][x])
        return im


def star_pts(cx, cy, ro, ri, n=5, rot=-math.pi / 2):
    pts = []
    for i in range(n * 2):
        r = ro if i % 2 == 0 else ri
        a = rot + i * math.pi / n
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def inside_poly(pts, px, py):
    n = len(pts)
    j = n - 1
    r = False
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi:
            r = not r
        j = i
    return r


# --------------------------------------------------------------------------- simgeler
def coin():
    """altın sikke: kenar halkası (üst-sol parlak, alt-sağ koyu), yüzde kabartma yıldız ve parıltı"""
    n = 18
    g = Px(n)
    cx = cy = 9.0
    star = star_pts(cx, cy + 0.2, 4.2, 1.9)

    def col(x, y):
        px, py = x + 0.5, y + 0.5
        d = math.hypot(px - cx, py - cy)
        lit = ((cx - px) + (cy - py)) / 8.0  # üst-sol +
        if d > 7.0:  # kenar
            return "#FFE27A" if lit > 0.35 else "#F2B632" if lit > -0.35 else "#9A5E10"
        if 5.4 < d <= 6.2:  # oluk
            return "#B97A16" if lit > -0.2 else "#8A5410"
        if inside_poly(star, px, py):
            return "#FFF1A8" if lit > -0.1 else "#FFD960"
        if inside_poly(star, px - 0.9, py - 0.9):  # yıldızın gölgesi (alt-sağ)
            return "#B07818"
        return "#F7C440" if lit > 0.2 else "#EBAA2C" if lit > -0.4 else "#D2901E"
    g.each(lambda x, y: math.hypot(x - cx, y - cy) <= 8.2, col)
    for x, y in ((5, 4), (4, 5), (5, 5), (6, 5), (5, 6)):
        g.set(x, y, "#FFFBE0")
    g.outline()
    return g


def gem():
    """kesme elmas: taç yüzeyleri açık, alt yüzeyler koyu, parıltı"""
    g = Px(18)
    g.poly([(2, 7), (5, 3.5), (8, 7)], "#9AF2FF")
    g.poly([(5, 3.5), (13, 3.5), (11, 7), (7, 7)], "#E4FFFF")
    g.poly([(13, 3.5), (16, 7), (10, 7)], "#56D2F4")
    g.poly([(2, 7), (9.2, 7), (9.2, 16.5)], "#3FB8EC")
    g.poly([(9, 7), (16, 7), (9, 16.5)], "#1E78C0")
    g.poly([(5.5, 7), (9, 7), (9, 16.5)], "#6AD8FA")
    g.line(2.5, 7.4, 15.5, 7.4, "#E4FFFF")
    for x, y in ((14, 1), (14, 2), (13, 2), (15, 2), (14, 3)):
        g.set(x, y, "#FFFFFF")
    g.outline()
    return g


def quest():
    """görev parşömeni: rulo kâğıt, kırmızı ünlem, alt rulo"""
    g = Px(24)
    g.rect(5, 5, 18.5, 19.5, lambda x, y: "#F0DCA8" if x < 12 else "#E2C890")
    g.rect(4, 3, 19.5, 5.5, "#C99A58")
    g.rect(4, 3, 19.5, 3.5, "#E0B470")
    g.rect(4, 19, 19.5, 21.5, "#B88648")
    g.rect(4, 19, 19.5, 19.5, "#D8A866")
    for y in (8, 10, 12, 14, 16):
        g.line(7, y + 0.5, 9.5, y + 0.5, "#C9AE7A")
        g.line(14.5, y + 0.5, 16.5, y + 0.5, "#C9AE7A")
    g.rect(10.5, 7, 13.5, 13.5, "#D63A2C")
    g.rect(10.5, 7, 11.5, 13.5, "#F06A4E")
    g.rect(10.5, 15, 13.5, 17.5, "#D63A2C")
    g.rect(10.5, 15, 11.5, 15.5, "#F06A4E")
    g.outline()
    return g


def skill():
    """yetenek ağacı: gövde, dallar ve parlayan üç düğüm"""
    g = Px(24)
    g.rect(10.5, 13, 13.5, 21.5, lambda x, y: "#8A5A30" if x < 12 else "#6A4222")
    g.rect(7, 20.5, 17, 21.5, "#6A4222")
    for (x0, y0, x1, y1) in ((12, 14, 6, 8), (12, 14, 18, 8), (12, 14, 12, 5)):
        g.line(x0, y0, x1, y1, "#7A4E2A", 2)
    for (cx, cy, c1, c2) in ((6, 8, "#FFE27A", "#E0A020"), (18, 8, "#9AF2FF", "#3FB8EC"), (12, 4.5, "#E0A8FF", "#9A50E0")):
        g.ellipse(cx, cy, 3.2, 3.2, c2)
        g.ellipse(cx - 0.6, cy - 0.6, 1.8, 1.8, c1)
        g.set(cx - 1, cy - 1, "#FFFFFF")
    g.outline()
    return g


def crate():
    """elmas sandığı: koyu ahşap, altın bantlar, mavi taşlı kilit, kapak aralığından ışık"""
    g = Px(24)
    g.rect(3, 11, 20.5, 20.5, lambda x, y: "#6A4428" if y < 16 else "#523320")
    g.each(lambda x, y: 3 <= x <= 20.5 and 4.5 <= y <= 10.5 and ((x - 12) / 9.0) ** 2 + ((y - 10.5) / 6.0) ** 2 <= 1.0,
           lambda x, y: "#7A5030" if y < 8 else "#5E3C24")
    g.rect(3, 10, 20.5, 11.5, "#8FF0FF")
    for bx in (5, 18):
        g.rect(bx, 5, bx + 1.5, 20.5, "#E8B23A")
        g.rect(bx, 5, bx + 0.5, 20.5, "#FFE07A")
    g.rect(3, 20, 20.5, 20.5, "#C88A20")
    g.rect(9.5, 12, 14.5, 17.5, "#E8B23A")
    g.rect(9.5, 12, 14.5, 12.5, "#FFE07A")
    g.poly([(12, 13), (13.8, 14.8), (12, 16.8), (10.2, 14.8)], "#5ED8FF")
    g.set(11, 14, "#E4FFFF")
    for x, y in ((12, 1), (12, 2), (7, 2), (17, 2), (8, 3), (16, 3)):
        g.set(x, y, "#8FF0FF")
    g.outline()
    return g


def bag():
    """deri sırt çantası: sap, kapak, altın toka, ön cep, dikiş"""
    g = Px(24)
    g.each(lambda x, y: 5 <= x <= 19 and 6 <= y <= 21.5 and not ((x < 6.5 or x > 17.5) and (y < 7.5 or y > 20)),
           lambda x, y: "#9A6234" if x < 9 else "#84522A" if x < 16 else "#6E4422")
    g.each(lambda x, y: 9 <= x <= 15 and 2 <= y <= 6 and not (10.5 <= x <= 13.5 and 3.5 <= y), "#5A3618")
    g.each(lambda x, y: 5 <= x <= 19 and 6 <= y <= 12.5 and not ((x < 6.5 or x > 17.5) and y < 7.5), lambda x, y: "#7A4A24" if y < 12 else "#4E2E14")
    g.rect(8, 15, 16, 20.5, lambda x, y: "#A8703C" if y < 16 else "#8E5C30")
    g.rect(10.5, 11, 13.5, 14.5, "#E8B23A")
    g.rect(11.5, 12, 12.5, 13.5, "#6E4422")
    for x in range(9, 16, 2):
        g.set(x, 19, "#C8955A")
    g.outline()
    return g


def trade():
    """takas: üstte sağa giden yeşil ok, altta sola giden altın ok"""
    g = Px(24)
    g.rect(3, 6.5, 15, 9.5, "#5CC85A")
    g.rect(3, 6.5, 15, 7.5, "#9AF09A")
    g.poly([(14, 3), (21, 8), (14, 13)], "#5CC85A")
    g.poly([(14, 3), (21, 8), (14, 8)], "#7ADC78")
    g.rect(9, 14.5, 21, 17.5, "#E8B23A")
    g.rect(9, 14.5, 21, 15.5, "#FFE07A")
    g.poly([(10, 11), (3, 16), (10, 21)], "#E8B23A")
    g.poly([(10, 11), (3, 16), (10, 16)], "#FFD060")
    g.outline()
    return g


def clan():
    """klan: altın kenarlı kalkan, kızıl alan, beyaz çapraz kılıçlar"""
    g = Px(24)
    shield = [(3.5, 3), (20.5, 3), (20.5, 12), (12, 21.5), (3.5, 12)]
    inner = [(5.5, 5), (18.5, 5), (18.5, 11.5), (12, 19), (5.5, 11.5)]
    g.poly(shield, "#E8B23A")
    g.poly(inner, lambda x, y: "#C8342A" if x < 12 else "#A0241E")
    g.line(7, 6.5, 17, 16, "#E8ECF0", 2)
    g.line(17, 6.5, 7, 16, "#C8CED6", 2)
    g.rect(6, 14.5, 8.5, 15.5, "#8A5A30")
    g.rect(15.5, 14.5, 18, 15.5, "#8A5A30")
    g.line(4, 3.5, 20, 3.5, "#FFE07A")
    g.outline()
    return g


def level():
    """seviye: altın yıldız (sağ yarısı gölgeli), altında mavi kurdele uçları"""
    g = Px(24)
    g.poly([(7, 15), (9, 15), (6.5, 22), (4.5, 20)], "#3A6AD0")
    g.poly([(15, 15), (17, 15), (19.5, 20), (17.5, 22)], "#2A50A8")
    st = star_pts(12, 10.5, 9.5, 4.2)
    g.poly(st, lambda x, y: "#FFD860" if x < 12 else "#E0A020")
    g.poly(star_pts(12, 10.5, 5.0, 2.3), lambda x, y: "#FFF1A8" if x < 12 else "#F2B632")
    g.outline()
    return g


def soldier():
    """asker sıralaması: zeytin kask, kayış ve gözlük"""
    g = Px(24)
    g.each(lambda x, y: ((x - 12) / 8.5) ** 2 + ((y - 13) / 8.0) ** 2 <= 1.0 and y <= 14.5,
           lambda x, y: "#8A9A4A" if x + y < 20 else "#6E7A3A" if x + y < 28 else "#55602C")
    g.rect(2.5, 13.5, 21.5, 15.5, "#4A5426")
    g.rect(2.5, 13.5, 21.5, 13.5, "#6E7A3A")
    g.rect(6, 9, 18, 10.5, "#3A3A34")
    g.ellipse(9, 9.8, 2.0, 1.6, "#9AE0F0")
    g.ellipse(15, 9.8, 2.0, 1.6, "#7AC0D8")
    g.set(8, 9, "#FFFFFF")
    g.line(4, 15.5, 7, 20, "#4A3A22")
    g.line(20, 15.5, 17, 20, "#4A3A22")
    g.outline()
    return g


ICONS = {
    "ic_coin": coin, "ic_gem": gem, "ic_quest": quest, "ic_magic": skill, "ic_crate": crate, "ic_bag": bag,
    "ic_trade": trade, "ic_clan": clan, "ic_level": level,
    "lb_level": level, "lb_soldiers": soldier, "lb_clan": clan,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    ims = {}
    for name, fn in ICONS.items():
        im = fn().image()
        im.save(os.path.join(OUT, name + ".png"))
        ims[name] = im
    if len(sys.argv) > 1:
        k = 6
        W = sum(i.width * k + 12 for i in ims.values()) + 12
        out = Image.new("RGBA", (W, 24 * k + 24), (34, 33, 28, 255))
        x = 12
        for im in ims.values():
            big = im.resize((im.width * k, im.height * k), Image.NEAREST)
            out.paste(big, (x, 12), big)
            x += big.width + 12
        out.save(sys.argv[1])
    print(len(ims), "simge")


if __name__ == "__main__":
    main()
