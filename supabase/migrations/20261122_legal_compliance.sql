-- Hukuki uyum (KVKK, 5651, 6502/Mesafeli Sözleşmeler Yönetmeliği, 6563):
--  1. Onay kayıtları (Kullanım Şartları kabulü, ticari elektronik ileti onayı) tarih, sürüm ve IP ile; ispat yükü bizde.
--  2. Trafik bilgisi (5651 / Yer Sağlayıcılar Yönetmeliği): oturum açma, sohbet, özel mesaj, profil fotoğrafı ve kayıt
--     anında IP + zaman. 1 yıl saklanır, sonra silinir. Hesap anonimleşse de süre dolana dek kalır (hukuki yükümlülük).
--  3. Satın alma sonrası kalıcı bilgilendirme postası (sipariş no, ürün, tutar, tarih, cayma hakkı istisnası) ve
--     oyuncunun satın alma geçmişi (zx_my_purchases).
--  4. KVKK m.11: oyuncu sunucudaki verilerinin özetini görür (zx_my_data); silme talebini iptal edebilir.
--  5. Saklama süreleri (zx_retention_purge, günlük): sohbet/DM 180 gün, posta 1 yıl, trafik 1 yıl, kayıtlar 2 yıl;
--     silme talebinden 30 gün sonra hesap anonimleştirilir (satın alma kayıtları vergi/ticaret mevzuatı için anonim kalır).
--  6. Destek konularına "KVKK" (veri sahibi başvurusu) ve "İtiraz" (yaptırım itirazı) eklenir.

alter table public.accounts add column if not exists terms_version integer not null default 0;
alter table public.accounts add column if not exists terms_at timestamptz;
alter table public.accounts add column if not exists anonymized_at timestamptz;

-- ------------------------------------------------------------------ istemci IP'si (PostgREST istek başlıkları)
create or replace function public._zx_client_ip() returns text language plpgsql stable set search_path = public as $$
declare h json;
begin
  h := nullif(current_setting('request.headers', true), '')::json;
  if h is null then return null; end if;
  return left(nullif(trim(coalesce(nullif(h ->> 'cf-connecting-ip', ''), nullif(split_part(coalesce(h ->> 'x-forwarded-for', ''), ',', 1), ''),
    h ->> 'x-real-ip')), ''), 64);
exception when others then return null;
end $$;

create or replace function public._zx_client_ua() returns text language plpgsql stable set search_path = public as $$
begin
  return left(nullif(current_setting('request.headers', true), '')::json ->> 'user-agent', 120);
exception when others then return null;
end $$;

-- ------------------------------------------------------------------ onay kayıtları
create table if not exists public.consents (
  id bigserial primary key,
  account_id bigint not null,           -- yabancı anahtar yok: hesap anonimleşse de kayıt 3 yıl kalır
  kind text not null check (kind in ('terms', 'notice', 'promo')),
  version integer not null default 0,
  granted boolean not null,
  ip text,
  at timestamptz not null default now()
);
create index if not exists consents_account on public.consents (account_id, kind, at desc);
alter table public.consents enable row level security;

create or replace function public.zx_consent(p_id bigint, p_key text, p_kind text, p_version integer, p_granted boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if p_kind not in ('terms', 'notice', 'promo') then raise exception 'Geçersiz onay türü'; end if;
  insert into public.consents (account_id, kind, version, granted, ip) values (a.id, p_kind, coalesce(p_version, 0), p_granted, public._zx_client_ip());
  if p_kind = 'terms' and p_granted then
    update public.accounts set terms_version = greatest(terms_version, coalesce(p_version, 0)), terms_at = now() where id = a.id;
  end if;
  return jsonb_build_object('ok', true);
end $$;
grant execute on function public.zx_consent(bigint, text, text, integer, boolean) to anon, authenticated;

-- açılışta: kabul edilen şartlar sürümü ve son ticari ileti tercihi (yeni cihazda tercih korunur)
create or replace function public.zx_consent_state(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return jsonb_build_object('terms', a.terms_version,
    'promo', (select c.granted from public.consents c where c.account_id = a.id and c.kind = 'promo' order by c.at desc limit 1),
    'delete_requested_at', case when a.delete_requested_at is null then null else (extract(epoch from a.delete_requested_at) * 1000)::bigint end);
end $$;
grant execute on function public.zx_consent_state(bigint, text) to anon, authenticated;

-- ------------------------------------------------------------------ trafik bilgisi (5651)
create table if not exists public.traffic_log (
  id bigserial primary key,
  account_id bigint,                    -- yabancı anahtar yok: hesap anonimleşse de süre dolana dek saklanır
  kind text not null,                   -- register | session | chat | dm | photo
  ip text not null,
  ua text,
  ref text,
  at timestamptz not null default now()
);
create index if not exists traffic_log_account on public.traffic_log (account_id, at desc);
create index if not exists traffic_log_at on public.traffic_log (at);
alter table public.traffic_log enable row level security;

create or replace function public._zx_traffic(p_account bigint, p_kind text, p_ref text) returns void
language plpgsql security definer set search_path = public as $$
declare ip text := public._zx_client_ip();
begin
  -- yalnız istemciden gelen isteklerde (zamanlanmış iş ve yönetici işlemlerinde IP yok)
  if ip is not null then
    insert into public.traffic_log (account_id, kind, ip, ua, ref) values (p_account, p_kind, ip, public._zx_client_ua(), left(p_ref, 60));
  end if;
exception when others then null;     -- günlük yazılamasa da asıl işlem sürsün
end $$;
revoke all on function public._zx_traffic(bigint, text, text) from public, anon, authenticated;

create or replace function public._zx_traffic_trg() returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_table_name = 'sessions' then
    if tg_op = 'INSERT' or new.sid is distinct from old.sid then perform public._zx_traffic(new.account_id, 'session', new.server); end if;
  elsif tg_table_name = 'chat' then
    perform public._zx_traffic(new.account_id, 'chat', new.id::text);
  elsif tg_table_name = 'dm' then
    perform public._zx_traffic(new.from_id, 'dm', new.id::text);
  elsif tg_table_name = 'accounts' then
    if tg_op = 'INSERT' then perform public._zx_traffic(new.id, 'register', null);
    elsif new.photo is distinct from old.photo and coalesce(new.photo, '') <> '' then perform public._zx_traffic(new.id, 'photo', null);
    end if;
  end if;
  return null;
end $$;

drop trigger if exists zx_traffic on public.sessions;
create trigger zx_traffic after insert or update of sid on public.sessions for each row execute function public._zx_traffic_trg();
drop trigger if exists zx_traffic on public.chat;
create trigger zx_traffic after insert on public.chat for each row execute function public._zx_traffic_trg();
drop trigger if exists zx_traffic on public.dm;
create trigger zx_traffic after insert on public.dm for each row execute function public._zx_traffic_trg();
drop trigger if exists zx_traffic_ins on public.accounts;
create trigger zx_traffic_ins after insert on public.accounts for each row execute function public._zx_traffic_trg();
drop trigger if exists zx_traffic_photo on public.accounts;
create trigger zx_traffic_photo after update of photo on public.accounts for each row execute function public._zx_traffic_trg();

-- ------------------------------------------------------------------ satın alma bilgilendirmesi (MSY m.18: kalıcı veri saklayıcısı)
create or replace function public._zx_purchase_receipt() returns trigger language plpgsql security definer set search_path = public as $$
declare pr public.store_products;
begin
  if new.test then return null; end if;
  select * into pr from public.store_products where id = new.product;
  insert into public.mail (account_id, sender, title, body, kind, favorite)
  values (new.account_id, 'Mağaza', 'Satın Alma Bilgilendirmesi',
    'Sipariş no: ' || new.order_id || E'\n' ||
    'Ürün: ' || coalesce(pr.title, new.product) || E'\n' ||
    'Tutar: ' || replace(to_char(new.price_try, 'FM9999990.00'), '.', ',') || ' TL (vergiler dahil; Google Play makbuzundaki tutar esastır)' || E'\n' ||
    'Tarih: ' || to_char(new.created_at at time zone 'Europe/Istanbul', 'DD.MM.YYYY HH24:MI') || E'\n' ||
    'Satıcı ve sözleşme: Ayarlar > Yasal > Ön Bilgilendirme Formu ve Mesafeli Satış Sözleşmesi.' || E'\n' ||
    'Dijital içerik anında teslim edildiğinden cayma hakkı yoktur (Mesafeli Sözleşmeler Yönetmeliği m.15/1-ğ). ' ||
    'Teslimle ilgili bir sorun olursa Ayarlar > Destek > Ödeme ile bize yaz.',
    'system', true);
  return null;
exception when others then return null;  -- bilgilendirme yazılamasa da satın alma geri alınmasın
end $$;
drop trigger if exists zx_receipt on public.purchases;
create trigger zx_receipt after insert on public.purchases for each row execute function public._zx_purchase_receipt();

create or replace function public.zx_my_purchases(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('order', p.order_id, 'product', p.product, 'title', coalesce(s.title, p.product),
      'price', p.price_try, 'state', p.state, 'test', p.test, 'at', (extract(epoch from p.created_at) * 1000)::bigint) order by p.created_at desc)
    from public.purchases p left join public.store_products s on s.id = p.product where p.account_id = a.id), '[]'::jsonb);
end $$;
grant execute on function public.zx_my_purchases(bigint, text) to anon, authenticated;

-- ------------------------------------------------------------------ KVKK m.11: verilerimin özeti
create or replace function public.zx_my_data(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return jsonb_build_object(
    'generated_at', now(),
    'controller', 'Zenvex Online veri sorumlusu (Ayarlar > Yasal > Künye)',
    'account', jsonb_build_object('id', a.id, 'name', a.name, 'created_at', a.created_at, 'last_seen', a.last_seen,
      'google_linked', a.google_sub is not null, 'email', a.email, 'profile_photo', coalesce(a.photo, '') <> '',
      'clan_id', a.clan_id, 'vip_points', a.vip_points, 'terms_version', a.terms_version, 'terms_accepted_at', a.terms_at,
      'delete_requested_at', a.delete_requested_at, 'banned_until', a.banned_until, 'ban_reason', a.ban_reason),
    'characters', (select coalesce(jsonb_agg(to_jsonb(c) - 'data'), '[]'::jsonb) from public.characters c where c.account_id = a.id),
    'devices', (select coalesce(jsonb_agg(jsonb_build_object('device_hash', d.device, 'model', d.model, 'info', d.info,
      'first_seen', d.first_seen, 'last_seen', d.last_seen)), '[]'::jsonb) from public.devices d where d.account_id = a.id),
    'purchases', public.zx_my_purchases(p_id, p_key),
    'consents', (select coalesce(jsonb_agg(jsonb_build_object('kind', c.kind, 'version', c.version, 'granted', c.granted, 'at', c.at) order by c.at desc), '[]'::jsonb)
      from public.consents c where c.account_id = a.id),
    'traffic_last_50', (select coalesce(jsonb_agg(jsonb_build_object('kind', t.kind, 'ip', t.ip, 'at', t.at) order by t.at desc), '[]'::jsonb)
      from (select * from public.traffic_log t where t.account_id = a.id order by t.at desc limit 50) t),
    'counts', jsonb_build_object(
      'chat_messages', (select count(*) from public.chat where account_id = a.id),
      'direct_messages_sent', (select count(*) from public.dm where from_id = a.id),
      'direct_messages_received', (select count(*) from public.dm where to_id = a.id),
      'mail', (select count(*) from public.mail where account_id = a.id),
      'activity_log', (select count(*) from public.activity where account_id = a.id),
      'reports_sent', (select count(*) from public.reports where reporter = a.id),
      'support_tickets', (select count(*) from public.support_tickets where account_id = a.id),
      'traffic_log', (select count(*) from public.traffic_log where account_id = a.id)),
    'recipients', jsonb_build_array('Supabase Inc. (AB Frankfurt bölgesinde barındırma)', 'Google LLC / Google Ireland (Google ile giriş, Google Play ödeme)',
      'Gerçek zamanlı oyun sunucusu sağlayıcısı', 'Kanunen yetkili kamu kurumları'),
    'retention', 'Ayarlar > Yasal > KVKK Aydınlatma Metni, 7. bölüm');
end $$;
grant execute on function public.zx_my_data(bigint, text) to anon, authenticated;

-- silme talebinden vazgeçme (30 gün dolmadan)
create or replace function public.zx_delete_cancel(p_id bigint, p_key text)
returns boolean language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if a.delete_requested_at is null then return false; end if;
  update public.accounts set delete_requested_at = null where id = a.id;
  perform public._zx_log(a.id, 'delete', 'Hesap silme talebi iptal edildi');
  return true;
end $$;
grant execute on function public.zx_delete_cancel(bigint, text) to anon, authenticated;

-- ------------------------------------------------------------------ destek: KVKK başvurusu ve itiraz konuları
create or replace function public.zx_support_open(p_id bigint, p_key text, p_device text, p_topic text, p_body text, p_photos integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); d text := left(coalesce(trim(p_device), ''), 80);
        b text := left(coalesce(trim(p_body), ''), 2000); t bigint;
begin
  if d = '' then raise exception 'Cihaz bilgisi yok'; end if;
  if length(b) < 10 then raise exception 'Sorunu biraz daha ayrıntılı yaz (en az 10 harf)'; end if;
  if p_topic not in ('Hata', 'Ödeme', 'Hesap', 'KVKK', 'İtiraz', 'Öneri', 'Diğer') then raise exception 'Bir konu seç'; end if;
  if p_photos < 0 or p_photos > 10 then raise exception 'En çok 10 fotoğraf eklenebilir'; end if;
  if exists (select 1 from public.support_tickets where (device = d or account_id = a.id) and created_at > now() - interval '15 minutes') then
    raise exception 'Yeni bir destek talebi için 15 dakika bekle';
  end if;
  insert into public.support_tickets (account_id, device, topic, body, photos) values (a.id, d, p_topic, b, p_photos) returning id into t;
  if p_topic = 'KVKK' then
    insert into public.mail (account_id, sender, title, body, kind, favorite) values (a.id, 'Destek', 'KVKK Başvurun Alındı (#' || t || ')',
      'Kişisel verilerinle ilgili başvurun ' || to_char(now() at time zone 'Europe/Istanbul', 'DD.MM.YYYY HH24:MI') ||
      ' tarihinde alındı. KVKK m.13 uyarınca en geç 30 gün içinde ücretsiz yanıtlanacak.', 'system', true);
  end if;
  return jsonb_build_object('ticket', t);
end $$;
grant execute on function public.zx_support_open(bigint, text, text, text, text, integer) to anon, authenticated;

-- ------------------------------------------------------------------ hesap anonimleştirme
create or replace function public._zx_anonymize(p bigint) returns void
language plpgsql security definer set search_path = public, extensions as $$
declare t text;
begin
  -- klan sahibiyse klan en kıdemli üyeye geçer
  update public.clans c set owner = coalesce((select a2.id from public.accounts a2 where a2.clan_id = c.id and a2.id <> p
      and a2.deleted_at is null order by a2.clan_rank desc, a2.clan_joined_at nulls last limit 1), c.owner)
    where c.owner = p;
  -- hesaba bağlı tüm kişisel kayıtlar (satın alma ve destek kayıtları hariç: onlar anonim kalır)
  for t in select c.table_name from information_schema.columns c
           join information_schema.tables tb on tb.table_schema = c.table_schema and tb.table_name = c.table_name and tb.table_type = 'BASE TABLE'
           where c.table_schema = 'public' and c.column_name = 'account_id'
             and c.table_name not in ('purchases', 'support_tickets', 'traffic_log', 'consents') loop
    execute format('delete from public.%I where account_id = $1', t) using p;
  end loop;
  delete from public.dm where from_id = p or to_id = p;
  delete from public.friends where a = p or b = p;
  delete from public.trades where a = p or b = p;
  delete from public.reports where reporter = p or target = p;
  update public.state_bases set captured_by = null where captured_by = p;
  update public.support_tickets set body = '[anonimleştirildi]', reply = null, device = '' where account_id = p;
  update public.accounts set
    name = 'Silinen~' || id, key_hash = crypt(gen_random_uuid()::text, gen_salt('bf', 8)), google_sub = null, email = null,
    photo = '', avatar = 0, frame = '', title_kind = '', clan_id = null, clan_rank = 0, clan_muted_until = null, clan_joined_at = null,
    vip_until = null, ban_reason = '', deleted_at = coalesce(deleted_at, now()), deleted_reason = 'Anonimleştirildi (KVKK)',
    anonymized_at = now()
  where id = p;
end $$;
revoke all on function public._zx_anonymize(bigint) from public, anon, authenticated;

-- ------------------------------------------------------------------ saklama süreleri (günlük)
create or replace function public.zx_retention_purge() returns jsonb
language plpgsql security definer set search_path = public as $$
declare r jsonb := '{}'; n int := 0; acc bigint;
begin
  for acc in select id from public.accounts where anonymized_at is null and not is_admin
             and ((delete_requested_at is not null and delete_requested_at < now() - interval '30 days')
               or (deleted_at is not null and deleted_at < now() - interval '30 days')) loop
    perform public._zx_anonymize(acc);
    n := n + 1;
  end loop;
  r := r || jsonb_build_object('anonymized', n);
  delete from public.chat where created_at < now() - interval '180 days'; get diagnostics n = row_count; r := r || jsonb_build_object('chat', n);
  delete from public.dm where created_at < now() - interval '180 days'; get diagnostics n = row_count; r := r || jsonb_build_object('dm', n);
  delete from public.mail where not favorite and created_at < now() - interval '1 year'; get diagnostics n = row_count; r := r || jsonb_build_object('mail', n);
  delete from public.traffic_log where at < now() - interval '1 year 30 days'; get diagnostics n = row_count; r := r || jsonb_build_object('traffic', n);
  delete from public.activity where created_at < now() - interval '2 years'; get diagnostics n = row_count; r := r || jsonb_build_object('activity', n);
  delete from public.cheat_flags where created_at < now() - interval '2 years';
  delete from public.admin_actions where created_at < now() - interval '2 years' and done_at is not null;
  delete from public.reports where created_at < now() - interval '2 years';
  delete from public.clan_log where created_at < now() - interval '2 years';
  delete from public.devices where last_seen < now() - interval '2 years'; get diagnostics n = row_count; r := r || jsonb_build_object('devices', n);
  -- fotoğrafı kalmamış eski destek talepleri (fotoğraflar zx-support "purge" ile depodan silinir)
  delete from public.support_tickets where created_at < now() - interval '2 years' and coalesce(array_length(paths, 1), 0) = 0;
  -- onay kayıtları: yerine yenisi gelmiş ya da hesabı anonimleşmiş kayıtlar 3 yıl sonra
  delete from public.consents c where c.at < now() - interval '3 years' and (
    exists (select 1 from public.consents c2 where c2.account_id = c.account_id and c2.kind = c.kind and c2.at > c.at and c2.at < now() - interval '3 years')
    or exists (select 1 from public.accounts a where a.id = c.account_id and a.anonymized_at < now() - interval '3 years'));
  return r;
end $$;
revoke all on function public.zx_retention_purge() from public, anon, authenticated;

-- destek fotoğrafı silinecek talepler (zx-support "purge", servis anahtarıyla)
create or replace function public.zx_support_purge_list() returns jsonb
language sql security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('ticket', t.id, 'paths', to_jsonb(t.paths))), '[]'::jsonb)
  from (select s.id, s.paths from public.support_tickets s left join public.accounts a on a.id = s.account_id
        where coalesce(array_length(s.paths, 1), 0) > 0 and (s.created_at < now() - interval '2 years' or a.anonymized_at is not null)
        limit 200) t
$$;
revoke all on function public.zx_support_purge_list() from public, anon, authenticated;

create or replace function public.zx_support_purge_done(p_ticket bigint) returns void
language sql security definer set search_path = public as $$
  update public.support_tickets set paths = '{}' where id = p_ticket
$$;
revoke all on function public.zx_support_purge_done(bigint) from public, anon, authenticated;

-- günlük çalıştırma: pg_cron varsa veritabanında, yoksa .github/workflows/retention.yml
do $$
begin
  begin
    create extension if not exists pg_cron with schema pg_catalog;
    perform cron.unschedule(jobid) from cron.job where jobname = 'zx_retention';
    perform cron.schedule('zx_retention', '17 0 * * *', 'select public.zx_retention_purge()');
  exception when others then
    raise notice 'pg_cron kurulamadı (%); saklama temizliği GitHub Actions ile çalışır', sqlerrm;
  end;
end $$;
