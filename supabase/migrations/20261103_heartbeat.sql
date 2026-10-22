-- Nabız: okunmamış posta sayısı (oyundayken posta rozeti güncellenir) ve sahte hız hilesi işaretinin düzeltmesi.
-- Hız kontrolü artık yalnız nabzın kendi zamanına (speed_at) bakar: zx_save3 kayıtta last_beat'i şimdiye çektiği için
-- kayıttan hemen sonraki nabızda gerçek süre ~0 görünüyor, oyun saati 15 sn ilerlemiş oluyordu (herkes "hız" işareti alıyordu).
alter table public.sessions add column if not exists speed_at timestamptz;

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
  return jsonb_build_object('ok', true, 'time', (extract(epoch from now()) * 1000)::bigint,
    'actions', (select count(*) from public.admin_actions where account_id = a.id and done_at is null),
    'unread', (select count(*) from public.mail m where m.account_id = a.id and not m.read and not m.deleted
               and (m.favorite or m.created_at > now() - interval '30 days')),
    'trade', (select t.id from public.trades t where ((t.a = a.id and not t.a_claimed) or (t.b = a.id and not t.b_claimed))
               and t.state in ('done', 'cancel') order by t.id desc limit 1));
end $$;
