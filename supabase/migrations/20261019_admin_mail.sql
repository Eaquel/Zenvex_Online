-- Yönetici postası: is_admin bayrağı olan hesap, hile panelinden oyunculara (ya da '*' ile herkese) altın / XP / eşya gönderir.
-- (Uygulandı: zenvex-online, 19.10.2026; yönetici hesabı 1000001)
alter table public.accounts add column if not exists is_admin boolean not null default false;
alter table public.mail add column if not exists xp int not null default 0;

create or replace function public._zx_profile(a public.accounts) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', a.id, 'name', a.name, 'admin', a.is_admin,
    'clan', (select jsonb_build_object('id', c.id, 'name', c.name, 'color', c.color, 'owner', c.owner = a.id)
             from public.clans c where c.id = a.clan_id),
    'chars', coalesce((select jsonb_object_agg(server, level) from public.characters where account_id = a.id), '{}'::jsonb),
    'unread', (select count(*) from public.mail where account_id = a.id and not read),
    'key_changed_at', a.key_changed_at)
$$;
revoke all on function public._zx_profile(public.accounts) from public, anon, authenticated;

create or replace function public.zx_admin_mail(p_id bigint, p_key text, p_to text, p_title text, p_body text,
  p_gold int, p_xp int, p_items jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); n int := 0;
begin
  if not a.is_admin then raise exception 'Bu işlem için yönetici yetkisi gerekli'; end if;
  if trim(coalesce(p_to, '')) = '*' then
    insert into public.mail (account_id, sender, title, body, gold, xp, items)
    select id, 'Yönetici', left(p_title, 40), left(p_body, 500), greatest(p_gold, 0), greatest(p_xp, 0), coalesce(p_items, '[]') from public.accounts;
    get diagnostics n = row_count;
  else
    insert into public.mail (account_id, sender, title, body, gold, xp, items)
    select id, 'Yönetici', left(p_title, 40), left(p_body, 500), greatest(p_gold, 0), greatest(p_xp, 0), coalesce(p_items, '[]')
    from public.accounts where lower(name) = lower(trim(p_to));
    get diagnostics n = row_count;
    if n = 0 then raise exception 'Oyuncu bulunamadı'; end if;
  end if;
  return jsonb_build_object('sent', n);
end $$;

create or replace function public.zx_mail_claim(p_id bigint, p_key text, p_mail bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); m public.mail;
begin
  update public.mail set claimed = true, read = true
  where id = p_mail and account_id = a.id and not claimed returning * into m;
  if m.id is null then raise exception 'Bu posta zaten alınmış'; end if;
  return jsonb_build_object('gold', m.gold, 'xp', m.xp, 'items', m.items);
end $$;

create or replace function public.zx_mail_list(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); r jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'from', sender, 'title', title, 'body', body, 'gold', gold, 'xp', xp,
    'items', items, 'read', read, 'claimed', claimed, 'ts', (extract(epoch from created_at) * 1000)::bigint) order by id desc), '[]'::jsonb)
  into r from (select * from public.mail where account_id = a.id order by id desc limit 50) m;
  update public.mail set read = true where account_id = a.id and not read;
  return r;
end $$;
