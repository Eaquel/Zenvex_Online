# Zenvex Online

[English](README.md) · **Türkçe** · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Bahasa Indonesia](README.id.md) · [العربية](README.ar.md)

Mobil öncelikli, çevrimiçi 2.5D piksel sanatı zombi hayatta kalma oyunu. Malzeme topla, üret, savaş, klana katıl ve üsleri ele geçir. Oyun çekirdeği Rust, Android uygulaması Kotlin (Jetpack Compose) ile yazıldı; çevrimiçi altyapı Supabase ve isteğe bağlı Rust oyun sunucusudur.

> Her push'ta kurulabilir bir APK derlenir: GitHub → **Releases** → "Zenvex Online — son derleme".

## Ekran görüntüleri

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Derleme

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Diller

Arayüz Türkçe (kaynak), İngilizce, İspanyolca, Brezilya Portekizcesi, Rusça, Almanca, Fransızca, İtalyanca, Endonezyaca ve Arapça sunulur. Türkçe metin çeviri anahtarıdır; `tools/i18n/extract.py` kataloğu koddan çıkarır, `tools/i18n/merge.py` çevirileri dil dosyalarına ekler. Bir dilde eksik metin ya da bozuk `{0}` yer tutucusu olursa birim test başarısız olur.

Ayrıntılar, belge listesi ve klasör yapısı için [ana README](README.md).
