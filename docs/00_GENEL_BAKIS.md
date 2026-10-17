# 00 — Genel Bakış & MVP Kapsamı

## 1. Vizyon (tek cümle)
Telefonda iki joystickle oynanan, sıcak renkli 2.5D pixel art bir dünyada zombileri yumruklayarak level kasılan, ileride online hayatta kalma MMO'suna dönüşecek bir oyun.

## 2. Görsel Kimlik
- **Kamera:** Üstten 3/4 (oblique / "top-down 2.5D"). İzometrik DEĞİL — dünya kare ızgara, nesneler önden-yukarıdan görünür (Stardew Valley / Zombix Online / Graveyard Keeper açısı).
- **Derinlik hissi:** Y-sıralama (aşağıdaki nesne öne çizilir), gölge sprite'ları, ağaç gövdesinin arkasına geçince tepenin yarı saydam olması, hafif paralaks bulut gölgeleri.
- **Pixel ölçeği:** 1 karo = 32×32 px. Sanal çözünürlük yüksekliği **360 px** (genişlik ekran oranına göre 640–800). Tamsayı ölçekleme (3×, 4×) + nearest-neighbor → bulanıklık yok.
- **Palet:** Sınırlı 48 renklik özel palet (bkz. 01_KARAKTER.md §5). Tüm görseller bu palete indirgenir.
- **Ton:** "Sevimli ama tehlikeli" — doygun yeşiller, sıcak gün ışığı, zombiler soluk yeşil-mor, kan yerine koyu kırmızı pixel parçacık (yaş sınırı için ayarlanabilir).

## 3. MVP (İlk Sürüm) — VAR
| Alan | İçerik |
|------|--------|
| Karakter | 1 adet hazır karakter ("Kaan" — bkz. 01), 4 yön + ayna, tam animasyon seti |
| Dünya | Tek düz harita (örn. 128×128 karo), çim/toprak/patika zemin, ağaçlar, çalılar, çiçekler, otlar, taşlar, rüzgârda sallanma |
| Düşman | 1 tip zombi (Yürüyen), basit yapay zeka: dolaş → gör → kovala → vur |
| Savaş | Sadece yumruk (3'lü kombo), zombiye hasar, geri itme, ölüm |
| Toplama | Ağaca yumruk → odun (envanter sadece sayaç) |
| İlerleme | XP, Level (1–50), skill puanı, 4 skill (bkz. 03) |
| Kontrol | Sol: Hareket joysticki · Sağ: Saldırı joysticki (yönlü vuruş) |
| Arayüz | Tüm lobi ekranları ve tuşları **tasarlanmış ve yerinde**; çoğu "Yakında" uyarısı verir |
| Ayarlar | Grafik, hassasiyet, kontroller (HUD düzenleyici), ses, dil — çalışır |
| Kayıt | Yerel kayıt (cihazda). Online yok. |

## 4. MVP — YOK (ama tuşu var)
Online/çok oyunculu, sohbet, klan, arkadaşlar, mağaza/ödeme, silahlar, inşa, craft, açlık/susuzluk, gece-gündüz (opsiyonel), araç, görevler, etkinlikler, posta, sıralama.

## 5. Yol Haritası (öneri)
| Aşama | Hedef | Çıktı |
|-------|-------|-------|
| F0 | Planlama & tasarım | Bu belgeler, görsel promptları, palet, UI wireframe |
| F1 | Görsel üretim | Konsept → sprite sheet → tileset → UI kit |
| F2 | Rust prototip (PC) | Hareket, kamera, y-sort, ağaçlar, rüzgâr |
| F3 | Savaş & zombi | Yumruk kombo, hitbox, zombi AI, XP |
| F4 | Android kabuğu | Kotlin + Compose lobi, JNI, joystickler, ayarlar |
| F5 | Level/Skill + kayıt | Skill ağacı, kayıt/yükleme, ses |
| F6 | Cila & test | Performans (düşük cihaz 60 FPS), kapalı test APK |
| F7+ | Online | Rust sunucu, hesap, çok oyunculu |

## 6. Başarı Ölçütleri (MVP)
- 3 GB RAM'li orta-alt Android'de **sabit 60 FPS**, ilk açılış < 5 sn.
- Dokunmadan karaktere hareket gecikmesi < 50 ms.
- APK < 80 MB.
- Yeni oyuncu 2 dakika içinde ilk zombiyi kesip level atlamalı.
