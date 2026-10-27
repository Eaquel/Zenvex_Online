# Eşya Simgeleri — Yeniden Üretim Prompt'ları (Setler, Kolye, Yüzük, Çanta, Mermi, Sandık)

Bu belge çantada, üretim ekranında, dükkânda ve haritada görünen **zırh setlerini, kolye ve yüzükleri, çantaları, mermileri ve
sandıkları** baştan üretmen içindir. Yazı hiçbir görselin içinde olmaz; adı ve nadirliği oyun koyar (10 dil).

Oyundaki eşya kimlikleri (`rust/zenvex_core/src/data.rs`) dosya adı olur: **`i_<kimlik>.png`** (ör. `i_helm_police.png`).
Şu an oyunda **4 set kademesi** var: **Basit → Polis → Askerî → Titan** (nadirlik: Sıradan, Nadir, Destansı, Efsanevi).

## 0. Ortak kurallar

**STİL satırı (her istemin başına ekle):**

```
high quality hand-polished pixel art inventory icon, crisp hard-edged pixels, clean 1px dark outline, a single object
filling 80% of a square canvas, slight 3/4 front angle, hue-shifted shading with the main light from the top-left and a
small rim light, rich material detail (stitching, scratches, rivets, wear), strong readable silhouette that still reads at
48x48 pixels, flat solid #00FF00 green background, no text, no letters, no frame, no shadow on the ground, no hands
```

**Negatif (varsa):** `blurry, anti-aliased soft edges, photo, 3d render, text, logo, watermark, frame, ground shadow, extra objects, cropped`

**Kademe dili (dört set de aynı dilde olsun):**

| Kademe | Nadirlik | Malzeme ve renk dili | Ayırt edici işaret |
|---|---|---|---|
| **Basit** | Sıradan | derme çatma: bez, ham deri, koli bandı, paslı metal; kirli kahve-gri | bant ve ip yamaları, eğri dikişler |
| **Polis** | Nadir | koyu lacivert kevlar ve plastik koruyucular; mat siyah donanım | sarı-beyaz reflektör şerit, kabartma rozet (harfsiz) |
| **Askerî** | Destansı | zeytin yeşili ve çöl kamuflajı, taktik naylon, çelik plakalar | MOLLE şeritleri, tokalar, kum rengi bant |
| **Titan** | Efsanevi | kara demir ve kemik plakalar, **çatlaklarından sızan turuncu magma ışığı** | dikenli omuz/diz çıkıntıları, parlayan çatlaklar |

**Tutarlılık için:** her kademenin 4 parçasını önce **tek tabloda** ürettir (aşağıda "Set tablosu"), beğendiğin tablodan parçaları kes; ayrı
ayrı istenen tek parça komutları aynı seed/stil referansıyla çalışsın. Aynı kademede renk paleti ve malzeme değişmemeli.

---

## 1. Zırh setleri (4 kademe × 4 parça = 16 simge)

### 1.1 Set tablosu (kademe başına bir kez; parçaları sonra kes)

```
[STİL] a four-piece armor set laid out in a 2x2 grid with generous gaps on one canvas: top-left a helmet, top-right a torso
vest/jacket, bottom-left a pair of cargo/combat trousers (flat view), bottom-right a pair of boots; all four pieces share the
same materials, palette and wear: [KADEME TARİFİ]
```

`[KADEME TARİFİ]` yerine:

- **Basit:** `scavenged survivor gear: a battered cycling helmet patched with duct tape, a quilted vest stitched from old jackets with rope straps, worn cargo trousers with knee patches, scuffed leather boots tied with cord; dirty brown-grey palette`
- **Polis:** `riot police gear: a navy riot helmet with a clear scratched visor, a navy kevlar vest with yellow-white reflective strips and a blank embossed badge, navy trousers with hard knee pads, black tactical boots; matte navy and black palette`
- **Askerî:** `military combat gear: an olive drab combat helmet with a netting cover and a mount rail, a plate-carrier jacket with MOLLE webbing and pouches, olive camo trousers with cargo pockets, tan combat boots with steel toe; olive, tan and gunmetal palette`
- **Titan:** `titan-forged gear: a black iron horned helmet with bone plates and a glowing orange magma crack across the brow, a heavy cuirass jacket of black iron and bone with glowing magma seams and spiked shoulders, black iron greaves trousers with spiked knees and magma cracks, heavy black boots with glowing seams; black, bone-white and molten orange palette`

### 1.2 Tek parça komutları (16 dosya)

Her komut: `[STİL] ` + aşağıdaki tarif. (Kademe dilini yukarıdaki satırdan al.)

| Dosya | Eşya (oyundaki adı) | Tarif |
|---|---|---|
| `i_helm_basic` | Basit Kask | a battered cycling helmet patched with duct tape and a strap of rope, scuffed, dirty brown-grey |
| `i_helm_police` | Polis Kaskı | a navy riot helmet with a clear scratched face visor, black padding, a small blank embossed badge on the forehead |
| `i_helm_military` | Askerî Kask | an olive drab combat helmet with a camouflage net cover, a front mount rail and a chin strap |
| `i_helm_titan` | Titan Kaskı | a black iron horned helmet with bone plates, glowing orange magma cracks across the brow, jagged teeth-like visor slit |
| `i_jacket_basic` | Basit Yelek | a quilted scrap vest stitched from old jackets, rope straps, tape patches, worn and stained |
| `i_jacket_police` | Polis Yeleği | a navy kevlar vest with yellow-white reflective strips, side buckles and a blank embossed badge |
| `i_jacket_military` | Askerî Ceket | an olive plate-carrier jacket with MOLLE webbing, ammo pouches, shoulder pads and a tan cummerbund |
| `i_jacket_titan` | Titan Ceketi | a heavy black iron and bone cuirass jacket with spiked shoulder guards and glowing orange magma seams |
| `i_pants_basic` | Kargo Pantolon | worn khaki cargo trousers with knee patches, frayed hems and a rope belt |
| `i_pants_police` | Dizlikli Pantolon | navy tactical trousers with hard plastic knee pads, side pockets and a utility belt |
| `i_pants_military` | Askerî Pantolon | olive camouflage combat trousers with large cargo pockets, knee pads and tan webbing belt |
| `i_pants_titan` | Titan Pantolonu | black iron greaves-style trousers with bone knee spikes, armored thigh plates and glowing magma cracks |
| `i_boots_basic` | Basit Bot | scuffed brown leather boots tied with cord, mismatched laces, a taped sole |
| `i_boots_police` | Polis Botu | black matte tactical boots with a side zipper, reinforced toe and a thin reflective stripe |
| `i_boots_military` | Askerî Bot | tan combat boots with steel toe caps, olive laces and a thick rugged sole |
| `i_boots_titan` | Titan Botu | heavy black iron boots with bone spikes at the ankle and glowing orange seams along the sole |

---

## 2. Kolyeler (3 simge)

| Dosya | Eşya | Nadirlik | Tarif |
|---|---|---|---|
| `i_neck_bone` | Kemik Kolye | Sıradan | a necklace of rough carved bones and a small animal skull on a frayed leather cord, a few beads |
| `i_neck_amulet` | Koruyucu Muska | Nadir | a silver protective amulet with a blue glowing gem in the centre, engraved rune-like patterns (no letters), a fine chain |
| `i_neck_dark` | Karanlık Kolye | Destansı | a dark obsidian pendant shaped like a thorned crescent with a violet glowing core, black chain with thorny links |

## 3. Yüzükler (3 simge)

| Dosya | Eşya | Nadirlik | Tarif |
|---|---|---|---|
| `i_ring_copper` | Bakır Yüzük | Sıradan | a plain hammered copper ring with a green patina in the grooves |
| `i_ring_hunter` | Avcı Yüzüğü | Nadir | a dark steel signet ring with a claw-mark engraving and a small red gem inlay |
| `i_ring_titan` | Titan Yüzüğü | Efsanevi | a massive black iron ring with bone spikes and a glowing orange magma gem set in the top |

## 4. Çantalar (3 simge)

| Dosya | Eşya | Nadirlik | Tarif |
|---|---|---|---|
| `i_bag_basic` | Basit Çanta | Sıradan | a small patched canvas satchel with a rope strap and one buckle, brown-grey, tape repairs |
| `i_bag_police` | Polis Çantası | Nadir | a navy tactical shoulder bag with yellow-white reflective strips, two zip pockets and a blank badge patch |
| `i_bag_military` | Askerî Çanta | Destansı | a large olive military rucksack with MOLLE webbing, side pouches, a rolled sleeping mat on top and steel buckles |

## 5. Silah mermileri (5 simge)

Her simge bir **mermi kutusu** ve önünde dizili **2-3 mermi**: kutu rengi kalibreye göre ayrışsın (oyunda sıradan/nadir/destansı ışıması kodla eklenir).

| Dosya | Eşya | Kullanıldığı silah | Tarif |
|---|---|---|---|
| `i_ammo_9mm` | 9 mm Mermi | Glock/tabanca | a small brass-and-grey cardboard box of 9mm pistol cartridges, two short brass cartridges with rounded tips standing in front |
| `i_ammo_762` | 7,62 mm Mermi | AK-47 | a green metal ammo tin of 7.62 rifle cartridges, three medium brass cartridges with copper-jacket tips in front |
| `i_ammo_556` | 5,56 mm Mermi | M4 | a tan cardboard box of 5.56 rifle cartridges with a small green stripe, three slim brass cartridges with green-tipped bullets |
| `i_ammo_308` | .308 Mermi | Keskin nişancı | a dark wooden-lidded box of .308 sniper cartridges, two long polished brass cartridges with pointed boat-tail bullets in front |
| `i_ammo_rocket` | Roket | RPG | a single olive RPG-7 rocket-propelled grenade standing upright with a bulbous warhead and a finned tail, a rope-bound wooden crate beside it |

## 6. Sandıklar (3 eşya simgesi × kapalı + açık = 6 görsel)

Sandık hem **eşya simgesi** hem de **haritada/üste kurulu nesne** olarak çizilir. Oyun yakına gelince `_open` görselini kullanır; bu
yüzden her sandık için **kapalı** ve **açık** hâlini aynı kamera açısıyla üret (açı: 3/4 önden, hafif yukarıdan).

| Dosya | Eşya | Nadirlik | Kapalı tarif | Açık tarif |
|---|---|---|---|---|
| `i_chest_wood` / `i_chest_wood_open` | Ahşap Sandık | Sıradan | a sturdy wooden storage chest with iron corner brackets and a simple latch, weathered planks | the same chest with the lid open, dark interior with a few folded cloths and a lantern glow inside |
| `i_chest_iron` / `i_chest_iron_open` | Demir Sandık | Nadir | a riveted dark-iron storage chest with a heavy padlock and reinforced straps, subtle rust | the same chest with the lid open, a padded interior with bolts and gears visible |
| `i_chest_military` / `i_chest_military_open` | Askerî Sandık | Destansı | an olive military supply crate with steel latches, stencil-like stripes (no letters) and carrying handles | the same crate with the lid open, foam-padded compartments with ammo boxes and a med kit |

---

## 7. Etkinlik ödülleri (isteğe bağlı, 6 etkinlik × 3 = 18 simge)

Etkinlik ödülleri 1. = **Yüzük**, 2. = **Kolye**, 3. = **Muhafız Kaskı**. Her etkinliğin teması farklı; üç parça aynı temada olsun.
Komut: `[STİL] ` + `[PARÇA]` + `, ` + `[TEMA]`. Parçalar: yüzük = `a signet ring`, kolye = `a pendant necklace on a chain`, kask = `a guardian helmet`.

| Etkinlik | Dosya ön eki | Tema |
|---|---|---|
| Kan Ayı | `i_ev_blood_` (`ring`, `neck`, `helm`) | crimson blood-moon theme: deep red metal, a crescent moon motif, dripping red gems and bone details |
| Titan Avı | `i_ev_titan_` | molten titan theme: black stone and steel with glowing orange cracks and heavy horns |
| Üs Fırtınası | `i_ev_base_` | military storm theme: olive steel, electric blue lightning engravings, rivets and a star-less emblem |
| Hurdacılar | `i_ev_scrap_` | scrapper theme: welded rusty gears, bolts, wire wrapping and a sawblade motif |
| Sürü Lideri | `i_ev_pack_` | wolf pack leader theme: fang and claw shapes, grey fur trim, amber eyes-like gems |
| Son Nefes | `i_ev_breath_` | last-breath theme: pale ghostly silver, cold blue wisps of mist and a faint skull motif |

---

## 8. Teslim ve kontrol

- Her simge **512×512 PNG**, düz **#00FF00** arka plan (yeşil nesnede kullanma: çanta/mermi renklerinde saf yeşil kullanma, zeytin ya da koyu yeşil olsun).
- Kontrol: yazı yok · kesilmiş parça yok · tek nesne (mermi: kutu + mermiler birlikte) · 48×48'e küçülünce siluet okunuyor · aynı kademenin 4 parçası aynı palet ve aşınmada.
- Sandık çiftlerinde (kapalı/açık) kamera açısı, boyut ve konum aynı olmalı (oyun ikisini aynı yere çizer).
- Dosyaları `art/items/` klasörüne koy (depoya girmez; yalnız son ürün `android/app/src/main/assets/ui` ve atlas'a işlenir). Üretince haber ver: yeşil
  arka planı temizleyip 64×64/128×128 piksel simgeye indirgeyen ve atlasa işleyen hattı ben çalıştırırım (`tools/assetgen`).
