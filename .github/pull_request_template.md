## What and why

## How it was tested

- [ ] `cargo clippy --workspace --all-targets -- -D warnings` and `cargo test --workspace`
- [ ] Kotlin compiles without warnings; UI snapshots updated if the UI changed
- [ ] New visible text goes through `tools/i18n` (all languages complete)
- [ ] Server-side checks for anything that grants items, gold or gems
