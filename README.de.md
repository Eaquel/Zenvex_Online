# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · **Deutsch** · [Français](README.fr.md) · [Italiano](README.it.md) · [Bahasa Indonesia](README.id.md) · [العربية](README.ar.md)

Ein mobiles Online-Zombie-Überlebensspiel in 2.5D-Pixelart. Sammle, stelle her, kämpfe, tritt einem Clan bei und erobere Basen. Der Spielkern ist in Rust geschrieben, die Android-App in Kotlin (Jetpack Compose); die Online-Dienste laufen auf Supabase und einem optionalen Rust-Spielserver.

> Bei jedem Push wird ein installierbares APK gebaut: GitHub → **Releases** → „Zenvex Online — letzter Build“.

## Screenshots

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Build

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Sprachen

Die Oberfläche gibt es auf Türkisch (Quelle), Englisch, Spanisch, brasilianischem Portugiesisch, Russisch, Deutsch, Französisch, Italienisch, Indonesisch und Arabisch. Der türkische Text ist der Übersetzungsschlüssel; `tools/i18n/extract.py` baut den Katalog aus dem Code neu auf und `tools/i18n/merge.py` fügt Übersetzungen ein. Ein Unit-Test schlägt fehl, wenn einer Sprache ein Text fehlt oder ein `{0}`-Platzhalter kaputt ist.

Details, Dokumentenliste und Ordnerstruktur findest du in der [Haupt-README](README.md).
