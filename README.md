# Zenvex Online

2.5D pixel art, mobil öncelikli, online hayatta kalma / zombi oyunu.
(İlham: Zombix Online tarzı üstten 3/4 bakış. Varlıklar, isim ve marka tamamen özgün olacak.)

> **Durum:** Planlama ve tasarım aşaması. Henüz kod yok.

## Belgeler

| # | Belge | İçerik |
|---|-------|--------|
| 00 | [Genel Bakış & MVP Kapsamı](docs/00_GENEL_BAKIS.md) | Vizyon, ilk sürümde ne var / ne yok, yol haritası |
| 01 | [Karakter Tasarımı](docs/01_KARAKTER.md) | İlk karakter, ölçüler, animasyon listesi, palet |
| 02 | [Arayüz (UI/UX)](docs/02_ARAYUZ.md) | Lobi, tüm ekranlar, tuşlar, HUD, joystickler, ayarlar |
| 03 | [Dünya & Oynanış](docs/03_DUNYA_OYNANIS.md) | Düz dünya, ağaç/bitki, zombi, yumruk, level & skill |
| 04 | [Teknik Mimari (Rust + Kotlin)](docs/04_TEKNIK_MIMARI.md) | Motor, katmanlar, klasör yapısı, JNI köprüsü, sunucu |
| 05 | [Yapay Zeka Görsel Promptları](docs/05_GORSEL_PROMPTLARI.md) | Her görsel için eksiksiz AAA kalite prompt |
| 06 | [Açık Sorular](docs/06_SORULAR.md) | Karar vermen gereken her şey |

## Teknoloji Özeti

- **Rust** → oyun motoru (render, ECS, fizik, animasyon, joystick/HUD, oyun mantığı) + ileride sunucu
- **Kotlin** → Android kabuğu (Activity, lobi/menü ekranları Jetpack Compose, ayarlar, hesap, bildirim, mağaza)
- Köprü: JNI (`jni` crate) — Kotlin lobi ⇄ Rust oyun
