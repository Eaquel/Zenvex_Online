# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · **Bahasa Indonesia** · [العربية](README.ar.md)

Game bertahan hidup melawan zombi secara online dalam pixel art 2.5D yang mengutamakan ponsel. Kumpulkan bahan, buat item, bertarung, gabung klan, dan rebut markas. Inti game ditulis dengan Rust, aplikasi Android dengan Kotlin (Jetpack Compose), dan layanan online memakai Supabase serta server game Rust opsional.

> APK yang bisa dipasang dibuat di setiap push: GitHub → **Releases** → "Zenvex Online — build terbaru".

## Tangkapan layar

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Build

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Bahasa

Antarmuka tersedia dalam bahasa Turki (sumber), Inggris, Spanyol, Portugis Brasil, Rusia, Jerman, Prancis, Italia, Indonesia, dan Arab. Teks Turki adalah kunci terjemahan; `tools/i18n/extract.py` membangun ulang katalog dari kode dan `tools/i18n/merge.py` menggabungkan terjemahan. Tes unit gagal jika suatu bahasa kekurangan kalimat atau merusak penampung `{0}`.

Untuk detail, daftar dokumen, dan struktur folder, lihat [README utama](README.md).
