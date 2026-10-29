-- V20 düzeltmeleri.
--  1. zx_photos: hesap kimlikleriyle profil fotoğrafları (sohbet, arkadaş, klan ve sıralama listeleri yalnız resim
--     numarası taşıdığı için fotoğraf değişince görünmüyordu). Fotoğraflar zaten herkese açık profilde görünür.

create or replace function public.zx_photos(p_ids bigint[]) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_object_agg(a.id::text, a.photo), '{}'::jsonb)
  from public.accounts a
  where a.id = any (p_ids[1:50]) and a.deleted_at is null and coalesce(a.photo, '') <> ''
$$;
grant execute on function public.zx_photos(bigint[]) to anon, authenticated;
