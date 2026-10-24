-- V15 (R45) yönetici paneli Ekonomi sekmesi: mağaza satışları, sunucunun verdiği altın/elmas, en zengin 20 karakter,
-- son satın almalar. Yalnız okuma.
create or replace function public.zx_admin_economy(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return jsonb_build_object(
    'sales_24h', (select jsonb_build_object('n', count(*), 'try', coalesce(sum(price_try), 0)) from public.purchases where not test and created_at > now() - interval '1 day'),
    'sales_7d', (select jsonb_build_object('n', count(*), 'try', coalesce(sum(price_try), 0)) from public.purchases where not test and created_at > now() - interval '7 days'),
    'sales_all', (select jsonb_build_object('n', count(*), 'try', coalesce(sum(price_try), 0)) from public.purchases where not test),
    'grants_24h', (select jsonb_build_object('gems', coalesce(sum(gems), 0), 'gold', coalesce(sum(gold), 0)) from public.grants where created_at > now() - interval '1 day'),
    'products', (select coalesce(jsonb_agg(x order by x.n desc), '[]') from
      (select product as id, count(*) as n, sum(price_try) as try from public.purchases where not test group by product) x),
    'recent', (select coalesce(jsonb_agg(jsonb_build_object('name', ac.name, 'id', ac.id, 'product', p.product, 'try', p.price_try, 'test', p.test,
        'at', (extract(epoch from p.created_at) * 1000)::bigint) order by p.created_at desc), '[]')
      from (select * from public.purchases order by created_at desc limit 20) p join public.accounts ac on ac.id = p.account_id),
    'richest', (select coalesce(jsonb_agg(jsonb_build_object('name', ac.name, 'id', ac.id, 'server', c.server, 'level', c.level,
        'gold', c.g, 'gems', c.e) order by c.g desc), '[]')
      from (select account_id, server, level, coalesce((data::jsonb ->> 'gold')::bigint, 0) g, coalesce((data::jsonb ->> 'gems')::bigint, 0) e
            from public.characters where data ~ '^\s*\{' order by 4 desc limit 20) c join public.accounts ac on ac.id = c.account_id)
  );
end $$;
grant execute on function public.zx_admin_economy(bigint, text) to anon, authenticated;
