-- Zenvex Online — profil, klan v2, arkadaşlık + özel mesaj, posta düzeni, yönetici (bakım/ban/VIP), sohbet kuralları.
-- (Uygulandı: zenvex-online, 20.10.2026)
-- Silme yerine "deleted" bayrağı kullanılır (kayıtlar geri alınabilir, posta 30 günde gizlenir).

-- ================================================================ şema
alter table public.accounts add column if not exists avatar int not null default 0;
alter table public.accounts add column if not exists frame text not null default '';
alter table public.accounts add column if not exists vip_until timestamptz;
alter table public.accounts add column if not exists banned_until timestamptz;
alter table public.accounts add column if not exists ban_reason text not null default '';
alter table public.accounts add column if not exists clan_rank smallint not null default 0;      -- 0 Üye, 1 Kıdemli Üye, 2 Yardımcı Lider, 3 Lider
alter table public.accounts add column if not exists clan_muted_until timestamptz;
alter table public.accounts add column if not exists clan_joined_at timestamptz;

alter table public.characters add column if not exists kills int not null default 0;
alter table public.characters add column if not exists xp_total bigint not null default 0;

alter table public.clans add column if not exists level int not null default 1;
alter table public.clans add column if not exists xp_total bigint not null default 0;
alter table public.clans add column if not exists gold bigint not null default 0;
alter table public.clans add column if not exists boosts jsonb not null default '{"dmg":0,"xp":0,"speed":0}';
alter table public.clans add column if not exists officer_send boolean not null default false;
alter table public.clans add column if not exists motto text not null default '';

alter table public.mail add column if not exists gems int not null default 0;
alter table public.mail add column if not exists favorite boolean not null default false;
alter table public.mail add column if not exists deleted boolean not null default false;
alter table public.mail add column if not exists kind text not null default 'reward';             -- reward | event | clan | system

create table if not exists public.clan_xp (
  clan_id bigint not null references public.clans(id) on delete cascade,
  account_id bigint not null references public.accounts(id) on delete cascade,
  week date not null,
  xp bigint not null default 0,
  kills int not null default 0,
  primary key (clan_id, account_id, week)
);
create index if not exists clan_xp_week on public.clan_xp (week, clan_id);

create table if not exists public.clan_depot (
  clan_id bigint not null references public.clans(id) on delete cascade,
  item text not null,
  n int not null default 0 check (n >= 0),
  primary key (clan_id, item)
);

create table if not exists public.clan_invites (
  clan_id bigint not null references public.clans(id) on delete cascade,
  account_id bigint not null references public.accounts(id) on delete cascade,
  by_name text not null,
  created_at timestamptz not null default now(),
  primary key (clan_id, account_id)
);

create table if not exists public.clan_log (
  id bigint generated always as identity primary key,
  clan_id bigint not null references public.clans(id) on delete cascade,
  text text not null,
  created_at timestamptz not null default now()
);
create index if not exists clan_log_clan on public.clan_log (clan_id, id desc);

create table if not exists public.friends (
  a bigint not null references public.accounts(id) on delete cascade,   -- isteği gönderen
  b bigint not null references public.accounts(id) on delete cascade,
  accepted boolean not null default false,
  removed boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (a, b)
);
create index if not exists friends_b on public.friends (b);

create table if not exists public.dm (
  id bigint generated always as identity primary key,
  from_id bigint not null references public.accounts(id) on delete cascade,
  to_id bigint not null references public.accounts(id) on delete cascade,
  text text not null check (char_length(text) between 1 and 200),
  read boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists dm_pair on public.dm (least(from_id, to_id), greatest(from_id, to_id), id desc);
create index if not exists dm_to on public.dm (to_id, read);

create table if not exists public.app_config (
  key text primary key,
  value jsonb not null,
  updated_at timestamptz not null default now()
);
insert into public.app_config (key, value) values ('maintenance', '{"on": false, "msg": ""}') on conflict do nothing;
insert into public.app_config (key, value) values ('clan_payout_week', '"2026-10-19"') on conflict do nothing;

alter table public.clan_xp enable row level security;
alter table public.clan_depot enable row level security;
alter table public.clan_invites enable row level security;
alter table public.clan_log enable row level security;
alter table public.friends enable row level security;
alter table public.dm enable row level security;
alter table public.app_config enable row level security;

-- ================================================================ yardımcılar
-- Giriş: askıya alınmış hesap giremez
create or replace function public._zx_auth(p_id bigint, p_key text) returns public.accounts
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts;
begin
  select * into a from public.accounts where id = p_id;
  if a.id is null or a.key_hash <> crypt(p_key, a.key_hash) then
    raise exception 'Hesap kimliği veya anahtar hatalı' using errcode = '28000';
  end if;
  if a.banned_until is not null and a.banned_until > now() then
    raise exception 'BAN|%|%', to_char(a.banned_until at time zone 'Europe/Istanbul', 'DD.MM.YYYY HH24:MI'), a.ban_reason using errcode = '28000';
  end if;
  update public.accounts set last_seen = now() where id = p_id;
  return a;
end $$;

create or replace function public._zx_week() returns date
language sql stable as $$ select (date_trunc('week', now() at time zone 'Europe/Istanbul'))::date $$;

-- Klan seviyesi için gereken toplam XP (1..9)
create or replace function public._zx_clan_need(l int) returns bigint
language sql immutable as $$
  select (array[0, 5000, 15000, 35000, 70000, 120000, 200000, 320000, 500000])[greatest(1, least(l, 9))]::bigint
$$;

create or replace function public._zx_clan_title(l int) returns text
language sql immutable as $$
  select (array['Kamp Ateşi','Barınak','Karakol','Sığınak','Kale','Hisar','Lejyon','Hanedan','Efsane'])[greatest(1, least(l, 9))]
$$;

-- Üye sınırı: 30'dan başlar, 9. seviyede 50
create or replace function public._zx_clan_cap(l int) returns int
language sql immutable as $$ select 30 + ((greatest(1, least(l, 9)) - 1) * 20) / 8 $$;

create or replace function public._zx_rank_name(r int) returns text
language sql immutable as $$ select (array['Üye','Kıdemli Üye','Yardımcı Lider','Lider'])[greatest(0, least(r, 3)) + 1] $$;

-- Hesabın en yüksek karakter seviyesi ve toplam öldürmesi
create or replace function public._zx_level(p bigint) returns int
language sql stable as $$ select coalesce(max(level), 1) from public.characters where account_id = p $$;

create or replace function public._zx_kills(p bigint) returns int
language sql stable as $$ select coalesce(sum(kills), 0)::int from public.characters where account_id = p $$;

-- Etiketler: VIP, seviye sıralaması (1-3 özel, ilk 10), canavar öldürme sıralaması (1-3)
create or replace function public._zx_tags(p bigint) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare t jsonb := '[]'::jsonb; lr int; kr int; a public.accounts;
begin
  select * into a from public.accounts where id = p;
  if a.vip_until is not null and a.vip_until > now() then t := t || '[{"id":"vip","name":"VIP"}]'::jsonb; end if;
  select r into lr from (
    select account_id, rank() over (order by max(level) desc, min(updated_at)) r from public.characters group by account_id
  ) s where account_id = p;
  if lr is not null and lr <= 10 then
    t := t || jsonb_build_array(jsonb_build_object('id', case when lr <= 3 then 'lvl' || lr else 'lvl10' end,
      'name', case lr when 1 then 'Seviye Şampiyonu #1' when 2 then 'Seviye Ustası #2' when 3 then 'Seviye Ustası #3' else 'İlk 10 · #' || lr end, 'rank', lr));
  end if;
  select r into kr from (
    select account_id, rank() over (order by sum(kills) desc) r from public.characters group by account_id having sum(kills) > 0
  ) s where account_id = p;
  if kr is not null and kr <= 3 then
    t := t || jsonb_build_array(jsonb_build_object('id', 'kill' || kr, 'name', 'Canavar Avcısı #' || kr, 'rank', kr));
  end if;
  return t;
end $$;

-- Genel klan sıralaması (toplam XP) ilk 3 → çerçeve efekti
create or replace function public._zx_clan_top(c bigint) returns int
language sql stable security definer set search_path = public as $$
  select r::int from (select id, rank() over (order by xp_total desc, id) r from public.clans where xp_total > 0) s where id = c and r <= 3
$$;

create or replace function public._zx_clan_json(c public.clans) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', c.id, 'name', c.name, 'color', c.color, 'level', c.level, 'title', public._zx_clan_title(c.level),
    'xp', c.xp_total, 'need', public._zx_clan_need(c.level + 1), 'prev', public._zx_clan_need(c.level), 'gold', c.gold,
    'boosts', c.boosts, 'officer_send', c.officer_send, 'motto', c.motto, 'top', public._zx_clan_top(c.id),
    'members', (select count(*) from public.accounts where clan_id = c.id), 'cap', public._zx_clan_cap(c.level),
    'week_xp', (select coalesce(sum(xp), 0) from public.clan_xp where clan_id = c.id and week = public._zx_week()))
$$;

create or replace function public._zx_profile(a public.accounts) returns jsonb
language sql stable security definer set search_path = public as $$
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
    'key_changed_at', a.key_changed_at)
$$;

revoke all on function public._zx_week() from public, anon, authenticated;
revoke all on function public._zx_tags(bigint) from public, anon, authenticated;
revoke all on function public._zx_clan_top(bigint) from public, anon, authenticated;
revoke all on function public._zx_clan_json(public.clans) from public, anon, authenticated;
revoke all on function public._zx_profile(public.accounts) from public, anon, authenticated;
revoke all on function public._zx_level(bigint) from public, anon, authenticated;
revoke all on function public._zx_kills(bigint) from public, anon, authenticated;

-- ================================================================ durum (bakım) — herkese açık
create or replace function public.zx_status() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('maintenance', (select value from public.app_config where key = 'maintenance'), 'now', now())
$$;

-- ================================================================ profil
create or replace function public.zx_profile_set(p_id bigint, p_key text, p_avatar int, p_frame text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); top int;
begin
  if p_avatar < 0 or p_avatar > 63 then raise exception 'Geçersiz fotoğraf'; end if;
  -- klan çerçeveleri yalnız ilk 3 klanın üyelerine
  if p_frame like 'clan%' then
    top := public._zx_clan_top(a.clan_id);
    if top is null or p_frame <> 'clan' || top then raise exception 'Bu çerçeve klanının sıralamasıyla açılır'; end if;
  end if;
  if p_frame = 'vip' and (a.vip_until is null or a.vip_until < now()) then raise exception 'VIP çerçevesi VIP üyelere özel'; end if;
  update public.accounts set avatar = p_avatar, frame = left(coalesce(p_frame, ''), 24) where id = a.id returning * into a;
  return public._zx_profile(a);
end $$;

-- Başka bir oyuncunun herkese açık profili
create or replace function public.zx_profile_view(p_name text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', a.id, 'name', a.name, 'avatar', a.avatar, 'frame', a.frame,
    'vip', a.vip_until is not null and a.vip_until > now(), 'level', public._zx_level(a.id), 'kills', public._zx_kills(a.id),
    'tags', public._zx_tags(a.id), 'online', a.last_seen > now() - interval '3 minutes',
    'clan', (select jsonb_build_object('name', c.name, 'title', public._zx_clan_title(c.level), 'level', c.level,
             'rank_name', public._zx_rank_name(a.clan_rank), 'top', public._zx_clan_top(c.id)) from public.clans c where c.id = a.clan_id))
  from public.accounts a where lower(a.name) = lower(trim(p_name))
$$;

-- ================================================================ kayıt (seviye, öldürme, toplam XP → klan katkısı)
create or replace function public.zx_save2(p_id bigint, p_key text, p_server text, p_level int, p_data text, p_kills int, p_xp bigint)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); old public.characters; dxp bigint := 0; dk int := 0;
        c public.clans; lvl int; n int;
begin
  if char_length(p_data) > 400000 then raise exception 'Kayıt çok büyük'; end if;
  select * into old from public.characters where account_id = a.id and server = p_server;
  if old.account_id is not null then
    -- bir kayıtta en çok 20.000 XP / 300 öldürme sayılır (hileli sıçramalar klana yazılmaz)
    dxp := least(greatest(p_xp - old.xp_total, 0), 20000);
    dk := least(greatest(p_kills - old.kills, 0), 300);
  end if;
  insert into public.characters (account_id, server, level, data, updated_at, kills, xp_total)
  values (a.id, p_server, greatest(1, least(p_level, 999)), p_data, now(), greatest(p_kills, 0), greatest(p_xp, 0))
  on conflict (account_id, server) do update set level = excluded.level, data = excluded.data, updated_at = now(),
    kills = greatest(public.characters.kills, excluded.kills), xp_total = greatest(public.characters.xp_total, excluded.xp_total);
  if a.clan_id is not null and (dxp > 0 or dk > 0) then
    insert into public.clan_xp (clan_id, account_id, week, xp, kills) values (a.clan_id, a.id, public._zx_week(), dxp, dk)
    on conflict (clan_id, account_id, week) do update set xp = public.clan_xp.xp + excluded.xp, kills = public.clan_xp.kills + excluded.kills;
    update public.clans set xp_total = xp_total + dxp where id = a.clan_id returning * into c;
    -- seviye atlama: tüm üyelere hesaba bağlı elmas ödülü
    lvl := c.level;
    while lvl < 9 and c.xp_total >= public._zx_clan_need(lvl + 1) loop lvl := lvl + 1; end loop;
    if lvl > c.level then
      update public.clans set level = lvl where id = c.id;
      insert into public.mail (account_id, sender, title, body, gems, kind)
      select id, c.name, 'Klan Seviye ' || lvl || ': ' || public._zx_clan_title(lvl),
        'Klanın ' || lvl || '. seviyeye ulaştı! Ödülün hesabına bağlıdır, takas edilemez. Üye sınırı: ' || public._zx_clan_cap(lvl) || '.',
        25 * lvl, 'clan'
      from public.accounts where clan_id = c.id;
      get diagnostics n = row_count;
      insert into public.clan_log (clan_id, text) values (c.id, 'Klan ' || lvl || '. seviyeye ulaştı (' || public._zx_clan_title(lvl) || ')');
    end if;
  end if;
  return jsonb_build_object('ok', true, 'clan_xp', dxp);
end $$;

-- ================================================================ sohbet: 5. seviye + 3 sn kuralı
create or replace function public.zx_chat_post(p_id bigint, p_key text, p_server text, p_text text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t text := left(trim(p_text), 140); r public.chat;
begin
  if t = '' then raise exception 'Boş mesaj'; end if;
  if public._zx_level(a.id) < 5 then raise exception 'Sohbet 5. seviyede açılır'; end if;
  if a.last_chat > now() - interval '3 seconds' then raise exception '3 saniye bekle'; end if;
  update public.accounts set last_chat = now() where id = a.id;
  insert into public.chat (server, account_id, name, clan, text)
  values (p_server, a.id, a.name, (select name from public.clans where id = a.clan_id), t) returning * into r;
  return jsonb_build_object('id', r.id, 'ts', (extract(epoch from r.created_at) * 1000)::bigint);
end $$;

-- ================================================================ posta: yalnız ödül/duyuru; 30 günde gizlenir (favori hariç)
revoke execute on function public.zx_mail_send(bigint, text, text, text, text) from anon, authenticated, public;

create or replace function public.zx_mail_list(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); r jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'from', sender, 'title', title, 'body', body, 'gold', gold, 'xp', xp,
    'gems', gems, 'items', items, 'read', read, 'claimed', claimed, 'favorite', favorite, 'kind', kind,
    'ts', (extract(epoch from created_at) * 1000)::bigint,
    'expires', case when favorite then null else (extract(epoch from created_at + interval '30 days') * 1000)::bigint end) order by id desc), '[]'::jsonb)
  into r from (select * from public.mail where account_id = a.id and not deleted
               and (favorite or created_at > now() - interval '30 days') order by id desc limit 60) m;
  update public.mail set read = true where account_id = a.id and not read and not deleted;
  return r;
end $$;

create or replace function public.zx_mail_claim(p_id bigint, p_key text, p_mail bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); m public.mail;
begin
  update public.mail set claimed = true, read = true
  where id = p_mail and account_id = a.id and not claimed and not deleted
    and (favorite or created_at > now() - interval '30 days') returning * into m;
  if m.id is null then raise exception 'Bu posta zaten alınmış'; end if;
  return jsonb_build_object('gold', m.gold, 'xp', m.xp, 'gems', m.gems, 'items', m.items);
end $$;

create or replace function public.zx_mail_favorite(p_id bigint, p_key text, p_mail bigint, p_on boolean) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  update public.mail set favorite = p_on where id = p_mail and account_id = a.id and not deleted;
  return p_on;
end $$;

create or replace function public.zx_mail_remove(p_id bigint, p_key text, p_mail bigint) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  update public.mail set deleted = true where id = p_mail and account_id = a.id;
  return true;
end $$;

-- ================================================================ arkadaşlık + özel mesaj
create or replace function public.zx_friend_request(p_id bigint, p_key text, p_name text) returns text
language plpgsql security definer set search_path = public as $$
declare me public.accounts := public._zx_auth(p_id, p_key); t bigint;
begin
  select id into t from public.accounts where lower(name) = lower(trim(p_name));
  if t is null then raise exception 'Oyuncu bulunamadı'; end if;
  if t = me.id then raise exception 'Kendine istek gönderemezsin'; end if;
  if (select count(*) from public.friends where (a = me.id or b = me.id) and accepted and not removed) >= 100 then raise exception 'Arkadaş listen dolu (100)'; end if;
  -- karşı taraf zaten istek göndermişse kabul edilmiş say
  if exists (select 1 from public.friends where a = t and b = me.id and not removed) then
    update public.friends set accepted = true where a = t and b = me.id;
    return 'Artık arkadaşsınız';
  end if;
  insert into public.friends (a, b) values (me.id, t)
  on conflict (a, b) do update set removed = false, accepted = public.friends.accepted, created_at = now();
  return 'Arkadaşlık isteği gönderildi';
end $$;

create or replace function public.zx_friend_respond(p_id bigint, p_key text, p_from bigint, p_accept boolean) returns text
language plpgsql security definer set search_path = public as $$
declare me public.accounts := public._zx_auth(p_id, p_key);
begin
  update public.friends set accepted = p_accept, removed = not p_accept where a = p_from and b = me.id and not removed;
  return case when p_accept then 'Artık arkadaşsınız' else 'İstek reddedildi' end;
end $$;

create or replace function public.zx_friend_remove(p_id bigint, p_key text, p_other bigint) returns boolean
language plpgsql security definer set search_path = public as $$
declare me public.accounts := public._zx_auth(p_id, p_key);
begin
  update public.friends set removed = true, accepted = false where (a = me.id and b = p_other) or (a = p_other and b = me.id);
  return true;
end $$;

create or replace function public.zx_friends(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare me public.accounts := public._zx_auth(p_id, p_key);
begin
  return jsonb_build_object(
    'friends', coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'name', o.name, 'avatar', o.avatar, 'frame', o.frame,
        'level', public._zx_level(o.id), 'online', o.last_seen > now() - interval '3 minutes',
        'unread', (select count(*) from public.dm where from_id = o.id and to_id = me.id and not read)) order by o.last_seen desc)
      from public.friends f join public.accounts o on o.id = case when f.a = me.id then f.b else f.a end
      where (f.a = me.id or f.b = me.id) and f.accepted and not f.removed), '[]'::jsonb),
    'incoming', coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'name', o.name, 'avatar', o.avatar, 'level', public._zx_level(o.id)))
      from public.friends f join public.accounts o on o.id = f.a where f.b = me.id and not f.accepted and not f.removed), '[]'::jsonb),
    'outgoing', coalesce((select jsonb_agg(jsonb_build_object('id', o.id, 'name', o.name))
      from public.friends f join public.accounts o on o.id = f.b where f.a = me.id and not f.accepted and not f.removed), '[]'::jsonb));
end $$;

create or replace function public.zx_dm_send(p_id bigint, p_key text, p_to bigint, p_text text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare me public.accounts := public._zx_auth(p_id, p_key); t text := left(trim(p_text), 200); r public.dm;
begin
  if t = '' then raise exception 'Boş mesaj'; end if;
  if public._zx_level(me.id) < 5 then raise exception 'Sohbet 5. seviyede açılır'; end if;
  if not exists (select 1 from public.friends where ((a = me.id and b = p_to) or (a = p_to and b = me.id)) and accepted and not removed) then
    raise exception 'Yalnız arkadaşlarına özel mesaj gönderebilirsin'; end if;
  if exists (select 1 from public.dm where from_id = me.id and created_at > now() - interval '3 seconds') then raise exception '3 saniye bekle'; end if;
  insert into public.dm (from_id, to_id, text) values (me.id, p_to, t) returning * into r;
  return jsonb_build_object('id', r.id, 'ts', (extract(epoch from r.created_at) * 1000)::bigint);
end $$;

create or replace function public.zx_dm_list(p_id bigint, p_key text, p_with bigint, p_after bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare me public.accounts := public._zx_auth(p_id, p_key); r jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'mine', from_id = me.id, 'text', text,
      'ts', (extract(epoch from created_at) * 1000)::bigint) order by id), '[]'::jsonb) into r
  from (select * from public.dm where least(from_id, to_id) = least(me.id, p_with) and greatest(from_id, to_id) = greatest(me.id, p_with)
        and id > coalesce(p_after, 0) order by id desc limit 60) d;
  update public.dm set read = true where from_id = p_with and to_id = me.id and not read;
  return r;
end $$;

-- ================================================================ klan v2
-- Yetki: 0 Üye, 1 Kıdemli Üye (davet, üye susturma), 2 Yardımcı Lider (+kıdemli atama, atma, izinle depodan gönderme), 3 Lider (her şey)
create or replace function public._zx_clan_me(p_id bigint, p_key text, need int) returns public.accounts
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if a.clan_id is null then raise exception 'Bir klanda değilsin'; end if;
  if (select owner from public.clans where id = a.clan_id) = a.id and a.clan_rank < 3 then
    update public.accounts set clan_rank = 3 where id = a.id returning * into a;
  end if;
  if a.clan_rank < need then raise exception 'Bunun için % gerekli', public._zx_rank_name(need); end if;
  return a;
end $$;
revoke all on function public._zx_clan_me(bigint, text, int) from public, anon, authenticated;

create or replace function public.zx_clan_create(p_id bigint, p_key text, p_name text, p_color int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); c public.clans; n text := trim(p_name);
begin
  if a.clan_id is not null then raise exception 'Önce klanından ayrıl'; end if;
  if char_length(n) < 3 or char_length(n) > 20 then raise exception 'Klan adı 3-20 harf olmalı'; end if;
  select * into c from public.clans where lower(name) = lower(n);
  if c.id is not null then
    if exists (select 1 from public.accounts where clan_id = c.id) then raise exception 'Bu klan adı alınmış'; end if;
    update public.clans set owner = a.id, color = p_color, level = 1, xp_total = 0, gold = 0,
      boosts = '{"dmg":0,"xp":0,"speed":0}', officer_send = false where id = c.id returning * into c;
  else
    insert into public.clans (name, color, owner) values (n, p_color, a.id) returning * into c;
  end if;
  update public.accounts set clan_id = c.id, clan_rank = 3, clan_joined_at = now() where id = a.id returning * into a;
  insert into public.clan_log (clan_id, text) values (c.id, a.name || ' klanı kurdu');
  return public._zx_profile(a);
end $$;

-- Katılma: davet varsa ya da klan açıksa (davetsiz katılım da serbest; sınır seviyeye göre)
create or replace function public.zx_clan_join(p_id bigint, p_key text, p_clan bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); c public.clans;
begin
  if a.clan_id is not null then raise exception 'Önce klanından ayrıl'; end if;
  select * into c from public.clans where id = p_clan;
  if c.id is null then raise exception 'Klan bulunamadı'; end if;
  if (select count(*) from public.accounts where clan_id = p_clan) >= public._zx_clan_cap(c.level) then
    raise exception 'Klan dolu (%)', public._zx_clan_cap(c.level); end if;
  update public.accounts set clan_id = p_clan, clan_rank = 0, clan_joined_at = now(), clan_muted_until = null where id = a.id returning * into a;
  insert into public.clan_log (clan_id, text) values (c.id, a.name || ' klana katıldı');
  return public._zx_profile(a);
end $$;

create or replace function public.zx_clan_leave(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); c bigint := a.clan_id; heir bigint;
begin
  update public.accounts set clan_id = null, clan_rank = 0, clan_muted_until = null where id = a.id returning * into a;
  if c is not null then
    insert into public.clan_log (clan_id, text) values (c, a.name || ' klandan ayrıldı');
    -- lider ayrılırsa en yüksek rütbeli, sonra en eski üye lider olur
    if (select owner from public.clans where id = c) = a.id then
      select id into heir from public.accounts where clan_id = c order by clan_rank desc, clan_joined_at nulls last, created_at limit 1;
      update public.clans set owner = coalesce(heir, 0) where id = c;
      if heir is not null then update public.accounts set clan_rank = 3 where id = heir; end if;
    end if;
  end if;
  return public._zx_profile(a);
end $$;

create or replace function public.zx_clans() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(public._zx_clan_json(c) order by c.xp_total desc), '[]'::jsonb)
  from public.clans c where exists (select 1 from public.accounts a where a.clan_id = c.id)
$$;

create or replace function public.zx_clan_info(p_clan bigint) returns jsonb
language sql stable security definer set search_path = public as $$
  select public._zx_clan_json(c) || jsonb_build_object(
    'members', coalesce((select jsonb_agg(jsonb_build_object('id', a.id, 'name', a.name, 'avatar', a.avatar, 'rank', a.clan_rank,
        'rank_name', public._zx_rank_name(a.clan_rank), 'level', public._zx_level(a.id),
        'online', a.last_seen > now() - interval '3 minutes', 'muted_until', a.clan_muted_until,
        'week_xp', (select coalesce(sum(x.xp), 0) from public.clan_xp x where x.account_id = a.id and x.clan_id = c.id and x.week = public._zx_week()))
        order by a.clan_rank desc, a.last_seen desc) from public.accounts a where a.clan_id = c.id), '[]'::jsonb),
    'member_count', (select count(*) from public.accounts where clan_id = c.id),
    'depot', coalesce((select jsonb_agg(jsonb_build_object('id', item, 'n', n) order by item) from public.clan_depot where clan_id = c.id and n > 0), '[]'::jsonb),
    'log', coalesce((select jsonb_agg(jsonb_build_object('text', text, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
        from (select * from public.clan_log where clan_id = c.id order by id desc limit 30) l), '[]'::jsonb),
    'quests', (select jsonb_build_array(
        jsonb_build_object('title', 'Haftalık Av: 1000 Zombi', 'n', coalesce(sum(kills), 0), 'goal', 1000, 'reward', '5.000 Klan XP'),
        jsonb_build_object('title', 'Tecrübe Yağmuru: 50.000 XP', 'n', coalesce(sum(xp), 0), 'goal', 50000, 'reward', 'Depoya 2.000 Altın'),
        jsonb_build_object('title', 'Birlik: 10 Aktif Üye', 'n', count(distinct account_id), 'goal', 10, 'reward', 'Depoya 5 Can İksiri'))
      from public.clan_xp where clan_id = c.id and week = public._zx_week()))
  from public.clans c where c.id = p_clan
$$;

-- eski üye listesi (geriye uyum)
create or replace function public.zx_clan_members(p_clan bigint) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('name', a.name, 'owner', c.owner = a.id, 'rank', a.clan_rank,
    'level', public._zx_level(a.id), 'online', a.last_seen > now() - interval '3 minutes') order by a.clan_rank desc, a.last_seen desc), '[]'::jsonb)
  from public.accounts a join public.clans c on c.id = a.clan_id where a.clan_id = p_clan
$$;

create or replace function public.zx_clan_invite(p_id bigint, p_key text, p_name text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 1); t public.accounts;
begin
  select * into t from public.accounts where lower(name) = lower(trim(p_name));
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if t.clan_id is not null then raise exception 'Oyuncu zaten bir klanda'; end if;
  insert into public.clan_invites (clan_id, account_id, by_name) values (a.clan_id, t.id, a.name)
  on conflict (clan_id, account_id) do update set by_name = excluded.by_name, created_at = now();
  insert into public.mail (account_id, sender, title, body, kind)
  values (t.id, (select name from public.clans where id = a.clan_id), 'Klan Daveti',
          a.name || ' seni klanına davet etti. Klan ekranındaki Davetler bölümünden katılabilirsin.', 'clan');
  return 'Davet gönderildi';
end $$;

create or replace function public.zx_clan_invites(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return coalesce((select jsonb_agg(public._zx_clan_json(c) || jsonb_build_object('by', i.by_name))
    from public.clan_invites i join public.clans c on c.id = i.clan_id
    where i.account_id = a.id and i.created_at > now() - interval '7 days'), '[]'::jsonb);
end $$;

-- Rütbe: Yardımcı Lider 0-1 arası atar; Lider 0-2 arası (3 = liderliği devret)
create or replace function public.zx_clan_set_rank(p_id bigint, p_key text, p_member bigint, p_rank int) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 2); m public.accounts;
begin
  select * into m from public.accounts where id = p_member and clan_id = a.clan_id;
  if m.id is null then raise exception 'Üye bulunamadı'; end if;
  if m.id = a.id then raise exception 'Kendi rütbeni değiştiremezsin'; end if;
  if m.clan_rank >= a.clan_rank then raise exception 'Bu üyenin rütbesini değiştiremezsin'; end if;
  if p_rank < 0 or p_rank > 3 then raise exception 'Geçersiz rütbe'; end if;
  if a.clan_rank < 3 and p_rank > 1 then raise exception 'Yardımcı Lider yalnız Üye ve Kıdemli Üye atayabilir'; end if;
  if p_rank = 3 then
    update public.clans set owner = m.id where id = a.clan_id;
    update public.accounts set clan_rank = 2 where id = a.id;
  end if;
  update public.accounts set clan_rank = p_rank where id = m.id;
  insert into public.clan_log (clan_id, text) values (a.clan_id, a.name || ', ' || m.name || ' adlı üyeyi ' || public._zx_rank_name(p_rank) || ' yaptı');
  return m.name || ': ' || public._zx_rank_name(p_rank);
end $$;

create or replace function public.zx_clan_kick(p_id bigint, p_key text, p_member bigint) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 2); m public.accounts;
begin
  select * into m from public.accounts where id = p_member and clan_id = a.clan_id;
  if m.id is null then raise exception 'Üye bulunamadı'; end if;
  if m.clan_rank >= a.clan_rank then raise exception 'Bu üyeyi atamazsın'; end if;
  update public.accounts set clan_id = null, clan_rank = 0, clan_muted_until = null where id = m.id;
  insert into public.clan_log (clan_id, text) values (a.clan_id, a.name || ', ' || m.name || ' adlı üyeyi klandan attı');
  return m.name || ' klandan atıldı';
end $$;

-- Susturma: Kıdemli Üye yalnız Üyeleri susturabilir; süre 5 dk / 1 sa / 6 sa / 24 sa (0 = kaldır)
create or replace function public.zx_clan_mute(p_id bigint, p_key text, p_member bigint, p_minutes int) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 1); m public.accounts;
begin
  if p_minutes not in (0, 5, 60, 360, 1440) then raise exception 'Geçersiz süre'; end if;
  select * into m from public.accounts where id = p_member and clan_id = a.clan_id;
  if m.id is null then raise exception 'Üye bulunamadı'; end if;
  if m.clan_rank >= a.clan_rank or (a.clan_rank = 1 and m.clan_rank > 0) then raise exception 'Bu üyeyi susturamazsın'; end if;
  update public.accounts set clan_muted_until = case when p_minutes = 0 then null else now() + make_interval(mins => p_minutes) end where id = m.id;
  insert into public.clan_log (clan_id, text) values (a.clan_id,
    case when p_minutes = 0 then a.name || ', ' || m.name || ' adlı üyenin susturmasını kaldırdı'
         else a.name || ', ' || m.name || ' adlı üyeyi ' || case p_minutes when 5 then '5 dakika' when 60 then '1 saat' when 360 then '6 saat' else '24 saat' end || ' susturdu' end);
  return case when p_minutes = 0 then 'Susturma kaldırıldı' else m.name || ' susturuldu' end;
end $$;

-- Depo: her üye eşya bırakabilir; altını yalnız lider yatırır
create or replace function public.zx_clan_deposit(p_id bigint, p_key text, p_item text, p_n int) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, case when p_item = 'coin' then 3 else 0 end);
begin
  if p_n <= 0 or p_n > 100000 then raise exception 'Geçersiz miktar'; end if;
  if p_item = 'coin' then
    update public.clans set gold = gold + p_n where id = a.clan_id;
    insert into public.clan_log (clan_id, text) values (a.clan_id, a.name || ' depoya ' || p_n || ' altın yatırdı');
  else
    if p_item !~ '^[a-z0-9_]{2,24}$' then raise exception 'Geçersiz eşya'; end if;
    insert into public.clan_depot (clan_id, item, n) values (a.clan_id, p_item, p_n)
    on conflict (clan_id, item) do update set n = public.clan_depot.n + excluded.n;
    insert into public.clan_log (clan_id, text) values (a.clan_id, a.name || ' depoya ' || p_n || '× ' || p_item || ' bıraktı');
  end if;
  return 'Depoya eklendi';
end $$;

-- Depodan üyeye gönderim (açıklamalı posta): lider; izin verildiyse yardımcı lider
create or replace function public.zx_clan_send(p_id bigint, p_key text, p_member bigint, p_item text, p_n int, p_note text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 2); m public.accounts; c public.clans;
begin
  select * into c from public.clans where id = a.clan_id;
  if a.clan_rank < 3 and not c.officer_send then raise exception 'Lider depodan gönderme izni vermedi'; end if;
  select * into m from public.accounts where id = p_member and clan_id = a.clan_id;
  if m.id is null then raise exception 'Üye bulunamadı'; end if;
  if p_n <= 0 then raise exception 'Geçersiz miktar'; end if;
  if p_item = 'coin' then
    update public.clans set gold = gold - p_n where id = c.id and gold >= p_n;
    if not found then raise exception 'Depoda yeterli altın yok'; end if;
    insert into public.mail (account_id, sender, title, body, gold, kind)
    values (m.id, c.name, 'Klan Deposundan', left(coalesce(nullif(trim(p_note), ''), a.name || ' gönderdi'), 200), p_n, 'clan');
  else
    update public.clan_depot set n = n - p_n where clan_id = c.id and item = p_item and n >= p_n;
    if not found then raise exception 'Depoda yeterli eşya yok'; end if;
    insert into public.mail (account_id, sender, title, body, items, kind)
    values (m.id, c.name, 'Klan Deposundan', left(coalesce(nullif(trim(p_note), ''), a.name || ' gönderdi'), 200),
            jsonb_build_array(jsonb_build_object('id', p_item, 'n', p_n)), 'clan');
  end if;
  insert into public.clan_log (clan_id, text) values (c.id, a.name || ', ' || m.name || ' adlı üyeye depodan ' || p_n || '× ' || p_item || ' gönderdi');
  return 'Gönderildi';
end $$;

create or replace function public.zx_clan_settings(p_id bigint, p_key text, p_officer_send boolean, p_motto text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 3);
begin
  update public.clans set officer_send = p_officer_send, motto = left(coalesce(p_motto, ''), 80) where id = a.clan_id;
  return 'Kaydedildi';
end $$;

-- Güçlendirme: yalnız lider, depodaki altınla. Hasar %2→%4 (seviye başı +%2 ... ), XP, hız. En çok klan seviyesi kadar.
create or replace function public.zx_clan_boost(p_id bigint, p_key text, p_kind text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 3); c public.clans; lv int; cost int;
begin
  if p_kind not in ('dmg', 'xp', 'speed') then raise exception 'Geçersiz güçlendirme'; end if;
  select * into c from public.clans where id = a.clan_id;
  lv := coalesce((c.boosts ->> p_kind)::int, 0);
  if lv >= least(c.level, 5) then raise exception 'Klan seviyesi yükseldikçe açılır (en çok 5)'; end if;
  cost := 3000 * (lv + 1);
  if c.gold < cost then raise exception 'Depoda % altın gerekli', cost; end if;
  update public.clans set gold = gold - cost, boosts = jsonb_set(boosts, array[p_kind], to_jsonb(lv + 1)) where id = c.id returning * into c;
  insert into public.clan_log (clan_id, text) values (c.id, a.name || ' ' ||
    case p_kind when 'dmg' then 'Hasar' when 'xp' then 'Tecrübe' else 'Hız' end || ' güçlendirmesini ' || (lv + 1) || '. seviyeye çıkardı');
  return public._zx_clan_json(c);
end $$;

-- Haftalık ödül: geçen haftanın ilk 10 klanı; klanın o hafta en çok XP kazandıran 10 üyesine posta.
-- 1. klan 10.000 altın + 100 elmas, 10. klan 1.000 altın + 10 elmas. İlk sıralama çağrısında bir kez dağıtılır.
create or replace function public._zx_clan_payout() returns void
language plpgsql security definer set search_path = public as $$
declare last date := (select (value #>> '{}')::date from public.app_config where key = 'clan_payout_week'); cur date := public._zx_week();
        r record; k int := 0;
begin
  if last is null or last >= cur then return; end if;
  update public.app_config set value = to_jsonb(cur::text), updated_at = now() where key = 'clan_payout_week'
    and (value #>> '{}')::date < cur;
  if not found then return; end if;  -- başka bir çağrı dağıtıyor
  for r in select clan_id, sum(xp) s from public.clan_xp where week = last group by clan_id having sum(xp) > 0 order by s desc limit 10 loop
    k := k + 1;
    insert into public.mail (account_id, sender, title, body, gold, gems, kind)
    select x.account_id, 'Klan Ligi', 'Haftalık Klan Ödülü · #' || k,
      'Klanın geçen hafta ' || k || '. oldu! En çok katkı verenlerden birisin. Elmaslar hesabına bağlıdır.',
      (11 - k) * 1000, (11 - k) * 10, 'event'
    from (select account_id from public.clan_xp where week = last and clan_id = r.clan_id and xp > 0
          and account_id in (select id from public.accounts where clan_id = r.clan_id) order by xp desc limit 10) x;
    insert into public.clan_log (clan_id, text) values (r.clan_id, 'Haftalık klan ligi: ' || k || '. sıra! Ödüller en aktif 10 üyeye gönderildi');
  end loop;
end $$;
revoke all on function public._zx_clan_payout() from public, anon, authenticated;

create or replace function public.zx_clan_ranking(p_kind text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform public._zx_clan_payout();
  if p_kind = 'week' then
    return coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'name', c.name, 'level', c.level, 'title', public._zx_clan_title(c.level),
        'xp', s.xp, 'top', public._zx_clan_top(c.id)) order by s.xp desc)
      from (select clan_id, sum(xp) xp from public.clan_xp where week = public._zx_week() group by clan_id order by 2 desc limit 30) s
      join public.clans c on c.id = s.clan_id), '[]'::jsonb);
  end if;
  return coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'name', c.name, 'level', c.level, 'title', public._zx_clan_title(c.level),
      'xp', c.xp_total, 'top', public._zx_clan_top(c.id)) order by c.xp_total desc)
    from (select * from public.clans where xp_total > 0 order by xp_total desc limit 30) c), '[]'::jsonb);
end $$;

-- Oyuncu sıralaması: seviye ya da öldürme
create or replace function public.zx_top2(p_kind text) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', a.id, 'name', a.name, 'avatar', a.avatar, 'frame', a.frame, 'v', s.v,
      'vip', a.vip_until is not null and a.vip_until > now(), 'clan', (select name from public.clans where id = a.clan_id)) order by s.v desc, s.t), '[]'::jsonb)
  from (select account_id, case when p_kind = 'kills' then sum(kills) else max(level) end v, min(updated_at) t
        from public.characters group by account_id order by 2 desc, 3 limit 50) s
  join public.accounts a on a.id = s.account_id
$$;

-- ================================================================ yönetici
create or replace function public._zx_admin(p_id bigint, p_key text) returns public.accounts
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if not a.is_admin then raise exception 'Bu işlem için yönetici yetkisi gerekli'; end if;
  return a;
end $$;
revoke all on function public._zx_admin(bigint, text) from public, anon, authenticated;

create or replace function public.zx_admin_maintenance(p_id bigint, p_key text, p_on boolean, p_msg text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); v jsonb;
begin
  v := jsonb_build_object('on', p_on, 'msg', left(coalesce(p_msg, ''), 200), 'by', a.name, 'at', now());
  update public.app_config set value = v, updated_at = now() where key = 'maintenance';
  return v;
end $$;

create or replace function public.zx_admin_find(p_id bigint, p_key text, p_name text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id', t.id, 'name', t.name, 'level', public._zx_level(t.id), 'kills', public._zx_kills(t.id),
      'last_seen', t.last_seen, 'banned_until', t.banned_until, 'ban_reason', t.ban_reason, 'vip_until', t.vip_until,
      'admin', t.is_admin, 'clan', (select name from public.clans where id = t.clan_id)))
    from (select * from public.accounts where name ilike '%' || trim(p_name) || '%' or id::text = trim(p_name) order by last_seen desc limit 20) t), '[]'::jsonb);
end $$;

-- Ban: saat (0 = kaldır, 87600 = kalıcı sayılır)
create or replace function public.zx_admin_ban(p_id bigint, p_key text, p_target bigint, p_hours int, p_reason text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if t.is_admin then raise exception 'Yönetici yasaklanamaz'; end if;
  update public.accounts set banned_until = case when p_hours <= 0 then null else now() + make_interval(hours => p_hours) end,
    ban_reason = left(coalesce(p_reason, ''), 120) where id = t.id;
  return case when p_hours <= 0 then t.name || ' yasağı kaldırıldı' else t.name || ' ' || p_hours || ' saat yasaklandı' end;
end $$;

create or replace function public.zx_admin_vip(p_id bigint, p_key text, p_target bigint, p_days int) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts;
begin
  update public.accounts set vip_until = case when p_days <= 0 then null else greatest(coalesce(vip_until, now()), now()) + make_interval(days => p_days) end
  where id = p_target returning * into t;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  return t.name || case when p_days <= 0 then ' VIP kaldırıldı' else ' VIP +' || p_days || ' gün' end;
end $$;

-- Yönetici postası: altın, elmas, XP, eşya; alıcı ad ya da '*' (herkes)
create or replace function public.zx_admin_mail2(p_id bigint, p_key text, p_to text, p_title text, p_body text,
  p_gold int, p_gems int, p_xp int, p_items jsonb, p_kind text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); n int := 0; k text := case when p_kind in ('reward','event','system') then p_kind else 'reward' end;
begin
  if trim(coalesce(p_to, '')) = '*' then
    insert into public.mail (account_id, sender, title, body, gold, gems, xp, items, kind)
    select id, 'Zenvex', left(p_title, 40), left(p_body, 500), greatest(p_gold, 0), greatest(p_gems, 0), greatest(p_xp, 0), coalesce(p_items, '[]'), k
    from public.accounts;
  else
    insert into public.mail (account_id, sender, title, body, gold, gems, xp, items, kind)
    select id, 'Zenvex', left(p_title, 40), left(p_body, 500), greatest(p_gold, 0), greatest(p_gems, 0), greatest(p_xp, 0), coalesce(p_items, '[]'), k
    from public.accounts where lower(name) = lower(trim(p_to)) or id::text = trim(p_to);
  end if;
  get diagnostics n = row_count;
  if n = 0 then raise exception 'Oyuncu bulunamadı'; end if;
  return jsonb_build_object('sent', n);
end $$;

-- ================================================================ yetkiler: yeni RPC'ler anon ile çağrılır (kimlik + anahtar içeride doğrulanır)
grant execute on function public.zx_status() to anon, authenticated;
grant execute on function public.zx_profile_set(bigint, text, int, text) to anon, authenticated;
grant execute on function public.zx_profile_view(text) to anon, authenticated;
grant execute on function public.zx_save2(bigint, text, text, int, text, int, bigint) to anon, authenticated;
grant execute on function public.zx_mail_favorite(bigint, text, bigint, boolean) to anon, authenticated;
grant execute on function public.zx_mail_remove(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_friend_request(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_friend_respond(bigint, text, bigint, boolean) to anon, authenticated;
grant execute on function public.zx_friend_remove(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_friends(bigint, text) to anon, authenticated;
grant execute on function public.zx_dm_send(bigint, text, bigint, text) to anon, authenticated;
grant execute on function public.zx_dm_list(bigint, text, bigint, bigint) to anon, authenticated;
grant execute on function public.zx_clan_info(bigint) to anon, authenticated;
grant execute on function public.zx_clan_invite(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_clan_invites(bigint, text) to anon, authenticated;
grant execute on function public.zx_clan_set_rank(bigint, text, bigint, int) to anon, authenticated;
grant execute on function public.zx_clan_kick(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_clan_mute(bigint, text, bigint, int) to anon, authenticated;
grant execute on function public.zx_clan_deposit(bigint, text, text, int) to anon, authenticated;
grant execute on function public.zx_clan_send(bigint, text, bigint, text, int, text) to anon, authenticated;
grant execute on function public.zx_clan_settings(bigint, text, boolean, text) to anon, authenticated;
grant execute on function public.zx_clan_boost(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_clan_ranking(text) to anon, authenticated;
grant execute on function public.zx_top2(text) to anon, authenticated;
grant execute on function public.zx_admin_maintenance(bigint, text, boolean, text) to anon, authenticated;
grant execute on function public.zx_admin_find(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_admin_ban(bigint, text, bigint, int, text) to anon, authenticated;
grant execute on function public.zx_admin_vip(bigint, text, bigint, int) to anon, authenticated;
grant execute on function public.zx_admin_mail2(bigint, text, text, text, text, int, int, int, jsonb, text) to anon, authenticated;

-- veri: mevcut klan sahipleri lider rütbesine
update public.accounts a set clan_rank = 3, clan_joined_at = coalesce(clan_joined_at, now()) from public.clans c where c.owner = a.id and a.clan_id = c.id;
update public.accounts set clan_joined_at = coalesce(clan_joined_at, created_at) where clan_id is not null;
