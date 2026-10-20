# Yapay Zekâ Görsel Prompt'ları (V7)

Bu paket şunları kapsar:

- Zombiler ve Titan'ın 3D'den piksel karaktere geçişi
- Bıçak ve katana
- Rehber NPC
- Yeni yerler
- Efektler
- Tuşlar ve HUD simgeleri
- Beceri simgeleri
- Üretim ekranı
- Bakım ekranı
- Logo ve yükleme ekranı

Yöntem V5 ile aynı:

1. Sen tek bir duruş görseli üretirsin.
2. Ben ızgarayı ve arka planı temizlerim.
3. Parçalara bölüp kodla oynatırım (hero ve köpekte yaptığımız gibi).

## Neden zombileri yeniden üretiyoruz?

Şu anki zombiler 3D modelden 8 yön × 26 kare olarak çizilmiş durumda. Her tür için 208 görsel var ve atlasın yarısından fazlasını bunlar kaplıyor.

Kahraman ve köpekteki yöntemle her tür için **tek görsel** yeter. Kalan her şeyi kod üretir:

- Yürüme, saldırı, ölüm ve vurulma animasyonları
- Ayna (sağ-sol yön)

Böylece APK'da zombilerin kapladığı yer yaklaşık **%90 azalır**, görüntü kalitesi de kahramanla aynı piksel stiline gelir.

---

## STİL (karakter ve nesne prompt'larının başına yapıştır)

```
high quality pixel art game sprite, crisp hard-edged pixels, 1px dark selective outline, limited dark palette
(charcoal, black, deep crimson, bone white, rust brown, muted steel), hue-shifted shading with light from the top-left,
three-quarter view facing the lower-left, full body, nothing cropped, flat solid #00FF00 green background,
no ground, no cast shadow, no glow, no text, no frame, no watermark
```

**Negatif:**

```
blurry, anti-aliased, smooth gradient, 3d render, photo, painterly, multiple characters, cropped, text, logo, shadow, background scenery
```

**Kodla oynatabilmem için duruş kuralları:**

- Kollar gövdeye **değmesin**.
- Bacaklar hafif aralık olsun.
- İki ayak da görünsün.
- Elinde silah olmasın.

**Arka plan:**

- Yeşil konularda (zehirli zombi, zehir) arka planı **düz macenta (#FF00FF)** yap.
- Efektlerde arka plan **düz siyah (#000000)** olsun. Ben siyahı saydamlığa çeviririm ve efekt ışıyarak eklenir.

---

## 1) ZOMBİLER — tek duruş, kodla canlanacak

**Z1 · Aylak (yürüyen zombi)**

```
[STİL] a shambling rotten male zombie in torn grey office shirt and dark trousers, pale green-grey skin with dark veins,
sunken glowing red eyes, slightly hunched, arms raised forward and apart from the body, legs apart, bare feet,
tattered clothing edges
```

**Z2 · Koşucu**

```
[STİL] a lean fast runner zombie in a ripped red hoodie and jogging pants, thin bony limbs, wild dark hair,
mouth wide open, leaning forward aggressively, arms back and apart from the body, legs in a wide stride, both feet visible
```

**Z3 · Zehirli** — arka plan **#FF00FF**:

```
[STİL but background flat solid #FF00FF magenta] a bloated toxic zombie with sickly yellow-green swollen skin,
glowing green pustules and dripping green slime, torn hazmat suit remains, gas mask hanging from the neck,
arms apart from the body, legs apart
```

**Z4 · Karanlık** (geceleri ışınlanan):

```
[STİL] a tall gaunt shadow zombie, almost black skin with purple cracks of light, long thin fingers,
ragged black cloak strips, two violet glowing eyes, floating wisps at the feet, arms apart, legs apart
```

**Z5 · Tank** — kahramandan 1,4 kat büyük:

```
[STİL] a huge hulking brute zombie, massive shoulders and fists, one arm bigger than the other, metal plates
and chains stuck into grey flesh, torn construction worker overalls, heavy boots, arms apart from the body,
legs wide apart, menacing
```

**Z6 · Gizemli** (kopyasını çıkaran):

```
[STİL] a mysterious faceless zombie in a long dirty white lab coat, smooth blank face with a single vertical glowing
cyan slit, cracked porcelain-like skin, arms apart, legs apart
```

**Z7 · TİTAN (boss)** — kahramandan 2,4 kat büyük. Tek görsel yeter; boyutunu ben ayarlarım.

```
[STİL] a colossal titan zombie boss, gigantic muscular body of fused corpses, exposed ribcage glowing orange inside,
bone spikes along the spine and shoulders, one huge clawed hand and one hand fused with rusted metal debris,
burning orange eyes, broken chains on the wrists, arms apart from the body, legs wide apart, epic and terrifying
```

**Z8 · Titan parçaları** — kollar tek başına oynasın diye ayrıca üretilir. Ben bunu Z7'nin üstüne takarım.

```
[STİL] the same titan zombie boss right arm alone, raised and clenched into a fist, isolated, same colors and style
```

> Not: Zombi köpeği (Z-köpek) zaten var, yeniden üretme.

---

## 2) SİLAHLAR — eşya simgesi + elde tutulan görsel

**W1 · Bıçak**

```
[STİL but side view, horizontal, blade pointing right] a survival combat knife, dark steel blade with a bright edge
highlight, black wrapped grip with a red cord, small guard, isolated
```

**W2 · Katana**

```
[STİL but side view, horizontal, blade pointing right] a katana sword, long slightly curved polished steel blade
with a wavy hamon line, golden round tsuba guard, black and crimson diamond-wrapped handle, isolated
```

**W3 · Kınında katana** (sırtta asılı görünüm için, isteğe bağlı):

```
[STİL but side view, diagonal] the same katana inside a black lacquered scabbard with a red cord, isolated
```

---

## 3) NPC ve YERLER

**N1 · Rehber Deniz** — öğretici NPC; şimdilik asker görselini kullanıyor:

```
[STİL] a friendly young female survivor guide, short dark hair with a red bandana, green military jacket with patches,
fingerless gloves, backpack with a rolled map, binoculars on the chest, one hand raised in a welcoming wave,
other arm apart from the body, legs apart, confident smile
```

**L1 · Benzin istasyonu binası**

```
[STİL but three-quarter top-down building view] a small abandoned gas station building with a cracked glass shop
front, faded red and white canopy sign without letters, rusty roof, boarded window, isolated
```

**L2 · Gizemli ağaç** — haritadaki görev yeri:

```
[STİL but tree, front view] a magical ancient autumn oak tree with glowing orange-gold leaves, faint floating light
motes, twisted trunk with a glowing rune carved into the bark, ring of small glowing mushrooms at the roots, isolated
```

**L3 · Zehirli kaynak** — arka plan **#FF00FF**:

```
[STİL but top-down 3/4 view, background flat solid #FF00FF] a small toxic spring pond with glowing green water,
bubbling, rusty leaking barrels at the edge, dead reeds, isolated
```

**L4 · Sahra hastanesi çadırı**

```
[STİL but three-quarter top-down view] a military field hospital tent, olive canvas with a red cross,
open flap showing a cot inside, ropes and stakes, isolated
```

---

## 4) EFEKTLER — siyah arka plan, ızgara sayfası

Ben her kareyi keser, siyahı saydam yapar ve ışık olarak eklerim. Prompt'a şunu ekle:

`"sprite sheet, N frames in one row, evenly spaced, same size, on solid pure black background"`

**E1 · Bıçak kesiği** (4 kare)

```
pixel art slash effect sprite sheet, 4 frames in one row on solid pure black background, a fast short white-blue
curved blade slash arc growing then fading, crisp pixels, glowing edge, game VFX
```

**E2 · Katana kesiği** (5 kare, daha geniş yay)

```
pixel art slash effect sprite sheet, 5 frames in one row on solid pure black background, a wide crescent katana
slash arc with a crimson-white glowing edge and trailing sparks, crisp pixels, game VFX
```

**E3 · Kan sıçraması** (5 kare)

```
pixel art blood splash effect sprite sheet, 5 frames in one row on solid pure black background, dark red blood burst
with droplets flying outward then dripping, crisp pixels
```

**E4 · Vuruş kıvılcımı** (4 kare)

```
pixel art hit spark impact sprite sheet, 4 frames in one row on solid pure black background, white-yellow star burst
impact with small sparks, crisp pixels
```

**E5 · İyileşme** (6 kare, döngü)

```
pixel art healing aura sprite sheet, 6 frames in one row on solid pure black background, soft green rising
plus-shaped particles and a glowing ring on the ground, loopable, crisp pixels
```

**E6 · Hız iksiri halkası** (6 kare, döngü)

```
pixel art speed buff aura sprite sheet, 6 frames in one row on solid pure black background, cyan wind streaks
circling around the feet, loopable, crisp pixels
```

**E7 · Güç iksiri halkası** (6 kare, döngü)

```
pixel art power buff aura sprite sheet, 6 frames in one row on solid pure black background, orange flames flickering
in a ring on the ground, loopable, crisp pixels
```

**E8 · Seviye atlama** (8 kare)

```
pixel art level up burst sprite sheet, 8 frames in one row on solid pure black background, golden light pillar
rising with sparkles and a star burst, crisp pixels
```

**E9 · Tanrıların yıldırımı** (6 kare, dikey)

```
pixel art lightning strike sprite sheet, 6 frames in one row on solid pure black background, tall vertical jagged
white-blue lightning bolt hitting the ground with an impact flash, crisp pixels
```

**E10 · Zehir bulutu** — macenta arka plan, 6 kare, döngü:

```
pixel art toxic gas cloud sprite sheet, 6 frames in one row on solid #FF00FF magenta background,
swirling green poison cloud with bubbles, loopable, crisp pixels
```

**E11 · Işınlanma** (6 kare)

```
pixel art teleport effect sprite sheet, 6 frames in one row on solid pure black background, violet vortex
implosion with dark particles, crisp pixels
```

**E12 · Takla izi / toz** (5 kare)

```
pixel art dust puff sprite sheet, 5 frames in one row on solid pure black background, small brown-grey dust cloud
puffs expanding and fading, crisp pixels
```

**E13 · Mızrak** (tek görsel, kule askerinin fırlattığı)

```
[STİL but side view, horizontal, pointing right] a long wooden spear with a leaf-shaped iron tip and a red cloth tied
near the tip, isolated
```

**E14 · Ganimet parıltısı** (6 kare, döngü)

```
pixel art loot sparkle sprite sheet, 6 frames in one row on solid pure black background, small four-point golden
twinkle stars blinking, loopable, crisp pixels
```

---

## 5) TUŞLAR ve HUD SİMGELERİ

Ben bunları ızgaradan keser, metni kodla yazarım. **Tuşlarda yazı olmasın.**

**B1 · Tuş plakaları** — 4 sütun × 3 satır ızgara:

```
pixel art game UI button plates sprite sheet, grid of 4 columns and 3 rows on flat solid #00FF00 green background,
each cell one rectangular riveted metal button plate with a thick dark outline and a bevel, no text:
row 1 normal state in moss green, steel blue, blood red, dark iron;
row 2 the same four pressed down (darker, bevel inverted);
row 3 the same four disabled (desaturated with diagonal scratches),
crisp pixels, post-apocalyptic survival style
```

**B2 · Yuvarlak dövüş tuşları** — 3 sütun × 2 satır, her biri ortasında simge, yazısız:

```
pixel art round action buttons sprite sheet, grid of 3 columns and 2 rows on flat solid #00FF00 green background,
each a round metal button with a dark rim and a glowing inner ring: 1 katana slash symbol, 2 rolling arrow (dodge),
3 speech bubble (talk), 4 car steering wheel (vehicle), 5 crossed knife and katana (switch weapon),
6 open hand (interact), crisp pixels, no text
```

**B3 · HUD simgeleri** — 6 sütun × 3 satır, her biri 24×24 hissinde, yazısız:

```
pixel art HUD icon set, grid of 6 columns and 3 rows on flat solid #00FF00 green background, simple bold readable
icons with dark outline: backpack, envelope, hamburger menu, scroll quest, star skill tree, paw print,
chat bubble, gear settings, trophy, two people (clan), heart friends, shield, map, gift box, bell, lock, crown, skull,
crisp pixels, consistent style
```

**B4 · Sohbet ve panel çerçevesi** (9-dilim):

```
pixel art UI panel frame, 9-slice style, dark iron frame with rivets at the corners and a slightly translucent dark
center, thick outline, flat solid #00FF00 green background outside, crisp pixels, no text
```

---

## 6) BECERİ SİMGELERİ — piksel ve çeşitli (8 × 3 ızgara)

```
pixel art skill icon set, grid of 8 columns and 3 rows on flat solid #00FF00 green background, each a square icon
with a dark bevelled frame, distinct symbol and color per icon, no text:
1 clenched fist (damage), 2 red heart (max health), 3 green heart with plus (regeneration), 4 shield (defense),
5 winged boot (move speed), 6 running figure (sprint), 7 lightning bolt (stamina), 8 crossed swords (attack speed),
9 sword with long reach arc (reach), 10 bleeding blade drop (crit damage), 11 target eye (crit chance),
12 vampire fang with drop (lifesteal), 13 rolling arrow circle (dodge cooldown), 14 coin stack (trade bonus),
15 axe in a log (gathering), 16 star burst (xp bonus), 17 paw with claws (dog damage), 18 dog head with heart (dog health),
19 skull with crown (kill heal), 20 thorny shield (reflect), 21 purple aura flame (dark aura), 22 hourglass (duration),
23 green potion (potion power), 24 golden key (keystone),
crisp pixels, consistent lighting from top-left
```

---

## 7) ÜRETİM EKRANI

**C1 · Tezgâh arka planı** (geniş görsel, 16:9):

```
pixel art wide 16:9 scene, close-up of a cluttered survivor crafting workbench seen from the front, wooden table
with tools, duct tape rolls, nails jar, scrap metal, a vise, a hanging lamp casting warm light, pegboard with tools
on the wall behind, dark corners, crisp pixels, no text, no characters
```

**C2 · Tarif kâğıdı / kart** (9-dilim):

```
pixel art crafting recipe card, aged paper note pinned with a nail, torn edges, coffee stain, empty center,
flat solid #00FF00 green background outside, crisp pixels, no text
```

**C3 · Üretim kategorileri simgeleri** (5 × 1):

```
pixel art crafting category icons, 5 in one row on flat solid #00FF00 green background: medical kit (healing),
potion bottle (potions), blade (weapons), toolbox (tools), backpack (gear), dark outline, crisp pixels, no text
```

---

## 8) BAKIM EKRANI

**M1 · Bakım sahnesi** (geniş görsel, 16:9) — kodla çizdiğim animasyonlu sahnenin yerini alacak:

```
pixel art wide 16:9 night scene, a fortified survivor camp under repair, workers silhouettes welding with bright sparks
on a wooden wall, a crane lifting a steel beam, warning lights and striped barricades, ruined city skyline under a
big moon in the background, warm and cool contrast lighting, cinematic, crisp pixels, empty dark space in the center
for a text box, no text
```

**M2 · Bakım rozeti**:

```
[STİL but icon] a big crossed wrench and hammer badge over a gear, rusty metal with orange warning stripes, isolated
```

---

## 9) LOGO ve YÜKLEME EKRANI

**T1 · Logo** — yazıyı ben de yazabilirim; görsel yalnız amblem olsun:

```
pixel art game emblem, a hooded masked warrior silhouette with a katana in front of a cracked moon, crimson and bone
white palette, bold dark outline, flat solid #00FF00 green background, crisp pixels, no text
```

**T2 · Yükleme ekranı** (16:9):

```
pixel art wide 16:9 key art, the hooded masked warrior with a katana and a german shepherd dog standing on a hill
looking at a ruined zombie-infested city at dusk, crows in the sky, dramatic orange and purple sky, crisp pixels,
no text
```

---

## Sıralama önerisi

1. Z1–Z7 (zombiler) — en çok APK yerini bunlar kazandırır.
2. W1–W2 (bıçak, katana) — çantadaki boş katana simgesi de düzelir.
3. B1–B3 (tuşlar, simgeler).
4. E1–E9 (efektler).
5. Beceri simgeleri.
6. Üretim, bakım, logo ve diğerleri.

Görselleri sohbete yükle, ben temizleyip oyuna bağlarım ve her birinin oyundaki halini sana göstermeden önce kontrol ederim.
