-- Etkinlik ödülleri v2 ve sıralama önbelleği.
-- 1) İlk 3 ödülü: 1. yüzük, 2. kolye, 3. etkinlik silahı (kask kalktı). İlk 3 ayrıca 30 gün takılabilen etkinlik unvanı alır
--    (ör. "Kan Ayı Kralı"), etkinlik bittikten sonra da 30 gün geçerli.
-- 2) Sıralama tabloları her 15 dakikada bir hesaplanır (board_cache); unvanlar da bu önbellekten okunur. Ad ve fotoğraf
--    önbellekte tutulmaz, hesap satırından canlı gelir (değişiklik anında görünür; istemci fotoğrafı 5 dk önbellekte tutar).

-- ---------------------------------------------------------------- etkinlik unvanları
create table if not exists public.event_titles (
  account_id bigint not null references public.accounts(id) on update cascade on delete cascade,
  kind text not null,                -- etkinlik türü (blood_moon ...)
  rank int not null,                 -- 1, 2, 3
  name text not null,
  expires_at timestamptz not null,
  primary key (account_id, kind)
);
alter table public.event_titles enable row level security;

create or replace function public._zx_event_rank_name(t text, r int) returns text
language sql immutable as $$
  select t || case r when 1 then ' Kralı' when 2 then ' Şampiyonu' else ' Savaşçısı' end
$$;

create or replace function public._zx_event_finalize(force boolean default false) returns void
language plpgsql security definer set search_path = public as $$
declare e public.events; r record; t text; pre text; it text; nm text; gems int;
begin
  for e in select * from public.events where active and not finalized and (force or ends_at <= now()) for update loop
    t := e.title; pre := public._zx_event_item(e.kind);
    for r in select s.account_id, s.score, row_number() over (order by s.score desc, s.updated_at) as rk
             from public.event_scores s where s.event_id = e.id and s.score > 0 order by s.score desc limit 100 loop
      if r.rk <= 3 then
        if r.rk = 1 then it := 'ev_' || pre || '_ring'; nm := t || ' Kralı Yüzüğü'; gems := 1000;
        elsif r.rk = 2 then it := 'ev_' || pre || '_neck'; nm := t || ' Şampiyonu Kolyesi'; gems := 700;
        else it := 'ev_' || pre || '_weapon'; nm := public._zx_event_weapon_name(pre); gems := 500; end if;
        insert into public.mail (account_id, sender, title, body, gems, items, kind)
        values (r.account_id, 'Zenvex', t || ': ' || r.rk || '. Sıra!', t || ' etkinliğini ' || r.rk || '. sırada bitirdin (' || r.score ||
          ' puan). Ödülün: ' || nm || ' (30 gün), ' || gems || ' elmas ve 30 gün takabileceğin "' || public._zx_event_rank_name(t, r.rk::int) ||
          '" unvanı. Tebrikler!', gems, jsonb_build_array(jsonb_build_object('id', it, 'n', 1)), 'event');
        insert into public.timed_items (account_id, item, expires_at) values (r.account_id, it, now() + interval '30 days')
        on conflict (account_id, item) do update set expires_at = excluded.expires_at, removed = false;
        insert into public.event_titles (account_id, kind, rank, name, expires_at)
        values (r.account_id, e.kind, r.rk, public._zx_event_rank_name(t, r.rk::int), now() + interval '30 days')
        on conflict (account_id, kind) do update set rank = excluded.rank, name = excluded.name, expires_at = excluded.expires_at;
      elsif r.rk <= 10 then
        insert into public.mail (account_id, sender, title, body, gems, items, kind)
        values (r.account_id, 'Zenvex', t || ': İlk 10', t || ' etkinliğini ' || r.rk || '. sırada bitirdin. Ödülün: 300 elmas, 5 titan parçası, 5 güç iksiri, 10 ilk yardım.',
          300, '[{"id":"titan_shard","n":5},{"id":"potion_power","n":5},{"id":"medkit","n":10}]'::jsonb, 'event');
      else
        insert into public.mail (account_id, sender, title, body, gems, items, kind)
        values (r.account_id, 'Zenvex', t || ': İlk 100', t || ' etkinliğini ' || r.rk || '. sırada bitirdin. Ödülün: 100 elmas ve 5 ilk yardım.',
          100, '[{"id":"medkit","n":5}]'::jsonb, 'event');
      end if;
    end loop;
    update public.events set active = false, finalized = true, ends_at = least(ends_at, now()) where id = e.id;
  end loop;
end $$;
revoke all on function public._zx_event_finalize(boolean) from public, anon, authenticated;

create or replace function public._zx_event_weapon_name(pre text) returns text
language sql immutable as $$
  select case pre when 'blood' then 'Kan Ayı Orağı' when 'titan' then 'Titan Kırıcı Balyoz' when 'base' then 'Fırtına Baltası'
    when 'scrap' then 'Hurdacı Testere Kılıcı' when 'pack' then 'Sürü Lideri Pençesi' when 'breath' then 'Son Nefes Tırpanı' else 'Etkinlik Silahı' end
$$;

-- ---------------------------------------------------------------- sıralama önbelleği (15 dk)
create table if not exists public.board_cache (
  kind text not null,
  account_id bigint not null,
  v bigint not null,
  rk bigint not null,
  at timestamptz not null default now(),   -- hesaplama kuşağı: geçerli satırlar board_meta.at ile eşleşenler
  primary key (kind, account_id)
);
create index if not exists board_cache_rank on public.board_cache (kind, rk);
create table if not exists public.board_meta (kind text primary key, at timestamptz not null);
alter table public.board_cache enable row level security;
alter table public.board_meta enable row level security;

-- kategoriyi gerekiyorsa yeniden hesaplar (ilk 10 tutulur; aynı anda tek hesaplama)
create or replace function public._zx_board_refresh(k text) returns timestamptz
language plpgsql security definer set search_path = public as $$
declare at0 timestamptz; t timestamptz := clock_timestamp();
begin
  select m.at into at0 from public.board_meta m where m.kind = k;
  if at0 is not null and at0 > now() - interval '15 minutes' then return at0; end if;
  if not pg_try_advisory_xact_lock(hashtext('zx_board:' || k)) then return coalesce(at0, now()); end if;
  insert into public.board_cache (kind, account_id, v, rk, at)
  select k, x.account_id, x.v, x.rk, t from (
    select s.account_id, s.v, rank() over (order by s.v desc, s.t) rk from public._zx_cat(k) s where s.v > 0) x
  where x.rk <= 10
  on conflict (kind, account_id) do update set v = excluded.v, rk = excluded.rk, at = excluded.at;
  insert into public.board_meta (kind, at) values (k, t) on conflict (kind) do update set at = excluded.at;
  return t;
end $$;
revoke all on function public._zx_board_refresh(text) from public, anon, authenticated;

create or replace function public._zx_board_kinds() returns text[]
language sql immutable as $$ select array['level', 'kills', 'pvp', 'titan', 'soldiers', 'bases', 'dog', 'crafted', 'time'] $$;

-- zx_board artık önbellekten (volatile: gerekiyorsa yeniler)
create or replace function public.zx_board(p_kind text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if not (p_kind = any(public._zx_board_kinds())) then return '[]'::jsonb; end if;
  perform public._zx_board_refresh(p_kind);
  return coalesce((select jsonb_agg(jsonb_build_object('rank', x.rk, 'id', a.id, 'name', a.name, 'avatar', a.avatar, 'frame', a.frame, 'photo', a.photo,
      'v', x.v, 'clan', (select name from public.clans where id = a.clan_id), 'title', public._zx_title(a),
      'banned', a.banned_until is not null and a.banned_until > now(), 'vip', a.vip_until is not null and a.vip_until > now()) order by x.rk, x.v desc)
    from public.board_cache x join public.board_meta m on m.kind = x.kind and m.at = x.at
    join public.accounts a on a.id = x.account_id where x.kind = p_kind), '[]'::jsonb);
end $$;

-- sıradaki yenileme zamanı (istemci geri sayım gösterir)
create or replace function public.zx_board_next(p_kind text) returns bigint
language sql stable security definer set search_path = public as $$
  select (extract(epoch from coalesce((select at from public.board_meta where kind = p_kind), now()) + interval '15 minutes') * 1000)::bigint
$$;

-- unvanlar: önbellekteki ilk 10 sıraları + süresi dolmamış etkinlik unvanları (tür "ev:<etkinlik>")
create or replace function public._zx_titles(p bigint) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce((select jsonb_agg(j) from (
    select jsonb_build_object('kind', b.kind, 'rank', b.rk, 'name', public._zx_cat_title(b.kind) || ' #' || b.rk) j
      from public.board_cache b join public.board_meta m on m.kind = b.kind and m.at = b.at where b.account_id = p
    union all
    select jsonb_build_object('kind', 'ev:' || e.kind, 'rank', e.rank, 'name', e.name,
                              'until', (extract(epoch from e.expires_at) * 1000)::bigint)
      from public.event_titles e where e.account_id = p and e.expires_at > now()) q), '[]'::jsonb)
$$;

create or replace function public._zx_title(a public.accounts) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare r bigint; e public.event_titles;
begin
  if a.title_kind = '' then return null; end if;
  if a.title_kind like 'ev:%' then
    select * into e from public.event_titles where account_id = a.id and kind = substr(a.title_kind, 4) and expires_at > now();
    if e.account_id is null then return null; end if;
    return jsonb_build_object('kind', a.title_kind, 'rank', e.rank, 'name', e.name);
  end if;
  select b.rk into r from public.board_cache b join public.board_meta m on m.kind = b.kind and m.at = b.at
   where b.kind = a.title_kind and b.account_id = a.id;
  if r is null then return null; end if;
  return jsonb_build_object('kind', a.title_kind, 'rank', r, 'name', public._zx_cat_title(a.title_kind) || ' #' || r);
end $$;

create or replace function public.zx_title_set(p_id bigint, p_key text, p_kind text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if coalesce(p_kind, '') <> '' and not exists (select 1 from jsonb_array_elements(public._zx_titles(a.id)) e where e ->> 'kind' = p_kind) then
    raise exception 'Bu unvan için o sıralamada ilk 10''da ya da etkinlikte ilk 3''te olmalısın';
  end if;
  update public.accounts set title_kind = coalesce(p_kind, '') where id = a.id returning * into a;
  return public._zx_profile(a);
end $$;

grant execute on function public.zx_board(text) to anon, authenticated;
grant execute on function public.zx_board_next(text) to anon, authenticated;

-- tüm sıralamaları gerekiyorsa yeniler (oyunda biri olduğu sürece en geç 15 dakikada bir; nabızdan çağrılır)
create or replace function public._zx_boards_refresh() returns void
language plpgsql security definer set search_path = public as $$
declare k text;
begin
  foreach k in array public._zx_board_kinds() loop perform public._zx_board_refresh(k); end loop;
end $$;
revoke all on function public._zx_boards_refresh() from public, anon, authenticated;

create or replace function public.zx_heartbeat(p_id bigint, p_key text, p_sid uuid, p_game_time double precision)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; wall double precision; dg double precision;
begin
  perform public._zx_maint_check(a);
  select * into s from public.sessions where account_id = a.id;
  if s.account_id is null or s.sid <> p_sid then
    raise exception 'OTURUM|Hesabına başka bir cihazdan girildi' using errcode = '28000';
  end if;
  wall := extract(epoch from now() - coalesce(s.speed_at, s.started_at));
  dg := p_game_time - s.game_time;
  if s.speed_at is not null and s.game_time > 0 and dg > wall * 1.25 + 8 and a.id <> 0 then
    perform public._zx_flag(a.id, 'speed', 'Oyun saati ' || round(dg::numeric, 1) || ' sn, gerçek ' || round(wall::numeric, 1) || ' sn');
  end if;
  update public.sessions set last_beat = now(), speed_at = now(), game_time = greatest(p_game_time, 0) where account_id = a.id;
  perform public._zx_deliver(a.id);
  perform public._zx_boards_refresh();
  return jsonb_build_object('ok', true, 'time', (extract(epoch from now()) * 1000)::bigint,
    'actions', (select count(*) from public.admin_actions where account_id = a.id and done_at is null),
    'unread', (select count(*) from public.mail m where m.account_id = a.id and not m.read and not m.deleted
               and (m.favorite or m.created_at > now() - interval '30 days')),
    'trade', (select t.id from public.trades t where ((t.a = a.id and not t.a_claimed) or (t.b = a.id and not t.b_claimed))
               and t.state in ('done', 'cancel') order by t.id desc limit 1));
end $$;
