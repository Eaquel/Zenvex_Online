# 8 Ayrı Bölge — Prompt'lar (V13)

Dünya artık tek bir haritanın parçaları değil: **8 bölge, her biri bambaşka bir yerde, kendi başına bir harita**.
Bölgeler birbirine bitişik değil; aralarında yürünmez, **Büyücü Aldric ışınlar**. Bir bölgenin kenarına gidince öteki
bölge **hiç görünmez**: kenarı o bölgeye özel kalın bir **sınır süsü** (kayalık, sık orman, yıkık binalar, buz uçurumu…)
kapatır, onun ötesinde de koyu bir "boşluk" zemini (derin su, sık ağaç tepesi, kanyon gölgesi…) olur.

| # | Kimlik | Bölge | Seviye | Tema | Bölgeye özel eşyalar |
|---|--------|-------|--------|------|----------------------|
| 1 | `forest` | Başlangıç Ormanı | 1–15 | Güneşli yeşil orman, nehir, ahşap köprüler, hayatta kalanların kampı | Reçine, İp |
| 2 | `city` | Yıkık Şehir | 10–25 | Çökmüş binalar, yanmış arabalar, güvenli bölge (Komutan Diana) | Elektronik Parça, Dişli, Pil, Paslı Anahtar, Altın Saat |
| 3 | `burnt` | Yanık Orman | 15–25 | Kömürleşmiş ağaçlar, kül, için için yanan korlar, duman | Kömür, Yanık Reçine |
| 4 | `desert` | Çöl Otoyolu | 20–30 | Kızıl kanyon, kum tepeleri, çatlak otoyol, terk edilmiş benzinlik | Kurutulmuş Deri, Benzin, Akrep Zehri |
| 5 | `bswamp` | Kara Bataklık | 25–35 | Kara su, çürük ağaçlar, zehirli sis, batık kulübeler | Bataklık Yosunu, Toksik Öz, Bataklık Demiri |
| 6 | `harbor` | Donmuş Liman | 40–50 | Buz tutmuş rıhtım, konteyner yığınları, donmuş gemiler, deniz feneri | Donmuş Balık, Buz Kristali, Karanlık Kristal |
| 7 | `front` | Cephe Hattı | 10+ (üsler 28–50) | Siperler, mayın tarlası, devlet ve klan üsleri; **zombi yok, yalnız askerler** | Barut Kesesi, Askerî Devre Kartı |
| 8 | `home` | Kişisel Üssüm | — | Küçük, güvenli, ahşap çitli kişisel üs; kalıcı tezgâh ve sandıklar | — |

- **Her yerde çıkan temel malzemeler:** odun, taş, kumaş, deri, paslı metal, kemik.
- **Titan** artık sabit bir arenada değil: **her 15 dakikada** Yıkık Şehir, Yanık Orman, Çöl, Bataklık ya da Liman'dan
  birinde uyanır (herkese duyurulur; büyücünün haritasında "Titan Burada" yazar). Titan Parçası yalnız ondan düşer.
- Askeri Bölge kalktı; askerî süsler Cephe Hattı'na taşındı.

## Her harita için üretilecekler

| Kod | Ne | Adet | Biçim |
|-----|----|------|-------|
| **K** | Konsept harita (yukarıdan genel görünüm) | 1 | 1:1 kare, 2048×2048 |
| **Z** | Zemin dokuları: ana, ikinci, yol/patika, **boşluk** (sınırın ötesi) | 4 | her biri ayrı, dikişsiz |
| **S** | Sınır süsleri sayfası (haritanın kenarını kapatan büyük nesneler) | 1 sayfa (6 nesne) | yeşil zemin |
| **D** | Dekor sayfası (haritanın içine serpilen süsler) | 1 sayfa (8 nesne) | yeşil zemin |
| **Y** | Simge yapılar (haritanın yer işaretleri) | 2 | yeşil zemin, tek tek |

Toplam bölge başına **9 görsel** (Kişisel Üs 5) → toplam 68 görsel + büyücü haritası + tezgâh simgesi. **Öncelik sırası:** K → Z → S → D → Y. Konsept haritayı ilk gönderirsen
yerleşimi (yollar, kamp, nehir, yapıların yeri) ona göre kurarım.

**Dosya adları:** `concept_<kimlik>.png`, `g_<kimlik>_main.png`, `g_<kimlik>_alt.png`, `g_<kimlik>_path.png`,
`g_<kimlik>_void.png`, `b_<kimlik>.png` (sınır sayfası), `d_<kimlik>.png` (dekor sayfası), `l_<kimlik>_1.png`, `l_<kimlik>_2.png`.

---

## ORTAK BLOKLAR (prompt'ların başına yapıştır)

**STİL-K (konsept harita için):**

```
top-down overview map of ONE self-contained game level for a pixel art zombie survival RPG, seen perfectly from above,
square image, rich readable detail, the whole level is enclosed on all four sides by a thick impassable border so nothing
outside the level is visible, the area beyond the border is a dark featureless void, no text, no labels, no UI, no grid
```

**STİL-Z (zemin dokusu için):**

```
seamless tileable top-down pixel art ground texture, 64x64 tile repeated 4x4, seen straight from above, no objects taller
than the ground, no shadows from objects, even lighting, crisp hard-edged pixels, no text
```

**STİL-N (sınır, dekor ve yapılar için — V11 ile aynı):**

```
high quality pixel art game sprite, crisp hard-edged pixels, 1px dark selective outline, hue-shifted shading with light
from the top-left, 3/4 top-down view (the same camera as a top-down survival RPG), nothing cropped,
flat solid #00FF00 green background, no ground, no cast shadow, no text, no frame, no watermark
```

**Sayfa eki (S ve D sayfaları için STİL-N'nin sonuna):**

```
sprite sheet, 2 rows x N columns, every object fully separate with wide empty green space between them, all objects at the
same scale, no overlapping
```

**Negatif (hepsinde):**

```
blurry, anti-aliased, smooth gradient, 3d render, photo, painterly, text, letters, numbers, logo, watermark, UI,
green objects (background is green), cropped, cut off, perspective tilt, isometric diamond tiles
```

> **Yeşil zemin uyarısı:** S, D ve Y görsellerinde saf yeşil kullanılmasın (ormanda bile ağaç yeşili koyu/zeytin olsun,
> #00FF00'a yakın olmasın), yoksa ayıklarken silinir.

> **Sınır süsleri neden büyük?** Kenar boyunca üst üste dizilip **duvar gibi** kapatacaklar. Her nesne 2-4 karo genişliğinde,
> yüksek ve opak olmalı; arasından öteye bakılamamalı.

---

## 1) `forest` — Başlangıç Ormanı (Sv 1–15)

**K · konsept** (STİL-K +)

```
a sunny green starting forest level: a survivor camp with tents, a campfire and a workbench in the center,
a winding blue river crossing from top to bottom with two wooden bridges, dirt paths connecting the camp to a small field
hospital in the west, a dog kennel in the south and an old gas station in the north-east, a flower meadow, a small toxic
pond in the south-west corner, a mysterious giant glowing tree in the east; dense dark pine forest and mossy rock cliffs
form the thick border all around
```

**Z · zemin** (STİL-Z +)

| Dosya | Tarif |
|-------|-------|
| `g_forest_main` | lush short grass with tiny flowers and clover, warm green |
| `g_forest_alt` | darker forest floor, moss, fallen pine needles and small twigs |
| `g_forest_path` | packed brown dirt path with small pebbles and footprints |
| `g_forest_void` | dense dark forest canopy seen from above, overlapping deep green and black tree tops, almost no light |

**S · sınır sayfası** (STİL-N + sayfa eki, 2×3)

```
six huge blocking border pieces for a forest edge: 1) a wall of three tall dark pine trees close together, 2) a mossy rock
cliff face 4 tiles wide, 3) a giant fallen tree trunk with roots, 4) a thick bramble and bush wall, 5) a cluster of huge
mossy boulders, 6) an old ancient oak with a massive trunk
```

**D · dekor sayfası** (STİL-N + sayfa eki, 2×4)

```
eight small forest decorations: a mushroom ring, a hollow log, a bird nest stump, a wooden signpost with no text,
a berry bush, wild lavender, a small rock with moss, an abandoned backpack
```

**Y · yapılar** (STİL-N, tek tek)

- `l_forest_1`: `a survivors' forest camp centerpiece: a large campfire with log benches, a cooking pot and a lantern pole`
- `l_forest_2`: `a mysterious giant ancient tree with glowing blue runes on its trunk and floating light motes, roots spread wide`

---

## 2) `city` — Yıkık Şehir (Sv 10–25)

**K · konsept** (STİL-K +)

```
a ruined post-apocalyptic downtown level: a grid of cracked asphalt streets with faded lane paint, collapsed apartment
blocks, burnt-out cars and buses, a central plaza with a dry fountain, a fortified safe zone in the south made of sandbags,
shipping containers and a gate, a hospital, a police station and a shopping mall as big landmarks, smoke columns;
the border all around is a continuous wall of collapsed skyscrapers, rubble mountains and piled wrecked cars
```

**Z · zemin**

| Dosya | Tarif |
|-------|-------|
| `g_city_main` | cracked dark asphalt with faded white lane paint fragments and small debris |
| `g_city_alt` | broken concrete sidewalk tiles with weeds growing through cracks |
| `g_city_path` | rubble-strewn road with brick fragments and glass shards |
| `g_city_void` | dark collapsed building roofs and rubble seen from above, deep shadows, almost black |

**S · sınır sayfası** (2×3)

```
six huge blocking city border pieces: 1) the front of a collapsed 5-storey apartment block, 2) a mountain of concrete rubble
and rebar, 3) three wrecked cars piled on top of each other, 4) a crashed city bus on its side, 5) a concrete barricade wall
with spray-painted warning marks (no letters), 6) a burnt-out shop facade with broken windows
```

**D · dekor sayfası** (2×4)

```
eight city decorations: a broken street lamp, a toppled traffic light, a fire hydrant, a dumpster with trash bags,
a bus stop shelter, a newspaper box, an abandoned shopping cart, a pile of tires
```

**Y · yapılar**

- `l_city_1`: `a fortified safe zone gate made of shipping containers, sandbags, a watchtower and a heavy steel gate, floodlights`
- `l_city_2`: `a ruined hospital entrance with a broken red cross sign shape (no letters), ambulances and stretchers`

---

## 3) `burnt` — Yanık Orman (Sv 15–25)

**K · konsept**

```
a burnt forest level: black charred tree trunks everywhere, grey ash ground, glowing orange ember cracks, drifting smoke,
a burnt ranger station in the center, a dry riverbed crossing the map, a collapsed fire lookout tower, a scorched campsite;
the border all around is a wall of fallen burning tree trunks and dark smoking ash cliffs
```

**Z · zemin**

| Dosya | Tarif |
|-------|-------|
| `g_burnt_main` | grey-black ash floor with charred twigs and tiny glowing ember dots |
| `g_burnt_alt` | scorched dry earth with cracks and burnt grass tufts |
| `g_burnt_path` | dry cracked riverbed with pale stones and ash |
| `g_burnt_void` | thick dark smoke over black ground, faint orange glow from below |

**S · sınır sayfası** (2×3)

```
six huge blocking burnt-forest border pieces: 1) a wall of charred standing tree trunks, 2) a pile of fallen burning logs
with flames, 3) a dark ash rock cliff with ember cracks, 4) a giant burnt tree stump, 5) a burnt-out forest truck on its side,
6) a smoking ash mound
```

**D · dekor sayfası** (2×4)

```
eight burnt forest decorations: smoldering log, ember rocks, a deer skeleton, a melted water tank, a burnt signpost with no
text, ash-covered bush, a charred backpack, a small fire still burning
```

**Y · yapılar**

- `l_burnt_1`: `a burnt ranger station cabin with collapsed roof and smoke rising`
- `l_burnt_2`: `a collapsed wooden fire lookout tower lying across the ground`

---

## 4) `desert` — Çöl Otoyolu (Sv 20–30)

**K · konsept**

```
a desert highway level: a long cracked highway crossing the map with sand drifts, a red rock canyon, sand dunes,
an abandoned gas station with a diner, a crashed truck convoy, a dry oasis with dead palms, an old motel;
the border all around is red sandstone canyon walls and huge dunes
```

**Z · zemin**

| Dosya | Tarif |
|-------|-------|
| `g_desert_main` | warm orange sand with fine ripples and small pebbles |
| `g_desert_alt` | cracked dry red clay ground |
| `g_desert_path` | sun-bleached cracked asphalt with faded yellow lane paint and sand drifts |
| `g_desert_void` | deep dark red canyon shadow seen from above, almost black |

**S · sınır sayfası** (2×3)

```
six huge blocking desert border pieces: 1) a red sandstone canyon wall section, 2) a tall layered rock mesa, 3) a huge
sand dune, 4) a stack of crashed semi trucks, 5) a cluster of big boulders, 6) a giant dead tree with a vulture
```

**D · dekor sayfası** (2×4)

```
eight desert decorations: saguaro cactus, cow skull, tumbleweed, an old oil barrel, a rusty road sign with no text,
a broken tire, a scorpion nest rock, a dead palm
```

**Y · yapılar**

- `l_desert_1`: `an abandoned desert gas station with a small diner, broken pumps and a faded canopy`
- `l_desert_2`: `a run-down roadside motel building with broken doors and an empty pool`

---

## 5) `bswamp` — Kara Bataklık (Sv 25–35)

**K · konsept**

```
a black swamp level: dark still water channels between muddy islands, twisted dead trees with hanging moss,
sickly yellow-olive fog, wooden plank walkways connecting islands, half-sunken shacks on stilts, a toxic glowing pool,
a sunken church tower; the border all around is deep black water with dense dead trees and a thick fog wall
```

**Z · zemin**

| Dosya | Tarif |
|-------|-------|
| `g_bswamp_main` | dark wet mud with puddles and dead leaves, olive and brown |
| `g_bswamp_alt` | shallow black swamp water with lily pads and reeds |
| `g_bswamp_path` | old wooden plank walkway boards, wet and rotten |
| `g_bswamp_void` | deep black water with faint fog, almost no detail |

**S · sınır sayfası** (2×3)

```
six huge blocking swamp border pieces: 1) a cluster of giant twisted dead trees with hanging moss, 2) a thick wall of tall
reeds and cattails, 3) a sunken rotten boat with roots, 4) a mangrove root tangle, 5) a collapsed stilt shack, 6) a huge
fallen rotting trunk covered in fungus
```

**D · dekor sayfası** (2×4)

```
eight swamp decorations: glowing toxic mushrooms, a rowboat, a lantern on a pole, a rotten crate, bones in mud,
a toxic barrel leaking, a crow on a stump, swamp flowers
```

**Y · yapılar**

- `l_bswamp_1`: `a half-sunken wooden shack on stilts with a porch and a lantern`
- `l_bswamp_2`: `a sunken stone church tower leaning out of black water`

---

## 6) `harbor` — Donmuş Liman (Sv 40–50)

**K · konsept**

```
a frozen harbor level: snowy concrete docks, frozen sea with ice sheets, stacked rusty shipping containers forming alleys,
two frozen ships locked in ice, harbor cranes, a lighthouse on a rocky point, a fish market warehouse;
the border all around is icy cliffs, a frozen open sea of dark water and towering container walls
```

**Z · zemin**

| Dosya | Tarif |
|-------|-------|
| `g_harbor_main` | snow-covered concrete dock with footprints and ice patches |
| `g_harbor_alt` | cracked blue-white sea ice |
| `g_harbor_path` | icy wooden pier boards with snow between |
| `g_harbor_void` | dark freezing sea water with small ice floes, almost black blue |

**S · sınır sayfası** (2×3)

```
six huge blocking harbor border pieces: 1) a stack of three icy shipping containers, 2) an icy rock cliff, 3) a frozen
ship hull section, 4) a harbor crane base, 5) a pile of ice blocks, 6) a snow-covered warehouse wall
```

**D · dekor sayfası** (2×4)

```
eight harbor decorations: a frozen anchor, a buoy, a snow-covered barrel stack, a frozen fishing net, a bollard with rope,
a frozen rowboat, a pallet of crates with snow, a broken forklift
```

**Y · yapılar**

- `l_harbor_1`: `a tall lighthouse on icy rocks with a lit lamp`
- `l_harbor_2`: `a large harbor crane covered in ice`

---

## 7) `front` — Cephe Hattı (zombi yok, yalnız askerler)

**K · konsept**

```
a war-torn front line level: trench lines zigzagging across muddy fields, craters, barbed wire, many small fortified
military bases with flags spread across the map, a command headquarters with a helipad in one corner, an abandoned army
camp with olive tents, a radar dish and a half-open hangar, destroyed bridges,
a minefield area; the border all around is a wall of sandbag fortifications, concrete bunkers and dense barbed wire
```

**Z · zemin**

| Dosya | Tarif |
|-------|-------|
| `g_front_main` | war-torn muddy battlefield soil with tire ruts and shell fragments |
| `g_front_alt` | dry dead grass with small craters |
| `g_front_path` | wooden duckboards over mud (trench floor) |
| `g_front_void` | dark smoke-covered no man's land, almost black |

**S · sınır sayfası** (2×3)

```
six huge blocking front-line border pieces: 1) a long sandbag fortification wall, 2) a concrete pillbox bunker,
3) a dense barbed wire barrier with wooden stakes, 4) a destroyed armored vehicle, 5) a stack of anti-tank hedgehogs,
6) a burning wreck pile
```

**D · dekor sayfası** (2×4)

```
eight battlefield decorations: shell crater, a broken field cannon, ammo boxes, a field telephone, a flag pole with a torn
flag (no symbols), a stretcher, a smoking crater, a rifle stuck in the ground with a helmet
```

**Y · yapılar**

- `l_front_1`: `a front line command headquarters tent complex with antennas and a helipad`
- `l_front_2`: `a small fortified clan base with walls, a gate, a flag pole and two watchtowers`

---

## 8) `home` — Kişisel Üssüm

**K · konsept**

```
a small cozy personal fort level in a quiet forest glade: a wooden palisade wall with a gate, a small cabin, storage chests
area, a garden, a well, a glowing purple magic portal stone near the gate; the border all around is dense pine forest
```

**Z · zemin:** `g_home_main` (soft grass with clover), `g_home_path` (flat stone path tiles)

**S · sınır sayfası** (2×3)

```
six blocking border pieces for a quiet glade: 1) a wooden palisade wall section, 2) a palisade corner tower, 3) a wall of
three pine trees, 4) a mossy rock pile, 5) a stacked firewood wall, 6) a thick hedge
```

**D · dekor sayfası** (2×4)

```
eight home decorations: a vegetable garden patch, a stone well, a rain barrel, a wooden bench, a clothesline,
a chopping block with axe, a lantern post, a flower pot
```

---

## 9) Aldric'in Haritası (8 ayrı ada)

Bölgeler yan yana değil; büyücünün haritası **karanlık büyülü bir boşlukta süzülen 8 ayrı ada**. Oyun dokunma
alanlarını şu merkezlere koyacak (yatay %, dikey %); her ada yaklaşık **genişliğin %20'si**:

| Ada | Merkez | Ada | Merkez |
|-----|--------|-----|--------|
| forest | (14, 27) | bswamp | (14, 73) |
| city | (38, 24) | harbor | (38, 76) |
| burnt | (62, 24) | front | (62, 76) |
| desert | (86, 27) | home | (86, 73) |

**`wizard_map.png`** (1:1 kare, 2048×2048):

```
an arcane wizard's atlas: eight separate small floating island maps hovering in a dark purple starry magical void,
seen from above, each island a completely different biome and clearly separated from the others with empty space between
them, arranged in two rows of four:
top row left to right: a green forest with a campfire camp and a river, a ruined grey city with broken streets,
a black burnt forest with glowing embers, an orange desert canyon with a cracked highway;
bottom row left to right: a dark toxic swamp with dead trees, a frozen snowy harbor with containers and a lighthouse,
a war-torn trench battlefield with small fortified bases and flags, a tiny cozy wooden fort with a glowing portal stone;
faint glowing purple rune circles under each island, thin magical light threads connecting them, no text, no labels
```

Bölge afişleri (`region_<kimlik>.png`, 3:1) `17_BOLGE_HARITASI_PROMPTLARI.md`'deki gibi (Askeri Bölge afişi artık gerekmez).

---

## 10) Tezgâh simgesi (yeni eşya)

Tezgâh artık **üretilen ve kurulan bir eşya**: elle üretilir (20 odun, 10 taş, 4 paslı metal, 2 ip), kullanınca bulunduğun
yere kurulur. **Kişisel üste kalıcı** (en çok 3), başka yerde **10 dakika** durur. Gelişmiş tarifler yalnız tezgâh
ekranında; el üretimi ekranında görünmez. Şu an çantada dünya tezgâhının görseli kullanılıyor; özel simge için:

**`i_workbench.png`** (STİL-N + simge eki):

```
single inventory icon, a compact portable wooden workbench with a small vise, a hammer and a saw lying on top, iron
corner brackets, object fills 80% of a square canvas, slight 3/4 angle, bold readable silhouette at 48x48 pixels
```

---

## Teslim

- Her görseli tablodaki adla gönder (ör. `g_desert_main.png`). Sayfa görsellerinde nesne sırası tarifteki sıra olsun.
- Ben: zeminleri dikişsiz 512'lik dokuya çeviririm, sayfaları keserim, yeşili silerim, ölçeği oyun metresine oturturum,
  konsept haritaya bakarak her haritanın yerleşimini (yollar, kamp, yapılar, sınır) kodla kurarım.
- Tek seferde hepsi gerekmez: bir bölgenin 9 görseli tamamlanınca o bölge yeni haliyle oyuna girebilir.
