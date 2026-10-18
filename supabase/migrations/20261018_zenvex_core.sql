-- Zenvex Online — Supabase şeması.
-- Hesap = Zenvex ID + hesap anahtarı (Supabase Auth kullanılmaz; panelde ayar gerekmez).
-- Tablolara doğrudan erişim kapalıdır (RLS açık, politika yok); istemci yalnızca zx_* RPC'lerini çağırır.
-- Uygulamak için: Supabase paneli → SQL Editor → bu dosyanın tamamını yapıştır → Run.

create extension if not exists pgcrypto with schema extensions;

create table public.clans (
  id bigint generated always as identity primary key,
  name text not null unique check (char_length(name) between 3 and 20),
  color int not null default 0,
  owner bigint not null,
  created_at timestamptz not null default now()
);

create table public.accounts (
  id bigint generated always as identity (start with 1000001) primary key,
  key_hash text not null,
  name text not null unique check (char_length(name) between 3 and 16),
  clan_id bigint references public.clans(id) on delete set null,
  created_at timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  key_changed_at timestamptz not null default now(),
  last_chat timestamptz not null default 'epoch'
);

create table public.characters (
  account_id bigint not null references public.accounts(id) on delete cascade,
  server text not null check (server in ('n1','n2','n3','hc')),
  level int not null default 1,
  data text not null default '',
  updated_at timestamptz not null default now(),
  primary key (account_id, server)
);
create index on public.characters (server, updated_at desc);
create index on public.characters (server, level desc);

create table public.chat (
  id bigint generated always as identity primary key,
  server text not null,
  account_id bigint not null references public.accounts(id) on delete cascade,
  name text not null,
  clan text,
  text text not null check (char_length(text) between 1 and 140),
  created_at timestamptz not null default now()
);
create index on public.chat (server, id desc);

create table public.mail (
  id bigint generated always as identity primary key,
  account_id bigint not null references public.accounts(id) on delete cascade,
  sender text not null default 'Sistem',
  title text not null,
  body text not null default '',
  gold int not null default 0,
  items jsonb not null default '[]',
  read boolean not null default false,
  claimed boolean not null default false,
  created_at timestamptz not null default now()
);
create index on public.mail (account_id, id desc);

alter table public.clans enable row level security;
alter table public.accounts enable row level security;
alter table public.characters enable row level security;
alter table public.chat enable row level security;
alter table public.mail enable row level security;

-- ---------------------------------------------------------------- yardımcılar (istemciye kapalı)
create or replace function public._zx_auth(p_id bigint, p_key text) returns public.accounts
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts;
begin
  select * into a from public.accounts where id = p_id;
  if a.id is null or a.key_hash <> crypt(p_key, a.key_hash) then
    raise exception 'Hesap kimliği veya anahtar hatalı' using errcode = '28000';
  end if;
  update public.accounts set last_seen = now() where id = p_id;
  return a;
end $$;

create or replace function public._zx_key() returns text
language sql volatile set search_path = public, extensions as $$
  select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', (get_byte(b, i) % 32) + 1, 1), '')
  from (select gen_random_bytes(12) b) s, generate_series(0, 11) i
$$;

create or replace function public._zx_profile(a public.accounts) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', a.id, 'name', a.name,
    'clan', (select jsonb_build_object('id', c.id, 'name', c.name, 'color', c.color, 'owner', c.owner = a.id)
             from public.clans c where c.id = a.clan_id),
    'chars', coalesce((select jsonb_object_agg(server, level) from public.characters where account_id = a.id), '{}'::jsonb),
    'unread', (select count(*) from public.mail where account_id = a.id and not read),
    'key_changed_at', a.key_changed_at)
$$;

create or replace function public._zx_name_ok(n text) returns boolean
language sql immutable as $$ select n ~ '^[A-Za-z0-9_ÇçĞğİıÖöŞşÜü.\-]{3,16}$' $$;

revoke all on function public._zx_auth(bigint, text) from public, anon, authenticated;
revoke all on function public._zx_key() from public, anon, authenticated;
revoke all on function public._zx_profile(public.accounts) from public, anon, authenticated;

-- ---------------------------------------------------------------- hesap
create or replace function public.zx_register(p_name text) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare k text := public._zx_key(); a public.accounts; n text := trim(p_name);
begin
  if not public._zx_name_ok(n) then raise exception 'İsim 3-16 harf/rakam olmalı'; end if;
  if exists (select 1 from public.accounts where lower(name) = lower(n)) then raise exception 'Bu isim alınmış'; end if;
  insert into public.accounts (key_hash, name) values (crypt(k, gen_salt('bf', 8)), n) returning * into a;
  insert into public.mail (account_id, title, body, gold, items)
  values (a.id, 'Hoş geldin!', 'Zenvex Online''a hoş geldin. Başlangıç hediyen ekte.', 250,
          '[{"id":"bandage","n":5},{"id":"food","n":3}]');
  return jsonb_build_object('key', k) || public._zx_profile(a);
end $$;

create or replace function public.zx_login(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return public._zx_profile(a);
end $$;

create or replace function public.zx_change_key(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key); k text := public._zx_key();
begin
  update public.accounts set key_hash = crypt(k, gen_salt('bf', 8)), key_changed_at = now() where id = a.id;
  return jsonb_build_object('key', k);
end $$;

create or replace function public.zx_rename(p_id bigint, p_key text, p_name text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); n text := trim(p_name);
begin
  if not public._zx_name_ok(n) then raise exception 'İsim 3-16 harf/rakam olmalı'; end if;
  if exists (select 1 from public.accounts where lower(name) = lower(n) and id <> a.id) then raise exception 'Bu isim alınmış'; end if;
  update public.accounts set name = n where id = a.id returning * into a;
  return public._zx_profile(a);
end $$;

-- ---------------------------------------------------------------- karakter kaydı (sunucu başına)
create or replace function public.zx_save(p_id bigint, p_key text, p_server text, p_level int, p_data text) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if char_length(p_data) > 400000 then raise exception 'Kayıt çok büyük'; end if;
  insert into public.characters (account_id, server, level, data, updated_at)
  values (a.id, p_server, greatest(1, least(p_level, 999)), p_data, now())
  on conflict (account_id, server) do update set level = excluded.level, data = excluded.data, updated_at = now();
  return true;
end $$;

create or replace function public.zx_load(p_id bigint, p_key text, p_server text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return (select data from public.characters where account_id = a.id and server = p_server);
end $$;

create or replace function public.zx_servers() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_object_agg(server, n), '{}'::jsonb) from (
    select server, count(*) n from public.characters where updated_at > now() - interval '3 minutes' group by server) s
$$;

create or replace function public.zx_top(p_server text) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('name', a.name, 'level', c.level, 'clan', cl.name)
         order by c.level desc, c.updated_at), '[]'::jsonb)
  from (select * from public.characters where server = p_server order by level desc, updated_at limit 30) c
  join public.accounts a on a.id = c.account_id left join public.clans cl on cl.id = a.clan_id
$$;

-- ---------------------------------------------------------------- sohbet (geçmiş; canlı akış Realtime yayınıyla)
create or replace function public.zx_chat_post(p_id bigint, p_key text, p_server text, p_text text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t text := left(trim(p_text), 140); r public.chat;
begin
  if t = '' then raise exception 'Boş mesaj'; end if;
  if a.last_chat > now() - interval '1500 milliseconds' then raise exception 'Çok hızlı yazıyorsun'; end if;
  update public.accounts set last_chat = now() where id = a.id;
  insert into public.chat (server, account_id, name, clan, text)
  values (p_server, a.id, a.name, (select name from public.clans where id = a.clan_id), t) returning * into r;
  delete from public.chat where server = p_server and id < r.id - 500;
  return jsonb_build_object('id', r.id, 'ts', (extract(epoch from r.created_at) * 1000)::bigint);
end $$;

create or replace function public.zx_chat_recent(p_server text) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'u', account_id, 'n', name, 'g', clan, 'm', text,
    'ts', (extract(epoch from created_at) * 1000)::bigint) order by id), '[]'::jsonb)
  from (select * from public.chat where server = p_server order by id desc limit 30) s
$$;

-- ---------------------------------------------------------------- posta
create or replace function public.zx_mail_list(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); r jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'from', sender, 'title', title, 'body', body, 'gold', gold,
    'items', items, 'read', read, 'claimed', claimed, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc), '[]'::jsonb)
  into r from (select * from public.mail where account_id = a.id order by id desc limit 50) m;
  update public.mail set read = true where account_id = a.id and not read;
  return r;
end $$;

create or replace function public.zx_mail_claim(p_id bigint, p_key text, p_mail bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); m public.mail;
begin
  update public.mail set claimed = true, read = true
  where id = p_mail and account_id = a.id and not claimed returning * into m;
  if m.id is null then raise exception 'Bu posta zaten alınmış'; end if;
  return jsonb_build_object('gold', m.gold, 'items', m.items);
end $$;

create or replace function public.zx_mail_send(p_id bigint, p_key text, p_to text, p_title text, p_body text) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t bigint;
begin
  select id into t from public.accounts where lower(name) = lower(trim(p_to));
  if t is null then raise exception 'Oyuncu bulunamadı'; end if;
  if (select count(*) from public.mail where sender = a.name and created_at > now() - interval '1 hour') >= 20 then
    raise exception 'Saatlik posta sınırı doldu'; end if;
  insert into public.mail (account_id, sender, title, body) values (t, a.name, left(p_title, 40), left(p_body, 500));
  return true;
end $$;

-- ---------------------------------------------------------------- klan
create or replace function public.zx_clan_create(p_id bigint, p_key text, p_name text, p_color int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); c public.clans;
begin
  if a.clan_id is not null then raise exception 'Önce klanından ayrıl'; end if;
  if exists (select 1 from public.clans where lower(name) = lower(trim(p_name))) then raise exception 'Bu klan adı alınmış'; end if;
  insert into public.clans (name, color, owner) values (trim(p_name), p_color, a.id) returning * into c;
  update public.accounts set clan_id = c.id where id = a.id returning * into a;
  return public._zx_profile(a);
end $$;

create or replace function public.zx_clan_join(p_id bigint, p_key text, p_clan bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if a.clan_id is not null then raise exception 'Önce klanından ayrıl'; end if;
  if not exists (select 1 from public.clans where id = p_clan) then raise exception 'Klan bulunamadı'; end if;
  if (select count(*) from public.accounts where clan_id = p_clan) >= 30 then raise exception 'Klan dolu (30)'; end if;
  update public.accounts set clan_id = p_clan where id = a.id returning * into a;
  return public._zx_profile(a);
end $$;

create or replace function public.zx_clan_leave(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); c bigint := a.clan_id;
begin
  update public.accounts set clan_id = null where id = a.id returning * into a;
  if c is not null then
    if not exists (select 1 from public.accounts where clan_id = c) then
      delete from public.clans where id = c;
    elsif (select owner from public.clans where id = c) = a.id then
      update public.clans set owner = (select id from public.accounts where clan_id = c order by created_at limit 1) where id = c;
    end if;
  end if;
  return public._zx_profile(a);
end $$;

create or replace function public.zx_clans() returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', c.id, 'name', c.name, 'color', c.color, 'members', c.n) order by c.n desc), '[]'::jsonb)
  from (select c.*, (select count(*) from public.accounts a where a.clan_id = c.id) n from public.clans c) c
$$;

create or replace function public.zx_clan_members(p_clan bigint) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('name', a.name, 'owner', c.owner = a.id,
    'level', (select max(level) from public.characters where account_id = a.id),
    'online', a.last_seen > now() - interval '3 minutes') order by a.last_seen desc), '[]'::jsonb)
  from public.accounts a join public.clans c on c.id = a.clan_id where a.clan_id = p_clan
$$;
