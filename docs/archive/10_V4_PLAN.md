# V4 Planı — Piksel Sanata Dönüş, Sadeleştirme, Tek Kahraman

> Kaynak: kullanıcı geri bildirimi (Ekim 2026). Bu belge V4'ün ne olacağını, hangi sırayla yapılacağını ve
> kimin neyi hazırlayacağını anlatır. Görseller için yapay zekâ prompt'ları ayrı: `11_AI_GORSEL_PROMPTLARI.md`.

## 0. Kısa özet (kararlar)

| Konu | Eski (V3) | Yeni (V4) |
|---|---|---|
| Görsel | Blender 3D → sprite (uzun render, 60 MB APK, model hataları) | **Yüksek detaylı piksel sanat** (yapay zekâ + bizim temizlik hattı). APK hedefi < 40 MB |
| Karakter | 4 kahraman | **1 kahraman: Savaşçı** (siyah kapüşonlu, yumruk dövüşçüsü), 10 karakter seviyesi |
| Ekipman | Kask/ceket/bot/çanta setleri | **Setler kalkıyor.** Üretilen silahlar + meşale + iksirler. Savunma skill ağacından |
| Gelişim | Basit yetenek puanı | **Skill ağacı**: her seviyede 1 puan, 50 seviye, yol seçerek ilerleme |
| Yardımcı | Drone | **Köpek** (tüccardan alınır; hasar / can / can yenileme, her biri 10 seviye) |
| Işık | Ekran karartma | **Gerçek ışık kaynakları**: meşale, kamp ateşi, sokak lambası, araba farları, namlu ateşi, aura |
| Lobi | Oyunla iç içe | **Ayrı ve sade**: Karakter + OYNA + Profil + Ayarlar. Gerisi oyunda |
| Giriş | Misafir / ID+anahtar | **Hikâye sineması → Giriş ekranı (Google ile bağlan / Misafir)** |
| Dil | TR (+EN yer tutucu) | **Yalnızca Türkçe** |
| Sosyal | Takas tuşu, sohbet listesi | Oyuncuya dokun → **profil kartı** (takas, grup, posta, arkadaş); **gruplar**; **konuşma balonu** |

## 1. Araştırma notları

- **Yapay zekâ ile piksel karakter**: En büyük sorun kareler arası tutarlılık. Önerilen yol: önce tek bir
  "ana referans" (güneye bakan, tam boy, düz arka plan) üret, sonra döndürme/animasyonu **bu referanstan** al;
  çıktı ızgarası ve şeffaf/düz arka plan baştan sabitlenmeli; sonunda **palet kilidi + temizlik** şart.
  ([Wayline](https://www.wayline.io/blog/ai-game-development-asset-creation-workflow),
  [Sorceress](https://sorceress.games/blog/use-an-ai-sprite-generator-game-ready-sheet),
  [Ziva piksel öğreticisi](https://ziva.sh/blogs/pixel-art-tutorial))
- **PixelLab**: metinden ya da referanstan karakter üretir, **8 yönü** çıkarır (G, GD, D, KD, K, KB, B, GB) ve
  yürüme/koşma/saldırı animasyonlarını yönler arası tutarlı yapar. Tuval 16–128 px. Öneri: güneye bakan
  referansla başla, birkaç dönüş üret, en iyisini elle düzelt, kalanını onu kilitleyerek yeniden üret.
  ([PixelLab](https://pixellab.ai/), [Rotate belgesi](https://pixellab.ai/docs/tools/rotate)) → **Ana aracımız.**
- **Piksel sanatta dinamik ışık**: sprite'a otomatik **normal haritası** (parlaklıktan yükseklik → Sobel) üretip
  ışık kaynaklarına göre aydınlatma; tek çizimle iki doku yükü sorunu var, gölgelendiricide birleştirilebilir.
  ([Wayline normal map](https://www.wayline.io/normal-map-generator),
  [Defold tartışması](https://forum.defold.com/t/normal-map-lighting-for-2d-pixel-art-sprites/70967),
  [Eurographics 2025 makalesi](https://diglib.eg.org/items/7d0ad13f-1371-4eff-979e-2e372e36f65b/full))
- **Mobil HUD**: sık basılan her şey başparmak bölgesinde, bilgi (can, harita) köşelerde; tek elle ulaşılamayan
  yere tuş koyma; sol el desteği. ([Gamedeveloper](https://gamedeveloper.com/design/how-to-design-for-thumbs-in-the-era-of-huge-screens),
  [Parachute Design](https://parachutedesign.ca/blog/thumb-zone-ux/))

## 2. Sanat yönü

**Hedef görüntü**: kullanıcının gönderdiği örnekler — koyu, doygun gece renkleri, kızıl ay, sıcak pencere/ateş ışığı;
karakterler ~3 piksel kalınlığında kontur, yumuşak (dithering'li) gölgelendirme, kumaşta kıvrım detayı.

| Öğe | Yerel (piksel) boyut | Ekranda | Not |
|---|---|---|---|
| Zemin karosu | 32×32 | 3× tam sayı ölçek | dikişsiz |
| Kahraman / zombi karesi | 96×96 (karakter ~80 px boy) | 3× | 8 yön: G, GD, D, KD, K üretilir; B, GB, KB **ayna** |
| Titan | 192×192 | 3× | |
| Köpek | 64×64 | 3× | |
| Eşya simgesi | 32×32 | arayüzde 2–4× | |
| Araba | 128×128 (yön başına) | 3× | hasar evreleri: sağlam / ezik / dumanlı / yanık |

- **Kamera**: üstten 3/4 (yukarıdan ~45°), Zombix gibi. Tüm görseller bu açıyla istenir.
- **Işık**: sprite'lar **düz, gölgesiz** ışıkla üretilir (ışığı oyun verecek). Zemin gölgesi oyunda çizilir.
- **Palet**: 48 renklik ortak "Zenvex Gece" paleti; her görsel bu palete kilitlenir (bizim araç).
- **Kıyafet sallanması**: koşu/yürüme animasyonunda pelerin ve kapüşon ucu 2–3 kare gecikmeli hareket eder
  (prompt'ta istenir; eksik kalırsa ikincil animasyonu biz ekleriz: pelerin katmanı ayrı çizilip gecikmeli salınır).

### Animasyon listesi (kahraman)

| Animasyon | Kare | Not |
|---|---|---|
| idle | 6 | nefes, pelerin hafif sallanır |
| walk | 8 | |
| run | 8 | pelerin arkaya uçuşur |
| roll (takla) | 6 | bakılan yöne yuvarlanma |
| punch_1 / punch_2 / punch_3 | 4 / 4 / 6 | kombo; 3. vuruş güçlü |
| hurt | 2 | |
| knockdown + getup | 5 + 5 | Titan dalgası, patlama |
| death | 7 | |
| silah tutuş/saldırı: knife, machete, bat, pistol, shotgun, rifle | her biri idle 4 + attack 4–6 | **her silahın kendi tutuşu ve vuruşu** |
| torch (meşale tutma) | yürüme/idle üstüne el katmanı | meşale ayrı katman, alevi oyunda çizilir |

Kahraman başına ~5 üretilen yön × ~25 animasyon. PixelLab bunu referanstan üretir; biz temizleyip paketleriz.

## 3. Görsel üretim hattı (telefondan)

1. Sen `11_AI_GORSEL_PROMPTLARI.md`'deki prompt'u PixelLab'e (ya da ChatGPT/Gemini görsel) yazarsın.
2. Beğendiğin görseli **bu sohbete eklersin** (ya da GitHub'da `art/inbox/` klasörüne yüklersin).
3. Ben: arka planı silerim → 96×96 ızgaraya oturturum → palete kilitlerim → 1 px kontur düzeltirim → ayak noktasını
   bulurum → yönleri/aynaları çıkarırım → **normal haritası** üretirim → atlasa paketlerim → oyuna bağlarım.
4. Bir görsel kötü çıkarsa sana **hangi prompt değişikliğiyle** yeniden üretmen gerektiğini söylerim.

Araç: `tools/pixel/` (yeni) — `clean.py` (arka plan, ızgara, palet, kontur), `sheet.py` (sprite sheet kesme),
`normals.py`, `pack.py` (mevcut paketleyicinin piksel sürümü, en yakın komşu ölçekleme).

## 4. Oyun sistemleri

### 4.1 Savaşçı (karakter seviyeleri 1–10)

| Sv | Özellik |
|---|---|
| 1 | Yumruk (temel hasar 20) + **Takla**: bakılan yöne 3 karo yuvarlanır, bekleme 5 sn |
| 2 | Yumruk hasarı +%10 |
| 3 | Hasardan kaçınma şansı %5 |
| 4 | Hareket hızı +%10 |
| 5 | Takladan sonra 1 sn hasar almama |
| 6 | Can +50 |
| 7 | Yumruk hasarı +%15 |
| 8 | Takla bekleme süresi −%25 |
| 9 | Öldürünce canın %5'i yenilenir |
| 10 | **Karanlık Aura**: parlayan aura, yakındaki düşmanlara saniyede 20 hasar |

Karakter XP'si oyuncu XP'sinden ayrı, öldürmelerle dolar (mevcut `char_xp` altyapısı).

### 4.2 Skill ağacı (oyuncu seviyesi 1–50 → 49 puan)

- Herkes için **aynı** ağaç, ~120 düğüm. Merkezden 4 dal: **Yakın Dövüş**, **Ateşli Silah**, **Dayanıklılık**, **Çeviklik**.
- Bir düğüm ancak **açık bir komşusu** varsa alınır → yol seçerek ilerlersin (49 puanla ağacın ~%40'ı açılır, seçim önemli).
- Küçük düğümler (tekrarlı): Kılıç/yakın hasar +5 (temel kılıç 50), Can +10, Alınan hasar −%1, Hasar yansıtma %1,
  Kaçınma %1, Mermi hasarı +%5, Patlama koruması %10, Yeniden doldurma hızı, Dayanıklılık, Takla mesafesi…
- Her dalın 5., 10., 15. derinliğinde **anahtar düğüm** (ör. "Kan Emici: yumruklar %2 can çalar",
  "Keskin Göz: ilk mermi kritik", "Kaya: 3 sn kıpırdamayınca −%20 hasar", "Gölge Adım: takla 2 kez kullanılır").
- Sıfırlama: altınla (tüccar).
- Arayüz: kaydırılıp yakınlaştırılan ağaç; alınabilir düğümler parlar, yol çizgileri yanar.

### 4.3 Silahlar ve üretim (setlerin yerine)

- Kask/ceket/bot/çanta **kalkar**; çanta kapasitesi seviyeyle artar.
- Silahlar **üretilir**: Muşta, Bıçak, Pala, Beyzbol sopası, Kılıç (temel hasar 50), Tabanca, Pompalı, Tüfek.
  Her biri 3 kalite (Paslı / Sağlam / Usta) → tarif aynı, malzeme farklı.
- Meşale: odun + bez + reçine. 3 dk yanar; yanarken çevresini aydınlatır, zombiler ışığa daha çok gelir (risk/ödül).

### 4.4 İksirler

| İksir | Etki | Bekleme |
|---|---|---|
| Can iksiri | 10 sn boyunca her saniye +5 can | 30 sn |
| Hız iksiri | 30 sn +%25 hız | 60 sn |
| (öneri) Güç iksiri | 20 sn +%20 hasar | 60 sn |
| (öneri) Panzehir | zehri temizler, 30 sn bağışıklık | 45 sn |

Kaynak: zombilerden düşme (%3–8), evlerdeki sandıklar, özel görevler, tüccar (pahalı).

### 4.5 Köpek

- Tüccardan alınır (500 altın). Seni takip eder, yakındaki zombiye saldırır, ölürse 30 sn sonra döner.
- Üç gelişim, her biri **1–10 seviye**, altınla: **Hasar**, **Can**, **Can yenileme**. Maliyet seviyeyle artar.

### 4.6 Işık

- Gece ortam ışığı çok düşük (ay ışığı mavi). Işık kaynakları: meşale (titreşen, turuncu), kamp ateşi,
  sokak lambası (şehir), **araba farları (koni)** ve stop lambaları, namlu ateşi, patlama, karanlık aura, pencereler.
- Uygulama: mevcut ışık haritasına koni ışık + titreşim; sonra sprite normal haritalarıyla yön duyarlı aydınlatma.
- Karakter artık kendi kendine parlamaz.

### 4.7 Dövüş hissi

- Titan yere vurunca **genişleyen şok dalgası**: değerse geri savrulma + yere düşme (1,2 sn, kalkma animasyonu).
  Takla anında hasar almama varsa dalgadan kaçılır.
- Vuruşta isabet duraklaması (hit-stop 40 ms), sarsıntı, toz/kan efekti.
- **Araba hasarı**: can %66 → ezik, %33 → duman, %10 → alev, 0 → patlama + yanık enkaz. Farlar kırılabilir.

### 4.8 Sosyal

- **Oyuncuya dokun** (dünyada, sıralamada, sohbette) → **profil kartı**: seviye, klan, öldürme, oynama süresi,
  kuşandığı silah, rozetler + tuşlar: Takas, Gruba davet, Posta, Arkadaş ekle.
- **Grup** (en fazla 4): ortak XP (+%10 bonus), grup sohbeti, haritada ve can barında takım arkadaşları.
- **Konuşma balonu**: yazdığın mesaj 5 sn kafanın üstünde görünür.
- **Sohbet**: klavye açılınca yazı alanı klavyenin üstüne kayar, oyun ekranı küçülmez.

### 4.9 Hikâye sineması (ilk açılış, atlanabilir, ~60 sn, altyazılı)

1. Gece, oda. Adam (kahraman, **kapüşonsuz**) bilgisayarda oyun oynuyor. *"Her şey sıradan bir gecede başladı…"*
2. Dışarıdan çığlıklar. Balkona çıkıyor: insanlar kaçıyor, zombiler, alev.
3. Uçaklar, bombalar, mermi izleri. *"Ordu şehri kurtarmaya çalıştı."*
4. Binasına roket iner → beyaz ekran, sessizlik.
5. Enkazda uyanır; yerde **siyah bir kapüşon**. Giyer.
6. Yüksek tepeye çıkar, şehrin yanışını izler (örnekteki kapüşonlu siluet). *"Artık hayatta kalan tek kural var…"*
7. Kızıl ay, logo: **ZENVEX ONLINE** → giriş ekranı (Google ile bağlan / Misafir).

Uygulama: her sahne 1–2 yapay zekâ görseli + bizim paralaks katmanları, parçacık (ateş, kül, yağmur),
kamera kayması, ekran sarsıntısı, ses efektleri ve altyazı.

### 4.10 Giriş ve hesap

- **Google ile bağlan**: Supabase Auth Google sağlayıcısı + Android Credential Manager. Gerekenler: Google Cloud'da
  OAuth istemci kimliği (Android, imza SHA-1'imiz) ve web istemci kimliği; Supabase'de Google'ı açmak. Adımları ben yazacağım,
  sen telefondan Google Cloud Console'da yapacaksın. Mevcut Zenvex ID hesabı Google'a bağlanabilir.
- **Misafir**: şimdiki gibi.

### 4.11 Geliştirici (hile) paneli eklemeleri

- **Diğer oyunculara posta**: isimle oyuncu seç → altın / eşya / XP ekle → gönder. Sunucuda `accounts.is_admin`
  bayrağı (yalnızca senin hesabın) olan kişi çağırabilir; APK'ya gizli anahtar konmaz.
- Sonsuz mermi, beklemesiz yetenek/takla, zombilere görünmezlik, duvardan geçme, zaman hızı (×0.25–×4),
  çarpışma kutularını göster, FPS/ağ grafiği, oyuncunun yanına ışınlan, köpek seviyesi, skill puanı.

## 5. Arayüz ve kullanıcı deneyimi kuralları

1. **Yerleşim ızgarası**: 8 dp. Ekran 5 bölge: sol üst (can/portre), üst orta (bölge adı), sağ üst (mini harita +
   en fazla 3 tuş), sol alt (hareket), sağ alt (saldırı + takla + 2 yetenek). Alt orta: 4 hızlı slot + sohbet.
2. **Hiçbir tuş başka tuşun ya da joystick'in alanına giremez**: düzen, ekran boyutuna göre hesaplanır; dar ekranda
   ikincil tuşlar bir "⋯" menüsüne katlanır. Otomatik çakışma testi yazılacak (her ekran oranı için).
3. **İsim etiketleri**: sıradan zombiler yalnızca can barı (hasar aldıysa). İsim + seviye sadece seçkin/boss
   zombilerde ve oyuncularda. Üst üste binen etiketler dikey kaydırılır, en fazla 8 etiket.
4. **Panel dili**: tek piksel yazı tipi, yapay zekâ ile üretilmiş 9-dilim piksel çerçeveler, tutarlı renkler.
5. Her ekranda **tek ana eylem** (lobi: OYNA). Menü derinliği en fazla 2.
6. Dokunma alanı en az 44 dp; geri tuşu her yerde tutarlı.

## 6. Kaldırılanlar / ertelenenler

Kalkıyor: 3 kahraman (Nova, Rady, Elif), zırh setleri, drone, elmas, sezon kartı (mağaza hazır olunca geri gelir),
kozmetik ekranı (sonra). Kalıyor: hesap, posta, klan, sıralama, takas, araçlar, tüccar, görevler, Titan.

## 7. Uygulama sırası

| Faz | İçerik | Görsel gerekir mi? |
|---|---|---|
| **0** | Arayüz düzeltmeleri: çakışan tuşlar/etiketler, sohbet + klavye, konuşma balonu, sadeleştirme, tek dil | Hayır |
| **1** | Oynanış çekirdeği: Savaşçı seviyeleri + takla, skill ağacı, iksirler, köpek, üretilen silahlar, setlerin kaldırılması, hile paneli eklemeleri | Hayır (geçici görsellerle) |
| **2** | Piksel hattı (`tools/pixel`) + kahraman + köpek + 6 zombi + Titan | **Evet** — önce kahraman ana referansı |
| **3** | Işık sistemi (meşale, farlar, lambalar), araba hasarı, Titan dalgası, vuruş hissi | Kısmen (efektler) |
| **4** | Sosyal: profil kartı, grup, dokunarak takas, Google ile giriş | Hayır (Google için senin kurulumun) |
| **5** | Hikâye sineması + giriş ekranı + zemin/bina/eşya piksel setleri | **Evet** |

Her faz sonunda tek APK. **Senden şimdilik tek istek**: `11_AI_GORSEL_PROMPTLARI.md` → **H1 (kahraman ana referansı)**
prompt'unu PixelLab'de üret, en iyi 2–3 sonucu buraya at. Onunla hattı kurup diğer her şeyi aynı stile bağlarım.
