-- V15 oyun sunucusu (rust/zenvex_server): canlı oyun Supabase Realtime yerine kendi WebSocket sunucumuzda.
--  - zx_net_auth: oyun sunucusu, bağlanan oyuncunun oturumunu doğrular ve imzayı kontrol etmek için oturum sırrını
--    alır. Yalnız servis anahtarı (service_role) çağırabilir; oyuncular çağıramaz.
--  - zx_status: app_config 'game_server' ({"url": "wss://..."}) dolu ise istemciler oyun sunucusuna bağlanır,
--    boşsa eskisi gibi Supabase Realtime kullanılır (sunucu kurulana kadar).

create or replace function public.zx_net_auth(p_id bigint, p_sid uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare a public.accounts; s public.sessions;
begin
  select * into a from public.accounts where id = p_id;
  if a.id is null or a.deleted_at is not null or (a.banned_until is not null and a.banned_until > now()) then
    raise exception 'OTURUM|Hesap kullanılamıyor' using errcode = '28000';
  end if;
  select * into s from public.sessions where account_id = p_id;
  if s.account_id is null or s.sid <> p_sid or s.last_beat < now() - interval '10 minutes' then
    raise exception 'OTURUM|Oturum geçersiz' using errcode = '28000';
  end if;
  return jsonb_build_object('secret', s.secret, 'name', a.name, 'clan', coalesce(a.clan_id, 0), 'server', s.server,
    'admin', a.id = 0 and a.is_admin);
end $$;
revoke all on function public.zx_net_auth(bigint, uuid) from public, anon, authenticated;
grant execute on function public.zx_net_auth(bigint, uuid) to service_role;

insert into public.app_config (key, value) values ('game_server', '{"url": ""}'::jsonb) on conflict (key) do nothing;

create or replace function public.zx_status(p_device text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('maintenance', (select value from public.app_config where key = 'maintenance'), 'now', now(),
    'admin_device', public._zx_admin_device(p_device),
    'net', coalesce((select value from public.app_config where key = 'game_server'), '{}'::jsonb))
$$;
grant execute on function public.zx_status(text) to anon, authenticated;
