-- Google ile giriş: hesap Google kimliğine (sub) bağlanır.
-- Bu fonksiyonları yalnızca zx-google edge function'ı (service_role) çağırır; Google ID jetonu orada doğrulanır.
alter table public.accounts add column if not exists google_sub text unique;
alter table public.accounts add column if not exists email text;

-- Google hesabıyla giriş: yoksa yeni Zenvex hesabı açılır (isim Google adından türetilir).
-- Her girişte yeni anahtar üretilir ve istemciye döner (cihaz kimlik + anahtarı saklar).
create or replace function public.zx_google_login(p_sub text, p_email text, p_name text) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts; k text := public._zx_key(); base text; n text; tries int := 0; fresh boolean := false;
begin
  if p_sub is null or char_length(p_sub) < 5 then raise exception 'Geçersiz Google hesabı'; end if;
  select * into a from public.accounts where google_sub = p_sub;
  if a.id is null then
    base := regexp_replace(coalesce(nullif(split_part(coalesce(p_name, ''), ' ', 1), ''), split_part(coalesce(p_email, ''), '@', 1), ''),
                           '[^A-Za-z0-9_ÇçĞğİıÖöŞşÜü]', '', 'g');
    base := left(base, 11);
    if char_length(base) < 3 then base := 'Oyuncu'; end if;
    n := base;
    while exists (select 1 from public.accounts where lower(name) = lower(n)) loop
      tries := tries + 1;
      n := base || (1000 + floor(random() * 9000))::int::text;
      if tries > 30 then raise exception 'İsim üretilemedi'; end if;
    end loop;
    insert into public.accounts (key_hash, name, google_sub, email)
    values (crypt(k, gen_salt('bf', 8)), n, p_sub, p_email) returning * into a;
    insert into public.mail (account_id, title, body, gold, items)
    values (a.id, 'Hoş geldin!', 'Zenvex Online''a hoş geldin. Başlangıç hediyen ekte.', 250,
            '[{"id":"bandage","n":5},{"id":"food","n":3}]');
    fresh := true;
  else
    update public.accounts set key_hash = crypt(k, gen_salt('bf', 8)), key_changed_at = now(), last_seen = now(),
           email = coalesce(p_email, email)
    where id = a.id returning * into a;
  end if;
  return jsonb_build_object('key', k, 'new', fresh) || public._zx_profile(a);
end $$;

-- Var olan (misafir) Zenvex hesabını Google'a bağlar.
create or replace function public.zx_google_link(p_id bigint, p_key text, p_sub text, p_email text) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  if exists (select 1 from public.accounts where google_sub = p_sub and id <> a.id) then
    raise exception 'Bu Google hesabı başka bir oyuncuya bağlı';
  end if;
  update public.accounts set google_sub = p_sub, email = p_email where id = a.id returning * into a;
  return jsonb_build_object('linked', true) || public._zx_profile(a);
end $$;

revoke all on function public.zx_google_login(text, text, text) from public, anon, authenticated;
revoke all on function public.zx_google_link(bigint, text, text, text) from public, anon, authenticated;
grant execute on function public.zx_google_login(text, text, text) to service_role;
grant execute on function public.zx_google_link(bigint, text, text, text) to service_role;
