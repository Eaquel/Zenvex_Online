-- Sezon Bileti: sezonlar, elmasla bilet kaydı, elmas ödüllerinin sunucu onayı.
-- Elmas istemci kaydında tutulur; kayıt doğrulayıcısı (zx_save3) elmas artışını grants defteriyle karşılaştırır.
-- Bilet alımı grants'a eksi elmas yazar (sonraki kayıt elmasın düştüğünü göstermek zorunda),
-- bilet ödülündeki elmas ise artı yazılır.

create table if not exists public.seasons (
  id int primary key,
  name text not null,
  starts_at timestamptz not null,
  ends_at timestamptz not null
);
alter table public.seasons enable row level security;

insert into public.seasons (id, name, starts_at, ends_at)
values (1, 'Sezon 1: Kan Ve Pas', '2026-10-20 00:00:00+03', '2027-01-04 00:00:00+03')
on conflict (id) do nothing;

create table if not exists public.pass_owned (
  account_id bigint not null,
  server text not null,
  season int not null,
  elite boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (account_id, server, season)
);
alter table public.pass_owned enable row level security;

create table if not exists public.pass_claims (
  account_id bigint not null,
  server text not null,
  season int not null,
  tier int not null,
  premium boolean not null,
  gems int not null,
  created_at timestamptz not null default now(),
  primary key (account_id, server, season, tier, premium)
);
alter table public.pass_claims enable row level security;

-- data.rs PASS_REWARDS ile aynı: hangi kademede kaç elmas
create or replace function public._zx_pass_gems(p_tier int, p_premium boolean) returns int
language sql immutable as $$
  select case when p_premium then
    case p_tier when 2 then 20 when 7 then 30 when 11 then 40 when 16 then 50 when 19 then 60
                when 22 then 70 when 26 then 100 when 29 then 150 else 0 end
  else
    case p_tier when 5 then 10 when 15 then 15 when 25 then 25 else 0 end
  end
$$;

create or replace function public._zx_season() returns public.seasons
language sql stable security definer set search_path = public as $$
  select * from public.seasons where starts_at <= now() order by starts_at desc limit 1
$$;

-- oturum başında: sezon, bitiş, sunucuda kayıtlı bilet
create or replace function public.zx_pass_state(p_id bigint, p_key text, p_sid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; se public.seasons := public._zx_season(); o public.pass_owned;
begin
  s := public._zx_session(a, p_sid);
  select * into o from public.pass_owned where account_id = a.id and server = s.server and season = se.id;
  return jsonb_build_object('season', coalesce(se.id, 0), 'name', se.name,
    'ends', (extract(epoch from se.ends_at) * 1000)::bigint, 'now', (extract(epoch from now()) * 1000)::bigint,
    'normal', o.account_id is not null, 'elite', coalesce(o.elite, false));
end $$;

-- bilet al: son geçerli kayıttaki elmas yetmeli; eksi elmas defteri sonraki kaydın elması düşürmesini şart koşar
create or replace function public.zx_pass_buy(p_id bigint, p_key text, p_sid uuid, p_elite boolean) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; se public.seasons := public._zx_season();
        o public.pass_owned; d jsonb; price int;
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  if se.id is null or se.ends_at < now() then raise exception 'Şu an açık bir sezon yok'; end if;
  select * into o from public.pass_owned where account_id = a.id and server = s.server and season = se.id;
  if o.account_id is not null and (o.elite or not p_elite) then raise exception 'Bu bilet zaten sende'; end if;
  price := case when p_elite and o.account_id is not null then 900 when p_elite then 1500 else 600 end;
  select data::jsonb into d from public.characters where account_id = a.id and server = s.server;
  if coalesce((d ->> 'gems')::int, 0) < price then raise exception 'Yetersiz elmas (% gerekli)', price; end if;
  insert into public.pass_owned (account_id, server, season, elite) values (a.id, s.server, se.id, p_elite)
    on conflict (account_id, server, season) do update set elite = public.pass_owned.elite or excluded.elite;
  insert into public.grants (account_id, gems, note) values (a.id, -price, 'sezon bileti ' || se.id || case when p_elite then ' elit' else '' end);
  perform public._zx_log(a.id, 'shop', 'Sezon Bileti aldı (' || price || ' elmas)');
  return jsonb_build_object('ok', true, 'price', price, 'season', se.id);
end $$;

-- elmaslı kademe ödülü: kademeye ulaşılmış (son kayıttaki bilet XP'si), premiumsa bilet kayıtlı, bir kez
create or replace function public.zx_pass_claim(p_id bigint, p_key text, p_sid uuid, p_tier int, p_premium boolean) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; se public.seasons := public._zx_season();
        d jsonb; g int := public._zx_pass_gems(p_tier, p_premium);
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  if se.id is null then raise exception 'Şu an açık bir sezon yok'; end if;
  if g <= 0 then raise exception 'Bu kademede elmas yok'; end if;
  select data::jsonb into d from public.characters where account_id = a.id and server = s.server;
  if coalesce((d #>> '{pass,xp}')::int, 0) < p_tier * 1000 then raise exception 'Bu kademeye henüz ulaşmadın'; end if;
  if p_premium and not exists (select 1 from public.pass_owned where account_id = a.id and server = s.server and season = se.id) then
    raise exception 'Bu ödül için Sezon Bileti gerekli';
  end if;
  begin
    insert into public.pass_claims (account_id, server, season, tier, premium, gems) values (a.id, s.server, se.id, p_tier, p_premium, g);
  exception when unique_violation then raise exception 'Zaten alındı';
  end;
  insert into public.grants (account_id, gems, note) values (a.id, g, 'sezon bileti kademe ' || p_tier);
  return jsonb_build_object('ok', true, 'gems', g);
end $$;

grant execute on function public.zx_pass_state(bigint, text, uuid) to anon, authenticated;
grant execute on function public.zx_pass_buy(bigint, text, uuid, boolean) to anon, authenticated;
grant execute on function public.zx_pass_claim(bigint, text, uuid, int, boolean) to anon, authenticated;
revoke all on function public._zx_season() from public, anon, authenticated;
