# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · **Français** · [Italiano](README.it.md) · [Bahasa Indonesia](README.id.md) · [العربية](README.ar.md)

Un jeu de survie zombie en ligne, en pixel art 2.5D, pensé pour le mobile. Récoltez, fabriquez, combattez, rejoignez un clan et capturez des bases. Le cœur du jeu est écrit en Rust, l'application Android en Kotlin (Jetpack Compose), et les services en ligne reposent sur Supabase et un serveur de jeu Rust facultatif.

> Un APK installable est compilé à chaque push : GitHub → **Releases** → « Zenvex Online — dernière version ».

## Captures d'écran

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Compilation

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Langues

L'interface existe en turc (source), anglais, espagnol, portugais du Brésil, russe, allemand, français, italien, indonésien et arabe. Le texte turc sert de clé de traduction ; `tools/i18n/extract.py` reconstruit le catalogue depuis le code et `tools/i18n/merge.py` fusionne les traductions. Un test unitaire échoue si une langue manque une chaîne ou casse un espace réservé `{0}`.

Pour les détails, la liste des documents et l'arborescence, consultez le [README principal](README.md).
