# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · **Italiano** · [Bahasa Indonesia](README.id.md) · [العربية](README.ar.md)

Un gioco online di sopravvivenza agli zombie in pixel art 2.5D, pensato per il mobile. Raccogli, crea, combatti, unisciti a un clan e conquista basi. Il nucleo del gioco è scritto in Rust, l'app Android in Kotlin (Jetpack Compose) e i servizi online usano Supabase e un server di gioco Rust opzionale.

> A ogni push viene compilato un APK installabile: GitHub → **Releases** → "Zenvex Online — ultima build".

## Schermate

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Compilazione

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Lingue

L'interfaccia è in turco (origine), inglese, spagnolo, portoghese del Brasile, russo, tedesco, francese, italiano, indonesiano e arabo. Il testo turco è la chiave di traduzione; `tools/i18n/extract.py` ricostruisce il catalogo dal codice e `tools/i18n/merge.py` unisce le traduzioni. Un test unitario fallisce se a una lingua manca una stringa o si rompe un segnaposto `{0}`.

Per i dettagli, l'elenco dei documenti e la struttura delle cartelle, vedi il [README principale](README.md).
