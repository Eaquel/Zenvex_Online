# Bölge Haritası Prompt'ları (V12)

V12'de dünya **ayrı bölgelere** bölündü. Bölgeler arasında yürünmüyor (sınırın ötesi karanlık); bölgeden bölgeye
**Büyücü Aldric** ışınlıyor. Büyücüyle konuşup "Işınlan" deyince **Aldric'in Haritası** açılıyor: bir bölgeye dokununca
sağda kartı çıkıyor (önerilen seviye, yaratıklar, çıkan eşyalar, ücret, "Işınlan").

Oyun şu an haritada dünyanın piksel haritasını, kartta da yalnızca yazıları gösteriyor. Bu belgedeki görseller gelince
onların yerine **otomatik olarak** çizilmiş harita ve bölge afişleri kullanılır (kod hazır).

| # | Dosya | Ölçü | Nerede |
|---|-------|------|--------|
| 1 | `wizard_map.png` | 1:1 kare, 2048×2048 | Aldric'in Haritası'nın zemini |
| 2 | `region_forest.png` … `region_home.png` (9 adet) | 3:1 geniş, 1536×512 | Bölge kartının üstündeki afiş |

Dosyaları `android/app/src/main/assets/ui/` klasörüne koymak yeterli (ya da bana gönder). Bölge adlarını, kilitleri,
"Buradasın" işaretini ve sınır çizgilerini oyun kendisi çiziyor; **görsellerde yazı olmasın**.

---

## STİL (her prompt'un başına yapıştır)

```
high quality pixel art, detailed hand-crafted pixels, dark post-apocalyptic zombie survival game, muted gritty palette
with strong accent colors per region, hue-shifted shading, light from the top-left, no text, no letters, no numbers,
no logo, no watermark, no UI elements
```

**Negatif (hepsinde):**

```
text, letters, words, labels, numbers, compass rose with letters, legend, logo, watermark, signature, UI, frame, border,
blurry, 3d render, photo, anime characters, people in the foreground, cut off, tilted
```

---

## 1) `wizard_map.png` — Aldric'in Haritası

Büyücünün masasında açık duran **büyülü bir harita**: kuşbakışı, dümdüz yukarıdan. Bölgelerin yerleşimi **tam olarak**
aşağıdaki gibi olmalı (oyun bölge sınırlarını bu oranlara çiziyor). Haritayı 10×10'luk bir ızgara gibi düşün; her kare
genişliğin %10'u.

```
     0%        20%       40%                 80%      100%
  0% ┌───────────────────┬───────────────────┬────────┐
     │   YIKIK ŞEHİR     │   ASKERİ BÖLGE    │        │
 17% ├───────────────────┤                   │        │
     │                   ├───────────────────┤ CEPHE  │ 20%
     │ BAŞLANGIÇ ORMANI  │   YANIK ORMAN     │ HATTI  │
 40% ├─────────┬─────────┼───────────────────┤ (doğu  │
     │  KARA   │  ÇÖL    │                   │ şeridi)│
     │BATAKLIK │ OTOYOLU │   DONMUŞ LİMAN    │        │
     │         │         │               [Ü] │        │ ← [Ü] kişisel üs: küçük surlu kare (x %76–79, y %76–79)
 80% ├─────────┴─────────┴───────────────────┤        │
     │      CEPHE HATTI (güney şeridi)                │
100% └────────────────────────────────────────────────┘
```

**Prompt:**

```
an enchanted top-down fantasy-survival world map drawn on old dark parchment, seen perfectly from above, square image,
the land is divided into clearly separate regions exactly like this layout:
top-left 40% wide and 17% tall: a ruined grey city with a grid of broken streets, collapsed apartment blocks and a small
walled safe zone; a long stone city wall runs along its bottom edge;
below it (left 40%, from 17% to 40% height): a green starting forest with a small campfire camp, dirt paths and a toxic pond
in the lower left;
top middle (40% to 80% wide, top 20%): a military zone with barracks, hangars, a radar dish and a runway, olive and khaki colors;
under it (40% to 80% wide, 20% to 40% height): a burnt forest of black charred trees, grey ash and glowing orange embers;
left lower (0 to 20% wide, 40% to 80% height): a black toxic swamp with dark water, dead trees and sickly fog;
next to it (20% to 40% wide, 40% to 80% height): a sandy desert highway with cracked asphalt, wrecked cars and cacti;
center lower (40% to 80% wide, 40% to 80% height): a frozen snowy harbor with icy docks, shipping containers and frozen boats,
and a tiny walled personal fort in its bottom-right corner;
an L-shaped war-torn front line strip along the entire right edge (last 20% of the width) and the entire bottom edge
(last 20% of the height): trenches, sandbags, craters, barbed wire and many small fortified military bases with flags;
each region separated by a thin dark magical boundary of faint purple mist, slightly glowing arcane runes in the corners
of the parchment, rich detail, readable at small size
```

> Yapay zekâ bölgeleri tam bu oranlarda çizmeyebilir; gelen görseli ben oranlara göre hizalar/gererim. En önemlisi:
> **sıralama** (hangisi nerede) ve **sağda + altta L şeklinde cephe şeridi**.

---

## 2) Bölge afişleri — `region_<id>.png` (3:1)

Kartın üstünde görünen geniş manzara. Ortası önemli (kenarlar kırpılabilir). Her birinin başına STİL'i ekle ve şu ekleri
kullan:

```
wide cinematic 3:1 landscape banner, 3/4 top-down view of the location, atmospheric lighting, no characters in focus
```

| Dosya | Prompt |
|-------|--------|
| `region_forest.png` | a calm green forest clearing at dusk with a small survivor campfire camp, tents and a wooden workbench, dirt path leading into darker woods, a few distant shambling zombie silhouettes between the trees |
| `region_city.png` | a ruined city street at dusk, collapsed apartment blocks, burnt cars, broken traffic lights, a fortified safe zone gate with sandbags on one side, smoke columns rising in the distance |
| `region_burnt.png` | a burnt forest, black charred tree trunks, grey ash ground, glowing orange embers and smoke drifting, red-orange sky light, a fast zombie running through the smoke |
| `region_desert.png` | a sun-bleached desert highway, cracked asphalt with faded lines, half-buried wrecked cars, cacti, a cow skull, heat haze, a hulking tank zombie in the distance |
| `region_bswamp.png` | a black toxic swamp at night, dark still water, dead twisted trees with hanging moss, sickly yellow-green fog (use olive, not pure green), glowing toxic puddles, a bloated toxic zombie wading |
| `region_military.png` | an abandoned military base, olive tents, sandbag nests, a radar dish, a hangar with open doors, ammo crates, searchlights, an armored tank zombie |
| `region_harbor.png` | a frozen harbor at night, snow-covered docks, stacked rusty shipping containers coated in ice, a frozen fishing boat, a lonely lighthouse beam, dark zombies on the ice |
| `region_front.png` | a war-torn front line, trenches and barbed wire, a fortified state base with a waving flag and watchtowers, soldiers behind sandbags, a helicopter approaching, smoke and tracers in the dusk sky |
| `region_home.png` | a small cozy walled personal fort, wooden palisade, a bed under a roof, storage chests, a rain barrel and a stove, warm lantern light, a glowing purple magic portal stone at the gate |

> Bataklık gibi zehirli yerlerde **saf yeşil kullanılmasın** demiştik; burada arka plan silinmediği için sorun değil,
> ama oyunun geri kalanıyla uyum için zeytin/camgöbeği tonları daha iyi duruyor.

---

## Oyunda değişenler (özet)

- **Bölgeler:** Başlangıç Ormanı (Sv 1+), Yıkık Şehir (Sv 10+), Cephe Hattı (Sv 10+), Yanık Orman (Sv 15+), Çöl Otoyolu (Sv 20+),
  Kara Bataklık (Sv 25+), Askeri Bölge (Sv 35+), Donmuş Liman (Sv 40+), Kişisel Üssüm.
- **Sınır:** bölgenin kenarı görünmez duvar; ötesi koyulaşan sisle kararır, mini harita ve harita da yalnız bulunulan bölgeyi gösterir.
- **Büyücü:** her bölgenin giriş karakolunda bir Aldric var. Işınlanma 15 altın (orman ve kişisel üs ücretsiz), 8 sn bekleme.
- **Ölünce:** öldüğün bölgenin giriş karakolunda doğarsın (kişisel üs seçiliyse orada).
- **Görev oku:** hedef başka bölgedeyse ok bu bölgenin büyücüsünü gösterir ("Büyücü → Yanık Orman").
- **Cephe Hattı:** üslerin çevresinde zombi doğmaz; yalnız üs askerleri (botlar) var.
