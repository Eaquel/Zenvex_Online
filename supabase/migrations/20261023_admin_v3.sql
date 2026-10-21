-- Yönetici v3: tek yönetici hesabı (ID 0), hesap silme / geri alma, anahtar sıfırlama, silme talepleri listesi.
-- ID 0 hesabının anahtarı bu dosyada YOK: anahtar yalnız bcrypt özeti olarak veritabanına yazıldı (uygulama: 20.10.2026).

alter table public.accounts add column if not exists deleted_at timestamptz;
alter table public.accounts add column if not exists deleted_reason text not null default '';

-- tek yönetici: ID 0 dışındaki herkesin yetkisi kalkar
update public.accounts set is_admin = false where id <> 0 and is_admin;

-- sahipsiz klan işareti 0 yerine -1 (0 artık yönetici hesabı)
update public.clans set owner = -1 where owner = 0;
create or replace function public.zx_clan_leave(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); c bigint := a.clan_id; heir bigint;
begin
  update public.accounts set clan_id = null, clan_rank = 0, clan_muted_until = null where id = a.id returning * into a;
  if c is not null then
    insert into public.clan_log (clan_id, text) values (c, a.name || ' klandan ayrıldı');
    if (select owner from public.clans where id = c) = a.id then
      select id into heir from public.accounts where clan_id = c order by clan_rank desc, clan_joined_at nulls last, created_at limit 1;
      update public.clans set owner = coalesce(heir, -1) where id = c;
      if heir is not null then update public.accounts set clan_rank = 3 where id = heir; end if;
    end if;
  end if;
  return public._zx_profile(a);
end $$;

-- giriş: silinmiş hesaplar giremez
create or replace function public._zx_auth(p_id bigint, p_key text) returns public.accounts
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts;
begin
  select * into a from public.accounts where id = p_id;
  if a.id is null or a.key_hash <> crypt(p_key, a.key_hash) then
    raise exception 'Hesap kimliği veya anahtar hatalı' using errcode = '28000';
  end if;
  if a.deleted_at is not null then
    raise exception 'Bu hesap silindi' using errcode = '28000';
  end if;
  update public.accounts set last_seen = now() where id = p_id;
  return a;
end $$;

-- yönetici yetkisi yalnız ID 0
create or replace function public._zx_admin(p_id bigint, p_key text) returns public.accounts
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if a.id <> 0 or not a.is_admin then raise exception 'Bu işlem için yönetici yetkisi gerekli'; end if;
  return a;
end $$;
revoke all on function public._zx_admin(bigint, text) from public, anon, authenticated;

-- arama: silinmiş / silme talebi bilgisi de gelir
create or replace function public.zx_admin_find(p_id bigint, p_key text, p_name text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id', t.id, 'name', t.name, 'level', public._zx_level(t.id), 'kills', public._zx_kills(t.id),
      'last_seen', t.last_seen, 'banned_until', t.banned_until, 'ban_reason', t.ban_reason, 'vip_until', t.vip_until,
      'admin', t.is_admin, 'clan', (select name from public.clans where id = t.clan_id),
      'deleted', t.deleted_at is not null, 'delete_req', t.delete_requested_at is not null))
    from (select * from public.accounts where name ilike '%' || trim(p_name) || '%' or id::text = trim(p_name) order by last_seen desc limit 20) t), '[]'::jsonb);
end $$;

-- hesap silme (geri alınabilir): giriş kapanır, klandan çıkar, Google bağı kalkar
create or replace function public.zx_admin_delete(p_id bigint, p_key text, p_target bigint, p_reason text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts; c bigint;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if t.id = 0 or t.is_admin then raise exception 'Yönetici hesabı silinemez'; end if;
  if t.deleted_at is not null then raise exception 'Hesap zaten silinmiş'; end if;
  c := t.clan_id;
  update public.accounts set deleted_at = now(), deleted_reason = left(coalesce(p_reason, ''), 120),
    clan_id = null, clan_rank = 0, google_sub = null where id = t.id;
  if c is not null and (select owner from public.clans where id = c) = t.id then
    update public.clans set owner = coalesce((select id from public.accounts where clan_id = c
      order by clan_rank desc, clan_joined_at nulls last, created_at limit 1), -1) where id = c;
  end if;
  perform public._zx_log(t.id, 'admin', a.name || ' hesabı sildi: ' || coalesce(p_reason, ''));
  return t.name || ' hesabı silindi';
end $$;

create or replace function public.zx_admin_restore(p_id bigint, p_key text, p_target bigint) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts;
begin
  update public.accounts set deleted_at = null, deleted_reason = '', delete_requested_at = null
  where id = p_target and deleted_at is not null returning * into t;
  if t.id is null then raise exception 'Silinmiş hesap bulunamadı'; end if;
  perform public._zx_log(t.id, 'admin', a.name || ' hesabı geri getirdi');
  return t.name || ' hesabı geri getirildi';
end $$;

-- anahtar sıfırlama: yeni anahtar yöneticiye döner (oyuncuya iletilir)
create or replace function public.zx_admin_reset_key(p_id bigint, p_key text, p_target bigint) returns text
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts; k text := public._zx_key();
begin
  if p_target = 0 then raise exception 'Yönetici anahtarı buradan değişmez'; end if;
  update public.accounts set key_hash = crypt(k, gen_salt('bf', 8)), key_changed_at = now() where id = p_target returning * into t;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  perform public._zx_log(t.id, 'admin', a.name || ' anahtarı sıfırladı');
  return k;
end $$;

-- silme talepleri
create or replace function public.zx_admin_delete_requests(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id', t.id, 'name', t.name, 'level', public._zx_level(t.id),
      'at', (extract(epoch from t.delete_requested_at) * 1000)::bigint) order by t.delete_requested_at)
    from public.accounts t where t.delete_requested_at is not null and t.deleted_at is null), '[]'::jsonb);
end $$;

grant execute on function public.zx_admin_find(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_admin_delete(bigint, text, bigint, text) to anon, authenticated;
grant execute on function public.zx_admin_restore(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_admin_reset_key(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_admin_delete_requests(bigint, text) to anon, authenticated;

-- oyuncu dosyası: silinme durumu da gelir
create or replace function public.zx_admin_player(p_id bigint, p_key text, p_target bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  return public._zx_profile(t) || jsonb_build_object(
    'created_at', t.created_at, 'last_seen', t.last_seen, 'banned_until', t.banned_until, 'ban_reason', t.ban_reason,
    'email', t.email, 'google', t.google_sub is not null,
    'activity', coalesce((select jsonb_agg(jsonb_build_object('k', kind, 't', text, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
        from (select * from public.activity where account_id = t.id order by id desc limit 120) x), '[]'::jsonb),
    'chat', coalesce((select jsonb_agg(jsonb_build_object('ch', server, 't', text, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
        from (select * from public.chat where account_id = t.id order by id desc limit 120) x), '[]'::jsonb),
    'dm', coalesce((select jsonb_agg(jsonb_build_object('to', (select name from public.accounts where id = case when from_id = t.id then to_id else from_id end),
          'out', from_id = t.id, 't', text, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
        from (select * from public.dm where from_id = t.id or to_id = t.id order by id desc limit 80) x), '[]'::jsonb),
    'saves', coalesce((select jsonb_agg(jsonb_build_object('server', server, 'level', level, 'kills', kills, 'updated', updated_at,
          'gold', (case when data ~ '^\s*\{' then (data::jsonb ->> 'gold') end),
          'gems', (case when data ~ '^\s*\{' then (data::jsonb ->> 'gems') end),
          'slots', (case when data ~ '^\s*\{' then (data::jsonb #> '{inv,slots}') end)))
        from public.characters where account_id = t.id), '[]'::jsonb),
    'deleted', t.deleted_at is not null, 'deleted_reason', t.deleted_reason, 'delete_req', t.delete_requested_at is not null,
    'pending', (select count(*) from public.admin_actions where account_id = t.id and done_at is null));
end $$;

-- profil: Google bağlı mı (Ayarlar → Hesap doğru durumu gösterir)
CREATE OR REPLACE FUNCTION public._zx_profile(a accounts)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select jsonb_build_object(
    'id', a.id, 'name', a.name, 'admin', a.is_admin, 'avatar', a.avatar, 'frame', a.frame,
    'vip', a.vip_until is not null and a.vip_until > now(), 'vip_until', a.vip_until,
    'clan', (select public._zx_clan_json(c) || jsonb_build_object('owner', c.owner = a.id, 'rank', a.clan_rank,
               'rank_name', public._zx_rank_name(a.clan_rank), 'muted_until', a.clan_muted_until)
             from public.clans c where c.id = a.clan_id),
    'chars', coalesce((select jsonb_object_agg(server, level) from public.characters where account_id = a.id), '{}'::jsonb),
    'level', public._zx_level(a.id), 'kills', public._zx_kills(a.id), 'tags', public._zx_tags(a.id),
    'unread', (select count(*) from public.mail m where m.account_id = a.id and not m.read and not m.deleted and (m.favorite or m.created_at > now() - interval '30 days')),
    'friend_req', (select count(*) from public.friends f where f.b = a.id and not f.accepted and not f.removed),
    'dm_unread', (select count(*) from public.dm d where d.to_id = a.id and not d.read),
    'invites', (select count(*) from public.clan_invites i where i.account_id = a.id),
    'key_changed_at', a.key_changed_at,
    'google', a.google_sub is not null)
$function$;
