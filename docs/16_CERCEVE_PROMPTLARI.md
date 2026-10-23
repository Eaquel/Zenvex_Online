# Profil Çerçevesi Prompt'ları (V11.2)

Oyundaki 6 çerçeve şu an kodla çiziliyor. Çizilmiş bir görsel gelince oyun **otomatik olarak onu kullanır**:
gövde (bant, taç, mücevherler, dikenler) görselden gelir, hareketli efektler ve rozet yazısı kodla üstüne çizilmeye devam eder.

| Kimlik | Ad | Nasıl açılır | Dosya |
|--------|----|--------------|-------|
| `iron` | Demir (varsayılan) | herkes | `frame_iron.png` |
| `lv10` | Bronz Savaşçı | Sv 10 | `frame_lv10.png` |
| `lv25` | Gümüş Muhafız | Sv 25 | `frame_lv25.png` |
| `lv50` | Efsanevi Alev | Sv 50 | `frame_lv50.png` |
| `season1` | Sezon #1 | Sezon #1 bileti | `frame_season1.png` |
| `blood` | Kanlı Ay | Kanlı Ay etkinliğinde ilk 10 | `frame_blood.png` |

---

## Ölçü kuralları (hepsi için aynı — oyunda hizalama buna göre)

```
┌──────────────────────────────┐  ← tuval 1:1 kare (1024×1024 önerilir), her yer düz #00FF00 yeşil
│    (taç / ay / alev payı)    │
│   ┌──────────────────────┐   │  ← çerçevenin dış kenarı: tuvalin ~%80'i, ortada
│   │ ┌──────────────────┐ │   │
│   │ │                  │ │   │  ← iç pencere: çerçevenin %80'i, BOŞ, düz #00FF00
│   │ │   (resim alanı)  │ │   │     (oyuncu fotoğrafı buraya gelir)
│   │ │                  │ │   │
│   │ └──────────────────┘ │   │  ← bant kalınlığı: çerçeve genişliğinin ~%10'u
│   └───────[rozet]────────┘   │  ← alt orta: yazısız boş plaka (sayıyı oyun yazar)
└──────────────────────────────┘
```

- Çerçeve **dört kenarda da kapalı** olmalı (iç pencere dışarıyla birleşmemeli), **sağ-sol simetrik**, düz önden bakış.
- İç pencere ve dış zemin **düz #00FF00** (gölge, ışık, desen yok). Çerçevede saf yeşil renk **kullanılmamalı**.
- Taç / ay / alev / diken gibi süsler dış kenardan **en fazla bant kalınlığı kadar** taşabilir, tuvalden taşmamalı.
- **Yazı, sayı, harf olmasın** — "10", "25", "50", "S1" rozet yazılarını oyun kendisi keskin piksel yazıyla çiziyor.
- İçine **karakter / yüz / portre çizilmesin**.

---

## STİL (her prompt'un başına yapıştır)

**A) Piksel sanat (önerilen — oyunun geri kalanıyla aynı):**

```
high quality pixel art game UI asset, an ornate square avatar frame (profile picture border) seen straight on,
perfectly symmetrical left and right, crisp hard-edged pixels on a 128x128 pixel grid upscaled, 1px dark outline,
hue-shifted shading with light from the top-left, rich small details, metallic highlights,
the frame band is about one tenth of the frame width thick, the frame is closed on all four sides,
the inside of the frame is an EMPTY square window filled with flat solid #00FF00 green and it takes up the central 80% of the frame,
the frame takes up the central 80% of the canvas, everything outside the frame is also flat solid #00FF00 green,
1:1 square canvas, centered, no text, no numbers, no letters, no portrait, no character inside
```

**B) İnce detaylı (boyalı, "HD") — piksel istemezsen:**

```
highly detailed premium mobile RPG game UI asset, an ornate square avatar frame (profile picture border) seen straight on,
perfectly symmetrical left and right, sharp clean edges, hand-painted metal and gem materials, strong rim light from the top-left,
the frame band is about one tenth of the frame width thick, the frame is closed on all four sides,
the inside of the frame is an EMPTY square window filled with flat solid #00FF00 green and it takes up the central 80% of the frame,
the frame takes up the central 80% of the canvas, everything outside the frame is also flat solid #00FF00 green,
1:1 square canvas, centered, no text, no numbers, no letters, no portrait, no character inside
```

**Negatif (hepsinde):**

```
text, letters, numbers, watermark, logo, signature, portrait, face, person, character inside the frame, picture inside the frame,
pattern or gradient inside the window, green colored parts, glow or haze spilling onto the background, drop shadow,
perspective, tilted, rotated, asymmetric, cropped, cut off, round frame, circle frame, blurry, jpeg artifacts, 3d render, photo
```

---

## 1) `frame_iron` — Demir (varsayılan)

Sade ama temiz; diğerlerinin yanında "başlangıç" hissi vermeli.

```
simple dark wrought-iron frame made of four riveted iron plates, a round iron rivet on each corner and two small rivets on each side,
worn gunmetal grey with subtle scratches and a thin brighter bevel on the inner edge, humble and plain, no gems, no extra ornaments,
a small blank iron plate at the bottom center
```

Kodla üstüne: yok (sabit).

## 2) `frame_lv10` — Bronz Savaşçı (Sv 10)

```
bronze warrior frame: hammered bronze band with an engraved celtic knotwork line running along it, round bronze rivets along each side,
a small round bronze shield boss on each of the four corners, two crossed short swords behind a round bronze boss at the top center
slightly overlapping the top edge, a blank bronze heater-shield plaque at the bottom center (no text),
warm copper highlights, dark brown patina in the shadows, sturdy and battle-worn
```

Kodla üstüne: bant boyunca dolaşan sıcak ışık, alt plakada **"10"**.

## 3) `frame_lv25` — Gümüş Muhafız (Sv 25)

```
silver guardian frame: polished silver band with fine engraved filigree scrollwork, a royal silver crest at the top center
overlapping the top edge holding one large faceted deep-blue sapphire, a diamond-shaped faceted blue sapphire set into each of the
four corners, small silver laurel leaves along both sides, a blank silver heater-shield plaque at the bottom center (no text),
cool white highlights and blue-grey shadows, noble, elegant and clearly more valuable than bronze
```

Kodla üstüne: tepe safirinde ve dört köşede nabız gibi mavi parıltı, bant üzerinde dolaşan yıldız ışıltıları, plakada **"25"**.

> Not: safirler tepe ortasında ve tam köşelerde olsun — oyundaki parıltı tam o noktalara çiziliyor.

## 4) `frame_lv50` — Efsanevi Alev (Sv 50)

```
legendary flame frame: thick ornate gold band with deep engraved royal patterns and small inset rubies along the sides,
a large golden crown with a red ruby in its center sitting on the top center and overlapping the top edge,
a gold diamond-shaped ornament with a red ruby on each of the four corners,
stylised golden-orange flames rising from the top edge and the upper half of both sides behind the band
(orange, yellow and white-hot tips, absolutely no green), a blank gold heater-shield plaque at the bottom center (no text),
extremely luxurious, epic legendary rarity, the most impressive frame of all
```

Kodla üstüne: çerçevenin arkasından titreyerek yükselen alevler, tacın etrafında altın ışıma, yükselen kıvılcımlar, plakada **"50"**.

> Taç tam tepe ortasında olsun (oyundaki ışıma oraya çiziliyor).

## 5) `frame_season1` — Sezon #1

```
season one battle pass frame: dark gunmetal sci-fi armor plate band with panel lines and small bolts,
a thin glowing cyan neon trim line along the inner edge, purple energy accents in the grooves,
a hexagonal metal node on each of the four corners with a glowing cyan core,
small angular tech fins on the two upper corners, a blank dark hexagonal badge with a purple rim at the bottom center (no text),
futuristic premium esports style, dark with neon cyan and violet (no green)
```

Kodla üstüne: mor dış ışıma (nabız), bant boyunca kuyruklu camgöbeği enerji topu, plakada **"S1"**.

> Camgöbeği yeşile kaçmasın (#3AF0E0 gibi mavimsi camgöbeği), yoksa ayıklarken silinir.

## 6) `frame_blood` — Kanlı Ay (Kanlı Ay etkinliği ilk 10)

```
blood moon frame: dark crimson wrought-iron band, sharp black thorn spikes pointing outward along all four outer edges,
thick blood dripping from the top edge down onto the band, a large glowing blood-red full moon with dark craters
at the top center overlapping the top edge, a small bone-white skull ornament on each of the two bottom corners,
gothic horror vampire style, deep reds and blacks, no bats, no text
```

Kodla üstüne: kırmızı dış ışıma (nabız), ayın etrafında parlayan hale, ayın iki yanında **kanat çırpan yarasalar**
(bu yüzden görselde yarasa olmasın), yanlardan süzülen kan damlaları, üst banttan sarkıp kısalan kan.

> Ay tam tepe ortasında, tepe bandının üstüne oturmuş olsun.

---

## Teslim

1. Görseli **1:1 kare**, en az 1024×1024 al; dosya adını tablodaki gibi ver (ör. `frame_lv50.png`).
2. Bana gönder ya da kendin şu komutla oyuna al:

```
python3 tools/pixel/frame_import.py frame_lv50.png lv50        # piksel sanat (A)
python3 tools/pixel/frame_import.py frame_lv50.png lv50 hd     # ince detaylı (B)
```

Araç yeşili siler, iç pencereyi bulup tam ortalar, resim alanına hizalar ve `android/app/src/main/assets/ui/frame_<id>.png`
olarak kaydeder. Oyun bir sonraki derlemede onu kullanır; görsel yoksa kodla çizilen çerçeveye döner.

**Sık hatalar:** iç pencere yeşil değil de desenli/gölgeli → resim görünmez; çerçeve bir kenarda açık → araç "boşluk dışarıya açık"
der; yuvarlak çerçeve → köşelerde resim taşar; yazı/sayı var → oyunun rozet yazısıyla üst üste biner.
