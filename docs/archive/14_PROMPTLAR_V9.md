# Yapay Zekâ Görsel Prompt'ları (V9)

V8 belgesiyle birlikte kullan (`13_PROMPTLAR_V8.md`). Bu paket şunları kapsar:

1. Ana karakter: yön sayfası (turnaround) ve poz sayfaları → sağa/sola/yukarı/aşağı doğru yürüyüş
2. Zombiler, köpek ve askerler için poz sayfaları
3. Uygulama simgesi (ana karakterin kendisi)
4. Sıralama: 1-2-3 madalyaları, ilk 10 rozeti, isim etiketi çerçeveleri (ilk 3 efektli)
5. 9 klan seviyesi amblemi
6. 6 etkinlik: afişler + özel ödül eşyaları (18 simge)
7. Elmas kasası (PUBG sandığı gibi): kasa, açılış sahnesi arka planı, nadirlik ışıkları
8. Efsane skinler: karakter, motosiklet, araba
9. Battle Pass ekranı ve tuşları
10. Bölgeler: zemin dokuları, bölgeye özel eşyalar (buz, çöl, bataklık, yanık orman, liman, askerî)
11. Kamp çadırları (yeşil çadırın yerine)
12. Arayüz çerçeveleri ve paneller
13. Gerçekçi efektler (kan, kıvılcım, ateş, duman, şok dalgası)

---

## STİL (karakter ve nesne prompt'larının başına yapıştır)

```
high quality pixel art game sprite, crisp hard-edged pixels, 1px dark selective outline, limited dark palette
(charcoal, black, deep crimson, bone white, rust brown, muted steel, olive drab), hue-shifted shading with light from the top-left,
full body, nothing cropped, flat solid #00FF00 green background, no ground, no cast shadow, no glow, no text, no frame, no watermark
```

**Negatif:**

```
blurry, anti-aliased, smooth gradient, 3d render, photo, painterly, cropped, text, logo, shadow, background scenery,
inconsistent character, different outfit between poses, extra limbs
```

### "100 animasyon" nasıl olacak?

Yapay zekâ 100 tutarlı kareyi tek görselde çizemez; kareler birbirine benzemez, titrer. Doğru yol şu:

- Sen **yön sayfası** (4 yön) ve **poz sayfaları** (her sayfada 8-12 anahtar poz) üretirsin.
- Ben her pozu iskelete bağlar, aradaki kareleri kodla üretirim (mesh deformasyon).
- Böylece her karakter için **100'ün üstünde kare** çıkar: 4 yön × (bekleme, yürüme, koşma, takla, yumruk, bıçak, katana, vurulma, düşme, kalkma, ölüm, motor, araba, sevinç...).

**Tutarlılık için:** her sayfada aynı karakter tarifini **kelimesi kelimesine** kullan. Önce yön sayfasını üret, beğendiğin sonucu "referans görsel" olarak ver ve poz sayfalarını onunla üret.

---

## 1) ANA KARAKTER

**Karakter tarifi (her prompt'a aynen koy):**

```
a hooded survivor in a long ragged black-charcoal coat with torn hem, dark hood fully shadowing the face (only darkness, no visible face),
leather bracers, brown leather belt with pouches and a small skull charm, dark cargo trousers, worn brown boots
```

**K1 · Yön sayfası (en önemlisi; yürüme yön hatasını bu çözer)**

```
[STİL] character turnaround sheet, the SAME character shown 4 times in a single row with wide empty space between them:
facing front, facing right (pure side profile), facing back, facing left (pure side profile),
[KARAKTER], neutral standing pose, arms slightly away from the body, feet visible, identical scale and identical outfit in all 4
```

**K2 · Yürüme anahtar pozları (yandan)**

```
[STİL] animation key pose sheet, the SAME character in pure side profile facing right, 8 poses in a single row with wide gaps:
walk cycle contact, down, passing, up, contact (other leg), down, passing, up — clear leg and arm swing, [KARAKTER]
```

**K3 · Koşma anahtar pozları (yandan)**

```
[STİL] animation key pose sheet, the SAME character in pure side profile facing right, 8 poses in a single row with wide gaps:
sprint cycle with strong forward lean, long strides, one frame with both feet off the ground, arms pumping, [KARAKTER]
```

**K4 · Yürüme önden ve arkadan**

```
[STİL] animation key pose sheet, the SAME character, two rows: top row 4 poses walking toward the viewer (front view),
bottom row 4 poses walking away from the viewer (back view), clear alternating legs, [KARAKTER]
```

**K5 · Dövüş pozları (yandan, sağa)**

```
[STİL] animation key pose sheet, the SAME character in side profile facing right, 10 poses in two rows with wide gaps:
fighting stance, left jab, right cross, uppercut, front kick, dodge back, roll start (crouch), roll mid (tucked ball), roll end, block,
empty hands, [KARAKTER]
```

**K6 · Katana pozları (yandan, sağa)** — katana elde, iki elle kavrama

```
[STİL] animation key pose sheet, the SAME character in side profile facing right holding a katana with a red wrapped handle in both hands,
10 poses in two rows with wide gaps: ready stance, overhead raise, downward slash, horizontal slash wind-up, horizontal slash follow-through,
rising slash, thrust, spin slash, sheathe, victory pose, [KARAKTER]
```

**K7 · Bıçak pozları (yandan, sağa)**

```
[STİL] animation key pose sheet, the SAME character in side profile facing right holding a combat knife in reverse grip,
8 poses with wide gaps: guard, quick stab, slash left, slash right, lunge, backstep, throw-ready, idle flip, [KARAKTER]
```

**K8 · Vurulma, düşme, ölüm**

```
[STİL] animation key pose sheet, the SAME character in side profile facing right, 8 poses with wide gaps:
hit flinch, heavy hit stagger, knocked back falling, lying on the back, getting up on one knee, standing up, collapse death, dead on the ground,
[KARAKTER]
```

**K9 · Motosiklet ve araba sürüşü** (aracı ayrı üretiyoruz, burada yalnız karakter)

```
[STİL] animation key pose sheet, the SAME character seated as if riding (no vehicle drawn), side profile facing left, 8 poses with wide gaps:
calm riding, leaning forward fast, leaning into a turn, wheelie lean back, looking over the shoulder, one hand waving, driving a car with both hands
on an invisible wheel, passenger seat relaxed, [KARAKTER]
```

**K10 · Sevinç, toplama, etkileşim**

```
[STİL] animation key pose sheet, the SAME character, 8 poses with wide gaps: picking up from the ground, chopping with an axe,
mining with a pickaxe, opening a chest, drinking a potion, bandaging an arm, cheering with a fist up, pointing forward, [KARAKTER]
```

---

## 2) ZOMBİ, KÖPEK VE ASKER POZ SAYFALARI

Aynı yöntem: önce yön sayfası, sonra poz sayfaları. Zombi tarifleri V7 belgesindekilerle aynı kalsın.

**Z1 · Zombi yön sayfası (her tür için ayrı)**

```
[STİL] character turnaround sheet, the SAME [ZOMBİ TARİFİ] shown 4 times in one row with wide gaps: front, right profile, back, left profile
```

**Z2 · Zombi hareket pozları**

```
[STİL] animation key pose sheet, the SAME [ZOMBİ TARİFİ] in side profile facing right, 12 poses in two rows with wide gaps:
shamble step 1-4, lunge, bite, claw swipe, hit flinch, stagger, fall, crawling, dead
```

**Z3 · Köpek poz sayfası (alman çoban, kırmızı fular, deri yelek)**

```
[STİL] animation key pose sheet, the SAME german shepherd dog with a red scarf and a small leather vest, side profile facing right,
12 poses in two rows with wide gaps: sit, stand, walk 1-2, trot, gallop 1-3 (one airborne), jump attack, bite hold, hurt yelp, lying down
```

**Z4 · Asker poz sayfası (M4)**

```
[STİL] animation key pose sheet, the SAME olive government soldier with black plate carrier and helmet holding an M4 carbine,
side profile facing right, 10 poses in two rows with wide gaps: patrol walk 1-2, aim standing, fire recoil, crouch aim, reload,
throw grenade, hit flinch, falling, dead
```

**Z5 · Asker poz sayfası (keskin nişancı)**

```
[STİL] animation key pose sheet, the SAME ghillie-cloaked sniper with a long bolt-action rifle, side profile facing right,
8 poses with wide gaps: prone aim, kneeling aim, standing aim, bolt cycle, rifle recoil, scanning with binoculars, hit flinch, dead
```

---

## 3) UYGULAMA SİMGESİ (ana karakterin kendisi)

```
pixel art mobile app icon, square composition, the hooded survivor [KARAKTER] shown from the chest up, face fully hidden in hood shadow,
holding a katana with a red wrapped handle diagonally in front, a loyal german shepherd head peeking from the lower right,
background: ruined city skyline at blood-red sunset with a big pale moon, strong rim light on the hood edge,
bold readable silhouette at small size, crisp pixels, no text, no border
```

Alternatif (daha sade):

```
pixel art mobile app icon, the hooded survivor's head and shoulders only, empty dark hood with two faint red glints,
crossed katana and combat knife behind, deep crimson circular backdrop with cracks, crisp pixels, no text
```

---

## 4) SIRALAMA SEMBOLLERİ

**S1 · 1-2-3 madalyaları** (yan yana, boşluklu)

```
pixel art ranking medal set, 3 separate medals in a row with wide empty space: a golden crown medal with number 1 engraved
and a ruby in the center with radiating light rays, a silver laurel medal with number 2 and a sapphire, a bronze shield medal with number 3
and an amber gem, each with a short ribbon, readable at 48px, flat solid #00FF00 background
```

**S2 · İlk 10 rozeti (4-10)**

```
pixel art ranking badge, a dark steel hexagon badge with a white star and an empty space for a number, subtle engraved edge,
readable at 40px, flat solid #00FF00 background
```

**S3 · İsim etiketi çerçeveleri (unvan için: "Canavar Avcısı #1", "Oyuncu Katili #1" vb.)**

Üç özel (ilk 3) ve bir normal (4-10) çerçeve; **uzun yatay şerit**, ortası boş (yazıyı ben koyarım).

```
pixel art name tag banner set, 4 separate long horizontal banners stacked vertically with wide gaps, empty center for text:
1) gold ornate banner with wings at both ends and small flames (first place), 2) silver banner with feather wings and frost sparkles (second place),
3) bronze banner with small horns and ember glow (third place), 4) plain dark steel banner with thin trim (4th to 10th),
flat solid #00FF00 background, no text
```

**S4 · Unvan simgeleri (kategori başına)**

```
pixel art small icon set, 8 separate icons in a row with gaps, readable at 24px: a zombie skull with a crosshair (monster hunter),
a red dagger with blood drop (player killer), a titan skull with horns (titan slayer), a stacked gold coins (richest),
a flag on a fortress (base conqueror), a dog paw with a star (dog master), a hammer and wrench (master crafter),
an hourglass (veteran playtime), flat solid #00FF00 background
```

---

## 5) 9 KLAN SEVİYESİ AMBLEMİ

Hepsi aynı kalkan biçimi, seviye arttıkça daha görkemli. **Tek tek** üret ya da 3'lü satırlar halinde. Ortası boş (klan rengini ben basarım).

```
pixel art clan level emblem, a heraldic shield emblem with an empty center area, [SEVİYE TARİFİ], readable at 64px,
crisp pixels, flat solid #00FF00 background, no text
```

| Seviye | [SEVİYE TARİFİ] |
|---|---|
| 1 | plain weathered wooden shield with iron rim, simple |
| 2 | wooden shield with iron rim and two crossed nails |
| 3 | iron shield with rivets and a small skull at the top |
| 4 | steel shield with crossed knives behind it |
| 5 | steel shield with crossed katanas and a red cloth ribbon |
| 6 | dark steel shield with bat-like wings and a crimson gem |
| 7 | blackened steel shield with large wings, chains and a burning ember gem |
| 8 | obsidian shield with golden trim, big wings, crossed katanas and a glowing ruby crown |
| 9 | legendary obsidian and gold shield with huge flaming wings, a titan skull at the top, glowing crimson aura, crown and laurel |

---

## 6) ETKİNLİKLER (6 adet, her biri 2 hafta)

| # | Etkinlik | Puan |
|---|---|---|
| 1 | Kan Ayı | Zombi öldürme (güçlü zombi daha çok puan) |
| 2 | Titan Avı | Titanlara verilen hasar ve titan öldürme |
| 3 | Üs Fırtınası | Devlet üssü ele geçirme ve asker öldürme |
| 4 | Hurdacılar | Toplama ve üretim |
| 5 | Sürü Lideri | Köpeğinin öldürdükleri ve köpek seviyesi |
| 6 | Son Nefes | Ölmeden tehlikeli bölgede geçen süre |

**E0 · Etkinlik afişi (6 kez, tarifi değiştir)**

```
pixel art wide event banner illustration, [ETKİNLİK SAHNESİ], dramatic lighting, empty dark band at the bottom for the title text,
crisp pixels, no text
```

| Etkinlik | [ETKİNLİK SAHNESİ] |
|---|---|
| Kan Ayı | a huge blood-red moon over a ruined city, silhouettes of a zombie horde and a hooded survivor with a katana on a rooftop |
| Titan Avı | a gigantic spiked titan zombie with a glowing orange ribcage towering over tiny survivors, chains and fire |
| Üs Fırtınası | a military compound under attack at night, helicopter searchlight, sandbags, a clan flag being raised |
| Hurdacılar | a scrapyard at dusk with towering junk piles, a crane, sparks from a welding table, glowing crafting bench |
| Sürü Lideri | a pack of fierce german shepherds running under a full moon led by a large alpha dog with a red scarf |
| Son Nefes | a lone hooded survivor in a toxic fog wasteland, gas mask hanging from the belt, hourglass shape in the clouds |

**E1 · Ödül eşyaları** — her etkinlik için 3 özel eşya (1., 2., 3. sıra). **Takas edilemez, süreli, çok güçlü.**

```
pixel art legendary inventory icon set, 3 separate items in a row with wide gaps, each readable at 48px, crisp 1px outline,
rich glowing details, flat solid #00FF00 background: [EŞYA 1], [EŞYA 2], [EŞYA 3]
```

| Etkinlik | 1. sıra | 2. sıra | 3. sıra |
|---|---|---|---|
| Kan Ayı | Kan Ayı Kralı Yüzüğü: a black gold ring with a blood-red moon gem dripping light | Kan Ayı Şampiyonu Kolyesi: a crimson crescent moon pendant on a dark chain | Kan Ayı Muhafız Kaskı: a dark steel helmet with a red moon crest |
| Titan Avı | Titan Avı Kralı Yüzüğü: a heavy obsidian ring with a glowing orange titan rib shard | Titan Avı Şampiyonu Kolyesi: a necklace of titan teeth around a molten core | Titan Avı Muhafız Kaskı: a helmet made from a titan skull |
| Üs Fırtınası | Üs Fırtınası Kralı Yüzüğü: a military gold signet ring with a star and lightning | Üs Fırtınası Şampiyonu Kolyesi: dog tags with a golden star | Üs Fırtınası Muhafız Kaskı: a tactical helmet with gold trim and night vision |
| Hurdacılar | Hurdacılar Kralı Yüzüğü: a ring forged from gears and copper with a glowing bolt gem | Hurdacılar Şampiyonu Kolyesi: a wrench pendant with circuit engravings | Hurdacılar Muhafız Kaskı: a welding helmet with brass rivets |
| Sürü Lideri | Sürü Lideri Kralı Yüzüğü: a silver ring with a howling wolf head and blue moon gem | Sürü Lideri Şampiyonu Kolyesi: a fang necklace with a paw charm | Sürü Lideri Muhafız Kaskı: a leather helmet with wolf ears and fur |
| Son Nefes | Son Nefes Kralı Yüzüğü: a pale ring with an hourglass gem that glows green | Son Nefes Şampiyonu Kolyesi: a gas filter pendant with a green glowing vial | Son Nefes Muhafız Kaskı: a hardened gas mask helmet with green lenses |

**E2 · İlk 10 ve ilk 100 ödülleri**

```
pixel art reward icon set, 2 separate items with wide gaps: an ornate event chest with a golden lock and the event's color glow (top 10),
a small sturdy event crate with a ribbon (top 100), readable at 48px, flat solid #00FF00 background
```

---

## 7) ELMAS KASASI (PUBG sandıkları gibi)

**C1 · Kasa (kapalı / açık)**

```
[STİL] a legendary loot crate, black armored case with glowing crimson edges, diamond emblem on the lid, heavy locks, two separate versions
side by side with wide gap: closed, and open with intense light beams bursting out
```

**C2 · Açılış sahnesi arka planı** (tam ekran, arayüz arkasında durur)

```
pixel art full screen background, a dark underground vault with a circular stone pedestal in the center lit by a spotlight,
crimson and gold light streaks on the walls, floating dust particles, high-tech armored doors in the back, cinematic, empty center
for the crate, no text, no characters
```

**C3 · Nadirlik ışıkları** (siyah arka plan, kutu yok)

```
pixel art effect animation strip, 6 frames left to right with gaps, a vertical beam of light bursting upward with sparkles,
[RENK] color, peak in the middle frames then fade, flat solid #000000 background, no boxes, no text
```

[RENK] sırayla: `white-grey (sıradan)`, `green (nadir)`, `blue (epik)`, `purple (efsane)`, `gold-crimson (mitik)`.

---

## 8) EFSANE SKİNLER

Karakter skinleri yön sayfası biçiminde üretilsin (ana karakterdeki K1 gibi), böylece aynı iskelete oturur.

**SK1 · Kızıl Ronin (karakter)**

```
[STİL] character turnaround sheet, 4 views in one row with wide gaps (front, right profile, back, left profile):
a legendary ronin survivor in crimson and black lacquered samurai armor over a ragged coat, hood under a straw-like iron kasa hat,
glowing red eyes in the shadow, tattered red sash, gold trims, identical outfit in all 4 views
```

**SK2 · Gece Avcısı (karakter)**

```
[STİL] character turnaround sheet, 4 views in one row with wide gaps: a night hunter survivor in a dark navy longcoat with silver buckles,
tricorn-like hood, silver crescent moon emblem on the chest, glowing cyan eye lenses, identical outfit in all 4 views
```

**SK3 · Titan Kemiği (karakter)**

```
[STİL] character turnaround sheet, 4 views in one row with wide gaps: a survivor wearing armor crafted from titan bones and molten-orange
ribcage plates over black leather, horned bone hood, ember glow in the cracks, identical outfit in all 4 views
```

**SK4 · Kızıl Ronin motosikleti / SK5 · Titan Kemiği arabası** (motor binince ya da arabaya binince skin değişir)

```
[STİL] a legendary custom dirt motorcycle in crimson and black lacquer with gold samurai crest details and a red banner on the back,
side view three-quarter facing left, no rider
```
```
[STİL] a legendary two-seat open-top convertible built from titan bones and scrap, molten-orange glowing cracks, spiked bumper,
two empty seats visible, side view three-quarter facing left, no driver
```

---

## 9) BATTLE PASS EKRANI

**BP1 · Arka plan**

```
pixel art full screen background for a season pass menu, a ruined highway at dusk leading to a glowing fortress on the horizon,
banners along the road, warm crimson and gold light, darker edges for UI, no text, no characters
```

**BP2 · Kademe kartları**

```
pixel art UI card set, 3 separate tall reward cards side by side with wide gaps, empty center for an item icon:
a free tier card in dark steel, a premium tier card in black and gold with a crown on top, a locked card with chains and a padlock,
flat solid #00FF00 background, no text
```

**BP3 · İlerleme şeridi ve kilit**

```
pixel art UI elements, 4 separate pieces with gaps: a long horizontal progress bar frame in dark metal with golden end caps,
a small golden level node circle, a checkmark stamp, a small padlock, flat solid #00FF00 background
```

**BP4 · Görev ikonları (günlük / haftalık / sezon)**

```
pixel art icon set, 3 separate icons with gaps, readable at 32px: a sun over a scroll (daily), a calendar with seven marks (weekly),
a crown over a flag (season), flat solid #00FF00 background
```

---

## 10) BÖLGELER: ZEMİN VE BÖLGEYE ÖZEL EŞYALAR

Zemin dokuları V8'de var (R1-R5). Burada her bölgeye **3-4 dekor** ve **2 özel eşya** ekliyoruz.

**B1 · Dekor setleri (her bölge için ayrı)**

```
[STİL] isometric environment prop set, 5 separate props with wide empty space between them: [PROPLAR]
```

| Bölge | [PROPLAR] |
|---|---|
| Donmuş Liman | a snow-covered pine tree, an ice boulder, a frozen fishing boat, a rusty anchor in snow, a snowy barrel stack |
| Yanık Orman | a burnt pine tree, a smoldering log, a charred deer skeleton, a fallen burnt tree trunk, ember-glowing rocks |
| Çöl Otoyolu | a dry cactus, a sun-bleached animal skull, a broken road sign, a sand-buried traffic cone pile, a rusty gas canister |
| Kara Bataklık | a twisted swamp tree with hanging moss, a rotten log with mushrooms, reeds clump, a sunken rowboat, a glowing toxic puddle |
| Askerî Bölge | an olive tent, a stack of ammo crates, a broken jeep, a radar dish, a sandbag nest |
| Eski Şehir | a broken street lamp, an overturned bus stop, a burnt car, a trash container, a fallen billboard |

**B2 · Bölgeye özel eşyalar (simge)**

```
pixel art inventory icon set, 12 separate items in two rows with gaps, readable at 48px, crisp outline, flat solid #00FF00 background:
frozen fish, ice crystal shard, scorched resin, charcoal chunk, desert scorpion venom vial, sun-dried hide,
swamp moss bundle, bog iron ore, military circuit board, gunpowder pouch, old city gold watch, rusty city key
```

---

## 11) KAMP ÇADIRLARI (yeşil çadırın yerine)

```
[STİL] isometric survivor camp tent set, 3 separate tents with wide gaps: a patched canvas A-frame tent in muted brown and grey with
rope lines and stakes, a larger military-style ridge tent in faded olive with a closed flap and a lantern hanging,
a lean-to tarp shelter over crates, all weathered and dirty, readable silhouettes
```

---

## 12) ARAYÜZ ÇERÇEVELERİ

**F1 · Ana panel çerçevesi (daha kaliteli)**

```
pixel art UI panel frame, dark gunmetal with subtle crimson inner bevel, ornate corner plates with tiny skull rivets, thin gold inner line,
empty dark center, wide rectangle, flat solid #00FF00 background outside the frame
```

**F2 · Tuş seti (normal / basılı / kapalı)**

```
pixel art UI button set, 3 separate wide buttons stacked vertically with gaps: a crimson metal button with gold trim (normal),
the same button pressed darker and shifted down (pressed), a dark grey disabled button, empty center for text,
flat solid #00FF00 background, no text
```

**F3 · Sekme ve küçük çerçeveler**

```
pixel art UI elements, 6 separate pieces with gaps: an active tab, an inactive tab, a square item slot frame (common),
a square item slot frame with purple glow (epic), a square item slot frame with gold glow (legendary), a small close X button,
flat solid #00FF00 background
```

---

## 13) GERÇEKÇİ EFEKTLER (siyah arka plan, kutu yok, kareler arasında boşluk)

```
pixel art effect animation strip, [N] frames left to right with gaps, [EFEKT], high detail, varied particle sizes, realistic motion,
flat solid #000000 background, no boxes, no text
```

| [EFEKT] | N |
|---|---|
| a dark red blood splatter bursting sideways with droplets and a small mist | 6 |
| a heavy blood pool slowly spreading on the ground (top-down) | 5 |
| metal-on-metal sparks shower with hot orange streaks | 5 |
| a realistic muzzle flash with smoke puff | 4 |
| a fire burst with licking flames and rising embers | 8 |
| thick grey-black smoke rising and dispersing | 8 |
| a ground shockwave ring with dust and debris (titan stomp) | 6 |
| an explosion with fireball, debris and lingering smoke | 10 |
| a bone-crack hit flash with small bone fragments (critical hit) | 5 |
| a heal effect with soft green crosses and leaves spiraling up | 8 |
| a cold frost burst with ice shards (ice region hit) | 6 |
| a toxic acid splash with dripping green drops (toxic zombie) | 6 |
