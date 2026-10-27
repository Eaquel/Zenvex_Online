# Zenvex Online — V16 Asset Belgesi (kısaltılmış)

V15'in yerine geçer (`docs/archive/22_DEVASA_PROMPT_V15.md`). Karar: **kodla yüksek kalitede yapılabilen her şeyi ben kodla
yaparım** (arayüz kiti, arka planlar, logo, ikonlar, rozetler, efektler, harita işaretleri, hasar yazı tipi). Senden yalnızca
**kodla yapılamayan iki şey** istenir: **3B modeller** (Meshy) ve **hareketsiz piksel yapılar**. Ses isteğe bağlı.

Referans görseller: `docs/denetim/` (`ref_yaratik/`, `ref_npc/`, atlas sayfaları).

---

## 1 · Karar tablosu

| Aile | Yöntem | Kim yapıyor |
|------|--------|-------------|
| Kahraman, silah, araç | 3B | **yapıldı** |
| NPC (9), zombi/yaratık (~22), dört ayaklı (6), akrep/yengeç, bosslar, bot/asker, helikopter, dron, VIP kanatları | **3B (Meshy)** | **sen** (Bölüm 2) |
| Araç kamuflajı, silah görünümü | Meshy Retexture | **sen** (2.7) |
| Eski 26 Blender yapı | **piksel resim** | **sen** (Bölüm 3) |
| Arayüz kiti (panel, tuş, sekme, yuva, çubuk, giriş, diyalog, joystick) | **kod** (9 dilimli, piksel basamaklı, kendi dokusu) | ben |
| 16 ekran arka planı, yükleme ekranları | **kod** (katmanlı, animasyonlu, parçacıklı) | ben |
| Logo, rozet, plaka, çerçeve, kart, mağaza süsleri, bilet, elmas yığınları | **kod** (vektör/piksel çizim) | ben |
| Yetenek ağacı ikonları, sistem/yönetici/destek ikonları, HUD ikonları, harita işaretleri, emote | **kod** (`SkillGlyphs`-tarzı glif motoru) | ben |
| Hasar yazı tipi | **kod** | ben |
| Efektler (ateş, aura, hava, iz, patlama, kıvılcım, unvan) | **kod** (`FxTex`/`AuraFx` motoru) | ben (çoğu yapıldı) |
| Eşya simgeleri | Mevcutlar kalır; silahlar 3B render; ortak çerçeve/nadirlik ışıması kodla | ben |
| Portre, kart içi kahraman | 3B render | ben |
| Ses | Sentezle (kod) + isteğe bağlı sende | ben / sen |

**Kod kalite kuralı:** düz yarı saydam daire yok; dokulu parçacık, çok katmanlı ışık, siluet gölge, bir olayda en çok 3 efekt
katmanı (ışık · gövde · iz). Yazı hiçbir görselin içinde olmaz; kod koyar (10 dil).

**Silinecekler (onayını bekliyor, ~3,5 MB):** `h/kaan/*`, `l/*`, eski `v/*` arabalar, `x8/moto`, `x8/car`, `page_23` (eski efekt
sayfası), kullanılmayan `tracer`/`heli_dust`/`magma_ball`, `w/ev_*`, `ui/btn_drive2.png`, `ui/lb_gold.png`, `i/chest`.
Yaratıklar 3B'ye geçince `z/*` (~21,7 MB) de gider. Depodaki `art/` (37 MB) APK'ya girmiyor, depoyu şişiriyor.

---

## 2 · 3B ÜRETİM (Meshy)

### 2.0 Genel kurallar

1. GLB, dokulu (albedo yeter). Üçgen sayısı yüksek olabilir; ben küçültüp bake ederim.
2. **İnsansı:** A-pozu (bacaklar omuz genişliğinde, **kollar ~35° açık**, avuçlar aşağı, yüz öne). **Dört ayaklı:** ayakta nötr, bacaklar düz ve ayrık, kuyruk düz. Silah modele kaynaşık olabilir, kollar gövdeye yapışık olmasın.
3. Boy metre cinsinden tablolarda; oranlar doğru olsun. Tek parça, tek karakter: **zemin, gölge, podyum, yazı yok**.
4. El boyaması koyu kıyamet sonrası palet, saf yeşil yok. Meshy'nin varsayılan eksenini (Y yukarı, yüz +Z) olduğu gibi bırak.
5. Ayrı parça: helikopter (ana + kuyruk rotoru), dron (4 pervane), kanat (sol-sağ).
6. Dosya adı `<kimlik>.glb`. İnsansı yaratık/NPC için `image-to-3D` ve referans `docs/denetim/ref_*/<ad>.png`; **referans tariften önceliklidir**.

**Stil satırı:** `stylized dark post-apocalyptic game character, hand-painted albedo texture, readable strong silhouette, grounded realistic proportions, full body, single subject, plain background, no text, no logo, no ground`

### 2.1 NPC'ler (9)

| Kimlik | Boy | Tarif |
|--------|-----|-------|
| `npc_marco` Tüccar | 1,75 | wandering trader, wide-brim hat, patched coat, many pouches, rolled blanket on back, stubble |
| `npc_silas` Görevci | 1,70 | old gray-bearded quest giver, long worn coat, walking stick, slightly hunched |
| `npc_elena` Doktor | 1,68 | field doctor woman, stained white coat over green scrubs, medic bag, red cross armband |
| `npc_viktor` Tamirci | 1,80 | burly mechanic, oily overalls, goggles on forehead, wrench in belt, tattooed forearms |
| `npc_jack` Köpek Eğitmeni | 1,82 | dog trainer, rugged vest, leather gloves, whistle on a cord, short beard |
| `npc_aldric` Büyücü | 1,78 | old mage, dark purple hooded robe with faint glowing runes, staff with pale crystal |
| `npc_diana` Komutan | 1,74 | military commander woman, olive uniform, red armband, short dark hair, sidearm |
| `npc_soldier` Asker | 1,82 | government soldier, olive helmet and vest, rifle on a sling |
| `npc_sniper` Nişancı | 1,80 | ghillie-suit sniper, hooded camouflage, scoped rifle on the back, face wrapped |

### 2.2 İnsansı yaratıklar (22) — hepsi zombi: soluk gri-yeşil çürümüş ten, yırtık giysi

| Kimlik | Boy | Tarif |
|--------|-----|-------|
| `z_walker` | 1,75 | civilian zombie, torn shirt and jeans, hollow eyes, slightly hunched |
| `z_runner` | 1,75 | lean sinewy runner, torn tracksuit, stretched veins, forward-leaning |
| `z_toxic` | 1,85 | yellow-green hazmat suit with hood and cracked gas mask, green glow at seams |
| `z_dark` | 1,85 | black cracked skin, violet glow in cracks, tattered dark cloak, white eyes |
| `z_tank` | 2,30 | hulking, fused riot plates and scrap armor, massive arms, tiny head |
| `z_mystery` | 1,95 | tall faceless figure, black suit and tie, blank pale mask, very long arms |
| `z_lumberjack` | 2,00 | torn flannel, thick beard, rusty axe embedded in shoulder, bark-like skin |
| `z_shroom` | 1,90 | covered in glowing mushrooms and moss, rotting hiking clothes |
| `z_office` | 1,75 | torn dress shirt and tie, name-badge, briefcase stuck to the hand |
| `z_riot` | 1,95 | dented helmet with cracked visor, scorched riot shield on arm, body armor |
| `z_crawler` | 1,00 | legless crawler on long thin arms, **yüzüstü poz** (A-pozu yok; ayrı iskelet benim) |
| `z_charred` | 1,95 | black cracked skin with glowing embers underneath, smoking |
| `z_ashwraith` | 2,00 | ghostly gray figure, lower body dissolving into ash, hovering, wispy cloth |
| `z_mummy` | 1,95 | dirty bandages, sand-dried skin, glowing eyes, tattered cloth ends |
| `z_biker` | 1,95 | studded leather vest, cracked half helmet, chains across chest |
| `z_bloater` | 2,10 | huge translucent green belly full of sludge, thin limbs, stained apron |
| `z_boggiant` | 2,80 | moss, mud and roots, vines, huge hands, glowing yellow eyes |
| `z_witch` | 1,70 | hooded ragged robe, bone charms and jars on belt, crooked fingers |
| `z_sailor` | 1,90 | ice-crusted oilskin coat, icicles on beard and hands, frost on blue-gray skin |
| `z_dockbrute` | 2,60 | huge, rusty crane hook instead of one hand, chains around torso |
| `z_flamebot` | 1,95 | armored flamethrower soldier, fuel tanks on back, glowing orange vents |
| `z_shieldbot` | 1,90 | steel riot shield on left arm, helmet with narrow visor, short baton |

### 2.3 Dört ayaklılar (6), özel iskeletliler (2), bosslar (2)

| Kimlik | Boy/uzunluk | Tarif |
|--------|-------------|-------|
| `q_wolf` Kuduz Kurt | 1,5 | rabid wolf, matted fur, exposed ribs, foaming jaws, glowing eyes |
| `q_emberhound` Kor Tazısı | 1,6 | charred hunting dog, glowing orange cracks, fire in the mouth |
| `q_zdog` Zombi Köpek | 1,2 | rotting skin, exposed bone, torn ear, gaunt |
| `q_dog0` Köpek Sv1 (dost) | 1,1 | young German-shepherd-type dog, worn red bandana (ref `pet_dog0`) |
| `q_dog5` Köpek Sv5 | 1,2 | same breed, leather harness with small steel plates (ref `pet_dog5`) |
| `q_dog10` Alfa Sv10 | 1,3 | same breed, scarred, heavy spiked armored harness, glowing blue collar (ref `pet_dog10`) |
| `x_scorpion` Dev Akrep | 1,6 | segmented armored body, pincers raised, curved stinger tail, sand chitin; pençe ve kuyruk düz |
| `x_icecrab` Buz Yengeci | 1,8 | frost-covered shell with ice spikes, two huge pincers, six legs spread, blue-white glow |
| `b_titan` TİTAN | 7,0 | colossal muscular body, torn flesh, exposed ribs, chains and rusted cables, tiny head, glowing eyes (ref `titan`) |
| `b_magma` Magma Titan | 6,0 | black obsidian plates with glowing lava cracks, horned head, claws, molten chest core (ref `magma`) |

Boss'lar için üçgen hedefi 24 bin; diğerleri 6-11 bin.

### 2.4 Araç ve havadan

| Kimlik | Boyut | Not |
|--------|-------|-----|
| `v_truck` kervan kamyoneti | 4,6 m | tekerlekler ayrı parça |
| `v_heli` helikopter | 12 m | olive drab transport, **ana ve kuyruk rotoru ayrı** |
| `v_drone` dron | 0,8 m | quadcopter combat drone, dark gray, camera eye, **4 pervane ayrı** |

### 2.5 VIP kanatları (4) — yalnız kanat, omuz noktası orijinde, iki kanat ayrı ve simetrik, yarı açık

`wing_ash` yanık tüylü, kor uçlu · `wing_dragon` kızıl zar, kemik parmaklı, altın kenarlı · `wing_titan` obsidyen plakalı, plakalar arası lav · `wing_s1` gece mavisi tüylü, yıldız tozlu, altın uçlu.

### 2.6 Retexture (aynı mesh, yeni doku)

`hellhound` ve `nightstalker`: **Gece** (siyah-lacivert dijital desen), **Altın Kaplama** (cilalı altın, kızıl şerit), **Sezon 1** (gece mavisi, yıldız deseni) · `katana`: **Obsidyen** (siyah cam, mor ışık), **Savaş Lordu** (altın-çelik) · tırpanlar: **Titan Tırpanı** (magma çatlaklı), **Kanlı Ay** (kızıl-gümüş).
İstem: `repaint this exact object with a {GÖRÜNÜM} livery, keep geometry and proportions identical, hand-painted, no text`.

### 2.7 Ben ne yapıyorum

GLB → oyun modeli hattı, dört ayaklı / akrep-yengeç / sürüngen / süzülen hayalet iskeletleri, tüm animasyon setleri (kodla), oyuna bağlama, kanat çırpma, doku varyantı, portre render'ları, eski sprite'ları silme, **telefonda hız ölçümü**.

---

## 3 · PİKSEL YAPILAR (26 nesne, 4 sayfa)

Eski Blender yapılar (düz, steril, **tabelalı**) piksel dünyaya (`page_02`, `page_24`) uydurulup yeniden çizilir. Her sayfaya eski görseli (`docs/denetim/page_00.jpg`, `page_01.jpg`) ve stil için `page_24.jpg` ekle.

**Stil satırı:** `high quality pixel art, crisp hard-edged pixels, no anti-aliasing blur, 1px dark selective outline, hue-shifted shading, light from the top-left, dark post-apocalyptic palette, consistent with the attached reference style`

**Şablon:** `[STİL] 3/4 top-down view of a top-down survival RPG. {N} separate objects on one sheet in {R} rows x {C} columns, wide empty space between objects, each object complete and uncropped, same scale, no cast shadows. Redraw each attached object as pixel art in the style reference, keep shape, size ratio, camera angle and footprint, weathered and abandoned, cracked paint, rust, grime, boarded windows, signs and billboards BLANK with no lettering. Objects: {LİSTE}. flat solid #00FF00 green background, no text, no grid lines.`

Kurallar: zemin düz #00FF00 (nesnede saf yeşil yok), kesme yok, ≥1024 px, yazı yok.

| Sayfa | Düzen | Nesneler |
|-------|-------|----------|
| `b3_city_big.png` | 2×2 | `apartment0` kırmızı tuğla 3 katlı · `apartment1` krem balkonlu · `police` karakol (mavi şerit, tepe lambası; tabela boş) · `tower` ahşap gözetleme kulesi |
| `b3_city_small.png` | 2×3 | `shop0` market (sarı tente) · `shop1` bakkal (kırmızı-beyaz tente) · `house0/1/2` evler · `stall` pazar tezgâhı |
| `b3_road.png` | 2×4 | `gas` benzinlik (pano boş) · `lamp` · `wreck0` SUV enkazı · `wreck1` spor araba enkazı · `gate` bariyer ve kum torbası · `wall_x` · `wall_z` · `fence` |
| `b3_small.png` | 2×4 | `tent` · `sandbags` · `barrel` · `barrel_tox` · `cone` · `trash` · `log` · `billboard` (boş) |

Bölge yapıları (`page_24`, 40+ nesne) yeniden istenmiyor; eksik çıkarsa yalnız onlar için istem yazarım.

---

## 4 · SES (isteğe bağlı)

37 efektin hepsi kullanılıyor. Yaratık sesleri için ortak sesler yeterli; **ben kodla sentezlerim** (motor döngüsü, ateş, nitro, kanat, mağaza şıngırtısı, kart açılışı).
Kendin üretmek istersen (ElevenLabs vb.): `sfx_<tür>_<idle|attack|hurt|death>.wav`, 0,5-1,5 sn, `{TARİF}, single short game sound effect, close mic, no music, no reverb tail`; türler: lumberjack, wolf, shroom, office, riot, crawler, charred, emberhound, ashwraith, mummy, scorpion, biker, bloater, boggiant, witch, sailor, icecrab, dockbrute, titan.

---

## 5 · SIRA VE TESLİM

| Sıra | Paket | Neden |
|------|-------|-------|
| 1 | 2.2 insansı yaratıklar (önce 8 temel + 6 bölge) | APK −16 MB, en büyük sıçrama |
| 2 | 2.1 NPC'ler | dünya canlanır |
| 3 | 2.3 dört ayaklı, özel, boss | kalan yaratıklar |
| 4 | 3 piksel yapılar | tabelalı eskiler gider |
| 5 | 2.4 araç/hava, 2.5 kanat, 2.6 retexture | VIP görünümleri |

Her paketi ayrı zip gönder (`3d/`, `world/`); ben geldikçe bağlarım. Her dosyayı bu listeyle eşleştirip eksik/sorunluyu bildiririm; 3B için önizleme sayfası da veririm.

**Kontrol:** yazı yok · saf yeşil nesnede yok · kesilmiş parça yok · aynı sayfanın parçaları aynı istemde · 3B: A-pozu, kollar açık, tek parça, zemin/gölge yok, ayrı parçalar ayrı.

**Eşya listesi** (kimlik, ad, nadirlik) belgeden çıkarıldı: kaynak `rust/zenvex_core/src/data.rs`.
