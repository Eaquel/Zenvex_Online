-- V15 destek ve bildirim (R41b, R41c).
--  - Bildirim: neden + 200 karakterlik açıklama. Yönetici her bildirimde iki profil kartını (seviye, klan, cihaz, geçmiş
--    bildirimler, son sohbet satırları) yan yana görür; ele alınan bildirim kapanır.
--  - Destek: konu + açıklama + en çok 10 fotoğraf (her biri en çok 10 MB). Aynı cihazdan ve aynı hesaptan 15 dakikada
--    bir talep. Fotoğraflar gizli "support" kovasında; yüklemeyi ve imzalı bağlantıyı zx-support edge function'ı yapar
--    (servis anahtarıyla, yalnız bu fonksiyonların onayladığı yollar için). Yanıt oyuncuya posta olarak gider.

alter table public.reports add column if not exists note text not null default '';
alter table public.reports add column if not exists handled boolean not null default false;
alter table public.reports add column if not exists handled_at timestamptz;
alter table public.reports add column if not exists action text;

create or replace function public.zx_report(p_id bigint, p_key text, p_target bigint, p_reason text, p_note text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t public.accounts; r text := left(coalesce(trim(p_reason), ''), 120);
        n text := left(coalesce(trim(p_note), ''), 200);
begin
  if r = '' then raise exception 'Bir neden seç'; end if;
  select * into t from public.accounts where id = p_target and deleted_at is null;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if t.id = a.id then raise exception 'Kendini şikâyet edemezsin'; end if;
  if (select count(*) from public.reports where reporter = a.id and created_at > now() - interval '1 day') >= 10 then
    raise exception 'Bugün çok fazla şikâyet gönderdin';
  end if;
  if exists (select 1 from public.reports where reporter = a.id and target = t.id and created_at > now() - interval '1 hour') then
    raise exception 'Bu oyuncuyu az önce şikâyet ettin';
  end if;
  insert into public.reports (reporter, target, reason, note) values (a.id, t.id, r, n);
  perform public._zx_flag(t.id, 'report', a.name || ' şikâyet etti: ' || r || case when n <> '' then ' — ' || n else '' end);
  return jsonb_build_object('ok', true);
end $$;
grant execute on function public.zx_report(bigint, text, bigint, text, text) to anon, authenticated;

-- yönetici: bir hesabın kısa kartı (bildirim ekranında iki profil yan yana)
create or replace function public._zx_card(p_acc bigint)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', a.id, 'name', a.name, 'avatar', a.avatar, 'frame', a.frame, 'photo', a.photo,
    'level', (select coalesce(max(c.level), 1) from public.characters c where c.account_id = a.id),
    'clan', (select name from public.clans where id = a.clan_id),
    'banned', a.banned_until is not null and a.banned_until > now(), 'created', (extract(epoch from a.created_at) * 1000)::bigint,
    'last_seen', (extract(epoch from a.last_seen) * 1000)::bigint,
    'device', (select s.device from public.sessions s where s.account_id = a.id order by s.started_at desc limit 1),
    'reported', (select count(*) from public.reports r where r.target = a.id),
    'reports_made', (select count(*) from public.reports r where r.reporter = a.id),
    'chat', (select coalesce(jsonb_agg(x.text order by x.created_at desc), '[]') from
              (select text, created_at from public.chat where account_id = a.id order by created_at desc limit 5) x))
  from public.accounts a where a.id = p_acc
$$;
revoke all on function public._zx_card(bigint) from public, anon, authenticated;

create or replace function public.zx_admin_reports(p_id bigint, p_key text, p_open boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id', r.id, 'reason', r.reason, 'note', r.note, 'handled', r.handled, 'action', r.action,
      'at', (extract(epoch from r.created_at) * 1000)::bigint, 'reporter', public._zx_card(r.reporter), 'target', public._zx_card(r.target))
      order by r.created_at desc)
    from (select * from public.reports where handled = not p_open order by created_at desc limit 60) r), '[]');
end $$;
grant execute on function public.zx_admin_reports(bigint, text, boolean) to anon, authenticated;

create or replace function public.zx_admin_report_done(p_id bigint, p_key text, p_report bigint, p_action text)
returns text language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  update public.reports set handled = true, handled_at = now(), action = left(coalesce(p_action, ''), 40) where id = p_report;
  if not found then raise exception 'Bildirim bulunamadı'; end if;
  return 'Bildirim kapatıldı';
end $$;
grant execute on function public.zx_admin_report_done(bigint, text, bigint, text) to anon, authenticated;

-- ------------------------------------------------------------------ destek
create table if not exists public.support_tickets (
  id bigserial primary key,
  account_id bigint not null references public.accounts(id),
  device text not null,
  topic text not null,
  body text not null,
  photos integer not null default 0,
  paths text[] not null default '{}',
  status text not null default 'open',
  reply text,
  created_at timestamptz not null default now(),
  replied_at timestamptz
);
create index if not exists support_tickets_device on public.support_tickets (device, created_at desc);
create index if not exists support_tickets_status on public.support_tickets (status, created_at desc);
alter table public.support_tickets enable row level security;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('support', 'support', false, 10485760, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = 10485760, allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.zx_support_open(p_id bigint, p_key text, p_device text, p_topic text, p_body text, p_photos integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); d text := left(coalesce(trim(p_device), ''), 80);
        b text := left(coalesce(trim(p_body), ''), 2000); t bigint;
begin
  if d = '' then raise exception 'Cihaz bilgisi yok'; end if;
  if length(b) < 10 then raise exception 'Sorunu biraz daha ayrıntılı yaz (en az 10 harf)'; end if;
  if p_topic not in ('Hata', 'Ödeme', 'Hesap', 'Öneri', 'Diğer') then raise exception 'Bir konu seç'; end if;
  if p_photos < 0 or p_photos > 10 then raise exception 'En çok 10 fotoğraf eklenebilir'; end if;
  if exists (select 1 from public.support_tickets where (device = d or account_id = a.id) and created_at > now() - interval '15 minutes') then
    raise exception 'Yeni bir destek talebi için 15 dakika bekle';
  end if;
  insert into public.support_tickets (account_id, device, topic, body, photos) values (a.id, d, p_topic, b, p_photos) returning id into t;
  return jsonb_build_object('ticket', t);
end $$;
grant execute on function public.zx_support_open(bigint, text, text, text, text, integer) to anon, authenticated;

-- edge function: fotoğraf yüklemeden önce yolu onaylar (talep sahibi, sıra numarası talepteki sayının altında, yeni)
create or replace function public.zx_support_photo_path(p_id bigint, p_key text, p_ticket bigint, p_idx integer, p_ext text)
returns text language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t public.support_tickets; path text;
begin
  select * into t from public.support_tickets where id = p_ticket and account_id = a.id;
  if t.id is null then raise exception 'Talep bulunamadı'; end if;
  if t.created_at < now() - interval '30 minutes' then raise exception 'Talebin fotoğraf süresi doldu'; end if;
  if p_idx < 0 or p_idx >= t.photos then raise exception 'Fotoğraf sırası geçersiz'; end if;
  if p_ext not in ('jpg', 'png', 'webp') then raise exception 'Desteklenmeyen biçim'; end if;
  path := t.id || '/' || p_idx || '.' || p_ext;
  if path = any(t.paths) then raise exception 'Bu fotoğraf zaten yüklendi'; end if;
  update public.support_tickets set paths = array_append(paths, path) where id = t.id;
  return path;
end $$;
revoke all on function public.zx_support_photo_path(bigint, text, bigint, integer, text) from public, anon, authenticated;
grant execute on function public.zx_support_photo_path(bigint, text, bigint, integer, text) to service_role;

create or replace function public.zx_admin_support(p_id bigint, p_key text, p_open boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id', t.id, 'topic', t.topic, 'body', t.body, 'paths', to_jsonb(t.paths), 'status', t.status,
      'reply', t.reply, 'device', t.device, 'at', (extract(epoch from t.created_at) * 1000)::bigint, 'from', public._zx_card(t.account_id))
      order by t.created_at desc)
    from (select * from public.support_tickets where (status = 'open') = p_open order by created_at desc limit 60) t), '[]');
end $$;
grant execute on function public.zx_admin_support(bigint, text, boolean) to anon, authenticated;

-- edge function: yönetici imzalı bağlantı istemeden önce (yönetici mi, yollar bu talebin mi)
create or replace function public.zx_admin_support_paths(p_id bigint, p_key text, p_ticket bigint)
returns text[] language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return coalesce((select paths from public.support_tickets where id = p_ticket), '{}');
end $$;
revoke all on function public.zx_admin_support_paths(bigint, text, bigint) from public, anon, authenticated;
grant execute on function public.zx_admin_support_paths(bigint, text, bigint) to service_role;

create or replace function public.zx_admin_support_reply(p_id bigint, p_key text, p_ticket bigint, p_reply text)
returns text language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.support_tickets; r text := left(coalesce(trim(p_reply), ''), 2000);
begin
  if r = '' then raise exception 'Yanıt boş'; end if;
  update public.support_tickets set status = 'closed', reply = r, replied_at = now() where id = p_ticket returning * into t;
  if t.id is null then raise exception 'Talep bulunamadı'; end if;
  insert into public.mail (account_id, sender, title, body, kind) values (t.account_id, 'Destek', 'Destek Talebin #' || t.id || ' (' || t.topic || ')', r, 'system');
  return 'Yanıt postayla gönderildi';
end $$;
grant execute on function public.zx_admin_support_reply(bigint, text, bigint, text) to anon, authenticated;
