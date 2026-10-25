# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · **Español** · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Bahasa Indonesia](README.id.md) · [العربية](README.ar.md)

Juego de supervivencia zombi en línea, en pixel art 2.5D y pensado para móviles. Recoge, fabrica, lucha, únete a un clan y captura bases. El núcleo del juego está escrito en Rust, la app de Android en Kotlin (Jetpack Compose) y los servicios en línea usan Supabase y un servidor de juego Rust opcional.

> En cada push se compila un APK instalable: GitHub → **Releases** → «Zenvex Online — última compilación».

## Capturas

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Compilación

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Idiomas

La interfaz está en turco (origen), inglés, español, portugués de Brasil, ruso, alemán, francés, italiano, indonesio y árabe. El texto turco es la clave de traducción; `tools/i18n/extract.py` reconstruye el catálogo desde el código y `tools/i18n/merge.py` fusiona las traducciones. Una prueba unitaria falla si a un idioma le falta una cadena o rompe un marcador `{0}`.

Para los detalles, la lista de documentos y la estructura de carpetas, consulta el [README principal](README.md).
