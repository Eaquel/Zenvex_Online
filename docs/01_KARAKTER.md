# 01 — Karakter Tasarımı

## 1. İlk Karakter: "Kaan" (çalışma adı — değiştirilebilir)
| Özellik | Değer |
|---------|-------|
| Rol | Sıradan bir hayatta kalan; salgından sonra ormanda uyanan genç adam |
| Yaş görünümü | 20'ler |
| Siluet | Sade, okunur: büyük kafa/beden oranı ~1:2.5 (chibi'ye yakın ama çocuksu değil) |
| Saç | Kısa, dağınık, koyu kahve; alnına düşen tutam (pixelde tanınma işareti) |
| Üst | Hardal sarısı kapüşonlu sweatshirt, kollar dirseğe kadar sıvalı |
| Alt | Koyu lacivert kot, dizde yırtık |
| Ayakkabı | Kirli beyaz spor ayakkabı |
| Aksesuar | Ellerde bez sargı (yumruk dövüşçüsü olduğunu anlatır), sırtta küçük kahverengi sırt çantası |
| Renk okunurluğu | Hardal üst → yeşil zeminde ve yeşilimsi zombiler arasında anında seçilir |

> Neden böyle? Mobil ekranda karakter ~90–130 px yüksekliğinde görünecek. Tek baskın renk (hardal) + koyu alt + açık ayakkabı = 3 net blok → her arka planda okunur.

## 2. Teknik Ölçüler
| Öğe | Değer |
|-----|-------|
| Kare (frame) boyutu | **48×48 px** |
| Karakterin görünür boyu | ~38 px (≈1.2 karo) |
| Pivot / ayak noktası | (24, 44) — y-sıralama ve gölge buna göre |
| Gölge | Ayrı sprite, 20×8 px yarı saydam elips |
| Yönler | 4 yön çizilir: **Aşağı, Yukarı, Yan (sağ)**, Sol = Sağ'ın aynası → 3 çizim seti. (İleride 8 yön opsiyonu, bkz. Sorular) |
| Çizgi (outline) | 1 px, renkli koyu outline (saf siyah değil, #1b1c26) |
| Işık yönü | Sol-üstten |
| Kare hızı | Animasyon başına 8–12 FPS (oyun 60 FPS) |

## 3. Animasyon Listesi (her biri × 3 yön)
| # | Animasyon | Kare | FPS | Döngü | Not |
|---|-----------|------|-----|-------|-----|
| 1 | Idle (bekleme, nefes) | 4 | 6 | ✔ | Omuz hafif iner-kalkar, göz kırpma 1/4 olasılık |
| 2 | Walk (yürüme) | 6 | 10 | ✔ | Joystick %0–60 |
| 3 | Run (koşma) | 6 | 12 | ✔ | Joystick %60–100, toz parçacığı |
| 4 | Punch A (sol yumruk) | 4 | 14 | ✘ | Kombo 1 |
| 5 | Punch B (sağ yumruk) | 4 | 14 | ✘ | Kombo 2 |
| 6 | Punch C (güçlü aparkat) | 6 | 12 | ✘ | Kombo 3, ekran sarsıntısı |
| 7 | Chop (ağaca vuruş) | 5 | 12 | ✘ | Punch C'nin yatay varyantı, talaş parçacığı |
| 8 | Hurt (hasar alma) | 2 | 10 | ✘ | Beyaz flaş 1 kare |
| 9 | Death (ölüm) | 6 | 8 | ✘ | Son kare yerde kalır |
| 10 | Level Up | 6 | 10 | ✘ | Yumruk havaya, ışık sütunu efekti ayrı |
| 11 | Spawn / uyanma | 6 | 8 | ✘ | Lobi → oyuna geçişte |
| 12 | Lobi pozu (idle_lobby) | 8 | 6 | ✔ | Lobide büyük önizleme için, sadece Aşağı yön |

Toplam ≈ 63 kare × 3 yön ≈ **190 kare**. Sprite sheet: her satır bir animasyon, her sütun bir kare (bkz. 05 promptları).

### Animasyon Durum Makinesi
```
Idle ⇄ Walk ⇄ Run
  │  (saldırı joysticki)
  ▼
Punch A → (0.35 sn içinde tekrar) → Punch B → Punch C → Idle
  │ hedef ağaç ise → Chop
Herhangi → Hurt → önceki
Herhangi → Death (HP=0)
```
- Hareket ederken yumruk: alt gövde yürümeye devam etmez; MVP'de yumruk anında %40 yavaşlama (basit, tek sprite seti).

## 4. Lobi İçin Ek Görseller
- Büyük karakter portresi (HUD sol üst, 64×64 px pixel)
- Lobi önizleme (idle_lobby, 3× ölçekli gösterim)
- Kart illüstrasyonu (karakter seçim ekranı, yüksek çözünürlüklü pixel art 256×384)

## 5. Ana Palet (taslak — 48 renk)
| Grup | Renkler (hex) |
|------|---------------|
| Outline / gölge | #1b1c26 #2b2d3c #3e4157 |
| Ten | #f2c6a0 #d99a74 #a8684a #6e3f2e |
| Hardal | #ffd25e #e8a93a #b9772a #7a4a1f |
| Lacivert | #4a6fa5 #33507e #23365a #172238 |
| Çim / bitki | #b6e36b #7fc44a #4f9a3a #2f6b33 #1d4428 |
| Toprak | #d9a066 #b07848 #7d5233 #4f3322 |
| Ağaç gövde | #8a5a3c #66402b #45291d |
| Taş | #c9c9c9 #9a9aa6 #6c6c7c #45465a |
| Zombi ten | #a7c48a #7f9f6a #5a7650 #3c5040 |
| Zombi kıyafet | #8a6fa0 #5f4a78 #3d2f52 |
| Kan / hasar | #e04848 #a02c3c #5e1a2a |
| UI vurgu | #ffe9a8 #5ee0ff #3aa0ff #ff7a3a |
| Beyaz/açık | #ffffff #eef2f0 #c7d3cf |

> Promptlar ile üretilen her görsel bu palete **quantize** edilecek (Aseprite / Pyxel Edit / "Palette Swap" scripti).
