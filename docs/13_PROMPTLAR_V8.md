# Yapay Zekâ Görsel Prompt'ları (V8)

Bu paket yeni özellikler için gereken görselleri kapsar:

- Devlet üsleri: binalar, askerler (M4 ve keskin nişancı), helikopter
- Büyücü NPC ve ışınlanma kapısı
- Kişisel üs: sandıklar, yatak, çit, zemin
- Yeni bölgeler: donmuş liman, yanık orman, çöl otoyolu, bataklık, askeri hangar
- Yeni ekipman simgeleri: kask, ceket, pantolon, bot, çanta, yüzük, kolye (pelerin kaldırıldı)
- Araçlar: piksel motosiklet ve iki kişilik üstü açık araba (SUV ve bisiklet kaldırıldı), araçtan in tuşu
- Efektler: helikopter iniş tozu, mermi izi, namlu ateşi, ölümsüzlük kalkanı, ele geçirme bayrağı, ışınlanma
- Arayüz: harita üs simgeleri, ele geçirme çubuğu

Yöntem aynı:

1. Sen tek bir duruş ya da tek bir şerit (efekt) üretirsin.
2. Ben arka planı temizler, parçalara böler ve kodla oynatırım.
3. Askerler ve helikopter de kahraman gibi **tek görselden** canlanır.

---

## STİL (karakter ve nesne prompt'larının başına yapıştır)

```
high quality pixel art game sprite, crisp hard-edged pixels, 1px dark selective outline, limited dark palette
(charcoal, black, deep crimson, bone white, rust brown, muted steel, olive drab), hue-shifted shading with light from the top-left,
three-quarter view facing the lower-left, full body, nothing cropped, flat solid #00FF00 green background,
no ground, no cast shadow, no glow, no text, no frame, no watermark
```

**Negatif:**

```
blurry, anti-aliased, smooth gradient, 3d render, photo, painterly, multiple characters, cropped, text, logo, shadow, background scenery
```

**Kodla oynatabilmem için duruş kuralları (karakterler):**

- Kollar gövdeye **değmesin**, bacaklar hafif aralık olsun, iki ayak da görünsün.
- Askerlerde silah elde olsun ama **gövdenin önünü kapatmasın** (silahı ayrıca da üret, aşağıda).
- Yeşil konularda arka plan **düz macenta (#FF00FF)** olsun.
- Efektlerde arka plan **düz siyah (#000000)**, kareler arasında boşluk olsun, kutu/çerçeve **çizilmesin**.

---

## 1) DEVLET ÜSSÜ ASKERLERİ

**A1 · M4'lü Asker**

```
[STİL] a disciplined government soldier in dark olive tactical uniform, black plate carrier vest with pouches,
matte black helmet with goggles, black balaclava, knee pads, combat boots, holding an M4 carbine at low ready
with both hands, legs apart in a stable stance, arms clearly separated from the torso
```

**A2 · Keskin Nişancı**

```
[STİL] a government sniper in a ragged dark ghillie cloak over olive uniform, hood up, face hidden by a dark scarf,
holding a long bolt-action sniper rifle diagonally across the body with both hands, legs apart, slim silhouette,
arms clearly separated from the torso
```

**A3 · Silahlar ayrı (elde tutuş için)**

```
[STİL] two separate firearms side by side, horizontal, pointing right: an M4 carbine with red-dot sight, and a long bolt-action
sniper rifle with scope and bipod folded, both matte black with steel highlights, generous empty space between them
```

**A4 · Asker vurulma / ölüm duruşu (isteğe bağlı)**

```
[STİL] the same olive government soldier falling backwards, knocked off balance, rifle slipping from the hands,
one knee bent, arms out, dramatic but readable silhouette
```

---

## 2) HELİKOPTER (askerleri üsse indirir)

**H1 · Gövde**

```
[STİL] a military transport helicopter seen from a high three-quarter top-down angle, dark olive and black paint,
open side door, landing skids, tail boom with tail rotor, NO main rotor blades (drawn separately), large readable shape
```

**H2 · Pervane (ayrı)**

```
[STİL] a single helicopter main rotor seen from directly above, four long dark grey blades from a central hub,
perfectly centered, flat solid #00FF00 background
```

**H3 · İniş tozu (efekt, siyah arka plan)**

```
pixel art effect animation strip, 6 frames left to right with gaps, a circular dust and debris shockwave blown outward
by helicopter rotor wash, brown-grey dust, small rocks, frames grow then fade, flat solid #000000 background, no boxes, no text
```

---

## 3) DEVLET ÜSSÜ YAPILARI

**Ü1 · Komuta Binası**

```
[STİL] isometric military command bunker, reinforced concrete walls, sandbags around the entrance, radio antenna,
camouflage net on the roof, small floodlights, olive and grey, readable at small size
```

**Ü2 · Gözetleme Kulesi**

```
[STİL] isometric tall wooden and steel watchtower with a sandbagged platform, ladder, searchlight on top, olive and rust
```

**Ü3 · Barikat ve Tel Örgü**

```
[STİL] isometric military barricade set: concrete jersey barrier, sandbag wall, razor wire fence segment, steel hedgehog,
each piece separated with empty space between
```

**Ü4 · Helikopter Pisti**

```
[STİL] isometric circular helipad on the ground, dark asphalt with a big white H and yellow edge markings, small edge lights,
viewed from above at a slight angle
```

**Ü5 · Ele Geçirme Bayrağı (3 renk)**

```
[STİL] three flag poles side by side with waving cloth flags: a dark olive government flag with a white star,
a neutral grey flag, and a white flag with a blank emblem area (for clan colors), separated with empty space
```

**Ü6 · Ödül Sandığı (üssün ürettiği eşya)**

```
[STİL] isometric military supply crate with stencil markings, steel corners, slightly open lid with a faint glow inside
```

---

## 4) BÜYÜCÜ NPC VE IŞINLANMA

**B1 · Büyücü**

```
[STİL] an old wandering wizard survivor, long tattered dark purple robe with rune embroidery, grey beard,
wooden staff topped with a glowing cyan crystal held in one hand away from the body, small satchel, calm wise face,
feet visible
```

**B2 · Işınlanma Kapısı**

```
[STİL] isometric ancient stone teleport circle on the ground with carved runes and four short broken pillars,
cyan crystals on the pillars, readable at small size
```

**B3 · Işınlanma efekti (siyah arka plan)**

```
pixel art effect animation strip, 8 frames left to right with gaps, a cyan and white magic teleport beam rising from a rune circle,
sparks spiral upward, peak in the middle frames then fade, flat solid #000000 background, no boxes, no text
```

---

## 5) KİŞİSEL ÜS

**K1 · Sandıklar (3 seviye)**

```
[STİL] three isometric storage chests side by side: a small wooden chest, a reinforced iron-banded chest,
and a large steel military locker chest, all closed, separated with empty space
```

**K2 · Açık sandıklar (aynı üçü, kapak açık)**

```
[STİL] the same three isometric storage chests with lids open, items faintly visible inside, separated with empty space
```

**K3 · Üs eşyaları**

```
[STİL] isometric survivor base furniture set: a camp bed with blanket, a small wood stove, a wooden fence segment,
a reinforced gate, a weapon rack, a rain barrel, each separated with empty space
```

**K4 · Üs zemini (döşenebilir doku)**

```
seamless tileable pixel art ground texture, packed dirt with scattered gravel and old wooden planks, muted brown and grey,
top-down, 64x64 tile feel, no objects, no shadows
```

---

## 6) YENİ BÖLGELER (zemin dokuları + birer simge yapı)

Her bölge için iki görsel: **döşenebilir zemin** ve **simge yapı**.

**R1 · Donmuş Liman**

```
seamless tileable pixel art ground texture, cracked frozen concrete dock with patches of snow and ice, cold blue-grey, top-down
```

```
[STİL] isometric rusted shipping container stack with snow on top and icicles, a broken harbor crane arm beside it
```

**R2 · Yanık Orman**

```
seamless tileable pixel art ground texture, burnt forest floor, black ash, charred twigs, glowing ember spots, top-down
```

```
[STİL] isometric burnt dead tree with charred black bark and a few glowing embers, separate small burnt stump beside it
```

**R3 · Çöl Otoyolu**

```
seamless tileable pixel art ground texture, sun-bleached cracked asphalt with sand drifts, faded yellow lane paint, top-down
```

```
[STİL] isometric abandoned rusted car wreck half buried in sand, flat tires, broken windows
```

**R4 · Bataklık** (macenta arka plan)

```
seamless tileable pixel art ground texture, murky swamp mud with dark green water puddles and reeds, top-down
```

```
[STİL] isometric rotten wooden swamp shack on stilts with hanging moss, flat solid #FF00FF background
```

**R5 · Askeri Hangar**

```
[STİL] isometric large military aircraft hangar with a half-open rolling door, corrugated metal, olive paint, warning stripes
```

---

## 7) YENİ EKİPMAN SİMGELERİ (çanta slotları)

Hepsi tek tek ya da yan yana boşluklu üretilebilir.

Slotlar: Kask, Ceket, Pantolon, Bot, Silah, Çanta, Yüzük, Kolye (pelerin yok).

```
pixel art inventory icon set, 7 separate items in a row with empty space between them, each centered and readable at 48px,
crisp 1px dark outline, flat solid #00FF00 background: a dented steel helmet, a worn leather jacket, a pair of reinforced
cargo trousers with knee pads, a pair of combat boots, a large survival backpack, a silver ring with a small red gem,
a bone-and-silver necklace with a pendant
```

**Kalite seviyeleri için (isteğe bağlı):** aynı seti "rusty / reinforced / military" diye üç kez üret.

---

## 8) EFEKTLER (hepsi siyah arka plan, kutu yok)

**E1 · Namlu ateşi**

```
pixel art effect animation strip, 4 frames left to right with gaps, a small rifle muzzle flash pointing right,
bright yellow-white core with orange petals, flat solid #000000 background, no boxes, no text
```

**E2 · Mermi izi**

```
pixel art effect, a single thin glowing bullet tracer streak pointing right, yellow-white fading to orange at the tail,
flat solid #000000 background
```

**E3 · Ölümsüzlük kalkanı (asker doğunca 5 sn)**

```
pixel art effect animation strip, 6 frames left to right with gaps, a translucent pale gold dome shield around a figure,
hexagon pattern shimmer, looping, flat solid #000000 background, no boxes, no text
```

**E4 · Ele geçirme halkası**

```
pixel art effect animation strip, 6 frames left to right with gaps, a large flat elliptical ground ring that fills clockwise,
white-gold glow, small rising particles, flat solid #000000 background, no boxes, no text
```

**E5 · Iskalama (mermi yere çarpar)**

```
pixel art effect animation strip, 4 frames left to right with gaps, a bullet impact on dirt: small dust puff and flying pebbles,
flat solid #000000 background, no boxes, no text
```

---

## 9) ARAYÜZ

**U1 · Harita üs simgeleri**

```
pixel art map icon set, 4 separate round map markers with empty space between them, 64px feel, crisp outline:
a government base marker (olive shield with white star), a clan-owned base marker (white shield, blank center for color),
a contested base marker (red shield with crossed rifles), an unknown base marker (grey shield with question mark),
flat solid #00FF00 background
```

**U2 · Çanta sekme simgeleri (4 çanta bölümü)**

```
pixel art UI tab icon set, 4 separate small bag icons in a row with empty space: a small pouch, a satchel,
a backpack, a large military rucksack, readable at 40px, flat solid #00FF00 background
```

**U3 · Ele geçirme / üs bilgi paneli çerçevesi**

```
pixel art UI panel frame, dark riveted steel with olive military trim and yellow-black hazard stripe accents at the corners,
empty dark center, wide rectangle, flat solid #00FF00 background outside the frame
```

---

## 10) ARAÇLAR (piksel, kodla sürülecek)

Kural: araçlar **yandan 3/4 açıyla, sola bakar**; tekerlekler ayrı katman gibi net görünsün (kodla döndüreceğim).
Sürücü **çizilmesin** (karakteri ben oturturum).

**V1 · Motosiklet**

```
[STİL] a rugged post-apocalyptic dirt motorcycle, matte black and rust red, knobby tires, small headlight, exhaust pipe,
saddle bags, side view three-quarter facing left, no rider, wheels clearly visible
```

**V2 · İki kişilik üstü açık araba (sürücü ve yolcu dışarıdan görünecek)**

```
[STİL] a two-seat open-top convertible survival car, no roof, low windshield frame, two empty seats clearly visible from outside,
roll bar behind the seats, armored bumper, spare tire on the back, dark olive and rust paint, side view three-quarter facing left,
no driver, no passenger
```

**V3 · Tekerlek (ayrı)**

```
[STİL] a single off-road car tire with a steel rim seen exactly from the side, perfectly circular, centered
```

**V4 · Araç izleri ve yol efektleri (siyah arka plan, kutu yok)**

```
pixel art effect animation strip, 5 frames left to right with gaps, a tire kicking up brown dirt and pebbles behind a wheel,
flat solid #000000 background, no boxes, no text
```
```
pixel art effect animation strip, 5 frames left to right with gaps, a tire spraying white snow powder, flat solid #000000 background
```
```
pixel art effect animation strip, 5 frames left to right with gaps, a tire splashing dark green swamp water droplets, flat solid #000000 background
```
```
pixel art effect animation strip, 5 frames left to right with gaps, fine pale sand blowing behind a wheel, flat solid #000000 background
```

## 11) HUD · ARAÇTAN İN TUŞU

Mevcut tuşlarla aynı çerçeve (gri metal halka, camgöbeği iç halka, koyu iç). İçinde **kapı açılıp dışarı çıkan ok** olsun ki "in" olduğu belli olsun.

```
pixel art round game HUD button, thick grey metal outer ring with small bolts, glowing cyan inner ring, dark navy center,
white icon in the center: a car door swinging open with a bold arrow pointing out and down-left (exit vehicle),
crisp pixels, flat solid #00FF00 background, single button centered
```

Aynı stille **Araca Bin** (direksiyon + içeri ok) ve **Battle Pass** (taç + şerit) tuşları:

```
pixel art round game HUD button, same metal and cyan ring style, white icon: a steering wheel with a bold arrow pointing in (enter vehicle)
```
```
pixel art round game HUD button, same metal and cyan ring style, gold icon: a crown above a ribbon banner with stars (battle pass)
```
