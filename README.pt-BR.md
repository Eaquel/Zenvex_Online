# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · [Español](README.es.md) · **Português (Brasil)** · [Русский](README.ru.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Bahasa Indonesia](README.id.md) · [العربية](README.ar.md)

Jogo online de sobrevivência zumbi em pixel art 2.5D, pensado para celular. Colete, fabrique, lute, entre num clã e capture bases. O núcleo do jogo é escrito em Rust, o app Android em Kotlin (Jetpack Compose) e os serviços online usam Supabase e um servidor de jogo Rust opcional.

> A cada push é gerado um APK instalável: GitHub → **Releases** → "Zenvex Online — última versão".

## Capturas de tela

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## Compilação

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## Idiomas

A interface está em turco (origem), inglês, espanhol, português do Brasil, russo, alemão, francês, italiano, indonésio e árabe. O texto em turco é a chave de tradução; `tools/i18n/extract.py` recria o catálogo a partir do código e `tools/i18n/merge.py` mescla as traduções. Um teste de unidade falha se um idioma deixar de ter uma frase ou quebrar um marcador `{0}`.

Para detalhes, a lista de documentos e a estrutura de pastas, veja o [README principal](README.md).
