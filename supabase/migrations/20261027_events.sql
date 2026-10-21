-- V9 etkinlikleri: yönetici başlatır (2 hafta), oyuncular oturumda puan yollar (dakika başı tavanlı),
-- bitince ilk 100 / ilk 10 / 1-2-3'e posta ile ödül; 1-3 ödülleri 30 gün sonra kaybolur (süreli eşya).

create table if not exists public.events (
  id bigint generated always as identity primary key,
  kind text not null,
  title text not null,
  starts_at timestamptz not null default now(),
  ends_at timestamptz not null,
  active boolean not null default true,
  finalized boolean not null default false
);
alter table public.events enable row level security;

create table if not exists public.event_scores (
  event_id bigint not null references public.events(id) on delete cascade,
  account_id bigint not null references public.accounts(id) on delete cascade,
  score bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (event_id, account_id)
);
create index if not exists event_scores_rank on public.event_scores (event_id, score desc);
alter table public.event_scores enable row level security;

-- süreli eşyalar (etkinlik ödülleri): süre dolunca oturum açılışında istemci çantadan siler
create table if not exists public.timed_items (
  account_id bigint not null references public.accounts(id) on delete cascade,
  item text not null,
  expires_at timestamptz not null,
  removed boolean not null default false,
  primary key (account_id, item)
);
alter table public.timed_items enable row level security;

create or replace function public._zx_event_title(k text) returns text
language sql immutable as $$
  select case k when 'blood_moon' then 'Kan Ayı' when 'titan_hunt' then 'Titan Avı' when 'base_storm' then 'Üs Fırtınası'
    when 'scavengers' then 'Hurdacılar' when 'pack_leader' then 'Sürü Lideri' when 'last_breath' then 'Son Nefes' else k end
$$;

-- etkinlik eşya ön eki (ev_<x>_ring / _neck / _helm)
create or replace function public._zx_event_item(k text) returns text
language sql immutable as $$
  select case k when 'blood_moon' then 'blood' when 'titan_hunt' then 'titan' when 'base_storm' then 'base'
    when 'scavengers' then 'scrap' when 'pack_leader' then 'pack' when 'last_breath' then 'breath' else 'blood' end
$$;

-- dakika başı puan tavanı (hileye karşı)
create or replace function public._zx_event_cap(k text) returns int
language sql immutable as $$
  select case k when 'blood_moon' then 800 when 'titan_hunt' then 4000 when 'base_storm' then 2500
    when 'scavengers' then 400 when 'pack_leader' then 400 when 'last_breath' then 60 else 300 end
$$;

-- süresi dolan / bitirilen etkinliği kapatır ve ödülleri yollar
create or replace function public._zx_event_finalize(force boolean default false) returns void
language plpgsql security definer set search_path = public as $$
declare e public.events; r record; t text; pre text;
begin
  for e in select * from public.events where active and not finalized and (force or ends_at <= now()) for update loop
    t := e.title; pre := public._zx_event_item(e.kind);
    for r in select s.account_id, s.score, row_number() over (order by s.score desc, s.updated_at) as rk
             from public.event_scores s where s.event_id = e.id and s.score > 0 order by s.score desc limit 100 loop
      if r.rk = 1 then
        insert into public.mail (account_id, sender, title, body, gems, items, kind)
        values (r.account_id, 'Zenvex', t || ' Şampiyonu!', t || ' etkinliğini 1. sırada bitirdin (' || r.score || ' puan). Ödülün: ' || t ||
          ' Kralı Yüzüğü (30 gün) ve 1000 elmas. Tebrikler!', 1000, jsonb_build_array(jsonb_build_object('id', 'ev_' || pre || '_ring', 'n', 1)), 'event');
        insert into public.timed_items (account_id, item, expires_at) values (r.account_id, 'ev_' || pre || '_ring', now() + interval '30 days')
        on conflict (account_id, item) do update set expires_at = excluded.expires_at, removed = false;
      elsif r.rk = 2 then
        insert into public.mail (account_id, sender, title, body, gems, items, kind)
        values (r.account_id, 'Zenvex', t || ': 2. Sıra', t || ' etkinliğini 2. sırada bitirdin (' || r.score || ' puan). Ödülün: ' || t ||
          ' Şampiyonu Kolyesi (30 gün) ve 700 elmas.', 700, jsonb_build_array(jsonb_build_object('id', 'ev_' || pre || '_neck', 'n', 1)), 'event');
        insert into public.timed_items (account_id, item, expires_at) values (r.account_id, 'ev_' || pre || '_neck', now() + interval '30 days')
        on conflict (account_id, item) do update set expires_at = excluded.expires_at, removed = false;
      elsif r.rk = 3 then
        insert into public.mail (account_id, sender, title, body, gems, items, kind)
        values (r.account_id, 'Zenvex', t || ': 3. Sıra', t || ' etkinliğini 3. sırada bitirdin (' || r.score || ' puan). Ödülün: ' || t ||
          ' Muhafız Kaskı (30 gün) ve 500 elmas.', 500, jsonb_build_array(jsonb_build_object('id', 'ev_' || pre || '_helm', 'n', 1)), 'event');
        insert into public.timed_items (account_id, item, expires_at) values (r.account_id, 'ev_' || pre || '_helm', now() + interval '30 days')
        on conflict (account_id, item) do update set expires_at = excluded.expires_at, removed = false;
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

-- şu anki etkinlik (herkes): tür, başlık, bitiş, katılımcı, ilk 10
create or replace function public.zx_event_current() returns jsonb
language plpgsql security definer set search_path = public as $$
declare e public.events;
begin
  perform public._zx_event_finalize(false);
  select * into e from public.events where active order by id desc limit 1;
  if e.id is null then return jsonb_build_object('active', false); end if;
  return jsonb_build_object('active', true, 'id', e.id, 'kind', e.kind, 'title', e.title,
    'starts', (extract(epoch from e.starts_at) * 1000)::bigint, 'ends', (extract(epoch from e.ends_at) * 1000)::bigint,
    'players', (select count(*) from public.event_scores where event_id = e.id and score > 0),
    'top', coalesce((select jsonb_agg(jsonb_build_object('name', a.name, 'id', a.id, 'score', s.score) order by s.score desc)
      from (select * from public.event_scores where event_id = e.id and score > 0 order by score desc limit 10) s
      join public.accounts a on a.id = s.account_id), '[]'::jsonb));
end $$;

-- ilk 100 + benim sıram
create or replace function public.zx_event_board(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); e public.events; mine bigint; rk bigint;
begin
  perform public._zx_event_finalize(false);
  select * into e from public.events where active order by id desc limit 1;
  if e.id is null then return jsonb_build_object('active', false); end if;
  select score into mine from public.event_scores where event_id = e.id and account_id = a.id;
  select count(*) + 1 into rk from public.event_scores where event_id = e.id and score > coalesce(mine, 0);
  return jsonb_build_object('active', true, 'kind', e.kind, 'title', e.title, 'ends', (extract(epoch from e.ends_at) * 1000)::bigint,
    'my_score', coalesce(mine, 0), 'my_rank', case when coalesce(mine, 0) > 0 then rk else null end,
    'top', coalesce((select jsonb_agg(jsonb_build_object('name', ac.name, 'id', ac.id, 'score', s.score) order by s.score desc)
      from (select * from public.event_scores where event_id = e.id and score > 0 order by score desc limit 100) s
      join public.accounts ac on ac.id = s.account_id), '[]'::jsonb));
end $$;

-- puan yolla (oturumda): dakika başı tavanlı
create or replace function public.zx_event_score(p_id bigint, p_key text, p_sid uuid, p_points int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; e public.events; last timestamptz; allow int; got int;
begin
  s := public._zx_session(a, p_sid);
  select * into e from public.events where active and ends_at > now() order by id desc limit 1;
  if e.id is null or p_points <= 0 then return jsonb_build_object('ok', false); end if;
  select updated_at into last from public.event_scores where event_id = e.id and account_id = a.id;
  allow := (public._zx_event_cap(e.kind) * least(greatest(extract(epoch from now() - coalesce(last, now() - interval '1 minute')) / 60.0, 0.1), 10))::int;
  got := least(p_points, allow);
  if p_points > allow * 2 and a.id <> 0 then
    perform public._zx_flag(a.id, 'event', e.kind || ': ' || p_points || ' puan istendi, sınır ' || allow);
  end if;
  insert into public.event_scores (event_id, account_id, score, updated_at) values (e.id, a.id, got, now())
  on conflict (event_id, account_id) do update set score = public.event_scores.score + excluded.score, updated_at = now();
  return jsonb_build_object('ok', true, 'added', got);
end $$;

create or replace function public.zx_admin_event_start(p_id bigint, p_key text, p_kind text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t text := public._zx_event_title(p_kind);
begin
  if p_kind not in ('blood_moon', 'titan_hunt', 'base_storm', 'scavengers', 'pack_leader', 'last_breath') then raise exception 'Geçersiz etkinlik'; end if;
  perform public._zx_event_finalize(false);
  if exists (select 1 from public.events where active) then raise exception 'Zaten süren bir etkinlik var'; end if;
  insert into public.events (kind, title, ends_at) values (p_kind, t, now() + interval '14 days');
  -- herkese duyuru postası (cihaz bildirimi de gider)
  insert into public.mail (account_id, sender, title, body, kind)
  select id, 'Zenvex', 'Yeni Etkinlik: ' || t, t || ' etkinliği başladı ve 14 gün sürecek! Sıralamaya gir: ilk 3 sıra efsanevi ödüller, ilk 10 ve ilk 100 elmas kazanır.', 'event'
  from public.accounts where deleted_at is null;
  return t || ' başladı (14 gün)';
end $$;

create or replace function public.zx_admin_event_stop(p_id bigint, p_key text) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  if not exists (select 1 from public.events where active) then raise exception 'Süren etkinlik yok'; end if;
  perform public._zx_event_finalize(true);
  return 'Etkinlik bitti, ödüller postayla gönderildi';
end $$;

-- süresi dolan eşyalar (oturum açılışında istemci siler)
create or replace function public.zx_expired_items(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); r jsonb;
begin
  select coalesce(jsonb_agg(item), '[]'::jsonb) into r from public.timed_items where account_id = a.id and not removed and expires_at <= now();
  update public.timed_items set removed = true where account_id = a.id and not removed and expires_at <= now();
  return r;
end $$;

grant execute on function public.zx_event_current() to anon, authenticated;
grant execute on function public.zx_event_board(bigint, text) to anon, authenticated;
grant execute on function public.zx_event_score(bigint, text, uuid, int) to anon, authenticated;
grant execute on function public.zx_admin_event_start(bigint, text, text) to anon, authenticated;
grant execute on function public.zx_admin_event_stop(bigint, text) to anon, authenticated;
grant execute on function public.zx_expired_items(bigint, text) to anon, authenticated;
