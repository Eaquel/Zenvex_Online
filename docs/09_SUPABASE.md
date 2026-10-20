# Supabase (çevrimiçi altyapı)

Proje: **zenvex-online** (Frankfurt, ücretsiz plan) — `https://blvstsvmvmdydyrnecpo.supabase.co`.
Uygulama bu adresi ve herkese açık (anon) anahtarı `android/app/build.gradle.kts` içinde varsayılan olarak kullanır.
Başka bir projeye geçmek için GitHub → Settings → Secrets → Actions altına `SB_URL` ve `SB_KEY` eklenebilir.

## Veritabanı
`supabase/migrations/20261018_zenvex_core.sql` zenvex-online projesine **kuruldu**. Yeni bir projeye kurmak için:
Supabase paneli → **SQL Editor** → dosyanın tamamı → **Run**.

## Neler nerede
| Özellik | Nasıl |
|---|---|
| Hesap | `zx_register`, `zx_login`, `zx_change_key`, `zx_rename` — ID + bcrypt'li anahtar |
| Karakter kaydı | `zx_save` / `zx_load` — sunucu başına (şimdilik tek sunucu: `n1`); oyun 60 sn'de bir ve çıkışta yükler |
| Sunucu listesi | `zx_servers` (son 3 dk aktif oyuncu sayısı), `zx_top` (sıralama) |
| Sohbet geçmişi | `zx_chat_post` / `zx_chat_recent` (canlı akış Realtime yayınıyla) |
| Posta | `zx_mail_list`, `zx_mail_claim` (hediye altın/eşya), `zx_mail_send` |
| Klan | `zx_clans`, `zx_clan_create`, `zx_clan_join`, `zx_clan_leave`, `zx_clan_members` |
| Canlı dünya | Realtime kanalı `zx-<sunucu>`: presence (kim bağlı), `p` konum, `z` zombiler, `hit`, `kill`, `pd`, `c` sohbet, `treq`/`tr` takas |

Tablolarda RLS açık ve politika yok: istemci tablolara doğrudan erişemez, yalnızca `zx_*` fonksiyonlarını çağırır.

## Ücretsiz plan sınırları (özet)
200 eşzamanlı bağlantı, saniyede ~100 Realtime mesajı. Oyun konumu 5 Hz, zombileri 5 Hz yollar; bu, aynı sunucuda
birkaç düzine oyuncuya kadar yeter. Daha fazlası için ücretli plan ya da kendi oyun sunucumuz gerekir.

## Google ile giriş

Akış:
1. Uygulama Android Credential Manager ile Google hesabını seçtirir ve Google ID jetonunu alır.
2. Jeton `zx-google` edge function'ına gönderilir.
3. Function Google'ın `tokeninfo` ucuyla jetonu doğrular: alıcı (`aud`), veren (`iss`), süre ve e-posta doğrulaması kontrol edilir.
4. Ardından yalnızca `service_role`'ün çağırabildiği `zx_google_login` ya da `zx_google_link` fonksiyonu çalışır.
5. Girişte hesap yoksa açılır. Her girişte yeni anahtar üretilir ve cihaza kaydedilir.

**Tek seferlik kurulum (Google Cloud Console → APIs & Services → Credentials):**

1. **OAuth consent screen**: External, uygulama adı "Zenvex Online".
2. **Create credentials → OAuth client ID → Android**:
   - Paket: `com.zenvex.online`
   - SHA-1: `75:9C:94:A2:22:72:F6:3C:C5:F0:3F:86:35:8D:84:F9:10:95:DF:E3`. Bu, `android/keystore/zenvex-dev.jks` dosyasının, yani CI'daki imza anahtarının SHA-1'i.
3. **Create credentials → OAuth client ID → Web application**. Bu istemcinin kimliği (`…apps.googleusercontent.com`) gerekli.
4. Web istemci kimliğini iki yere yaz:
   - **Supabase → Edge Functions → Secrets**: `GOOGLE_WEB_CLIENT_ID`.
   - **Uygulama**: `android/app/build.gradle.kts` içindeki `GOOGLE_WEB_CLIENT_ID` varsayılanı, ya da GitHub Secret `GOOGLE_WEB_CLIENT_ID`. Kimlik gizli değildir.

Kurulum tamamlandı (20 Ekim 2026): proje "Zenvex Online", yayın durumu "In production", Web istemci kimliği
`145695347343-afg8kme2oe2ir1hlie4f36pi1f7ilf3m.apps.googleusercontent.com` (zx-google ve build.gradle.kts varsayılanı).
Gizlilik/ana sayfa bağlantıları: `zx-legal` function'ı (`?p=home|privacy|terms`).
