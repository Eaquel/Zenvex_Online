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
