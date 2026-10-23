# 07 — Alınan Kararlar & Güncel Durum

## 1. Kararlar (kullanıcı onaylı)
| Konu | Karar |
|------|-------|
| Yön sayısı | **8 yön** (G, GD, D, KD, K, KB, B, GB) — her yön ayrı çizilir, ayna yok |
| Karakter oranı | **Gerçekçi** (~7,5 kafa boyu; chibi değil) |
| Karakter | **Sabit, tek karakter** ("Kaan"); karakter yaratma yok |
| Saldırı | **Tek saldırı joysticki.** Hareket joysticki gibi çevrilir; karakter o yöne dönüp yumruk atar, basılı tuttukça sol-sağ (jab-düz) seri devam eder. Başka saldırı/skill tuşu **yok** |
| Görseller | **Tamamen kodla üretilir** (yapay zeka gerekmiyor) → `tools/assetgen` |
| Geliştirme ortamı | **Sadece Android telefon**, PC yok → derleme GitHub Actions'ta, APK telefondan indirilir |
| Maliyet | Ücretsiz araçlar: GitHub Actions, açık kaynak Rust/Kotlin, OFL lisanslı font |

Bu kararlar nedeniyle önceki belgelerden değişenler:
- 01: 4 yön + ayna → **8 yön**, 48×48 → **96×80 kare** (ölüm animasyonu sığsın diye), yarı-chibi → **gerçekçi**.
- 02 §5: HUD'daki skill slotları, etkileşim tuşu, yemek/silah tuşları **kaldırıldı**. Sadece 2 joystick + çanta/görev/duraklat.
- 03 §5: Aktif "Kaçış (Dash)" yeteneği yerine pasif **"Rüzgâr Gibi"** (+%15 koşu hızı) — skill tuşu olmadığı için.
- 04: Bevy yerine **Rust çekirdek + Kotlin Canvas çizici** (aşağıda).
- 05: Promptlar artık zorunlu değil; ileride pazarlama görselleri / konsept için saklanıyor.

## 2. Mimari (uygulanan)
```
rust/zenvex_core   → oyun kuralları (dünya üretimi, oyuncu, zombi YZ, yumruk, ağaç kesme, XP/level/yetenek)
                     motordan bağımsız, 18 birim testi
rust/zenvex_jni    → libzenvex.so; Kotlin'e 12 fonksiyon (create, step, drawList, hud, events, ...)
android/           → Kotlin: Compose ile tüm menüler + SurfaceView ile oyun çizimi, joystickler, HUD
tools/assetgen     → Python: 3D SDF modelden 8 yönlü pixel sprite, ağaç/kaya, zemin, arayüz, ikonlar
.github/workflows  → her push'ta test + APK + "latest" sürüm sayfası
```
**Neden Bevy değil?** Telefon-only geliştirmede derlemenin bulutta hızlı ve sorunsuz olması kritik. Rust'ın oyun
mantığını, Kotlin'in Android'in donanım hızlandırmalı Canvas'ıyla çizmesi: küçük APK, hızlı derleme, kolay
hata ayıklama. İleride gerekirse çizim Rust'a (wgpu) taşınabilir; oyun kuralları zaten Rust'ta.

**Kare akışı (60 FPS):** Kotlin joystick → `step(dt, girdi)` → Rust simülasyon → `drawList(görüş alanı)`
(y'ye göre sıralı, görünmeyenler elenmiş) → Kotlin çizer → `events()` (hasar sayısı, XP, seviye, sarsıntı, titreşim).

## 3. Görsel üretim hattı (`tools/assetgen`)
- `sdf.py` — işaretli mesafe alanı (SDF) ışın yürütücü: 3/4 kamera (35°), 3 tonlu cel gölgelendirme,
  renkli dış çizgi, derinlik çizgileri, göz gibi tek piksel detaylar.
- `character.py` — insan iskeleti (kalça, diz, omuz, dirsek, omurga, kafa) + Kaan ve zombi modelleri + pozlar.
- `nature.py` — meşe, çam, huş, ölü ağaç, çalılar, kayalar, kütük, devrik kütük.
- `flat.py` — dikişsiz zemin karoları, toprak lekeleri, ot/çiçek, gölge, joystick, panel/tuş, 27 pixel ikon.
- `sfx.py` — ses efektlerini sentezler (`assets/sfx/*.wav`).
- `build_assets.py` — hepsini üretip `android/app/src/main/assets/sprites/` + `manifest.json` yazar (~2 dk).
- Önizlemeler: `art/preview/*.png`.

| Varlık | Adet |
|--------|------|
| Kaan animasyonları | idle 4 · walk 8 · run 8 · punch 8 (jab+düz) · hurt 2 · death 6 → **36 kare × 8 yön = 288 kare** |
| Zombi animasyonları | idle 4 · walk 8 · attack 6 · hurt 2 · death 6 → **26 × 8 = 208 kare** |
| Lobi karakteri (2× detay) | 4 kare × 8 yön |
| Doğa | 3 meşe, 3 çam, 2 huş, 2 ölü ağaç, 5 çalı, 6 kaya, kütük, devrik kütük |
| Zemin & dekor | 16 karo, 3 toprak lekesi, 5 ot, 6 çiçek |
| Arayüz | 2 joystick seti, 4 tuş stili, panel, slot, 27 ikon, uygulama ikonu |

## 4. Oyunda şu an ÇALIŞANLAR
- Açılış → Giriş (Misafir) → **Lobi** (döndürülebilir 8 yönlü karakter, tüm tuşlar yerinde) → Yükleme → Oyun
- 128×128 karolu düz orman: ~1000 ağaç, çalılar, kayalar, binlerce ot/çiçek, patikalar, doğuş açıklığı
- 8 yönlü hareket, yürüme/koşma (joystick gücüne göre), dayanıklılık
- **Saldırı joysticki**: çevir → o yöne yumruk serisi; yürürken de vurulabilir (yavaşlar)
- Zombiler: dolaşma, fark etme, kovalama, pençe saldırısı, geri itilme, ölüm + saydamlaşma; kenarlara doğru güçlenir
- Ağaç kesme: ağaç sallanır, can barı, yıkılınca odun + XP, kütük kalır, 90 sn sonra yeniden büyür
- XP, Level 1–50, yetenek puanı, 12 yetenek (4 dal) — lobide yükseltilir, etkileri oyunda aktif
- HUD: portre, can/dayanıklılık/XP barları, odun & öldürme sayısı, mini harita (zombiler), düşük can uyarısı,
  hasar sayıları, seviye atlama yazısı, ekran sarsıntısı, titreşim, parçacıklar
- Rüzgârda sallanan ağaç/ot, arkasına geçince saydamlaşan ağaçlar, gölgeler
- Duraklat menüsü, oyun içi ayarlar, çanta, ölüm ekranı (yeniden doğ / lobiye dön)
- Ayarlar (hepsi kalıcı): grafik hazır ayarları, FPS 30–120, kamera uzaklığı, bitki yoğunluğu, rüzgâr, gölge,
  parçacık, ağaç saydamlığı, sarsıntı, hasar sayısı, pil tasarrufu, FPS göstergesi; hareket/saldırı
  hassasiyeti ve ölü bölgeleri, koşma eşiği, joystick boyutu/saydamlığı, dinamik/sabit joystick, titreşim,
  sol el modu
- Otomatik kayıt (60 sn, lobiye dönüş, uygulama arka plana alınınca)
- **Ses efektleri** (kodla sentezlendi, `tools/assetgen/sfx.py`): yumruk savurma, isabet, ağaç vuruşu, ağaç
  devrilmesi, 3 zombi homurtusu (uzaklığa göre kısılır), zombi saldırısı/ölümü, hasar alma, seviye atlama,
  ölüm, tuş sesi — ana ses / efekt / arayüz ses ayarlarına bağlı

## 5a. V3 güncellemesi (paket adı: com.zenvex.online)
- **Karakterler artık gerçekçi:** MakeHuman/MPFB2 (CC0) insan modelleri — gerçek deri dokusu, göz, kaş, saç,
  dokulu kıyafet ve ayakkabılar. Bizim prosedürel animasyon iskeletimize bağlanıyor (13 animasyon × 8 yön).
  Zombiler: yeşil/gri deri, kan lekeli yırtık kıyafet, parlayan gözler; Titan dev, zırhlı, boynuzlu.
- **Silahlar elde gerçek model** (Glock, AK-47, M4, keskin nişancı, RPG, sopa, cop) — her silahlı animasyon ayrı render.
- **Eşya simgeleri** 50 eşya için gerçek 3D model (mermi kutuları, ilk yardım, benzin bidonu, devre kartı, kask/yelek/bot/çanta setleri...).
- **Efektler:** namlu alevi, patlama ateş topu + şok dalgası, kıvılcım, kan, Rady kalkan kubbesi, Elif şifa sütunu,
  Kaan öfke aurası, ışınlanma girdabı, zehir bulutu, seviye atlama ışığı, araç egzozu ve toz.
- **Tam online (Firebase, ev sahibi modeli):** aynı odadaki oyuncular aynı zombileri görür; bir oyuncunun telefonu
  zombileri çalıştırır (en küçük kimlikli oyuncu; ayrılırsa sıradaki devralır). Vuruşlar ev sahibine gider, öldürme
  ödülü vurana verilir, zombi hasarı hedef oyuncuya iletilir. **Takas** (yakındaki oyuncuya Takas tuşu → iki taraf da
  onaylayınca eşya + altın değişimi) ve **yolculuk** (2/4 kişilik arabası olan oyuncunun aracına yolcu olarak bin).
- Paket adı değişti → telefondaki eski "Zenvex Online"u silip yenisini kur.

## 5. V2 durumu (bu sürüm: 0.2.x)
**Görseller (hepsi kodla, Blender Cycles ile 3D render):** 4 kahraman (13 animasyon × 8 yön, silah için ayrı kol
katmanı + ek noktaları), 7 zombi türü + zombi köpek (5 animasyon × 8 yön), Titan boss, asker/tüccar NPC'leri,
gerçek modelli 4 araç (bisiklet, motosiklet, spor araba, SUV; jant, far, ayna, plaka) × 8 yön, 15 detaylı bina
(tuğla/kiremit/sıva/beton prosedürel dokular, pencere çerçevesi, balkon, tabela yazıları, çatıda klima/güneş
paneli/çanak), enkazlar, 37 doğa nesnesi (yaprak parçacıklı ağaçlar), 50 eşya simgesi, ekipman parçaları
(kask, ceket, bot, çanta, pelerin × 4 kademe), 7 silah. 9 dikişsiz 512 px zemin dokusu (spektral gürültü + kabartma).
Paketleme: kırpma, renk/keskinlik düzenleme, aktörlere ince kontur, WebP atlas + manifest (`tools/assetgen/pack.py`).

**Oyun (Rust `zenvex_core`):** 360×360 karo dünya (kamp, orman, derin orman, bataklık, şehir duvarı + kapı,
şehir güvenli bölgesi, banliyö, merkez, sanayi, Titan arenası), seviye 10 şehir kapısı, güvenli bölgede keskin
nişancılar, yerden eşya toplama, 50 eşya, envanter + çanta kapasitesi, ekipman (silah/kask/ceket/bot/çanta/pelerin),
18 üretim tarifi (tezgâh), tüccar (al/sat), altın ekonomisi, 7 silah (sopa, cop, Glock, AK-47, M4, keskin nişancı,
RPG), mermi/şarjör/dolum, 8 düşman türü (Normal, Koşucu, Köpek sürüsü, Toksik: zehir birikintisi + ölünce bulut,
Karanlık: ışınlanma + can emme + gece 2×, Tank: yer sarsma, Gizemli Maskeli: görünmezlik + sahte kopya, TİTAN:
şok dalgası, çağırma, öfke), araçlar (yakıt, çarpışma, zombilere çarpma, patlama), 4 karakter + yetenekleri
(Kaan öfke, Nova dron + hedef seçimi, Rady kalkan 50 +20/sv, Elif şifa %5/5sn → %20/3sn), karakter seviyesi 10,
12 yetenek, 8 ana görev + günlük görevler, Sezon 1 Pass (30 kademe, ücretsiz/premium, Tanrı Pelerini),
kozmetikler (kostüm, kaplama, çerçeve, etiket), gece/gündüz, geliştirici hileleri.

**Arayüz (Kotlin):** animasyonlu lobi (gece gökyüzü, ay, bulut, paralaks şehir/orman, sis, korlar; sürükleyerek
döndürülen 3D kahraman, ◀ ▶ karakter seçimi), profil kartı, altın/elmas, görev/etkinlik/sunucu kartları, Sezon
kartı, alt dok, OYNA; Karakterler, Yetenekler, Envanter (ekipman yuvaları + ayrıntı), Üretim, Tüccar (al/sat/elmas),
Görevler, Sezon Pass (49,99 TL / 99,99 TL — ödeme henüz bağlı değil), Kozmetik, Profil, Sohbet, Ayarlar.
Oyun içi: isim etiketleri ("Lv X Ad", türe göre renk), boss barı, radar, gece ışık haritası (fener, lamba, kamp
ateşi, namlu alevi, patlama), parçacıklar, hasar sayıları, afişler, harita, ölüm ekranı (kamp/şehir).
**Ses:** 37 efekt + lobi ve oyun müziği (`tools/assetgen/sfx2.py`).
**Çevrimiçi:** Supabase (Zenvex ID + anahtar hesabı, sunucu başına bulut kaydı, Realtime ile oyuncular/zombiler/sohbet/takas, posta, klan, sıralama) — ayrıntı: `docs/09_SUPABASE.md`.
Ayar yoksa oyun çevrimdışı çalışır.

**Geliştirici menüsü:** lobide alttaki sürüm yazısına 7 kez dokun (altın, seviye, eşya, set, ölümsüzlük, zombi/boss
çağırma, ışınlanma, pass, araç...).

## 6. Henüz yok / sonraki adımlar
Google Play ödeme (pass, elmas), Google ile giriş, hile korumalı kendi Rust sunucumuz (şu anki ev sahibi modeli istemciye güvenir).

## 7. APK'yı telefona kurma
1. GitHub'da repo sayfası → **Releases** → **"Zenvex Online — son derleme"**.
2. `ZenvexOnline-0.2.X.apk` dosyasına dokun → indir → aç → "Bu kaynaktan yüklemeye izin ver" → Yükle.
3. Aynı imzayla derlendiği için eskisinin üstüne kurulur. (V2 yeni kayıt dosyası kullanır; V1 ilerlemesi taşınmaz.)

## 8. Derleme
CI sadece derler (test yok, isteğin üzerine): her push'ta APK üretip Releases'a yükler.
Görselleri yeniden üretmek için (bilgisayarda): `pip install bpy` → `python tools/blender/render_all.py`,
`render_build.py`, `render_vehicles.py` → `python tools/assetgen/ground_tex.py` → `python tools/assetgen/pack.py`.

## V3 — Lobi ve çevrimiçi yenilendi (Ekim 2026)
- **Firebase kaldırıldı, Supabase geldi.** Hesap = Zenvex ID + 12 harfli anahtar (Supabase Auth gerekmez).
- **Şimdilik tek sunucu:** "Sunucu #1". Kod çoklu sunucuya hazır (`SERVERS` listesi; Rust'ta Hardcore kuralları var).
- **Lobi:** kayan piksel dünya haritası; solda büyük karakter vitrini (sürükle-döndür, oklarla karakter değiştir),
  sağda oyuncu kartı (klan, isim, seviye, altın, XP, sunucu durumu) ve OYNA; sağ üstte hesap kartı, Sıralama, Klanlar,
  Haberler, Ayarlar. Ayarlar → Hesap: ID, anahtar, isim, başka hesaba giriş.
- **Yeni uygulama simgesi:** 3D renderlardan (Kaan + AK47, arkada zombiler, kızıl ay).
- **Oyun içi:** harita + bölgeler arası seyahat, görevler, sezon, yetenekler (sol üst); posta, oyuncular, klan, menü (sağ üst);
  hızlı eşya slotları + çanta + sohbet çubuğu (alt orta); XP şeridi ve koordinat (alt). Envanter/üretim/tüccar/kozmetik oyunun içinde.
- **Kahraman sprite'ı (V5):** 3D→piksel hattı yerine kullanıcının verdiği piksel karakterin kendisi kullanılır
  (`art/v5/warrior_ref.jpg` → ızgara 2.34 px tespiti, arka plan temizliği, palet → `art/v5/warrior_base.png`, 94×258).
  Animasyonlar `tools/pixel/cutout_rig.py` ile: kol ayrı katman (omuzdan döner), iki bacak ayrı katman (kalçadan salınır),
  üst gövde eğilme/nefes, düşme/takla tüm figür döndürmesi; döndürme Scale2x³ + en yakın örnekleme (RotSprite benzeri).
  Yönler: ön (güney), aynası (doğu/güneydoğu), maske/atkı kapüşonla örtülmüş sırt (kuzey ve aynası).
  Üret: `python3 tools/pixel/cutout_rig.py art/v5/warrior_base.png /home/user/zx_pix/hero` sonra `pack.py`.

## V10 — Kullanıcı görselleri (3 paket, Ekim 2026)
Ham görseller `art/v10u/`, ayıklanmış parçalar `art/v10/` (`tools/pixel/v10_objects.py`, efektler `tools/pixel/fx_sheet.py`).
- **Atlas yaması:** ham render dizinleri (zx_render, zx_pix) depoda yok; `tools/assetgen/v10_pack.py` mevcut atlas sayfalarından
  ilgili piksel gruplarını (kahraman, NPC, dekor, eşya, efekt) geri çıkarıp yeni parçalarla yeniden paketler, diğer sayfalara dokunmaz.
  Aynı tanımlar `tools/assetgen/v10_assets.py` içinde; `pack.py` tam paketlemede de bunları kullanır.
- **Kahraman:** elle çizilmiş vurulma, yere düşme, kalkma ve ölüm pozları (`tools/pixel/hero_poses.py`); yeni etkileşim pozları:
  toplama, odun kesme (yumrukla ağaca vurunca balta), kazma (taşa vurunca), sandık açma, içme (eşya kullanınca), sevinç (seviye atlayınca),
  işaret etme (NPC ile konuşunca). Pozlar yalnız dururken oynar; hareket ya da saldırı keser (`Game::play_pose`).
- **Askerler:** yan görünüm devriye, nişan, ateş, kulede çömelme, şarjör, dürbün, vurulma, düşüş, ölü; namlu ucu görselden.
- **Dünya:** 34 bölge dekoru ve 3 kamp çadırı. Eski nesnenin yerine bölgesine ve konumuna göre seçilir (`world::v10_variant`);
  rastgele sayı dizisi değişmediği için ağaç/bina yerleşimi aynı kalır.
- **Eşyalar:** 12 bölge eşyasının simgesi. **Efektler:** kan, kan gölü (zemin), kıvılcım, namlu ateşi, alev, duman, yer sarsıntısı,
  patlama, kritik vuruş, şifa, buz (Donmuş Liman'da vuruş), zehir.
- **Arayüz** (`assets/ui`, `tools/pixel/v10_ui.py`): kurukafa köşeli pencere çerçevesi, kızıl/kapalı tuş, sekme, nadirliğe göre eşya yuvası,
  kapat tuşu; sıralama madalyaları, ilk 10 rozeti, kanatlı/boynuzlu unvan şeritleri (oyunda isim etiketinde de), kategori simgeleri;
  9 klan seviyesi amblemi; 6 etkinlik afişi; elmas sandığı, kasa odası, nadirlik ışınları, hediye kutuları; Sezon Bileti arka planı,
  kademe kartları, ilerleme şeridi, düğüm, mühür, kilit ve görev simgeleri.
- **Uygulama simgesi:** katanalı kapüşonlu savaşçı ve köpeği (`tools/pixel/v10_icon.py`).

## V11 — Oynanış ve sunucu güncellemesi (Ekim 2026)
- **Ağ:** zombiler ve diğer oyuncular 5 Hz yayın arasında hızla ileri tahmin edilir (en çok 0,35 sn), kare hızından bağımsız
  yumuşak takip; ping göstergesi (Realtime kalp atışı). Bölgeler ve pencereler: dokunma pencerenin altındaki oyun tuşlarına geçmez,
  pencere açıkken oyun tuşları başka pencere açmaz, diyaloglar yalnız X/geri ile kapanır.
- **Üretim:** sekmeler (Şifa, İksir, Silah Ve Alet, Ekipman, Takı, Malzeme, Araç, Üs), aynı eşyanın tarifleri tek satır,
  malzemelerin nereden çıktığı (`data::item_source`), tuşlar her zaman görünür.
- **Ağaç/kaya:** 500 can; vuruş gücü çantadaki en iyi baltadan/kazmadan (yumruk 25, taş 50, demir 100).
- **Araçlar:** dünyada sahipsiz araç yok. Parçalar (tekerlek, motor, şasi) üretilir, Tamirci Viktor'a verilir; motosiklet 10, araba 30 dk
  sonra anahtar postayla gelir (`vehicle_orders`, nabızda tembel teslim). Anahtar kullanılınca araç yanına gelir; parçalanan aracı
  Viktor uzaktan çekip onarır. İsteğe bağlı 8 yön görselleri: `x8/moto/0-7`, `x8/car/0-7`.
- **Etkinlik:** 1. yüzük, 2. kolye, 3. etkinlik silahı (6 yeni silah; kasklar emekli). İlk 3 ayrıca 30 gün takılabilen etkinlik unvanı
  alır (`event_titles`). HUD afişi canlandırıldı.
- **Sıralama:** tablolar 15 dakikada bir hesaplanır (`board_cache` + `board_meta`, nabızdan tembel yenileme; DELETE yok, kuşak
  zaman damgasıyla). Unvanlar önbellekten okunur. Ekran açıkken yenileme anında kendiliğinden yüklenir.
- **Profil:** yalnız NPC portreleri (Aldric eklendi; kaldırılanlar Tüccar görünür, varsayılan 1). Oyun içinden isim değişir; ad
  sonraki konum paketiyle diğer oyunculara gider.
- **Dünya:** harita 900 karo. Ana dünya (720) karantina duvarıyla çevrili; şehir surunun batı ucu ve şehrin doğusu kapatıldı;
  hızlı hareket alt adımlarla çözülür (`World::resolve_move`). Doğu ve güney şerit **Cephe Hattı**: 50 üsün hepsi orada (kimlik,
  seviye, sahiplik aynı), güneydoğuda Cephe Karargâhı (pazar, Aldric). Girişi yalnız Aldric, Sv 10'dan. Sis 150×3 kelime; V8-V10
  kayıtları hücre hücre taşınır.
- **Öğretici:** Sv 1-5 rehbere taş balta, ağaç kesme ve Aldric eklendi (eski kayıtların sırası `migrate_guide_index`).
  Sv 10: şehirde Komutan Diana → Titan Arenası → yerel Eğitim Üssü (6 zayıf asker, klansız 10 sn ele geçirme, bomba, şehirde uyanış).
  Eğitim üssü `base_defs()`'in son elemanı (`TRAIN_BASE`), sunucuya gitmez.
- **Görsel istemleri:** `docs/15_PROMPTLAR_V11.md`.

## V11.1 — Oyuncu geri bildirimi düzeltmeleri (Ekim 2026)
- **Donma:** oyun ekranı kapanırken 1,5 sn gecikmeli oturum kapatma, o arada açılan yeni oturumu kapatıyordu (dünya durur,
  karakter yürüyemez, takla ortasında kalır). Artık yalnız kendi oturumunu kapatır (`Session.endIf`); oturum oyundayken
  kapanırsa lobiye dönülür.
- **HUD:** FPS/ping sağ altta tek satır; altın posta/menü tuşlarının altında (takla tuşuyla çakışmaz); Sezon Bileti en başta;
  sol taraftaki görev takipçisi ve NPC çağrı balonları kaldırıldı; ekran dışı görev hedefi oyuncunun çevresinde küçük ok + mesafe.
- **Canavarlar:** can çubuğu başın üstünde (görsel ölçeği k unutulmuştu), seviye her zaman, hasar alınca "kalan/azami" can.
- **Simgeler:** `tools/pixel/icons_v11.py` piksel simgeler (sikke, elmas, görev, yetenek ağacı, elmas sandığı, çanta, takas,
  klan, seviye, asker); `Icon()` varsa onları çizer. Boş ekipman yuvaları koyu siluet.
- **Unvanlar:** baş üstünde plakasız; ilk 3 metal geçişli, kayan ışık, taç ve yıldız parıltısı. Ayarlar → "Oyuncu Unvanlarını Gizle".
- **Çerçeveler:** yalnız Sv 10 / 25 / 50, Sezon #1 (bilet), Kanlı Ay (etkinlikte ilk 10, geçmiş etkinlikler dahil). Kodla çizilir,
  animasyonlu; sohbette de görünür. Seviye elması: 2-10 → 25, 11-20 → 50, 21-30 → 100, 31-40 → 150, 41-50 → 250 (`zx_level_gems`, grants).
- **Oyuncuya dokununca:** Fısıltı, Takas Teklif Et, Arkadaş Ekle, Profili Gör, Şikâyet Et (`zx_report`).
- **Diğer:** tezgâh yakındayken halka + çekiç rozeti ve dokununca üretim; kamp ateşi piksel kıvılcım; köpek tek görünüm;
  NPC portreleri yüz merkezli; sıralamada "nasıl girilir" notları; karakollarda yerin adı.
- **Test:** `GameShotsTest` gerçek oyun ekranını (Rust çekirdeği bilgisayar için derlenir) HUD ile birlikte çizer.

## V11.2 — Zombi canı ve çizilmiş çerçeveler (Ekim 2026)

- **Zombi canı:** her zombinin başında seviyenin yanında kalan/azami can yazar (`Sv 4 · 63/104`); can çubuğu yine
  vurulunca ya da boss'ta çıkar. Çekirdek can oranını her zaman yollar, azami can kaydın özel alanında (`4 × azami + bayraklar`).
- **Çizilmiş çerçeveler:** `assets/ui/frame_<id>.png` varsa çerçeve gövdesi o görselden, hareketli efektler ve rozet yazısı kodla
  üstüne çizilir; yoksa kodla çizilen çerçeve. Görsel kutunun 1.25 katı (taşan süs payı), iç pencere resim alanına hizalı.
  Prompt'lar `16_CERCEVE_PROMPTLARI.md`, içe aktarma `tools/pixel/frame_import.py` (piksel sanat ya da `hd`).

## V12 — Ayrı bölgeler ve Aldric'in Haritası (Ekim 2026)

- **Bölgeler ayrı harita** (`rust/zenvex_core/src/areas.rs`): Başlangıç Ormanı, Yıkık Şehir, Yanık Orman, Çöl Otoyolu,
  Kara Bataklık, Askeri Bölge, Donmuş Liman, Cephe Hattı, Kişisel Üssüm. Sınırlar düz dikdörtgen (V8 bölge sınırlarındaki
  gürültü kaldırıldı). Sınır görünmez duvar (`areas::clamp`: oyuncu, takla, savrulma, araç, köpek, zombi); ötesi oyun
  ekranında katmanlı sisle kararır; harita ve mini harita yalnız bulunulan bölgeyi gösterir; harita seyahati bölge içinde.
- **Büyücü:** her bölgenin giriş karakolunda Aldric. "Işınlan" → Aldric'in Haritası (`WizardMap.kt`): bölgeye dokun →
  önerilen/gereken seviye, yaratıklar (seviye aralığıyla), çıkan eşyalar, ücret, Işınlan (`wizard_tp`, yalnız büyücünün
  yanında). Ücret 15 altın; orman ve kişisel üs ücretsiz.
- **Ölünce** öldüğün bölgenin giriş karakolunda doğulur (kişisel üs seçiliyse orada).
- **Görev oku:** hedef başka bölgedeyse `story_json` "via" ile bu bölgenin büyücüsünü gösterir.
- **Cephe Hattı'nda zombi doğmaz** (üslerin çevresi yalnız üs askerleri).
- Komutan Diana'nın sohbet konuları genel kalıbın arkasında kalıp hiç çıkmıyordu; düzeltildi.
- Görseller: `17_BOLGE_HARITASI_PROMPTLARI.md` (`wizard_map.png`, `region_<id>.png`).

## V12.1 — 8 bölge, bölgeye özel eşyalar, kurulabilir tezgâh, gezgin Titan (Ekim 2026)

- **Bölgeler (8):** Başlangıç Ormanı, Yıkık Şehir, Yanık Orman, Çöl Otoyolu, Kara Bataklık, Donmuş Liman, Cephe Hattı,
  Kişisel Üssüm. Askeri Bölge kalktı (alanı Yanık Orman'a, askerî dekor Cephe Hattı'na). İleride her bölge ayrı harita
  olarak yeniden çizilecek (`18_HARITA_PROMPTLARI.md`).
- **Eşyalar:** her bölgeden yalnız kendi malzemeleri çıkar (`data::region_items`); temel malzemeler (odun, taş, kumaş,
  deri, hurda, kemik) her yerde. Kaldırılan ara malzemeler (vida, çivi, yay, bakır, plastik, yapıştırıcı, bant, diş, barut)
  `RETIRE_INTO` ile eski kayıtlarda karşılığına döner (eşyalar sırayla kaydedildiği için listeden silinmedi, emekliye ayrıldı).
  Tarifler baştan yazıldı: her eşyanın tek tarifi var; el tarifleri temel + orman malzemesi, tezgâh tarifleri bölge malzemesi ister.
- **Tezgâh eşyası** (`benches.rs`): elle üretilir, kullanınca kurulur; kişisel üste kalıcı (en çok 3, geri alınabilir),
  başka yerde 10 dakika. **El Üretimi** (çanta/HUD) ve **Tezgâh** (tezgâha dokununca) ayrı ekranlar; tezgâh tarifleri
  el ekranında görünmez.
- **Titan** her 15 dakikada (sunucu saatiyle, herkeste aynı) şehir/yanık orman/çöl/bataklık/liman'dan birinde uyanır;
  duyurulur, haritada ve büyücü haritasında görünür; o dilimde öldürülürse bir sonrakine kadar gelmez.
- **Aldric'in Haritası 8 ayrı ada** (4×2, süzülen adalar, ışık iplikleri, yıldızlı boşluk): tek haritanın bölünmüş hali
  görünmüyor. Görseller `island_<kimlik>.png` + `wizard_bg.png` (docs/18 §9); yoksa ada üstünde bölgenin harita kesiti.
