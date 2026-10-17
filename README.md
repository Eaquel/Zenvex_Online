# Zenvex Online

2.5D pixel art, mobil öncelikli, online hayatta kalma / zombi oyunu.
(İlham: Zombix Online tarzı üstten 3/4 bakış. Varlıklar, isim ve marka tamamen özgün olacak.)

> **Durum:** Oynanabilir ilk prototip (MVP). Telefona kurulabilir APK her push'ta otomatik derlenir.
> 📱 **Kurulum:** GitHub → **Releases** → "Zenvex Online — son derleme" → `.apk` dosyasını indir ve kur.

## Belgeler

| # | Belge | İçerik |
|---|-------|--------|
| 00 | [Genel Bakış & MVP Kapsamı](docs/00_GENEL_BAKIS.md) | Vizyon, ilk sürümde ne var / ne yok, yol haritası |
| 01 | [Karakter Tasarımı](docs/01_KARAKTER.md) | İlk karakter, ölçüler, animasyon listesi, palet |
| 02 | [Arayüz (UI/UX)](docs/02_ARAYUZ.md) | Lobi, tüm ekranlar, tuşlar, HUD, joystickler, ayarlar |
| 03 | [Dünya & Oynanış](docs/03_DUNYA_OYNANIS.md) | Düz dünya, ağaç/bitki, zombi, yumruk, level & skill |
| 04 | [Teknik Mimari (Rust + Kotlin)](docs/04_TEKNIK_MIMARI.md) | Motor, katmanlar, klasör yapısı, JNI köprüsü, sunucu |
| 05 | [Yapay Zeka Görsel Promptları](docs/05_GORSEL_PROMPTLARI.md) | Her görsel için eksiksiz AAA kalite prompt |
| 06 | [Açık Sorular](docs/06_SORULAR.md) | Karar vermen gereken her şey |
| 07 | [Kararlar & Güncel Durum](docs/07_KARARLAR_VE_DURUM.md) | **Önce bunu oku:** neler yapıldı, neler çalışıyor, APK kurulumu |

## Teknoloji Özeti

- **Rust** (`rust/`) → oyun çekirdeği: dünya üretimi, oyuncu, zombi yapay zekası, yumruk savaşı, XP/level/yetenek + ileride sunucu
- **Kotlin** (`android/`) → Android uygulaması: Compose ile lobi/menüler/ayarlar, SurfaceView ile pixel-perfect oyun çizimi, joystickler, HUD
- Köprü: JNI (`jni` crate) — `libzenvex.so`
- **Görseller** (`tools/assetgen/`) → Python ile kodla üretilir (3D SDF modelden 8 yönlü pixel art). Yeniden üretmek: `python3 tools/assetgen/build_assets.py`
- **CI** (`.github/workflows/android.yml`) → Rust testleri + APK derleme + Releases sayfasına yükleme

## Klasörler

```
android/          Kotlin Android uygulaması (Gradle)
rust/             zenvex_core (oyun kuralları) + zenvex_jni (Android köprüsü)
tools/assetgen/   görsel üreticiler (Python + numpy + Pillow)
tools/ai_gen.py   isteğe bağlı yapay zeka görsel üretici (konsept/arka plan)
art/concept/      yapay zeka konsept görselleri (lobi/giriş arka planı bunlardan pixel'leştirildi)
art/preview/      üretilen sprite'ların büyütülmüş önizlemeleri
docs/             tasarım belgeleri
```
