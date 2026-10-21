-- V8 devlet üsleri: sunucu başına 50 üs. Sahiplik, ödül ve süreler burada; zaman okuma anında işlenir (cron yok).
-- Kurallar: devlet üssü hiçbir şey vermez. Klan üssü dakikada 60 klan tecrübesi verir; ödül süresi dolduğunda üs hâlâ
-- klandaysa eşya klan deposuna düşer ve yeni ödül/süre başlar; sahiplik başka klan alana kadar sürer.

create table if not exists public.state_bases (
  server text not null,
  id int not null check (id between 0 and 49),
  owner_clan bigint references public.clans(id) on delete set null,
  captured_at timestamptz,
  captured_by bigint references public.accounts(id) on delete set null,
  reward text not null,
  reward_n int not null default 1,
  reward_until timestamptz not null,
  xp_paid_until timestamptz,
  primary key (server, id)
);
alter table public.state_bases enable row level security;

create table if not exists public.base_captures (
  id bigint generated always as identity primary key,
  server text not null,
  base int not null,
  account_id bigint not null references public.accounts(id) on delete cascade,
  clan_id bigint,
  created_at timestamptz not null default now()
);
create index if not exists base_captures_acc on public.base_captures (account_id, created_at desc);
alter table public.base_captures enable row level security;

-- ödül havuzu (tekrar = ağırlık): eşya, adet, süre (dk)
create or replace function public._zx_base_reward(out item text, out n int, out mins int)
language sql volatile set search_path = public as $$
  select r.item, r.n, r.mins from (values
    ('titan_shard', 1, 20),
    ('potion_heal', 2, 10), ('potion_heal', 2, 10),
    ('potion_speed', 2, 10), ('potion_power', 2, 10),
    ('medkit', 3, 15), ('medkit', 3, 15),
    ('dark_crystal', 1, 25),
    ('electronics', 5, 12),
    ('battery', 4, 12)
  ) as r(item, n, mins) order by random() limit 1
$$;

create or replace function public._zx_item_tr(i text) returns text
language sql immutable as $$
  select case i when 'titan_shard' then 'Titan Parçası' when 'potion_heal' then 'Can İksiri' when 'potion_speed' then 'Hız İksiri'
    when 'potion_power' then 'Güç İksiri' when 'medkit' then 'İlk Yardım Kiti' when 'dark_crystal' then 'Kara Kristal'
    when 'electronics' then 'Elektronik Parça' when 'battery' then 'Pil' else i end
$$;

-- klan tecrübesi ekle (haftalık sıralamaya ele geçiren üyenin adına) + seviye atlama ödülü
create or replace function public._zx_clan_add_xp(p_clan bigint, p_acc bigint, p_xp bigint) returns void
language plpgsql security definer set search_path = public as $$
declare c public.clans; lvl int;
begin
  if p_xp <= 0 then return; end if;
  if p_acc is not null and exists (select 1 from public.accounts where id = p_acc) then
    insert into public.clan_xp (clan_id, account_id, week, xp, kills) values (p_clan, p_acc, public._zx_week(), p_xp, 0)
    on conflict (clan_id, account_id, week) do update set xp = public.clan_xp.xp + excluded.xp;
  end if;
  update public.clans set xp_total = xp_total + p_xp where id = p_clan returning * into c;
  if c.id is null then return; end if;
  lvl := c.level;
  while lvl < 9 and c.xp_total >= public._zx_clan_need(lvl + 1) loop lvl := lvl + 1; end loop;
  if lvl > c.level then
    update public.clans set level = lvl where id = c.id;
    insert into public.mail (account_id, sender, title, body, gems, kind)
    select id, c.name, 'Klan Seviye ' || lvl || ': ' || public._zx_clan_title(lvl),
      'Klanın ' || lvl || '. seviyeye ulaştı! Ödülün hesabına bağlıdır, takas edilemez. Üye sınırı: ' || public._zx_clan_cap(lvl) || '.',
      25 * lvl, 'clan'
    from public.accounts where clan_id = c.id;
    insert into public.clan_log (clan_id, text) values (c.id, 'Klan ' || lvl || '. seviyeye ulaştı (' || public._zx_clan_title(lvl) || ')');
  end if;
end $$;

-- geçen zamanı işle: üsleri oluştur, klan tecrübesini öde, süresi dolan ödülleri depoya koy ve yenisini başlat
create or replace function public._zx_bases_tick(p_server text) returns void
language plpgsql security definer set search_path = public as $$
declare b public.state_bases; r record; mins bigint; k int; paid timestamptz;
begin
  if p_server is null or p_server not in ('n1', 'n2', 'n3', 'hc') then raise exception 'Geçersiz sunucu'; end if;
  insert into public.state_bases (server, id, reward, reward_n, reward_until)
  select p_server, g, x.item, x.n, now() + make_interval(mins => x.mins + (g % 7))
  from generate_series(0, 49) g, lateral public._zx_base_reward() x
  on conflict do nothing;
  for b in select * from public.state_bases where server = p_server order by id for update loop
    -- klan tecrübesi: dakikada 60
    if b.owner_clan is not null then
      paid := coalesce(b.xp_paid_until, b.captured_at, now());
      mins := floor(extract(epoch from (now() - paid)) / 60);
      if mins > 0 then
        perform public._zx_clan_add_xp(b.owner_clan, b.captured_by, least(mins, 10080) * 60);
        b.xp_paid_until := paid + make_interval(mins => mins::int);
      end if;
    end if;
    -- ödüller: süre dolduğunda sahibi olan klan alır (devlet üssü vermez)
    k := 0;
    while b.reward_until <= now() and k < 30 loop
      if b.owner_clan is not null and b.captured_at <= b.reward_until then
        insert into public.clan_depot (clan_id, item, n) values (b.owner_clan, b.reward, b.reward_n)
        on conflict (clan_id, item) do update set n = public.clan_depot.n + excluded.n;
        insert into public.clan_log (clan_id, text)
        values (b.owner_clan, 'Devlet Üssü ' || (b.id + 1) || ': ' || b.reward_n || '× ' || public._zx_item_tr(b.reward) || ' klan deposuna geldi');
      end if;
      select * into r from public._zx_base_reward();
      b.reward := r.item; b.reward_n := r.n;
      b.reward_until := b.reward_until + make_interval(mins => r.mins);
      k := k + 1;
    end loop;
    if b.reward_until <= now() then
      -- çok uzun süre kimse bakmadıysa yakala (birikmiş ödül verilmez)
      select * into r from public._zx_base_reward();
      b.reward := r.item; b.reward_n := r.n; b.reward_until := now() + make_interval(mins => r.mins);
    end if;
    update public.state_bases set reward = b.reward, reward_n = b.reward_n, reward_until = b.reward_until, xp_paid_until = b.xp_paid_until
    where server = b.server and id = b.id;
  end loop;
end $$;
revoke all on function public._zx_bases_tick(text) from public, anon, authenticated;
revoke all on function public._zx_clan_add_xp(bigint, bigint, bigint) from public, anon, authenticated;

create or replace function public._zx_bases_json(p_server text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'now', (extract(epoch from now()) * 1000)::bigint,
    'bases', coalesce((select jsonb_agg(jsonb_build_object(
        'id', b.id, 'owner', coalesce(b.owner_clan, 0), 'name', coalesce(c.name, ''), 'color', coalesce(c.color, 0),
        'reward', b.reward, 'n', b.reward_n, 'end', (extract(epoch from b.reward_until) * 1000)::bigint,
        'cap', coalesce((extract(epoch from b.captured_at) * 1000)::bigint, 0)) order by b.id)
      from public.state_bases b left join public.clans c on c.id = b.owner_clan where b.server = p_server), '[]'::jsonb))
$$;

-- herkes okuyabilir (misafir de haritada görür); okuma geçen zamanı işler
create or replace function public.zx_bases(p_server text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform public._zx_bases_tick(p_server);
  return public._zx_bases_json(p_server);
end $$;

-- ele geçirme: klan üyesi, Sv10+, üs başka klanda/devlette, son 90 sn'de el değiştirmemiş, oyuncu başına dakikada 1
create or replace function public.zx_base_capture(p_id bigint, p_key text, p_server text, p_base int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); b public.state_bases; lv int; old_name text; cname text;
begin
  if a.clan_id is null then raise exception 'Üs ele geçirmek için bir klanda olmalısın'; end if;
  if p_base is null or p_base < 0 or p_base > 49 then raise exception 'Geçersiz üs'; end if;
  select level into lv from public.characters where account_id = a.id and server = p_server;
  if coalesce(lv, 0) < 10 then raise exception 'Üs baskını için en az 10. seviye olmalısın'; end if;
  if exists (select 1 from public.base_captures where account_id = a.id and created_at > now() - interval '60 seconds') then
    raise exception 'Çok sık deniyorsun, biraz bekle';
  end if;
  perform public._zx_bases_tick(p_server);  -- eski sahibin tecrübesi/ödülü şimdiye kadar ödenir
  select * into b from public.state_bases where server = p_server and id = p_base for update;
  if b.owner_clan = a.clan_id then raise exception 'Bu üs zaten klanının'; end if;
  if b.captured_at is not null and b.captured_at > now() - interval '90 seconds' then
    raise exception 'Üs az önce el değiştirdi, biraz bekle';
  end if;
  select name into old_name from public.clans where id = b.owner_clan;
  select name into cname from public.clans where id = a.clan_id;
  update public.state_bases set owner_clan = a.clan_id, captured_at = now(), captured_by = a.id, xp_paid_until = now()
  where server = p_server and id = p_base;
  insert into public.base_captures (server, base, account_id, clan_id) values (p_server, p_base, a.id, a.clan_id);
  insert into public.clan_log (clan_id, text) values (a.clan_id, a.name || ' Devlet Üssü ' || (p_base + 1) || '''ü ele geçirdi');
  if b.owner_clan is not null then
    insert into public.clan_log (clan_id, text) values (b.owner_clan, 'Devlet Üssü ' || (p_base + 1) || ' kaybedildi: ' || coalesce(cname, '?') || ' aldı');
  end if;
  perform public._zx_log(a.id, 'base', 'Devlet Üssü ' || (p_base + 1) || ' ele geçirildi (' || coalesce(old_name, 'Devlet') || ' → ' || coalesce(cname, '?') || ')');
  return public._zx_bases_json(p_server);
end $$;

grant execute on function public.zx_bases(text) to anon, authenticated;
grant execute on function public.zx_base_capture(bigint, text, text, int) to anon, authenticated;
