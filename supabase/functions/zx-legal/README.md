# zx-legal

Herkese açık ana sayfa, gizlilik politikası ve kullanım şartları sayfaları (Google OAuth izin ekranı ve Play Store için).
Kimlik doğrulaması kapalıdır (verify_jwt = false); yalnızca statik metin döner.

- https://blvstsvmvmdydyrnecpo.supabase.co/functions/v1/zx-legal?p=home
- https://blvstsvmvmdydyrnecpo.supabase.co/functions/v1/zx-legal?p=privacy
- https://blvstsvmvmdydyrnecpo.supabase.co/functions/v1/zx-legal?p=terms

Supabase varsayılan alan adında HTML'i düz metne çevirdiği için sayfalar düz metin olarak sunulur.
Kaynak: index.ts (Supabase'e MCP ile yayınlandı; yerelde `supabase functions deploy zx-legal --no-verify-jwt`).
