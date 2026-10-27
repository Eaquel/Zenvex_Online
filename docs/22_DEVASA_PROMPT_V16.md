# Zenvex Online — V16 Devasa Asset ve Prompt Belgesi

Bu belge V15 istem belgesinin (`docs/archive/22_DEVASA_PROMPT_V15.md`) yerine geçer. V15 belgesi "her şeyi yapay zekâ
resmiyle üret" varsayımıyla yazılmıştı. Bu arada oyunun çekirdeği değişti: kahramanlar, silahlar ve araçlar artık gerçek 3B
model; 3B çizici, iskelet, animasyon ve kod efektleri çalışıyor. Bu belge **her kalem için en iyi yöntemi seçer**
(3B model, yapay zekâ resmi ya da kod) ve her yöntem için üretim şartnamesini verir.

Okuma sırası: **1 Denetim** (neden değişiyor) → **2 Yöntem kararları** (ne neyle üretilecek) → 3 Efektler (hepsi kod, senden
istenen yok) → 4 3B üretim → 5 Piksel dünya → 6 Arayüz kiti → 7 Eşya simgeleri → 8 Ses → 9 Teslim ve sıra.

> Referans görseller: `docs/denetim/` klasöründeki iletişim sayfaları (atlas sayfaları ve arayüz görselleri, bugünkü hâli).
> İstemlerde "referans ekle" yazan yerde ya bu sayfalardan ilgili kısmı ya da aynı adlı oyun içi görseli ekle.

---

## 0 · KISACA — 10 karar

1. **Hareket eden her şey 3B olacak:** NPC'ler, bütün zombi ve yaratıklar, köpekler, bosslar, botlar, helikopter, dron,
   VIP kanatları. Neden: aynı model her yönden, her animasyonla, her ışıkta çizilir. Şu an yaratıklar **3.152 kare / ~22 MB**
   (APK'nın üçte ikisi); 3B ile ~5 MB ve çok daha fazla animasyon.
2. **Hareketsiz dünya nesneleri piksel resim olarak kalır** (yapılar, doğa, zemin). Mevcut piksel sayfalar (`page_02`,
   `page_24`) oyunun en güçlü işi; yeni parçalar onlara uydurulur. Eski Blender'dan çıkma düz yapılar yeniden çizilir.
3. **Arayüz sıfırdan, bir "kit" olarak** çizilir: dokuz parçalı paneller, tuş durumları, yuvalar, çubuklar, ikonlar, 16 ekran
   arka planı. Kaynak çözünürlük **iki katına** çıkar (şimdi 24 px'lik ikonlar var).
4. **Efektlerin tamamı kodla, yüksek kalitede.** Efekt resmi istenmiyor (V15'teki Bölüm 5, 7, 10D ve 15'in efektleri iptal).
   Kod efektleri artık dokulu parçacık + rün mührü + siluet gölge motoruyla çiziliyor; düz yarı saydam daire yok (bkz. 3.3).
   Üst üste binen efektlerin kökü de buydu (bkz. 1.5 ve 3).
5. **Kamuflaj, silah görünümü, kanat** gibi "aynı şeyin boyalı hâli" olan kalemler resim yerine **doku değişimi** olur
   (30 araç kamuflaj resmi yerine 3 doku).
6. **Yazı hiçbir görselin içinde olmayacak.** Eski görsellerde Türkçe tabela var ("MARKET", "POLİS", "BAKKAL", "BENZİN");
   oyun 10 dilli, yazıyı kod koyar.
7. **Boşta duran görseller APK'dan çıkarılır** (1.2: ~3,5 MB; yaratıklar 3B'ye geçince ~−17 MB daha).
8. **Kodun beklediği ama dosyası olmayan** altı görsel var (1.3); önce onlar.
9. **Kahraman animasyon istemleri (`docs/20` B bölümü) iptal:** kahramanlar 3B ve kodla canlanıyor.
10. **Önce 3B yaratıklar** (APK ve kalite kazancı en büyük), sonra arayüz kiti.

---

## 1 · DENETİM — APK'ya giren her görsel

### 1.1 Üç farklı sanat kuşağı karışık duruyor

| Kuşak | Nerede | Durum |
|-------|--------|-------|
| **A · Blender render (düz, steril)** | `page_00`, `page_01` (2,8 MB): apartmanlar, polis, market, bakkal, evler, benzinlik, enkaz arabalar, duvar, çit; `page_05` eski arabalar (0,9 MB) | Üstüne üstlük **yazılı tabelalar** ve piksel dünyaya uymayan düz ışık. Yeniden çizilecek (Bölüm 5.2). |
| **B · Piksel (güçlü)** | `page_02` (yapılar, kule, çadır, tank), `page_24` (bölge yapıları: deniz feneri, vinç, kilise, kulübeler, kamp ateşi, hastane), doğa (ağaç, çalı, kaya), zemin | Kalite çıtası bu. Dokunulmaz. |
| **C · Boyalı büyük simge** | yüzükler, kolyeler, etkinlik silahları (`page_04`), klan armaları, çerçeveler, adalar | Güzel ama eşya simgelerinin yarısı 16-32 px'lik minik piksel; yan yana tutarsız (Bölüm 7). |

### 1.2 Boşta duran / ölü ağırlık

| Ne | Boyut (tahmini) | Neden ölü |
|----|-----------------|-----------|
| Kahraman piksel kareleri `h/kaan/*` (208) + lobi `l/*` (12) | ~0,55 MB | Kahramanlar 3B; yalnız 3B yüklenemezse yedek |
| Eski araba görselleri `v/*` (24), `x8/moto`, `x8/car`, `x/moto`, `x/car` | ~1,3 MB | Motor ve araba 3B. (`x8/truck` kervan kamyoneti için kalır) |
| Eski efekt sayfası `page_23` (bulanık degrade: `glow`, `skid`, `rage`, `exhaust`) | ~1,7 MB | `glow` ve `skid` hiç kullanılmıyor; `rage`/`exhaust` düşük kalite, kodla değişecek |
| Kullanılmayan efektler: `tracer`, `heli_dust`, `magma_ball` | küçük | Kodda hiç çağrılmıyor |
| Piksel etkinlik silahı kaplamaları `w/ev_*` (6) | ~0,04 MB | 3B silah geldi |
| `ui/btn_drive2.png`, `ui/lb_gold.png`, `i/chest` | küçük | Hiçbir yerde geçmiyor |
| **Toplam hemen silinebilir** | **~3,5 MB** | |
| Depodaki `art/` klasörü (konsept, önizleme, v4-v10) | 37 MB | APK'ya girmiyor ama depoyu şişiriyor |
| Yaratık sprite sayfaları (`z/*`, 3.152 kare) | **~21,7 MB** | Silinmiyor, **3B'ye çevrilecek** (Bölüm 4) |

Ses: 37 efektin hepsi kullanılıyor (`groan*`, `step*`, `zattack` adlarla dinamik çağrılıyor). Müzik 2 parça.

### 1.3 Kodun beklediği ama dosyası olmayan görseller

Kodda "varsa onu çiz, yoksa yedek" olarak bağlı; dosya gelince otomatik görünür:

| Dosya | Nerede kullanılıyor |
|-------|---------------------|
| `logo_wordmark` | lobi ve yükleme ekranı logosu |
| `shop_bg` | mağaza arka planı |
| `btn_shop` | mağaza tuşu |
| `ticket_vip`, `ticket_premium` | mağaza ve pass biletleri |
| `char_bg` | karakter ekranı arka planı |
| `gems_1..6`, `char_<kimlik>_card` | elmas paketleri; (kart görselleri 3B ile gereksizleşti) |

### 1.4 Çözünürlük sorunu

`ic_*` ikonları **18-24 px**, `lb_*` sıralama ikonları **16-34 px**, `btn_close` 23 px, yuva çerçeveleri 28-66 px. Telefonda
2340×1080'de bunlar iri bloklara dönüşüyor. Yeni kitte kaynak çözünürlük iki katına çıkıyor (Bölüm 6).

### 1.5 Efekt çakışmaları (kodu okuyarak)

| Olay | Üst üste binen katmanlar | Sorun |
|------|--------------------------|-------|
| **Motor/araba sürerken (arkada)** | ① zemin izi (60 ms'de bir) ② yüzey parçacığı (kum/kar/çamur/kül/toprak/asfalt dumanı) ③ **tekerlek fırlatması sprite'ı `road_*`** (aynı noktada, ②'yle aynı iş) ④ egzoz sprite'ı ⑤ **toz sprite'ı `dust`** (hız>160; ②, ③ ile aynı iş) ⑥ far ışığı ⑦ kaplama izi (yalnız elmas kaplamada) | Aynı tekerlek noktasında **üç ayrı toz/fırlatma efekti** + iz + egzoz |
| Vuruş (`hit`/`dmg`) | `spark` sprite + 6-8 parçacık + (3B) element patlaması + ekran sarsıntısı | Aynı çarpma anında 3 kıvılcım |
| Öldürme (`kill`) | `blood` sprite + kan gölü + `dust` sprite + 16 parçacık | Kan ve toz iki kez |
| Patlama (`explode`) | `explosion` + `shockwave` + `fire` + `smoke` + ışık + 2 parçacık patlaması + sarsıntı | 4 sprite + 2 emitter üst üste |
| Seviye atlama (`lvl`) | `levelup` + `level_ring` + 40 parçacık + afiş | Halka iki kez |
| Silah savurma/ateş | eski `slash`/`muzzle` sprite'ı + (yeni) kod izi / namlu alevi | Yeni sistem gelince eski yalnız 3B yokken çalışacak şekilde kapatıldı |

Kök neden: aynı işi yapan **sprite ve parçacık** birlikte atılıyor ve her olayın kendi tarifi yok. Çözüm Bölüm 3'te.

**Uygulandı (kodda):** motor/araba arkası (3.2), vuruş (3B silahta tek element patlaması) ve öldürmedeki ikinci toz. Patlama ve seviye atlama
bu belgedeki kurala göre bir sonraki efekt turunda sadeleşecek.

### 1.6 V15 belgesi: ne teslim edildi

| V15 bölümü | Durum | V16'da |
|------------|-------|--------|
| 1 Logo | Yok (kod kancası hazır) | Bölüm 6.3 |
| 2 HUD | Yok | Bölüm 6.1 (`kit_hud_*`) |
| 3 Eski 3B yapılar (26) | Yok; hâlâ eski, yazılı tabelalı | Bölüm 5.2 (yazısız) |
| 4 Bölge yapı setleri (46) | **Büyük kısmı V13'te teslim** (`page_24`: fener, vinç, kilise, kulübeler, hastane, benzinlik...) | Yeniden istenmiyor; eksikler zip gelince tespit |
| 5 Ateş/aura/hava (19 animasyon) | Resim olarak yok | **Kod** (Bölüm 3) |
| 6 Sıralama plakaları (44) | Kısmen: 4 genel plaka (`plate_1/2/3/n`) | 4 plaka + 9 motif rozeti, kod birleştirir (6.4) |
| 7 Unvan efektleri | Yok | **Kod** |
| 8 Yetenek ağacı (52) | Sanat yok; kodla çizilmiş glifler (`SkillGlyphs.kt`) | Bölüm 6.5 |
| 9 Mağaza (34) | Kısmen: `crate_vault`, hediye, madalya, pass parçaları | Bölüm 6.6 (eksikler) |
| 10 VIP görünümleri | Yok | Kanat 3B, kamuflaj/silah görünümü doku, ayak izi kod (4.7, 4.8, 6.7) |
| 11 Karakter sistemi | **3B ile çözüldü** (kartlar canlı 3B) | Yalnız `char_bg` + kart çerçeveleri (6.2) |
| 12 Harita | `island_*`, `region_*`, `map_clan/gov/unknown/war` var | İşaret seti, çerçeve, kilit (6.8) |
| 13 Panel/destek/admin | Yok | Bölüm 6.9 |
| 14 NPC animasyonları (32 sayfa) | Yok | **3B** (4.1); 32 sayfa gerekmiyor |
| 15 Kalite artırıcılar | Çoğu efekt | Kod; kalanlar 6.10 |
| 16 Ses | İsteğe bağlı, sfx 37 var | Bölüm 8 |

---

## 2 · YÖNTEM KARARLARI

Seçim kuralı: **hareket ediyor mu, çok açıdan görünüyor mu, aynı şeyin renk varyantı var mı?** Evet → 3B. Hareketsiz tek
açı, el çizimi doku gerektiriyor → resim. Zaman/parçacık/ışık → kod.

| Aile | Yöntem | Gerekçe | Etki |
|------|--------|---------|------|
| Kahraman, silah, araç | **3B (yapıldı)** | — | — |
| NPC (9) | **3B** | Konuşma, yürüme, tepki animasyonu kodla; 32 animasyon sayfası yerine 9 model | ~−1,5 MB, çok daha canlı |
| İnsansı yaratıklar (~22) | **3B** | 8 yön × N kare yerine tek model | **~−16 MB**, akıcı 60 fps animasyon |
| Dört ayaklılar (kurt, kor tazısı, zombi köpek, 3 köpek dostu) | **3B + dört ayaklı iskelet** (ben yazarım) | Aynı | dahil |
| Akrep, buz yengeci | **3B + özel iskelet** (ben yazarım) | Aynı | dahil |
| Titan, Magma Titan | **3B yüksek ayrıntı** | Magma tek başına 6,5 MB | −5 MB |
| Bot (2), kule askeri (2), helikopter, dron | **3B** | Rotor/silah ayrı parça | küçük |
| VIP kanatları (4) | **3B** (kanat + çırpma kodla) | 4 kanat × 3 yön × 8 kare = 96 kare yerine 4 model | −1 MB |
| Araç kamuflajı (3), silah görünümü (4) | **Doku değişimi** | Aynı mesh, yeni doku (Meshy "retexture") | 30 resim → 3 doku |
| Yapılar (eski 26) | **Piksel resim** | Hareketsiz; piksel dünyaya uyum | ±0 |
| Doğa, zemin | **Mevcut, dokunma** | Kalite yüksek | — |
| Eşya simgeleri (129) | **Boyalı piksel resim**, silahlar 3B render (yapıldı) | Tutarlı set, 128 px | +1 MB |
| Arayüz kiti, arka planlar, logo, rozet, çerçeve, plaka, beceri ikonu, mağaza görselleri | **Resim** (yapay zekâ) | El çizimi malzeme | +4 MB |
| Efektler (ateş, aura, hava, iz, patlama, kıvılcım, ışıma, unvan efekti) | **Kod** | Bölüm 3 | **−2 MB**, sınırsız varyasyon |
| Profil/NPC portreleri | **3B render** (ben) | Modelden otomatik | — |

**APK bütçesi (tahmini):** şimdi ~54 MB ham varlık. Yaratıklar 3B'ye geçince −16, ölü ağırlık −3,5, efekt sayfası −1,7;
yeni arayüz kiti ve simgeler +5 → **~38 MB civarı**. (Ölçüp gerçek rakamı raporlarım.)

---

## 3 · EFEKTLER — hepsi kod (senden istenen yok)

### 3.1 Kural: bir olay en çok 3 katman, her katman farklı iş

| Rol | Ne yapar | Örnek |
|-----|----------|-------|
| **Işık** | Toplamalı karışım, ≤0,15 sn | namlu parlaması, patlama ışığı |
| **Gövde** | Olayın asıl görüntüsü (parçacık/şerit) | toz bulutu, kıvılcım yağmuru |
| **İz** | Kalıcı kalıntı (zemin lekesi, duman) | lastik izi, kan gölü |

Aynı rolde iki efekt olmaz. Eski sprite efektler kaldırılır; her olayın **tek bir efekt tarifi** (preset) olur.

### 3.2 Motor/araba arkası: 7 katman → 3 katman *(uygulandı)*

| Rol | Yeni tek efekt |
|-----|----------------|
| Gövde | **Tekerlek tozu**: tek yayıcı arka tekerlek noktasında; renk/boyut yüzeye göre (kum, kar, çamur, kül, toprak, asfaltta yalnız hızlıyken lastik dumanı), yoğunluk hıza göre |
| İz | **Lastik izi** zemin dekali (yumuşak zeminde kalıcı, asfaltta yalnız frende) |
| Işık | **Egzoz alevi** yalnız ivmelenirken + far konisi; Hellhound'da nitro |
| (Kaldırılan) | `road_*` sprite'ı, `dust` sprite'ı, `exhaust` sprite'ı, ayrı `surfaceFx` parçacığı |
| (Koşullu) | Elmas kaplama izi yalnız kaplama takılıysa; ana efektle aynı yayıcıya renk katar |

### 3.3 Kalite çıtası ve efekt motoru v2 *(uygulandı)*

Düz yuvarlak yarı saydam diskler efekt sayılmaz. Motor artık şunları kullanır (`FxTex.kt`, `AuraFx.kt`):

| Parça | Ne yapıyor |
|-------|------------|
| **Dokulu parçacık** | Kodla üretilen piksel-art dokular: lobludan duman/toz bulutu (8), alev dili (6, S eğrili, beyaz öz, dağılan uç), 4 köşeli parıltı, kaya parçası, sıçrama lekesi, halka. Işık sol üstten, 4 ton, 1 px koyu kenar |
| **Rün mührü aura** | Yerde yatık elips: çift dış halka, dönen kesik çizgili halka, 48 çentik, 12 dönen rün (6 şekil), ters dönen iki üçgen yıldız, nabız dalgası, 7 ışık sütunu; üstüne küçük alev dilleri ve kor. Kademeler: karakter Sv10 mor · Sv25 gümüş-mavi · Sv40 kızıl-mor · Sv50 altın-kızıl |
| **Siluet gölge** | Kahraman ve araç karesi yere yatırılıp eğilir (iki geçiş, güneş yönünde), temas noktasında koyu iz; eski oval gölge kalktı |
| **Ayak/tekerlek tozu** | Yüzey rengine göre bulut + yumuşak zeminde kaya parçası; koşu adımının yere değdiği karede çıkar |
| **Lastik izi** | Yuvarlak uçlu, kenar vurgulu, tırtıllı şerit |

Yeni efekt eklerken kural: önce `FxTex` dokusu, sonra parçacık; **düz daire çizen yeni efekt kabul edilmez.**
Hâlâ eski dairesel olan kalemler (kod listesi 3.4'te işaretli) sırayla bu motora taşınacak.

### 3.4 Yapılacak efekt listesi (Claude kodlar)

*(Aura, gölge, toz, iz, silah izi ve namlu alevi bu listede **yapıldı**; kalanlar: patlama ve seviye atlama katman sadeleştirmesi,
zehir kabarcığı, deri izleri, NPC/sandık altındaki oval gölgeler.)*

- **Araç:** tekerlek tozu (6 yüzey), lastik izi, frende kayma dumanı, egzoz, nitro, far, düşük canda duman, çok düşük canda alev.
- **Kahraman:** ayak tozu (6 yüzey), takla izi, aura Sv25 (gümüş-mavi halka), Sv40 (mor-kızıl halka, alev dilleri), Sv50 (altın-kızıl dönen mühür, yükselen
  alev ve rün kıvılcımları; arka ve ön katman), iksir auraları (can/hız/güç), kalkan, öfke, zehir, donma, yanma, ışınlanma giriş-çıkış
  (rün çemberi), seviye atlama (yerden yükselen halka + ışık sütunu).
- **Ayak izleri (VIP):** kıvılcım, alev, buz; sönerek kaybolan.
- **Unvan efektleri:** altın (kayan ışık şeridi + 4 köşeli parıltı), gümüş (soğuk parıltı, halka nabzı), bronz (alttan kıvılcım), VIP (alev çizgisi + kor).
- **Silah:** savurma izi, namlu alevi, kovan, element efektleri, vuruş patlaması *(yapıldı)*.
- **Dünya:** kamp ateşi/meşale/varil/mangal (alev + kor + duman, üç katman), yanık orman ateşleri, su halkası, ışık hüzmeleri,
  sandık parıltısı, ganimet ışık sütunu (nadirliğe göre renk), etkileşim halkası, görev işareti kristali, patlamalar (4 boy), kan lekesi/gölü.
- **Hava (bölgeye göre):** yağmur, kar (Liman), kül + kor (Yanık Orman), kum tozu (Çöl), alçak sis (Kara Bataklık).

---

## 4 · 3B ÜRETİM ŞARTNAMESİ (Meshy veya benzeri)

### 4.0 Genel kurallar (her model)

Aşağıdakileri Meshy'de ayarla / isteme ekle. Daha önce gönderdiğin 24 modelle aynı yöntem; boru hattım hepsini otomatik işliyor.

1. **Biçim:** GLB, dokulu (albedo yeter; PBR/normal gerekmiyor). Üçgen sayısı yüksek olabilir, ben küçültüp yeniden bake ederim.
2. **Poz (insansı):** **A-pozu**: ayakta, bacaklar omuz genişliğinde, **kollar gövdeden ~35° açık**, avuçlar aşağı, yüz öne bakıyor, nötr duruş.
   Silah/eşya modele **kaynaşık** olabilir (balta saplı, kalkan kolda) ama kollar gövdeye yapışık olmasın.
3. **Poz (dört ayaklı):** ayakta nötr, dört bacak düz ve ayrık, baş ileri, kuyruk düz arkada, ağız kapalı.
4. **Ölçek:** boyu belirtilen metreye göre; ben normalize ederim ama **oranlar** doğru olsun (kafa/gövde/bacak).
5. **Tek parça, tek karakter:** zemin, gölge, arka plan, podyum, yazı **yok**.
6. **Doku:** el boyaması görünüm, koyu kıyamet sonrası palet; **yazı/logo yok**; saf yeşil yok.
7. **Eksen:** Meshy'nin varsayılan GLB çıktısı (Y yukarı, yüz +Z) **olduğu gibi** kabul edilir; dışa aktarırken döndürme. Modeli yan veya arkadan üretme, öne bakan üret. (Araçlarda boy ekseni X; Meshy'nin önceki araçları öyleydi, farklı gelirse ben çeviririm.)
8. **Ayrı parça gereken modeller:** helikopter (ana rotor + kuyruk rotoru ayrı), dron (4 pervane ayrı), kanat (sol-sağ ayrı).
9. **İsim:** `<kimlik>.glb` (aşağıdaki tablolardaki "kimlik"). Meshy'nin varsayılan uzun adı da olur, yanına kimliği yaz.

**Stil satırı (3B istemlerinin başına):**
`stylized dark post-apocalyptic game character, hand-painted albedo texture, readable strong silhouette, grounded realistic proportions, full body, single subject, plain background, no text, no logo, no ground`

### 4.1 NPC'ler (9) — insansı, A-pozu

**Referans:** `docs/denetim/ref_npc/<ad>.png` (mevcut piksel görünüş). `image-to-3D` kullan ve **görünüşü aynen koru**; tarif ile referans çelişirse referans kazanır. Boylar metre.

| Kimlik | Kişi | Boy | Tarif |
|--------|------|-----|-------|
| `npc_marco` | Gezgin Tüccar Marco | 1,75 | `middle-aged wandering trader, wide-brim hat, heavy patched coat, many pouches and a rolled blanket on the back, friendly weathered face, stubble` |
| `npc_silas` | Görevci Yaşlı Silas | 1,70 | `old gray-bearded quest giver, long worn coat, walking stick, tired kind eyes, hunched slightly` |
| `npc_elena` | Doktor Elena | 1,68 | `field doctor woman, stained white coat over green scrubs, medic bag, hair tied back, red cross armband` |
| `npc_viktor` | Tamirci Viktor | 1,80 | `burly mechanic, oil-stained overalls, goggles on forehead, big wrench in belt, rolled sleeves, tattooed forearms` |
| `npc_jack` | Köpek Eğitmeni Jack | 1,82 | `dog trainer, rugged vest, leather gloves, whistle on a cord, short beard, confident stance` |
| `npc_aldric` | Büyücü Aldric | 1,78 | `mysterious old mage, dark purple hooded robe with faint glowing runes, tall staff with a pale crystal, long beard` |
| `npc_diana` | Komutan Diana | 1,74 | `military commander woman, olive uniform with a red armband, short dark hair, stern face, sidearm holster` (portre: `commander_portrait`) |
| `npc_soldier` | Devlet askeri / kule askeri | 1,82 | `post-apocalyptic government soldier, olive helmet and vest, rifle on a sling, worn gear` |
| `npc_sniper` | Keskin nişancı | 1,80 | `ghillie-suit sniper, hooded camouflage, long rifle with scope on the back, face wrapped` |

### 4.2 İnsansı yaratıklar (~22) — A-pozu, zombi hâli

**Referans:** `docs/denetim/ref_yaratik/<ad>.png` (kimlikteki `z_` ön ekini at: `z_walker` → `walker.png`). `image-to-3D` ile üret; referans tariften önceliklidir. Hepsi **zombi**: soluk gri-yeşil çürümüş ten, yırtık giysi, hafif kambur ama **A-pozunu koru**. Boy = oyundaki ölçek çarpanıyla uyumlu.

| Kimlik | Ad | Boy | Tarif (stil satırına ekle) |
|--------|----|-----|----------------------------|
| `z_walker` | Zombi | 1,75 | `ragged civilian zombie, torn shirt and jeans, gray-green rotting skin, hollow eyes, slightly hunched` |
| `z_runner` | Koşucu | 1,75 | `lean sinewy runner zombie, torn tracksuit, stretched veins, wild eyes, gaunt, forward-leaning` |
| `z_toxic` | Toksik Zombi | 1,85 | `toxic zombie in a stained yellow-green hazmat suit with a hood and a cracked gas mask, sickly green glow at the seams, sludge stains` |
| `z_dark` | Karanlık Zombi | 1,85 | `shadowy dark zombie, black cracked skin with faint violet glow in the cracks, tattered dark cloak, glowing white eyes` |
| `z_tank` | Tank Zombi | 2,30 | `huge hulking tank zombie, fused riot plates and scrap armor, massive arms, tiny head, heavy stance` |
| `z_mystery` | Gizemli Maskeli | 1,95 | `tall faceless figure in a black suit and tie, blank pale featureless face mask, unnaturally long arms, calm eerie still stance` |
| `z_lumberjack` | Oduncu Zombi | 2,00 | `lumberjack zombie, torn flannel, thick beard, a rusty axe embedded in the shoulder, bark-like skin patches` |
| `z_shroom` | Mantar Zombi | 1,90 | `fungus-infected zombie covered in glowing mushrooms and moss, soft spore glow, rotting hiking clothes` |
| `z_office` | Ofis Zombisi | 1,75 | `office worker zombie, torn dress shirt and loosened tie, name-badge, briefcase stuck to the hand, gray skin` |
| `z_riot` | Çevik Kuvvet Zombisi | 1,95 | `riot police zombie, dented helmet with cracked visor, scorched riot shield on the arm, body armor, rusty` |
| `z_crawler` | Sürüngen | 1,00 | `legless crawling zombie dragging itself on long thin arms, prone pose` — **özel**: yüzüstü poz (A-pozu yok); ben ayrı iskeletle canlandırırım |
| `z_charred` | Kömürleşmiş Zombi | 1,95 | `burnt charred zombie, black cracked skin with glowing embers underneath, smoking, shredded remains of clothes` |
| `z_ashwraith` | Kül Hayaleti | 2,00 | `ash wraith, ghostly gray figure whose lower body dissolves into drifting ash, hollow glowing eyes, hovering, wispy tattered cloth` |
| `z_mummy` | Mumya | 1,95 | `desert mummy zombie wrapped in dirty bandages, sand-dried skin, glowing eyes in the wrappings, tattered cloth ends` |
| `z_biker` | Motorcu Zombi | 1,95 | `biker zombie, studded leather vest, cracked half helmet, chains across the chest, tattooed rotting arms` |
| `z_bloater` | Şişkin | 2,10 | `huge swollen bloater zombie, enormous translucent green belly full of sludge, thin limbs, stained apron` |
| `z_boggiant` | Bataklık Devi | 2,80 | `swamp giant zombie covered in moss, mud and roots, vines hanging, huge hands, glowing yellow eyes` |
| `z_witch` | Bataklık Cadısı | 1,70 | `swamp witch zombie, hooded ragged robe, bone charms and jars hanging from the belt, long crooked fingers, faint green glow` |
| `z_sailor` | Donmuş Denizci | 1,90 | `frozen sailor zombie, ice-crusted oilskin coat, icicles on the beard and hands, frost on blue-gray skin` |
| `z_dockbrute` | Rıhtım Canavarı | 2,60 | `dock brute zombie, huge muscular, a rusty crane hook in place of one hand, chains wrapped around the torso, torn dock-worker clothes` |
| `z_flamebot` | Alev Askeri (bot) | 1,95 | `armored flamethrower soldier, scorched heavy armor, fuel tanks on the back, nozzle in the hands, glowing orange vents` |
| `z_shieldbot` | Kalkan Askeri (bot) | 1,90 | `armored shield soldier, heavy steel riot shield on the left arm, helmet with narrow visor, short baton` |

### 4.3 Dört ayaklılar (6) — ayakta nötr poz

| Kimlik | Ad | Uzunluk (burun-kuyruk dibi) | Tarif |
|--------|----|------|-------|
| `q_wolf` | Kuduz Kurt | 1,5 | `rabid infected wolf, patchy matted fur, exposed ribs, foaming jaws, glowing eyes` |
| `q_emberhound` | Kor Tazısı | 1,6 | `ember hound, charred lean hunting dog, glowing orange cracks in the skin and fire in the mouth, smoking` |
| `q_zdog` | Zombi Köpek | 1,2 | `zombie dog, rotting skin, exposed bone, torn ear, gaunt` |
| `q_dog0` | Köpek, seviye 1 (dost) | 1,1 | `young German-shepherd-type dog with a worn red bandana, bright loyal eyes, alert` (referans `pet_dog0.png`) |
| `q_dog5` | Köpek, seviye 5 (dost) | 1,2 | `same breed in a leather harness with small steel armor plates, stronger build` (`pet_dog5.png`) |
| `q_dog10` | Alfa, seviye 10 (dost) | 1,3 | `same breed, scarred and muscular, in a heavy spiked armored harness with a glowing blue collar light, majestic` (`pet_dog10.png`) |

### 4.4 Özel iskelet gerektirenler (ben yazarım)

| Kimlik | Ad | Boy/uzunluk | Tarif |
|--------|----|-------------|-------|
| `x_scorpion` | Dev Akrep | 1,6 | `giant desert scorpion, segmented armored body, big pincers raised, curved stinger tail, sand-colored chitin with dark joints` — pençe ve kuyruk **düz uzatılmış** |
| `x_icecrab` | Buz Yengeci | 1,8 | `giant ice crab, frost-covered shell with ice spikes, two huge pincers, six legs spread, blue-white glow` — bacaklar ayrık |

### 4.5 Bosslar (yüksek ayrıntı, A-pozu)

| Kimlik | Ad | Boy | Tarif |
|--------|----|-----|-------|
| `b_titan` | TİTAN | 7,0 | `colossal titan zombie, mountainous muscular body with torn flesh and exposed ribs, chains and rusted cables wrapped around, tiny head with glowing eyes, long arms` |
| `b_magma` | Magma Titan | 6,0 | `magma titan, giant humanoid of black obsidian plates with glowing lava cracks, horned head, claws, molten core visible in the chest, ember glow` |

Referans: `ref_yaratik/titan.png`, `ref_yaratik/magma.png` (referans tariften önceliklidir). Boss'lar için üçgen hedefi 24 bin; kalan her şey 6-11 bin.

### 4.6 Araçlar ve havadan: ayrı parçalı

| Kimlik | Ad | Boyut | Not |
|--------|----|-------|-----|
| `v_truck` | Kervan kamyoneti (SUV) | 4,6 m | tekerlekler ayrı parça (Hellhound'la aynı kural) |
| `v_heli` | Takviye helikopteri | 12 m | ana rotor ve kuyruk rotoru **ayrı**; `military transport helicopter, olive drab, weathered, side doors open` |
| `v_drone` | Dron | 0,8 m | dört pervane **ayrı**; `quadcopter combat drone, dark gray, camera eye, small red light` |

### 4.7 VIP kanatları (4) — kanat tek başına

Karakter yok; yalnız kanat (omuz bağlantı noktası orijinde), **iki kanat simetrik ve ayrı parça**, kanatlar yarı açık duruşta. Ben çırpmayı kodla yaparım,
sırt hizası kahramana otomatik oturur.

| Kimlik | Kaynak | Tarif |
|--------|--------|-------|
| `wing_ash` | V8 | `pair of burnt feathered wings, charred black feathers with glowing ember tips, ash flakes` |
| `wing_dragon` | V10 | `pair of dragon wings, dark crimson membrane over bone fingers, gold trim on the edges` |
| `wing_titan` | V10+ | `pair of colossal obsidian plated wings, lava glowing between the plates, jagged, the most imposing` |
| `wing_s1` | Pass 30 | `pair of night-blue feathered wings with sparkling star dust and gold feather tips` |

### 4.8 Doku değişimi (kamuflaj ve silah görünümü) — Meshy "Retexture"

Aynı mesh, yeni doku: Meshy'de ilgili modelin **Retexture** özelliği; geometri aynı kalmalı (ben tek UV'ye bake ederim).

| Hedef | Görünümler |
|-------|------------|
| `hellhound`, `nightstalker` kamuflajı | **Gece** (V6): siyah-lacivert dijital desen · **Altın Kaplama** (V9): cilalı altın, kızıl şerit · **Sezon 1** (Pass): gece mavisi, yıldız deseni |
| `katana` | **Obsidyen Katana** (V7): siyah cam, mor ışık · **Savaş Lordu Katanası**: altın-çelik |
| `ice_scythe` / `blood_scythe` | **Titan Tırpanı** (V10+): magma çatlaklı · **Kanlı Ay Kılıcı** (Pass 25): kızıl-gümüş |

Retexture istemi: `repaint this exact object with a {GÖRÜNÜM} livery, keep geometry and proportions identical, hand-painted, no text`.

### 4.9 Benim yapacaklarım (3B geldikten sonra)

- GLB → oyun modeli hattı (yaratık ve NPC için hazır); **dört ayaklı**, **akrep/yengeç**, **sürüngen**, **süzülen hayalet** iskeletleri.
- Her yaratık için animasyon seti (kodla): durma, yürüme, koşma, saldırı 1-2, vurulma, ölme, yerden doğma, kükreme; boss'larda özel saldırılar
  (Magma: nefes, top, pençe, yere vurma).
- NPC: durma, nefes, konuşurken el hareketi, oyuncuya dönme, selam; nöbetçi/keskin nişancı için dürbünle bakma.
- Oyuna bağlama (yaratık çizimi, köpek dostu, kervan, helikopter, dron), kanat çırpma, doku varyantı tabanlı kamuflaj, portre render'ları.
- Eski sprite sayfalarını silip APK'yı küçültmek ve **telefonda hız ölçümü**.

---

## 5 · PİKSEL DÜNYA (resim)

### 5.0 Kurallar (V15 Bölüm 0'dan, değişmeden)

1. **Stil (her istemin başına):** `high quality pixel art, crisp hard-edged pixels, no anti-aliasing blur, 1px dark selective outline, hue-shifted shading, light from the top-left, dark post-apocalyptic palette, consistent with the attached reference style`
2. **Kamera:** 3/4 yukarıdan; ön yüz görünür.
3. **Zemin:** düz **#00FF00**; nesnede saf yeşil yok (zeytin/koyu yeşil kullan).
4. **Yazı yok:** tabela, pano, afiş **boş**; yazıyı kod koyar (çok dilli).
5. **Kesme yok**, nesneler arasında geniş boşluk, aynı ölçek.
6. **Çözünürlük:** en az 1024 px (tercihen 2048); ben piksel ızgarasına oturturum.
7. **Tutarlılık:** aynı sayfanın parçaları **aynı istemde**.

**Dünya nesnesi sayfası şablonu**
```
[STİL] 3/4 top-down view of a top-down survival RPG.
{N} separate objects on one sheet in a {R} rows x {C} columns layout, wide empty space between objects, each object
complete and uncropped, same scale for all objects, objects sit on an invisible flat ground, no cast shadows.
Objects: {LİSTE}.
flat solid #00FF00 green background, no text, no numbers, no grid lines.
```

### 5.1 Neden piksel resim (3B değil)

Yapılar hareketsiz, tek açıdan görünüyor ve `page_02`/`page_24`'teki güçlü piksel işine uyması gerekiyor. 3B render üçüncü bir görünüş
daha eklerdi. (Kahramanlar 3B olsa da piksel dünyada iyi duruyor; yapılar o dünyanın parçası.)

### 5.2 Eski Blender yapıların yeniden çizimi — 26 nesne, 4 sayfa

Referans: `docs/denetim/page_00.jpg`, `page_01.jpg` (eski görseller) + stil için `page_24.jpg`. Her sayfaya o sayfanın eski görsellerini ekle ve şunu yaz:
`redraw each attached object as pixel art in the same style as the style reference, keep the same shape, size ratio, camera angle and footprint, make it look weathered and abandoned (zombie apocalypse), cracked paint, rust, grime, broken windows boarded up, signs and billboards are BLANK with no lettering`

| Sayfa | Düzen | Nesneler (eski anahtar) |
|-------|-------|-------------------------|
| `b3_city_big.png` | 2×2 | `apartment0` kırmızı tuğla 3 katlı (klima, su deposu, uydu) · `apartment1` krem balkonlu · `police` polis karakolu (mavi şerit, tepe lambası, bariyer; **tabela boş**) · `tower` ahşap gözetleme kulesi |
| `b3_city_small.png` | 2×3 | `shop0` market (sarı tente, kasalar; **tente yazısız**) · `shop1` bakkal (kırmızı-beyaz tente; **yazısız**) · `house0` kırmızı kiremit · `house1` gri çatılı tuğla · `house2` yeşil çatılı mavi · `stall` çizgili tenteli pazar tezgâhı |
| `b3_road.png` | 2×4 | `gas` benzinlik (kırmızı-beyaz sundurma, 2 pompa; **pano boş**) · `lamp` sokak lambası · `wreck0` paslı SUV enkazı · `wreck1` turuncu spor araba enkazı · `gate` bariyerli kapı ve kum torbaları · `wall_x` yatay beton duvar (dikenli tel) · `wall_z` dikey duvar parçası · `fence` ahşap çit |
| `b3_small.png` | 2×4 | `tent` kamp çadırı · `sandbags` · `barrel` kırmızı varil · `barrel_tox` sarı zehirli varil · `cone` · `trash` çöp ve moloz · `log` kütük · `billboard` boş reklam panosu |

Not: tabelaya yazılan Türkçe metni (MARKET, POLİS, BAKKAL, BENZİN) kod çok dilli olarak üzerine koyar; sen boş tabela üret.

### 5.3 Bölge yapı setleri

`page_24`'te 40'ı aşkın bölge yapısı var (V13). V15'teki 8 sayfalık liste **yeniden istenmiyor**. Yeni zip'i verdiğinde eksik kalanları tek tek
tespit edip yalnız onlar için istem yazarım.

---

## 6 · ARAYÜZ KİTİ (resim, sıfırdan)

### 6.0 Stil kararı ve teknik şartlar

**Karar: piksel diline sadık kal, çözünürlüğü iki katına çıkar.** Oyun yazı tipi ve dünya piksel; pürüzsüz vektör arayüz uymaz. Ama 24 px'lik ikonlar
yetmiyor. Yeni kit "yüksek çözünürlüklü piksel": kenarlar basamaklı ve net, **bulanıklık yok**, detay iki kat ince.

**Arayüz stil satırı (her kit istemine):**
`hand-painted dark post-apocalyptic game UI asset, high-resolution pixel-art look with clean stepped edges and no blur or anti-aliasing smear, thick 2px dark outline, riveted gunmetal steel and aged leather with rust, warm amber and cold teal accents, soft top-left bevel light, one consistent family look`

**Teknik şartlar (hepsi için):**
- Zemin **#00FF00** (ya da belirtilen sahne arka planı). Yazı, harf, sayı **yok** (tek istisna: hasar rakamları 6.10).
- **Dokuz parçalı** (9-slice) çizimler: köşeler 24 px, orta bölge **düz/tekrar edebilen** (desen yok), kenarlar tek yönde esnetilebilir. Ortası **boş**.
- **Üç parçalı** tuşlar: iki uç + esneyen orta.
- Her tuş/yuva için durumlar **aynı sayfada yan yana**: normal · basılı · devre dışı (· seçili).
- Işık her yerde sol üstten.

### 6.1 Kit sayfaları (11 sayfa)

Her istem tek görselde; sayfa düzeni verildi. Dosya adı kalın.

| Dosya | İçerik ve düzen | İstem (stil satırından sonra) |
|-------|------------------|-------------------------------|
| **`kit_panels.png`** | 3×2: 6 panel çerçevesi — ① koyu metal ana panel ② açık deri/parşömen panel ③ cam-koyu şeffaf panel (çanta için) ④ seçili/vurgu paneli (amber kenar) ⑤ tehlike paneli (kızıl) ⑥ premium panel (altın-bordo) | `six empty rectangular UI panel frames, 3 columns x 2 rows, each with an empty flat center and ornate riveted border, same size, wide gaps: dark gunmetal main panel, light worn leather panel, dark smoked-glass panel with thin steel edge, highlighted panel with amber glowing edge, danger panel with red edge and cracks, premium panel with gold and burgundy trim` |
| **`kit_buttons.png`** | 5 renk × 3 durum (5 satır × 3 sütun): ana (amber), ikincil (çelik), onay (yeşil), tehlike (kızıl), premium (altın); normal · basılı · devre dışı | `fifteen empty horizontal button plates in 5 rows x 3 columns, rows are amber, steel, green, red, gold; columns are normal, pressed (darker, inset, 2px lower), disabled (desaturated gray); empty centers, rounded riveted ends` |
| **`kit_tabs.png`** | 2 satır: sekme (aktif / pasif) ve sekme çubuğu zemini | `tab strip pieces: an inactive tab, an active tab (raised, amber top edge), and a thin dark tab-bar background, empty centers` |
| **`kit_slots.png`** | 4×2: eşya yuvası çerçeveleri — Sıradan, Nadir, Destansı, Efsanevi, Sezonluk, seçili, kilitli (zincirli), boş | `eight empty square item slot frames 4x2: common gray steel, rare blue, epic purple, legendary gold with flame accents, seasonal crimson, selected (bright amber corners), locked (chains and a padlock), empty dark inset` |
| **`kit_bars.png`** | çubuk çerçeveleri: ince (10:1), kalın (6:1), kapsül (can/XP) ve **dolgu şeritleri** (can kırmızı, XP mavi, dayanıklılık sarı, pass altın, zehir yeşil; her biri 10:1, ortası düz) | `empty horizontal progress bar frames in thin, thick and capsule shapes, and matching flat gradient fill strips in red, blue, yellow, gold and green, riveted steel ends` |
| **`kit_inputs.png`** | giriş alanı (normal / odak / hata), onay kutusu (kapalı / açık), anahtar (kapalı / açık), kaydırıcı (ray + tutamaç), radyo (kapalı / açık) | `UI form controls: text input field in normal, focused (amber glow) and error (red) states, checkbox unchecked and checked, toggle switch off and on, slider rail with a round knob, radio button off and on` |
| **`kit_dialog.png`** | iletişim kutusu çerçevesi (başlık şeridi dahil), bildirim şeridi (toast), ipucu balonu (kuyruklu), uyarı rozeti | `empty dialog window frame with a title ribbon on top, a thin toast notification strip, a small tooltip bubble with a tail, and a red notification dot with a count bubble` |
| **`kit_misc.png`** | ayırıcı çizgiler (3 çeşit), kaydırma çubuğu, köşe süsleri, ok işaretleri (4 yön), yükleme halkası çerçevesi | `UI trim pieces: three divider lines (plain, ornate with a center diamond, chain), a vertical scrollbar track and thumb, four corner ornaments, four arrow chevrons, a loading ring frame` |
| **`kit_hud_buttons.png`** | 3×4, 12 simge (HUD): altın, elmas, yeşil +, mağaza (aralık sandık), takla, silah değiştir, yetenek, etkileşim (açık el), inşa, onayla, iptal, kum saati | `icon sheet, 3 rows x 4 columns, each icon centered in its own cell, bold readable silhouette, thick dark outline, same visual weight`. Simgeler: ① altın para yığını (küçük, parlak) ② elmas (mavi-turkuaz, keskin yüzeyli) ③ yeşil **+** düğmesi (yuvarlak köşeli kare, kabarık) ④ **mağaza**: kapağı aralık, içinden altın ışık taşan küçük hazine sandığı ⑤ **takla**: kıvrılıp yuvarlanan figür silüeti + hareket çizgileri ⑥ **silah değiştir**: çapraz bıçak ve katana, aralarında dönen ok ⑦ **yetenek**: parlayan pençe izi / enerji küresi ⑧ **etkileşim**: açık el ⑨ **inşa**: çekiç ve ızgara karesi ⑩ **onayla**: kalın yeşil tik ⑪ **iptal**: kalın kırmızı çarpı ⑫ **saat**: küçük kum saati |
| **`kit_hud_bars.png`** | üst bar: para kapsülü (6:1; soldaki simge yuvası, sağdaki + yuvası), seviye rozeti (düz gri; renklendirmeyi kod yapar), teklif şeridi (8:1; sol hediye yuvası) | **Para kapsülü:** `pixel art horizontal UI capsule bar for a mobile game HUD, dark gunmetal with riveted ends and a thin inner bevel, empty center for numbers, left end has a round socket for an icon, right end has a small square socket for a plus button, 6:1 aspect ratio` · **Seviye rozeti:** `small shield-shaped level badge for an enemy health bar, neutral light-gray steel with a dark center plate, beveled rim, front view` (renkleri kod boyar: gri/yeşil/sarı/turuncu/kırmızı) · **Teklif şeridi:** `thin horizontal banner strip for a limited-time offer timer, dark red-black leather with gold stitched trim, small gift-box socket on the left, empty center, 8:1` — hepsi #00FF00 zeminde |
| **`kit_joystick.png`** | joystick tabanı, joystick topuzu, saldırı çubuğu çerçevesi, takla/silah/yetenek tuş zeminleri (yuvarlak, 3 durum) | `mobile game controls: a round joystick base ring with notches, a round thumb knob, a long attack-stick track, and round action button plates in normal, pressed and cooldown states, riveted steel with cyan glow accents` |

### 6.2 Ekran arka planları (16)

Tam sahne, **16:9, 2560×1440**, **karakter ve yazı yok**; önemli içerik ortadaki 16:9 güvenli alanda, kenarlarda boşluk (telefon 20:9). Karartma ve kayma
efektini kod yapar. Isı sol üst, derinlik hissi, koyu palet.

| Dosya | Ekran | Sahne |
|-------|-------|-------|
| `bg_lobby.png` | Lobi | `ruined city at dusk, burning orange sky, silhouettes of derelict skyscrapers, a campfire and barricade in the foreground, drifting embers, wide empty center for a 3D hero` |
| `char_bg.png` | Karakterler | `dark hall with four empty spotlit pedestals in a row, dust motes in the light beams, cracked concrete floor, rusted steel pillars` |
| `bg_craft.png` | Üretim | `cluttered survival workbench in a dim garage, hanging tools, pegboard, warm lamp light, parts and blueprints, empty center` |
| `bg_garage.png` | Garaj | `post-apocalyptic repair garage, an empty lift bay in the center, tires, engine hoist, oil stains, cold fluorescent light` |
| `shop_bg.png` | Mağaza | `post-apocalyptic black market tent interior at night, lanterns, crates of loot, glowing gem display cases, warm gold and teal light, depth, no characters` |
| `pass_bg.png` | Pass | `a long cracked road leading to a glowing ruined fortress under a crimson sky, banners on both sides, epic scale` |
| `crate_vault.png` | Elmas Kasası | `circular armored vault chamber, a lit empty pedestal in the center, cold spotlight from above, red-black metal walls` |
| `clan_bg.png` | Klan | `fortified clan hall interior, long wooden table, hanging banners, trophies, torches, empty center` |
| `lb_bg.png` | Sıralama | `gladiator arena seen from the stands at dusk, empty podium steps in the center, banners, floodlights` |
| `mail_bg.png` | Posta | `old postal office desk, scattered letters, a brass lamp, wax seals, cork board, empty center` |
| `map_bg.png` | Büyük harita | `weathered wooden table seen from above with torn map corners, pins, a compass, candle light, empty center` |
| `wizard_bg.png` | Büyücü haritası | `deep night sky with purple nebula, floating rune circles, faint stars, mystical glow` |
| `skill_bg.png` | Yetenek ağacı | `dark ancient stone floor with faint carved concentric rings and four faded directional runes, dim blue torchlight vignette, subtle cracks` |
| `settings_bg.png` | Ayarlar | `dark blurred workshop wall with steel plates and rivets, subtle vignette, very low contrast` |
| `support_bg.png` | Destek | `a cozy field office with a radio set, desk lamp, notice board, dim teal light, low contrast` |
| `admin_bg.png` | Yönetici | `command room, wall of region maps, monitors with blank screens, dark blue light` |

Yükleme/ışınlanma ekranları (8, bölge başına `load_<bölge>.png`, 16:9, karakter yok): her bölgenin atmosferik manzarası; referans `region_<bölge>.webp`.

### 6.3 Logo — 3 görsel

Eski amblem (kırmızı kapüşonlu, katanalı adam) kaldırılıyor. Yerine oyunun adı.

| Dosya | İstem |
|-------|-------|
| `logo_wordmark.png` | `pixel art game logo wordmark reading exactly "ZENVEX ONLINE", "ZENVEX" large on top in heavy chiseled rusted-steel letters with cracked edges, faint ember glow in the cracks and a thin blood-red underline, "ONLINE" smaller below in clean bold steel letters with wide letter spacing, subtle dripping rust, post-apocalyptic zombie survival mood, centered, flat solid #00FF00 green background, no other text, no characters, no weapons, no moon` (**tek istisna: yazılı**) |
| `logo_mark.png` | `square pixel art app badge: a stylized metal letter "Z" made of two crossed rusted steel beams with a small ember glowing at the center, chipped edges, dark iron border ring, flat solid #00FF00 green background, no other text` |
| `store_feature.png` | Google Play öne çıkan görseli **1024×500**: logo için sağda boşluk, solda karakter ve köpek, arkada yıkık şehir ve titan silüeti (karakter için 3B render'ımı kullanabilirsin; istersen ben üretirim) |

(Logo parıltısını kod yapar, `logo_glow` istenmiyor.)

### 6.4 Sıralama plakaları — 4 plaka + 9 motif

V15'in 44 plakası yerine **4 kademe plaka + 9 motif rozeti**; kod birleştirir (kademe × motif).

- **`plate_tiers.png`** (4 plaka alt alta, **5:1**, ortası %70 boş düz koyu yazı alanı, uçlarda küçük süs yeri **boş**): `four horizontal nameplate banners stacked vertically with wide gaps, each a slim 5:1 plate for a player title floating above a character in a top-down game: a flat dark center panel taking 70% of the width, empty round sockets at both ends for small emblems, no wings, no big crown, nothing sticking far out. Plate 1 polished gold with a tiny crown point on top and ruby inset, plate 2 polished silver with sapphire inset, plate 3 bronze with amber inset, plate 4 plain dark steel with rivets. flat solid #00FF00 green background, no text`
- **`plate_motifs.png`** (3×3, her biri 96 px'lik yuvarlak/rozet simgesi): Seviye = yıldız · Zombi Avı = kafatası · Oyuncu Avı = çapraz kılıçlar · Titan = magma çatlaklı boynuz · Asker = kalkan · Üs Fethi = bayrak · Köpek = pati izi · Zanaat = çekiç ve örs · Emektar = kum saati. Stil: altın kenarlı koyu madalyon.
- **`plate_special.png`** (2×4, aynı kurallar): Kan Ayı (kızıl-kemik), Titan (magma), Üs Savaşı (çelik-bayrak), Hurda Avı (paslı dişli), Sürü (pençe), Kış Nefesi (buz kristali), **Sezon 1 Öncüsü** (gece mavisi-altın), **VIP10+** (obsidyen, ateş damarlı).

### 6.5 Yetenek ağacı — 52 parça, kodla çizilen glifler yerine sanat

Ağaç: merkez + 4 dal (Yakın Dövüş **kızıl**, Silah Ustalığı **çelik mavisi**, Dayanıklılık **toprak yeşili/bronz**, Çeviklik **mor-camgöbeği**).
Şablon: **simge sayfası**, zemin **#00FF00**, her simge **daire içine sığar** (çerçeveyi kit koyar), **128 px**.

**5A · Küçük düğümler (21): `skill_small_1.png` 3×4 ve `skill_small_2.png` 3×3**

| # | Kod | Simge | # | Kod | Simge |
|---|-----|-------|---|-----|-------|
| 1 | MeleeFlat | sıkılmış yumruk, darbe çizgileri | 12 | DmgTaken | çatlamış ama ayakta kalkan |
| 2 | FistPct | sargılı boks eli, çatlayan taş | 13 | Reflect | kalkandan geri seken ok |
| 3 | DmgPct | yukarı kızıl ok ve kılıç ucu | 14 | BlastRes | patlama içinde sağlam kalkan |
| 4 | Crit | ortası parlayan nişangâh | 15 | Regen | yaprak sarmalı kalp, yeşil artı |
| 5 | Stun | dönen yıldızlar ve çekiç | 16 | LastBreath | kum saati içinde son damla kalp |
| 6 | Lifesteal | kan damlası içinde kalp | 17 | Dodge | kenara kayan silüet, yanından ok |
| 7 | BladePct | parlayan katana ağzı | 18 | Speed | kanatlı çizme |
| 8 | Reach | uzayan kılıç, iki uçlu ok | 19 | RollCd | dairesel ok içinde yuvarlanan figür |
| 9 | AtkSpeed | üç ardışık hızlı kesik izi | 20 | Stamina | sarı enerji şimşeği |
| 10 | CritDmg | kırılan kafatası, kızıl kıvılcım | 21 | IFrame | yarı saydam hayalet silüet |
| 11 | HpFlat | dolu kırmızı kalp | | | |

**5B · Belirgin düğümler (4): `skill_notable.png` 1×4** — Ağır El (taş gibi dev yumruk) · Usta Kılıç (çapraz iki katana) · Kalın Deri (perçinli deri zırh) · Rüzgâr (rüzgâr girdabı ve tüy).

**5C · Anahtar düğümler (12): `skill_key_1.png`, `skill_key_2.png` 2×3**
Yakın Dövüş: **Kan Emici** (vampir dişli yumruk) · **Sarsıcı Darbe** (yere vuran yumruk, şok dalgası) · **Yıkım** (alevli patlayan yumruk).
Silah Ustalığı: **Kanlı Kenar** (kan damlayan katana ağzı) · **Rüzgâr Kesik** (hilal rüzgâr kesiği) · **Ölüm Ustası** (kafatası kabzalı kara kılıç).
Dayanıklılık: **Kaya** (taş gövdeli göğüs) · **Son Nefes** (ölüm meleği kanadı altında kalp) · **Titan Derisi** (magma çatlaklı zırh).
Çeviklik: **Gölge Adım** (gölgeye dönüşen ayak izi) · **Fırtına** (şimşekli hız girdabı) · **Hayalet** (yarı saydam pelerinli figür).

**5D · Amblemler (5): `skill_emblems.png` 1×5** — Hayatta Kalan (kamp ateşi önünde kapüşonlu silüet; kapüşonu 3B hâle uygun, Kael) + 4 dal madalyonu (kızıl yumruk, mavi kılıç, yeşil kalkan, mor kanatlı çizme).

**5E · Düğüm çerçeveleri (9): `skill_frames.png` 3×3** — satırlar: küçük (yuvarlak ince), belirgin (altıgen kalın), anahtar (yıldızlı taçlı); sütunlar: kilitli (koyu taş, zincir), açılabilir (parlak kenar), alınmış (altın). Ortası **boş**.

### 6.6 Mağaza ve ekonomi — eksikler

Kullanıcı görünce "almak istemeli": pırıltılı, dolu, değerli. Referans: `crate_vault.webp`, `pass_bg.webp`, mevcut kasa.

| Dosya | Adet | İstem özü |
|-------|------|-----------|
| `btn_shop.png` | 1 | mağaza tuşu: kapağı aralık, içinden altın ışık taşan küçük hazine sandığı (HUD simge dili) |
| `shop_featured_frame.png` | 1 | büyük öne çıkan teklif çerçevesi (altın-kızıl, köşelerde perçin), ortası boş, 2:1 |
| `shop_gems.png` (`gems_1..6`, 2×3) | 6 | artan elmas yığınları: avuç dolusu · deri kese · küçük sandık · kilitli kasa · taşan hazine sandığı · ejderha heykelli hazine yığını |
| `shop_ribbon_x2.png` | 1 | köşe kurdelesi (yazısız; kızıl-altın) |
| `pack_start/growth/war.png` | 3 | Başlangıç: yeşil-bronz sırt çantası taşan malzeme · Gelişim: mavi-gümüş askeri kasa, motor parçaları · Savaş: kızıl-altın zırhlı sandık, silah ve kalkan |
| `card_weekly/monthly.png` | 2 | dikey 3:4 değerli kartlar: haftalık gümüş kenar · aylık altın kenar, elmas işlemeli |
| `ticket_vip.png`, `ticket_premium.png` | 2 | VIP: mor-gümüş delikli bilet · Premium: altın-kızıl, mühürlü, ışık saçan |
| `frame_s1.png` | 1 | S1 profil çerçevesi (mevcut çerçevelerle aynı ölçü, yuvarlak iç): gece mavisi-altın, tepede yıldız rozeti |
| `vip_badge_1.png` / `vip_badge_2.png` | 11 | V1-V6 ve V7-V10+: bronz → gümüş → altın → elmas → obsidyen-ateş, her biri öncekinden süslü, ortası numara için boş |
| `shop_tabs.png` | 6 | yıldız · elmas · hediye kutusu · taç · takvim kartı · bilet |
| `card_frames.png` (1×4) | 4 | karakter kartı çerçeveleri 3:4: Sıradan gri-çelik · Nadir mavi · Destansı mor · Efsanevi altın-kızıl alevli; ortası boş (içine canlı 3B kahraman konur) |
| `card_back.png` | 1 | kartın arkası: koyu deri, kabartma Z amblemi (`logo_mark` ile uyumlu) |

Satın alma patlaması, sandık açılışı, kart açılış ışığı **kod** (3.3).

### 6.7 VIP görünümleri (resim kısmı)

- **Sohbet balonu temaları** `chat_themes.png` 2×2: Bronz (V3) · Gümüş (V7) · Altın (V10) · Obsidyen-Ateş (V10+). Yuvarlak köşeli, alt ortada kuyruk, süs yalnız köşelerde, **ortası düz koyu**, 9 dilimli.
- **Giriş afişleri** `entry_banner_v5.png` (gümüş-mavi, 6:1) ve `entry_banner_v10.png` (altın-kızıl, alevli uçlar, 6:1), ortası boş.
- **Silah görünümü simgeleri** `weapon_skins.png` 2×2 (Obsidyen Katana · Titan Tırpanı · Kanlı Ay Kılıcı · Savaş Lordu Katanası): 3B doku değişimi (4.8) gelince simgeyi ben render ederim; **bu resim istenmiyor**.
- Kanat, kamuflaj, ayak izi: 3B/doku/kod (4.7, 4.8, 3.3).

### 6.8 Harita

| Dosya | Adet | İçerik |
|-------|------|--------|
| `map_markers.png` | 20 (4×5) | kendin (altın ok), diğer oyuncu (beyaz ok), klan üyesi (yeşil ok), doktor (kızıl haç), tamirci (İngiliz anahtarı), eğitmen (hedef), tüccar (kamyonet önden), büyücü (asa ve yıldız), görev veren (parşömen), komutan (bayrak), sandık, tezgâh, kamp ateşi, görev hedefi (altın elmas), titan (magma kafatası), boss (boynuzlu kafatası), üs dost (mavi kale), üs düşman (kızıl kale), üs tarafsız (gri kale), doğuş (yıldızlı çember). Küçük boyda okunur, kalın kontur |
| `map_frames.png` | 2 | mini harita çerçevesi (kare, yıpranmış metal, köşe perçini, üstte ok ucu; yazısız) · büyük harita çerçevesi (16:9, eski parşömen kenarlı metal) |
| `map_legend.png` | 1 | açıklama şeridi arka planı: koyu deri ve metal, 1:3 dikey |
| `lock_pixel.png` | 2 | kilitli bölge: kalın piksel asma kilit · açılırken (dil havada, parıltı) |

### 6.9 Yönetici paneli, destek, sistem ikonları

`sys_icons.png` (48 simge, **128 px**, 6×8): **sistem** — ayarlar, posta, arkadaşlar, sohbet, harita, görevler, klan, mağaza, pass, kasa, çanta, üretim, yetenek, karakterler, sıralama, profil, destek, ev, geri, kapat, onay, kilit, bilgi, ara · **işlem** — sırala, süz, çöp, düzenle, paylaş, kopyala, artı, eksi, yukarı, aşağı, sola, sağa, yenile, indir, yükle, saat, takvim, bildirim, ses, titreşim, dil, güvenlik, FPS, ağ.

`admin_icons.png` (24, 4×6): sekmeler — oyuncular (iki silüet), dünya (küre), sunucu (raf), ekonomi (altın ve grafik), destek (zarf ve can simidi), kayıt (defter), hileler (sihirli asa) · eylemler — ışınlan (portal), yanına çek (mıknatıs), eşya ver (hediye), altın ver, elmas ver, seviye ayarla (yukarı ok), envanter (çanta), sustur (çarpılı balon), at (kapıdan ok), yasakla (çekiç), izle (göz), iyileştir (kalp), öldür (kafatası), hava (bulut), gece-gündüz (ay-güneş), zombi doğur (mezardan el), duyuru (megafon).

`support_icons.png` (8, 2×4): hata (böcek), ödeme (kart ve elmas), hesap (anahtar ve silüet), öneri (ampul), diğer (üç noktalı balon), fotoğraf ekle (makine), gönder (kâğıt uçak), bildir (kızıl bayrak).

### 6.10 Küçük ama gerekli (kalan kalite artırıcılar)

| Dosya | Adet | Ne için |
|-------|------|---------|
| `ui_damage_font.png` | 1 | hasar sayıları için piksel rakam seti **0-9, +, −, !** (**tek yazı izinli sayfa**): beyaz dolgu, kalın koyu kontur; kritik için ayrı altın set |
| `ui_emotes.png` | 12 | hızlı ifade simgeleri: selam, gülme, kızgın, ağlama, kalp, kafatası, başparmak, yardım, takip et, dikkat, teşekkür, GG kupası |

---

## 7 · EŞYA SİMGELERİ (129 eşya)

**Sorun:** büyük boyalı simgeler (yüzük, kolye, etkinlik silahı) ile 16-32 px'lik minik piksel malzemeler aynı yuvada yan yana.
**Karar:** hepsi **128 px boyalı piksel**, tek bakışta okunur siluet, kalın koyu kontur; **silahlar 3B render** (yapıldı, 17 silah simgesi atlasta).

**Simge sayfası şablonu**
```
[UI-STİL] set of {N} game item icons, {R} rows x {C} columns, each icon centered in its own cell with generous padding,
same size and same visual weight, bold readable silhouette at small sizes, thick dark outline, light from the top-left,
rarity tint only in the object itself (no frame).
Icons: {LİSTE}.
flat solid #00FF00 green background, no text, no letters, no numbers, no grid lines.
```

Gruplama (aynı sayfada tutarlılık için, **6 sayfa**):
1. **Giyim** (kask 10 + ceket 4 + pantolon 4 + bot 4 + çanta 3 + pelerin 1 = 26): 6×5
2. **Takılar** (yüzük 9 + kolye 9 = 18): 6×3 — mevcut boyalı yüzük/kolyeleri **referans ekle**, aynı kalitede kalsın
3. **İksir ve yiyecek** (tüketilebilir 10 + panzehir, bandaj, ilk yardım...): 5×2 + ek
4. **Malzemeler A** (39 malzemenin ilk yarısı): 5×4
5. **Malzemeler B**: 5×4
6. **Alet, araç parçası, sandık, para, tezgâh**: 5×3

Eşya kimlik ve adları **Ek A**'da; "Mevcut simge: YOK" yazanlar oyundan kaldırılan eşyalardır (istemde kullanma).

Silahlar (19): 3B render, **yeniden istenmiyor**. Etkinlik yüzük/kolye (12) mevcut boyalı simgeler **korunur** (kalite çıtası).

---

## 8 · SES (isteğe bağlı)

Yaratıklar bölge türleri için ortak seslerle konuşuyor. ElevenLabs Sound Effects gibi bir araçla: her biri 0,5-1,5 sn, sessizlik kırpılmış, OGG/WAV, dosya
`sfx_<tür>_<idle|attack|hurt|death>.wav`. Şablon: `{TARİF}, single short game sound effect, close mic, no music, no reverb tail`.

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

Ek: `sfx_campfire_loop` (4 sn döngü), `sfx_shop_buy` (para ve elmas şıngırtısı), `sfx_card_reveal_<0..3>` (nadirliğe göre artan), `sfx_wings_flap`, `sfx_vip_entry` (kısa fanfar), `sfx_engine_moto_loop`, `sfx_engine_car_loop`, `sfx_nitro`.

---

## 9 · TESLİM, SIRA, BÜTÇE

### 9.1 Önerilen üretim sırası (kazanç / emek)

| Sıra | Paket | Neden önce |
|------|-------|-----------|
| 1 | **Bölüm 1.3'teki 6 eksik görsel** (logo, `shop_bg`, `btn_shop`, biletler, `char_bg`) | Kod hazır, dosya yok |
| 2 | **4.2 insansı yaratıklar** (önce 8 temel + 6 bölge) | APK −16 MB, en büyük görsel sıçrama |
| 3 | **4.1 NPC'ler** | Dünya canlanır |
| 4 | **6.0-6.1 arayüz kiti** | Bütün ekranlar yenilenir |
| 5 | **6.2 arka planlar** | Ekran ekran yenileme |
| 6 | **4.3-4.5** dört ayaklılar, özel, boss | Kalan yaratıklar |
| 7 | **7 eşya simgeleri** | Çanta ve mağaza tutarlılığı |
| 8 | 6.5 yetenek, 6.6 mağaza, 6.4 plaka | |
| 9 | 4.7 kanat, 4.8 doku varyantları | VIP görünümleri |
| 10 | 5.2 eski yapılar, 6.8-6.10, 8 ses | |

Her paket bitince ayrı zip olarak gönder; ben geldikçe oyuna bağlarım (3B → hat + iskelet + animasyon; resim → atlas / kit).

### 9.2 Dosya düzeni (zip)

```
Zenvex_V16/
  3d/        npc_*.glb  z_*.glb  q_*.glb  x_*.glb  b_*.glb  v_*.glb  wing_*.glb  (+ retexture/ alt klasörü)
  ui_kit/    kit_*.png
  bg/        bg_*.png  shop_bg.png  char_bg.png  load_*.png
  icons/     skill_*.png  sys_icons.png  admin_icons.png  items_*.png ...
  world/     b3_*.png
  misc/      logo_*.png  plate_*.png  map_*.png  card_*.png  ...
```

### 9.3 Benim sorumluluğum (her paket için)

Zip'i alınca: her dosyayı bu listeyle eşleştirip **eksik ya da sorunlu olanları bildiririm**, sonra işlerim. 3B için: hat çalıştırma, iskelet, animasyon,
önizleme sayfası (hareketleri kontrol edebilesin). Arayüz için: kit'i kod bileşenlerine bağlama, **ekran görüntüsü testleri** (cihazsız), 3 ekran oranında doğrulama.

### 9.4 Kontrol listesi (üretirken)

- Yazı yok (logo ve rakam sayfası hariç) · saf yeşil nesnede yok · hiçbir parça kenardan kesilmemiş · aynı sayfanın parçaları aynı istemde
- 3B: A-pozu, kollar açık, tek parça, zemin/gölge yok, öne bakıyor, ayrı parçalar ayrı
- 9 dilimli çizimlerde orta bölge **düz** (desen yok), ortası boş

---

## EK A · Eşya listesi (kimlik, ad, nadirlik, mevcut simge)


**Silah (19)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `w_bat` | Tahta Sopa | Common | var |
| `w_baton` | Polis Copu | Rare | YOK |
| `w_glock` | Tabanca | Rare | var |
| `w_ak47` | AK-47 | Epic | var |
| `w_m4` | M4 | Epic | YOK |
| `w_sniper` | Keskin Nişancı Tüfeği | Legendary | var |
| `w_rpg` | RPG-7 | Legendary | var |
| `w_knife` | Bıçak | Rare | var |
| `w_katana` | Katana | Epic | var |
| `ev_blood_weapon` | Kan Ayı Orağı | Seasonal | var |
| `ev_titan_weapon` | Titan Kırıcı Balyoz | Seasonal | var |
| `ev_base_weapon` | Fırtına Baltası | Seasonal | var |
| `ev_scrap_weapon` | Hurdacı Testere Kılıcı | Seasonal | var |
| `ev_pack_weapon` | Sürü Lideri Pençesi | Seasonal | var |
| `ev_breath_weapon` | Son Nefes Tırpanı | Seasonal | var |
| `w_uzi` | Uzi | Epic | var |
| `w_hatchet` | El Baltası | Common | var |
| `w_handaxe` | Savaş Baltası | Rare | var |
| `w_effigy` | Lanetli Taş Tokmak | Epic | var |

**Kask (10)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `helm_basic` | Basit Kask | Common | var |
| `helm_police` | Polis Kaskı | Rare | var |
| `helm_military` | Askerî Kask | Epic | var |
| `helm_titan` | Titan Kaskı | Legendary | var |
| `ev_blood_helm` | Kan Ayı Muhafız Kaskı | Seasonal | var |
| `ev_titan_helm` | Titan Avı Muhafız Kaskı | Seasonal | var |
| `ev_base_helm` | Üs Fırtınası Muhafız Kaskı | Seasonal | var |
| `ev_scrap_helm` | Hurdacılar Muhafız Kaskı | Seasonal | var |
| `ev_pack_helm` | Sürü Lideri Muhafız Kaskı | Seasonal | var |
| `ev_breath_helm` | Son Nefes Muhafız Kaskı | Seasonal | var |

**Ceket (4)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `jacket_basic` | Basit Yelek | Common | var |
| `jacket_police` | Polis Yeleği | Rare | var |
| `jacket_military` | Askerî Ceket | Epic | var |
| `jacket_titan` | Titan Ceketi | Legendary | var |

**Pantolon (4)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `pants_basic` | Kargo Pantolon | Common | var |
| `pants_police` | Dizlikli Pantolon | Rare | var |
| `pants_military` | Askerî Pantolon | Epic | var |
| `pants_titan` | Titan Pantolonu | Legendary | var |

**Bot (4)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `boots_basic` | Basit Bot | Common | var |
| `boots_police` | Polis Botu | Rare | var |
| `boots_military` | Askerî Bot | Epic | var |
| `boots_titan` | Titan Botu | Legendary | var |

**Çanta (3)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `bag_basic` | Basit Çanta | Common | var |
| `bag_police` | Polis Çantası | Rare | var |
| `bag_military` | Askerî Çanta | Epic | var |

**Pelerin (1)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `cloak` | Tanrı Pelerini | Seasonal | var |

**Yüzük (9)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `ring_copper` | Bakır Yüzük | Common | var |
| `ring_hunter` | Avcı Yüzüğü | Rare | var |
| `ring_titan` | Titan Yüzüğü | Legendary | var |
| `ev_blood_ring` | Kan Ayı Kralı Yüzüğü | Seasonal | var |
| `ev_titan_ring` | Titan Avı Kralı Yüzüğü | Seasonal | var |
| `ev_base_ring` | Üs Fırtınası Kralı Yüzüğü | Seasonal | var |
| `ev_scrap_ring` | Hurdacılar Kralı Yüzüğü | Seasonal | var |
| `ev_pack_ring` | Sürü Lideri Kralı Yüzüğü | Seasonal | var |
| `ev_breath_ring` | Son Nefes Kralı Yüzüğü | Seasonal | var |

**Kolye (9)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `neck_bone` | Kemik Kolye | Common | var |
| `neck_amulet` | Koruyucu Muska | Rare | var |
| `neck_dark` | Karanlık Kolye | Epic | var |
| `ev_blood_neck` | Kan Ayı Şampiyonu Kolyesi | Seasonal | var |
| `ev_titan_neck` | Titan Avı Şampiyonu Kolyesi | Seasonal | var |
| `ev_base_neck` | Üs Fırtınası Şampiyonu Kolyesi | Seasonal | var |
| `ev_scrap_neck` | Hurdacılar Şampiyonu Kolyesi | Seasonal | var |
| `ev_pack_neck` | Sürü Lideri Şampiyonu Kolyesi | Seasonal | var |
| `ev_breath_neck` | Son Nefes Şampiyonu Kolyesi | Seasonal | var |

**Tüketilebilir (10)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `bandage` | Bandaj | Common | var |
| `medkit` | İlk Yardım Kiti | Rare | var |
| `food` | Konserve | Common | var |
| `energy` | Enerji İçeceği | Common | var |
| `antidote` | Panzehir | Rare | var |
| `potion_heal` | Can İksiri | Rare | var |
| `potion_speed` | Hız İksiri | Rare | var |
| `potion_power` | Güç İksiri | Epic | var |
| `frozen_fish` | Donmuş Balık | Rare | var |
| `char_card` | Karakter Kartı | Epic | var |

**Alet / anahtar (6)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `t_axe_stone` | Taş Balta | Common | var |
| `t_axe_iron` | Demir Balta | Rare | var |
| `t_pick_stone` | Taş Kazma | Common | var |
| `t_pick_iron` | Demir Kazma | Rare | var |
| `veh_moto` | Motosiklet Anahtarı | Epic | var |
| `veh_car` | Araba Anahtarı | Legendary | var |

**Mermi (oyundan çıkarıldı) (5)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `ammo_9mm` | 9 mm Mermi | Common | YOK |
| `ammo_762` | 7,62 mm Mermi | Common | YOK |
| `ammo_556` | 5,56 mm Mermi | Common | YOK |
| `ammo_308` | .308 Mermi | Rare | YOK |
| `ammo_rocket` | Roket | Epic | YOK |

**Malzeme (39)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `wood` | Odun | Common | var |
| `stone` | Taş | Common | var |
| `cloth` | Kumaş | Common | var |
| `glue` | Yapıştırıcı | Common | var |
| `resin` | Reçine | Common | var |
| `leather` | Deri | Common | var |
| `scrap` | Paslı Metal | Common | var |
| `screw` | Vida | Common | YOK |
| `powder` | Barut | Rare | YOK |
| `electronics` | Elektronik Parça | Rare | var |
| `tooth` | Zombi Dişi | Common | var |
| `toxic` | Toksik Öz | Rare | var |
| `dark_crystal` | Karanlık Kristal | Epic | var |
| `titan_shard` | Titan Parçası | Legendary | var |
| `fuel` | Benzin | Common | var |
| `tape` | Bant | Common | var |
| `copper` | Bakır Tel | Common | var |
| `nails` | Çivi | Common | var |
| `rope` | İp | Common | var |
| `battery` | Pil | Rare | var |
| `plastic` | Plastik | Common | var |
| `gear` | Dişli | Rare | var |
| `spring` | Yay | Rare | var |
| `bone` | Kemik | Common | var |
| `ice_shard` | Buz Kristali | Rare | var |
| `scorched_resin` | Yanık Reçine | Rare | var |
| `charcoal` | Kömür | Common | var |
| `scorpion_venom` | Akrep Zehri | Rare | var |
| `dried_hide` | Kurutulmuş Deri | Common | var |
| `swamp_moss` | Bataklık Yosunu | Common | var |
| `bog_iron` | Bataklık Demiri | Rare | var |
| `circuit_board` | Askerî Devre Kartı | Epic | var |
| `powder_pouch` | Barut Kesesi | Rare | var |
| `gold_watch` | Altın Saat | Epic | var |
| `rusty_key` | Paslı Anahtar | Rare | var |
| `v_wheel` | Tekerlek | Rare | var |
| `v_engine` | Motor | Epic | var |
| `v_frame_moto` | Motosiklet Gövdesi | Rare | var |
| `v_frame_car` | Araba Gövdesi | Epic | var |

**Sandık (3)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `chest_wood` | Ahşap Sandık | Common | var |
| `chest_iron` | Demir Sandık | Rare | var |
| `chest_military` | Askerî Sandık | Epic | var |

**Para (2)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `coin` | Altın | Common | var |
| `gem` | Elmas | Epic | var |

**Tezgâh (1)**

| Kimlik | Ad | Nadirlik | Mevcut simge |
|--------|----|----------|--------------|
| `workbench` | Tezgâh | Rare | var |