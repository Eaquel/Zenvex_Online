-- V13 yönetici cihazları: yönetici hesabının (id 0) oturum açtığı her cihaz kalıcı olarak "yönetici cihazı" olur.
--  - Bakım açıkken yönetici cihazından herhangi bir hesapla girilebilir (test hesapları dahil).
--  - Yönetici yetkileri (panel, hileler) yine yalnız yönetici hesabındadır; cihaz yalnız bakım kapısını açar.
-- zx_status(p_device) bakım ekranını göstermeden önce cihazın yönetici cihazı olup olmadığını da söyler.

create table if not exists public.admin_devices (
  device text primary key,
  model text,
  added_at timestamptz not null default now()
);
alter table public.admin_devices enable row level security;

-- şimdiye kadar yönetici hesabının girdiği cihazlar
insert into public.admin_devices (device, model)
select device, max(model) from public.devices where account_id = 0 group by device
on conflict (device) do nothing;

create or replace function public._zx_admin_device(p_device text) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(p_device, '') <> '' and exists (select 1 from public.admin_devices where device = p_device)
$$;

-- bakım kontrolü: yönetici hesabı ya da oturumu yönetici cihazında açılmış hesap geçer
create or replace function public._zx_maint_check(a public.accounts) returns void
language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  select value into v from public.app_config where key = 'maintenance';
  if coalesce((v ->> 'on')::boolean, false) and not (a.id = 0 and a.is_admin)
     and not exists (select 1 from public.sessions s join public.admin_devices d on d.device = s.device where s.account_id = a.id) then
    raise exception 'BAKIM|%', coalesce(v ->> 'msg', '') using errcode = '28000';
  end if;
end $$;

-- eski istemciler için parametresiz zx_status() yerinde kalır
create or replace function public.zx_status(p_device text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('maintenance', (select value from public.app_config where key = 'maintenance'), 'now', now(),
    'admin_device', public._zx_admin_device(p_device))
$$;
grant execute on function public.zx_status(text) to anon, authenticated;

create or replace function public.zx_session_start(p_id bigint, p_key text, p_server text, p_device jsonb)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key); dev text := left(coalesce(p_device ->> 'id', ''), 80);
        s public.sessions; ch public.characters; br public.banned_devices; adev boolean;
begin
  -- yönetici hesabı girdiği cihazı yönetici cihazı yapar
  if a.id = 0 and a.is_admin and dev <> '' then
    insert into public.admin_devices (device, model) values (dev, left(coalesce(p_device ->> 'model', ''), 80))
    on conflict (device) do update set model = excluded.model;
  end if;
  adev := public._zx_admin_device(dev);
  if not adev then perform public._zx_maint_check(a); end if;
  if p_server not in ('n1', 'n2', 'n3', 'hc') then raise exception 'Geçersiz sunucu'; end if;
  if dev = '' then raise exception 'Cihaz doğrulanamadı'; end if;
  select * into br from public.banned_devices where device = dev and active;
  if br.device is not null and a.id <> 0 then
    raise exception 'BAN|Kalıcı|Bu cihaz yasaklı: %', br.reason using errcode = '28000';
  end if;
  if coalesce((p_device ->> 'sig')::boolean, true) = false or coalesce((p_device ->> 'hook')::boolean, false)
     or coalesce((p_device ->> 'dbg')::boolean, false) then
    perform public._zx_flag(a.id, 'client', 'Giriş reddedildi: ' || p_device::text);
    if a.id <> 0 then raise exception 'Güvenlik kontrolü başarısız: uygulama değiştirilmiş ya da izleniyor'; end if;
  end if;
  if coalesce((p_device ->> 'root')::boolean, false) or coalesce((p_device ->> 'emu')::boolean, false) then
    if not exists (select 1 from public.cheat_flags where account_id = a.id and kind = 'device' and created_at > now() - interval '1 day') then
      perform public._zx_flag(a.id, 'device', 'Root/Emülatör: ' || p_device::text);
    end if;
  end if;
  insert into public.devices (account_id, device, model, info) values (a.id, dev, left(coalesce(p_device ->> 'model', ''), 80), p_device)
  on conflict (account_id, device) do update set last_seen = now(), info = excluded.info, model = excluded.model;
  insert into public.sessions (account_id, sid, secret, server, device, started_at, last_beat, seq, last_save, game_time)
  values (a.id, gen_random_uuid(), encode(gen_random_bytes(24), 'hex'), p_server, dev, now(), now(), 0, null, 0)
  on conflict (account_id) do update set sid = excluded.sid, secret = excluded.secret, server = excluded.server, device = excluded.device,
    started_at = now(), last_beat = now(), seq = 0, last_save = null, game_time = 0
  returning * into s;
  select * into ch from public.characters where account_id = a.id and server = p_server;
  perform public._zx_log(a.id, 'session', 'Oyuna girdi (' || p_server || ', ' || coalesce(p_device ->> 'model', '?') || ')');
  return jsonb_build_object('sid', s.sid, 'secret', s.secret, 'data', coalesce(ch.data, ''), 'time', (extract(epoch from now()) * 1000)::bigint,
    'admin', a.id = 0 and a.is_admin, 'admin_device', adev);
end $$;
