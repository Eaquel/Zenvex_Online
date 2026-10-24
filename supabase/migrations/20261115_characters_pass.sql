-- V15 karakter kartları ve yeni sezon bileti.
--  - zx_char_pull: kart açma zarını sunucu atar (hile yapılamaz). Altın son kayıttan kontrol edilir, düşüşü ve en üst
--    seviyedeki karakterin kartlarının elmas karşılığı grants'a yazılır (kayıt doğrulayıcısı bunları bilir).
--  - Sezon bileti kademeleri zorlaşır: 1-10 → 1.000, 11-20 → 2.000, 21-30 → 5.000 XP (Rust data::pass_xp_for ile aynı).
--  - Bilet içerikleri bilet açılınca oyunda verilir (Premium: +10.000 kademe XP'si, S1 çerçevesi); postayla değil.

create table if not exists public.char_pulls (
  id bigserial primary key,
  account_id bigint not null references public.accounts(id),
  server text not null,
  count integer not null,
  cost integer not null,
  results jsonb not null,
  gems integer not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists char_pulls_account on public.char_pulls (account_id, created_at desc);
alter table public.char_pulls enable row level security;

create or replace function public.zx_char_pull(p_id bigint, p_key text, p_sid uuid, p_count integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; d jsonb; cost int; cards int[]; r int; i int; k int;
        res int[] := '{}'; gems int := 0; rare boolean := false;
        max_cards constant int := 226; dupe constant int[] := array[1, 3, 8, 20];
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  if p_count not in (1, 10) then raise exception 'Geçersiz adet'; end if;
  cost := case when p_count = 10 then 20000 else 2500 end;
  select data::jsonb into d from public.characters where account_id = a.id and server = s.server;
  if coalesce((d ->> 'gold')::bigint, 0) < cost then raise exception 'Yetersiz altın (% gerekli)', cost; end if;
  cards := array[coalesce((d #>> '{char_cards,0}')::int, 1), coalesce((d #>> '{char_cards,1}')::int, 0),
                 coalesce((d #>> '{char_cards,2}')::int, 0), coalesce((d #>> '{char_cards,3}')::int, 0)];
  for k in 1..p_count loop
    r := floor(random() * 100)::int;
    i := case when r < 55 then 0 when r < 83 then 1 when r < 96 then 2 else 3 end;
    -- 10'luda en az bir Nadir ya da üstü
    if p_count = 10 and k = 10 and not rare and i = 0 then i := 1; end if;
    if i > 0 then rare := true; end if;
    if cards[i + 1] >= max_cards then gems := gems + dupe[i + 1]; else cards[i + 1] := cards[i + 1] + 1; end if;
    res := res || i;
  end loop;
  insert into public.char_pulls (account_id, server, count, cost, results, gems) values (a.id, s.server, p_count, cost, to_jsonb(res), gems);
  insert into public.grants (account_id, gems, gold, note) values (a.id, gems, -cost, 'karakter kartı ×' || p_count);
  return jsonb_build_object('results', to_jsonb(res), 'gems', gems, 'cost', cost);
end $$;
grant execute on function public.zx_char_pull(bigint, text, uuid, integer) to anon, authenticated;

-- yeni bilet ödülleri (Rust data::PASS_REWARDS ile aynı): ücretsiz 150, premium 900 elmas
create or replace function public._zx_pass_gems(p_tier integer, p_premium boolean)
returns integer language sql immutable set search_path = public as $$
  select case when p_premium then
    case p_tier when 3 then 40 when 7 then 60 when 11 then 80 when 14 then 90 when 17 then 100
                when 21 then 120 when 24 then 130 when 27 then 140 when 29 then 140 else 0 end
  else
    case p_tier when 5 then 15 when 10 then 25 when 15 then 30 when 20 then 35 when 25 then 45 else 0 end
  end
$$;

create or replace function public._zx_pass_xp_for(p_tier integer)
returns integer language sql immutable as $$
  select least(p_tier, 10) * 1000 + greatest(least(p_tier, 20) - 10, 0) * 2000 + greatest(least(p_tier, 30) - 20, 0) * 5000
$$;

create or replace function public.zx_pass_claim(p_id bigint, p_key text, p_sid uuid, p_tier integer, p_premium boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; se public.seasons := public._zx_season();
        d jsonb; g int := public._zx_pass_gems(p_tier, p_premium);
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  if se.id is null then raise exception 'Şu an açık bir sezon yok'; end if;
  if g <= 0 then raise exception 'Bu kademede elmas yok'; end if;
  select data::jsonb into d from public.characters where account_id = a.id and server = s.server;
  if coalesce((d #>> '{pass,xp}')::int, 0) < public._zx_pass_xp_for(p_tier) then raise exception 'Bu kademeye henüz ulaşmadın'; end if;
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

-- elmasla bilet artık yok (Google Play ile alınır)
create or replace function public.zx_pass_buy(p_id bigint, p_key text, p_sid uuid, p_elite boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  raise exception 'Sezon Bileti artık Mağaza''dan alınır';
end $$;

update public.store_products set items = '[]' where id in ('pass_premium', 'pass_upgrade');
