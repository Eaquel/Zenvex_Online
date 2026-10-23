# Zenvex Online — V15 Devasa Prompt Belgesi

Bu belge V15 planındaki (`docs/21_BUYUK_PLAN_V15.md`) **görsel gereken her şeyin** istemlerini içerir. Kahramanın
8 yön animasyonları ve 18 etkinlik eşyası ayrı belgede: `docs/20_KAHRAMAN_ANIMASYON_PROMPTLARI.md`; bu belge onu
tamamlar, tekrar etmez.

Referans görseller: **`Zenvex_Referanslar_V15.zip`**. Klasör adları bu belgedeki bölüm numaralarıyla başlar
(`03_...` → Bölüm 3). Bir istemde "referans ekle" yazıyorsa o klasördeki dosyayı ekle.

**Önerilen üretim sırası** (oyuna en çok etkisi olandan): 1 → 3 → 5 → 12 → 2 → 6 → 7 → 8 → 9 → 10 → 11 → 4 → 13 → 14 → 15 → 16.
Her bölümü bitince ayrı zip olarak da gönderebilirsin, hepsini tek zip'te de. Klasörü bölüm numarasıyla adlandır.

---

## 0 · ORTAK KURALLAR

1. **Stil (aksi yazmadıkça her istemin başına):**
   `high quality pixel art, crisp hard-edged pixels, no anti-aliasing blur, 1px dark selective outline, hue-shifted shading,
   light from the top-left, dark post-apocalyptic palette, consistent with the attached reference style`
2. **Kamera:** dünya nesneleri ve karakterler için **3/4 yukarıdan** (oyunun kamerası: hafif yukarıdan, ön yüz görünür).
   Simgeler için düz önden, hafif 3/4.
3. **Zemin:** düz **#00FF00** yeşil. Nesnede saf yeşil kullanılmasın (yeşil gereken yer zeytin/koyu yeşil).
   Ateş, aura, ışık gibi **parlayan efektlerde zemin düz siyah #000000** olsun (oyunda "ekleme" karışımıyla çizilir).
   Her bölümde hangisi olduğu ayrıca yazıyor.
4. **Yazı yok:** yazı, harf, sayı, logo, filigran, imza olmasın. (Tek istisna Bölüm 1 logo.)
5. **Kesme yok:** hiçbir parça kenardan taşmasın; nesneler arasında geniş boşluk, birbirine değmesin.
6. **Sayfa düzeni:** her istem kaç nesne/kare istediğini ve ızgarasını söyler. Izgara çizgisi çizdirme.
7. **Animasyon:** "N kare" yazan her şey **tek satır ya da 2×4 ızgara**, soldan sağa okunur, son kare ilk kareye
   kusursuz bağlanır (döngü). Her karede nesne **aynı yerde, aynı boyda**.
8. **Çözünürlük:** en az 1024 px, tercihen 2048 px kare ya da istenen en-boy. Ben küçültüp piksel ızgarasına oturturum.
9. **Tutarlılık:** aynı setin her parçası **aynı istemde** üretilsin (ör. bir bölgenin 6 yapısı tek görselde 2×3),
   böylece renk ve ölçek tutarlı olur. Tek tek üretmek zorunda kalırsan bir öncekini referans ekle.
10. **Dosya adı:** her istemde yazan adı kullan (`b3_apartment0.png` gibi). Tutturamazsan klasör adı yeter, ben ayıklarım.

### Ortak şablonlar

**Dünya nesnesi sayfası**
```
[STİL] 3/4 top-down view of a top-down survival RPG.
{N} separate objects on one sheet in a {R} rows x {C} columns layout, wide empty space between objects, each object
complete and uncropped, same scale for all objects, objects sit on an invisible flat ground, no cast shadows.
Objects: {LİSTE}.
flat solid #00FF00 green background, no text, no numbers, no grid lines.
```

**Animasyonlu efekt (parlayan)**
```
[STİL] {N}-frame looping animation sprite sheet, {DÜZEN}, every frame centered at the same position and same size,
seamless loop (last frame flows into the first). Effect: {TARİF}.
flat solid pure black #000000 background, no text, no grid lines.
```

**Simge sayfası**
```
[STİL] set of {N} game UI icons, {R} rows x {C} columns, each icon centered in its own cell with generous padding,
same size and same visual weight, bold readable silhouette at small sizes, thick dark outline.
Icons: {LİSTE}.
flat solid #00FF00 green background, no text, no letters, no numbers, no grid lines.
```

---

## 1 · LOGO (lobi ve yükleme ekranındaki katanalı adamın yerine) — 3 görsel

Eski amblem (kırmızı kapüşonlu, katanalı, arkasında ay olan adam) kaldırılıyor. Yerine oyunun adı.
Referans: `01_logo/eski_amblem.png` (ne OLMAYACAĞINI görmek için), `01_logo/lobi_arka_plan.png` (renk uyumu için).

| Dosya | İstem |
|-------|-------|
| `logo_wordmark.png` | `pixel art game logo wordmark reading exactly "ZENVEX ONLINE", "ZENVEX" large on top in heavy chiseled rusted-steel letters with cracked edges, faint ember glow in the cracks and a thin blood-red underline, "ONLINE" smaller below in clean bold steel letters with wide letter spacing, subtle dripping rust, post-apocalyptic zombie survival mood, centered, transparent-ready: flat solid #00FF00 green background, no other text, no characters, no weapons, no moon` |
| `logo_mark.png` | `square pixel art app badge: a stylized metal letter "Z" made of two crossed rusted steel beams with a small ember glowing at the center, chipped edges, dark iron border ring, flat solid #00FF00 green background, no other text` |
| `logo_glow.png` | 8 kare, siyah zemin: `8-frame looping glow animation for a game logo: soft ember-orange light sweeping left to right across a horizontal band, with tiny drifting sparks, 2 rows x 4 columns, flat solid black #000000 background` |

---

## 2 · OYUN İÇİ ARAYÜZ (HUD) — 1 sayfa + 3 tek görsel

Referans: `02_hud/oyun_ekrani_*.png` (mevcut ekran), `02_hud/mevcut_simgeler.png`.

**`hud_buttons.png`** — Simge sayfası, 3×4, **12 simge**:
1. altın para yığını (küçük, parlak, tek bakışta "altın")
2. elmas (mavi-turkuaz, keskin yüzeyli, mevcut elmasla aynı renk)
3. yeşil **+** düğmesi (yuvarlak köşeli kare, kabarık, pikselli parıltılı)
4. **mağaza** tuşu: kapağı aralık, içinden altın ışık taşan küçük hazine sandığı
5. **takla** tuşu: kıvrılıp yuvarlanan figür silüeti + hareket çizgileri
6. **silah değiştir** tuşu: çapraz bıçak ve katana, aralarında dönen ok
7. **yetenek** tuşu: parlayan pençe izi / enerji küresi
8. **etkileşim** tuşu: açık el (al / konuş)
9. **inşa** tuşu: çekiç ve ızgara karesi
10. **onayla**: kalın yeşil tik
11. **iptal**: kalın kırmızı çarpı
12. **saat**: küçük kum saati (geri sayımlar için)

| Dosya | İstem |
|-------|-------|
| `hud_currency_bar.png` | `pixel art horizontal UI capsule bar for a mobile game HUD, dark gunmetal with riveted ends and a thin inner bevel, empty center for numbers, left end has a round socket for an icon, right end has a small square socket for a plus button, 6:1 aspect ratio, flat solid #00FF00 green background` |
| `hud_level_badge.png` | `pixel art small shield-shaped level badge for an enemy health bar, neutral light-gray steel with a dark center plate for a number, beveled rim, front view, flat solid #00FF00 green background` (renkleri oyun içinde ben boyarım: gri/yeşil/sarı/turuncu/kırmızı) |
| `hud_offer_strip.png` | `pixel art thin horizontal banner strip for a limited-time offer timer at the bottom of a game screen, dark red-black leather with gold stitched trim, small gift-box socket on the left, empty center, 8:1 aspect ratio, flat solid #00FF00 green background` |

---

## 3 · ESKİ 3D YAPILARIN PİKSEL KARŞILIKLARI — 26 nesne, 4 sayfa

Bunlar oyunda hâlâ duran, Blender ile render edilmiş eski 3D görseller. **Aynı nesne, aynı ölçü ve aynı açıyla**,
ama V13 bölge görselleriyle aynı piksel stilinde ve kıyamet sonrası yıpranmış halde yeniden çiz.
Referans: `03_eski_3d/<anahtar>.png` (her biri ayrı; eski görsel) + stil için `03_eski_3d/_stil_v13_bolge.png`.
Her sayfada o sayfanın eski görsellerini ekle ve şunu yaz:
`redraw each attached object as pixel art in the same style as the style reference, keep the same shape, size ratio, camera angle and footprint, make it look weathered and abandoned (zombie apocalypse), cracked paint, rust, grime, broken windows boarded up`

| Sayfa | Düzen | Nesneler (eski anahtar → yeni dosya) |
|-------|-------|--------------------------------------|
| `b3_city_big.png` | 2×2 | `apartment0` kırmızı tuğla 3 katlı apartman, düz çatı (klima, su deposu, uydu) · `apartment1` krem balkonlu apartman · `police` polis karakolu (mavi şerit, çatıda tepe lambası, önünde bariyer) · `tower` ahşap gözetleme kulesi (yeşil çatı, merdiven) |
| `b3_city_small.png` | 2×3 | `shop0` market (sarı tente, kasalar) · `shop1` bakkal (kırmızı-beyaz tente) · `house0` kırmızı kiremit çatılı ev · `house1` gri çatılı tuğla ev · `house2` yeşil çatılı mavi ev · `stall` çizgili tenteli pazar tezgâhı |
| `b3_road.png` | 2×4 | `gas` benzin istasyonu (kırmızı-beyaz sundurma, 2 pompa; tabelada yazı YOK, boş pano) · `lamp` sokak lambası · `wreck0` paslı SUV enkazı · `wreck1` turuncu spor araba enkazı · `gate` kırmızı-beyaz bariyerli kapı ve kum torbaları · `wall_x` yatay beton duvar (dikenli tel) · `wall_z` dikey beton duvar parçası (aynı duvarın önden görünüşü) · `fence` ahşap çit |
| `b3_small.png` | 2×4 | `tent` yeşil kamp çadırı · `sandbags` kum torbası yığını · `barrel` kırmızı varil · `barrel_tox` sarı zehirli varil (koyu yeşil sızıntı) · `cone` trafik konisi · `trash` çöp torbaları ve molozlar · `log` kesilmiş kütük · `billboard` boş reklam panosu (direkli) |

> Not: Ağaçlar, çalılar, kayalar, çiçekler zaten piksel; onları üretme.

---

## 4 · BÖLGE YAPI SETLERİ (her bölge baştan özel) — 8 sayfa, 46 yapı

Bölgeler artık ayrı, kare dünyalar. Her birine **kendine özgü** büyük yapılar gerekiyor (plan belgesi bölüm 3).
Her sayfa 2×3 (6 yapı), aynı istemde. Referans: `04_bolgeler/<bölge>_mevcut_dekor.png` (o bölgenin şimdiki süsleri,
renk uyumu için) + `04_bolgeler/<bölge>_konsept.png`.
Şablon: **Dünya nesnesi sayfası**; nesneler büyük yapı ölçeğinde (her biri 3-6 karo genişliğinde).

| Sayfa | Yapılar |
|-------|---------|
| `r_forest.png` | sahra hastanesi çadırı (kızıl haç, sedye) · köpek barınağı (tel örgü, kulübeler) · atış alanı (hedef tahtaları, saman balyaları) · göl iskelesi (ahşap, bağlı kayık) · oduncu kulübesi (balta saplı kütük, odun yığını) · kamp meydanı (taş halka kamp ateşi yeri, kütük oturaklar, görev panosu) |
| `r_city.png` | metro girişi (yıkık merdiven, kırık tabela direği) · hastane cephesi (ambulans enkazı) · Viktor'un garajı (açık kepenk, kriko, lastik yığını) · Silas'ın barakası (teneke, kum torbası) · fabrika avlusu kapısı (boss alanı, zincirli) · keskin nişancı yuvası (çatı üstü, kum torbası) |
| `r_burnt.png` | yanmış itfaiye kulesi · kömürleşmiş köy evi · kül gölü kıyısı (kararmış iskele) · devrilmiş elektrik direği hattı · közlü krater (boss alanı, kızıl çatlaklar) · yanmış kamyon |
| `r_desert.png` | terk edilmiş motel (tek katlı, kırık tabela direği) · araç mezarlığı (üst üste paslı arabalar) · kanyon geçidi kayaları · su kulesi · kumla gömülü köprü ayağı (boss alanı) · çöl çadır kampı |
| `r_bswamp.png` | kazıklı iskele ve kulübe · cadı kulübesi (asılı kemikler, kazan) · batık kilise (yarısı suda, çan kulesi) · çürük kök kemeri · kara su göleti adası (boss alanı) · sis feneri (yeşil ışık) |
| `r_harbor.png` | liman ofisi (buzlu) · konteyner vinci · buza saplanmış balıkçı gemisi · balıkhane · kırık iskele ucu (boss alanı) · donmuş konteyner yığını |
| `r_front.png` | karargâh çadırı (Komutan Diana'nın masası, harita) · beton sığınak · helikopter pisti · mayın tarlası uyarı çiti · makineli tüfek yuvası · ikmal deposu |
| `r_home.png` (2×2) | kişisel ev (küçük, tamir edilmiş, sıcak ışık) · sandık sundurması · tezgâh atölyesi · köpek kulübesi (büyük, isim tabelası boş) |

---

## 5 · ATEŞ, IŞIK, AURA VE PARILTILAR — 19 animasyon

Kodla çizilen kamp ateşi ve auralar kalkıyor; hepsi elle çizilmiş animasyon olacak. **Parlayan kısımlar siyah zemin**,
katı nesneler (odun, varil) **yeşil zemin**. Referans: `05_efektler/mevcut_kamp_atesi.png`, `05_efektler/mevcut_fx.png`,
`05_efektler/kahraman_on.png` (auranın boyu için).

### 5A · Ateşler (katı taban yeşil zeminde ayrı, alev siyah zeminde ayrı)
| Dosya | Kare | Tarif |
|-------|------|-------|
| `campfire_base.png` | 1 | `pixel art campfire base: ring of blackened stones, crossed charred logs, glowing embers between logs, 3/4 top-down, #00FF00 background` |
| `campfire_off.png` | 1 | aynı taban, sönmüş: kül, ince duman yok |
| `campfire_flame.png` | 8 (2×4) | `realistic-feeling pixel art campfire flames rising from a log pile: layered flame tongues (deep red outer, orange middle, yellow-white core), flames flicker and lean slightly with wind, occasional flame tip detaches and fades, height about 1.6x the width of the base` — siyah zemin |
| `campfire_embers.png` | 8 | `tiny glowing ember sparks rising and drifting upward in a narrow column, some fade out, random sway` — siyah zemin |
| `campfire_smoke.png` | 8 | `soft gray pixel smoke puffs rising and widening, semi-transparent dithering, drifting slightly right` — siyah zemin |
| `torch.png` | 8 | duvar meşalesi: demir tutucu (yeşil zeminde ayrı tek kare) + alev 8 kare (siyah zemin, ayrı sayfa `torch_flame.png`) |
| `fire_barrel.png` | 8 | paslı varil içinde yanan ateş: varil (yeşil zemin, 1 kare) + alev 8 kare (siyah zemin, `fire_barrel_flame.png`) |
| `brazier_flame.png` | 8 | taş mangal üstünde mavi-beyaz özünde turuncu alev (büyücü meydanı için) — siyah zemin |
| `burning_fire.png` | 8 | yanık ormandaki yerde yanan küçük ateş kümeleri (kütük ve kül üstünde) — siyah zemin |

### 5B · Karakter auraları (her aura iki katman: **arka** = karakterin arkasında, **ön** = önünde)
| Dosya | Kare | Tarif |
|-------|------|-------|
| `aura_max_back.png` / `aura_max_front.png` | 8 + 8 | **Seviye 50 aurası.** `majestic level-max aura for a hooded survivor: a glowing golden-crimson ground sigil ring slowly rotating under the feet (back layer), rising flame-like energy wisps and floating rune sparks around the body, brief upward light streaks (front layer), height about 1.4x a character, symmetric, center-bottom anchored` |
| `aura_lv25_back/front.png` | 8 + 8 | daha sade: gümüş-mavi zemin halkası, ince yükselen kıvılcımlar |
| `aura_lv40_back/front.png` | 8 + 8 | mor-kızıl halka, kısa alev dilleri |
| `aura_potion_heal.png` | 8 | yeşil yaprak ve artı parçacıkları yukarı süzülür, ayak altında yumuşak yeşil halka |
| `aura_potion_speed.png` | 8 | mavi rüzgâr çizgileri ve hız izleri, ayak altında mavi halka |
| `aura_potion_power.png` | 8 | kızıl-turuncu güç dalgaları, yumruk/pençe biçimli kısa alevler, ayak altında kızıl halka |

### 5C · Nesne parıltıları
| Dosya | Kare | Tarif |
|-------|------|-------|
| `glow_chest.png` | 8 | sandığın etrafında yumuşak ışık ve yukarı süzülen ışık zerreleri (beyaz; oyunda nadirlik rengine boyanır) |
| `glow_drop.png` | 8 | yerdeki ganimetin üstünde dikey ışık sütunu ve dönen küçük parıltı (beyaz; nadirliğe göre boyanır) |
| `glow_interact.png` | 8 | etkileşilebilir nesnenin (tezgâh, NPC) ayağında nabız gibi atan ince elmas biçimli halka (beyaz) |
| `marker_quest.png` | 8 | NPC başında zıplayan altın ünlem işareti biçimli pikselli kristal (yazı değil, kristal biçimi) — yeşil zemin |

### 5D · Hava (bölgeye özel, ekranı kaplayan döşenebilir katman)
| Dosya | Kare | Tarif |
|-------|------|-------|
| `weather_rain.png` | 4 | `seamless tileable pixel rain streaks overlay, diagonal thin light-blue lines` — siyah zemin |
| `weather_snow.png` | 8 | döşenebilir kar taneleri, yavaş, salınarak (Liman) |
| `weather_ash.png` | 8 | döşenebilir gri kül ve tek tük kor (Yanık Orman) |
| `weather_sand.png` | 8 | döşenebilir yatay kum tozu çizgileri (Çöl) |
| `weather_fog.png` | 8 | döşenebilir yeşilimsi alçak sis bulutları (Kara Bataklık) |

---

## 6 · SIRALAMA PLAKALARI (kanatlı kurdelenin yerine) — 44 plaka

Kanatlı kızıl kurdele kalkıyor. Karakterin başında, unvan yazısının **arkasında** duran **ince, alçak, okunaklı**
plakalar. Yazıyı ben koyarım; plakanın ortası **boş ve düz koyu** olsun.
Referans: `06_plakalar/eski_kanatli.png` (ne OLMAYACAĞI), `06_plakalar/oyun_ici_unvan.png` (boyut),
`06_plakalar/sira_simgeleri.png` (her sıralamanın mevcut simgesi).

**Kurallar:** en-boy **5:1**, ortada düz koyu yazı alanı (plakanın ~%70'i), iki uçta küçük süs. Kanat, büyük taç,
dışa taşan süs YOK. Plaka karakterden geniş olmamalı.
**Kademeler:** `1` altın (en süslü, küçük taç ucu), `2` gümüş, `3` bronz, `4` (4-10. sıra) sade koyu çelik.

| Sıralama | Kod | Uçlardaki süs (motif) |
|----------|-----|------------------------|
| Seviye | `level` | yıldız |
| Zombi Avı | `kills` | kafatası |
| Oyuncu Avı | `pvp` | çapraz kılıçlar |
| Titan | `titan` | magma çatlaklı boynuz |
| Asker | `soldiers` | kalkan |
| Üs Fethi | `bases` | bayrak |
| Köpek | `dog` | pati izi |
| Zanaat | `crafted` | çekiç ve örs |
| Emektar | `time` | kum saati |

Her sıralama için **bir görsel, 4 plaka alt alta** (1, 2, 3, 4): `plate_<kod>.png` → 9 görsel = 36 plaka.

```
[STİL] four horizontal nameplate banners stacked vertically with wide gaps, each a slim 5:1 plate for a player title
floating above a character in a top-down game: a flat dark center panel for text taking 70% of the width, small
{MOTİF} ornaments on both ends, no wings, no big crown, nothing sticking far out.
Plate 1 polished gold with a tiny crown point on top and ruby inset, plate 2 polished silver with sapphire inset,
plate 3 bronze with amber inset, plate 4 plain dark steel with rivets.
flat solid #00FF00 green background, no text, no letters.
```

**Ek 8 plaka** (aynı kurallar, tek görselde 2×4, `plate_special.png`): 6 etkinlik (Kan Ayı kızıl-kemik, Titan
magma, Üs Savaşı çelik-bayrak, Hurda Avı paslı dişli, Sürü av-pençe, Kış Nefesi buz kristali) + **Sezon 1 Öncüsü**
(gece mavisi ve altın, uçlarda yıldız) + **VIP10+** (obsidyen ve ateş damarlı).

---

## 7 · UNVAN EFEKTLERİ ("+" işaretlerinin yerine) — 4 animasyon

Unvan yazısının çevresinde dönen gerçek efektler. Siyah zemin, 8 kare, 2×4. Yatay **5:1** alanda (plakanın çevresi).
| Dosya | Tarif |
|-------|-------|
| `title_fx_1.png` | altın: plakanın üstünden soldan sağa kayan parlak ışık şeridi, köşelerde beliren 4 köşeli yıldız parıltıları, üstte süzülen altın tozları |
| `title_fx_2.png` | gümüş: soğuk beyaz parıltılar, ince ışık halkası nabzı |
| `title_fx_3.png` | bronz: sıcak turuncu kıvılcımlar alttan yukarı |
| `title_fx_vip.png` | obsidyen-ateş: plakanın kenarında yanan ince alev çizgisi ve yükselen kor |

---

## 8 · YETENEK AĞACI — 52 parça, 8 sayfa

Ağaç: merkez + 4 dal (Yakın Dövüş, Silah Ustalığı, Dayanıklılık, Çeviklik). Her yeteneğin **kendi** simgesi.
Referans: `08_yetenek/agac_ekrani.png`, `08_yetenek/mevcut_simgeler.png`, stil için `08_yetenek/_stil_esya.png`.
Şablon: **Simge sayfası**, ama zemin **#00FF00** ve her simge **daire içine sığacak** biçimde (çerçeveyi ben koyarım).
Dal renkleri: Yakın Dövüş **kızıl**, Silah Ustalığı **çelik mavisi**, Dayanıklılık **toprak yeşili/bronz**,
Çeviklik **mor-camgöbeği**.

**8A · Küçük düğümler — istatistik simgeleri (21 simge, `skill_small_1.png` 3×4 ve `skill_small_2.png` 3×3)**
| # | Kod | Simge |
|---|-----|-------|
| 1 | MeleeFlat | sıkılmış yumruk, darbe çizgileri |
| 2 | FistPct | sargılı boks eli, çatlayan taş |
| 3 | DmgPct | yukarı bakan kızıl ok ve kılıç ucu |
| 4 | Crit | ortası parlayan hedef nişangâh |
| 5 | Stun | başın etrafında dönen yıldızlar ve çekiç |
| 6 | Lifesteal | kan damlası içinde kalp |
| 7 | BladePct | parlayan katana ağzı |
| 8 | Reach | uzayan kılıç ve iki uçlu ok |
| 9 | AtkSpeed | üç ardışık hızlı kesik izi |
| 10 | CritDmg | kırılan kafatası ve kızıl kıvılcım |
| 11 | HpFlat | dolu kırmızı kalp |
| 12 | DmgTaken | çatlamış ama ayakta duran kalkan |
| 13 | Reflect | kalkandan geri seken ok |
| 14 | BlastRes | patlama içinde sağlam kalkan |
| 15 | Regen | yaprak sarmalı kalp, yeşil artı |
| 16 | LastBreath | kum saati içinde son damla kırmızı kalp |
| 17 | Dodge | kenara kayan silüet, yanından geçen ok |
| 18 | Speed | kanatlı çizme |
| 19 | RollCd | dairesel ok içinde yuvarlanan figür |
| 20 | Stamina | sarı enerji şimşeği, akciğer biçimli |
| 21 | IFrame | yarı saydam hayalet silüet, kalkan parıltısı |

**8B · Belirgin düğümler (4, `skill_notable.png` 1×4, biraz daha büyük ve süslü)**
Ağır El (taş gibi dev yumruk) · Usta Kılıç (çapraz iki katana, ışık) · Kalın Deri (perçinli deri zırh) · Rüzgâr (rüzgâr girdabı ve tüy)

**8C · Anahtar düğümler (12, `skill_key_1.png` ve `skill_key_2.png` 2×3, en gösterişli, kendi dal renginde parlak)**
| Dal | Yetenekler |
|-----|------------|
| Yakın Dövüş | **Kan Emici** (vampir dişli yumruk, kan halkası) · **Sarsıcı Darbe** (yere vuran yumruk, şok dalgası) · **Yıkım** (alevli patlayan yumruk) |
| Silah Ustalığı | **Kanlı Kenar** (kan damlayan katana ağzı) · **Rüzgâr Kesik** (hilal biçimli rüzgâr kesiği) · **Ölüm Ustası** (kafatası kabzalı kara kılıç) |
| Dayanıklılık | **Kaya** (taş gövdeli göğüs) · **Son Nefes** (ölüm meleği kanadı altında kalp) · **Titan Derisi** (magma çatlaklı zırh plakası) |
| Çeviklik | **Gölge Adım** (gölgeye dönüşen ayak izi) · **Fırtına** (şimşekli hız girdabı) · **Hayalet** (yarı saydam pelerinli figür) |

**8D · Merkez ve dal amblemleri (5, `skill_emblems.png` 1×5)**
Hayatta Kalan (merkez: kamp ateşi önünde kapüşonlu silüet) · 4 dal amblemi (kızıl yumruk, mavi kılıç, yeşil kalkan, mor kanatlı çizme), büyük ve madalyon biçimli.

**8E · Düğüm çerçeveleri (9, `skill_frames.png` 3×3)**
Satırlar: küçük (yuvarlak ince çerçeve) · belirgin (altıgen kalın) · anahtar (yıldızlı taçlı). Sütunlar: kilitli
(koyu taş, zincir) · açılabilir (parlak kenar, nabız) · alınmış (altın, ışıklı). Ortası **boş** (simge içine konur).

**8F · Arka plan (1, `skill_bg.png`, 16:9)**: `dark ancient stone floor with faint carved concentric rings and four
faded directional runes, dim blue torchlight vignette, subtle cracks, no text` (yeşil zemin değil, tam sahne).

---

## 9 · MAĞAZA VE EKONOMİ — 34 görsel

Kullanıcı görünce "almak istemeli". Pırıltılı, dolu, değerli hissi. Referans: `09_magaza/mevcut_sandik.png`,
`09_magaza/mevcut_elmas.png`, `09_magaza/pass_ekrani.png`.

| Dosya | Adet | İstem özü |
|-------|------|-----------|
| `shop_bg.png` | 1 | 16:9 sahne: `post-apocalyptic black market tent interior at night, lanterns, crates of loot, glowing gem display cases, warm gold and teal light, depth, no characters, no text` |
| `shop_featured_frame.png` | 1 | büyük öne çıkan teklif çerçevesi (altın ve kızıl, köşelerde perçin), ortası boş, 2:1 |
| `gems_1..6.png` (tek sayfa `shop_gems.png`, 2×3) | 6 | artan elmas yığınları: 1) avuç dolusu, 2) deri kese, 3) küçük sandık, 4) kilitli kasa, 5) taşan hazine sandığı, 6) ejderha heykelli hazine yığını; her biri bir öncekinden belirgin büyük ve parlak |
| `shop_ribbon_x2.png` | 1 | köşe kurdelesi "ilk alım 2×" (yazısız; kızıl-altın köşe şeridi, ben yazarım) |
| `pack_start.png`, `pack_growth.png`, `pack_war.png` | 3 | seviye paketleri: Başlangıç = yeşil-bronz sırt çantası taşan malzeme; Gelişim = mavi-gümüş askeri kasa, motor parçaları; Savaş = kızıl-altın zırhlı sandık, silah ve kalkan |
| `card_weekly.png`, `card_monthly.png` | 2 | kart biçimli (dikey 3:4) değerli kartlar: haftalık gümüş kenarlı, aylık altın kenarlı ve elmas işlemeli, ortada takvim yaprakları ve elmas |
| `ticket_vip.png`, `ticket_premium.png` | 2 | pass biletleri: VIP Bilet mor-gümüş delikli bilet; Premium Bilet altın-kızıl, mühürlü, ışık saçan |
| `frame_s1.png` | 1 | **S1 profil çerçevesi** (oyundaki diğer çerçevelerle aynı ölçü ve yuvarlak iç alan): gece mavisi ve altın, tepede yıldızlı rozet. Referans: `09_magaza/cerceve_ornekleri.png` |
| `vip_badge.png` | 11 | V1…V10 ve V10+ rozetleri, **2 görsel**: `vip_badge_1.png` (V1-V6) ve `vip_badge_2.png` (V7-V10+). Bronz → gümüş → altın → elmas → obsidyen-ateş ilerlemesi; her biri bir öncekinden süslü; ortası numara için boş |
| `shop_burst.png` | 8 kare | satın alma patlaması: altın ışınlar, yağan elmas ve paralar — siyah zemin |
| `shop_chest_open.png` | 8 kare | mağaza sandığının açılması: kapak açılır, içinden ışık sütunu yükselir — yeşil zemin |
| `shop_tabs.png` | 6 simge | sekme simgeleri: yıldız (öne çıkan), elmas, hediye kutusu (paketler), taç (VIP), takvim kart (kartlar), bilet (pass) |

---

## 10 · VIP GÖRÜNÜMLERİ — 4 kanat, 4 silah görünümü, 4 sohbet teması, 3 ayak izi, 3 araç kamuflajı, 2 giriş afişi

Referans: senin ana karakter görselin + `10_vip/kahraman_yonler.png` (oyundaki kahraman önden ve arkadan) ve `10_vip/araclar/` (motor ve araba yön görselleri).

### 10A · Kanatlar (karakterin sırtında, animasyonlu)
Her kanat için **3 görsel**: önden (karakter kameraya bakıyor, kanatlar arkasından iki yana açılmış), yandan
(sağa bakıyor), arkadan. Her biri **8 kare çırpma döngüsü**, 2×4, **yalnız kanat** (karakter yok), karakter
referansına göre doğru boy ve sırt hizası. Yeşil zemin (parlak kısımlar varsa ayrıca siyah zeminli `_glow` sayfası).
Dosya: `wings_<ad>_<0|2|4>.png`.
| Ad | Kaynak | Tarif |
|----|--------|-------|
| `ash` | V8 | Kül Kanatları: yanık tüyler, uçlarında kor, kül dökülür |
| `dragon` | V10 | Ejder Kanatları: koyu kızıl zar kanat, kemik iskelet, altın kenar |
| `titan` | V10+ | Titan Kanatları: magma çatlaklı obsidyen plakalar, aralarında akan lav ışığı, en büyük ve en gösterişli |
| `s1` | Pass 30 | Sezon 1 Kanatları: gece mavisi tüyler, yıldız parıltısı, altın tüy uçları |

### 10B · Silah görünümleri
Elindeki silah oyunda **renk paleti** değiştirilerek boyanır; bu yüzden her görünüm için **simge + palet şeridi** yeter.
`weapon_skins.png`, 2×2, her hücrede büyük silah simgesi ve altında 6 renklik yatay palet şeridi (koyu→açık):
**Obsidyen Katana** (V7, siyah cam, mor ışık) · **Titan Tırpanı** (V10+, magma) · **Kanlı Ay Kılıcı** (Pass 25, kızıl-gümüş)
· **Savaş Lordu Katanası** (Savaş Paketi, altın-çelik). Referans: `10_vip/mevcut_katana.png`.

### 10C · Sohbet balonu temaları (9 dilimli çerçeve)
`chat_themes.png`, 2×2: **Bronz** (V3) · **Gümüş** (V7) · **Altın** (V10) · **Obsidyen-Ateş** (V10+). Her biri
yuvarlak köşeli konuşma balonu, alt ortada küçük kuyruk, kenar süsleri yalnız köşelerde (ortası düz koyu, yazı için).

### 10D · Ayak izleri (yürürken arkada kalan, sönerek kaybolan)
`footprints.png`: 3 satır × 4 kare (her satır bir tür, 4 karede sönme): **Kıvılcım** (V5) · **Alev** (V9) · **Buz** (Pass).
Siyah zemin.

### 10E · Araç kamuflajları
**Motor ve araba**, mevcut 8 yönlü görsellerin **aynı açıdan** yeniden boyanmış hali. Yalnız **0, 1, 2, 3, 4 yönleri**
(5-7 aynalanır). Her istemde o yönün mevcut görselini ekle:
`repaint the attached vehicle with the {KAMUFLAJ} livery, keep the exact same vehicle, angle, size and silhouette, pixel art`.
Kamuflajlar: **Gece Kamuflajı** (V6, siyah-lacivert dijital desen) · **Altın Kaplama** (V9, cilalı altın, kızıl şerit)
· **Sezon 1** (Pass, gece mavisi, yıldız deseni). Dosya: `camo_<ad>_<moto|car>_<yön>.png` → 3 × 2 × 5 = **30 görsel**.

### 10F · Giriş afişleri
`entry_banner_v5.png` (gümüş-mavi yatay şerit, 6:1, ortası boş) · `entry_banner_v10.png` (altın-kızıl, alevli uçlar, 6:1).

---

## 11 · KARAKTER SİSTEMİ — 4 karakter

Yeni sekme "Karakterler": 4 karakter, açılmamışlar silüet (silüeti kart çiziminden ben üretirim). Savaşçı mevcut
ana karakterin kendisi (senin referans görselin). Diğer 3 karakter yeni; aşağıda tarifleri var.

### 11A · Yeni karakterlerin tarifleri (her istemde bunu kullan)
| Karakter | Nadirlik | Tarif (İngilizce, istemde aynen) |
|----------|----------|----------------------------------|
| İz Sürücü | Nadir (mavi) | `lean scout survivor, dark teal hooded cloak with torn edges, leather chest straps with throwing knives, cloth mask over the lower face, fingerless gloves, light boots, a curved hunting knife in each hand, agile crouching stance` |
| Muhafız | Destansı (mor) | `heavily built guardian survivor, patched riot armor plates painted dull purple, a large rectangular scrap-metal shield with a viewing slit, short heavy cleaver, gas mask with one cracked lens, thick boots, broad shoulders` |
| Gölge | Efsanevi (altın-kızıl) | `mysterious swordsman, long black coat with crimson inner lining, deep hood with only glowing red eyes visible, a long katana with a black blade and red edge glow, bandaged forearms, faint crimson mist around the feet` |

> Tarife ekle: `no green colors on the character` (yeşil zemin silinirken karakter bozulmasın).

### 11B · Her karakter için görseller
| Dosya | Adet | Tarif |
|-------|------|-------|
| `char_<ad>_card.png` | 1 | kart çizimi (dikey 3:4): karakter dramatik pozda, arkada kendi bölge atmosferi, ışık kendi nadirlik renginde; **yeşil değil, tam sahne arka plan** |
| `char_<ad>_turn.png` | 1 | dönüş sayfası: 5 görünüm (güney, güneydoğu, doğu, kuzeydoğu, kuzey) tek satır, aynı boy, yeşil zemin. **Bu görsel o karakterin bütün animasyonlarının referansı olacak.** |
| `char_<ad>_portrait.png` | 1 | baş-omuz portresi (kare), profil resmi ve diyalog için, yeşil zemin |
| `char_<ad>_skill.png` | 8 kare | özel yetenek efekti: İz Sürücü = gölge atılımı izi; Muhafız = kalkan duvarı ve şok halkası; Gölge = kızıl kan sisi ve can çekme izleri. Siyah zemin |

Savaşçı için yalnız `char_kaan_card.png` ve `char_kaan_portrait.png` (senin karakter görselini ekleyerek; oyundaki hali `11_karakter/savasci_mevcut.png`).

### 11C · Kart ve açılış görselleri
| Dosya | Adet | Tarif |
|-------|------|-------|
| `card_frames.png` | 4 (1×4) | karakter kartı çerçeveleri: Sıradan gri-çelik, Nadir mavi, Destansı mor, Efsanevi altın-kızıl alevli; ortası boş, 3:4 |
| `card_back.png` | 1 | kartın arkası: koyu deri, ortada kabartma Z amblemi (Bölüm 1 `logo_mark` ile uyumlu) |
| `card_reveal.png` | 4 sayfa × 8 kare | nadirliğe göre açılış ışığı: gri pırıltı / mavi ışık sütunu / mor şimşekli sütun / altın-kızıl patlama ve yükselen alevler. Siyah zemin. `card_reveal_<0..3>.png` |
| `char_bg.png` | 1 | karakter sekmesi arka planı 16:9: dört kaide üstünde dört boş spot ışığı, karanlık salon, toz zerreleri |

### 11D · Animasyonlar
Oynanabilir oldukları için her karakterin 8 yön animasyonu `docs/20_KAHRAMAN_ANIMASYON_PROMPTLARI.md` belgesindeki
şablonlarla üretilir, referans olarak o karakterin `char_<ad>_turn.png` görseli eklenir. Asgari set (her karakter):
B1 temel hareketler (durma, yürüme, koşma, takla, vurulma, ölüm) + kendi silahıyla saldırı (İz Sürücü bıçak,
Muhafız kalkan ve satır, Gölge katana) + özel yetenek pozu + B5'ten balta, kazma, toplama + B6 araçlar.

---

## 12 · HARİTA VE MİNİ HARİTA — 3 sayfa

Mini harita artık bölgenin gerçek küçük görüntüsü olacak (oyunun kendi görselleriyle çizilir). Gereken yalnız
**işaret simgeleri, çerçeve ve kilit**. Zombi işaretleri için türlerin kafa simgelerini mevcut görsellerden ben keserim.
Referans: `12_harita/mevcut_harita.png`, `12_harita/mevcut_buyucu.png`.

| Dosya | Adet | İçerik |
|-------|------|--------|
| `map_markers.png` | 20 (4×5) | kendi konumun (altın ok), diğer oyuncu (beyaz ok), klan üyesi (yeşil ok), doktor (kızıl haç), tamirci (İngiliz anahtarı), eğitmen (hedef), tüccar (kamyonet önden), büyücü (asa ve yıldız), görev veren (parşömen), komutan (bayrak), sandık, tezgâh, kamp ateşi, görev hedefi (altın elmas), titan (magma kafatası), boss (boynuzlu kafatası), üs dost (mavi kale), üs düşman (kızıl kale), üs tarafsız (gri kale), doğuş noktası (yıldızlı çember). Küçük boyda okunur, kalın kontur |
| `map_frame.png` | 2 | mini harita çerçevesi: kare, yıpranmış metal, köşelerde perçin, üstte küçük pusula "K" yerine ok ucu (yazısız) · büyük harita çerçevesi (16:9, eski parşömen kenarlı metal) |
| `map_legend.png` | 1 | sol alttaki açıklama şeridi arka planı: koyu deri ve metal, satırlar için boş, 1:3 dikey |
| `lock_pixel.png` | 2 (1×2) | büyücü haritasında kilitli bölge simgesi: kalın piksel asma kilit (demir, perçinli) · açılırken hali (kilit dili havada, parıltı) |

---

## 13 · GELİŞTİRİCİ PANELİ, DESTEK, DİL — 3 sayfa

Referans: `13_panel/mevcut_simgeler.png` (stil uyumu için).
| Dosya | Adet | İçerik |
|-------|------|--------|
| `admin_bg.png` | 1 | 16:9 komuta odası: duvarda bölge haritaları, monitörler (yazısız ekranlar), koyu mavi ışık |
| `admin_icons.png` | 24 (4×6) | sekmeler: oyuncular (iki silüet), dünya (küre), sunucu (raf), ekonomi (altın ve grafik çubukları), destek (zarf ve can simidi), kayıt (defter), hileler (sihirli asa). Eylemler: ışınlan (portal), yanına çek (mıknatıs), eşya ver (hediye), altın ver, elmas ver, seviye ayarla (yukarı ok), envanter (çanta), sustur (çarpılı balon), at (kapıdan ok), yasakla (çekiç), izle (göz), iyileştir (kalp), öldür (kafatası), hava (bulut), gece-gündüz (ay-güneş), zombi doğur (mezardan el), duyuru (megafon) |
| `support_icons.png` | 8 (2×4) | destek konuları: hata (böcek), ödeme (kart ve elmas), hesap (anahtar ve silüet), öneri (ampul), diğer (üç noktalı konuşma balonu), fotoğraf ekle (fotoğraf makinesi), gönder (kâğıt uçak), bildir (kızıl bayrak) |

Dil seçimi için bayrak çizdirmiyoruz (dil adları kendi yazısıyla görünecek).

---

## 14 · NPC'LERİN DÜNYA ANİMASYONLARI — 8 NPC

NPC'ler artık bölgenin kendi köşelerinde; tek kare durdukları için cansız görünüyorlar. Her biri için **durma (idle)
6 kare** ve **konuşma 6 kare**, 5 yön değil yalnız **güney ve güneydoğu** (oyuncu çoğunlukla aşağıdan gelir).
Referans: `14_npc/<ad>.png` (mevcut görünüş; aynen koru).
NPC'ler: Doktor, Tamirci Viktor, Eğitmen, Tüccar (dükkânda), Görev veren Silas, Büyücü Aldric, **Komutan Diana**
(dünyada görseli hiç yok: `14_npc/diana_portre.png` portresinden tam boy üret), Keskin nişancı (nöbette dürbünle bakma).
Dosya: `npc_<ad>_<idle|talk>_<0|1>.png`, her biri 6 kare, 2×3. Şablon: `docs/20` ortak şablonu, karakter yerine NPC.

---

## 15 · BENİM EKLEDİĞİM KALİTE ARTIRICILAR

Sen söylemedin ama oyunu "AAA" hissettirmek için gerekli gördüklerim:

| Dosya | Adet | Ne için |
|-------|------|---------|
| `load_<bölge>.png` | 8 | Büyücüyle ışınlanırken yükleme ekranı (16:9, bölgenin atmosferik manzarası, karakter yok). Referans: o bölgenin konsept haritası |
| `fx_footstep_dust.png` | 4 kare × 4 zemin | yürürken toz: toprak, kum, kar, çamur (yeşil zemin değil, siyah zemin, yarı saydam) |
| `fx_water_ripple.png` | 8 | su birikintisi/göl kenarında halka |
| `fx_hit_spark.png` | 6 × 3 | vuruş kıvılcımı: et (kan), metal (kıvılcım), taş (toz) |
| `fx_levelup_ring.png` | 8 | seviye atlama: yerden yükselen altın halka ve ışık sütunu (mevcut daha sade) |
| `fx_teleport_in/out.png` | 8 + 8 | büyücü ışınlaması: mor-mavi rün çemberi, yukarı çekilen ışık |
| `ui_damage_font.png` | 1 | hasar sayıları için piksel rakam seti **0-9, +, −, !** (tek bu sayfada rakam serbest): beyaz dolgu, kalın koyu kontur, kritik için ayrı altın set |
| `ui_emotes.png` | 12 | hızlı ifade simgeleri (sohbet tekerleği): selam, gülme, kızgın, ağlama, kalp, kafatası, başparmak, yardım, takip et, dikkat, teşekkür, GG kupası |
| `store_feature.png` | 1 | Google Play öne çıkan görseli 1024×500: logo için sağda boşluk, solda karakter ve köpek, arkada yıkık şehir ve titan silüeti |

---

## 16 · SES İSTEMLERİ (isteğe bağlı, ses yapay zekâsı için)

Yaratıklar şimdilik ortak seslerle konuşuyor. ElevenLabs Sound Effects gibi bir araçla üretebilirsin; her biri
0,5-1,5 sn, sessizlik kırpılmış, OGG/WAV. Dosya: `sfx_<tür>_<idle|attack|hurt|death>.wav`.
Şablon: `{TARİF}, single short game sound effect, close mic, no music, no reverb tail`

| Tür | Tarif |
|-----|-------|
| lumberjack | heavy undead man grunting, wood creak |
| wolf | infected wolf snarl, wet growl |
| shroom | fungal squelch, spore puff hiss |
| office | dry zombie moan, paper rustle |
| riot | muffled groan behind a riot helmet, armor clank |
| crawler | raspy crawling hiss, nails scraping |
| charred | crackling burnt flesh groan |
| emberhound | fiery dog snarl with flame whoosh |
| ashwraith | ghostly ashen wail, wind |
| mummy | dusty raspy moan, cloth tearing |
| scorpion | chitin clicking, tail hiss |
| biker | gravelly undead roar, chain rattle |
| bloater | bubbling gut groan, wet burp |
| boggiant | deep swamp giant bellow, mud splash |
| witch | eerie cackle, whisper |
| sailor | drowned gurgling moan |
| icecrab | ice crunching claw snap |
| dockbrute | huge brute roar, metal hook clank |
| titan (magma) | colossal magma roar, rock rumble, fire burst |

Ek: `sfx_campfire_loop` (çıtırdayan ateş, 4 sn döngü), `sfx_shop_buy` (para ve elmas şıngırtısı, parlak), `sfx_card_reveal_<0..3>`
(nadirliğe göre artan yükseliş), `sfx_wings_flap`, `sfx_vip_entry` (kısa fanfar).

---

## TESLİM ÖZETİ

| Bölüm | Görsel sayısı (yaklaşık) |
|-------|--------------------------|
| 1 Logo | 3 |
| 2 HUD | 4 sayfa (15 parça) |
| 3 Eski 3D'ler | 4 sayfa (26 nesne) |
| 4 Bölge yapıları | 8 sayfa (46 yapı) |
| 5 Ateş, aura, hava | 30 animasyon |
| 6 Plakalar | 10 sayfa (44 plaka) |
| 7 Unvan efektleri | 4 animasyon |
| 8 Yetenek ağacı | 8 sayfa (52 parça) |
| 9 Mağaza | ~20 sayfa (34 parça) |
| 10 VIP | 12 kanat + 1 + 1 + 1 + 30 araç + 2 = ~47 |
| 11 Karakterler | 3×4 + 2 + 4 kart/açılış sayfası + 1 = ~19 (+ animasyonlar, docs/20) |
| 12 Harita | 4 sayfa |
| 13 Panel ve destek | 3 sayfa |
| 14 NPC animasyonları | 32 |
| 15 Kalite artırıcılar | ~25 |
| 16 Sesler (isteğe bağlı) | ~80 ses |

Zip'i verdiğinde her dosyayı bu listeyle eşleştirip eksik ya da sorunlu olanları sana bildireceğim, sonra plan
belgesindeki sırayla oyuna işleyeceğim.
