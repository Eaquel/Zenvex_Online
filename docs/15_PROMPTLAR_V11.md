# Yapay Zekâ Görsel Prompt'ları (V11)

V9 belgesiyle (`14_PROMPTLAR_V9.md`) aynı stil. Karakter animasyonları bu belgede **yok** (onlar ayrı paket).
Bu paket, V11 güncellemesinde oyuna giren ama henüz kendi görseli olmayan her şeyi kapsar. Görsel gelene kadar oyun
yedek bir görsel kullanıyor (aşağıdaki tablolarda "şu an" sütunu), yani hiçbir şey eksik görünmez; ama gerçek görseller
gelince çok daha iyi olacak.

| # | Paket | Adet | Atlas anahtarı / dosya |
|---|-------|------|------------------------|
| A | Etkinlik ödülleri (6 etkinlik × yüzük, kolye, silah) | 18 simge + 6 elde silah | `i/ev_<etkinlik>_<ring/neck/weapon>`, `w/<silah>` |
| B | Ağaçlar, çalılar, kayalar (3B model → piksel) | 21 | `s/oak0` … `s/rock_small2` |
| C | Aletler ve araç parçaları | 10 simge | `i/t_axe_stone` … `i/veh_car` |
| D | Motosiklet ve araba, 8 yön | 2 sayfa × 8 | `x8/moto/0-7`, `x8/car/0-7` |
| E | Cephe Hattı ve karargâh | 9 | `s/…` |
| F | Öğretici ve eğitim üssü (bomba, uçak) | 6 | `s/air_bomb`, `fx/…` |
| G | Dünya sınırı süsleri | 5 | `s/…` |
| H | Arayüz (üretim sekmeleri, garaj, ping, etkinlik çerçevesi) | 20 | `ui/…` |
| I | Efektler | 9 sayfa | `fx/…` |

Gönderirken dosya adını tablodaki adla ver (ör. `ev_blood_weapon.png`), birden fazla şeyi tek görselde gönderiyorsan
hangisinin hangisi olduğunu yaz; ben ayıklarım.

---

## STİL (her prompt'un başına yapıştır)

```
high quality pixel art game sprite, crisp hard-edged pixels, 1px dark selective outline, limited dark palette
(charcoal, black, deep crimson, bone white, rust brown, muted steel, olive drab), hue-shifted shading with light from the top-left,
3/4 top-down view (the same camera as a top-down survival RPG), nothing cropped, centered,
flat solid #00FF00 green background, no ground, no cast shadow, no text, no frame, no watermark
```

**Simgeler için (A, C, H) STİL'in sonuna ekle:**

```
single inventory icon, object fills 80% of a square canvas, slight 3/4 angle, bold readable silhouette at 48x48 pixels
```

**Negatif (hepsinde):**

```
blurry, anti-aliased, smooth gradient, 3d render, photo, painterly, cropped, text, letters, logo, watermark, drop shadow,
background scenery, multiple objects (unless asked), green objects (background is green), glow halo outside the object
```

> **Yeşil zemin uyarısı:** zemin #00FF00 olduğu için nesnede saf yeşil kullanma (zehirli/yosunlu şeylerde koyu zeytin ya da
> camgöbeği kullan). Yoksa ayıklarken nesnenin yeşil kısmı da silinir.

---

## A) ETKİNLİK ÖDÜLLERİ (18 simge + 6 elde silah)

Her etkinliğin **1. ödülü yüzük, 2. ödülü kolye, 3. ödülü silah** (kask kalktı). Aynı etkinliğin üç eşyası aynı malzeme,
renk ve motifi paylaşmalı; yan yana durunca "set" gibi görünmeli. Mevcut yüzük ve kolye simgeleri oyunda var; seti
eşleştirmek için **18'ini birden yeniden üretmeni** öneririm (en tutarlısı). Sadece eksikleri istiyorsan 6 silah yeter.

**Ortak tarif (her etkinlik prompt'una ekle):**

```
legendary event reward, ornate but readable, dark metal base with the event's accent color as glowing gem inlays and edge highlights,
tiny engraved motif, premium collectible look
```

| Etkinlik | Önek | Renk (vurgu) | Motif |
|----------|------|--------------|-------|
| Kan Ayı | `blood` | koyu kızıl #C8201A + kemik beyazı | kızıl ay, damla, diş |
| Titan Avı | `titan` | kor turuncusu #E0904A + is | dev kafatası, çatlak taş, alev |
| Üs Fırtınası | `base` | buz mavisi #6AB8FF + çelik | yıldırım, kalkan, perçin |
| Hurdacılar | `scrap` | pas + zehir sarısı #C8B030 | dişli, somun, testere dişi |
| Sürü Lideri | `pack` | mor #C07AFF + kurt grisi | kurt başı, pençe izi, diş |
| Son Nefes | `breath` | soluk camgöbeği #7FE0FF + kemik | kalp atışı çizgisi, kum saati, kafatası |

### A1 · Yüzükler (6) → `ev_<önek>_ring.png`

```
[STİL] [SİMGE] [ORTAK] a heavy signet ring seen at a 3/4 angle, thick band, large faceted gem in the accent color set in claws,
the event motif engraved on the band. Event: <ETKİNLİK ADI>, accent color <RENK>, motif <MOTİF>.
```

### A2 · Kolyeler (6) → `ev_<önek>_neck.png`

```
[STİL] [SİMGE] [ORTAK] a pendant necklace laid in a loose U shape, dark iron chain, large amulet in the middle shaped like the
event motif with an accent color gem core. Event: <ETKİNLİK ADI>, accent color <RENK>, motif <MOTİF>.
```

### A3 · Silahlar (6) → simge `ev_<önek>_weapon.png` + elde görünüm `ev_<önek>_weapon_held.png`

Hepsi yakın dövüş silahı (oyunda ateşli silah yok). Oyundaki adları ve hisleri:

| Dosya | Ad | Tür ve his |
|-------|----|------------|
| `ev_blood_weapon` | Kan Ayı Orağı | uzun saplı tek ağızlı orak; kızıl ay şeklinde kavisli bıçak, sapında kemik halkalar |
| `ev_titan_weapon` | Titan Kırıcı Balyoz | iki elle tutulan devasa balyoz; başı çatlak taş blok, çatlaklarından kor ışığı |
| `ev_base_weapon` | Fırtına Baltası | çift ağızlı savaş baltası; çelik, ağızlarında mavi yıldırım damarları, perçinli sap |
| `ev_scrap_weapon` | Hurdacı Testere Kılıcı | hurdadan kaynak yapılmış kılıç; ağzı zincir testere dişli, sapı bantlı boru |
| `ev_pack_weapon` | Sürü Lideri Pençesi | bileğe takılan üç bıçaklı pençe (kurt pençesi); mor deri kayışlar, kurt dişi süs |
| `ev_breath_weapon` | Son Nefes Tırpanı | uzun ince tırpan; soluk camgöbeği, sapında kum saati, bıçağında kalp atışı çizgisi oyuğu |

**Simge:**

```
[STİL] [SİMGE] [ORTAK] <SİLAH TARİFİ>, diagonal from bottom-left to top-right, whole weapon visible.
Event: <ETKİNLİK ADI>, accent color <RENK>.
```

**Elde görünüm (oyunda karakterin elinde çizilir):**

```
[STİL] the same weapon as a held-weapon sprite, side view, pointing to the right, horizontal, handle on the left,
no hands, no character, same colors and details as the icon, about 1.4 meters long in game scale
```

> Elde görünüm gelene kadar oyun büyük silahlarda katana, pençe ve testerede bıçak çizimini kullanıyor.

---

## B) AĞAÇLAR, ÇALILAR, KAYALAR (3B model → piksel) — 21 görsel

Ormandaki eski ağaçlar 3B modelden render edilmiş; V10 bölge dekorları gibi piksel olmaları lazım. **Her biri ayrı görsel**,
gövdenin dibi görselin altında ortada olsun (oyun dibinden yere oturtur). Ağaçların artık 500 canı var ve vuruldukça
sallanıyor; bu yüzden tepesi gövdeden ayrı okunabilmeli (ben tepeyi ayırıp sallayacağım).

**Ortak:**

```
[STİL] a single tree for a top-down zombie survival RPG forest, 3/4 top-down view, trunk base at the bottom center of the image,
canopy clearly separated from the trunk by a darker band, slightly desaturated post-apocalyptic forest colors (deep olive, moss, dark teal greens — NOT pure green),
tall image (about 1:1.6), no grass at the base
```

| Dosya | Tarif (ortak'ın sonuna ekle) |
|-------|------------------------------|
| `oak0` | broad old oak, round dense canopy, thick gnarled trunk |
| `oak1` | oak with a lopsided canopy leaning left, one broken branch |
| `oak2` | young slimmer oak, smaller canopy, visible roots |
| `oak_autumn` | oak in autumn, rust orange and dull red leaves, some bare branches |
| `pine0` | tall dark pine, layered triangular tiers, straight trunk |
| `pine1` | pine with a snapped top, sparse lower branches |
| `pine2` | short wide pine, dense, almost cone shaped |
| `birch0` | white birch with black bark marks, light airy yellow-green canopy (not pure green) |
| `birch1` | two birch trunks growing from one base, thin canopy |
| `dead0` | dead leafless tree, twisted grey branches like claws |
| `dead1` | dead tree broken in half, splintered top, a crow sitting on it |
| `bush0` | low round bush, dark olive leaves (short image, 1:0.8) |
| `bush1` | thorny bush with dark red berries |
| `bush2` | wide flat bush, swampy dark teal-green |
| `bush3` | tall reed-like bush, brownish |
| `bush4` | dry desert scrub bush, yellow-brown |
| `rock_big0` | large grey boulder with moss patches (not pure green), cracked |
| `rock_big1` | stacked flat rocks, slate grey |
| `rock_big2` | jagged rock with iron-rust streaks (a kazma/pickaxe target) |
| `rock_small0`–`rock_small2` | 3 small pebble clusters in one row, wide spacing between them |

**Bonus (isteğe bağlı):** kesilmiş ağaç kütüğü `stump` (yeni kesilmiş, açık renk halkalı) ve devrilmiş gövde `log`.

---

## C) ALETLER VE ARAÇ PARÇALARI (10 simge)

Şu an: balta/kazma için taş ve bıçak, parçalar için dişli/elektronik/hurda, anahtarlar için paslı anahtar simgesi.

```
[STİL] [SİMGE] <TARİF>
```

| Dosya | Tarif |
|-------|-------|
| `t_axe_stone` | primitive stone axe, sharp flint head tied to a wooden handle with rope |
| `t_axe_iron` | iron woodcutter axe, dark steel head with a bright worn edge, leather-wrapped wooden handle, two nails in the head |
| `t_pick_stone` | primitive stone pickaxe, two pointed stone heads lashed with rope to a wooden handle |
| `t_pick_iron` | iron pickaxe, curved dark steel head, leather grip |
| `v_wheel` | a single worn off-road vehicle wheel with a chunky rubber tire and rusty rim, seen at 3/4 |
| `v_engine` | a small salvaged engine block, cylinders, belts and pipes, oil stains, scrap-built look |
| `v_frame_moto` | a bare motorcycle frame (chassis) welded from scrap pipes, no wheels, no engine |
| `v_frame_car` | a bare car chassis / roll cage welded from scrap, no wheels, no engine, seen from 3/4 above |
| `veh_moto` | a motorcycle key on a leather tag with a small engraved motorcycle, steel key, red tassel |
| `veh_car` | a car key with a chunky black fob and a dog-tag style keychain engraved with a jeep |

---

## D) MOTOSİKLET VE ARABA — 8 YÖN

Şu an araçlar sadece yandan çiziliyor ve sola/sağa aynalanıyor; aşağı-yukarı giderken yan duruyor. 8 yön gelince araç
gittiği yöne döner, egzoz ve far da o yönden çıkar.

**Sayfa düzeni (çok önemli):** tek görselde **8 araç, tek satırda**, aralarında geniş boşluk, soldan sağa bu sırayla:

1. aşağı (kameraya doğru) · 2. sağ-aşağı · 3. sağ · 4. sağ-yukarı · 5. yukarı (kameradan uzağa) · 6. sol-yukarı · 7. sol · 8. sol-aşağı

**Motosiklet** → `moto_8dir.png`:

```
[STİL] vehicle turnaround sheet, the SAME rugged post-apocalyptic dirt motorcycle shown 8 times in one row with wide empty space between them,
rotating in 45 degree steps in this exact order: facing down toward the viewer, down-right, right, up-right, up away from the viewer,
up-left, left, down-left. 3/4 top-down camera, identical design in every view: matte black tank with a red stripe, knobby tires,
exhaust pipe on the right side, small round headlight, a bedroll strapped behind the seat, no rider
```

**Araba (açık cip)** → `car_8dir.png`:

```
[STİL] vehicle turnaround sheet, the SAME rusty open-top off-road jeep shown 8 times in one row with wide empty space between them,
rotating in 45 degree steps in this exact order: facing down toward the viewer, down-right, right, up-right, up away from the viewer,
up-left, left, down-left. 3/4 top-down camera, identical design in every view: olive-drab paint with rust patches, roll bar,
spare tire on the back, two round headlights, jerry can on the side, empty seats, no driver
```

> İpucu: önce sadece "sağa bakan" tek görseli üret, beğendiğini referans olarak verip 8 yönlü sayfayı onunla üret. Tutarlılık çok artar.

---

## E) CEPHE HATTI VE KARARGÂH (9)

Üslerin hepsi artık haritanın doğusundaki ve güneyindeki **Cephe Hattı**'nda. Savaş alanı havası: çamur, siper, enkaz.

**E1 · Zemin dokusu** → `ground_front.png` (tekrarlayan doku; STİL yerine bunu kullan):

```
seamless tileable top-down pixel art ground texture, 64x64 tile repeated 4x4, war-torn muddy battlefield soil, tire ruts,
small craters, scattered spent shells, dark brown and grey, no objects taller than the ground, no text
```

**E2 · Nesneler** (`[STİL]` + tarif):

| Dosya | Tarif |
|-------|-------|
| `front_trench` | a short straight trench segment with wooden plank walls and sandbags on top, seen from 3/4 above, wide image |
| `front_crater` | a shallow shell crater in mud with scorched rim and a few stones (flat, ground decal, very wide and short) |
| `front_barbed` | a section of barbed wire on wooden X-shaped stakes (czech hedgehog style), wide image |
| `front_tank_wreck` | a burnt-out destroyed tank hull with the turret knocked off beside it, rust and soot |
| `hq_command_tent` | a large military command tent with an open flap, a folding map table visible inside, antenna on top, olive canvas |
| `hq_radio_mast` | a tall radio antenna mast with guy wires and a small generator at its base |
| `hq_flag` | a tall flag pole with a dark banner showing a white crossed-swords emblem (no text) |
| `hq_sign` | a wooden signpost with arrows (arrows only, no letters) and a hanging lantern |

---

## F) ÖĞRETİCİ VE EĞİTİM ÜSSÜ (6)

Sv 10'da Komutan oyuncuyu eğitim üssüne yollar: botları öldürür, üssü alır, üstüne bomba düşer, şehirde uyanır.

| Dosya | Prompt |
|-------|--------|
| `air_bomb` | `[STİL] a single falling aerial bomb pointing straight down, olive drab body, red stripe near the nose, four tail fins, side view, tall image` |
| `bomber_shadow` | `top-down pixel art silhouette of a twin-engine bomber plane seen from directly above, solid flat dark grey shape (it will be drawn as a moving ground shadow), wings spread, no details, #00FF00 background` |
| `train_target` | `[STİL] a flat ground decal of a red and white target marker painted on dirt, concentric circles with a cross, seen at 3/4, very wide and short` |
| `train_sign` | `[STİL] a wooden military training-ground sign board on two posts with a painted crossed-rifles emblem (no letters), olive paint` |
| `train_dummy` | `[STİL] a straw training dummy on a wooden pole wearing an old army helmet, with stitched X eyes` |
| `tut_hand` | `[STİL] a pixel art pointing hand cursor (white glove, index finger pointing up), thick dark outline, for a tutorial hint, small square image` |

---

## G) DÜNYA SINIRI SÜSLERİ (5)

Haritanın kenarına karantina duvarı geldi (kuleler, lambalar, barikatlar kodla dizildi). Bunlar gelirse duvar boyunca kullanılacak:

| Dosya | Tarif (`[STİL]` +) |
|-------|--------------------|
| `quarantine_wall` | a 4-tile-long concrete quarantine wall segment with barbed wire coils on top and faded red-white hazard stripes, seen from 3/4 above, very wide image |
| `quarantine_post` | a concrete wall pillar with a red warning light on top and a hazard symbol plate (symbol only, no letters) |
| `quarantine_sign` | a metal warning sign with a skull and biohazard symbol (no letters) bolted to a post |
| `border_searchlight` | a large military searchlight on a tripod, lens glowing pale yellow |
| `border_bones` | a pile of old bones and a torn hazmat suit at the foot of a wall (flat, wide decal) |

---

## H) ARAYÜZ (20)

**H1 · Üretim sekmesi simgeleri** (8) → `tab_<ad>.png`. `[STİL] [SİMGE]` + "a flat UI tab icon, bold silhouette, single accent color":

| Dosya | Simge | Vurgu |
|-------|-------|-------|
| `tab_heal` | bandage roll with a red cross | kızıl |
| `tab_potion` | round potion flask with bubbles | mor |
| `tab_weapon` | crossed sword and axe | çelik |
| `tab_gear` | combat vest | haki |
| `tab_jewel` | ring with a gem | altın |
| `tab_material` | stacked wood planks and a gear | kahve |
| `tab_vehicle` | car wheel with a wrench | turuncu |
| `tab_base` | small fortified wall with a flag | taş grisi |

**H2 · Diğer arayüz parçaları:**

| Dosya | Prompt |
|-------|--------|
| `bg_garage` | `pixel art background illustration, interior of a cluttered mechanic garage in a post-apocalyptic gas station, a half-built jeep on a lift, tools on the wall, warm lamp light, dark moody, 16:9, no characters, no text` |
| `card_order` | `pixel art UI card frame, dark riveted metal plate with a small window on the left for an icon and an empty progress bar slot at the bottom, 3:1 wide, no text, #00FF00 background` |
| `ping_bars` | `pixel art signal strength icon sheet, 4 states in one row: 1 bar red, 2 bars orange, 3 bars yellow, 4 bars green-cyan (not pure green), square cells, #FF00FF magenta background` |
| `event_frame` | `pixel art ornate horizontal banner frame for a game HUD, dark iron with skull corners and an empty center, 6:1 wide, the inner area transparent-ready flat #00FF00, no text` |
| `event_frame_glow` | `the same banner frame outline only, as a bright white glowing outline (it will be tinted per event color in game), #000000 black background` |
| `mail_badge` | `pixel art notification badge, small red wax seal with a white envelope symbol (no letters), square, #00FF00 background` |
| `title_ribbon_event` | `pixel art name ribbon banner for a player title, dark crimson cloth with gold trim and small wings, empty center for text, 5:1 wide, no text, #00FF00 background` (etkinlik unvanları için) |
| `commander_portrait` | `pixel art bust portrait for a dialog box: a tough female army commander, short dark hair, scar on the cheek, olive uniform with a red armband, looking forward, square, #00FF00 background` (öğretici konuşma kutusu; NPC'nin tam boy görseli değil) |

---

## I) EFEKTLER (9 sayfa)

Efekt sayfaları: **tek satırda 6-8 kare**, kareler eşit genişlikte, **siyah zemin (#000000)**, parlak kısımlar beyaza yakın
(ben siyahı saydam yaparım). STİL yerine bunu kullan:

```
pixel art VFX animation sprite sheet, one row of N equal frames left to right showing the animation progressing,
crisp pixels, no anti-aliasing, pure black #000000 background, no text
```

| Dosya | Kare | Tarif |
|-------|------|-------|
| `fx_wood_chips` | 6 | wood chips and splinters bursting outward from an axe hit on a tree trunk, light brown chips, fading |
| `fx_rock_chips` | 6 | grey stone fragments and dust puff bursting from a pickaxe hit, small orange sparks in the first 2 frames |
| `fx_axe_swing` | 6 | a curved white motion trail arc of an axe swing from top-right to bottom-left, fading |
| `fx_exhaust` | 6 | a single small grey-blue exhaust puff, appears small, grows, thins out and fades (it will be spawned repeatedly) |
| `fx_heli_dust` | 8 | a flat ring of dust blown outward on the ground by helicopter rotor wash, seen at 3/4, expanding ring, very wide frames |
| `fx_teleport` | 8 | a purple magic teleport circle with runes appearing on the ground, a column of light rises then collapses into sparkles |
| `fx_bomb_blast` | 8 | a huge aerial bomb explosion seen at 3/4: bright white flash, orange fireball, rising black mushroom smoke, debris flying |
| `fx_bomb_whistle` | 6 | a falling bomb motion streak: thin vertical white air trail lines with a small flicker, for a falling projectile |
| `fx_level_ring` | 8 | a golden ring of light expanding on the ground with rising golden sparkles, for level up / title earned |

---

## Teslim notları

- Her görsel **ayrı dosya** olursa en temiz sonuç çıkar; sayfa halinde gönderirsen sıra tablodaki sıra olsun.
- Gönderdiğinde ben: yeşil/siyah zemini temizler, piksel ızgarasına oturtur, ölçeği oyun metresine çevirir ve atlasa eklerim.
  Kod tarafı hazır: `x8/…` araç yönleri, `s/air_bomb`, `npc/commander`, `i/ev_*_weapon` gibi anahtarlar geldiği anda kullanılıyor.
