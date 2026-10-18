# 08 — Zenvex Online V2: Büyük Plan

Bu belge V2'nin tamamını tanımlar: görsel kalite, dünya, düşmanlar, eşyalar, craft, ekonomi, karakterler,
araçlar, online, sezon/pass ve kozmetikler. Kod bu belgedeki tablolara göre yazılır (veriler Rust'ta
`zenvex_core/src/data/*.rs` içinde tek yerde durur).

Durum işaretleri: ✅ V2'de yapıldı · 🟡 kısmen (altyapı var, içerik/denge sonra) · ⏳ sonraki aşama

---

## 1. Görsel Kalite (en önemli)

| Konu | V1 | V2 |
|------|----|----|
| Çözünürlük | 1 m ≈ 28 px, karakter 46 px | 1 m ≈ 42 px, **karakter ~70 px** (1,5×) |
| Gölgelendirme | 3 düz ton | **5 ton, renk kaydırmalı rampa** (gölgeler mora/maviye, ışıklar sarıya kayar), **ortam gölgesi (AO)**, arkadan **kenar ışığı (rim)**, metalde parlama |
| Doku | yok | kumaş örgüsü, kot dokusu, ağaç kabuğu, yaprak kümeleri, kaya damarları |
| Çizgi | her yerde koyu | renkli, ışık tarafında açık (seçici dış çizgi) |
| Ekranda | tam sayı ölçek, keskin | **HD (yumuşak) ölçek** varsayılan; ayarlardan "Keskin piksel" |
| Zemin | 32 px karolar, köşeli yollar | **Rust'ta piksel piksel harmanlanan dokular**: çim/toprak/patika/asfalt/kaldırım/beton/bataklık, kenarlar organik |
| Bitki ölçeği | çiçek karakterin 1/3'ü | **gerçek ölçek**: ot 0,3 m, çiçek 0,15 m, çalı 0,6–1 m, ağaç 5–9 m; çiçekler seyrek ve kümeli |
| Atlaslar | kare ızgara | **kırpılmış + paketlenmiş** atlas (bellek ~%60 daha az) |
| Ek parçalar | yok | silah / kask / çanta / pelerin **ayrı sprite**, kare başına el-baş-sırt bağlantı noktaları |
| Arayüz | 16 px elle çizim ikonlar, pixel font | **HD vektör ikonlar** (kenar yumuşatmalı, gradyanlı), **Lilita One + Rubik** fontları, cam/bevel paneller, hareketli lobi |

## 2. Dünya ve Bölgeler

Harita 320×320 karo (1 karo = 48 px ≈ 1,15 m) → ~370 m × 370 m. Güney orman, kuzey şehir.

| Bölge | Seviye | Zombiler | Özellik |
|-------|--------|----------|---------|
| 🏕 **Kamp (Güvenli)** | — | yok | Doğuş noktası, Tüccar Hasan, çalışma tezgâhı, kamp ateşi |
| 🌲 Başlangıç Ormanı | 1–5 | Yürüyen, Köpek | ağaç, taş, kumaş, reçine |
| 🌳 Derin Orman | 5–10 | Yürüyen, Koşucu, Köpek sürüsü | daha sık orman, deri |
| 🟢 Bataklık | 8–15 | **Toksik**, Yürüyen | zehirli su, toksik öz, **Boss: Bataklık Anası** ⏳ |
| 🚧 Şehir Kapısı | **Lv10 kilidi** | — | Lv10 altı geçemez ("Şehir 10. seviyede açılır") |
| 🛡 **Şehir Güvenli Bölgesi** | — | giremez | duvarlar, **keskin nişancı NPC'ler** yaklaşan zombileri vurur, tüccar, tezgâh, banka ⏳ |
| 🏘 Banliyö | 10–20 | Yürüyen, Koşucu, Tank | evler, araçlar, polis eşyaları |
| 🏢 Merkez | 20–30 | **Karanlık**, Tank, Köpek | apartmanlar, karanlık sokaklar |
| ⚙ Sanayi / Askerî Üs | 30–50 | Karanlık, Toksik, **Gizemli** | askerî eşya, **Boss: Titan** |

- Bölge seviyesi yükseldikçe: zombi seviyesi ↑, **düşürme şansı ×1,0 → ×3,0**, nadir eşya şansı ↑.
- Oyuncular birbirine **asla hasar veremez** (PvP yok).
- Gece-gündüz döngüsü: 1 tam gün = 16 dk; gece Karanlık Zombi sayısı ×2, sokak lambaları ve ateş ışık verir.
- Canlı harita: kuşlar, kelebekler, yaprak düşmesi, sis, ara sıra yağmur.

## 3. Düşmanlar

İsim etiketi her düşmanın üstünde: **`Lv 12 Toksik Zombi`** (türe göre renk), boss'larda ekran üstünde büyük can barı.

| Tür | Can | Hız | Hasar | Özel Yetenek | Efekt |
|-----|-----|-----|-------|--------------|-------|
| Yürüyen | 40+8/lv | yavaş | 6+1,4/lv | — | — |
| Koşucu | 25+5/lv | çok hızlı | 4+1/lv | ani atılma | toz izi |
| Köpek | 20+4/lv | çok hızlı | 5+1/lv | sürü halinde gelir | — |
| **Toksik** | 50+9/lv | yavaş | 5+1/lv + zehir | yürüdüğü yere **zehir gölü**; ölünce **zehir bulutu** patlar | yeşil aura, damlama |
| **Karanlık** | 45+8/lv | orta | 8+1,6/lv | yarı görünmez titrer, **kısa ışınlanma**, vurduğunda **can çalar** | mor duman aura, parlayan gözler |
| Tank | 160+20/lv | yavaş | 14+2/lv | **yere vuruş**: alan hasarı + geri itme | sarsıntı, toz halkası |
| **Gizemli (Maskeli)** | 120+15/lv | orta | 10+2/lv | görünmez yaklaşır, yakınken belirir, **kopya** üretir; nadir eşya düşürür, kaçar | beyaz maske, sis |
| **Boss: Titan** | 6000 | yavaş | 40 | şok dalgası, zombi çağırma, öfke modu (%30 altı) | dev, zincirler, kırmızı gözler |
| Boss: Bataklık Anası ⏳ | 4000 | — | 30 | asit tükürme, yumurta | — |

## 4. Eşyalar

### 4.1 Malzemeler
Odun, Taş, **Kumaş**, **Yapıştırıcı**, Reçine, Deri, Metal Hurda, Vida, Barut, Elektronik Parça,
Zombi Dişi, Toksik Öz, Karanlık Kristal, **Titan Parçası**, Benzin.

### 4.2 Tüketilebilir
Bandaj (+25 can), İlk Yardım Kiti (+60), Konserve (+3/sn × 10 sn), Enerji İçeceği (dayanıklılık + %20 hız 10 sn),
Panzehir (zehri temizler). Mermiler: 9 mm, 7,62 mm, 5,56 mm, .308, Roket.

### 4.3 Silahlar
| Silah | Tür | Hasar | Atış/sn | Menzil | Mermi | Not |
|-------|-----|-------|---------|--------|-------|-----|
| Yumruk | yakın | 8 (+lv) | 3,5 | 1,4 m | — | her zaman |
| Sopa | yakın | 16 | 2,0 | 1,8 m | — | craft |
| Polis Copu (Jop) | yakın | 22 | 2,2 | 1,8 m | — | %15 sersemletme |
| Glock | tabanca | 14 | 4 | 12 m | 9 mm | şarjör 17 |
| AK-47 | tüfek | 22 | 8 | 18 m | 7,62 | şarjör 30, sekme |
| M4 | tüfek | 19 | 10 | 20 m | 5,56 | şarjör 30, isabetli |
| Keskin Nişancı | tüfek | 95 | 0,8 | 35 m | .308 | delip geçer |
| RPG | ağır | 220 alan | 0,4 | 25 m | Roket | patlama, kendine hasar yok |

Saldırı joysticki silaha göre çalışır: yakın dövüşte vuruş, ateşli silahta o yöne ateş.

### 4.4 Zırh / Çanta / Pelerin
| Eşya | Slot | Bonus |
|------|------|-------|
| Basit Kask | kafa | +5 zırh |
| Polis Kaskı | kafa | +12 zırh |
| Askerî Kask | kafa | +20 zırh |
| **Titan Kaskı** | kafa | +35 zırh, +%10 can |
| Basit Yelek | gövde | +6 zırh |
| Polis Yeleği | gövde | +15 zırh |
| **Titan Ceketi** | gövde | +40 zırh, +%10 hasar direnci |
| Basit Çanta | sırt | +8 slot |
| Polis Çantası | sırt | +16 slot |
| Askerî Çanta | sırt | +24 slot |
| **Tanrı Pelerini** (Sezon 1) | pelerin | Can %20'nin altına düşünce **3 sn hasar almaz, +%20 hız, +%25 hasar** (60 sn bekleme) |

Zırh: alınan hasar × 100/(100+zırh).

### 4.5 Nadirlik
Sıradan (gri) · Nadir (yeşil) · Epik (mor) · Efsanevi (turuncu) · Sezonluk (altın). Yere düşen eşyanın ışık halkası nadirlik renginde.

## 5. Yere Düşme ve Toplama
- Zombiler, ağaçlar, kayalar, sandıklar eşya ve **altın** düşürür; eşya yerde sallanır ve parlar.
- **Üstüne yürüyünce otomatik toplanır** (çanta doluysa "Çanta dolu" uyarısı).
- 120 sn sonra yerdeki eşya kaybolur.
- Envanterden "At" → yere bırakılır.

## 6. Craft (Üretim)
Çalışma tezgâhında (kamp ve güvenli bölge) veya her yerde (basit tarifler).

| Ürün | Malzemeler | Yer |
|------|-----------|-----|
| Yapıştırıcı | 2 Reçine | her yer |
| Kumaş ×2 | 3 Zombi kıyafeti parçası → (V2'de doğrudan düşer) | — |
| Bandaj | 2 Kumaş + 1 Yapıştırıcı | her yer |
| İlk Yardım Kiti | 2 Bandaj + 1 Toksik Öz | tezgâh |
| Panzehir | 2 Toksik Öz + 1 Yapıştırıcı | tezgâh |
| Sopa | 4 Odun + 2 Vida | her yer |
| Basit Çanta | 5 Kumaş + 2 Deri + 2 Yapıştırıcı | tezgâh |
| Basit Kask | 4 Metal Hurda + 2 Vida + 1 Kumaş | tezgâh |
| Basit Yelek | 4 Kumaş + 3 Metal Hurda + 2 Yapıştırıcı | tezgâh |
| 9 mm ×15 | 1 Metal Hurda + 1 Barut | tezgâh |
| 7,62 ×15 | 2 Metal Hurda + 2 Barut | tezgâh |
| 5,56 ×15 | 2 Metal Hurda + 2 Barut | tezgâh |
| .308 ×5 | 2 Metal Hurda + 3 Barut | tezgâh |
| Roket ×1 | 3 Metal Hurda + 5 Barut + 2 Elektronik | tezgâh |
| Glock | 8 Metal Hurda + 4 Vida + 2 Elektronik | tezgâh |
| **Titan Kaskı** | 5 Titan Parçası + 10 Metal + 2 Karanlık Kristal | tezgâh |
| **Titan Ceketi** | 8 Titan Parçası + 6 Deri + 3 Karanlık Kristal | tezgâh |

## 7. Ekonomi
- **Altın:** zombilerden düşer (seviyeye göre), tüccara satış, boss ödülleri, görevler, oyuncu ticareti.
- **Elmas:** premium para (mağaza/pass) — satın alma sistemi ⏳ (Google Play Faturalandırma).
- **Tüccar Hasan** (kamp) ve **Tüccar Selin** (şehir): mermi, tüketilebilir, basit ekipman satar; her şeyi satın alır (değerin %40'ı).
- **Oyuncu ticareti** 🟡: yakındaki oyuncuya teklif → iki taraf onaylar → Firebase işlemiyle (transaction) takas. V2'de arayüz ve protokol, sunucu doğrulaması ⏳.

## 8. Karakterler (4 adet)
Hesap seviyesi (1–50) + **karakter seviyesi (1–10)**. Karakter seviyesi o karakterle oynayınca artar.

| Karakter | Sınıf | Açılış | Yetenek (otomatik / tuş) |
|----------|-------|--------|--------------------------|
| **Kaan** | Savaşçı | başta açık | Pasif: yakın dövüş +%5/lv; Lv5 kombo; Lv10 "Öfke": 5 sn +%40 hasar (tuş, 40 sn) |
| **Nova** | Tekno | 5000 altın veya Lv8 | **Dron** (otomatik): Lv1 basit dron 6 hasar/0,8 sn → Lv5 ikinci namlu → Lv10 roket. Hedefler ayardan: zombiler / köpekler / boss'lar |
| **Rady** | Büyücü (Kalkan) | 8000 altın veya Lv12 | **Büyü Kalkanı** (tuş, 25 sn): kendine + yakın dostlara kalkan: Lv1 **50**, her seviye **+20** (Lv10: 230), süre 8 sn |
| **Elif** | Şifacı | 8000 altın veya Lv12 | **Şifa Alanı** (tuş, **30 sn**): alan içindekilere toplam can: Lv1 **%5 / 5 sn**, Lv5 **%10 / 4 sn**, Lv10 **%20 / 3 sn** (arası doğrusal) |

Her karakterin 4 dallı yetenek ağacı vardır; ortak dallar (Güç, Dayanıklılık, Çeviklik, Hayatta Kalma) + sınıfa özel dal.

## 9. Araçlar
| Araç | Koltuk | Hız | Yakıt | Nerede |
|------|--------|-----|-------|--------|
| Bisiklet | 1 | 2,2× yürüme | yok | kamp, orman |
| Motosiklet | 1 | 4× | benzin | banliyö |
| Spor Araba | 2 | 5× | benzin | şehir |
| SUV | 4 | 4,5× | benzin | şehir, üs |

- Yakındayken **"Bin"** tuşu belirir; sürüşte hareket joysticki direksiyon, saldırı joysticki gizlenir.
- Araç zombilere çarpınca hasar verir, kendisi de hasar alır (can barı).
- Arkadaşlarla aynı araca binme (koltuklar) online ile ⏳ (protokol hazır).

## 10. NPC'ler
- **Keskin nişancılar:** güvenli bölge kulelerinde; 18 m içine giren zombiyi 1,2 sn'de bir vurur (ağır hasar).
- **Tüccarlar:** kamp ve şehir.
- **Görev veren:** Kamp lideri Murat ⏳ (görevler V2'de görev panelinden).

## 11. Görevler
Günlük 3 görev (ör. "15 zombi öldür", "5 bandaj üret", "Şehirde 3 sandık aç") + ana hikâye görevleri
("Şehir kapısına ulaş", "Titan'ı yen"). Ödül: altın, XP, pass XP, eşya.

## 12. Sohbet
- Oyunun içinde (lobide değil). Sol altta küçük kutu → dokununca açılır.
- Kanallar: **Yerel** (yakındaki oyuncular, 25 m), **Global**, **Klan** ⏳, **Sistem** (düşen eşya, seviye, boss).
- Online yoksa yalnız Sistem + kendi mesajların görünür.

## 13. Sezon 1 — "Uyanış" ve Pass
| Pass | Fiyat | İçerik |
|------|-------|--------|
| Ücretsiz | 0 | 30 kademe, altın, malzeme, 1 kostüm |
| **Normal Pass** | **49,99 TL** | + premium yol: kostümler, silah kaplamaları, **sohbet etiketi**, **profil çerçevesi**, **Tanrı Pelerini** (30. kademe) |
| **Yüksek Seviye Pass** | **99,99 TL** | Normal Pass + 10 kademe atlama + özel "Sezon 1 Efsanesi" etiketi ve altın çerçeve + %20 pass XP |

Pass XP: zombi, görev, boss. Satın alma Google Play Faturalandırma ile ⏳ (V2'de ekran ve kademe sistemi hazır).

## 14. Kozmetik
- Karakter kostümleri (her karaktere 2–3): ör. Kaan "Gece Avcısı", Nova "Neon", Rady "Kızıl Büyü", Elif "Kar Beyazı".
- Silah kaplamaları: Altın, Kamuflaj, Neon, Titan.
- Profil çerçeveleri, sohbet etiketleri, isim renkleri.
- Kozmetikler istatistik vermez (Tanrı Pelerini hariç; sezonluk özel eşya).

## 15. Etkinlikler ⏳
Hafta sonu "Kanlı Ay" (gece uzun, Karanlık Zombi ×3, ödüller ×2), "Titan Avı" (boss doğma sıklığı ↑).

## 16. Hile Menüsü (geliştirici)
Ayarlar → Hakkında → **Sürüm yazısına 7 kez dokun** → Geliştirici Menüsü:
altın ekle, tüm eşyalar, seviye ayarla, karakter seviyesi, ölümsüzlük, tek vuruş, hız ×2, zaman (gündüz/gece),
zombi/boss doğur, şehre ışınlan, tüm karakterleri/kozmetikleri aç, pass kademesi.

## 17. Online Mimari
```
Telefon ── Firebase Auth (anonim → ileride Google)
       ── Realtime Database
            /presence/{bölge}/{uid}  : isim, karakter, lv, x, y, yön, anim, araç   (≈8 Hz, onDisconnect sil)
            /chat/global             : son 50 mesaj
            /chat/local/{bölge}      : konumlu mesaj, istemci 25 m süzer
            /trade/{id}              : teklif → kabul (transaction)
            /profiles/{uid}          : görünen isim, çerçeve, etiket
```
- `android/app/google-services.json` yoksa uygulama **çevrimdışı** derlenir ve çalışır.
- Zombiler V2'de her istemcide yerel (aynı tohum); paylaşımlı zombi + otoriter sunucu ⏳ → **Rust sunucu**
  (`zenvex_core` aynen kullanılacak) ücretsiz bir barındırmaya (Fly.io / Oracle Free) taşınacak.
- Kurulum adımları: `docs/09_FIREBASE_KURULUM.md`.

## 18. Lobi (yeniden tasarım)
- Hareketli katmanlı arka plan (gökyüzü, uzak şehir silueti, orman, sis, kıvılcımlar), karakter ışıklı platformda.
- **Az tuş, net hiyerarşi:** üstte profil + paralar + Pass; sağda karakter seçimi (◀ ▶); altta 5 sekmeli dok
  (Karakter · Envanter · Mağaza · Pass · Sosyal); sağ altta büyük **OYNA** + bölge.
- Görev ve etkinlik küçük rozetli ikonlar.

## 19. Sesler
Silah başına atış sesi, şarjör, patlama, araç motoru, kapı, eşya toplama, altın, craft, seviye, boss kükremesi,
zehir, ışınlanma, kalkan, şifa, ayak sesi (çim/asfalt), ortam (rüzgâr, kuş, şehir uğultusu), arayüz;
**müzik:** lobi teması ve oyun ortam müziği (kodla sentez).
