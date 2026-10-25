# Yapay Zekâ Görsel Prompt'ları (V5)

Sen görseli üretirsin. Ben şunları yaparım:
- ızgarayı ve arka planı temizlerim,
- herkesi aynı boy tablosuna göre ölçeklerim (orantısızlık olmaz),
- parçalara bölüp kodla oynatırım (yürüme, koşma, saldırı, düşme…).

Bu yüzden **her şeyin tek bir duruş görseli yeter**. Animasyon karesi üretmene gerek yok.

## Kurallar (çok önemli)

1. **Bir görselde bir şey** olsun. İstisna, aşağıdaki "ızgara tablo" promptları: onlar bilerek çoklu.
2. **Arka plan düz yeşil (#00FF00)** olsun. Konu yeşilse (toksik zombi, zehir) **düz macenta (#FF00FF)** kullan. Gölge, zemin, yazı, çerçeve olmasın.
3. **Tam boy, hiçbir yeri kesilmemiş** olsun. Ayaklar ve kafanın üstü görünsün.
4. **Bakış yönü**: hepsi **sol-aşağıya, 3/4 açı** (senin maskeli karakter fotoğrafındaki gibi). Sağ tarafı ben aynalarım.
5. **Duruş (kodla oynatabilmem için)**:
   - Kollar gövdeye **değmesin**, hafif açık olsun.
   - Bacaklar hafif aralık olsun, iki ayak da görünsün.
   - Silah ya da eşya elde olmasın. Silahları ayrı üretip ben ele takıyorum.
6. **Boyut önemli değil**. 1024 de gelse ben gerçek piksel boyuna indiririm, APK küçük kalır.
7. Çıktıyı beğenmezsen aynı promptla 2–3 kez üret, en iyisini at. Bir karakterin varyantını istiyorsan önceki görseli
   referans olarak yükle ve `same character, same style, same colors` ekle.

### APK boyutu için hedefler (benim tarafım)

| Tür | Oyundaki gerçek piksel boyu |
|---|---|
| Kahraman | 258 px boy (senin onayladığın, değişmiyor) |
| İnsan zombi, NPC | 110–130 px boy |
| Tank | 150 px |
| Titan | 260 px |
| Köpek | 55 px boy, 80 px uzunluk |
| Eşya ve beceri simgesi | 32×32 |
| Arayüz simgesi | 24×24 |
| Zemin karosu | 32×32 |
| Araba | 150 px uzunluk |

Tüm atlas hedefi ≤ 10 MB.

### Boy tablosu (oran)

Kahraman = 100 kabul edilir:

| Konu | Boy |
|---|---|
| Kahraman | 100 |
| Köpek (omuz yüksekliği) | 42 |
| Yürüyen zombi | 98 |
| Koşucu | 95 |
| Tank | 140 |
| Titan | 240 |
| Tüccar | 96 |
| Bisiklet | 60 boy, 105 uzun |
| Araba | 75 boy, 260 uzun |
| Kapı | 120 |
| Ağaç | 250–400 |

Görseller farklı boyda gelse de ben bu tabloya göre ölçeklerim.

## STİL (her prompt'un başına yapıştır)

```
high quality pixel art game sprite, crisp hard-edged pixels, 1px dark selective outline, limited dark palette
(charcoal, black, deep crimson, bone white, rust brown, muted steel), hue-shifted shading with light from the top-left,
three-quarter view facing the lower-left, full body, nothing cropped, flat solid #00FF00 green background,
no ground, no cast shadow, no glow, no text, no frame, no watermark
```

**Negatif** (araç destekliyorsa):
`blurry, anti-aliased, smooth gradient, 3d render, photo, painterly, multiple characters, cropped, text, logo, shadow, background scenery`

---

## 1) KÖPEK — önce bunu üret

**D1 · Evcil köpek, yandan**: kod bu görselden yürüme, koşma, ısırma ve yatma animasyonlarını çıkaracak.
```
[STİL] a loyal scruffy german shepherd mix dog companion, dark brown and black fur, red bandana around the neck,
alert ears, standing side view facing LEFT, all four legs clearly separated and visible, tail slightly raised,
mouth closed
```
**D2 · Aynı köpek, 3/4 önden**: kameraya doğru koşarken kullanılacak.
```
[STİL] same dog as reference, same colors and bandana, standing three-quarter front view facing the lower-left,
all four legs visible
```
**D3 · Seviye görünümleri** (isteğe bağlı):
- Seviye 5: `same dog wearing a dark leather armor vest`
- Seviye 10: `same dog wearing spiked dark iron armor, glowing amber eyes`

**D4 · Zombi köpek, yandan**:
```
[STİL] rotting zombie dog, patchy grey-brown fur, exposed ribs and bones, torn ear, glowing red eyes, side view facing
LEFT, all four legs clearly separated
```

## 2) ZOMBİLER — tek görsel, kural 5'teki duruş

Bütün zombilerde STİL'den sonra bu eki kullan:
`undead zombie, grey-green rotting skin, torn clothes, arms slightly away from the body, legs slightly apart`

| Kod | Prompt eki |
|---|---|
| Z1 Yürüyen | `ordinary townsperson zombie, torn office shirt and loose tie, one shoe missing, hunched` |
| Z2 Koşucu | `thin fast runner zombie in a torn red tracksuit, long arms, leaning slightly forward` |
| Z3 Toksik (**macenta arka plan**) | `bloated toxic zombie with glowing green boils, torn yellow hazmat suit, dripping green slime` |
| Z4 Karanlık | `shadow zombie made of black smoke-like flesh, purple glowing veins and eyes, wispy edges` |
| Z5 Tank | `huge muscular brute zombie, torn military pants, metal plates bolted to the shoulders, tiny head` |
| Z6 Gizemli | `tall faceless zombie in a black suit wearing a cracked white porcelain mask with red tear marks` |
| Z7 Titan | `colossal titan boss zombie, exposed rib cage, chains hanging from the wrists, burning red eyes, cracked stone-like skin` |

## 3) KİŞİLER

| Kod | Prompt |
|---|---|
| N1 Tüccar | `[STİL] old wasteland merchant with a huge backpack full of goods, goggles on the forehead, long patched coat, friendly face` |
| N2 Asker | `[STİL] city wall soldier in dark tactical gear and helmet, standing guard, empty hands` |

## 4) BECERİ AĞACI SİMGELERİ — tek görselde 16'lı ızgara

Beceri ağacında "neyin ne olduğu belli değil" sorununu bunlar çözecek. **Tek görsel, 4×4 ızgara.**
```
pixel art RPG skill icon sheet, 4 by 4 grid of 16 separate square icons with equal spacing on a flat #00FF00
background, each icon a bold readable symbol with 1px dark outline, dark fantasy style, no text, no frames:
row 1: crossed swords (damage), red heart (max health), cracked shield (damage reduction), spiked mirror shield (reflect),
row 2: wind swirl with a ghost silhouette (dodge), glowing bullet (bullet damage), bomb with a shield (explosion resist),
winged boot (move speed),
row 3: red lightning skull (critical hit), blood drop with fangs (lifesteal), hourglass (cooldown),
green plus with leaves (regeneration),
row 4: clenched fist with sparks (punch power), rolling arrows circle (dodge roll), purple dark flame (dark aura),
golden crown with a star (keystone)
```
İstersen aynısını `glowing gold rim` ekiyle bir kez daha üret. "Açılmış" hâli için kullanırım, yoksa parlamayı kodla yaparım.

## 5) EŞYA SİMGELERİ — 4×4 ızgaralar (her görsel 16 simge)

Ortak giriş: `pixel art inventory icon sheet, 4 by 4 grid of 16 separate items with equal spacing on a flat #00FF00 background, each item centered at a slight angle, 1px dark outline, no text:`

- **I1 Silahlar**: `brass knuckles, combat knife, rusty machete, wooden baseball bat with nails, long steel sword, black 9mm pistol,
  pump shotgun, assault rifle, sniper rifle, wooden torch with flame, unlit torch, crossbow, axe, crowbar, sledgehammer, grenade`
- **I2 Malzemeler**: `stack of wooden planks, grey stones, folded cloth, glue tube, amber resin chunk, leather piece,
  scrap metal, screws, gunpowder pouch, circuit board, zombie tooth, toxic green crystal, dark purple crystal,
  glowing red titan shard, fuel jerrycan, rope coil`
- **I3 Tüketilir**: `red health potion bottle, cyan speed potion bottle, orange power potion bottle, green antidote bottle,
  bandage roll, first aid kit, canned food, energy drink can, gold coin pile, pistol ammo box, shotgun shells,
  rifle ammo box, dog food bowl, bone treat, map scroll, key`
- **I4 Ekipman**: `dark hood, leather jacket, police vest, military armor vest, work boots, military boots, small backpack,
  large hiking backpack, motorcycle helmet, military helmet, cloth gloves, iron gauntlets, black cloak, gas mask,
  night vision goggles, lantern`

## 6) ARAYÜZ — 4×4 ızgara + tek parçalar

- **U1 Simgeler** (24×24 hedef):
  ```
  pixel art UI icon sheet, 4 by 4 grid on flat #00FF00:
  map, quest scroll, mail envelope, backpack, hammer and anvil, skull, gear, chat bubble, trophy, people group,
  paw print, potion, torch, shopping bag, crossed swords, lock
  ```
- **U2 Panel**: `pixel art game UI panel frame, dark charcoal stone with rusted iron corners and rivets, empty center, symmetrical, flat #00FF00 background`
- **U3 Tuşlar**: `pixel art game buttons sheet on flat #00FF00: wide dark green metal button, wide dark red metal button, wide dark blue metal button, round dark metal action button with ember rim, each also in pressed state`
- **U4 Beceri düğüm çerçeveleri**:
  `pixel art skill tree node frames on flat #00FF00: small round iron ring, medium double gold ring, large ornate octagon frame with spikes and a red gem, each empty in the center`

## 7) ZEMİN VE DÜNYA

- **T1 Zemin karoları**:
  ```
  pixel art seamless tileable top-down ground textures, 4 by 4 grid on flat magenta #FF00FF: grass, dark grass,
  dry grass, dirt road, mud, gravel, cracked asphalt, sidewalk, cobblestone, shallow water, swamp water, sand,
  burnt ground, snow, wooden floor, concrete floor
  ```
- **T2 Nesneler**: STİL'i bu girişle kullan: `top-down three-quarter view game prop, one object, flat #00FF00 background`.
  - Ağaçlar: çam, meşe, ölü ağaç (her biri ayrı görsel).
  - Tek ızgarada: `bush, rock, fence segment, street lamp, campfire, wooden crate, open crate, barrel, dumpster, rubble pile,
    gravestone, burnt car wreck, sandbags, barbed wire, tent, signpost`.
- **T3 Binalar** (her biri ayrı): STİL + `top-down three-quarter view pixel art building, roof visible, flat #00FF00
  background` ve ardından: ahşap ev, tuğla ev, market, benzin istasyonu, karakol, hastane, yıkık bina, şehir duvarı
  parçası, nöbet kulesi.
- **T4 Araçlar**: her aracı **iki görsel** yap:
  - `side view facing LEFT`
  - `three-quarter view from the front-left`

  Araçlar: `rusty orange sedan`, `black SUV`, `motorbike`, `bicycle`. Hasarlı hâl için ek: `dented, broken windows, smoking`.

## 8) EFEKTLER — yatay şerit, 6 kare

`pixel art effect animation strip, 6 frames in a single row, equal spacing, flat #00FF00 background:` ardından şunlardan
biri: `dust puff`, `blood splatter`, `explosion with smoke`, `green toxic gas` (macenta arka plan), `ground shockwave ring`,
`fire`, `muzzle flash`, `level up golden light pillar`, `dark purple aura swirl`, `heal sparkles`.

## 9) HİKÂYE VE EKRANLAR (16:9, sahne olduğu için arka plan serbest)

Ortak: `cinematic pixel art scene, 16:9, detailed, moody night lighting, dark purples, crimson moon, warm orange window light`

| Kod | Prompt |
|---|---|
| S1 Oda | `a young man sitting in a dark bedroom playing a video game on a glowing PC monitor, posters on the wall, calm night city in the window` |
| S2 Balkon | `the same man on an apartment balcony looking down in shock at a street where people flee from zombies, burning cars` |
| S3 Savaş | `military jets over a burning city at night, explosions, tracer bullets, helicopters` |
| S4 Roket | `a rocket streaking toward an apartment building at night, bright explosion flash` |
| S5 Enkaz | `the man waking up in rubble, dust in the air, a black hood and a white porcelain mask with red stripes lying on the floor in red moonlight` |
| S6 Tepe | `the hooded warrior with a white mask standing on a cliff seen from behind, black cloak flowing, burning city under a huge crimson moon` |
| L1 Giriş | `the hooded masked warrior sitting on a broken wall at night with a torch, ruined city and crimson moon behind, empty space on the right for menu buttons` |
| I1 Simge | `app icon, close-up of the white porcelain mask with red vertical stripes and glowing yellow eyes inside a black hood, crimson moon behind, bold silhouette, no text` |

## Gönderme sırası

Görselleri bu sohbete at. Mümkünse dosya adına kodu yaz (D1, Z3, I2…), ama şart değil.

1. **D1, D2, D4** (köpekler)
2. **4) beceri simgeleri**, U4 düğüm çerçeveleri
3. Z1–Z7, N1–N2
4. I1–I4 ve U1–U3
5. T1–T4, 8) efektler
6. S1–S6, L1, I1

Her paket gelince temizleyip oyuna koyar, sonra **kendim kontrol edip** önizlemesini gönderirim.
