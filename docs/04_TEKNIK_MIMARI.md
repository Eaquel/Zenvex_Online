# 04 — Teknik Mimari (Rust + Kotlin)

## 1. Görev Paylaşımı
| Katman | Dil | Sorumluluk |
|--------|-----|-----------|
| Android kabuğu | **Kotlin** | `MainActivity`, Jetpack Compose ile Splash/Giriş/Lobi/alt ekranlar/Ayarlar, `DataStore` ayarlar, izinler, titreşim, yaşam döngüsü, ileride Google giriş & ödeme & bildirim |
| Oyun ekranı | **Rust** (Kotlin `GameActivity` içinde) | Render, ECS, oyun mantığı, animasyon, dokunma → joystick, HUD, ses, kayıt |
| Ortak çekirdek | **Rust** | Oyun kuralları (`zenvex_core`) — hem istemci hem ileride sunucu kullanır |
| Sunucu (F7+) | **Rust** | `tokio` + `quinn` (QUIC/UDP) veya WebSocket, otoriter sunucu, `sqlx` + PostgreSQL |

> Lobi neden Kotlin/Compose? Menüler metin, liste, kaydırma, erişilebilirlik ve sistem entegrasyonu ağırlıklı — Compose bunda çok hızlı. Oyun içi HUD ve joystickler ise **Rust'ta**, çünkü her karede dünya ile senkron olmalı ve gecikmesiz olmalı.
> Alternatif: Lobi de Rust'ta (Bevy UI / egui). Tek kod tabanı, PC'de de aynı. → Sorular listesinde.

## 2. Motor Seçimi (öneri)
| Seçenek | Artı | Eksi | Öneri |
|---------|------|------|-------|
| **Bevy** (ECS, wgpu) | Hazır ECS, sprite, animasyon, ses, Android desteği, büyük topluluk | APK büyük (~30–50 MB), sürümler arası API kırılması | ✅ **MVP için öneri** |
| Macroquad | Çok hafif, basit, Android kolay | ECS yok, büyüyünce dağınık | Prototip için |
| Özel (wgpu + hecs + winit) | Tam kontrol, küçük | Çok iş | Sonra gerekirse |

Bevy seçilirse sürüm sabitlenir (`Cargo.lock` + belirli minor sürüm), yükseltmeler planlı yapılır.

### Kullanılacak crate'ler (taslak)
`bevy` · `bevy_ecs_tilemap` (zemin) · `bevy_rapier2d` veya basit kendi AABB çarpışması · `serde`, `ron`, `bincode` · `noise` (dünya üretimi) · `rand`/`rand_pcg` (seed) · `fluent` (çeviri) · `jni` (Kotlin köprüsü) · `bevy_kira_audio` (ses) · `leafwing-input-manager` (PC test) · `tracing`.

## 3. Klasör Yapısı (plan)
```
Zenvex_Online/
├─ docs/                      # bu belgeler
├─ art/                       # kaynak görseller (.aseprite, .png), palet .gpl
│   ├─ characters/ zombies/ tiles/ nature/ ui/ fx/ marketing/
├─ rust/
│   ├─ Cargo.toml             # workspace
│   ├─ crates/
│   │   ├─ zenvex_core/       # saf oyun kuralları: stat, XP formülü, skill, hasar (motor bağımsız, test edilebilir)
│   │   ├─ zenvex_game/       # Bevy oyun: plugins (world, player, zombie, combat, hud, input, camera, save, audio)
│   │   ├─ zenvex_android/    # cdylib: android_main + JNI fonksiyonları
│   │   ├─ zenvex_desktop/    # PC'de hızlı test için bin (klavye/fare + sanal joystick)
│   │   └─ zenvex_server/     # (F7+)
│   └─ assets/                # oyunun yüklediği atlaslar, ses, font, .ron veri tabloları
└─ android/
    ├─ settings.gradle.kts
    └─ app/src/main/
        ├─ kotlin/online/zenvex/
        │   ├─ MainActivity.kt          # Compose: lobi akışı
        │   ├─ GameActivity.kt          # androidx.games GameActivity → Rust
        │   ├─ ui/ (splash, login, lobby, character, skills, inventory, shop, settings, components/)
        │   ├─ data/ (SettingsRepository-DataStore, SaveBridge)
        │   └─ bridge/NativeBridge.kt   # external fun ... (JNI)
        ├─ jniLibs/arm64-v8a/libzenvex_android.so  # cargo-ndk çıktısı
        └─ res/ (strings TR/EN, font, ikon)
```

## 4. Kotlin ⇄ Rust Köprüsü
- Derleme: `cargo ndk -t arm64-v8a -t armeabi-v7a -o android/app/src/main/jniLibs build --release`, Gradle görevine bağlanır.
- Lobi → Oyun: `MainActivity` "OYNA" → `GameActivity` başlatır; Intent extra olarak JSON: `{ worldSeed, settings{...}, saveSlot }`.
- Rust ↔ Kotlin çağrıları (minimum yüzey):
  | Yön | Fonksiyon | Amaç |
  |-----|-----------|------|
  | K→R | `nativeApplySettings(json)` | Ayar değişince (duraklat menüsünden) |
  | K→R | `nativeLoadSave() : String` / lobi önizleme verisi | Lobide level/XP göstermek |
  | R→K | `onReturnToLobby(statsJson)` | Oyundan çıkış |
  | R→K | `vibrate(ms, amp)` | Haptik |
  | R→K | `showToast(text)` | "Yakında" mesajı vb. |
- Kayıt dosyası tek kaynak: Rust yazar; Kotlin lobi okumak için Rust'ın küçük `nativeReadProfile()` fonksiyonunu çağırır (format tek yerde).

## 5. Render Tasarımı
- Sanal çözünürlük: yükseklik 360 px, genişlik oranla. Önce düşük çözünürlüklü offscreen hedefe çiz → tamsayı ölçekle ekrana (pixel-perfect). Kamera konumu tam pixele yuvarlanır (titreşmesin).
- Katmanlar (z): 0 zemin tilemap → 1 decal → 2 gölgeler → 3 y-sıralı nesneler (karakter, zombi, ağaç gövdeleri, çalı) → 4 ağaç tepeleri (saydamlık mantığı) → 5 parçacık → 6 ışık/bloom → 7 dünya-UI (HP barı, hasar sayısı) → 8 HUD (ekran çözünürlüğünde, keskin).
- Atlaslar: karakter/zombi/doğa/UI ayrı 2048×2048 atlas, `TexturePacker` veya `bevy_asset_loader` + `.ron` animasyon tanımları.
- Rüzgâr shader: WGSL, `offset.x = sin(time*f + phase + world.x*k) * amp * uv_height^2`.
- Kalite ayarları doğrudan ECS kaynağı (`Res<GraphicsSettings>`), değişince sistemler anında uyar.
- Görünmeyen (ekran dışı + kenar payı) nesnelerde animasyon/AI güncellemesi azaltılır (LOD tick).

## 6. Giriş (Input)
- Rust tarafı `Touches` olaylarını alır; ekranı sol/sağ bölgeye ayırır; her bölge bir parmak ID'si "sahiplenir" (çoklu dokunma güvenli).
- Joystick çıktısı: `Vec2` (−1..1) + güç; ölü bölge + hassasiyet eğrisi (ayardan) uygulanır.
- HUD düzeni `.ron` profil olarak kaydedilir (konum normalize 0–1, boyut, saydamlık).
- PC test: WASD = hareket, fare yönü + sol tık = saldırı.

## 7. Performans Bütçesi (orta-alt cihaz, 60 FPS = 16.6 ms)
| Kalem | Bütçe |
|-------|-------|
| Oyun mantığı + AI | ≤ 3 ms |
| Render (CPU) | ≤ 4 ms |
| Draw call | ≤ 60 (atlas + batching) |
| Aktif varlık | ≤ 1500 sprite görünür, ≤ 25 zombi |
| Bellek | ≤ 350 MB |

## 8. Test & Kalite
- `zenvex_core` saf Rust → birim testleri (XP formülü, hasar, skill etkileri).
- `cargo clippy`, `cargo fmt`, Kotlin `ktlint`. GitHub Actions: Rust test + Android debug APK derleme.
- Hata raporu: ileride Firebase Crashlytics (soru listesinde).

## 9. Online'a Hazırlık (şimdiden alınacak kararlar)
- Oyun kuralları `zenvex_core`'da, deterministik ve motor bağımsız → sunucu aynı kodu kullanır.
- Her varlığın kalıcı `EntityId`'si (u64), komutlar "niyet" olarak modellenir (`MoveIntent`, `AttackIntent`) → ileride ağ üstünden gönderilir.
- Kayıt formatı sürümlü (`version: u32`).
