-- V12.2 klan tecrübesi düzeltmesi.
-- Hata: zx_save2 oyuncunun kazandığı XP'nin tamamını (kayıt başına 20.000'e kadar) klana da yazıyordu; tek kişilik bir klan
-- birkaç günde 9. seviyeye çıkıyordu. Artık:
--  - oyuncu XP'sinin yalnız %5'i klana gider (kayıt başına en çok 1.000),
--  - tutulan üs dakikada 15 klan tecrübesi verir (60 idi),
--  - seviye eşikleri çok daha dik: 3 bin → 1,4 milyon.
-- Mevcut klanlar: eski değerler xp_total_v1 / level_v1 sütunlarında saklanır, toplam tecrübe 20'ye bölünür ve seviye yeni
-- eşiklere göre yeniden hesaplanır (yalnız bir kez).

alter table public.clans add column if not exists xp_total_v1 bigint;
alter table public.clans add column if not exists level_v1 int;

create or replace function public._zx_clan_need(l integer) returns bigint
language sql immutable set search_path = public as $$
  select (array[0, 3000, 10000, 25000, 60000, 140000, 300000, 650000, 1400000])[greatest(1, least(l, 9))]::bigint
$$;

-- tek seferlik: yedekle, ölçekle, seviyeyi yeniden hesapla
update public.clans set xp_total_v1 = xp_total, level_v1 = level, xp_total = xp_total / 20 where xp_total_v1 is null;
update public.clans c set level = coalesce((select max(l) from generate_series(1, 9) l where c.xp_total >= public._zx_clan_need(l)), 1)
 where c.level_v1 is not null;

create or replace function public.zx_save2(p_id bigint, p_key text, p_server text, p_level integer, p_data text, p_kills integer, p_xp bigint)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); old public.characters; dxp bigint := 0; dk int := 0; cxp bigint := 0;
        c public.clans; lvl int; n int; gold bigint;
begin
  if char_length(p_data) > 400000 then raise exception 'Kayıt çok büyük'; end if;
  select * into old from public.characters where account_id = a.id and server = p_server;
  if old.account_id is not null then
    dxp := least(greatest(p_xp - old.xp_total, 0), 20000);
    dk := least(greatest(p_kills - old.kills, 0), 300);
  end if;
  -- klana oyuncu XP'sinin %5'i
  cxp := dxp / 20;
  begin gold := coalesce((p_data::jsonb ->> 'gold')::bigint, 0); exception when others then gold := 0; end;
  if old.account_id is not null and p_level > old.level then
    perform public._zx_log(a.id, 'level', 'Seviye ' || old.level || ' → ' || p_level);
  end if;
  if not exists (select 1 from public.activity where account_id = a.id and kind = 'save' and created_at > now() - interval '15 minutes') then
    perform public._zx_log(a.id, 'save', 'Sv ' || p_level || ' · ' || gold || ' altın · ' || p_kills || ' öldürme · +' || dk || ' öldürme, +' || dxp || ' XP');
  end if;
  insert into public.characters (account_id, server, level, data, updated_at, kills, xp_total)
  values (a.id, p_server, greatest(1, least(p_level, 999)), p_data, now(), greatest(p_kills, 0), greatest(p_xp, 0))
  on conflict (account_id, server) do update set level = excluded.level, data = excluded.data, updated_at = now(),
    kills = greatest(public.characters.kills, excluded.kills), xp_total = greatest(public.characters.xp_total, excluded.xp_total);
  if a.clan_id is not null and (cxp > 0 or dk > 0) then
    insert into public.clan_xp (clan_id, account_id, week, xp, kills) values (a.clan_id, a.id, public._zx_week(), cxp, dk)
    on conflict (clan_id, account_id, week) do update set xp = public.clan_xp.xp + excluded.xp, kills = public.clan_xp.kills + excluded.kills;
    update public.clans set xp_total = xp_total + cxp where id = a.clan_id returning * into c;
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
  return jsonb_build_object('ok', true, 'clan_xp', cxp,
    'actions', (select count(*) from public.admin_actions where account_id = a.id and done_at is null));
end $$;

-- üs: dakikada 15 klan tecrübesi (60 idi)
do $$
declare d text;
begin
  select pg_get_functiondef('public._zx_bases_tick(text)'::regprocedure) into d;
  if position('least(mins, 10080) * 60)' in d) > 0 then
    execute replace(d, 'least(mins, 10080) * 60)', 'least(mins, 10080) * 15)');
  end if;
end $$;

-- (uygulandı, bir kez) bu haftanın klan sıralaması da aynı ölçeğe çekildi:
-- update public.clan_xp set xp = xp / 20 where week = public._zx_week();
