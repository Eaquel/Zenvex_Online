# Yayın ve Dağıtım Rehberi

Bu belge Zenvex Online'ı test kullanıcılarına ve mağazaya çıkarmak için gereken her şeyi tek yerde toplar.

## 1. Sürüm numarası
- GitHub Actions her derlemede `0.4.<çalıştırma-no>-beta` adını ve aynı numarada `versionCode` üretir (`android.yml`).
- Yerelde: `VERSION_NAME=0.4.0-beta VERSION_CODE=1 ./gradlew assembleRelease`.

## 2. Android derlemesi (otomatik)
`main` dalına her gönderimde **Android APK** iş akışı çalışır:
1. Rust çekirdeği `cargo ndk` ile 3 mimari için derlenir (arm64-v8a, armeabi-v7a, x86_64).
2. `assembleRelease` + `bundleRelease` + `assembleDebug`. CI'da Kotlin uyarısı derlemeyi durdurur.
3. APK, **Releases → Zenvex Online — son derleme** sayfasına yüklenir; `.aab` Actions çıktısında durur (Play Console'a yüklenecek dosya).
4. Emülatör işi (API 28 ve 35) uygulamayı açar ve ekranları gezer; görüntüler "ui-shots" çıktısıdır.

### Mağaza imzası (bir kez yapılır)
Depodaki `android/keystore/zenvex-dev.jks` **yalnızca geliştirme/test** anahtarıdır (herkesin bildiği şifreyle). Mağazaya çıkacak sürüm ayrı, gizli bir anahtarla imzalanmalıdır:

```bash
keytool -genkeypair -v -keystore zenvex-store.jks -alias zenvex -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 zenvex-store.jks   # çıktıyı kopyala
```
GitHub → Settings → Secrets and variables → Actions → şu 4 secret'ı ekle:

| Secret | İçerik |
|---|---|
| `STORE_KEYSTORE_B64` | yukarıdaki base64 çıktısı |
| `STORE_PASSWORD` | anahtar deposu şifresi |
| `STORE_KEY_ALIAS` | `zenvex` |
| `STORE_KEY_PASSWORD` | anahtar şifresi |

Secret varsa yayın sürümü otomatik onunla imzalanır; yoksa geliştirme anahtarı kullanılır (test APK'sı eskisinin üstüne kurulabilsin diye). **Anahtar dosyasını ve şifreleri kaybetme**; Play Console'da "Play Uygulama İmzalama"yı açarsan Google asıl anahtarı saklar, sen yalnızca yükleme anahtarını korursun.

> Not: anahtar değişince test kullanıcıları eski sürümü silip yeniden kurmalıdır (imza uyuşmazlığı).

## 3. Sunucu (Rust oyun sunucusu)
- **Oyun Sunucusu** iş akışı x64 ve ARM64 için `zenvex-server-<mimari>.tar.gz` üretir (clippy sıfır uyarı, testler, 100 botluk yük testi).
- Kurulum (Ubuntu 22.04/24.04, Oracle Cloud Ampere dahil): arşivi sunucuya aç, `sudo ./install.sh`. Betik servisi (`systemd`) ve HTTPS'yi (Caddy, `<ip>.sslip.io`) kurar; yalnızca Supabase `service_role` anahtarını sorar.
- Güncelleme: `sudo ./update.sh` (yeni `zenvex_server` dosyasıyla). Sağlık: `curl 127.0.0.1:7778`.
- Ayrıntı: `docs/teknik/OYUN_SUNUCUSU.md`.

## 4. Supabase
- Şema: `supabase/migrations` (sırayla uygulanır). Edge fonksiyonları: `zx-billing`, `zx-google`, `zx-legal`, `zx-support`.
- İstemci yalnızca yayınlanabilir (anon) anahtarı taşır; tüm yazma işlemleri `zx_*` RPC'leri üzerinden, sunucu doğrulamalıdır.
- Yönetici hesabı: kimlik 0; anahtarın yalnızca bcrypt özeti veritabanındadır.
- Google ile giriş için OAuth "Web application" istemci kimliği `GOOGLE_WEB_CLIENT_ID` secret'ıyla değiştirilebilir (varsayılan koddadır). **İstemci gizli anahtarı uygulamaya konmaz.**

## 5. Mağaza kontrol listesi (Google Play)
- [ ] Uygulama adı: **Zenvex Online**; paket: `com.zenvex.online`; hedef SDK 36, en düşük 26 (Android 8).
- [ ] 16 KB sayfa hizası: yerel kütüphaneler sıkıştırılmadan paketlenir (Android 15+ gereği).
- [ ] Gizlilik politikası ve kullanım koşulları: uygulama içinde (`zx-legal`) ve Play Console'da bağlantı olarak.
- [ ] Veri güvenliği formu: hesap (e-posta/Google), oyun verisi, kullanıcı kimliği; satın alma Play Billing ile.
- [ ] İçerik derecelendirmesi: zombi şiddeti (kurgusal, kansız piksel/3B) → 12+/Teen önerilir.
- [ ] Ekran görüntüleri: `art/promo/` (tanıtım videosu ve görseli `docs/23_TANITIM.md`).
- [ ] Uygulama içi ürünler: elmas paketleri ve Battle Pass (Play Console'da tanımlanır, `zx-billing` doğrular).
- [ ] Kapalı test (en az 12 test kullanıcısı, 14 gün) → üretim. Test notları: `docs/TEST_NOTLARI.md`.

## 6. Yayın öncesi son kontrol
```bash
(cd rust && cargo test --workspace && cargo clippy --workspace --all-targets -- -D warnings)
(cd android && ./gradlew :app:testDebugUnitTest)   # arayüz ve oyun ekranı testleri (Paparazzi) ayrıca: recordPaparazziDebug
```
