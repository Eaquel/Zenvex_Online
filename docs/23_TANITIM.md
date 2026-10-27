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

## 2. Tanıtım videoları (yatay fragman ve dikey Reels): gerçek oynanış, gerçek oyun sesleri

**Yapay zekâ video yok.** Kareler oyunun kendi çizicisinden (Rust + Kotlin, cihazsız) alınır; kahraman bir oyuncu gibi oynar: koşar, sürüyü
toplar, takla atar, roket ve tırpanla kırar, motosiklete biner, Titan'la dövüşür. Kareler **gerçek oyun arayüzüyle** (can çubuğu, mini harita, joystick,
takla tuşu, hasar sayıları, öfke bildirimi) çizilir: izleyen bunun oynayacağı oyun olduğunu görür.

**Ses de gerçek oyunun sesidir:** kare üretilirken oyunun çaldığı her ses (`Audio.play`) ve karesi kaydedilir (`sfx.json`); kurgu aracı bu sesleri
olayın anına birebir yerleştirir (vuruş, patlama, takla, roket, seviye atlama…). Üstüne oyunun müziği, her kesimde "vuuş" ve kancada yükselen ses girer.

**Seslendirme isteğe bağlıdır ve varsayılan KAPALIdır.** Yerel metin-okuma (Piper) yapay duyulduğu için kullanılmıyor; reklamların çoğu zaten yazı + ses efektiyle çalışır.
Gerçek bir insan sesi istersen: `art/promo/seslendirme_metni.md` metnini kaydet ve videoya ekle (aşağıda).

### Üretim
```bash
# 1) oyunun kendi motorundan kareler (cihazsız, bilgisayarda; yatay sonra dikey)
(cd rust && cargo build -p zenvex_jni --release)
cd android
PROMO=1 PROMO_HUD=1                    ./gradlew :app:recordPaparazziDebug -x cargoBuild --tests '*GameShotsTest.y0*'   # 2340×1080 → build/promo_hud/<klip>
PROMO=1 PROMO_HUD=1 PROMO_PORTRAIT=1   ./gradlew :app:recordPaparazziDebug -x cargoBuild --tests '*GameShotsTest.y0*'   # 1080×1920 → build/promo_v/<klip>
cd ..

# 2) kurgu (numpy, pillow, ffmpeg gerekir)
python3 tools/promo/edit.py --format wide     # → art/promo/zenvex_tanitim.mp4 (1080p, yatay: mağaza/YouTube)
python3 tools/promo/edit.py --format tall     # → art/promo/zenvex_reel.mp4    (1080×1920: Reels/TikTok/Shorts)
```
Klipler: `rocket` (RPG ile sürü ve tank), `horde` (tırpan, gece kampı), `chase` (çölde motosiklet), `titan` (Titan boss dövüşü, takla), `frost` (buz tırpanı).
Sahneler, süreler, yazılar ve kancanın seçimi `tools/promo/edit.py` başındaki `WIDE` ve `TALL` listelerindedir; başlangıç kareleri en yoğun an (en çok ses) olarak otomatik seçilir.

### Reklam kurgusu (Instagram/TikTok kalıbı)
1. **Kanca (ilk saniye):** roket patlaması, yavaş çekim, beyaz parlama. Logo girişi yok.
2. **Mekanik gösterimi (3–12 sn):** her sahne tek bir fiil: biç, kaç, dövüş, donmuş limanı temizle. Yazılar kısa, ekranın okunur yerinde (dikeyde oyun arayüzünün üstünde).
3. **Çağrı kartı (son 3 sn):** ZENVEX ONLINE + "Test sürümü açık" düğmesi.
4. **Ses:** müzik + oyunun kendi vuruş/patlama sesleri + kesim "vuuş"ları; son kartta seviye atlama sesi.

### Gerçek insan seslendirmesi (isteğe bağlı)
Kısa, tek nefeste cümleler (yatay): "Tek roket. Bütün sürü." · "Tırpanla biç." · "Çölde tam gaz." · "Titan uyandı." · "Soğukta bile durma." · "Zenvex Online. Şimdi test sürümünde."
Sesi kaydet (telefon yeter, sessiz oda), `tools/promo/edit.py` çıktısındaki `.srt` zamanlamasına göre bir düzenleyicide (CapCut, DaVinci) hizala.
Metin okuma kullanmak istersen: `pip install piper-tts` + Türkçe model + `--voice tr.onnx` (yapay duyulur; yalnız taslak içindir).

### Mağaza için
- Google Play video: 30 sn–2 dk, YouTube bağlantısıyla (listeli değil olabilir); yatay sürüm uygundur.
- Dikey sürümü Instagram Reels, TikTok ve YouTube Shorts'a yükle; ilk karede kısa başlık, kaydırma sonunda soru ("Hangi silahla başlarsın?") ve `.srt` altyazı.
- Videolar ≈ 20–30 MB olduğu için depoya girmez (`.gitignore`); Releases'a ya da bulut sürücüye yükle.

### Yapay zekâ video araçları ne zaman?
Gerçek oynanışın yetmediği yerde (hikâye sahnesi, sinematik karakter tanıtımı) Higgsfield, Kling, Runway gibi araçlarla 3–5 sn'lik klipler üretip kurguya eklenebilir.
Bunlar **oyunun gerçek görünümü değildir**; mağaza kuralları ve oyuncu beklentisi için yanlarında mutlaka gerçek oynanış klipleri bulunmalıdır.
