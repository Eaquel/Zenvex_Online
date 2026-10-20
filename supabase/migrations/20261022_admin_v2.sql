-- Yönetici v2: oyuncu dosyası (son işlemler, sohbetler, özel mesajlar, envanter), uzaktan işlem kuyruğu (eşya silme vb.),
-- tüm sohbet kanallarının kaydı. (Uygulandı: zenvex-online, 20.10.2026)

create table if not exists public.activity (
  id bigint generated always as identity primary key,
  account_id bigint not null references public.accounts(id) on delete cascade,
  kind text not null,
  text text not null,
  created_at timestamptz not null default now()
);
create index if not exists activity_acc on public.activity (account_id, id desc);
alter table public.activity enable row level security;

create table if not exists public.admin_actions (
  id bigint generated always as identity primary key,
  account_id bigint not null references public.accounts(id) on delete cascade,
  action jsonb not null,
  by_name text not null,
  created_at timestamptz not null default now(),
  done_at timestamptz
);
create index if not exists admin_actions_acc on public.admin_actions (account_id) where done_at is null;
alter table public.admin_actions enable row level security;

create or replace function public._zx_log(p bigint, k text, t text) returns void
language sql security definer set search_path = public as $$
  insert into public.activity (account_id, kind, text) values (p, k, left(t, 300))
$$;
revoke all on function public._zx_log(bigint, text, text) from public, anon, authenticated;

-- giriş kaydı
create or replace function public.zx_login(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if not exists (select 1 from public.activity where account_id = a.id and kind = 'login' and created_at > now() - interval '10 minutes') then
    perform public._zx_log(a.id, 'login', 'Giriş yaptı');
  end if;
  return public._zx_profile(a);
end $$;

-- kayıt: seviye atlama ve 15 dakikada bir özet işlem kaydı
create or replace function public.zx_save2(p_id bigint, p_key text, p_server text, p_level int, p_data text, p_kills int, p_xp bigint)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); old public.characters; dxp bigint := 0; dk int := 0;
        c public.clans; lvl int; n int; gold bigint;
begin
  if char_length(p_data) > 400000 then raise exception 'Kayıt çok büyük'; end if;
  select * into old from public.characters where account_id = a.id and server = p_server;
  if old.account_id is not null then
    dxp := least(greatest(p_xp - old.xp_total, 0), 20000);
    dk := least(greatest(p_kills - old.kills, 0), 300);
  end if;
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
  if a.clan_id is not null and (dxp > 0 or dk > 0) then
    insert into public.clan_xp (clan_id, account_id, week, xp, kills) values (a.clan_id, a.id, public._zx_week(), dxp, dk)
    on conflict (clan_id, account_id, week) do update set xp = public.clan_xp.xp + excluded.xp, kills = public.clan_xp.kills + excluded.kills;
    update public.clans set xp_total = xp_total + dxp where id = a.clan_id returning * into c;
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
  return jsonb_build_object('ok', true, 'clan_xp', dxp,
    'actions', (select count(*) from public.admin_actions where account_id = a.id and done_at is null));
end $$;

-- posta alma kaydı
create or replace function public.zx_mail_claim(p_id bigint, p_key text, p_mail bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); m public.mail;
begin
  update public.mail set claimed = true, read = true
  where id = p_mail and account_id = a.id and not claimed and not deleted
    and (favorite or created_at > now() - interval '30 days') returning * into m;
  if m.id is null then raise exception 'Bu posta zaten alınmış'; end if;
  perform public._zx_log(a.id, 'mail', 'Posta aldı: ' || m.title || ' (' || m.gold || ' altın, ' || m.gems || ' elmas, ' || m.items::text || ')');
  return jsonb_build_object('gold', m.gold, 'xp', m.xp, 'gems', m.gems, 'items', m.items);
end $$;

-- yakın / klan sohbetinin kaydı (genel sohbet zx_chat_post ile kaydedilir); kanal: "<sunucu>:local" ya da "clan:<id>"
create or replace function public.zx_chat_log(p_id bigint, p_key text, p_channel text, p_text text) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t text := left(trim(p_text), 140);
begin
  if t = '' or p_channel !~ '^[a-z0-9:]{2,24}$' then return false; end if;
  if exists (select 1 from public.chat where account_id = a.id and created_at > now() - interval '1 second') then return false; end if;
  insert into public.chat (server, account_id, name, clan, text)
  values (p_channel, a.id, a.name, (select name from public.clans where id = a.clan_id), t);
  return true;
end $$;

-- ---------------------------------------------------------------- uzaktan işlemler
-- işlem: {"t":"remove_item","item":<index>,"n":N} | {"t":"clear_bag"} | {"t":"set_gold","v":N} | {"t":"kick","msg":".."} | {"t":"msg","msg":".."}
create or replace function public.zx_actions_pull(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id', id, 'a', action) order by id)
    from public.admin_actions where account_id = a.id and done_at is null), '[]'::jsonb);
end $$;

create or replace function public.zx_actions_done(p_id bigint, p_key text, p_ids bigint[]) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  update public.admin_actions set done_at = now() where account_id = a.id and id = any(p_ids) and done_at is null;
  return true;
end $$;

-- Yönetici: işlemi kuyruğa ekler; eşya silme ve altın ayarı bulut kaydına da hemen uygulanır (oyuncu çevrimdışıysa)
create or replace function public.zx_admin_action(p_id bigint, p_key text, p_target bigint, p_action jsonb) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts; k text := p_action ->> 't'; ch record; d jsonb; slots jsonb;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if k not in ('remove_item', 'clear_bag', 'set_gold', 'kick', 'msg') then raise exception 'Geçersiz işlem'; end if;
  insert into public.admin_actions (account_id, action, by_name) values (t.id, p_action, a.name);
  for ch in select * from public.characters where account_id = t.id loop
    begin
      d := ch.data::jsonb;
      if k = 'set_gold' then
        d := jsonb_set(d, '{gold}', to_jsonb(greatest((p_action ->> 'v')::bigint, 0)));
      elsif k = 'clear_bag' then
        slots := (select jsonb_agg('null'::jsonb) from jsonb_array_elements(d #> '{inv,slots}'));
        d := jsonb_set(d, '{inv,slots}', coalesce(slots, '[]'::jsonb));
      elsif k = 'remove_item' then
        slots := (select jsonb_agg(case when e -> 'item' is not null and (e ->> 'item')::int = (p_action ->> 'item')::int then 'null'::jsonb else e end order by i)
                  from jsonb_array_elements(d #> '{inv,slots}') with ordinality as x(e, i));
        d := jsonb_set(d, '{inv,slots}', coalesce(slots, '[]'::jsonb));
      end if;
      if k in ('set_gold', 'clear_bag', 'remove_item') then
        update public.characters set data = d::text where account_id = ch.account_id and server = ch.server;
      end if;
    exception when others then null;  -- bozuk kayıt: yalnız kuyruk uygulanır
    end;
  end loop;
  perform public._zx_log(t.id, 'admin', a.name || ' yönetici işlemi: ' || p_action::text);
  return case k when 'remove_item' then 'Eşya silindi' when 'clear_bag' then 'Çanta boşaltıldı' when 'set_gold' then 'Altın ayarlandı'
                when 'kick' then 'Oyuncu oyundan atılacak' else 'Mesaj gönderildi' end;
end $$;

-- Yönetici: oyuncu dosyası
create or replace function public.zx_admin_player(p_id bigint, p_key text, p_target bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  return public._zx_profile(t) || jsonb_build_object(
    'created_at', t.created_at, 'last_seen', t.last_seen, 'banned_until', t.banned_until, 'ban_reason', t.ban_reason,
    'email', t.email, 'google', t.google_sub is not null,
    'activity', coalesce((select jsonb_agg(jsonb_build_object('k', kind, 't', text, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
        from (select * from public.activity where account_id = t.id order by id desc limit 120) x), '[]'::jsonb),
    'chat', coalesce((select jsonb_agg(jsonb_build_object('ch', server, 't', text, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
        from (select * from public.chat where account_id = t.id order by id desc limit 120) x), '[]'::jsonb),
    'dm', coalesce((select jsonb_agg(jsonb_build_object('to', (select name from public.accounts where id = case when from_id = t.id then to_id else from_id end),
          'out', from_id = t.id, 't', text, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc)
        from (select * from public.dm where from_id = t.id or to_id = t.id order by id desc limit 80) x), '[]'::jsonb),
    'saves', coalesce((select jsonb_agg(jsonb_build_object('server', server, 'level', level, 'kills', kills, 'updated', updated_at,
          'gold', (case when data ~ '^\s*\{' then (data::jsonb ->> 'gold') end),
          'gems', (case when data ~ '^\s*\{' then (data::jsonb ->> 'gems') end),
          'slots', (case when data ~ '^\s*\{' then (data::jsonb #> '{inv,slots}') end)))
        from public.characters where account_id = t.id), '[]'::jsonb),
    'pending', (select count(*) from public.admin_actions where account_id = t.id and done_at is null));
end $$;

grant execute on function public.zx_chat_log(bigint, text, text, text) to anon, authenticated;
grant execute on function public.zx_actions_pull(bigint, text) to anon, authenticated;
grant execute on function public.zx_actions_done(bigint, text, bigint[]) to anon, authenticated;
grant execute on function public.zx_admin_action(bigint, text, bigint, jsonb) to anon, authenticated;
grant execute on function public.zx_admin_player(bigint, text, bigint) to anon, authenticated;

-- hesap silme talebi (Ayarlar → Yasal); yönetici 30 gün içinde siler
alter table public.accounts add column if not exists delete_requested_at timestamptz;
create or replace function public.zx_request_delete(p_id bigint, p_key text) returns boolean
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  update public.accounts set delete_requested_at = now() where id = a.id;
  perform public._zx_log(a.id, 'delete', 'Hesap silme talebi');
  return true;
end $$;
grant execute on function public.zx_request_delete(bigint, text) to anon, authenticated;
