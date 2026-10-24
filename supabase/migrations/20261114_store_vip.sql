-- V15 mağaza: gerçek parayla elmas, seviye paketleri, haftalık / aylık kart, sezon bileti; VIP puanı ve seviyeleri.
-- Ödeme Google Play ile yapılır; zx-billing edge function'ı satın almayı Google'dan doğrular ve zx_store_verified'ı
-- (yalnız servis anahtarı) çağırır. İçerik posta olarak gelir (posta alınınca grants kaydı düşer, kayıt doğrulayıcısı
-- elmas artışını kabul eder). Aynı sipariş iki kez işlenmez (order_id tekil).

alter table public.accounts add column if not exists vip_points integer not null default 0;

create table if not exists public.store_products (
  id text primary key,
  kind text not null check (kind in ('gems', 'pack', 'card', 'pass')),
  title text not null,
  price_try numeric(10, 2) not null,
  gems integer not null default 0,
  gold bigint not null default 0,
  items jsonb not null default '[]',
  vip_points integer not null default 0,
  sort integer not null default 0,
  active boolean not null default true,
  meta jsonb not null default '{}'
);
alter table public.store_products enable row level security;

create table if not exists public.purchases (
  id bigserial primary key,
  account_id bigint not null references public.accounts(id),
  server text,
  product text not null references public.store_products(id),
  order_id text not null unique,
  token text,
  price_try numeric(10, 2) not null default 0,
  test boolean not null default false,
  state text not null default 'granted',
  created_at timestamptz not null default now()
);
create index if not exists purchases_account on public.purchases (account_id, created_at desc);
alter table public.purchases enable row level security;

-- seviye paketleri: seviyeye ulaşınca bir kez, süreli teklif
create table if not exists public.store_offers (
  account_id bigint not null references public.accounts(id),
  product text not null references public.store_products(id),
  starts_at timestamptz not null default now(),
  ends_at timestamptz not null,
  bought boolean not null default false,
  primary key (account_id, product)
);
alter table public.store_offers enable row level security;

-- haftalık / aylık kart
create table if not exists public.store_cards (
  account_id bigint not null references public.accounts(id),
  product text not null references public.store_products(id),
  ends_at timestamptz not null,
  last_claim date,
  primary key (account_id, product)
);
alter table public.store_cards enable row level security;

-- VIP günlük ödülü
create table if not exists public.vip_daily (
  account_id bigint primary key references public.accounts(id),
  last_claim date not null
);
alter table public.vip_daily enable row level security;

insert into public.store_products (id, kind, title, price_try, gems, gold, items, vip_points, sort, meta) values
  ('gems_60',   'gems', 'Avuç Dolusu Elmas', 29.99,   60, 0, '[]', 30, 10, '{}'),
  ('gems_220',  'gems', 'Elmas Kesesi',      99.99,  220, 0, '[]', 100, 11, '{"bonus": 10}'),
  ('gems_600',  'gems', 'Elmas Sandığı',    249.99,  600, 0, '[]', 250, 12, '{"bonus": 20}'),
  ('gems_1300', 'gems', 'Elmas Kasası',     499.99, 1300, 0, '[]', 500, 13, '{"bonus": 30}'),
  ('gems_2800', 'gems', 'Elmas Hazinesi',   999.99, 2800, 0, '[]', 1000, 14, '{"bonus": 40}'),
  ('gems_6000', 'gems', 'Ejder Hazinesi',  1999.99, 6000, 0, '[]', 2000, 15, '{"bonus": 50}'),
  ('pack_start',  'pack', 'Başlangıç Paketi', 39.99, 300, 20000,
    '[{"id": "potion_heal", "n": 3}, {"id": "potion_speed", "n": 3}, {"id": "medkit", "n": 5}, {"id": "char_card", "n": 3}]', 40, 20, '{"level": 5, "max_level": 10, "hours": 24}'),
  ('pack_growth', 'pack', 'Gelişim Paketi',   99.99, 800, 60000,
    '[{"id": "char_card", "n": 10}, {"id": "v_wheel", "n": 2}, {"id": "v_engine", "n": 1}, {"id": "v_frame_moto", "n": 1}, {"id": "potion_power", "n": 3}]', 100, 21, '{"level": 10, "max_level": 25, "hours": 72}'),
  ('pack_war',    'pack', 'Savaş Paketi',    199.99, 1800, 150000,
    '[{"id": "char_card", "n": 25}, {"id": "v_wheel", "n": 4}, {"id": "v_engine", "n": 1}, {"id": "v_frame_car", "n": 1}, {"id": "potion_power", "n": 5}]', 200, 22, '{"level": 25, "max_level": 51, "hours": 168}'),
  ('card_week',  'card', 'Haftalık Kart',  49.99, 150, 0, '[]', 50, 30, '{"days": 7, "daily_gems": 60, "daily_gold": 2000}'),
  ('card_month', 'card', 'Aylık Kart',    149.99, 450, 0, '[]', 150, 31, '{"days": 30, "daily_gems": 90, "daily_gold": 3000, "daily_items": [{"id": "char_card", "n": 1}]}'),
  ('pass_vip',     'pass', 'VIP Bilet',              99.99, 0, 0, '[]', 100, 40, '{"pass": "normal"}'),
  ('pass_premium', 'pass', 'Premium Bilet',         199.99, 0, 0, '[{"id": "pass_xp", "n": 10000}, {"id": "cos_frame_s1", "n": 1}]', 200, 41, '{"pass": "elite"}'),
  ('pass_upgrade', 'pass', 'Premium Bilete Yükselt', 99.99, 0, 0, '[{"id": "pass_xp", "n": 10000}, {"id": "cos_frame_s1", "n": 1}]', 100, 42, '{"pass": "elite", "needs": "normal"}')
on conflict (id) do update set kind = excluded.kind, title = excluded.title, price_try = excluded.price_try, gems = excluded.gems,
  gold = excluded.gold, items = excluded.items, vip_points = excluded.vip_points, sort = excluded.sort, meta = excluded.meta;

-- VIP seviyesi (0-11; 11 = V10+), eşikler TL ≈ puan
create or replace function public._zx_vip_level(p integer) returns integer language sql immutable as $$
  select (count(*) - 1)::int from unnest(array[0, 50, 150, 350, 700, 1250, 2250, 3750, 6000, 10000, 20000, 50000]) v where p >= v
$$;

create or replace function public._zx_vip_next(p integer) returns integer language sql immutable as $$
  select coalesce(min(v), 0) from unnest(array[50, 150, 350, 700, 1250, 2250, 3750, 6000, 10000, 20000, 50000]) v where v > p
$$;

-- satın alma içeriğini verir (doğrulanmış ya da yönetici testi); aynı sipariş ikinci kez işlenmez
create or replace function public._zx_store_grant(p_account bigint, p_server text, p_product text, p_order text, p_token text, p_test boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare pr public.store_products; first boolean; g int; se public.seasons := public._zx_season(); days int; body text;
begin
  select * into pr from public.store_products where id = p_product;
  if pr.id is null then raise exception 'Ürün bulunamadı'; end if;
  if exists (select 1 from public.purchases where order_id = p_order) then
    return jsonb_build_object('ok', true, 'dup', true);
  end if;
  first := not exists (select 1 from public.purchases where account_id = p_account and product = p_product and not test);
  insert into public.purchases (account_id, server, product, order_id, token, price_try, test)
  values (p_account, p_server, p_product, p_order, p_token, case when p_test then 0 else pr.price_try end, p_test);
  if not p_test then
    update public.accounts set vip_points = vip_points + pr.vip_points where id = p_account;
  end if;
  g := pr.gems;
  if pr.kind = 'gems' and first then g := g * 2; end if;
  body := 'Satın aldığın için teşekkürler! ' || case when pr.kind = 'gems' and first then 'İlk alım: elmaslar 2 katı. ' else '' end;
  if pr.kind = 'pack' then
    update public.store_offers set bought = true where account_id = p_account and product = p_product;
  elsif pr.kind = 'card' then
    days := coalesce((pr.meta ->> 'days')::int, 7);
    insert into public.store_cards (account_id, product, ends_at, last_claim) values (p_account, p_product, now() + make_interval(days => days), null)
    on conflict (account_id, product) do update set ends_at = greatest(public.store_cards.ends_at, now()) + make_interval(days => days);
    body := body || 'Günlük ödülün her gün postana gelir (Mağaza → Kartlar → Al).';
  elsif pr.kind = 'pass' then
    if se.id is null then raise exception 'Şu an açık bir sezon yok'; end if;
    insert into public.pass_owned (account_id, server, season, elite) values (p_account, coalesce(p_server, 'n1'), se.id, (pr.meta ->> 'pass') = 'elite')
      on conflict (account_id, server, season) do update set elite = public.pass_owned.elite or excluded.elite;
    body := body || 'Bilet ' || coalesce(p_server, 'n1') || ' sunucusundaki karakterinde açıldı.';
  end if;
  if g > 0 or pr.gold > 0 or jsonb_array_length(pr.items) > 0 then
    insert into public.mail (account_id, sender, title, body, gold, gems, items, kind)
    values (p_account, 'Mağaza', pr.title, body, pr.gold, g, pr.items, 'shop');
  end if;
  perform public._zx_log(p_account, 'shop', 'Satın aldı: ' || pr.title || case when p_test then ' (test)' else ' (' || pr.price_try || ' TL)' end);
  return jsonb_build_object('ok', true, 'product', p_product, 'gems', g, 'first', first);
end $$;
revoke all on function public._zx_store_grant(bigint, text, text, text, text, boolean) from public, anon, authenticated;

-- zx-billing edge function'ı (servis anahtarı) Google doğrulamasından sonra çağırır
create or replace function public.zx_store_verified(p_id bigint, p_key text, p_server text, p_product text, p_order text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return public._zx_store_grant(a.id, p_server, p_product, p_order, p_token, false);
end $$;
revoke all on function public.zx_store_verified(bigint, text, text, text, text, text) from public, anon, authenticated;
grant execute on function public.zx_store_verified(bigint, text, text, text, text, text) to service_role;

-- yönetici: ödeme olmadan test satın alması (VIP puanı vermez)
create or replace function public.zx_admin_test_purchase(p_id bigint, p_key text, p_target bigint, p_product text, p_server text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return public._zx_store_grant(p_target, p_server, p_product, 'test-' || gen_random_uuid(), null, true);
end $$;
grant execute on function public.zx_admin_test_purchase(bigint, text, bigint, text, text) to anon, authenticated;

-- mağaza durumu: ürünler, VIP, seviye paketi teklifi (seviyeye ulaşınca açılır), kartlar, ilk alımlar
create or replace function public.zx_store_state(p_id bigint, p_key text, p_server text, p_level integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); pr record; ch public.characters; lv int;
begin
  select * into ch from public.characters where account_id = a.id and server = p_server;
  -- seviye sunucudaki kayıttan (istemcinin söylediğinden büyük olamaz)
  lv := least(greatest(coalesce(ch.level, 1), 1), greatest(p_level, 1));
  for pr in select * from public.store_products where kind = 'pack' and active loop
    if lv >= (pr.meta ->> 'level')::int and lv < (pr.meta ->> 'max_level')::int then
      insert into public.store_offers (account_id, product, ends_at)
      values (a.id, pr.id, now() + make_interval(hours => (pr.meta ->> 'hours')::int)) on conflict do nothing;
    end if;
  end loop;
  return jsonb_build_object(
    'now', (extract(epoch from now()) * 1000)::bigint,
    'vip', jsonb_build_object('points', a.vip_points, 'level', public._zx_vip_level(a.vip_points), 'next', public._zx_vip_next(a.vip_points),
      'daily', not exists (select 1 from public.vip_daily v where v.account_id = a.id and v.last_claim >= (now() at time zone 'Europe/Istanbul')::date)),
    'products', (select coalesce(jsonb_agg(jsonb_build_object('id', p.id, 'kind', p.kind, 'title', p.title, 'price', p.price_try, 'gems', p.gems,
        'gold', p.gold, 'items', p.items, 'vip', p.vip_points, 'meta', p.meta,
        'first', not exists (select 1 from public.purchases u where u.account_id = a.id and u.product = p.id and not u.test)) order by p.sort), '[]')
      from public.store_products p where p.active),
    'offers', (select coalesce(jsonb_agg(jsonb_build_object('id', o.product, 'ends', (extract(epoch from o.ends_at) * 1000)::bigint)), '[]')
      from public.store_offers o where o.account_id = a.id and not o.bought and o.ends_at > now()),
    'cards', (select coalesce(jsonb_agg(jsonb_build_object('id', c.product, 'ends', (extract(epoch from c.ends_at) * 1000)::bigint,
        'claimable', c.ends_at > now() and (c.last_claim is null or c.last_claim < (now() at time zone 'Europe/Istanbul')::date))), '[]')
      from public.store_cards c where c.account_id = a.id and c.ends_at > now() - interval '1 day'),
    'pass', (select jsonb_build_object('normal', o.account_id is not null, 'elite', coalesce(o.elite, false))
      from (select 1) x left join public.pass_owned o on o.account_id = a.id and o.server = p_server and o.season = (public._zx_season()).id));
end $$;
grant execute on function public.zx_store_state(bigint, text, text, integer) to anon, authenticated;

-- kart günlük ödülü: alınmayan günler en çok 3 gün birikir; posta olarak gelir
create or replace function public.zx_card_claim(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); c record; today date := (now() at time zone 'Europe/Istanbul')::date; n int; total int := 0;
        items jsonb;
begin
  for c in select s.*, p.meta, p.title from public.store_cards s join public.store_products p on p.id = s.product
           where s.account_id = a.id and s.ends_at > now() and (s.last_claim is null or s.last_claim < today) loop
    n := least(3, case when c.last_claim is null then 1 else today - c.last_claim end);
    select coalesce(jsonb_agg(jsonb_build_object('id', e ->> 'id', 'n', (e ->> 'n')::int * n)), '[]') into items
      from jsonb_array_elements(coalesce(c.meta -> 'daily_items', '[]')) e;
    insert into public.mail (account_id, sender, title, body, gold, gems, items, kind)
    values (a.id, 'Mağaza', c.title || ' — Günlük Ödül', n || ' günlük ödül', (c.meta ->> 'daily_gold')::int * n,
      (c.meta ->> 'daily_gems')::int * n, items, 'shop');
    update public.store_cards set last_claim = today where account_id = a.id and product = c.product;
    total := total + n;
  end loop;
  if total = 0 then raise exception 'Bugünkü kart ödülün alındı'; end if;
  return jsonb_build_object('ok', true, 'days', total);
end $$;
grant execute on function public.zx_card_claim(bigint, text) to anon, authenticated;

-- VIP günlük elması: V4 20, V8 40, V10 80, V10+ 150
create or replace function public.zx_vip_daily(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); lv int := public._zx_vip_level(a.vip_points); g int;
        today date := (now() at time zone 'Europe/Istanbul')::date;
begin
  g := case when lv >= 11 then 150 when lv >= 10 then 80 when lv >= 8 then 40 when lv >= 4 then 20 else 0 end;
  if g = 0 then raise exception 'VIP günlük ödülü V4''te açılır'; end if;
  if exists (select 1 from public.vip_daily where account_id = a.id and last_claim >= today) then raise exception 'Bugünkü VIP ödülün alındı'; end if;
  insert into public.vip_daily (account_id, last_claim) values (a.id, today) on conflict (account_id) do update set last_claim = today;
  insert into public.mail (account_id, sender, title, body, gems, kind) values (a.id, 'VIP', 'VIP Günlük Ödül', 'V' || least(lv, 10) || case when lv >= 11 then '+' else '' end || ' günlük elmasın', g, 'shop');
  return jsonb_build_object('ok', true, 'gems', g);
end $$;
grant execute on function public.zx_vip_daily(bigint, text) to anon, authenticated;
