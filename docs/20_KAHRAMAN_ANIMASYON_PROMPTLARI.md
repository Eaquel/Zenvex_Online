# Ana Karakter Animasyonları ve Etkinlik Eşyaları — Prompt Belgesi (V14)

Bu belge **iki büyük paketi** kapsar:

- **A · Etkinlik eşyaları (18 adet):** 6 etkinliğin yüzüğü, kolyesi ve silahı. Gönderdiğin Kan Ayı görselleri
  (kolye, orak, yüzük) kalite ve stil referansıdır; diğer 5 etkinliğin eşyaları aynı kalitede, kendi renk ve
  motifleriyle yeniden üretilecek.
- **B · Ana karakter (Savaşçı) animasyonları:** 8 yön; yumruk, bıçak, katana ve 6 etkinlik silahıyla durma, yürüme,
  koşma ve saldırı; ayrıca balta ve kazma, toplama, sandık açma, iksir, takla, vurulma, ölüm, yere düşüp kalkma,
  motosiklet ve araba gibi diğer bütün hareketler.

Karakteri tarif etmiyoruz: **her istemde senin vereceğin referans karakter görselini ekle**. Böylece görünüş her
karede aynı kalır.

**Önerilen sıra:** A1 (eşya simgeleri) → B0 (karakter dönüş sayfaları) → B1 (temel hareketler) → B2 (yumruk) →
B3 (bıçak, katana) → B4 (etkinlik silahları) → B5 (iş ve etkileşim) → B6 (araçlar) → B7 (ifadeler).
Her bölüm bitince gönderebilirsin; ben geldikçe oyuna eklerim.

---

## ORTAK KURALLAR (hepsi için geçerli)

1. **Referans:** Her istemde ana karakter görselini ekle ve şunu yaz:
   `use the attached character as the exact reference: same face, hood, clothes, colors and proportions in every frame`
2. **Zemin:** düz **#00FF00** yeşil. Karakterde, silahta ve efektte saf yeşil olmasın (yeşil gereken yer zeytin/koyu yeşil olsun).
3. **Yazı yok:** yazı, sayı, logo, filigran, çerçeve, kare çizgisi, ızgara olmasın.
4. **Kamera:** 3/4 yukarıdan (oyunun kamerası), ışık sol üstten. Her karede aynı kamera.
5. **Ölçek ve taban:** Karakter her karede **aynı boyda**. Ayaklar her karede **aynı yatay çizgide**. Hiçbir şey
   kesilmesin (silah ucu, pelerin, efekt dahil).
6. **Kare düzeni:** Bir görselde **tek animasyonun tek yönü**, **8 kare, 2 satır × 4 sütun**. Okuma sırası soldan
   sağa, sonra alt satır. Kareler arasında geniş boşluk olsun, kareler birbirine değmesin.
   (Magma Boss paketi bu düzendeydi ve en temiz sonucu verdi.)
7. **Stil:** `high quality pixel art game sprite, crisp hard-edged pixels, 1px dark selective outline, hue-shifted shading,
   dark post-apocalyptic palette` — Etkinlik eşyalarının simgesinde bu kural yok (bkz. A).
8. **Döngü:** Durma, yürüme ve koşmada son kare ilk kareye kusursuz bağlansın.

### Yönler

| Kod | Yön | İngilizce (istemde kullan) |
|-----|-----|----------------------------|
| 0 | Güney — kameraya doğru | `facing the camera (south)` |
| 1 | Güneydoğu | `three-quarter front, facing down-right (south-east)` |
| 2 | Doğu — sağa | `right side profile, facing right (east)` |
| 3 | Kuzeydoğu | `three-quarter back, facing up-right (north-east)` |
| 4 | Kuzey — kameradan uzağa | `back view, facing away from the camera (north)` |
| 5 | Kuzeybatı | `three-quarter back, facing up-left (north-west)` |
| 6 | Batı — sola | `left side profile, facing left (west)` |
| 7 | Güneybatı | `three-quarter front, facing down-left (south-west)` |

> **Zaman kazandırır:** 5, 6 ve 7 yönleri, 3, 2 ve 1'in aynası olarak oyunda kendiliğinden üretilebilir (silah o
> zaman öbür elde görünür). Önce **0-4 yönlerini** üret. Silahın elinin değişmesi gözüne batarsa 5-7'yi de ayrıca
> üretirsin.

### Dosya adları

`h_<animasyon>_<yön>.png` — örnek: `h_walk_2.png`, `h_katana_attack_0.png`, `h_ev_blood_idle_3.png`.
Dosya adını tutturamazsan klasör adıyla gönder; ben ayıklarım.

### Ortak istem şablonu

```
[REFERANS GÖRSELİ EKLİ]
use the attached character as the exact reference: same face, hood, clothes, colors and proportions in every frame.
high quality pixel art game sprite sheet, crisp hard-edged pixels, 1px dark selective outline, hue-shifted shading,
light from the top-left, 3/4 top-down camera of a top-down survival RPG.
ONE animation, ONE direction: {ANİMASYON TARİFİ}, the character is {YÖN}.
8 frames in a 2 rows x 4 columns grid, reading left to right then the second row, wide empty space between frames,
same character size in every frame, feet on the same baseline, nothing cropped.
flat solid #00FF00 green background, no ground, no cast shadow, no text, no numbers, no frame borders, no grid lines.
```

**Negatif (her istemde):**

```
blurry, anti-aliased, smooth gradients, 3d render, photo, painterly, text, letters, numbers, watermark, logo, UI,
grid lines, frame borders, cropped, cut off, different outfit, different face, extra limbs, extra weapons,
green clothing, pure green objects, inconsistent scale, perspective change between frames
```

---

## A) ETKİNLİK EŞYALARI — 18 adet

Gönderdiğin Kan Ayı görselleri harika ama **tanıtım afişi** biçiminde: arka plan, yazı, detay kutuları var.
Oyuna girmesi için her eşyanın **tek başına, yazısız, yeşil zeminli** hali gerekiyor. Afişleri de kullanırım:
etkinlik penceresindeki ödül detayında büyük görsel olarak gösteririm. Göndermeye devam et.

### A1 · Simge (zorunlu, 18 adet) — `i_ev_<etkinlik>_<ring|neck|weapon>.png`, 1:1, 1024×1024

```
a single legendary game item, {EŞYA TARİFİ}, centered, fills 80% of a square canvas, slight 3/4 angle,
ornate dark metal base with {VURGU RENGİ} glowing gem inlays and edge highlights, tiny engraved {MOTİF} motif,
premium collectible look, crisp detailed rendering readable at 64x64 pixels,
flat solid #00FF00 green background, no text, no frame, no reflection floor, no shadow
```

> Kan Ayı için gönderdiğin üç görseli de referans olarak ekleyip `same design as the attached reference, isolated`
> yazarsan aynı tasarımın yalnız hali çıkar.

### A2 · Tanıtım afişi (isteğe bağlı, 18 adet) — `art_ev_<etkinlik>_<ring|neck|weapon>.png`

Gönderdiğin görseller gibi. **Tek bir değişiklik:** "LEGENDARY EVENT REWARD" yazısını ve sağdaki detay kutularını
koy, ama **eşya adını yazma**. Adı oyun Türkçe yazıyor.

### Etkinlik tabloları

| Etkinlik | Kimlik | Vurgu rengi | Motif |
|----------|--------|-------------|-------|
| Kan Ayı | `blood` | koyu kızıl `#C8201A` + kemik beyazı | kızıl ay, kan damlası, sivri diş |
| Titan Avı | `titan` | lav turuncusu `#E0904A` + kömür siyahı | çatlak taş, lav damarı, dev pençe izi |
| Üs Fırtınası | `base` | çelik mavisi `#6AB8FF` + haki | çapraz kılıçlar, şimşek, askerî künye |
| Hurdacılar | `scrap` | pas turuncusu + asit yeşili `#9AD84A` (koyu ton) | dişli, perçin, testere dişi |
| Sürü Lideri | `pack` | mor `#C07AFF` + gümüş | kurt başı, pençe izi, ay hilali |
| Son Nefes | `breath` | soluk camgöbeği `#7FE0FF` + kemik | hayalet sis, kafatası, kum saati |

| Eşya | Ad | Tarif (`{EŞYA TARİFİ}`) |
|------|----|-------------------------|
| `ev_blood_ring` | Kan Ayı Kralı Yüzüğü | `a massive signet ring with a faceted blood-red gem held by bone fang prongs, a red crescent moon with a dripping drop on the side` |
| `ev_blood_neck` | Kan Ayı Şampiyonu Kolyesi | `a gothic necklace with a heavy dark chain, a crescent moon pendant with a central blood-red crystal, bone fangs and dripping crystal drops` |
| `ev_blood_weapon` | Kan Ayı Orağı | `a long war scythe with a curved glowing crimson moon blade, a dark thorned head with red gem inlays, a black shaft wrapped with bone rings` |
| `ev_titan_ring` | Titan Avı Kralı Yüzüğü | `a heavy basalt ring with glowing orange lava cracks, a rough molten gem clutched by a stone claw` |
| `ev_titan_neck` | Titan Avı Şampiyonu Kolyesi | `a chain of dark iron links holding a cracked volcanic stone pendant with a glowing lava core and a titan claw mark` |
| `ev_titan_weapon` | Titan Kırıcı Balyoz | `a huge two-handed stone-and-steel sledgehammer with glowing orange lava cracks in the head, iron bands and a long dark handle` |
| `ev_base_ring` | Üs Fırtınası Kralı Yüzüğü | `a military steel signet ring with a blue lightning gem and crossed swords engraved, olive enamel details` |
| `ev_base_neck` | Üs Fırtınası Şampiyonu Kolyesi | `a ball-chain necklace with a steel shield-shaped medal, crossed swords and a glowing blue lightning gem` |
| `ev_base_weapon` | Fırtına Baltası | `a tactical battle axe with an olive grip, a polished steel head with glowing blue lightning lines and crossed-sword engraving` |
| `ev_scrap_ring` | Hurdacılar Kralı Yüzüğü | `a rusty riveted ring made from a small gear, a glowing acid-green bolt as the gem` |
| `ev_scrap_neck` | Hurdacılar Şampiyonu Kolyesi | `a necklace of welded scrap links holding a saw-blade pendant with a glowing acid-green core and rivets` |
| `ev_scrap_weapon` | Hurdacı Testere Kılıcı | `a scrap saw-sword made from a circular saw blade welded to a pipe with rivets, a rusty grip, glowing acid-green edge wear` |
| `ev_pack_ring` | Sürü Lideri Kralı Yüzüğü | `a silver ring shaped like a howling wolf head with glowing purple eyes and a purple moon gem` |
| `ev_pack_neck` | Sürü Lideri Şampiyonu Kolyesi | `a leather and silver necklace with a wolf fang pendant, a purple crescent moon gem and claw marks` |
| `ev_pack_weapon` | Sürü Lideri Pençesi | `a wolf-claw gauntlet with three long curved silver blades, purple gem knuckles and fur trim` |
| `ev_breath_ring` | Son Nefes Kralı Yüzüğü | `a pale bone ring with a small skull holding a glowing ghostly cyan gem, wisps of mist` |
| `ev_breath_neck` | Son Nefes Şampiyonu Kolyesi | `a thin dark chain with an hourglass pendant filled with glowing cyan mist and a tiny skull` |
| `ev_breath_weapon` | Son Nefes Tırpanı | `a long dark scythe with a pale translucent ghostly cyan blade, wisps of mist trailing, a bone-wrapped handle` |

> Kask simgeleri (`ev_*_helm`) eski ödül; gerekmez.

### A3 · Elde silah görseli (zorunlu, 6 adet) — `w_ev_<etkinlik>.png`

Silah simgesindeki tasarımın **oyun içi piksel hali**. Elde nasıl görüneceğini sabitler; karakter animasyonlarında
(B4) referans olarak eklenir.

```
[A1 SİLAH SİMGESİ EKLİ] the same weapon as the attached image, redrawn as a pixel art game sprite, side view lying
horizontally, crisp hard-edged pixels, 1px dark outline, flat solid #00FF00 background, no text
```

---

## B) ANA KARAKTER ANİMASYONLARI

### B0 · Karakter dönüş sayfaları (önce bunlar) — `h_turn_<görünüm>.png`

Bunlar her şeyin referansı. Önce bunları üret, beğendiklerini sonraki bütün istemlere ek olarak ver.

| Dosya | Ne |
|-------|----|
| `h_turn_base.png` | Karakter boş eller, 8 yön tek satırda (0, 1, 2, 3, 4, 5, 6, 7 sırasıyla) |
| `h_turn_basic.png` · `h_turn_police.png` · `h_turn_military.png` · `h_turn_titan.png` | Aynı karakter, o seviyenin kask + yelek + pantolon + botu ve çantasıyla, 8 yön |

```
[REFERANS EKLİ] character turnaround sheet, the same character shown 8 times in one row with wide gaps, rotating in
45 degree steps in this exact order: facing the camera, down-right, right, up-right, away from the camera, up-left,
left, down-left. {KIYAFET} standing relaxed, empty hands, pixel art, flat #00FF00 background, no text
```

| Seviye | `{KIYAFET}` |
|--------|------------|
| basic | `wearing a simple padded cap, a worn canvas vest, cargo pants, old leather boots and a small backpack` |
| police | `wearing a black riot helmet with a raised visor, a police tactical vest, knee-pad pants, police boots and a police backpack` |
| military | `wearing an olive military helmet, a military plate carrier jacket, camo pants, combat boots and a large military rucksack` |
| titan | `wearing a dark armored helm with glowing orange cracks, titan plate armor jacket, armored pants, heavy titan boots` |

> **Kıyafetler animasyonlarda:** Her animasyonu 5 kıyafetle ayrı üretmek binlerce görsel eder. Önerim:
> **animasyonlar temel görünüşle** (referans karakter) üretilir. Kıyafet seviyesi karakter ekranında, profilde ve
> sıralamada bu dönüş sayfalarıyla görünür. Oyun içinde kask ve çanta gibi küçük parçaları ben üstüne eklerim.
> Hepsinin animasyonda da görünmesini istersen haber ver; o zaman sayı 5 katına çıkar.

### B1 · Temel hareketler (boş el) — 8 kare, her yön ayrı

| Dosya | Kare | `{ANİMASYON TARİFİ}` |
|-------|------|----------------------|
| `h_idle_<yön>` | 8 | `idle breathing loop, standing relaxed with empty hands, subtle chest rise, slight hood sway, a small weight shift on frame 5` |
| `h_walk_<yön>` | 8 | `walk cycle loop, empty hands swinging naturally, contact, down, passing, up poses for each leg` |
| `h_run_<yön>` | 8 | `run cycle loop, leaning forward, arms pumping, long strides, both feet off the ground in frames 3 and 7` |
| `h_roll_<yön>` | 8 | `combat dodge roll forward: crouch, dive, tucked roll on the ground, roll through, rise to a crouch, stand up` |
| `h_hurt_<yön>` | 8 | `taking a hit: head snaps back, body recoils, stagger one step back, recover to a guard stance` (yalnız ilk 3-4 kare kullanılır, 8 kare çiz) |
| `h_death_<yön>` | 8 | `death: hit hard, drop to the knees, fall forward to the ground, lie still on the ground in the last 2 frames` |
| `h_knock_<yön>` | 8 | `knocked down by a heavy blow: flung backward, land on the back on the ground, lie dazed` |
| `h_getup_<yön>` | 8 | `get up from lying on the back: roll to the side, push up with arms, kneel, stand up, shake it off` |

> **Ölüm, devrilme, kalkma:** yalnız **0 ve 2** yönleri yeter (diğerleri bunlardan aynalanır).

### B2 · Yumruk dövüşü — `h_punch_<yön>`

```
unarmed brawler attack combo: left jab, right cross, rising uppercut, return to fighting guard, fists clenched,
motion smear on the punching fist in the impact frames
```

`h_punch_idle_<yön>` (8): `fighting stance idle loop, fists raised in guard, light bouncing on the feet`

### B3 · Bıçak ve Katana — her silah için 4 animasyon

Silah görselini (`i_w_knife`, `i_w_katana` ya da kendi çizimin) de ekle: `holding the attached weapon in the right hand`.

| Dosya | `{ANİMASYON TARİFİ}` |
|-------|----------------------|
| `h_knife_idle_<yön>` | `idle loop holding a combat knife in a reverse grip in the right hand, low ready stance` |
| `h_knife_walk_<yön>` | `walk cycle loop holding a combat knife low in the right hand` |
| `h_knife_run_<yön>` | `run cycle loop holding a combat knife, leaning forward` |
| `h_knife_attack_<yön>` | `fast knife combo: quick forward stab, slash across, backhand slash, recover, a thin white motion trail on the blade in the strike frames` |
| `h_katana_idle_<yön>` | `idle loop holding a katana with both hands in a low guard, blade angled forward` |
| `h_katana_walk_<yön>` | `walk cycle loop holding a katana in a low guard with both hands` |
| `h_katana_run_<yön>` | `run cycle loop holding a katana back along the side in the right hand` |
| `h_katana_attack_<yön>` | `heavy katana attack: raise overhead, wide diagonal slash from top-right to bottom-left, follow-through, return to guard, a wide white arc trail in the strike frames` |

### B4 · Etkinlik silahları — 6 silah × 4 animasyon

A3'teki elde silah görselini ve karakter referansını **birlikte** ekle. Silahın vurgu rengindeki parıltı
(kızıl, lav, mavi şimşek, asit yeşili, mor, camgöbeği sis) saldırı karelerinde iz olarak görünsün.

| Silah | Tutuş (`idle/walk/run` için) | Saldırı tarifi (`h_ev_<etkinlik>_attack`) |
|-------|------------------------------|-------------------------------------------|
| `blood` Orak | `holding a long scythe diagonally with both hands, blade curving above the head` | `a wide 270-degree spinning scythe sweep around the body, crimson moon-shaped glowing trail, blood drops` |
| `titan` Balyoz | `carrying a huge two-handed sledgehammer resting on the shoulder` | `raise the sledgehammer overhead, slam it into the ground in front, lava cracks and rocks burst from the impact, recover slowly` |
| `base` Balta | `holding a tactical battle axe in the right hand at the side` | `a strong diagonal axe chop followed by a horizontal backswing, blue lightning sparks on the blade` |
| `scrap` Testere kılıcı | `holding a saw-sword in the right hand, blade spinning slightly` | `three fast spinning-saw slashes left, right, forward, metal sparks flying, acid-green glow on the teeth` |
| `pack` Pençe | `wearing the wolf-claw gauntlet on the right hand, crouched hunter stance` | `a rapid four-hit claw flurry with lunges, three parallel purple claw trails per strike` |
| `breath` Tırpan | `holding a long ghostly scythe low behind the body with both hands` | `a long reaching forward sweep of the scythe, pale cyan ghostly mist trail and wisps, slow and heavy` |

Her silah için 4 dosya: `h_ev_<etkinlik>_idle_<yön>`, `h_ev_<etkinlik>_walk_<yön>`, `h_ev_<etkinlik>_run_<yön>`,
`h_ev_<etkinlik>_attack_<yön>`.

### B5 · İş ve etkileşim (boş el ya da alet) — 8 kare

| Dosya | Yönler | `{ANİMASYON TARİFİ}` |
|-------|--------|----------------------|
| `h_chop_<yön>` | 0-4 | `chopping a tree with a stone axe (reference: i_t_axe_stone): raise the axe over the right shoulder, swing horizontally into an imaginary trunk in front, impact, pull back, wood chips on impact` |
| `h_chop_iron_<yön>` | 0-4 | aynı tarif, `with an iron axe (reference: i_t_axe_iron)` |
| `h_mine_<yön>` | 0-4 | `mining a rock with a stone pickaxe (reference: i_t_pick_stone): raise the pickaxe high, strike down on an imaginary rock in front, impact with stone chips and sparks, pull back` |
| `h_mine_iron_<yön>` | 0-4 | aynı tarif, `with an iron pickaxe (reference: i_t_pick_iron)` |
| `h_pickup_<yön>` | 0, 2, 4 | `bend down and pick up a small item from the ground, look at it, put it into the backpack, stand up` |
| `h_open_<yön>` | 0, 2, 4 | `kneel and open a heavy chest in front: grab the lid, lift it open, light glows on the face, reach inside` |
| `h_drink_<yön>` | 0, 2 | `drink a potion: take a small glass bottle from the belt, drink it with the head tilted back, lower it, a short glowing shimmer around the body` |
| `h_heal_<yön>` | 0, 2 | `use a bandage: wrap a bandage around the left forearm, tighten it, flex the arm` |
| `h_craft_<yön>` | 0, 2, 4 | `working at a workbench in front: hammering, sawing and fitting parts, small sparks and wood shavings` |
| `h_cast_<yön>` | 0-4 | `activate a skill: clench fists, crouch, then burst upward with arms spread, a ring of energy pushes out from the body` |
| `h_talk_<yön>` | 0, 2 | `talking to an NPC: relaxed gestures with one hand, nod, shrug` |
| `h_sit_<yön>` | 0, 2 | `sit down cross-legged by a campfire, warm the hands, breathe slowly (last 4 frames loop)` |

### B6 · Araçlar (motosiklet ve araba) — 8 yön, oyundaki araç görselleriyle aynı

Oyundaki 8 yönlü motor ve cip görsellerini (`x8/moto`, `x8/car`; istersen dosyalarını gönderirim) referans
olarak ekle. **Karakter araçla birlikte çizilir.** Oyun aracı yönüne göre seçip üstüne çizmek yerine bu birleşik
kareyi kullanır.

| Dosya | `{ANİMASYON TARİFİ}` |
|-------|----------------------|
| `h_moto_idle_<yön>` | `sitting on the attached dirt motorcycle, engine idling, both feet on the ground, hands on the handlebar, slight engine vibration` |
| `h_moto_ride_<yön>` | `riding the attached dirt motorcycle at speed, leaning forward, hood and coat flapping in the wind, wheels spinning, small dust behind` |
| `h_moto_turn_<yön>` | `riding the motorcycle while leaning into a turn, knee out, body tilted` |
| `h_car_idle_<yön>` | `sitting in the driver seat of the attached open-top jeep, hands on the steering wheel, waiting` |
| `h_car_drive_<yön>` | `driving the attached open-top jeep at speed, hands on the wheel, coat flapping, wheels spinning, dust behind` |
| `h_mount_<yön>` | yalnız 0, 2: `walk to the motorcycle and swing a leg over to sit on it` (inme bunun tersi oynatılır) |

### B7 · İfadeler ve sosyal hareketler — yalnız 0 yönü, 8 kare

| Dosya | `{ANİMASYON TARİFİ}` |
|-------|----------------------|
| `h_cheer_0` | `victory cheer: raise a fist into the air, jump slightly, land with a confident pose` (seviye atlama, görev bitirme) |
| `h_point_0` | `point forward to a destination with the right arm, look there, nod` |
| `h_wave_0` | `friendly wave to another player` |
| `h_taunt_0` | `taunt: crack the knuckles and beckon the enemy with one hand` |
| `h_levelup_0` | `power-up pose: crouch, then rise with arms spread as golden light bursts upward around the body` |

---

## Sayılar ve öncelik

| Bölüm | Animasyon | Yön (önerilen) | Görsel |
|-------|-----------|----------------|--------|
| A1 simge | 18 | — | 18 |
| A3 elde silah | 6 | — | 6 |
| B0 dönüş | 5 | — | 5 |
| B1 temel | 8 | 0-4 (ölüm, devrilme, kalkma 0 ve 2) | 31 |
| B2 yumruk | 2 | 0-4 | 10 |
| B3 bıçak, katana | 8 | 0-4 | 40 |
| B4 etkinlik silahları | 24 | 0-4 | 120 |
| B5 iş | 12 | tabloya göre | 42 |
| B6 araçlar | 6 | 0-7 (binme 0 ve 2) | 42 |
| B7 ifadeler | 5 | 0 | 5 |
| **Toplam** | | | **≈ 319** |

5-7 yönlerini de ayrı üretirsen toplam yaklaşık 450 görsel olur.

**Öncelik:** A1 → B0 → B1 (idle, walk, run) → B2 → B5 (chop, mine) → B3 → B4 → B6 → kalanlar.
Yarım bir set de işe yarar: gelmeyen animasyon için oyun eldeki en yakın animasyonu kullanır (ör. koşma yoksa
hızlandırılmış yürüme).

## Teslim

- Her görseli tablodaki adla gönder; tek klasörde ya da bölüm bölüm zip.
- Ben: yeşili silerim, kareleri 2×4 ızgaradan keserim, boyu tek ölçüye ve ayakları tek tabana oturturum, eksik
  yönleri aynalarım, silah izlerini efekt olarak ayırırım, oyunun animasyon tablosuna bağlarım.
  Etkinlik eşyaları gelince çanta, mağaza ve etkinlik penceresinde yeni simgeler, ödül detayında afişler görünür.
