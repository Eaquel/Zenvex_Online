-- V9: tamamen çevrimiçi oyun. Oturum (tek cihaz), nabız, imzalı ve doğrulanan kayıt, cihaz izi, hile kayıtları,
-- sunucu kayıtlı elmas defteri ve emanetli (escrow) takas.

-- ================================================================ tablolar
create table if not exists public.sessions (
  account_id bigint primary key references public.accounts(id) on delete cascade,
  sid uuid not null,
  secret text not null,
  server text not null,
  device text not null default '',
  started_at timestamptz not null default now(),
  last_beat timestamptz not null default now(),
  seq bigint not null default 0,
  last_save timestamptz,
  game_time double precision not null default 0
);
alter table public.sessions enable row level security;

create table if not exists public.devices (
  account_id bigint not null references public.accounts(id) on delete cascade,
  device text not null,
  model text not null default '',
  info jsonb not null default '{}',
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  primary key (account_id, device)
);
create index if not exists devices_dev on public.devices (device);
alter table public.devices enable row level security;

create table if not exists public.banned_devices (
  device text primary key,
  reason text not null default '',
  created_at timestamptz not null default now()
);
alter table public.banned_devices enable row level security;

create table if not exists public.cheat_flags (
  id bigint generated always as identity primary key,
  account_id bigint not null references public.accounts(id) on delete cascade,
  kind text not null,
  detail text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists cheat_flags_acc on public.cheat_flags (account_id, id desc);
alter table public.cheat_flags enable row level security;

-- sunucunun verdiği elmas / altın / eşya (posta, etkinlik, klan, takas): kayıt doğrulamasında "meşru kazanç" sayılır
create table if not exists public.grants (
  id bigint generated always as identity primary key,
  account_id bigint not null references public.accounts(id) on delete cascade,
  gems int not null default 0,
  gold bigint not null default 0,
  levels int not null default 0,
  note text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists grants_acc on public.grants (account_id, created_at desc);
alter table public.grants enable row level security;

-- emanetli takas
create table if not exists public.trades (
  id bigint generated always as identity primary key,
  a bigint not null references public.accounts(id) on delete cascade,
  b bigint not null references public.accounts(id) on delete cascade,
  server text not null,
  a_items jsonb not null default '[]',
  b_items jsonb not null default '[]',
  a_ready boolean not null default false,
  b_ready boolean not null default false,
  a_claimed boolean not null default false,
  b_claimed boolean not null default false,
  state text not null default 'open' check (state in ('open', 'done', 'cancel')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists trades_a on public.trades (a, id desc);
create index if not exists trades_b on public.trades (b, id desc);
alter table public.trades enable row level security;

alter table public.characters add column if not exists play_seconds bigint not null default 0;

-- ================================================================ yardımcılar
create or replace function public._zx_flag(p bigint, k text, d text) returns void
language sql security definer set search_path = public as $$
  insert into public.cheat_flags (account_id, kind, detail) values (p, k, left(d, 400))
$$;
revoke all on function public._zx_flag(bigint, text, text) from public, anon, authenticated;

create or replace function public._zx_maint_check(a public.accounts) returns void
language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  select value into v from public.app_config where key = 'maintenance';
  if coalesce((v ->> 'on')::boolean, false) and not (a.id = 0 and a.is_admin) then
    raise exception 'BAKIM|%', coalesce(v ->> 'msg', '') using errcode = '28000';
  end if;
end $$;
revoke all on function public._zx_maint_check(public.accounts) from public, anon, authenticated;

-- kayıttaki bir eşyanın toplamı (çanta + sandıklar)
create or replace function public._zx_item_total(d jsonb, item int) returns bigint
language sql immutable as $$
  select coalesce((select sum((e ->> 'count')::bigint) from jsonb_array_elements(coalesce(d #> '{inv,slots}', '[]')) e
                   where jsonb_typeof(e) = 'object' and (e ->> 'item')::int = item), 0)
       + coalesce((select sum((e ->> 'count')::bigint)
                   from jsonb_array_elements(coalesce(d -> 'chests', '[]')) c,
                        jsonb_array_elements(coalesce(c -> 'slots', '[]')) e
                   where jsonb_typeof(e) = 'object' and (e ->> 'item')::int = item), 0)
$$;

-- oturum doğrulama: geçerli oturum + nabız 90 sn içinde
create or replace function public._zx_session(a public.accounts, p_sid uuid) returns public.sessions
language plpgsql security definer set search_path = public as $$
declare s public.sessions;
begin
  select * into s from public.sessions where account_id = a.id;
  if s.account_id is null or s.sid <> p_sid then
    raise exception 'OTURUM|Hesabına başka bir cihazdan girildi' using errcode = '28000';
  end if;
  if s.last_beat < now() - interval '90 seconds' then
    raise exception 'OTURUM|Bağlantı koptu, yeniden gir' using errcode = '28000';
  end if;
  return s;
end $$;
revoke all on function public._zx_session(public.accounts, uuid) from public, anon, authenticated;

-- ================================================================ oturum
-- Oyuna giriş: bakım/yasak/cihaz yasağı kontrolü, cihaz kaydı, tek cihaz (eski oturum düşer), sunucudaki kayıt döner.
create or replace function public.zx_session_start(p_id bigint, p_key text, p_server text, p_device jsonb) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key); dev text := left(coalesce(p_device ->> 'id', ''), 80);
        s public.sessions; ch public.characters; br public.banned_devices;
begin
  perform public._zx_maint_check(a);
  if p_server not in ('n1', 'n2', 'n3', 'hc') then raise exception 'Geçersiz sunucu'; end if;
  if dev = '' then raise exception 'Cihaz doğrulanamadı'; end if;
  select * into br from public.banned_devices where device = dev;
  if br.device is not null and a.id <> 0 then
    raise exception 'BAN|Kalıcı|Bu cihaz yasaklı: %', br.reason using errcode = '28000';
  end if;
  -- istemci katmanları: imza, hata ayıklayıcı, kanca (Frida/Xposed) → giriş yok; root/emülatör → yalnız kayıt
  if coalesce((p_device ->> 'sig')::boolean, true) = false or coalesce((p_device ->> 'hook')::boolean, false)
     or coalesce((p_device ->> 'dbg')::boolean, false) then
    perform public._zx_flag(a.id, 'client', 'Giriş reddedildi: ' || p_device::text);
    if a.id <> 0 then raise exception 'Güvenlik kontrolü başarısız: uygulama değiştirilmiş ya da izleniyor'; end if;
  end if;
  if coalesce((p_device ->> 'root')::boolean, false) or coalesce((p_device ->> 'emu')::boolean, false) then
    if not exists (select 1 from public.cheat_flags where account_id = a.id and kind = 'device' and created_at > now() - interval '1 day') then
      perform public._zx_flag(a.id, 'device', 'Root/Emülatör: ' || p_device::text);
    end if;
  end if;
  insert into public.devices (account_id, device, model, info) values (a.id, dev, left(coalesce(p_device ->> 'model', ''), 80), p_device)
  on conflict (account_id, device) do update set last_seen = now(), info = excluded.info, model = excluded.model;
  insert into public.sessions (account_id, sid, secret, server, device, started_at, last_beat, seq, last_save, game_time)
  values (a.id, gen_random_uuid(), encode(gen_random_bytes(24), 'hex'), p_server, dev, now(), now(), 0, null, 0)
  on conflict (account_id) do update set sid = excluded.sid, secret = excluded.secret, server = excluded.server, device = excluded.device,
    started_at = now(), last_beat = now(), seq = 0, last_save = null, game_time = 0
  returning * into s;
  select * into ch from public.characters where account_id = a.id and server = p_server;
  perform public._zx_log(a.id, 'session', 'Oyuna girdi (' || p_server || ', ' || coalesce(p_device ->> 'model', '?') || ')');
  return jsonb_build_object('sid', s.sid, 'secret', s.secret, 'data', coalesce(ch.data, ''), 'time', (extract(epoch from now()) * 1000)::bigint,
    'admin', a.id = 0 and a.is_admin);
end $$;

-- Nabız (15 sn'de bir): oturum, bakım, yasak; hız hilesi kontrolü (oyun saati gerçek saatten hızlı akamaz)
create or replace function public.zx_heartbeat(p_id bigint, p_key text, p_sid uuid, p_game_time double precision) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; wall double precision; dg double precision;
begin
  perform public._zx_maint_check(a);
  select * into s from public.sessions where account_id = a.id;
  if s.account_id is null or s.sid <> p_sid then
    raise exception 'OTURUM|Hesabına başka bir cihazdan girildi' using errcode = '28000';
  end if;
  wall := extract(epoch from now() - s.last_beat);
  dg := p_game_time - s.game_time;
  if s.game_time > 0 and dg > wall * 1.25 + 8 and a.id <> 0 then
    perform public._zx_flag(a.id, 'speed', 'Oyun saati ' || round(dg::numeric, 1) || ' sn, gerçek ' || round(wall::numeric, 1) || ' sn');
  end if;
  update public.sessions set last_beat = now(), game_time = greatest(p_game_time, 0) where account_id = a.id;
  return jsonb_build_object('ok', true, 'time', (extract(epoch from now()) * 1000)::bigint,
    'actions', (select count(*) from public.admin_actions where account_id = a.id and done_at is null),
    'trade', (select id from public.trades where (a = a.id or b = a.id) and state <> 'cancel'
               and ((a = a.id and not a_claimed) or (b = a.id and not b_claimed)) and state = 'done' order by id desc limit 1));
end $$;

create or replace function public.zx_session_end(p_id bigint, p_key text, p_sid uuid) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  delete from public.sessions where account_id = a.id and sid = p_sid;
  return true;
end $$;

-- ================================================================ doğrulanan kayıt
-- İmza: hmac_sha256(secret, seq || ':' || data). Kurallar (yönetici hariç):
--  - oturum geçerli, seq artıyor, imza doğru
--  - elmas artışı ≤ sunucunun verdiği elmas + 3 + dakika başı 2
--  - altın artışı ≤ sunucunun verdiği altın + 3000 + dakika başı 4000
--  - seviye artışı ≤ 2 + dakika başı 1
-- İhlalde kayıt reddedilir, hile kaydı düşer, istemci sunucudaki son kayda geri döner.
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
  return r;
end $$;

-- posta alındığında meşru kazanç defterine yazılır (elmas/altın/XP seviyesi)
create or replace function public.zx_mail_claim(p_id bigint, p_key text, p_mail bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); m public.mail;
begin
  update public.mail set claimed = true, read = true
  where id = p_mail and account_id = a.id and not claimed and not deleted
    and (favorite or created_at > now() - interval '30 days') returning * into m;
  if m.id is null then raise exception 'Bu posta zaten alınmış'; end if;
  insert into public.grants (account_id, gems, gold, levels, note)
  values (a.id, m.gems, m.gold, case when m.xp > 0 then 3 + m.xp / 5000 else 0 end, 'posta ' || m.id);
  perform public._zx_log(a.id, 'mail', 'Posta aldı: ' || m.title || ' (' || m.gold || ' altın, ' || m.gems || ' elmas, ' || m.items::text || ')');
  return jsonb_build_object('gold', m.gold, 'xp', m.xp, 'gems', m.gems, 'items', m.items);
end $$;

-- ================================================================ emanetli takas
-- 1) A, B'ye takas açar (ikisi de aynı sunucuda oturumda). 2) Her biri teklifini "hazır"la gönderir: teklif edilen eşyalar
-- istemcide çantadan düşülmüş kayıtla birlikte gelir; sunucu eski kayıtta o eşyaların gerçekten olduğunu ve yeni kayıtta
-- tam o kadar azaldığını doğrular (eşya sunucuda emanette). 3) İki taraf da hazırsa takas biter; her taraf karşı tarafın
-- eşyasını bir kez alır (zx_trade_claim). İptalde herkes kendi emanetini geri alır.
create or replace function public.zx_trade_open(p_id bigint, p_key text, p_sid uuid, p_other bigint) returns bigint
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; o public.sessions; t bigint;
begin
  s := public._zx_session(a, p_sid);
  select * into o from public.sessions where account_id = p_other and last_beat > now() - interval '90 seconds';
  if o.account_id is null or o.server <> s.server then raise exception 'Oyuncu çevrimiçi değil'; end if;
  if p_other = a.id then raise exception 'Kendinle takas yapamazsın'; end if;
  update public.trades set state = 'cancel', updated_at = now()
  where state = 'open' and (a = a.id or b = a.id) and not a_ready and not b_ready;
  insert into public.trades (a, b, server) values (a.id, p_other, s.server) returning id into t;
  return t;
end $$;

create or replace function public.zx_trade_ready(p_id bigint, p_key text, p_sid uuid, p_trade bigint, p_items jsonb,
  p_seq bigint, p_sig text, p_level int, p_data text, p_kills int, p_xp bigint) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; t public.trades; old public.characters;
        od jsonb; nd jsonb; it jsonb; idx int; n bigint; side text;
begin
  s := public._zx_session(a, p_sid);
  select * into t from public.trades where id = p_trade for update;
  if t.id is null or t.state <> 'open' then raise exception 'Takas kapandı'; end if;
  if t.a = a.id then side := 'a'; elsif t.b = a.id then side := 'b'; else raise exception 'Bu takas senin değil'; end if;
  if (side = 'a' and t.a_ready) or (side = 'b' and t.b_ready) then raise exception 'Teklifin zaten kilitli'; end if;
  if jsonb_typeof(coalesce(p_items, '[]')) <> 'array' or jsonb_array_length(coalesce(p_items, '[]')) > 12 then raise exception 'Geçersiz teklif'; end if;
  select * into old from public.characters where account_id = a.id and server = s.server;
  if old.account_id is null then raise exception 'Önce oyunu kaydet'; end if;
  od := old.data::jsonb;
  begin nd := p_data::jsonb; exception when others then raise exception 'Bozuk kayıt'; end;
  for it in select * from jsonb_array_elements(coalesce(p_items, '[]')) loop
    idx := (it ->> 'item')::int; n := (it ->> 'n')::bigint;
    if idx is null or n is null or n <= 0 or n > 9999 then raise exception 'Geçersiz eşya'; end if;
    if public._zx_item_total(od, idx) < n then
      perform public._zx_flag(a.id, 'trade', 'Olmayan eşya teklif edildi: #' || idx || ' x' || n);
      raise exception 'Teklif ettiğin eşya çantanda yok';
    end if;
    if public._zx_item_total(nd, idx) <> public._zx_item_total(od, idx) - n then
      raise exception 'Eşya emanete alınamadı, tekrar dene';
    end if;
  end loop;
  -- kayıt (imzalı) emanetten sonraki hâli
  perform public.zx_save3(p_id, p_key, p_sid, p_seq, p_sig, p_level, p_data, p_kills, p_xp);
  if side = 'a' then update public.trades set a_items = p_items, a_ready = true, updated_at = now() where id = t.id returning * into t;
  else update public.trades set b_items = p_items, b_ready = true, updated_at = now() where id = t.id returning * into t; end if;
  if t.a_ready and t.b_ready then
    update public.trades set state = 'done', updated_at = now() where id = t.id returning * into t;
    perform public._zx_log(t.a, 'trade', 'Takas #' || t.id || ' tamamlandı: verdi ' || t.a_items::text || ', aldı ' || t.b_items::text);
    perform public._zx_log(t.b, 'trade', 'Takas #' || t.id || ' tamamlandı: verdi ' || t.b_items::text || ', aldı ' || t.a_items::text);
  end if;
  return jsonb_build_object('state', t.state, 'a_ready', t.a_ready, 'b_ready', t.b_ready);
end $$;

create or replace function public.zx_trade_cancel(p_id bigint, p_key text, p_trade bigint) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  update public.trades set state = 'cancel', updated_at = now() where id = p_trade and state = 'open' and (a = a.id or b = a.id);
  return true;
end $$;

-- Takas sonucu: bitti → karşı tarafın eşyası; iptal → kendi emanetin. Bir kez verilir.
create or replace function public.zx_trade_claim(p_id bigint, p_key text, p_trade bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t public.trades; out jsonb := '[]';
begin
  select * into t from public.trades where id = p_trade for update;
  if t.id is null or (t.a <> a.id and t.b <> a.id) then raise exception 'Takas bulunamadı'; end if;
  if t.state = 'open' then return jsonb_build_object('state', 'open', 'a_ready', t.a_ready, 'b_ready', t.b_ready, 'items', '[]'::jsonb); end if;
  if t.a = a.id and not t.a_claimed then
    out := case when t.state = 'done' then t.b_items when t.a_ready then t.a_items else '[]' end;
    update public.trades set a_claimed = true where id = t.id;
  elsif t.b = a.id and not t.b_claimed then
    out := case when t.state = 'done' then t.a_items when t.b_ready then t.b_items else '[]' end;
    update public.trades set b_claimed = true where id = t.id;
  end if;
  return jsonb_build_object('state', t.state, 'items', out);
end $$;

-- ================================================================ yönetici: cihaz yasağı, hile kayıtları
create or replace function public.zx_admin_flags(p_id bigint, p_key text, p_target bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return jsonb_build_object(
    'flags', coalesce((select jsonb_agg(jsonb_build_object('k', kind, 't', detail, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
                       from (select * from public.cheat_flags where account_id = p_target order by id desc limit 100) x), '[]'::jsonb),
    'devices', coalesce((select jsonb_agg(jsonb_build_object('id', device, 'model', model, 'info', info,
                          'banned', exists (select 1 from public.banned_devices b where b.device = d.device),
                          'others', (select count(distinct account_id) - 1 from public.devices e where e.device = d.device),
                          'last', (extract(epoch from last_seen) * 1000)::bigint) order by last_seen desc)
                         from public.devices d where account_id = p_target), '[]'::jsonb),
    'online', exists (select 1 from public.sessions where account_id = p_target and last_beat > now() - interval '90 seconds'));
end $$;

create or replace function public.zx_admin_ban_device(p_id bigint, p_key text, p_device text, p_on boolean, p_reason text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  if p_on then
    insert into public.banned_devices (device, reason) values (p_device, left(coalesce(p_reason, ''), 120))
    on conflict (device) do update set reason = excluded.reason;
    delete from public.sessions where device = p_device and account_id <> 0;
    return 'Cihaz yasaklandı';
  end if;
  delete from public.banned_devices where device = p_device;
  return 'Cihaz yasağı kaldırıldı';
end $$;

-- yasaklanan oyuncunun oturumu hemen düşer (nabız BAN alır)
create or replace function public.zx_admin_ban(p_id bigint, p_key text, p_target bigint, p_hours int, p_reason text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if t.is_admin then raise exception 'Yönetici yasaklanamaz'; end if;
  update public.accounts set banned_until = case when p_hours <= 0 then null else now() + make_interval(hours => p_hours) end,
    ban_reason = left(coalesce(p_reason, ''), 120) where id = t.id;
  if p_hours > 0 then delete from public.sessions where account_id = t.id; end if;
  perform public._zx_log(t.id, 'admin', case when p_hours <= 0 then 'Yasak kaldırıldı' else 'Yasaklandı: ' || coalesce(p_reason, '') end);
  return case when p_hours <= 0 then t.name || ' yasağı kaldırıldı' else t.name || ' yasaklandı' end;
end $$;

-- ================================================================ yetkiler
grant execute on function public.zx_session_start(bigint, text, text, jsonb) to anon, authenticated;
grant execute on function public.zx_heartbeat(bigint, text, uuid, double precision) to anon, authenticated;
grant execute on function public.zx_session_end(bigint, text, uuid) to anon, authenticated;
grant execute on function public.zx_save3(bigint, text, uuid, bigint, text, int, text, int, bigint) to anon, authenticated;
grant execute on function public.zx_trade_open(bigint, text, uuid, bigint) to anon, authenticated;
grant execute on function public.zx_trade_ready(bigint, text, uuid, bigint, jsonb, bigint, text, int, text, int, bigint) to anon, authenticated;
grant execute on function public.zx_trade_cancel(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_trade_claim(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_admin_flags(bigint, text, bigint) to anon, authenticated;
grant execute on function public.zx_admin_ban_device(bigint, text, text, boolean, text) to anon, authenticated;
-- eski kayıt yolu artık kullanılmaz (doğrulamasız)
revoke execute on function public.zx_save2(bigint, text, text, int, text, int, bigint) from anon, authenticated;
