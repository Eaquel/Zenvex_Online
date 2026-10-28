# Görsel Prompt'ları (V19) — Yönetici Paneli, 3 Sandık ve Kalan Simgeler

Bu belge eski prompt belgelerinin (15–27) yerini alır; onlardan üretilip oyuna giren her şey çıkarıldı, yalnız hâlâ
bekleyenler burada. Hepsinde ortak kurallar:

- Düz **#00FF00** yeşil zemin (arka plan görselleri hariç), yazı / harf / rakam yok, filigran yok.
- En az **1024×1024**; nesne kadrajın ~%80'i, ortada, tek nesne.
- Dosya adı = aşağıdaki tablodaki ad. Hepsini tek zip olarak gönder; yeşil zemini temizleyip yerleştiririm.

**Stil satırı (her prompt'un sonuna `[STİL]` yerine):**
```
high quality detailed pixel-art style game icon, crisp edges, bold dark outline, rich hue-shifted shading, light from the
top-left, dark post-apocalyptic survival game, premium mobile game art, centered single object, no text, no letters,
no numbers, no watermark, no frame, no shadow on the background, flat solid #00FF00 green background
```

---

## 1. Kişisel üs sandıkları (oyunda yalnız bu 3 sandık var)

Dünyadaki ganimet sandıkları da artık bu üç görseli kullanıyor (eski `crate`, `crate_mil`, `supply_crate` kaldırıldı).
Her sandık için **3 dosya**: çanta simgesi, dünyada kapalı hâli, dünyada açık hâli. Dünya görselleri **izometrik, hafif
yukarıdan 3/4 açı**, zemine oturan alt kenar düz, yere düşen gölge YOK (gölgeyi oyun çizer).

| Dosya | Ne |
|---|---|
| `chest_wood` | Ahşap Sandık — çanta simgesi (düz önden 3/4) |
| `chest_wood_world` | Ahşap Sandık — dünyada, kapalı |
| `chest_wood_world_open` | Ahşap Sandık — dünyada, kapak açık, içi boş ve loş |
| `chest_iron` | Demir Sandık — çanta simgesi |
| `chest_iron_world` / `chest_iron_world_open` | Demir Sandık — dünyada kapalı / açık |
| `chest_military` | Askerî Sandık — çanta simgesi |
| `chest_military_world` / `chest_military_world_open` | Askerî Sandık — dünyada kapalı / açık |

**Ahşap Sandık** (en basit, 16 yuva)
```
a small sturdy wooden storage chest made of weathered pine planks, rope handles on the sides, rusty iron corner brackets,
a simple wooden latch, a few scratches and nail heads, warm brown wood. [STİL]
```
**Demir Sandık** (orta, 24 yuva)
```
a heavy reinforced storage chest with dark wood panels fully banded with riveted grey steel straps, a big iron padlock
hasp on the front, scuffed metal edges with light rust, cold steel highlights. [STİL]
```
**Askerî Sandık** (en büyük, 32 yuva)
```
a large olive-green military footlocker / ammo crate with stencil-style blank panel (no letters), steel latches and
reinforced corners, carrying handles, worn paint chips showing metal underneath, a small yellow hazard stripe on the lid
edge. [STİL]
```
Açık hâller için prompt'un sonuna ekle: `lid open, empty dark interior, seen from slightly above`.

---

## 2. Yönetici paneli (yeni düzen: solda menü, sağda kartlar)

Panel şu an kodla çizilen hâliyle eksiksiz çalışıyor; bu görseller gelince otomatik kullanılır.

### 2.1 Arka plan (`admin_bg`, 2400×1080 yatay, yeşil zemin YOK)
```
dark moody command center interior of a post-apocalyptic survivor outpost at night, wall of old CRT monitors showing
green radar maps, cables and sandbags, a large wall map with pins, warm amber desk lamp light on the right, cold blue
monitor glow on the left, heavy depth of field, lots of empty dark space in the center for interface panels,
painterly pixel-art style, no text, no letters, no people, 2400x1080
```

### 2.2 Amblem (`admin_emblem`, kare)
```
an administrator emblem badge: a golden heater shield with a stylized crowned eagle head, small gemstone at the top,
engraved steel rim, glowing faint gold aura. [STİL]
```

### 2.3 Menü simgeleri (10 dosya, **64×64'te okunmalı**, aynı aile: tek ana renk altın-amber + beyaz parlama, kalın hat)

| Dosya | Bölüm | Konu |
|---|---|---|
| `admin_nav_home` | Genel Bakış | a small radar dashboard with a gauge needle |
| `admin_nav_players` | Oyuncular | two survivor head-and-shoulder silhouettes with a magnifying glass |
| `admin_nav_reports` | Bildirimler | a red warning flag on a pole with an exclamation shape (no letters) |
| `admin_nav_support` | Destek | a speech bubble with a small wrench inside |
| `admin_nav_economy` | Ekonomi | a stack of gold coins next to a cyan diamond and a tiny up-down chart |
| `admin_nav_mail` | Posta | a sealed envelope with a red wax seal |
| `admin_nav_events` | Etkinlik | a burning torch with a golden trophy behind it |
| `admin_nav_maint` | Bakım | a crossed wrench and screwdriver over a gear |
| `admin_nav_hero` | Karakterim | a survivor helmet with a small star badge |
| `admin_nav_world` | Dünya | a small globe with a skull marker pin |

Her satırdaki konu + `[STİL]`; ek olarak `icon readable at 64x64, simple bold shapes`.

---

## 3. Hâlâ bekleyen simgeler (eski belgelerden taşındı, isteğe bağlı)

### 3.1 Yetenek ağacı aileleri (128×128'de okunur)
| Dosya | Aile | Konu |
|---|---|---|
| `sk_damage` | Hasar | a clenched armored fist, red glow |
| `sk_crit` | Kritik | a golden lightning bolt striking a skull crack |
| `sk_weapon` | Silah | two crossed blades with an orange ember edge |
| `sk_life` | Can | a green heart with a small leaf sprouting |
| `sk_defense` | Savunma | a steel-blue heater shield with a rivet ring |
| `sk_agility` | Çeviklik | a winged boot trailing cyan wind lines |

Anahtar düğümler için aynı simgenin altın sekizgen çerçeveli hâli: `sk_<aile>_key`.

### 3.2 Harita işaretleri (128×128)
`map_npc` (yeşil yuvarlak içinde insan başı-omuz), `map_place` (mavi elmas taş), `map_titan` (kızıl kafatası),
`map_quest` (turuncu ünlem kalkanı), `map_merchant` (altın kese), `map_you` (mavi ok başı).

### 3.3 Öğretici eli (`tut_hand` yeni hâli, isteğe bağlı)
Şu anki `tut_hand` kullanılıyor; daha net bir dokunma eli istersen:
```
a cartoon pointing hand cursor with index finger extended downward, white glove with a dark outline, small tap ripple
circles under the fingertip, slight golden rim light. [STİL]
```

---

## 4. Gelen ve yerleşenler (V19)
- 18 etkinlik eşyası (silah / yüzük / kolye, 6 etkinlik) piksel stiliyle değişti.
- `gems_5` (açık elmas sandığı), `ic_respawn` (yeniden doğma), `cloud_tile` (sınır bulutu dokusu) yerleşti.
- Kaldırılanlar: etkinlik kaskları, Tanrı Pelerini, genel sandık simgesi, eski dünya sandıkları.
