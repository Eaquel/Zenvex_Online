# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · **Русский** · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Bahasa Indonesia](README.id.md) · [العربية](README.ar.md)

Онлайн-игра о выживании среди зомби в 2.5D пиксель-арте, рассчитанная в первую очередь на телефоны. Собирай ресурсы, создавай предметы, сражайся, вступай в клан и захватывай базы. Ядро игры написано на Rust, приложение для Android — на Kotlin (Jetpack Compose), онлайн-часть работает на Supabase и необязательном игровом сервере на Rust.

> При каждом push собирается устанавливаемый APK: GitHub → **Releases** → «Zenvex Online — последняя сборка».

## Скриншоты

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Сборка

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Языки

Интерфейс доступен на турецком (исходный), английском, испанском, бразильском португальском, русском, немецком, французском, итальянском, индонезийском и арабском. Турецкий текст служит ключом перевода; `tools/i18n/extract.py` пересобирает каталог из кода, а `tools/i18n/merge.py` добавляет переводы. Модульный тест падает, если в языке не хватает строки или сломан заполнитель `{0}`.

Подробности, список документов и структуру папок смотри в [основном README](README.md).
