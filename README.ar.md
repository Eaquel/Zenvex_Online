# Zenvex Online

[English](README.md) · [Türkçe](README.tr.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Bahasa Indonesia](README.id.md) · **العربية**

لعبة بقاء ضد الزومبي عبر الإنترنت بفن البكسل 2.5D موجّهة للهواتف أولًا. اجمع المواد واصنع الأغراض وقاتل وانضم إلى عشيرة واستولِ على القواعد. نواة اللعبة مكتوبة بلغة Rust وتطبيق أندرويد بلغة Kotlin (Jetpack Compose)، وتعمل الخدمات عبر الإنترنت على Supabase وخادم لعبة Rust اختياري.

> يُبنى ملف APK قابل للتثبيت عند كل دفعة: GitHub ← **Releases** ← «Zenvex Online — أحدث إصدار».

## لقطات الشاشة

| | |
|---|---|
| ![](art/screenshots/lobi.png) | ![](art/screenshots/oyun.png) |
| ![](art/screenshots/yetenekler.png) | ![](art/screenshots/ayarlar.png) |

## البناء

```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
cd android && ./gradlew :app:assembleDebug
```

## اللغات

الواجهة متوفرة بالتركية (المصدر) والإنجليزية والإسبانية والبرتغالية البرازيلية والروسية والألمانية والفرنسية والإيطالية والإندونيسية والعربية. النص التركي هو مفتاح الترجمة؛ يعيد `tools/i18n/extract.py` بناء الفهرس من الشيفرة ويدمج `tools/i18n/merge.py` الترجمات. يفشل اختبار الوحدة إذا نقصت لغةً عبارة أو انكسر عنصر نائب `{0}`.

للتفاصيل وقائمة المستندات وبنية المجلدات راجع [الملف الرئيسي README](README.md).
