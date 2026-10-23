-- V11 çerçeveler ve seviye elması.
-- Çerçeveler (şimdilik yalnız bunlar): lv10, lv25, lv50 (seviye), season1 (Sezon #1 bileti), blood (Kanlı Ay etkinliğinde ilk 10).
-- Seviye atlayınca elmas: 2-10 → 25, 11-20 → 50, 21-30 → 100, 31-40 → 150, 41-50 → 250 (her seviye için).
-- Elmas "grants" ile verilir (zx_save3 kayıt doğrulayıcısı artışı bununla eşler); karakter başına son ödüllendirilen seviye tutulur.

alter table public.characters add column if not exists level_gems int not null default 1;
-- mevcut karakterler geriye dönük ödül almaz (yalnız bundan sonraki seviyeler)
update public.characters set level_gems = greatest(level, 1) where level_gems < level;

create table if not exists public.account_frames (
  account_id bigint not null references public.accounts(id) on update cascade on delete cascade,
  frame text not null,
  created_at timestamptz not null default now(),
  primary key (account_id, frame)
);
alter table public.account_frames enable row level security;

create or replace function public._zx_level_gem(l int) returns int
language sql immutable set search_path = public as $$
  select case when l <= 1 then 0 when l <= 10 then 25 when l <= 20 then 50 when l <= 30 then 100 when l <= 40 then 150 else 250 end
$$;

-- açılmış çerçeveler
create or replace function public._zx_frames(p bigint) returns jsonb
language sql stable security definer set search_path = public as $$
  select to_jsonb(array_remove(array[
    case when public._zx_level(p) >= 10 then 'lv10' end,
    case when public._zx_level(p) >= 25 then 'lv25' end,
    case when public._zx_level(p) >= 50 then 'lv50' end,
    case when exists (select 1 from public.pass_owned o where o.account_id = p and o.season = 1) then 'season1' end,
    case when exists (select 1 from public.account_frames f where f.account_id = p and f.frame = 'blood') then 'blood' end
  ], null))
$$;

-- seviye ödülü: son kayıttaki seviyeye kadar ödüllendirilmemiş her seviye için elmas (oturumun sunucusundaki karakter)
create or replace function public.zx_level_gems(p_id bigint, p_key text, p_sid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; c public.characters; total int := 0; l int;
begin
  s := public._zx_session(a, p_sid);
  select * into c from public.characters where account_id = a.id and server = s.server for update;
  if c.account_id is null or c.level <= c.level_gems then
    return jsonb_build_object('gems', 0);
  end if;
  for l in (c.level_gems + 1)..least(c.level, 60) loop total := total + public._zx_level_gem(l); end loop;
  update public.characters set level_gems = c.level where account_id = a.id and server = s.server;
  if total > 0 then
    insert into public.grants (account_id, gems, gold, note) values (a.id, total, 0, 'seviye ödülü ' || c.level_gems || '→' || c.level);
  end if;
  return jsonb_build_object('gems', total, 'from', c.level_gems, 'to', c.level);
end $$;
grant execute on function public.zx_level_gems(bigint, text, uuid) to anon, authenticated;

-- eski çerçeveler yenilerine taşınır (bronz → Sv 10, gümüş/altın → Sv 25, diğerleri kaldırılır)
update public.accounts set frame = case frame when 'bronze' then 'lv10' when 'silver' then 'lv25' when 'gold' then 'lv25' else '' end
 where frame not in ('', 'lv10', 'lv25', 'lv50', 'season1', 'blood');

create or replace function public.zx_profile_set(p_id bigint, p_key text, p_avatar integer, p_frame text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); f text := coalesce(p_frame, '');
begin
  if p_avatar < 0 or p_avatar > 63 then raise exception 'Geçersiz fotoğraf'; end if;
  if f <> '' and not (public._zx_frames(a.id) ? f) then
    raise exception '%', case f when 'lv10' then '10. seviyede açılır' when 'lv25' then '25. seviyede açılır' when 'lv50' then '50. seviyede açılır'
      when 'season1' then 'Sezon #1 bileti alınınca açılır' when 'blood' then 'Kanlı Ay etkinliğinde ilk 10''a girince açılır' else 'Bu çerçeve yok' end;
  end if;
  update public.accounts set avatar = p_avatar, frame = left(f, 24) where id = a.id returning * into a;
  return public._zx_profile(a);
end $$;

-- Kanlı Ay: geçmiş etkinliklerin ilk 10'una çerçeve
insert into public.account_frames (account_id, frame)
select x.account_id, 'blood' from (
  select s.account_id, row_number() over (partition by s.event_id order by s.score desc, s.updated_at) rk
  from public.event_scores s join public.events e on e.id = s.event_id
  where e.kind = 'blood_moon' and e.finalized and s.score > 0) x
where x.rk <= 10
on conflict do nothing;

-- etkinlik kapanışı: Kanlı Ay ilk 10 çerçevesi
create or replace function public._zx_event_finalize(force boolean default false) returns void
language plpgsql security definer set search_path = public as $$
declare e public.events; r record; t text; pre text; it text; nm text; gems int;
begin
  for e in select * from public.events where active and not finalized and (force or ends_at <= now()) for update loop
    t := e.title; pre := public._zx_event_item(e.kind);
    for r in select s.account_id, s.score, row_number() over (order by s.score desc, s.updated_at) as rk
             from public.event_scores s where s.event_id = e.id and s.score > 0 order by s.score desc limit 100 loop
      -- Kanlı Ay: ilk 10'a kalıcı çerçeve
      if e.kind = 'blood_moon' and r.rk <= 10 then
        insert into public.account_frames (account_id, frame) values (r.account_id, 'blood') on conflict do nothing;
      end if;
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

-- profil: açılmış çerçeveler listesi eklendi
create or replace function public._zx_profile(a public.accounts) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', a.id, 'name', a.name, 'admin', a.is_admin, 'avatar', a.avatar, 'frame', a.frame, 'photo', a.photo,
    'vip', a.vip_until is not null and a.vip_until > now(), 'vip_until', a.vip_until,
    'clan', (select public._zx_clan_json(c) || jsonb_build_object('owner', c.owner = a.id, 'rank', a.clan_rank,
               'rank_name', public._zx_rank_name(a.clan_rank), 'muted_until', a.clan_muted_until)
             from public.clans c where c.id = a.clan_id),
    'chars', coalesce((select jsonb_object_agg(server, level) from public.characters where account_id = a.id), '{}'::jsonb),
    'level', public._zx_level(a.id), 'kills', public._zx_kills(a.id), 'tags', public._zx_tags(a.id),
    'title', public._zx_title(a), 'titles', public._zx_titles(a.id), 'frames', public._zx_frames(a.id),
    'hours', (select coalesce(sum(play_seconds), 0) / 3600 from public.characters where account_id = a.id),
    'unread', (select count(*) from public.mail m where m.account_id = a.id and not m.read and not m.deleted and (m.favorite or m.created_at > now() - interval '30 days')),
    'friend_req', (select count(*) from public.friends f where f.b = a.id and not f.accepted and not f.removed),
    'dm_unread', (select count(*) from public.dm d where d.to_id = a.id and not d.read),
    'invites', (select count(*) from public.clan_invites i where i.account_id = a.id),
    'key_changed_at', a.key_changed_at,
    'google', a.google_sub is not null)
$$;
