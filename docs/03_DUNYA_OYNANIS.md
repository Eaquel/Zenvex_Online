# 03 — Dünya & Oynanış

## 1. Dünya: "Başlangıç Ormanı"
| Özellik | Değer |
|---------|-------|
| Boyut | 128×128 karo (4096×4096 px) — ileride chunk'lı sonsuz/büyük harita |
| Zemin | Düz, yükselti yok. Katmanlar: Çim (ana), Koyu çim lekeleri, Toprak patika, Çıplak toprak, Küçük gölet (geçilmez su — opsiyonel) |
| Kenar | Harita kenarında aşılmaz sık orman duvarı |
| Doğuş noktası | Haritanın ortasında küçük açıklık + sönmüş kamp ateşi + devrilmiş kütük |
| Üretim | Prosedürel (seed ile): Poisson-disk dağıtım + gürültü (noise) maskesi → orman yoğun bölgeler, açıklıklar, çiçek tarlaları |
| Kamera | Karaktere yumuşak takip (lerp), ölü bölge küçük, kenarda durma |
| Zaman | MVP: sabit "öğleden sonra" ışığı. (Gece-gündüz soru listesinde) |

### 1.1 Zemin Tileset (32×32)
- Çim düz ×4 varyant, çim + çiçek ×4, koyu çim ×2
- Toprak ×3, patika ×3
- **Geçiş karoları:** çim↔toprak için 47'lik "blob" autotile (veya 16'lık basit set)
- Su + kıyı autotile (opsiyonel)
- Detay "decal"ler (karoya bağlı değil): küçük taşlar, yaprak, dal, mantar, kemik

### 1.2 Doğa Nesneleri (sprite, y-sıralı)
| Nesne | Boyut (px) | Varyant | Etkileşim | Animasyon |
|-------|-----------|---------|-----------|-----------|
| Meşe (büyük yuvarlak ağaç) | 96×128 | 3 | Yumrukla kesilir → odun | Tepe rüzgârda sallanır, vurulunca titrer + yaprak düşer, yıkılma 6 kare, kütük kalır |
| Çam | 64×128 | 3 | Kesilir | Sallanma, vurma, yıkılma |
| Huş (beyaz gövde) | 64×112 | 2 | Kesilir | Sallanma |
| Ölü ağaç (yapraksız) | 64×96 | 2 | Kesilir (az odun) | Sabit + vurma |
| Kütük (kesilmiş) | 32×24 | 1 | — (X sn sonra ağaç yeniden büyür) | Büyüme 3 aşama |
| Çalı / bush | 32×32, 48×32 | 4 | İçinden geçilir, yavaşlatır, hışırdar | Sallanma, içinden geçerken titreme |
| Böğürtlen çalısı | 32×32 | 1 | 🔒 (ileride yemek) | Sallanma |
| Uzun ot | 16×24 | 5 | Dekor, geçince eğilir | Sallanma (shader) |
| Çiçek (papatya, gelincik, mavi) | 16×16 | 6 | Dekor | Hafif sallanma |
| Eğrelti otu (fern) | 32×24 | 2 | Dekor | Sallanma |
| Mantar kümesi | 16×16 | 3 | Dekor | — |
| Kaya büyük / orta / küçük | 64×48 / 32×32 / 16×16 | 3'er | Engel (ileride madencilik) | — |
| Devrik kütük | 64×32 | 1 | Engel | — |
| Kamp ateşi | 32×32 | 1 | Lobi/doğuş dekoru | 6 kare alev |

### 1.3 Görsel Derinlik Kuralları
1. Her nesnenin **pivotu tabanında**; çizim sırası `pivot.y`'ye göre.
2. Ağaç tepesi ayrı katman: karakter arkasına girince tepe %40 saydam olur (yumuşak geçiş 0.2 sn).
3. Her nesnenin altında gölge (ayar: blob / yönlü).
4. Rüzgâr: vertex shader ile sinüs sallanma — sadece sprite'ın **üst kısmı** hareket eder (UV.y ağırlıklı); her nesneye rastgele faz. Çalı/ot oyuncu temasında ek itme.
5. Ortam: süzülen yaprak parçacıkları, kelebek, bulut gölgesi kayması, uzakta kuş sesi.

## 2. Zombi: "Yürüyen" (MVP tek tip)
| Özellik | Değer |
|---------|-------|
| Görünüm | Yırtık mor tişört, soluk yeşil ten, bir kolu öne uzanmış, kambur, tek gözü parlak sarı (karanlıkta okunur) |
| Kare | 48×48, karakterle aynı ölçek |
| Animasyonlar (×3 yön) | Idle (sallanma) 4 · Walk (topallama) 6 · Run/Lunge (kovalama) 6 · Attack (pençe) 5 · Hurt 2 · Death 7 (yere yığılma, sonra pixel'e dağılarak yok olma) · Spawn (yerden çıkma) 6 |
| HP | 40 + (level × 8) |
| Hız | Dolaşma %40, kovalama %85 (oyuncu koşusundan yavaş) |
| Hasar | 6–9, 1.2 sn aralıkla |
| Görüş | 6 karo yarıçap, ses (koşma) 9 karo |
| XP | 15 + level×3 |
| Düşürme | %30 "Zombi Dişi" (malzeme, sayaç) |
| Doğma | Harita genelinde en fazla 25 aktif, oyuncudan min. 12 karo uzakta, 20 sn'de bir tamamla. Bölgeye göre zombi level'i (merkez Lv1–3, kenarlar Lv8–12) |

### 2.1 Zombi AI (durum makinesi)
```
Dolaş (rastgele nokta) → [oyuncu görüldü] → Fark Et (0.4 sn "!" balonu, homurtu)
   → Kovala → [menzil 1 karo] → Saldır → (tekrar)
   → [oyuncu 14 karo uzak / 6 sn görünmez] → Kaybet → Dolaş
Herhangi → Hurt (geri itilme, 0.25 sn sersemleme) → Kovala
HP=0 → Ölüm → XP orb'ları oyuncuya uçar
```
- Yol bulma: MVP'de basit "steering + engelden kaçma"; F5'te karo tabanlı A*/flow field.
- Sürü: yakındaki zombiler birbirini iter (separation) — üst üste binmezler.

## 3. Savaş: Yumruk Sistemi
| Özellik | Değer |
|---------|-------|
| Kombo | A (sol) 8 hasar → B (sağ) 8 → C (aparkat) 16 + geri itme. Pencere 0.35 sn |
| Vuruş alanı | Karakter önünde 70° yay, 1.1 karo menzil |
| Hitstop | İsabette 50 ms donma (C'de 90 ms) — "vuruş hissi" için kritik |
| Geri bildirim | Beyaz flaş, hasar sayısı, pixel toz/kan parçacığı, ses, hafif ekran sarsıntısı, haptik |
| Kritik | %5 taban şans, ×1.75 |
| Stamina | Her yumruk 4, koşu 6/sn, yenilenme 15/sn (1 sn bekledikten sonra). Stamina 0 → yumruk yavaşlar |
| Ağaç | Ağaca vuruş: Chop animasyonu, ağaç HP: Meşe 60, Çam 45, Huş 40, Ölü 25. Yıkılınca 3–6 odun + 10 XP |
| Hedef önceliği | Otomatik nişan: Zombi > Ağaç > Diğer |

## 4. İlerleme: XP & Level
- Level aralığı: 1–50 (MVP)
- Level n → n+1 için gereken XP: `XP(n) = round(50 × n^1.6)` → Lv1→2: 50 · Lv4→5: 459 · Lv9→10: 1.682 · Lv19→20: 5.559 · Lv49→50: 25.310 (Lv50'ye toplam ≈ 490k XP)
- XP kaynakları: zombi öldürme, ağaç kesme, ilk kez keşfedilen bölge (bonus), (ileride görev)
- Her level: **+1 Skill Puanı**, +5 maks HP, +2 maks stamina; her 5 level'da +1 ekstra puan
- Level up: tam HP doldurma, ışık sütunu efekti, ses, banner

## 5. Skill (Yetenek) Ağacı — MVP 4 dal
| Dal | Skill | Maks | Etki / seviye | Gereken Lv |
|-----|-------|------|---------------|-----------|
| 👊 **Güç** | Demir Yumruk | 10 | +6% yumruk hasarı | 1 |
| | Ağır Aparkat | 5 | Kombo C +12% hasar, +geri itme | 5 |
| | Sarsıcı Darbe | 1 | Kombo C %25 şansla 1 sn sersemletir | 15 |
| ❤ **Dayanıklılık** | Kalın Deri | 10 | +4% maks HP | 1 |
| | Yeniden Doğuş | 5 | Savaş dışı HP yenilenmesi +20% | 5 |
| | Son Nefes | 1 | Ölümcül darbede 1 HP ile kal (90 sn bekleme) | 15 |
| 💨 **Çeviklik** | Hafif Adım | 10 | +2% hareket hızı | 1 |
| | Nefes Kontrolü | 5 | Stamina harcama −8% | 5 |
| | Kaçış (Dash) | 1 | Skill slotu 1: kısa atılma 🔓 (HUD tuşu çalışır hale gelir) | 15 |
| 🪓 **Odunculuk** | Sert Eller | 10 | Ağaca +10% hasar | 1 |
| | Bereket | 5 | Ağaçtan +1 odun şansı %10 | 5 |
| | Ormanın Dostu | 1 | Kesilen ağaçlar %50 hızlı yeniden büyür | 10 |

Ek pasif: **Kritik Göz** (her dalın 20. puanından sonra açılan ortak düğüm, +1% kritik ×5).

## 6. Kayıt
Rust tarafında `serde` + `bincode`/`ron` → cihaz iç depolama. İçerik: karakter (level, XP, skill puanları, istatistikler), envanter sayaçları, ayarlar (ayarlar Kotlin `DataStore`'da, oyun açılışında Rust'a aktarılır), dünya seed'i, kesilmiş ağaç zamanlayıcıları. Otomatik kayıt: level up, lobiye dönüş, 60 sn'de bir, uygulama arka plana alınınca.

## 7. Ses Listesi (tasarım)
Müzik: lobi (sakin akustik + synth, döngü), oyun gündüz ortam (kuş, rüzgâr), savaş katmanı (zombi yakındayken davul girer). Efekt: yumruk ×3 varyant, isabet ×3, kritik, ağaç vuruş ×3, ağaç yıkılma, odun toplama, ayak sesi çim/toprak, çalı hışırtı, zombi homurtu ×4, zombi saldırı, zombi ölüm, level up, UI tık/geri/hata/kilitli, XP toplama tını.
