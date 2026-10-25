# Contributing

Thanks for helping with Zenvex Online. Short version: small focused changes, tests and lints green, no warnings.

## Setup

- Rust stable (see `rust/rust-toolchain.toml` if present), `cargo-ndk` for Android builds
- Android SDK 37 (`platforms;android-37.0`), build-tools 36, NDK 27, JDK 21
- Python 3.11+ for the tools under `tools/`

## Before you push

```bash
(cd rust && cargo fmt --check && cargo clippy --workspace --all-targets -- -D warnings && cargo test --workspace)
cd android && CI=true ./gradlew :app:compileDebugKotlin -x cargoBuild   # Kotlin warnings are errors on CI
```

If you change the UI, record and review the Paparazzi snapshots (`:app:recordPaparazziDebug`).

## Code style

- Match the surrounding code: naming, comment density, idiom. `.editorconfig` covers whitespace.
- Game rules live in `rust/zenvex_core`; the Kotlin side draws and relays input. Do not duplicate rules in Kotlin.
- Anything that grants items, gold or gems must be validated on the server (Supabase functions), never trusted from the client.

## Translations

Turkish source text is the translation key. After adding or changing a visible string:

```bash
python3 tools/i18n/extract.py            # rebuilds catalog.json and lists what is missing per language
python3 tools/i18n/merge.py <lang> file  # lines of "<catalog index> <translation>"; placeholders {0} {1} must be kept
```

`I18nTest` fails when a language misses a string or alters a placeholder. Prefer short, natural wording; the UI uses a
pixel font and buttons are narrow.

## Commits and pull requests

Imperative subject line, body explaining the why. One logical change per commit. Fill in the pull request template.
