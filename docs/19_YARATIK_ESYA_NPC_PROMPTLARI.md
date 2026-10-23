# Bölge Yaratıkları, Eşya Simgeleri ve NPC'ler — Prompt'lar (V13)

Bu belge diğer prompt belgelerinden **ayrı**. İçinde:

1. **Bölgeye özel yaratıklar** (20 yaratık) — her biri **8 yönde** animasyonlu
2. **Eksik eşya simgeleri** (üretim ekranında ve çantada görünen 17 simge)
3. **NPC'ler** — her haritada nerede durdukları, Gezgin Tüccar Marco'nun kamyoneti, Marco'nun yürüme animasyonu

---

## 8 yön nasıl çalışıyor?

Oyun kamerası 3/4 yukarıdan bakar. 8 yön: **G** (güney, bize bakan), **GD**, **D** (sağ), **KD**, **K** (kuzey, sırtı dönük),
**KB**, **B** (sol), **GB**. Sol taraftaki 3 yön (GB, B, KB) sağdakilerin aynası olarak oyunda çevrilir; bu yüzden
**her animasyon için 5 yön** yeterli: **G, GD, D, KD, K**.

Her yaratık için **6 görsel** üretilir:

| # | Sayfa | Satırlar | Kareler (her satırda) |
|---|-------|----------|----------------------|
| 1 | **Yön sayfası** (referans) | 1 satır, 5 yön (G, GD, D, KD, K) | 1 duruş |
| 2 | **Bekleme** (idle) | 5 satır = 5 yön | 4 kare (nefes, sallanma) |
| 3 | **Yürüme** | 5 satır = 5 yön | 6 kare |
| 4 | **Saldırı** | 5 satır = 5 yön | 6 kare |
| 5 | **Vurulma** | 5 satır = 5 yön | 2 kare |
| 6 | **Ölüm** | 1 satır (yalnız G ve D yeter: 2 satır) | 6 kare (yere yığılır) |

**Tutarlılık:** önce yön sayfasını üret; beğendiğin sonucu **referans görsel** olarak verip diğer 5 sayfayı onunla üret.
Her sayfada aynı yaratık tarifini **kelimesi kelimesine** kullan. Kareler arasında geniş boşluk olsun, kare kare aynı boy.

> Yapay zekâ bazen bir satırı bozar; bana gönderdiğinde bozuk kareleri ben ayıklar, eksik ara kareleri iskelete bağlayıp
> kodla üretirim. 5 yön × 5 animasyon ≈ **120 kare** her yaratık için.

**Dosya adları:** `z_<kimlik>_dirs.png`, `z_<kimlik>_idle.png`, `z_<kimlik>_walk.png`, `z_<kimlik>_attack.png`,
`z_<kimlik>_hurt.png`, `z_<kimlik>_death.png`

---

## ORTAK BLOKLAR

**STİL (her yaratık sayfasının başına):**

```
high quality pixel art game sprite sheet, crisp hard-edged pixels, 1px dark selective outline, gritty post-apocalyptic
palette, hue-shifted shading with light from the top-left, 3/4 top-down view (the same camera as a top-down survival RPG),
every frame the SAME character at the SAME scale, frames evenly spaced in a clean grid with wide empty gaps,
flat solid #00FF00 green background, no ground, no cast shadow, no text, no numbers, no labels, no frame borders
```

**Sayfa ekleri (STİL'in sonuna, ilgili olanı):**

- Yön sayfası: `character turnaround in one row, 5 views left to right: facing the camera (south), three-quarter front right (south-east), right profile (east), three-quarter back right (north-east), back view (north)`
- Bekleme: `idle animation sheet, 5 rows (south, south-east, east, north-east, north), 4 frames per row: subtle breathing and swaying`
- Yürüme: `walk cycle animation sheet, 5 rows (south, south-east, east, north-east, north), 6 frames per row: a full looping walk cycle`
- Saldırı: `attack animation sheet, 5 rows (south, south-east, east, north-east, north), 6 frames per row: wind-up, strike, follow-through, recover`
- Vurulma: `hit reaction sheet, 5 rows (south, south-east, east, north-east, north), 2 frames per row: flinch back, recover`
- Ölüm: `death animation sheet, 2 rows (south, east), 6 frames per row: stagger, fall, collapse on the ground, still`

**Negatif (hepsinde):**

```
blurry, anti-aliased, 3d render, photo, painterly, inconsistent character between frames, different sizes, cropped limbs,
overlapping frames, text, numbers, watermark, green colored parts (background is green), cast shadow, ground tiles
```

> **Yeşil uyarısı:** zemin #00FF00. Zehirli/yosunlu yaratıklarda yeşil yerine **zeytin, sarımsı yeşil, camgöbeği** kullan.

---

## 1) YARATIKLAR

Her bölge kendi yaratıklarıyla gelir; genel zombiler bölgeye göre bunlarla değişecek. Seviyeler bölgenin seviyesini izler.
Tablodaki "Tarif" metnini sayfalarda **[TARİF]** yerine aynen kullan:

```
[STİL] [SAYFA EKİ], the SAME [TARİF]
```

### Başlangıç Ormanı (Sv 1–15)

| Kimlik | Ad | Davranış | Tarif |
|--------|----|----------|-------|
| `lumberjack` | Çürümüş Oduncu | yavaş, ağır vuruş | `rotting zombie lumberjack in a torn red flannel shirt and suspenders, pale grey-green skin, a rusty axe stuck in his back, shuffling, one boot missing` |
| `wolf` | Kuduz Kurt | hızlı, sürüyle | `infected grey wolf with patchy fur, exposed ribs, glowing pale yellow eyes, foaming mouth, low hunting stance` |
| `shroom` | Mantar Zombi | yavaş, ölünce spor bulutu | `slow zombie overgrown with brown and orange mushrooms sprouting from shoulders and head, mossy tattered hiking clothes, dripping spores` |

### Yıkık Şehir (Sv 10–25)

| Kimlik | Ad | Davranış | Tarif |
|--------|----|----------|-------|
| `office` | Ofis Zombisi | koşucu | `fast zombie office worker in a torn white shirt, loose blue tie and dark trousers, broken glasses, bloody hands reaching forward` |
| `riot` | Çevik Kuvvet Zombisi | önden kalkanla korunur | `armored riot police zombie with a cracked black helmet visor, body armor and a dented transparent riot shield, heavy and slow` |
| `crawler` | Kanalizasyon Sürüngeni | yerden fırlar, düşük | `mutated pale crawling creature from the sewers, long thin limbs, no legs below the knees, wide jaw with many teeth, wet grey skin` |

### Yanık Orman (Sv 15–25)

| Kimlik | Ad | Davranış | Tarif |
|--------|----|----------|-------|
| `charred` | Kömür Zombi | arkasında kor izi | `charred burnt zombie with black cracked skin and glowing orange ember cracks, smoke rising from its shoulders, scraps of burnt clothing` |
| `emberhound` | Kor Tazısı | çok hızlı, ateşli ısırık | `skinless burning hound made of charcoal and glowing embers, flaming mane, orange eyes, sparks falling from its body` |
| `ashwraith` | Kül Hayaleti | yarı saydam, uçar | `floating ghost made of grey ash and smoke, hollow glowing ember eyes, long tattered arms, lower body fading into smoke` |

### Çöl Otoyolu (Sv 20–30)

| Kimlik | Ad | Davranış | Tarif |
|--------|----|----------|-------|
| `mummy` | Kumcul Gezgin | uzun menzilli tükürük | `desert zombie wrapped in sun-bleached cloth and a torn shemagh scarf, dusty goggles, dried leathery skin, sand falling from its wraps` |
| `scorpion` | Dev Akrep | zehirli kuyruk | `giant mutant desert scorpion the size of a dog, cracked rust-red armored shell, glowing amber venom drop on the tail stinger` |
| `biker` | Motorcu Zombi | ağır, zincirli | `big zombie biker in a sleeveless black leather vest with spikes, bandana, swinging a heavy rusty chain, tattooed grey arms` |

### Kara Bataklık (Sv 25–35)

| Kimlik | Ad | Davranış | Tarif |
|--------|----|----------|-------|
| `bloater` | Şişkin Zombi | ölünce zehir patlaması | `bloated swollen zombie with stretched purple-grey skin, glowing sickly yellow boils, torn fishing waders, waddling` |
| `boggiant` | Bataklık Devi | dev, yavaş, elit | `huge hulking swamp giant made of black mud, rotten roots and dead branches, moss hanging from its arms, two pale glowing eyes` |
| `witch` | Bataklık Cadısı | büyü topu atar | `gaunt masked swamp witch zombie in a ragged dark hooded cloak, a bone mask with a painted eye, bony fingers holding a glowing purple orb` |

### Donmuş Liman (Sv 40–50)

| Kimlik | Ad | Davranış | Tarif |
|--------|----|----------|-------|
| `sailor` | Donmuş Denizci | zıpkın atar | `frozen zombie sailor in a navy peacoat and knit cap covered in frost and icicles, pale blue skin, holding a rusty harpoon` |
| `icecrab` | Buz Yengeci | kalın kabuk | `giant mutant crab with an ice-encrusted blue-grey shell, frost crystals on its back, huge uneven claws` |
| `dockbrute` | Liman Devi | elit, zincirle savurur | `massive frozen dock worker brute zombie in a torn orange safety vest, frost on its shoulders, heavy anchor chain wrapped around one arm` |

### Cephe Hattı (zombi yok — üs botları)

| Kimlik | Ad | Davranış | Tarif |
|--------|----|----------|-------|
| `shieldbot` | Kalkanlı Asker | önden ilerler, kalkan | `government soldier in olive armor and black helmet holding a heavy ballistic shield with a viewport and a pistol` |
| `flamebot` | Alev Makineli Asker | kısa menzil alev | `government soldier in a heavy fire-resistant suit and gas mask carrying a flamethrower with fuel tanks on the back` |

---

## 2) EKSİK EŞYA SİMGELERİ

Bunlar oyunda var ama kendi simgesi yok (şimdilik yedek görsel çıkıyor). Üretim ekranında, çantada ve dükkânda görünürler.

**Simge STİL'i:**

```
high quality pixel art inventory icon, crisp hard-edged pixels, 1px dark outline, single object filling 80% of a square
canvas, slight 3/4 angle, hue-shifted shading with light from the top-left, readable silhouette at 48x48 pixels,
flat solid #00FF00 green background, no text, no frame
```

| Dosya | Tarif |
|-------|-------|
| `i_t_axe_stone` | a primitive stone axe: a sharpened grey stone lashed to a wooden handle with rope |
| `i_t_axe_iron` | a sturdy iron hatchet with a worn wooden handle and a leather grip |
| `i_t_pick_stone` | a primitive stone pickaxe: a pointed rock tied to a wooden handle with rope |
| `i_t_pick_iron` | an iron pickaxe with a curved steel head and a wooden handle |
| `i_v_wheel` | a single chunky off-road wheel with a worn tire and a rusty rim |
| `i_v_engine` | a compact greasy motorcycle-sized engine block with pipes and a small fuel line |
| `i_v_frame_moto` | a bare welded motorcycle frame without wheels, rusty steel tubes |
| `i_v_frame_car` | a bare rusty jeep chassis frame seen from 3/4, no wheels |
| `i_veh_moto` | a motorcycle key on a ring with a small leather tag |
| `i_veh_car` | a car key with a black plastic head on a ring with a small metal tag |
| `i_workbench` | a compact portable wooden workbench with a small vise, a hammer and a saw on top, iron corner brackets |
| `i_ev_blood_weapon` | a crimson war scythe with a curved blood-red blade and a bone handle, dripping |
| `i_ev_titan_weapon` | a huge stone-and-steel sledgehammer with glowing orange cracks |
| `i_ev_base_weapon` | a military battle axe with an olive handle and a tactical steel head |
| `i_ev_scrap_weapon` | a scrap saw-sword made from a circular saw blade welded to a pipe |
| `i_ev_pack_weapon` | a wolf-claw gauntlet with three long curved blades |
| `i_ev_breath_weapon` | a long dark scythe with a pale ghostly blade and wisps of mist |

---

## 3) NPC'LER — HER HARİTADA NEREDE?

Bölgeler ayrı harita olunca her haritada NPC'ler **sabit yerlerde** durur. Konsept haritaları (`18_HARITA_PROMPTLARI.md`)
çizerken bu yerleri bırak (küçük bir meydan, çadır, ateş).

| Bölge | Güvenli giriş karakolu | Başka yerler |
|-------|------------------------|--------------|
| Başlangıç Ormanı | Kamp ateşi: **Rehber Nova**, **Görevci Silas**, **Büyücü Aldric** | Sahra hastanesi: **Doktor Elena** · Benzinlik: **Tamirci Viktor** · Barınak: **Eğitmen Jack** |
| Yıkık Şehir | Güvenli bölge pazarı: **Silas**, Elena, Viktor, Jack, **Aldric** | Güvenli bölge kapısı: **Komutan Diana** |
| Yanık Orman | Kül Karakolu: **Aldric**, **Silas** | — |
| Çöl Otoyolu | Çöl Karakolu: **Aldric**, **Silas** | — |
| Kara Bataklık | Kara Bataklık Karakolu: **Aldric**, **Silas** | — |
| Donmuş Liman | Liman Karakolu: **Aldric**, **Silas** | — |
| Cephe Hattı | Cephe Karargâhı: **Aldric**, Silas, Elena, Viktor, Jack | — |
| Kişisel Üssüm | Portal taşının yanında: **Aldric** | — |

- **Görevler:** **Rehber Nova** oyunun başında **kendiliğinden** yol gösterir (görev kabul etmek gerekmez). Görevleri güvenli
  bölgelerde **Yaşlı Silas** verir. Şehir ve üs öğreticisi **Komutan Diana**'dadır.
- **Gezgin Tüccar Marco:** sabit yeri yok. Her bölgede kamyonetiyle **20 dakikada bir** başka bir karakola gelir, park eder,
  satış yapar ve gider. Her gelişinde başka mallar getirir (dükkânda "Gelme Olasılığı" yazar; herkes aynı malları görür).

### 3.1 Tüccar Marco'nun kamyoneti (8 yön)

Şu an oyundaki cip kullanılıyor. Kendi kamyoneti için **tek sayfada 8 yön** (oyun `x8/truck/0..7` olarak keser):

```
[STİL] vehicle sprite sheet, the SAME battered old merchant pickup truck in 8 directions in one row with wide gaps:
south, south-east, east, north-east, north, north-west, west, south-west; rusty olive-green paint, a canvas-covered cargo bed
piled with crates, sacks and barrels, a roof rack with a spare tire and a hanging lantern, a hand-painted wooden sign board
on the side (no letters), mud on the wheels
```

Dosya: `x8_truck.png`

### 3.2 Tüccar Marco yürüme ve bekleme (8 yön)

Marco artık kamyonetinden inip yanında bekliyor; ileride yürüyecek. Yukarıdaki 8 yön yöntemiyle (5 yön, aynalanır):

**[TARİF]:** `traveling merchant in his fifties with a grey beard, a long brown leather duster coat, a wide-brimmed hat, a big backpack full of goods with pots and lanterns hanging from it, friendly grin`

Sayfalar: `npc_marco_dirs.png`, `npc_marco_idle.png`, `npc_marco_walk.png`

---

## Teslim

- Bir yaratığın 6 sayfası tamamlanınca o yaratık oyuna girebilir; hepsinin birden gelmesi gerekmez.
- Öncelik önerisi: her bölgeden **ilk yaratık** (lumberjack, office, charred, mummy, bloater, sailor) → kamyonet → simgeler →
  diğer yaratıklar.
- Gönderdiğinde ben: yeşili silerim, kareleri ızgaradan keserim, 5 yönü 8'e aynalarım, ölçeği oyun metresine oturturum,
  eksik ara kareleri üretirim ve yaratığı bölgesinin doğma tablosuna eklerim.
