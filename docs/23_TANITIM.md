# Tanıtım Paketi

İki parça var: **(1) tanıtım görseli** (yapay zekâyla senin üreteceğin; aşağıda hazır komutlar) ve **(2) tanıtım videosu** (yapay zekâ video değil: oyunun kendi motorundan alınmış gerçek oynanış, oyunun kendi sesleri ve Türkçe seslendirme; betik hazır).

---

## 1. Tanıtım görseli (mağaza kapağı / sosyal medya)

Oyunun gerçek görünümüne sadık kal: yukarıdan bakan 2.5B dünya, 3B kahramanlar, kor ve kıvılcım efektleri, gün batımında yıkık şehir. Aşağıdaki komutlar İngilizce yazıldı (görsel üreticiler en iyi onunla çalışır). Yazı yerleştirmeyi yapay zekâya bırakma; ürettiğin görselin üstüne logoyu kendin koy (`ZENVEX ONLINE`, oyundaki `display_bold` yazı tipi).

### A. Ana görsel (16:9, mağaza öne çıkan görsel 1024×500 ve YouTube küçük resmi için)

```
Cinematic key art for a mobile zombie survival game called "Zenvex Online". A lone hooded survivor in dark
layered armor stands in the center foreground on a cracked asphalt street at sunset, holding a huge curved
black scythe with a glowing red blade edge, mid-swing, a crescent-shaped red shockwave trailing behind it.
Around the hero a glowing circular rune-seal aura of pale blue and white light spins on the ground.
Background: a ruined city in orange-red dusk haze, burning cars, drifting embers and sparks in the air,
a few silhouetted zombies lurching toward the hero, a hulking armored brute zombie on the right, a rocket
trail and fireball explosion far in the back left. Slightly elevated three-quarter camera (like a top-down
action RPG), strong rim light on the hero from the sunset, volumetric smoke, deep teal-and-orange color
grading, high contrast, painterly detailed digital illustration, sharp focus on the hero, wide composition
with empty dark space on the left third for a logo. No text, no watermark, no UI, no extra limbs.
```

**Negatif (varsa):** `text, logo, watermark, UI, HUD, blurry, low quality, extra fingers, deformed hands, cartoon, anime, 3d render plastic look`

### B. Dikey afiş (9:16, telefon ekranı / hikâye paylaşımı)

```
Vertical movie-poster style key art for the mobile game "Zenvex Online". Top: a giant glowing molten-orange
titan silhouette (a hulking armored colossus with cracks of lava) rising behind a burning ruined city skyline
at dusk. Middle: a hooded survivor hero seen from a slightly elevated angle, rocket launcher on shoulder,
runic blue-white aura ring at the feet, embers swirling. Bottom: a motorcycle racing on desert asphalt with
a dust trail, zombies running behind, a clan of three silhouetted allies far behind. Rich orange, red and
teal palette, dramatic backlight, smoke, sparks, painterly high-detail game key art. Leave the top 15% and
bottom 20% calmer for title and store badges. No text, no watermark.
```

### C. Dört kahraman afişi (kare, 1:1)

```
Square character lineup art for a zombie survival game: four distinct survivor heroes standing side by side on
a ruined street at golden hour, each with a unique weapon (scythe, katana, rocket launcher, assault rifle),
hooded cloaks, tactical armor, glowing rune-seal circles under their feet, embers in the air, cinematic rim
lighting, consistent painterly style, full body visible, even spacing, dark moody background with a warm
orange horizon. No text.
```

### Kullanım ipuçları
- Üç görseli de **2–4 farklı tohumla** üret; en tutarlı olanı seç.
- Yüz/el bozuklukları için aynı komutu "close crop" ile değil, **inpainting** ile düzelt.
- Mağaza için son boyutlar: öne çıkan görsel **1024×500**, simge **512×512** (mevcut simge kullanılır), ekran görüntüleri en az 2 adet (aşağıdaki video karelerinden alınabilir).
- Görseli ürettiğinde `art/promo/` klasörüne koy; video betiği ana görseli istersen açılış kartı olarak da kullanabilir (`INTRO` içindeki kart yerine).

---

## 2. Tanıtım videosu (oyun motorundan, gerçek oynanış)

**Bu video yapay zekâ ile üretilmedi.** Kareler oyunun kendi Rust/Kotlin çizim motorundan alınır (`GameShotsTest.promoClip`, temiz mod: arayüz yok); sesler `assets/sfx` ve `assets/music` içindeki oyun sesleridir; seslendirme yerel bir metin-okuma modeliyle (Piper, Türkçe) üretilir.

### Üretim
```bash
# 1) oyun karelerini al (yaklaşık 20 dk, bilgisayarda cihazsız)
(cd rust && cargo build -p zenvex_jni --release)
(cd android && ./gradlew :app:recordPaparazziDebug -x cargoBuild --tests '*GameShotsTest.z0*')

# 2) sesi indir ve videoyu birleştir
pip install piper-tts numpy scipy pillow
curl -L -o tr.onnx      https://huggingface.co/rhasspy/piper-voices/resolve/main/tr/tr_TR/dfki/medium/tr_TR-dfki-medium.onnx
curl -L -o tr.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/tr/tr_TR/dfki/medium/tr_TR-dfki-medium.onnx.json
python3 tools/promo/trailer.py --voice tr.onnx      # → art/promo/zenvex_tanitim.mp4 + seslendirme.srt
```

### Akış (≈ 37 sn, 1080p, 30 kare/sn)
| Süre | Sahne | Seslendirme |
|---|---|---|
| 0:00 | ZENVEX ONLINE açılış kartı, gök gürültüsü | "Dünya çöktü." |
| 0:03 | **Tırpan:** üç vuruşluk kombo, hilal şok dalgası | "Elinde bir tırpan var. Önünde ise bir sürü." |
| 0:07 | **Roketatar:** roket çıkar, patlama, yeni roket takılır | "Roketle yık." |
| 0:12 | **Araçlar:** çölde motosiklet, toz izi | "Çölü motorunla uçarak geç." |
| 0:16 | **Üret:** balta ve kazma, kıymık ve kıvılcım | "Kes. Kır. Üret." |
| 0:19 | **Titan:** dev düşman, çok yüksek seviye silah | "Titan uyandı." |
| 0:24 | **Donmuş Liman:** buz silahı | "Soğuk, seni durdurmaz." |
| 0:28 | **Güçlen:** rün mühürlü aura | "Güçlen. Klanını topla." |
| 0:32 | Kapanış kartı | "Zenvex Online. Hayatta kal. Güçlen. Üssünü savun." |

Sahne listesi, süreler ve cümleler `tools/promo/trailer.py` başındaki `SCENES`, `INTRO`, `OUTRO` değişkenlerinde; değiştirip yeniden çalıştırman yeter. Ses efektleri karelerdeki hareket yoğunluğuna göre (vuruş, patlama) otomatik yerleştirilir.

### Kendi sesinle / başka seslendirmeyle
`art/promo/seslendirme.srt` dosyası metin ve zamanlamayı içerir. Kendi sesini kaydedersen `--voice` vermeden çalıştır (yalnız müzik ve efekt), sonra kaydını bir video düzenleyicide altyazıya göre hizala. Piper sesi robotsu bulursan daha doğal bir Türkçe ses için ElevenLabs/Azure gibi bir hizmetten aynı cümleleri üretip `vo*.wav` olarak betiğin `tmp` klasörüne koyabilir veya düzenleyicide birleştirebilirsin.

### Mağaza için ek
- Video süresi 30 sn ile 2 dk arası olmalı (Google Play); bu video uygun.
- Play Console'a bir YouTube bağlantısı olarak yüklenir; "listeli değil" yayınlayabilirsin.
- İlk 3 sn'de logo ve ilk oynanış vurgusu var; sessiz izleyenler için altyazı dosyası hazır.

---

## 3. Reels / TikTok / Shorts reklam videosu (9:16, ≈14 sn)

Instagram'daki oyun reklamları aynı formülle çalışır: **ilk saniyede en güçlü an (kanca)** → hızlı mekanik gösterimi → sonda çağrı kartı. Logo girişi, yavaş açılış yok. Bizim hattımız bunu yapay zekâ video olmadan, gerçek oynanışla yapar:

```bash
PIPER_PY=<piper python> python3 tools/promo/reel.py --voice tr.onnx     # → art/promo/zenvex_reel.mp4 (1080×1920) + .srt
```

| Süre | Sahne | Yazı | Ses |
|---|---|---|---|
| 0:00 | **Kanca:** roket, patlama, sürü | TEK ROKET. BÜTÜN SÜRÜ. | patlama + "Tek roket. Bütün sürü." |
| 0:02 | Tırpan kombosu | TIRPAN | vuruş sesleri |
| 0:05 | Motosiklet | TAM GAZ | motor |
| 0:07 | Titan | TİTAN UYANDI | Titan sesi |
| 0:10 | Buz silahı | SEN DE KATIL | |
| 0:11 | Çağrı kartı | ZENVEX ONLINE · Test sürümü açık | |

Her kesimde ekran sarsıntısı, hızlı beyaz parlama ve "vuuş" sesi var; yazılar ekranın üst üçte birinde ve büyük (telefonda okunur). Ek öneriler: yayınlarken ilk karede başlık yaz ("Tek roket, bütün sürü"), altyazıyı `.srt` ile ekle, kaydırma sonunda kısa bir soru sor ("Hangi silahla başlarsın?"). Sahne listesi `reel.py` başındaki `SCENES` içinde.

### Yapay zekâ video araçları ne zaman?
Gerçek oynanış yetmeyen yerde (sinematik karakter tanıtımı, hikâye sahnesi) Higgsfield/Kling/Runway gibi araçlarla 3–5 sn'lik görselden-videoya klipler üretip aynı kurguya ekleyebilirsin; ancak bunlar **oyunun gerçek görünümü değildir**: mağaza kurallarına ve oyuncu beklentisine uygun olarak "gerçek oynanış" kliplerini mutlaka yanında kullan. İstersen `docs/23_TANITIM.md` Bölüm 1'deki görsel komutlarını bu araçlara referans olarak verirsin.
