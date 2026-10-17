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

## 5. "Yakında" (tuşu var, çalışmıyor)
Google giriş, hesap oluşturma, İngilizce, posta, görevler, etkinlikler, sıralama, arkadaş, klan, sohbet, mağaza
satın alma, kıyafet/görünüm/isim değiştirme, ekipman, eşya kullanma/atma, yetenek sıfırlama, HUD düzenleyici,
renk körlüğü filtreleri, bulut kayıt, online sunucular, **ses ve müzik**.

## 6. APK'yı telefona kurma
1. GitHub'da repo sayfası → **Releases** → **"Zenvex Online — son derleme"**.
2. `ZenvexOnline-0.1.X.apk` dosyasına dokun → indir → aç.
3. İlk seferde "Bu kaynaktan yüklemeye izin ver" → Yükle.
4. Yeni sürümler aynı imzayla derlendiği için eskisini silmeden üstüne kurulur (ilerleme korunur).

## 7. Sıradaki adımlar (öneri)
1. Telefonda ilk test → his/denge geri bildirimi (hız, hasar, zombi sayısı, joystick)
2. Ses efektleri (kodla sentez: yumruk, isabet, ağaç, zombi homurtusu) + basit müzik
3. Karakter animasyon cilası (yürüme/koşu kol-bacak, yumrukta gövde dönüşü)
4. Gece-gündüz, yeni zombi türleri
5. Online altyapı (Rust sunucu — `zenvex_core` aynen kullanılacak)
