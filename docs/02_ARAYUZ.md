# 02 — Arayüz (UI/UX) Tasarımı

Kural: **Her tuş yerinde olacak.** Çalışmayan tuşlar gri değil, normal görünür; basınca "🔒 Yakında!" tostu (1.5 sn) çıkar. Böylece oyun "eksik" değil "büyüyen" hissi verir.

## 0. Genel UI Kuralları
| Konu | Karar |
|------|-------|
| Yön | **Yatay (landscape)** kilitli |
| Stil | Pixel art UI: 9-slice paneller, 2 px koyu outline, üst kenarda 1 px açık vurgu, alt kenarda 2 px gölge (bastırılmış "tuş" hissi) |
| Ana renkler | Panel: koyu lacivert #23365a (%92 opak) · Birincil tuş: hardal #e8a93a · İkincil: #33507e · Tehlike: #a02c3c · Kilitli/yakında: üzerinde küçük kilit rozeti |
| Font | Pixel font (öneri: "Pixelify Sans" / "Press Start 2P" başlık, "m5x7"/"Silkscreen" gövde). **Türkçe karakterler (ğ ş ı İ ö ü ç) zorunlu** |
| Dokunma alanı | Min. 48×48 dp, tuşlar arası ≥ 8 dp |
| Güvenli alan | Çentik / kamera deliği için `WindowInsets` boşluğu |
| Basma geri bildirimi | Tuş 2 px aşağı iner + kısa "tık" sesi + hafif titreşim (ayarlardan kapatılabilir) |
| Geçişler | 150–250 ms; ekran geçişinde pixel "dissolve" (dither) efekti |
| Dil | Türkçe (varsayılan) + İngilizce. Tüm metinler `strings.xml` / Rust tarafında `.ftl` (Fluent) |

## 1. Ekran Akışı
```
Splash (logo) → Yükleme/Güncelleme → Giriş Ekranı → [İlk kez ise] Karakter Tanıtım/İsim
      → ANA LOBİ ──┬─ OYNA → Sunucu/Dünya Seç → Yükleme Ekranı → OYUN (HUD)
                   ├─ Karakter
                   ├─ Yetenekler (Skill)
                   ├─ Envanter
                   ├─ Mağaza 🔒
                   ├─ Arkadaşlar 🔒 · Klan 🔒 · Sohbet 🔒
                   ├─ Görevler 🔒 · Etkinlikler 🔒 · Posta 🔒 · Sıralama 🔒
                   ├─ Ayarlar
                   └─ Profil
OYUN → Duraklat Menüsü → (Devam / Ayarlar / Lobiye Dön)
OYUN → Ölüm Ekranı → (Yeniden Doğ / Lobiye Dön)
```

## 2. Splash & Giriş
**Splash:** Siyah → pixel logo "ZENVEX ONLINE" düşer + sallanır, altında stüdyo adı. 2 sn.

**Giriş ekranı** (arka plan: hareketli pixel orman, zombiler uzakta yürür):
| Öğe | Konum | Durum |
|-----|-------|-------|
| Logo | Üst orta | — |
| [Misafir Olarak Oyna] | Orta, büyük, hardal | ✔ çalışır |
| [Google ile Giriş] | Altında | 🔒 |
| [Hesap Oluştur / E-posta] | Altında | 🔒 |
| Dil seç (🌐 TR/EN) | Sağ üst | ✔ |
| Sürüm no. | Sol alt, küçük | ✔ |
| Kullanım Şartları · Gizlilik | Alt orta, link | ✔ (metin sayfası) |
| "Ekrana dokun" yanıp sönen yazı | (Misafir hesabı varsa direkt) | ✔ |

## 3. ANA LOBİ (en önemli ekran)
```
┌──────────────────────────────────────────────────────────────────────────┐
│ [Avatar][Kaan  Lv 7 ███░░ XP]     [🪙 1.250 +] [💎 30 +] [⚡ 5/5]  [✉][⚙] │
│                                                                          │
│ ┌────────┐                                                ┌────────────┐ │
│ │Görevler│                                                │ Etkinlik   │ │
│ │Etkinlik│          ( KARAKTER BÜYÜK ÖNİZLEME )           │ banner 🔒  │ │
│ │Sıralama│          idle_lobby animasyonu, yerde          └────────────┘ │
│ │Posta   │          ateş/kamp, arkada orman paralaks                     │
│ └────────┘          [⟲ karakteri döndür sürükle]                         │
│                                                                          │
│ [Karakter][Yetenek][Envanter][Mağaza][Arkadaş][Klan]   [ Sunucu: TR-1 ▾] │
│                                                      ┌────────────────┐  │
│ [💬 Sohbet satırı (kapalı) .....................]    │    ▶ OYNA      │  │
│                                                      └────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```
| Bölge | Öğeler | Durum |
|-------|--------|-------|
| Üst sol | Avatar (profil), isim, level rozeti, XP barı | ✔ |
| Üst orta-sağ | Altın 🪙 (+), Elmas 💎 (+), Enerji ⚡ (görsel) | Sayı ✔, (+) 🔒 |
| Üst sağ | Posta ✉ (kırmızı nokta), Ayarlar ⚙ | ✉ 🔒, ⚙ ✔ |
| Sol dikey menü | Görevler, Etkinlikler, Sıralama, Posta | 🔒 |
| Orta | Karakter önizleme, kamp ateşi, sürükle → döndür (4 yön) | ✔ |
| Sağ üst | Etkinlik banner'ı (kaydırmalı) | 🔒 |
| Alt şerit | Karakter, Yetenek, Envanter, Mağaza, Arkadaş, Klan | Karakter/Yetenek/Envanter ✔, diğerleri 🔒 |
| Alt sağ | **OYNA** (büyük, nabız gibi atan parlama), Sunucu seçici | OYNA ✔, sunucu sadece "Tek Oyunculu" |
| Alt sol | Sohbet satırı | 🔒 |
| Arka plan | Gece-akşam ormanı, ateş ışığı titreşimi, ateşböcekleri | ✔ |

## 4. Alt Ekranlar
### 4.1 Karakter
Sol: büyük önizleme (döndürülebilir). Sağ: istatistik paneli — Level, HP, Dayanıklılık, Saldırı, Hız, Kritik, Toplam Öldürme, Kesilen Ağaç, Oynama Süresi. Alt: [Kıyafet 🔒] [Görünüm 🔒] [İsim Değiştir 🔒] sekmeleri. Ekipman slotları (Kafa, Gövde, Bacak, Ayak, Eldiven, Çanta) — boş çerçeve, dokununca 🔒.

### 4.2 Yetenekler (Skill)
- Üstte "Skill Puanı: 3" + [Sıfırla 🔒]
- 4 sütunlu ağaç (bkz. 03 §6): her düğüm 48×48 ikon, altında seviye `2/10`, dokununca açıklama paneli + [Yükselt] tuşu.
- Kilitli düğümler koyu + zincir ikon, gereken level yazılı.

### 4.3 Envanter
6×5 ızgara slot (MVP: sadece Odun, Taş?, Zombi Dişi gibi düşen eşyalar sayaç olarak). Sağda seçili eşya detayı. Tuşlar: [Kullan 🔒] [At 🔒] [Sırala ✔]. Üst sekmeler: Tümü / Malzeme / Ekipman 🔒 / Tüketilebilir 🔒.

### 4.4 Mağaza 🔒
Tasarımı yapılır, ekran açılır ama "Yakında" katmanı ile: sekmeler Öne Çıkan / Kostüm / Elmas / Paketler.

### 4.5 Profil
Avatar, isim, ID (kopyala), oyun istatistikleri, rozetler (boş), [Çıkış Yap].

### 4.6 Sunucu / Dünya Seçimi
Kart listesi: "🌲 Başlangıç Ormanı (Tek Oyunculu) ✔", "TR-1 Online 🔒", "EU-1 🔒". Her kartta ping (—), oyuncu sayısı (—), zorluk.

### 4.7 Yükleme Ekranı
Tam ekran illüstrasyon (05 §UI-12), alt bar pixel ilerleme çubuğu (zombi eli sürünüyor), rastgele ipucu metni ("Ağaçlara yumruk atarak odun toplayabilirsin!").

## 5. OYUN İÇİ HUD
```
┌──────────────────────────────────────────────────────────────────────────┐
│[Portre] ❤ HP ██████████ 100/100            [🗺 Mini-harita]   [⏸]       │
│ Lv 7    ⚡ Dayanıklılık ████████            (yuvarlak, 96dp)  [🎒][📋🔒] │
│ XP ████░░░░░░  (ince, ekran üst kenarı boyunca da olabilir)              │
│                                                                          │
│                        ( OYUN DÜNYASI )                                  │
│                                                       [🔄🔒][🍖🔒]        │
│                                                       [⚔ Skill1🔒]       │
│   ╭───────╮                                       [Skill2🔒] ╭───────╮   │
│   │   ●   │  HAREKET                              SALDIRI    │  👊   │   │
│   ╰───────╯  joysticki                            joysticki  ╰───────╯   │
│                          [Etkileşim / Topla ✋] (bağlamsal)               │
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.1 Hareket Joysticki (sol)
| Özellik | Değer |
|---------|-------|
| Tür | **Dinamik** (varsayılan): sol yarı ekranda nereye dokunulursa orada belirir. Ayardan "Sabit" seçilebilir |
| Boyut | Taban 140 dp, topuz 60 dp (ayardan %70–150) |
| Ölü bölge | %12 (ayar 0–30) |
| Yürüme/Koşma eşiği | %60 (ayar) |
| Görsel | Pixel halka, bastığında hafif parlar; topuz 8 yönlü ok izi |
| Boşta | %40 opak, kullanımda %90 |

### 5.2 Saldırı Joysticki (sağ)
| Özellik | Değer |
|---------|-------|
| Davranış | **Dokun = en yakın hedefe otomatik yönelip vur** (aim-assist), **sürükle = o yöne vur** (yönlü), basılı tut = otomatik kombo |
| Boyut | Taban 120 dp, ortada yumruk ikonu |
| Yön göstergesi | Sürüklerken karakterin önünde yarı saydam yay (vuruş alanı) gösterilir |
| Bekleme | Kombo sonunda kısa cooldown, halka dolum animasyonu |
| Ayarlar | Otomatik nişan aç/kapa, nişan gücü, yay göstergesi aç/kapa |

### 5.3 Diğer HUD Öğeleri
| Öğe | Durum |
|-----|-------|
| Portre + HP + Dayanıklılık (stamina — koşu/yumruk harcar) + XP | ✔ |
| Mini-harita (dokununca büyük harita 🔒 veya ✔ basit) | ✔ basit |
| Duraklat ⏸ | ✔ |
| Çanta 🎒 (envanter overlay) | ✔ |
| Görev listesi 📋 | 🔒 |
| Etkileşim ✋ (ağaç/eşya yakınında belirir) | ✔ (opsiyonel; yumruk da yeter) |
| Skill 1–2 slotları, eşya hızlı slotu, yemek 🍖, silah değiştir 🔄 | 🔒 (yerleri hazır) |
| Hasar sayıları (beyaz, kritik sarı büyük) | ✔ |
| Zombi üstü HP barı (vurulunca 3 sn görünür) | ✔ |
| Level Up efekti + "SEVİYE 8!" banner + skill puanı bildirimi | ✔ |
| Toplama bildirimi (sol orta: "+3 Odun") | ✔ |
| Düşük HP'de ekran kenarı kırmızı nabız | ✔ |
| FPS / ping göstergesi (ayarlardan) | ✔ |

### 5.4 Duraklat Menüsü
Bulanık/dither karartma + panel: [Devam Et] [Ayarlar] [Kontrolleri Düzenle] [Lobiye Dön] (onay penceresi).

### 5.5 Ölüm Ekranı
Ekran gri-kırmızıya döner, "ÖLDÜN" pixel yazı, istatistik (yaşama süresi, öldürme), [Yeniden Doğ (5 sn)] [Lobiye Dön]. MVP cezası: XP kaybı yok (veya %5 — bkz. sorular).

## 6. AYARLAR (tam liste — hepsi çalışacak)
Sekmeli panel: **Grafik · Kontrol · Ses · Oyun · Hesap · Hakkında**

### 6.1 Grafik
| Ayar | Seçenekler | Varsayılan |
|------|-----------|-----------|
| Hazır ayar | Düşük / Orta / Yüksek / Ultra / Özel | Cihaza göre otomatik |
| FPS limiti | 30 / 45 / 60 / 90 / 120 (cihaz destekliyorsa) | 60 |
| Render ölçeği | %50 / %75 / %100 (piksel çözünürlüğü değil, son çıktı) | %100 |
| Pixel ölçekleme | Tamsayı (keskin) / Ekrana doldur (hafif bulanık) | Tamsayı |
| Bitki yoğunluğu | Düşük (%30) / Orta (%60) / Yüksek (%100) | Orta |
| Rüzgâr animasyonu (ağaç/ot sallanma) | Kapalı / Basit / Tam (shader) | Tam |
| Gölgeler | Kapalı / Blob (elips) / Yönlü sprite gölge | Blob |
| Parçacık efektleri | Kapalı / Az / Çok | Çok |
| Işıklandırma (ateş, ışık sütunu, bloom) | Kapalı / Açık | Açık |
| Bulut gölgeleri | Kapalı / Açık | Açık |
| Görüş mesafesi (aktif nesne yarıçapı) | Yakın / Orta / Uzak | Orta |
| Ağaç arkası saydamlık | Açık / Kapalı | Açık |
| Ekran sarsıntısı | 0–100% | 70% |
| Hasar sayıları | Açık / Kapalı | Açık |
| Pil tasarrufu modu | Açık (30 FPS + düşük efekt) / Kapalı | Kapalı |
| FPS göstergesi | Açık / Kapalı | Kapalı |

### 6.2 Kontrol & Hassasiyet
| Ayar | Seçenekler | Varsayılan |
|------|-----------|-----------|
| Hareket joysticki türü | Dinamik / Sabit | Dinamik |
| Hareket hassasiyeti | 0.5× – 2.0× (kaydırıcı) | 1.0× |
| Hareket ölü bölge | %0 – %30 | %12 |
| Koşma eşiği | %40 – %90 | %60 |
| Saldırı joysticki hassasiyeti | 0.5× – 2.0× | 1.0× |
| Saldırı ölü bölge | %0 – %30 | %15 |
| Otomatik nişan | Kapalı / Zayıf / Güçlü | Zayıf |
| Vuruş yayı göstergesi | Açık / Kapalı | Açık |
| Basılı tutunca otomatik saldırı | Açık / Kapalı | Açık |
| Joystick boyutu | %70 – %150 | %100 |
| Joystick saydamlığı | %20 – %100 | %60 |
| Titreşim (haptik) | Kapalı / Hafif / Güçlü | Hafif |
| Sol el modu (joystickleri değiştir) | Açık / Kapalı | Kapalı |
| **HUD Düzenleyici** → [Düzenle] | Her HUD öğesini sürükle, boyutlandır, saydamlık; [Sıfırla] [Kaydet]; 3 profil slotu | — |
| Gamepad desteği | (bilgi) Bluetooth gamepad algılandı/algılanmadı | Otomatik |

### 6.3 Ses
Ana ses, Müzik, Efektler, Ortam (rüzgâr/kuş), Arayüz sesleri, Zombi sesleri — her biri 0–100 kaydırıcı. [Arka planda sesi kapat] aç/kapa.

### 6.4 Oyun
Dil (TR/EN), Hasar sayısı stili, Bildirimler 🔒, Kamera yakınlaştırma (Yakın/Normal/Uzak = 3×/2.5×/2× ölçek), Kamera yumuşatma, Renk körlüğü modu (Deuteranopi/Protanopi/Tritanopi filtre), Yazı boyutu.

### 6.5 Hesap
Misafir ID, [Hesabı Bağla 🔒], [Bulut Kayıt 🔒], [Verileri Sil] (çift onay), [Çıkış].

### 6.6 Hakkında
Sürüm, yapımcılar, lisanslar (Rust crate'leri, fontlar), [Destek/Geri Bildirim 🔒], Gizlilik.

## 7. UI Bileşen Kiti (çizilecek görseller listesi)
Panel 9-slice (koyu, açık, uyarı), tuş 9-slice (birincil/ikincil/tehlike × normal/basılı/kilitli), sekme, kaydırıcı (ray + topuz), aç/kapa anahtarı, onay kutusu, açılır menü, ilerleme barları (HP kırmızı, stamina sarı, XP mavi-camgöbeği), slot çerçeveleri (normal/seçili/nadirlik renkleri), joystick tabanı + topuz, yumruk ikonu, para ikonları, ~40 menü ikonu, kilit rozeti, kırmızı bildirim noktası, tost kutusu, onay penceresi, tooltip. Ayrıntılı promptlar → 05.
