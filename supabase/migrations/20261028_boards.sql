-- V9 sıralamalar: 8 kategori (ilk 10 oyuncu), klanlar (ilk 10), unvanlar ("Canavar Avcısı #1"; yalnız ilk 10'a),
-- profil fotoğrafı, yasaklı profil, oynama süresi.

alter table public.characters add column if not exists titan_kills int not null default 0;
alter table public.characters add column if not exists soldier_kills int not null default 0;
alter table public.characters add column if not exists base_caps int not null default 0;
alter table public.characters add column if not exists dog_kills int not null default 0;
alter table public.characters add column if not exists crafted int not null default 0;
alter table public.accounts add column if not exists photo text not null default '';
alter table public.accounts add column if not exists title_kind text not null default '';

-- eski kayıtlardan sayaçları doldur
update public.characters set
  titan_kills = coalesce((data::jsonb -> 'kills_by' ->> 7)::int, 0),
  soldier_kills = coalesce((data::jsonb -> 'ev' ->> 'soldier_kills')::int, 0),
  base_caps = coalesce((data::jsonb -> 'ev' ->> 'base_caps')::int, 0),
  dog_kills = coalesce((data::jsonb -> 'ev' ->> 'dog_kills')::int, 0),
  crafted = coalesce((data::jsonb ->> 'crafted')::int, 0),
  play_seconds = greatest(play_seconds, coalesce((data::jsonb ->> 'play_time')::double precision, 0)::bigint)
where data ~ '^\s*\{';

create or replace function public._zx_cat_title(k text) returns text
language sql immutable as $$
  select case k when 'level' then 'Efsane' when 'kills' then 'Canavar Avcısı' when 'titan' then 'Titan Avcısı'
    when 'soldiers' then 'Savaş Lordu' when 'bases' then 'Fatih' when 'dog' then 'Sürü Lideri'
    when 'crafted' then 'Usta Zanaatkâr' when 'time' then 'Emektar' else k end
$$;

-- kategori değerleri (hesap başına, tüm sunucular); silinmiş hesaplar ve yönetici hariç
create or replace function public._zx_cat(k text) returns table(account_id bigint, v bigint, t timestamptz)
language sql stable security definer set search_path = public as $$
  select c.account_id,
    case k when 'level' then max(c.level)::bigint when 'kills' then sum(c.kills)::bigint when 'titan' then sum(c.titan_kills)::bigint
      when 'soldiers' then sum(c.soldier_kills)::bigint when 'bases' then sum(c.base_caps)::bigint when 'dog' then sum(c.dog_kills)::bigint
      when 'crafted' then sum(c.crafted)::bigint when 'time' then sum(c.play_seconds)::bigint else 0 end as v,
    min(c.updated_at) as t
  from public.characters c join public.accounts a on a.id = c.account_id
  where a.deleted_at is null and a.id <> 0
  group by c.account_id
$$;

-- oyuncunun şu an hak ettiği unvanlar (her kategoride ilk 10)
create or replace function public._zx_titles(p bigint) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare k text; r bigint; res jsonb := '[]'::jsonb;
begin
  foreach k in array array['level', 'kills', 'titan', 'soldiers', 'bases', 'dog', 'crafted', 'time'] loop
    select rk into r from (select s.account_id, rank() over (order by s.v desc, s.t) rk from public._zx_cat(k) s where s.v > 0) x where x.account_id = p;
    if r is not null and r <= 10 then
      res := res || jsonb_build_array(jsonb_build_object('kind', k, 'rank', r, 'name', public._zx_cat_title(k) || ' #' || r));
    end if;
  end loop;
  return res;
end $$;

-- seçili unvan (hâlâ ilk 10'daysa), yoksa null
create or replace function public._zx_title(a public.accounts) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare r bigint;
begin
  if a.title_kind = '' then return null; end if;
  select rk into r from (select s.account_id, rank() over (order by s.v desc, s.t) rk from public._zx_cat(a.title_kind) s where s.v > 0) x where x.account_id = a.id;
  if r is null or r > 10 then return null; end if;
  return jsonb_build_object('kind', a.title_kind, 'rank', r, 'name', public._zx_cat_title(a.title_kind) || ' #' || r);
end $$;

-- sıralama: ilk 10 (yasaklılar görünür ama işaretli)
create or replace function public.zx_board(p_kind text) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('rank', x.rk, 'id', a.id, 'name', a.name, 'avatar', a.avatar, 'frame', a.frame, 'photo', a.photo,
      'v', x.v, 'clan', (select name from public.clans where id = a.clan_id), 'title', public._zx_title(a),
      'banned', a.banned_until is not null and a.banned_until > now(), 'vip', a.vip_until is not null and a.vip_until > now()) order by x.rk), '[]'::jsonb)
  from (select s.account_id, s.v, rank() over (order by s.v desc, s.t) rk from public._zx_cat(p_kind) s where s.v > 0 order by s.v desc, s.t limit 10) x
  join public.accounts a on a.id = x.account_id
$$;

create or replace function public.zx_board_clans() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('rank', x.rk, 'id', x.id, 'name', x.name, 'color', x.color, 'level', x.level, 'xp', x.xp_total,
      'title', public._zx_clan_title(x.level), 'members', (select count(*) from public.accounts where clan_id = x.id)) order by x.rk), '[]'::jsonb)
  from (select c.*, rank() over (order by c.xp_total desc, c.id) rk from public.clans c order by c.xp_total desc, c.id limit 10) x
$$;

create or replace function public.zx_title_set(p_id bigint, p_key text, p_kind text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if coalesce(p_kind, '') <> '' and not exists (select 1 from jsonb_array_elements(public._zx_titles(a.id)) e where e ->> 'kind' = p_kind) then
    raise exception 'Bu unvan için o kategoride ilk 10''da olmalısın';
  end if;
  update public.accounts set title_kind = coalesce(p_kind, '') where id = a.id returning * into a;
  return public._zx_profile(a);
end $$;

-- profil fotoğrafı (cihazdan; istemci 96×96 WebP'ye küçültür, base64)
create or replace function public.zx_photo_set(p_id bigint, p_key text, p_photo text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if char_length(coalesce(p_photo, '')) > 20000 then raise exception 'Fotoğraf çok büyük'; end if;
  update public.accounts set photo = coalesce(p_photo, '') where id = a.id returning * into a;
  perform public._zx_log(a.id, 'profile', case when coalesce(p_photo, '') = '' then 'Profil fotoğrafını kaldırdı' else 'Profil fotoğrafı yükledi' end);
  return public._zx_profile(a);
end $$;

-- başkasının profili (sıralamadan): yasaklıysa işaretli, unvan, fotoğraf, oynama süresi
create or replace function public.zx_profile_view(p_name text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', a.id, 'name', a.name, 'avatar', a.avatar, 'frame', a.frame, 'photo', a.photo,
    'vip', a.vip_until is not null and a.vip_until > now(), 'level', public._zx_level(a.id), 'kills', public._zx_kills(a.id),
    'tags', public._zx_tags(a.id), 'online', a.last_seen > now() - interval '3 minutes',
    'title', public._zx_title(a), 'titles', public._zx_titles(a.id),
    'banned', a.banned_until is not null and a.banned_until > now(),
    'hours', (select coalesce(sum(play_seconds), 0) / 3600 from public.characters where account_id = a.id),
    'stats', (select jsonb_build_object('titan', sum(titan_kills), 'soldiers', sum(soldier_kills), 'bases', sum(base_caps),
                'dog', sum(dog_kills), 'crafted', sum(crafted)) from public.characters where account_id = a.id),
    'clan', (select jsonb_build_object('name', c.name, 'title', public._zx_clan_title(c.level), 'level', c.level,
             'rank_name', public._zx_rank_name(a.clan_rank), 'top', public._zx_clan_top(c.id)) from public.clans c where c.id = a.clan_id))
  from public.accounts a where lower(a.name) = lower(trim(p_name)) or a.id::text = trim(p_name)
  limit 1
$$;

grant execute on function public.zx_board(text) to anon, authenticated;
grant execute on function public.zx_board_clans() to anon, authenticated;
grant execute on function public.zx_title_set(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_photo_set(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_profile_view(text) to anon, authenticated;

-- kendi profilim: fotoğraf, unvan, hak ettiğim unvanlar, oynama süresi
CREATE OR REPLACE FUNCTION public._zx_profile(a accounts)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select jsonb_build_object(
    'id', a.id, 'name', a.name, 'admin', a.is_admin, 'avatar', a.avatar, 'frame', a.frame, 'photo', a.photo,
    'vip', a.vip_until is not null and a.vip_until > now(), 'vip_until', a.vip_until,
    'clan', (select public._zx_clan_json(c) || jsonb_build_object('owner', c.owner = a.id, 'rank', a.clan_rank,
               'rank_name', public._zx_rank_name(a.clan_rank), 'muted_until', a.clan_muted_until)
             from public.clans c where c.id = a.clan_id),
    'chars', coalesce((select jsonb_object_agg(server, level) from public.characters where account_id = a.id), '{}'::jsonb),
    'level', public._zx_level(a.id), 'kills', public._zx_kills(a.id), 'tags', public._zx_tags(a.id),
    'title', public._zx_title(a), 'titles', public._zx_titles(a.id),
    'hours', (select coalesce(sum(play_seconds), 0) / 3600 from public.characters where account_id = a.id),
    'unread', (select count(*) from public.mail m where m.account_id = a.id and not m.read and not m.deleted and (m.favorite or m.created_at > now() - interval '30 days')),
    'friend_req', (select count(*) from public.friends f where f.b = a.id and not f.accepted and not f.removed),
    'dm_unread', (select count(*) from public.dm d where d.to_id = a.id and not d.read),
    'invites', (select count(*) from public.clan_invites i where i.account_id = a.id),
    'key_changed_at', a.key_changed_at,
    'google', a.google_sub is not null)
$function$;

-- kayıtta sıralama sayaçları da güncellenir
create or replace function public._zx_char_counters(p bigint, srv text, d jsonb) returns void
language sql security definer set search_path = public as $$
  update public.characters set
    titan_kills = greatest(titan_kills, coalesce((d -> 'kills_by' ->> 7)::int, 0)),
    soldier_kills = greatest(soldier_kills, coalesce((d -> 'ev' ->> 'soldier_kills')::int, 0)),
    base_caps = greatest(base_caps, coalesce((d -> 'ev' ->> 'base_caps')::int, 0)),
    dog_kills = greatest(dog_kills, coalesce((d -> 'ev' ->> 'dog_kills')::int, 0)),
    crafted = greatest(crafted, coalesce((d ->> 'crafted')::int, 0))
  where account_id = p and server = srv
$$;
revoke all on function public._zx_char_counters(bigint, text, jsonb) from public, anon, authenticated;

create or replace function public.zx_save3(p_id bigint, p_key text, p_sid uuid, p_seq bigint, p_sig text,
  p_level int, p_data text, p_kills int, p_xp bigint) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; old public.characters; d jsonb; od jsonb;
        mins double precision; g_gems bigint; g_gold bigint; g_lv bigint; bad text := ''; r jsonb; pt bigint;
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  if p_seq <= s.seq then raise exception 'Eski kayıt'; end if;
  if encode(hmac(p_seq::text || ':' || p_data, s.secret, 'sha256'), 'hex') <> lower(coalesce(p_sig, '')) then
    perform public._zx_flag(a.id, 'sig', 'Kayıt imzası tutmadı');
    raise exception 'HILE|Kayıt imzası geçersiz';
  end if;
  begin d := p_data::jsonb; exception when others then raise exception 'Bozuk kayıt'; end;
  select * into old from public.characters where account_id = a.id and server = s.server;
  if old.account_id is not null and old.data ~ '^\s*\{' and a.id <> 0 then
    od := old.data::jsonb;
    mins := greatest(extract(epoch from now() - greatest(old.updated_at, s.started_at - interval '10 minutes')) / 60.0, 0.25);
    select coalesce(sum(gems), 0), coalesce(sum(gold), 0), coalesce(sum(levels), 0) into g_gems, g_gold, g_lv
      from public.grants where account_id = a.id and created_at > old.updated_at - interval '5 seconds';
    if coalesce((d ->> 'gems')::bigint, 0) - coalesce((od ->> 'gems')::bigint, 0) > g_gems + 3 + mins * 2 then
      bad := bad || 'elmas +' || (coalesce((d ->> 'gems')::bigint, 0) - coalesce((od ->> 'gems')::bigint, 0)) || '; ';
    end if;
    if coalesce((d ->> 'gold')::bigint, 0) - coalesce((od ->> 'gold')::bigint, 0) > g_gold + 3000 + mins * 4000 then
      bad := bad || 'altın +' || (coalesce((d ->> 'gold')::bigint, 0) - coalesce((od ->> 'gold')::bigint, 0)) || '; ';
    end if;
    if p_level - old.level > g_lv + 2 + floor(mins) then
      bad := bad || 'seviye ' || old.level || '→' || p_level || '; ';
    end if;
    if bad <> '' then
      perform public._zx_flag(a.id, 'save', bad || round(mins::numeric, 1) || ' dk');
      update public.sessions set seq = p_seq where account_id = a.id;
      return jsonb_build_object('ok', false, 'rollback', old.data, 'msg', 'Kayıt sunucuda doğrulanamadı, son geçerli kayda dönüldü');
    end if;
  end if;
  pt := greatest(coalesce((d ->> 'play_time')::double precision, 0)::bigint, 0);
  update public.sessions set seq = p_seq, last_save = now(), last_beat = now() where account_id = a.id;
  r := public.zx_save2(p_id, p_key, s.server, p_level, p_data, p_kills, p_xp);
  update public.characters set play_seconds = greatest(play_seconds, pt) where account_id = a.id and server = s.server;
  perform public._zx_char_counters(a.id, s.server, d);
  return r;
end $$;

-- PvP: Oyuncu Katili kategorisi
alter table public.characters add column if not exists pvp_kills int not null default 0;

create or replace function public._zx_cat_title(k text) returns text
language sql immutable as $$
  select case k when 'level' then 'Efsane' when 'kills' then 'Canavar Avcısı' when 'titan' then 'Titan Avcısı'
    when 'soldiers' then 'Savaş Lordu' when 'bases' then 'Fatih' when 'dog' then 'Sürü Lideri'
    when 'crafted' then 'Usta Zanaatkâr' when 'time' then 'Emektar' when 'pvp' then 'Oyuncu Katili' else k end
$$;

create or replace function public._zx_cat(k text) returns table(account_id bigint, v bigint, t timestamptz)
language sql stable security definer set search_path = public as $$
  select c.account_id,
    case k when 'level' then max(c.level)::bigint when 'kills' then sum(c.kills)::bigint when 'titan' then sum(c.titan_kills)::bigint
      when 'soldiers' then sum(c.soldier_kills)::bigint when 'bases' then sum(c.base_caps)::bigint when 'dog' then sum(c.dog_kills)::bigint
      when 'crafted' then sum(c.crafted)::bigint when 'time' then sum(c.play_seconds)::bigint when 'pvp' then sum(c.pvp_kills)::bigint else 0 end as v,
    min(c.updated_at) as t
  from public.characters c join public.accounts a on a.id = c.account_id
  where a.deleted_at is null and a.id <> 0
  group by c.account_id
$$;

create or replace function public._zx_titles(p bigint) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare k text; r bigint; res jsonb := '[]'::jsonb;
begin
  foreach k in array array['level', 'kills', 'pvp', 'titan', 'soldiers', 'bases', 'dog', 'crafted', 'time'] loop
    select rk into r from (select s.account_id, rank() over (order by s.v desc, s.t) rk from public._zx_cat(k) s where s.v > 0) x where x.account_id = p;
    if r is not null and r <= 10 then
      res := res || jsonb_build_array(jsonb_build_object('kind', k, 'rank', r, 'name', public._zx_cat_title(k) || ' #' || r));
    end if;
  end loop;
  return res;
end $$;

create or replace function public._zx_char_counters(p bigint, srv text, d jsonb) returns void
language sql security definer set search_path = public as $$
  update public.characters set
    titan_kills = greatest(titan_kills, coalesce((d -> 'kills_by' ->> 7)::int, 0)),
    soldier_kills = greatest(soldier_kills, coalesce((d -> 'ev' ->> 'soldier_kills')::int, 0)),
    base_caps = greatest(base_caps, coalesce((d -> 'ev' ->> 'base_caps')::int, 0)),
    dog_kills = greatest(dog_kills, coalesce((d -> 'ev' ->> 'dog_kills')::int, 0)),
    pvp_kills = greatest(pvp_kills, coalesce((d -> 'ev' ->> 'pvp_kills')::int, 0)),
    crafted = greatest(crafted, coalesce((d ->> 'crafted')::int, 0))
  where account_id = p and server = srv
$$;
revoke all on function public._zx_char_counters(bigint, text, jsonb) from public, anon, authenticated;
