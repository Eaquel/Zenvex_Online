# Mağaza ve VIP Görselleri — Yepyeni Prompt'lar

Mağazada şu an görsel yok: VIP rozetleri, elmas paketleri, paketler, kartlar ve biletler koddan çizilen yedek simgelerle
(kalkan + "V3", düz simge) görünüyor. Aşağıdaki görseller gelince oyun **hiçbir kod değişikliği olmadan** onları kullanır
(dosya adı = kodun aradığı ad). Hepsini tek bir zip olarak gönder; yeşil zemini ben temizler, boyutlandırır ve yerleştiririm.

---

## 0. Ortak kurallar (her prompt'un sonuna ekle)

**Stil satırı (son eşya ve bina görsellerinle aynı):**
```
high quality detailed pixel-art style game icon, crisp edges, bold dark outline, rich hue-shifted shading, light from the
top-left, dark post-apocalyptic survival game, premium mobile game shop art, centered single object, no text, no letters,
no numbers, no watermark, no frame, no shadow on the background, flat solid #00FF00 green background
```

- Zemin **düz #00FF00 yeşil** (nesnenin içinde saf yeşil olmasın; yeşil mücevher gerekiyorsa zümrüt koyu yeşil olsun).
- **Yazı, rakam, harf yok.** "V5", "x600", "%50" gibi yazıları ben oyunda çizerim (çeviri 10 dilde).
- En az **1024×1024** (kare), bilet/kartlar **1024×1024** içinde dik dursun. Nesne görselin ~%80'ini kaplasın, kesilmesin.
- Aynı aileden olanları **aynı oturumda, aynı tohum (seed) ailesiyle** üret; büyüklük ve kamera açısı tutarlı olsun.

---

## 1. VIP rozetleri (11 dosya: `vip_badge_1` … `vip_badge_11`)

Her seviye bir öncekinden **belirgin şekilde daha görkemli** olmalı: malzeme, kanat/taç eki ve ışık artar.
Ortak şablon:

```
a VIP rank emblem for a mobile survival game: a heraldic shield-shaped badge, {MALZEME}, {SÜS}, a large empty smooth
center plate (left blank for a number), front view, symmetrical, {IŞIK}. [STİL]
```

| Dosya | Seviye | {MALZEME} | {SÜS} | {IŞIK} |
|---|---|---|---|---|
| `vip_badge_1` | V1 | battered dark iron with rivets | a simple riveted rim | no glow |
| `vip_badge_2` | V2 | brushed steel | two small rivet studs and a thin engraved border | faint cold highlight |
| `vip_badge_3` | V3 | polished bronze | small ember-orange gem at the top, a pair of tiny burnt feather wings at the sides | warm ember glow |
| `vip_badge_4` | V4 | shining copper and bronze | crossed swords behind the shield | warm rim light |
| `vip_badge_5` | V5 | bright silver | laurel branches on both sides, a small sapphire at the top | soft blue sparkle |
| `vip_badge_6` | V6 | silver with crimson enamel | red dragon-membrane wings with golden bone tips spread behind the shield | crimson glow |
| `vip_badge_7` | V7 | gold | laurel wreath and a small crown on top | golden sparkle |
| `vip_badge_8` | V8 | gold with emerald inlays | ornate filigree, three dark emeralds, small wings | green-gold glow |
| `vip_badge_9` | V9 | black obsidian plates with glowing lava cracks and gold edges | obsidian titan wings behind the shield, horns at the top | molten orange glow |
| `vip_badge_10` | V10 | royal purple amethyst and platinum | a jeweled crown, large feathered wings, star dust | violet aura |
| `vip_badge_11` | V11 | white diamond and platinum | radiant halo ring, large angelic golden-tipped wings, a crown with a big diamond | bright white-gold radiant aura |

**İsteğe bağlı** `vip_badge_0` (VIP değil): `a plain worn grey stone shield emblem with an empty center, no decoration, dull`.

---

## 2. Elmas paketleri (6 dosya: `gems_1` … `gems_6`)

Fiyat arttıkça yığın büyür; aynı mavi-camgöbeği elmas tasarımı her görselde aynı olsun.

```
a pile of glowing cyan-blue cut diamonds for a mobile game shop, {MİKTAR}, three-quarter view, sparkling. [STİL]
```

| Dosya | Paket | {MİKTAR} |
|---|---|---|
| `gems_1` | 60 elmas | a small handful of 5 diamonds |
| `gems_2` | 220 elmas | a small pile of about 12 diamonds |
| `gems_3` | 600 elmas | a leather pouch overflowing with diamonds |
| `gems_4` | 1300 elmas | a wooden crate with iron corners full of diamonds |
| `gems_5` | 2800 elmas | an open iron treasure chest heaped with diamonds |
| `gems_6` | 6000 elmas | a huge mountain of diamonds with a golden open vault chest, light rays from above |

---

## 3. Paketler (3 dosya: `pack_start`, `pack_growth`, `pack_war`)

```
a premium bundle for a post-apocalyptic zombie survival game shop: {İÇERİK}, items arranged as a tight group, three-quarter
view. [STİL]
```

| Dosya | Paket | {İÇERİK} |
|---|---|---|
| `pack_start` | Başlangıç Paketi | a canvas survival backpack with a small pile of gold coins, a few cyan diamonds and a first-aid kit leaning on it |
| `pack_growth` | Gelişim Paketi | an olive military supply crate with the lid open, glowing green level-up arrow emblem shape made of light (no text), coins and diamonds |
| `pack_war` | Savaş Paketi | a black weapon case open with an assault rifle, ammo boxes, a grenade and a scythe blade, red emergency light glow |

---

## 4. Abonelik kartları (2 dosya: `card_week`, `card_month`)

```
a premium subscription card for a mobile game shop: a vertical metal card standing upright, {KART}, ornate frame, a big
empty center area, slight tilt. [STİL]
```

| Dosya | {KART} |
|---|---|
| `card_week` | brushed silver card with a cyan diamond embedded at the top and seven small notches along the side |
| `card_month` | heavy gold card with a large cyan diamond at the top, a small crown and thirty small engraved dots along the side, golden glow |

---

## 5. Biletler (2 dosya: `ticket_vip`, `ticket_premium`)

```
a premium season ticket for a mobile game: an ornate torn-edge ticket floating at a slight angle, {BİLET}. [STİL]
```

| Dosya | {BİLET} |
|---|---|
| `ticket_vip` | violet and silver ticket with a stylized wing emblem and star dust, purple glow (Sezon Bileti VIP) |
| `ticket_premium` | crimson and gold ticket with a crown emblem and ember sparks, golden glow (Sezon Bileti Premium) |

---

## 6. Mağaza arka planı (1 dosya: `shop_bg`, **yeşil zemin YOK**)

```
wide background illustration for a mobile game shop screen, interior of a fortified survivor black-market bunker: concrete
walls with metal shelves of weapons, supply crates, a glowing neon-free warm lamp light, a wooden counter in the center,
hanging tarps, dark moody post-apocalyptic atmosphere, the center area darker and calm (UI will be drawn on top), painterly
pixel-art style, 2400x1080, no characters, no text, no letters
```

---

## 7. Mağaza sekme simgeleri (isteğe bağlı, 6 dosya: `shoptab_0` … `shoptab_5`)

Sıra: Öne Çıkan, Elmas, Paketler, Kartlar, VIP, Sezon Bileti. Gelirse sekmelerde yedek simgelerin yerine geçer.

```
a small square game UI icon of {SİMGE}. [STİL]
```

| Dosya | {SİMGE} |
|---|---|
| `shoptab_0` | a glowing golden star |
| `shoptab_1` | a single cyan cut diamond |
| `shoptab_2` | a closed wooden supply crate with a golden ribbon |
| `shoptab_3` | a stack of two metal subscription cards with a calendar ring binding |
| `shoptab_4` | a golden crown with small wings |
| `shoptab_5` | a torn ticket with a star emblem |

---

## 8. Teslim

1. Tüm dosyaları adlarıyla (`vip_badge_1.png` …) tek zip yap; JPG de olur, PNG daha iyi.
2. Bana gönder; yeşil zemini temizleyip `android/app/src/main/assets/ui/` altına uygun boyutta koyarım.
3. Toplam: **11 rozet + 6 elmas + 3 paket + 2 kart + 2 bilet + 1 arka plan (+1 + 6 isteğe bağlı) = 25–32 görsel.**
