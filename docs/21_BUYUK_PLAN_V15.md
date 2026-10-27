# Zenvex Online — V15 Büyük Plan

Bu belge son mesajındaki **47 isteğin tamamını** tek tek ele alır: kökte ne bozuk, nasıl çözülecek, hangi dosyalara
dokunulacak, ne zaman "bitti" sayılacak. Görsel gereken her şeyin istemi ayrı belgede:
**`docs/archive/22_DEVASA_PROMPT_V15.md` (yerine `docs/22_DEVASA_PROMPT_V16.md`)**. Görselleri üretip zip olarak verdiğinde zip'i inceleyip bu planı aşağıdaki
sırayla adım adım işleyeceğim.

İşaretler: ⚙ yalnız kod · 🎨 zip'ten görsel bekler · 🗄 sunucu/veritabanı · ❓ senin kararını bekler

---

## 0. Senin kararını bekleyen 4 konu

Bunlar dışında her şeyi ben karara bağladım (aşağıda gerekçeleriyle).

| # | Konu | Önerim | Neden |
|---|------|--------|-------|
| K1 ❓ | **Oyun sunucusu** | Kendi Rust oyun sunucumuzu yazıyorum. Şimdilik **Hetzner CX22** (2 çekirdek, 4 GB, ayda ~5 €) ya da **Oracle Cloud Always Free** (ücretsiz, 4 çekirdek ARM). 100 kişiye geçince aynı sunucu programı daha büyük makineye (CPX31, ~15 €/ay) taşınır. | Supabase ücretsiz plan saniyede **100 mesaj** ile sınırlı; 10 oyuncu saniyede **~550 mesaj** üretiyor (bkz. 1.1). Supabase Pro bile 500/sn: 10 kişiyi zor kaldırır, 100 kişiyi hiç. |
| K2 ❓ | **Gerçek para ile satış** (pass, VIP, kartlar, elmas) | Google Play Faturalandırma + sunucuda makbuz doğrulama. | Android'de dijital ürünler yalnız Google Play ile satılabilir. Bunun için Play Console hesabı, ödeme profili ve uygulamanın en az "Dahili test" kanalına yüklenmesi gerekiyor. Hesap yoksa açman gerek. |
| K3 ❓ | **10 dil** | Türkçe, English, Español, Português (BR), Русский, Deutsch, Français, Italiano, Bahasa Indonesia, العربية | En büyük mobil oyun pazarları; hepsi tek bir piksel yazı tipi ailesiyle (Latin + Kiril + Arap) çizilebilir. Çince, Japonca, Korece APK'ya ~4-8 MB yazı tipi ekler; istersen birini Italiano yerine koyarız. Arapça sağdan sola yazılır, arayüzü buna göre aynalayacağım. |
| K4 ❓ | **Yeni 3 karakter oynanabilir mi?** | Evet, oynanabilir. Her biri kendi görünüşü ve 8 yön animasyonuyla. | Her karakter için ana karakterdeki gibi ~150 animasyon görseli gerekir (22 numaralı belge, bölüm 10). İstemezsen karakterler "pasif güç kartı" olur, tek görsel yeter. |

---

## 1. Kök neden analizleri (en ağır 6 sorun)

### 1.1 Sunucu 10 kişide çöküyor 🗄
- Çok oyunculu dünya Supabase Realtime'ın tek kanalında (`zx-<sunucu>`). Her oyuncu konumunu **saniyede 5 kez**
  yayınlıyor, ev sahibi oyuncu zombi listesini (80 zombi × 12 sayı, JSON) yine saniyede 5 kez yayınlıyor.
- Supabase her yayını "1 gönderim + alıcı sayısı" kadar mesaj sayıyor. 10 oyuncuda:
  `(10 × 5 konum + 5 zombi) × 10 = ~550 mesaj/sn`. Ücretsiz plan sınırı **100/sn**, aylık kota **2 milyon** mesaj
  (bu hızda ~1 saatte biter). Sınır aşılınca Supabase bağlantıları kısıyor, oyun "ölmüş" gibi oluyor.
- Ek sorun: "ev sahibi" bir oyuncunun telefonu. Onun interneti yavaşsa herkesin zombisi takılıyor, o çıkınca devir
  teslim sırasında zombiler sıçrıyor; bölgeler ayrı olduğu halde herkes aynı kanalda.

**Çözüm — kendi oyun sunucumuz (`rust/zenvex_server`):**
1. Rust (tokio + WebSocket), oyunun çekirdeği `zenvex_core` ile aynı kodu kullanır: zombileri **sunucu** yönetir,
   hile yapmak zorlaşır, ev sahibi kavramı kalkar.
2. **Oda = (sunucu, bölge).** Oyuncu yalnız bulunduğu bölgenin odasındadır; ilgi alanı (ekranın 1,5 katı) dışındaki
   oyuncu ve zombiler gönderilmez.
3. İkili (binary) ve sıkıştırılmış paket: konum 10 bayt, zombi 8 bayt. Saniyede 10 güncelleme; duran oyuncu
   için gönderim yok. 10 oyuncuda sunucu çıkışı ~40 KB/sn; 100 oyuncu tek çekirdeğe sığar.
4. Kimlik: istemci Supabase oturum anahtarını (`sid` + HMAC) gönderir, sunucu Supabase'den doğrular. Kayıt,
   hesap, klan, posta yine Supabase'de (değişmiyor).
5. Supabase Realtime yalnız **genel ve klan sohbeti** için kalır (saniyede birkaç mesaj, kota sorunu yok).
6. Sunucu kurulumu: tek komutla (`deploy/install.sh`) systemd servisi + otomatik HTTPS (Caddy). GitHub Actions
   her sürümde sunucu programını da derler.
7. **Sunucu gelene kadar geçici önlem** (ilk iş, kod hazır olunca hemen): bölge başına ayrı kanal, konum yayını
   2/sn ve yalnız hareket edince, zombi listesi yalnız değişenler. Bu 10 kişiyi kurtarmaz ama 4-6 kişiyi rahatlatır.

**Bitti sayılır:** 10 sahte istemci (bot) testinde 30 dk boyunca ping < 120 ms, kopma yok, sunucu işlemcisi < %15.
Yük testi aracı (`tools/loadtest`) depoda kalır; 100 bot ile de denenir.

### 1.2 Mini harita "aynı ağaç, kırmızı nokta" 🎨⚙
- Mini harita (`lib.rs::minimap`) tüm dünyayı karo karo renkle boyuyor; her ağaç aynı yeşil daire, her bina aynı
  kahverengi kutu, her zombi aynı kırmızı nokta. Bulunduğun bölgenin gerçek görüntüsüyle ilgisi yok.

**Çözüm:**
1. Bölgeye girilince bölgenin tamamı **oyunun kendi çizim kodu ve kendi görselleriyle** (zemin dokuları, ağaçlar,
   binalar, süsler) küçük ölçekte ekran dışı bir resme çizilir ve saklanır (bölge başına bir kez, ~150 ms).
   Mini harita ve büyük harita bu resmi kullanır: haritada gördüğün şey gerçekten bölgenin küçültülmüş hali olur.
2. Canlı işaretler küçük **piksel simgelerle**: zombi türüne göre kafa simgesi (tehlike rengi çerçeveli), boss,
   oyuncular (ok), klan üyeleri, NPC'ler (kendi simgeleri), tüccar, büyücü, sandık, görev hedefi. 🎨
3. Mini harita çerçevesi ve pusula yeni piksel çerçeveyle. 🎨
4. Harita ekranındaki **Seyahat / Işınlanma** kaldırılır; ışınlanma yalnız Büyücü'de.
5. Sol alttaki açıklama şeridi (Titan kırmızı kutu, seyahat sarı kutu…) yeni simgelerle yeniden yapılır. 🎨

### 1.3 Harita sistemi: bölgeler hâlâ tek büyük dünyadan oyulmuş ⚙🎨
- Bugün 8 bölge, 720×720 karelik tek dünyanın dikdörtgen parçaları (cephe "L" şeklinde). Harita ekranı da bu büyük
  dünyanın kalanını karartıp gösteriyor; eski şehir kapısı, karayolu, karakollar yerinde duruyor.

**Çözüm — her bölge ayrı, kare bir dünya:**
1. `world.rs` bölge başına ayrı harita üretir: `World::build(area)` → kendi boyutu, kendi zemini, kendi yapıları.
   Bellekte yalnız bulunulan bölge durur (bellek ~%70 azalır, yükleme hızlanır).
2. Boyutlar (kare): Orman 160, Şehir 192, Yanık Orman 144, Çöl 160, Kara Bataklık 144, Liman 160, Cephe 192,
   Kişisel Üs 48 karo. Kenarlar bölgenin kendi sınır süsleriyle kapanır (dağ, kayalık, su, karantina duvarı).
3. Eski yollar, şehir kapısı, karayolu, bölgeler arası geçitler ve **eski karakollar** tamamen silinir.
4. Her bölgenin baştan elle tasarlanmış yerleşim planı olur (bölüm 3, İP-3): giriş meydanı (büyücü + doğuş
   noktası), 3-4 ilgi noktası, NPC'lerin kendi köşeleri, zorluk halkaları (girişten uzaklaştıkça zorlaşır),
   boss alanı, kaynak alanları (ağaç, taş, demir).
5. Kayıt dosyası geçişi: eski kayıttaki konum yeni bölgenin doğuş noktasına taşınır; envanter, sandıklar ve kişisel
   üs korunur.

### 1.4 Oyunu arka plana alınca bağlantı kopuyor, kampta başlıyorsun ⚙🗄
- Arka planda Android ağı ve iş parçacıklarını uyutuyor; 90 sn kalp atışı gelmeyince oturum düşüyor. Dönünce yeni
  oturum açılıyor, konum kayıtta tutulmadığı için doğuş noktasından başlıyorsun.

**Çözüm:** Konum ve bölge kayda yazılır (her kayıtta). Arka plana geçerken kayıt + "duraklat" işareti; 10 dakikaya
kadar oturum sunucuda açık kalır. Dönüşte sessizce yeniden bağlanır, kaldığın yerden devam edersin (ekranda yalnız
"Yeniden bağlanıyor…" şeridi). 10 dakikayı geçerse lobiye dönersin ama yine kaldığın yerde doğarsın.

### 1.5 Bazı yaratıklar dönünce büyüyüp küçülüyor ⚙
- Yapay zekâ sayfalarında her yönün çizim boyu farklı. İçe aktarma her sayfayı kendi medyanına göre ölçekliyor,
  yönler arası fark %10-25'e çıkabiliyor.

**Çözüm:** Tür başına tek ölçek (güney "durma" boyu), yönler gövde yüksekliğine göre (kafa-ayak) eşitlenir;
geniş saldırı karelerinde ölçek değil yalnız tuval büyür. Otomatik test: her tür/yön/animasyon için boy sapması
%5'i geçerse test kırılır. Kahraman, köpekler, NPC'ler ve araçlar da aynı testten geçer.

### 1.6 Sohbet balonu okunmuyor ⚙
- Balon yazısı, oyundaki diğer yazılar gibi kalın siyah kontur ile çiziliyor; açık balon üstünde koyu yazı + koyu
  kontur birbirine karışıyor.

**Çözüm:** Balon içi yazı konturuz, daha büyük, satır kaydırmalı (en çok 3 satır), koyu yarı saydam balon + beyaz
yazı (okunurluk testi: her zemin dokusunda kontrast ≥ 7:1). Balon 6 sn kalır, uzun mesajda süre uzar.

---

## 2. İsteklerin tamamı → iş paketleri

Her istek bir kez yer alır. "R" numaraları mesajındaki sıraya göre.

### İP-1 · Sunucu, bağlantı, kayıt 🗄⚙
| R | İstek | Çözüm |
|---|-------|-------|
| R27 | 10 kişi rahat oynasın, ileride 100 | 1.1 — kendi oyun sunucusu |
| R31 | Arka plana alınca kopma, kampta başlama | 1.4 |

### İP-2 · Harita ve mini harita 🎨⚙
| R | İstek | Çözüm |
|---|-------|-------|
| R5 | Bölgeler ayrı dünyalar, kare, karartılmış büyük harita değil | 1.3 |
| R44 | Eski yollar/kapılar olmayacak, her bölge baştan özel | 1.3 + İP-3 |
| R37 | Haritada eski karakollar | Silinir (1.3) |
| R30a | Mini harita bölgenin gerçek küçük hali | 1.2 |
| R30b | Haritada ışınlanma kaldırılsın | 1.2 |
| R30c | Sol alt açıklama şeridi AAA | 1.2 + 🎨 harita simgeleri |
| R33 | Büyücüde gidilemeyen bölge: siyah daire yerine karartma + piksel kilit | Ada resmi gri/koyu tonlanır, üstünde piksel kilit simgesi (🎨) ve "Sv X" rozeti; dokununca kilit sallanır |

### İP-3 · Bölgelerin baştan tasarımı ve NPC yerleşimi 🎨⚙
| R | İstek | Çözüm |
|---|-------|-------|
| R10 | NPC'ler hep doğuş noktasında yığılı | Her NPC'nin bölgede kendi köşesi (aşağıdaki tablo); doğuş meydanında yalnız büyücü + görev panosu |
| R9 | Rehber Nova kaldırılsın | Nova, kodu, diyalogları, görev işaretçisi silinir; ilk 5 seviye öğretici kendi kendine ilerler |
| R35 | Binalar hâlâ eski 3D | 22. belge bölüm 3-4: bütün eski 3D yapılar pikselle değişir |
| R18 | Kalan 3D'leri piksele çevir | Aynı; 3D render araçları (`tools/blender`) depodan kalkar |

NPC yerleşim kuralı (her bölge için ayrı plan, 1.3 maddesindeki yerleşim çiziminde):
- **Doğuş meydanı:** Büyücü Aldric, görev panosu, kamp ateşi.
- **Sahra hastanesi köşesi:** Doktor (yalnız Orman ve Şehir).
- **Garaj / tamirhane:** Tamirci Viktor (Şehir, Çöl).
- **Atış alanı:** Eğitmen (Orman).
- **Tüccar:** Marco kamyonetiyle bölgede gezer, 3 durakta 5'er dk bekler (haritada simgesi).
- **Görev veren Silas:** Şehrin güvenli bölgesinde kendi barakasında.
- **Komutan Diana:** Cephe karargâhı.
- Bölgeye özel NPC'ler o bölgenin ilgi noktalarında.

### İP-4 · Oyun içi arayüz düzeltmeleri ⚙🎨
| R | İstek | Çözüm |
|---|-------|-------|
| R1 | Sohbette birine basınca profil açılmıyor | Sohbet satırındaki isim/avatar dokununca oyuncu menüsü (profil, fısılda, arkadaş ekle, takas, bildir) |
| R2 | Zombi adı üstte; seviye ve can çubuğun içinde; seviyeye arka plan | Yeni zombi plakası: üstte ad (tehlike rengi), altında çubuk: solda seviye rozeti (🎨 kalkan şeklinde, seviye farkına göre gri/yeşil/sarı/turuncu/kırmızı), içinde "1.240 / 2.000" |
| R4 | Köpek vs üstümüze gelince isim/klan yukarı kayıyor | Çakışan isimleri yukarı kaydırma kaldırılır. İsim her zaman sahibinin başında; çakışmada uzaktakinin ismi saydamlaşır. Kendi ismin hiç kaymaz ve hep görünür |
| R12 | 6-7 haneli para haritaya taşıyor, elmas görünmüyor | Üst şerit yeniden: altın ve elmas ayrı kutucuklarda, 1,2 B / 12,5 M kısaltması, kutucuk sabit genişlik; ikisinin yanında **+** → Mağaza |
| R16 | Sohbet balonu okunmuyor | 1.6 |
| R32 | Takas ve takla tuşları üst üste / orantısız | Sağ alt düzen yeniden: saldırı çubuğu merkezli yay üstünde takla, silah değiştir, yetenek; dokunma alanları ≥ 48 dp, hiçbiri çakışmaz; 3 ekran oranında (16:9, 20:9, 4:3 tablet) ekran görüntüsü testi |
| R17 | Giyilen eşyayı basılı tutup çantaya sürükleyememe | Ekipman yuvasında uzun basış → sürükle → çanta ızgarasına bırak = çıkar; çantadan yuvaya sürükle = giy. Bırakma yeri yeşil/kırmızı vurgulanır |
| R40 | Unvanların üstünde yalnız "+" işaretleri | Unvan efektleri sprite animasyonu (🎨 22. belge bölüm 7): 1. altın ışıltı + taç, 2. gümüş parıltı, 3. bronz kıvılcım, 4-10 sade |
| R7 | Kanatlı isim plakaları çok absürt | Kanatlı kurdele kaldırılır; her sıralama için ayrı, ince, okunaklı plaka (🎨 bölüm 6) |

### İP-5 · Sohbet, sosyal, destek ⚙🗄
| R | İstek | Çözüm |
|---|-------|-------|
| R36 | Sohbette "Yakın" sekmesi kaldırılsın | Sekme, kanalı ve kayıt kodu silinir (Genel, Klan, Özel, Sistem kalır) |
| R41b | Ayarlara Destek bölümü | Destek formu: konu (Hata, Ödeme, Hesap, Öneri, Diğer), açıklama, en çok **10 fotoğraf × 10 MB**; aynı cihaz **15 dakikada 1** (sunucu kontrolü). Fotoğraflar Supabase Storage'da gizli kovada. Yönetici panelinde "Destek" sekmesi: talepler, fotoğraflar, gönderenin profili, yanıt (yanıt posta olarak gider) |
| R41c | Oyuncu bildirme: açıklama kutusu; bildiren ve bildirilen profilini görme | Bildir penceresinde neden seçimi + kısa açıklama kutusu (200 karakter). Yönetici panelinde her bildirimde iki profil kartı yan yana (seviye, klan, cihaz, geçmiş bildirimler, son sohbet satırları) ve "uyar / sustur / at / yasakla" tuşları |
| R43 | Bütün sohbet metinleri yeni güncellemeye göre elden geçsin | Bütün NPC konuşmaları (`talk.rs`), öğretici, görev ve sistem mesajları yeni dünyaya göre yeniden yazılır (eski bölge, kapı, Nova, karakol geçen her cümle). Çeviriden **önce** yapılır |

### İP-6 · Ekonomi, mağaza, VIP, paketler, kartlar, pass 🗄⚙🎨❓K2
| R | İstek | Çözüm |
|---|-------|-------|
| R13a | Paranın yanında + → mağaza | İP-4 R12 |
| R13b | Seviye paketleri (1-10, 10-25, 25-50), süreli | Bölüm 4.2 |
| R13c | VIP V0-V10+ | Bölüm 4.3 |
| R13d | Haftalık/aylık kart | Bölüm 4.4 |
| R30d | Elmas yalnız tüccardan alınıyor → tüccardaki elmas kısmı kalksın, mağaza butonu | Tüccardan elmas satışı kalkar; HUD'a **Mağaza** tuşu (animasyonlu sandık simgesi) |
| R30e | Mağaza çok kaliteli, animasyonlu, etkileyici | Mağaza ekranı: üstte dönen öne çıkan teklif afişi, sekmeler (Öne Çıkan, Elmas, Paketler, VIP, Kartlar, Pass), kart dokunuşunda parlama, satın almada sandık açılma ve ödül yağmuru animasyonu; görseller 🎨 bölüm 9 |
| R26 | Pass gerçek para: VIP Bilet 99,99 TL, Premium 199,99 TL | Bölüm 4.5 |
| R24 | Pass kademesi eşiğe göre zorlaşsın; ödüller mantıklı | Bölüm 4.5 |
| R25 | Karakter sistemi: ayrı sekme, 4 silüet, günlük görev, kart, 2.500 / 20.000 açma, 25 kartta seviye, max'ta elmas | Bölüm 4.6 |
| R6 | Oyunda olmayan eşyalar kalksın, sahiplerine para | Kullanılmayan her eşya kimliği listelenir (tarif, düşme, mağaza, görev, kullanım yok). Kayıt geçişi: o eşyalar silinir, değerinin **2 katı** altın verilir, oyuncuya "Eski eşyaların altına çevrildi: X adet → Y altın" postası gelir. Sunucudaki emanet takaslar ve klan depoları da taranır |

### İP-7 · Üretim, tüccar, çalışma tezgâhı, klan deposu ⚙
| R | İstek | Çözüm |
|---|-------|-------|
| R8 | Doğuştaki tezgâh elde üretilenleri gösteriyor | Tezgâha dokununca **tezgâh tarifleri** açılır (eldeki üretim ayrı). Hata: tezgâh kimliği yanlış sekmeye düşüyor; düzeltilip testle korunur |
| R8b | Tezgâh kurma tuşu yok; "Kur" → inşa modu, dokunulan yere | Envanterde tezgâh seçince **Kur** tuşu. İnşa modunda yarı saydam önizleme parmağı izler; uygun yer yeşil, engelli/uzak yer kırmızı; ✓ ile kurulur, ✕ ile iptal. Menzil 6 karo |
| R19 | Çoklu üretim 5×/10×/25×/50× | Üretim kartında, yapılabilen miktara göre görünen tuşlar: 1×, 5× (≥5), 10× (≥10), 25× (≥25), 50× (≥50). Üretilen sayı sıralamaya tam yazılır (5× → 5) |
| R30f | Tüccara satarken kaç tane | Satışta miktar penceresi: kaydırıcı + −/+ + "Tümü"; toplam fiyat canlı |
| R38 | Tüccar listesini görmek için kaydırma; olasılıklar tuşu; tek ekran | Tüccar ekranı tek ekrana sığan 4×3 ızgara; "Olasılıklar" tuşu → liste (eşya, nadirlik, çıkma şansı) |
| R3 | Klan deposundan gönderme: gönder tuşu, miktar (max 50) | Depo eşyasına dokun → üye seç → miktar (1-50, kaydırıcı) → **Gönder**. Sunucu tarafında da 50 sınırı ve yetki kontrolü |

### İP-8 · Oynanış kuralları ⚙
| R | İstek | Çözüm |
|---|-------|-------|
| R34 | Ölünce "Kampta Doğ / Şehirde Doğ" | Tek tuş: **Bölgede Yeniden Doğ** (bulunduğun bölgenin doğuş meydanı, 5 sn geri sayım) + **Lobiye Dön** |
| R39 | Yüksek seviye, düşük zombiden XP almasın | Kural: zombi seviyesi < oyuncu seviyesi − 10 ise XP 0 (Sv 20 → Sv 10 altı yok). −6…−10 arası %50. Ganimet etkilenmez. İlk seferde ve sonra en çok dakikada bir: "XP kazanmak için seviyene uygun zombi avla (Sv 10+)." Zombi plakasında seviye rozeti gri görünür (R2) |
| R23a | Görevlerde yenilenmeye kalan süre | Günlük ve haftalık başlıklarında canlı geri sayım (ss:dd:sn) |

### İP-9 · Sıralama, profil, lobi ⚙
| R | İstek | Çözüm |
|---|-------|-------|
| R21a | İlk 10'da "Efsanevi" yazısı | Kaldırılır; yerine "Mevcut sıralaman: #x" (ilk 100 dışındaysan "#100+") |
| R21b | Soldaki başlıklar kaydırmasız sığsın | Sol sekme listesi ekran yüksekliğine göre ölçeklenir (9 başlık tek ekranda), yazı küçülür, simge kalır |
| R22 | Profilde kazanılan unvanlar alta kayıyor | Unvan bölümü sabit yükseklikte kendi içinde kayan alan; yerleşim 3 ekran oranında testle korunur |
| R28 | Lobide/yüklemede kırmızı kapüşonlu, katanalı, arkasında ay olan adam | Bu, eski oyun amblemi (`ui/emblem.png`). Kaldırılır; yerine Zenvex Online yazı logosu (🎨 bölüm 1) |
| R46 | Eskiyi hatırlatan yazılar, eski kodlar, eski özellikler | Lobideki "Yenilikler" listesindeki eski sürüm notları, eski ipuçları, eski kayıt geçişlerinin artık gereksiz olanları, eski bölge/karakol/şehir kapısı kodu, Nova, bisiklet/SUV yer tutucuları, eski prompt belgeleri temizlenir |

### İP-10 · Geliştirici paneli ⚙🎨
| R | İstek | Çözüm |
|---|-------|-------|
| R45 | Hile panelinin görselleri + daha fazla kontrol, güzel ve animasyonlu | Yeni panel, sekmeli: **Oyuncular** (canlı liste, ara, profil, ışınlan/yanına çek, eşya/altın/elmas ver, seviye ayarla, envanter gör, sustur, at, yasakla, izle), **Dünya** (titan çağır, hava, gece/gündüz, zombi doğur, etkinlik başlat/bitir), **Sunucu** (oda başına oyuncu, ping, işlemci; çok dilli duyuru; çok dilli bakım mesajı; bakım aç/kapa), **Ekonomi** (günlük altın/elmas girişi, mağaza satışları, en zengin 20), **Destek** (İP-5), **Kayıt** (yönetici işlem günlüğü). Görseller 🎨 bölüm 12 |

### İP-11 · Görsel kalite, efektler, animasyonlar 🎨
| R | İstek | Çözüm |
|---|-------|-------|
| R20 | Kamp ateşi, aura vs. kodla çizilmiş, gerçekçi değil | Kamp ateşi, meşale, yanan varil, max seviye aurası, iksir auraları, nesne parıltıları sprite animasyonu olur (🎨 bölüm 5); kod yalnız ışık halkasını çizer |
| R45b | Bazı karakterler dönünce büyüyüp küçülüyor | 1.5 — tür başına tek ölçek + otomatik boy testi |
| R14 | Yetenek ağacındaki her yeteneğin görseli | 🎨 bölüm 8 (42 simge + düğüm çerçeveleri) |
| R47 | Verdiğin devasa zip'i oyuna orantılı, eksiksiz, en yüksek kalitede işle | Zip gelince: önce envanter çıkarılır (hangi görsel hangi isteme karşılık), eksik/hatalı olanlar listelenip sana bildirilir, sonra bölüm 5'teki sırayla işlenir. Her paket oyun içi ekran görüntüsüyle doğrulanır |

### İP-12 · Dil, depo, araçlar, boyut ⚙🗄❓K3
| R | İstek | Çözüm |
|---|-------|-------|
| R41a | 10 dil, cihaz diliyle başlasın, ayarlardan değişsin; hileler ve bakım mesajları da çok dilli | Bölüm 4.7 |
| R42 | Eşya ve genel açıklamalar anlaşılır | Bütün eşya açıklamaları baştan yazılır: ne işe yarar, nereden düşer, neyle üretilir, kaç değerinde (3 satırı geçmez) |
| R15 | Uygulamanın dosya ve klasör düzeni temiz, anlaşılır | Bölüm 4.8 |
| R29 | Depo profesyonel, çok dilli README | Bölüm 4.8 |
| R11 | Derleme araçları ve Actions en son sürüm, sıfır uyarı | Bölüm 4.9 |
| R48 | APK boyutu | Bölüm 4.10 |

---

## 3. Bölge yerleşim planları (İP-3)

Her bölge kare; giriş meydanı güneyde ortada; kuzeye gittikçe zorlaşır. Karo = 64 px.

| Bölge | Boyut | Giriş meydanı | İlgi noktaları | Boss alanı | Kenar |
|-------|-------|---------------|----------------|------------|-------|
| Başlangıç Ormanı | 160 | Kamp: büyücü, kamp ateşi, görev panosu | Sahra hastanesi (Doktor), köpek barınağı, atış alanı (Eğitmen), göl ve iskele, oduncu kulübesi, mantar korusu | Kuzeydoğu bataklık açıklığı | Sık çam duvarı + kayalık |
| Yıkık Şehir | 192 | Güvenli meydan (keskin nişancılar): büyücü, Silas'ın barakası | Polis karakolu, market sokağı, apartman blokları, Viktor'un garajı, metro girişi, hastane | Sanayi bölgesindeki fabrika avlusu | Yıkık bina sırası + barikat |
| Yanık Orman | 144 | Yanmış itfaiye kulesi | Kömürleşmiş köy, kül gölü, devrilmiş elektrik hattı | Közlü krater | Duman perdesi + kızgın kaya |
| Çöl Otoyolu | 160 | Terk edilmiş motel | Benzin istasyonu, araç mezarlığı, kanyon geçidi, reklam panosu | Kumla örtülü köprü altı | Kanyon duvarları |
| Kara Bataklık | 144 | Kazıklı iskele | Cadı kulübesi, batık kilise, sis ormanı | Kara su gölü ortası | Kara su + kök duvarı |
| Donmuş Liman | 160 | Liman ofisi | Vinçler, konteyner labirenti, buzlu gemi, balıkhane | Kırık iskele ucu | Donmuş deniz + buz sarkıtlı duvar |
| Cephe Hattı | 192 | Karargâh (Komutan Diana) | 6 üs (devlet/klan), siperler, mayın tarlası, helikopter pisti | — (üsler) | Dikenli tel + mayın şeridi |
| Kişisel Üs | 48 | Ev | Sandık alanı, tezgâh alanı, köpek kulübesi | — | Kendi duvarın |

Her bölgenin yerleşimi kodda elle yazılmış tek bir plan tablosu olur (`world/areas/<id>.rs`), rastgelelik yalnız
süs ve kaynak dağılımında. Plan değişince test, NPC'lerin ve doğuş noktasının erişilebilir olduğunu (yol bulma ile)
doğrular.

---

## 4. Tasarım tabloları

### 4.1 Mağaza fiyatları (TL, Google Play fiyat basamaklarına yakın)

| Ürün | Fiyat | İçerik | VIP puanı |
|------|-------|--------|-----------|
| Elmas — Avuç | 29,99 | 60 💎 | 30 |
| Elmas — Kese | 99,99 | 220 💎 (+%10) | 100 |
| Elmas — Sandık | 249,99 | 600 💎 (+%20) | 250 |
| Elmas — Kasa | 499,99 | 1.300 💎 (+%30) | 500 |
| Elmas — Hazine | 999,99 | 2.800 💎 (+%40) | 1.000 |
| Elmas — Ejder Hazinesi | 1.999,99 | 6.000 💎 (+%50) | 2.000 |
| İlk alım | — | Her elmas paketinin **ilk** alımında elmas 2 katı | — |

Her TL = 1 VIP puanı (Google'ın kestiği pay hesaba katılmaz; oyuncu ödediği tutar kadar puan alır).

### 4.2 Seviye paketleri (tek seferlik, süreli)

| Paket | Görünür | Süre | Fiyat | İçerik (değer ~5 kat) |
|-------|---------|------|-------|------------------------|
| Başlangıç Paketi | Sv 5'e çıkınca | 24 saat | 39,99 | 300 💎, 20.000 altın, Nadir kaplama, 3 Nadir iksir, köpek yavrusu kartı, "Kurucu" çerçevesi |
| Gelişim Paketi | Sv 10'a çıkınca | 3 gün | 99,99 | 800 💎, 60.000 altın, 10 karakter kartı, motosiklet parçaları seti, Destansı kaplama |
| Savaş Paketi | Sv 25'e çıkınca | 7 gün | 199,99 | 1.800 💎, 150.000 altın, 25 karakter kartı, araba parçaları seti, Efsanevi silah görünümü |

- Paket açılınca oyun ekranında, altta ince bir şerit: paket simgesi + "Başlangıç Paketi 23:41:08" geri sayım;
  dokununca paket sayfası. Süre dolunca kaybolur, bir daha gelmez. Sv 1-10 aralığında yalnız Başlangıç, 10-25'te
  Gelişim, 25-50'de Savaş satın alınabilir (kaçırdığın önceki paket geri gelmez).
- Süre sunucuda tutulur (cihaz saati değiştirilerek uzatılamaz).

### 4.3 VIP seviyeleri

| VIP | Gereken puan (≈TL) | Yeni kazanılan |
|-----|--------------------|----------------|
| V0 | 0 | — |
| V1 | 50 | Günlük ücretsiz sandığa +1 hak; isim yanında VIP rozeti |
| V2 | 150 | Çanta +10 yuva; tüccar yenileme 1 kez ücretsiz/gün |
| V3 | 350 | **Özel sohbet balonu teması (Bronz)**; üretim süresi −%10 |
| V4 | 700 | Günlük 20 💎; köpek deneyimi +%10 |
| V5 | 1.250 | **Özel ayak izi (kıvılcım)**; **oyuna giriş yazısı** ("V5 Ad oyuna katıldı" genel sohbette) |
| V6 | 2.250 | Araç kamuflajı: **Gece Kamuflajı**; çanta +10; ölünce eşya kaybı yok (1 kez/gün) |
| V7 | 3.750 | **Özel silah görünümü (Obsidyen Katana)**; sohbet teması Gümüş |
| V8 | 6.000 | **Özel kanat: Kül Kanatları** (sırtta, animasyonlu); günlük 40 💎 |
| V9 | 10.000 | Araç kamuflajı: **Altın Kaplama**; ayak izi alev; özel profil çerçevesi |
| V10 | 20.000 | **Kanat: Ejder Kanatları**, sohbet teması Altın, giriş yazısı ekrandaki herkese afiş olarak, günlük 80 💎 |
| V10+ | 50.000 | **Kanat: Titan Kanatları** (en gösterişli), özel silah görünümü (Titan Tırpanı), özel isim rengi, kişisel yönetici iletişim kanalı, günlük 150 💎 |

- VIP ayrıcalıkları görünüm + kolaylık; güç satılmaz (hasar, can vermez). Rekabet adil kalır.
- VIP sayfasında seviye seviye ödüllerin canlı önizlemesi (kanat karakterin sırtında döner).
- Görseller 🎨 bölüm 9-10.

### 4.4 Haftalık / Aylık Kart (abonelik değil, süreli kart; Google Play'de tek seferlik ürün)

| Kart | Fiyat | Anında | Her gün (giriş yaptıkça) | Toplam |
|------|-------|--------|--------------------------|--------|
| Haftalık Kart | 49,99 | 150 💎 | 60 💎 + 2.000 altın × 7 gün | 570 💎 |
| Aylık Kart | 149,99 | 450 💎 | 90 💎 + 3.000 altın + 1 karakter kartı × 30 gün | 3.150 💎 |

- Günlük ödül gelen kutusuna (posta) düşer, giriş yapmadığın gün kaybolmaz ama birikme en çok 3 gün.
- Kart bitmeden 1 gün önce hatırlatma.

### 4.5 Pass (Sezon 1)

**Satış:** Ücretsiz yol herkese açık. **VIP Bilet 99,99 TL**: premium ödülleri açar. **Premium Bilet 199,99 TL**:
premium ödüller + **S1 Çerçevesi** + **"Sezon 1 Öncüsü" unvanı** + **anında +10.000 pass XP'si** + premium
ödüllerde +%10 altın. VIP Bilet alan sonradan farkı (99,99) ödeyip Premium'a geçebilir. Elmasla satış kalkar.

**Kademe XP'si (30 kademe):** 1-10: 1.000 / kademe · 11-20: 2.000 · 21-30: 5.000 → toplam 80.000 XP.
Pass XP'si: günlük görevler (5 × 600), haftalık görevler (5 × 3.000), zombi (seviyeye göre 2-10), titan (500),
üs fethi (300). Aktif oyuncu ~45 günde bitirir; Premium'un +10.000'i ~6 gün kazandırır.

**Ödüller (özet; tam liste kodda tablo):** Ücretsiz yol: altın, iksir, malzeme sandığı, 5 kademede bir elmas (toplam
150 💎), karakter kartları. Premium yol: elmas (toplam 900 💎), karakter kartları (toplam 30), Destansı kaplama,
araç kamuflajı, 25. kademede Efsanevi silah görünümü, 30. kademede **S1 kanatları**. Kullanılmayan, işe yaramayan
ödül olmayacak (her ödül ya harcanabilir ya görünür).

### 4.6 Karakter sistemi

- **Ayrı sekme:** Menüde "Karakterler". Yetenek ağacı kendi sekmesinde kalır, ikisi birbirine bağlı değil.
- **4 karakter** (açılmamışlar silüet olarak görünür):

| Karakter | Nadirlik | Oyun tarzı | Özel yetenek |
|----------|----------|------------|--------------|
| Savaşçı (Kaan) | Sıradan | Yumruk ve takla | Takla (mevcut) |
| İz Sürücü | Nadir | Bıçak, hız, kritik | Gölge Atılımı: hedefe atılıp arkasında belirir |
| Muhafız | Destansı | Can, kalkan, yakın grup | Kalkan Duvarı: 3 sn önündeki hasarı keser, çevreyi sersemletir |
| Gölge | Efsanevi | Katana, kombo, can çalma | Kan Yemini: 6 sn boyunca vuruşlar can çalar, hız artar |

- **Kart açma (altınla):** 1 kart **2.500**, 10 kart **20.000** (%20 indirim). Olasılık: Sıradan %55, Nadir %28,
  Destansı %13, Efsanevi %4; 10'luda en az bir Nadir garantisi. Olasılıklar ekranda yazar.
- **İlk kart karakteri açar;** sonrakiler seviye: **her 25 kartta +1 seviye**, en çok 10. seviye
  (toplam 1 + 250 kart). Her seviye karakterin kendi özellik basamağını açar (Savaşçı'nın bugünkü 10 basamağı gibi).
- **Max karakterden kart gelince elmas:** Sıradan 1, Nadir 3, Destansı 8, Efsanevi 20 💎.
- **Karakter görevleri:** Her karakterin kendi görev listesi; her gün her karakter için **1 görev** (o karakterle
  oynanarak yapılır), ödülü o karakterin **1 kartı**. Örnek: "İz Sürücü ile 40 zombiyi arkadan vur".
- Kart açma animasyonu: kart masaya düşer, nadirliğe göre renkli ışık sütunu, dönerek açılır (🎨 bölüm 10).

### 4.7 Çok dil (10 dil)

1. **Önce metinler toparlanır:** Rust çekirdeğindeki (eşya, görev, NPC, öğretici, savaş yazıları) ve Kotlin
   arayüzündeki **bütün** metinler anahtarlı tablolara taşınır: `assets/lang/<dil>.json`. Kodda tek bir sabit
   Türkçe cümle kalmaz (test bunu kontrol eder: kaynak kodda Türkçe karakterli dize araması).
2. **Dil seçimi:** İlk açılışta cihaz dili (destekli değilse English). Ayarlar → Dil: 10 dil, kendi adlarıyla ve
   bayraklarıyla (🎨 bölüm 13). Değişince oyun yeniden başlatmadan güncellenir.
3. **Yazı tipi:** Mevcut piksel yazı tipi Türkçe + Latin; Kiril ve Arapça için aynı görünüşte piksel yazı tipi
   eklenir (açık lisanslı, ör. Silver / Unifont türevi). Arapçada arayüz sağdan sola aynalanır.
4. **Sunucu metinleri çok dilli:** Bakım mesajı, duyuru, posta, etkinlik adları veritabanında
   `{"tr": "...", "en": "...", ...}` olarak tutulur. Yönetici panelinde her dil için ayrı kutu; boş bırakılan dil
   İngilizceye, o da yoksa Türkçeye düşer. Panelde "Türkçeden doldur" tuşu, yazılanı diğer kutulara taslak olarak
   kopyalar.
5. **Hileler/geliştirici paneli de çevrilir** (komut adları değil, açıklamaları ve düğmeleri).
6. **Çeviri:** Ben 10 dile eksiksiz çeviririm; oyun terimleri için sözlük (`docs/lang/sozluk.md`: Titan, Pass, VIP,
   altın, elmas, bölge adları her dilde sabit) tutarlılığı korur. Oyuncu sohbetleri çevrilmez.
7. **Test:** Her dil için arayüz ekran görüntüsü testleri; taşan yazı (metin kutusundan uzun) otomatik yakalanır.

### 4.8 Kod düzeni ve depo

- **Kotlin:** `ui/` ekran başına klasör (`ui/lobby`, `ui/game`, `ui/shop`, `ui/clan`, `ui/profile`, `ui/admin`…),
  ortak bileşenler `ui/kit`. `GameView.kt` (3.194 satır) parçalanır: `render/World`, `render/Hud`,
  `render/Plates`, `render/Fx`, `input/Controls`. `net/` (Supabase, oyun sunucusu istemcisi), `game/` (oturum,
  hesap, kayıt), `i18n/`.
- **Rust:** `zenvex_core/src/` modülleri klasörlere: `world/` (bölgeler, yerleşim, zemin), `entities/` (zombi,
  oyuncu, köpek, araç, NPC), `systems/` (savaş, üretim, ilerleme, ekonomi), `net/`, `i18n/`. `zenvex_server` yeni.
- **Araçlar:** `tools/pixel` (görsel içe aktarma) kalır ve düzenlenir; eski `tools/blender`, `tools/assetgen`'in
  kullanılmayan betikleri, `tools/renders` silinir.
- **Belgeler:** `docs/` yeniden: `docs/tasarim/` (oyun tasarımı), `docs/teknik/` (mimari, sunucu, kurulum),
  `docs/prompt/` (yalnız geçerli istem belgeleri), eski V2-V11 belgeleri silinir (git geçmişinde durur).
- **README:** İngilizce ana README (logo, ekran görüntüleri, özellikler, mimari çizimi, derleme adımları, lisans) +
  `README.tr.md` ve diğer 9 dil; dil seçim satırı en üstte. `CONTRIBUTING.md`, `SECURITY.md`, `CHANGELOG.md`,
  issue/PR şablonları, `.editorconfig`.

### 4.9 Araçlar ve CI (sıfır uyarı)

- Gradle, Android Gradle Plugin, Kotlin, Compose BOM, AndroidX kütüphaneleri, NDK, Paparazzi, Rust araç zinciri,
  cargo-ndk ve bütün GitHub Actions eylemleri o günkü **en son kararlı** sürüme çekilir (sürümler uygulama günü
  resmi kaynaklardan doğrulanır).
- Kotlin uyarıları hata sayılır (`allWarningsAsErrors`), Rust'ta `cargo clippy -D warnings`, `cargo fmt --check`,
  Android Lint `warningsAsErrors`. Kullanımdan kalkmış (deprecated) her API çağrısı yenisine taşınır.
- Actions: derleme + test + lint + sunucu derlemesi + APK/AAB çıktısı; önbellekli; Node 24 tabanlı eylemler;
  "deprecated" uyarısı veren hiçbir adım kalmaz.

### 4.10 APK boyutu

Bugün varlıklar ~45 MB (atlas 35 MB). Plan:
1. Kullanılmayan sprite'lar (eski zombiler yerine türler, kaldırılan araçlar, eski 3D yapılar, eski UI) silinir;
   atlas yeniden paketlenir. Kodda adı geçmeyen ve hiçbir tablodan çağrılmayan her görseli bulan bir denetim betiği
   (`tools/audit_assets.py`) CI'da çalışır.
2. Ayna yönler zaten çalışma anında; ölüm/vurulma kareleri gereksizse azaltılır.
3. Piksel sayfalar kayıpsız WebP, fotoğraf benzeri arka planlar kalite 88 WebP; sesler OGG 96 kbps.
4. Google Play için **AAB**: cihaz yalnız kendi işlemci mimarisini indirir (x86_64 telefonlara gitmez).
5. Hedef: Play'den indirme ~35 MB, tek APK ≤ 60 MB.

---

## 5. Uygulama sırası

Zip gelmeden yapılabilecek kod işleri de bu sıradadır; zip gelince 🎨 adımları araya girer.

| Faz | İçerik | Bağımlılık |
|-----|--------|------------|
| **F0 — Acil** | Supabase geçici rahatlatma (1.1/7), arka plan kopması (1.4), sohbet balonu (1.6), isim kayması (R4), ölüm ekranı (R34), XP kuralı (R39), "Yakın" sohbet (R36), Nova (R9), amblem (R28 — geçici olarak yazı logosu) | yok |
| **F1 — Oyun sunucusu** | `zenvex_server`, istemci ağ katmanı, yük testi, kurulum betiği | K1 (sunucu kiralama) |
| **F2 — Bölgeler** | Ayrı kare dünyalar, yerleşim planları, NPC yerleşimi, eski yol/karakol temizliği, kayıt geçişi | — |
| **F3 — Harita** | Gerçek mini harita, büyük harita, işaret simgeleri, ışınlanma kaldırma, büyücü kilitleri | F2, 🎨 bölüm 11 |
| **F4 — Arayüz** | HUD (para/elmas/+), tuş düzeni, zombi plakası, sürükle-çıkar, sıralama, profil, görev sayacı, tüccar, üretim çoklu, tezgâh inşa modu, klan deposu, sohbetten profil | — (görseller gelince yenilenir) |
| **F5 — Ekonomi** | Mağaza, Play faturalandırma + sunucu doğrulama, VIP, seviye paketleri, haftalık/aylık kart, pass, karakter sistemi, eski eşya → altın | K2, K4, 🎨 bölüm 9-10 |
| **F6 — Görseller** | Zip'in tamamı: 3D'lerin yerine pikseller, bölge yapıları, efektler, auralar, plakalar, unvan efektleri, yetenek simgeleri, mağaza, VIP, karakterler, logo | zip |
| **F7 — Panel ve destek** | Geliştirici paneli, destek sistemi, bildirim iyileştirmesi | F5 (ekonomi sekmesi) |
| **F8 — Metinler** | Bütün diyaloglar, eşya açıklamaları, sistem mesajları yeniden; metinlerin anahtar tablosuna taşınması | F2-F7 (metinler oturmuş olmalı) |
| **F9 — 10 dil** | Çeviri, yazı tipleri, sağdan sola, sunucu metinleri, testler | F8, K3 |
| **F10 — Temizlik ve teslim** | Klasör düzeni, eski kod/belge temizliği, araç sürümleri, sıfır uyarı, APK boyutu, README'ler | hepsi |

Her faz sonunda: Rust testleri, arayüz ve oyun ekran görüntüsü testleri, gerçek akışta oynanış kaydı; commit + push;
sana kısa özet ve ekran görüntüleri.

---

## 6. Zip geldiğinde ne yapacağım

1. Zip ayrı, boş bir klasöre açılır; her dosya 22. belgedeki listeyle eşleştirilir. Eşleşmeyen / eksik / kalitesiz
   (yanlış zemin rengi, kesik kare, yanlış yön, tutarsız boy) olanların listesi sana gider; gerekiyorsa yeniden
   üretim istemi o dosyaya özel düzeltilmiş olarak verilir.
2. İçe aktarma betikleri (`tools/pixel/*`) her bölüm için: zemin silme, parçalara ayırma, piksel ızgarasına oturtma,
   tek ölçek, çapa (ayak tabanı), atlas paketleme.
3. Oyuna bağlama + ekran görüntüsü testleri; her bölüm ayrı commit.
4. Kullanılmayan eski görsel kalmayana kadar denetim betiği çalıştırılır.
