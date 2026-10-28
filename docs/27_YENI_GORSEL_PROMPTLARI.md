# Yeniden Üretilecek Görseller — Prompt'lar (V18)

Önceki mağaza görselleri (docs/25) yerleşti; aşağıdakiler eksik ya da yenilenecek. Hepsinde ortak kurallar docs/25 §0 ile aynı:
düz **#00FF00** zemin, yazı/rakam yok, en az 1024×1024, nesne kadrajın ~%80'i, aynı stil satırı.

**Stil satırı (her prompt'un sonuna):**
```
high quality detailed pixel-art style game icon, crisp edges, bold dark outline, rich hue-shifted shading, light from the
top-left, dark post-apocalyptic survival game, premium mobile game art, centered single object, no text, no letters,
no numbers, no watermark, no frame, no shadow on the background, flat solid #00FF00 green background
```

## 1. Eksik mağaza görseli (1 dosya: `gems_5`)
Şu an `gems_4` sandığı + yığından geçici birleşim kullanılıyor.
```
an open iron-bound treasure chest heaped with glowing cyan-blue cut diamonds spilling over the front, three-quarter view,
sparkling. [STİL]
```

## 2. Yeniden doğma simgesi (`ic_respawn`, kare, saydam ya da yeşil zemin)
Şu an kodla çizilen yedek kullanılıyor (yeşil dönüş oku + kalp).
```
a revive icon for a survival game death screen: a glowing crimson heart surrounded by a circular emerald-green rotating
arrow, small golden sparkle at the top right, soft green aura. [STİL]
```

## 3. Yetenek ağacı simgeleri (isteğe bağlı; ağaç şu an kodla çizilen vektör simgelerle kaliteli çalışıyor)
Altı aile için birer simge, her biri **128×128'de okunur**, aynı çizim ailesi (kalın koyu hat, tek ana renk + beyaz parlama):
| Dosya | Aile | Prompt konusu |
|---|---|---|
| `sk_damage` | Hasar (kırmızı) | a clenched armored fist, red glow |
| `sk_crit` | Kritik (sarı) | a golden lightning bolt striking a skull crack |
| `sk_weapon` | Silah (turuncu) | two crossed blades with an orange ember edge |
| `sk_life` | Can (yeşil) | a green heart with a small leaf sprouting |
| `sk_defense` | Savunma (mavi) | a steel-blue heater shield with a rivet ring |
| `sk_agility` | Çeviklik (camgöbeği) | a winged boot trailing cyan wind lines |
Anahtar (keystone) düğümler için aynı simgenin **altın çerçeveli** hâli: `sk_<aile>_key` (dikenli sekizgen altın çerçeve içinde).

## 4. Harita işaretleri (isteğe bağlı, 6 dosya, 128×128)
`map_npc` (yeşil yuvarlak içinde insan başı-omuz), `map_place` (mavi elmas taş), `map_titan` (kızıl kafatası), `map_quest`
(turuncu ünlem kalkanı), `map_merchant` (altın kese), `map_you` (mavi ok başı). Stil: aynı piksel stil satırı, yeşil zemin.

## 5. Bölge sınırı bulutu (`border_cloud`, 512×512, **kusursuz döşenir**, yeşil zemin YOK)
Şu an kodla üretilen bulutlar kullanılıyor.
```
seamless tileable stylized cloud sea texture for a game map border, soft lavender-grey clouds with lighter tops and darker
shaded undersides, painterly, top-down view, no text, no characters, 512x512
```

## 6. Teslim
Dosya adı = kodun aradığı ad. Zip olarak gönder; yeşil zemini temizleyip `android/app/src/main/assets/ui/` altına koyarım.
