-- V15 (R3): klan deposundan tek gönderimde bir eşyadan en çok 50 adet (altın sınırsız; depodaki kadar)
create or replace function public.zx_clan_send(p_id bigint, p_key text, p_member bigint, p_item text, p_n integer, p_note text)
returns text language plpgsql security definer set search_path to 'public' as $function$
declare a public.accounts := public._zx_clan_me(p_id, p_key, 2); m public.accounts; c public.clans;
begin
  select * into c from public.clans where id = a.clan_id;
  if a.clan_rank < 3 and not c.officer_send then raise exception 'Lider depodan gönderme izni vermedi'; end if;
  select * into m from public.accounts where id = p_member and clan_id = a.clan_id;
  if m.id is null then raise exception 'Üye bulunamadı'; end if;
  if p_n <= 0 then raise exception 'Geçersiz miktar'; end if;
  if p_item <> 'coin' and p_n > 50 then raise exception 'Bir eşyadan tek seferde en çok 50 adet gönderilir'; end if;
  if p_item = 'coin' then
    update public.clans set gold = gold - p_n where id = c.id and gold >= p_n;
    if not found then raise exception 'Depoda yeterli altın yok'; end if;
    insert into public.mail (account_id, sender, title, body, gold, kind)
    values (m.id, c.name, 'Klan Deposundan', left(coalesce(nullif(trim(p_note), ''), a.name || ' gönderdi'), 200), p_n, 'clan');
  else
    update public.clan_depot set n = n - p_n where clan_id = c.id and item = p_item and n >= p_n;
    if not found then raise exception 'Depoda yeterli eşya yok'; end if;
    insert into public.mail (account_id, sender, title, body, items, kind)
    values (m.id, c.name, 'Klan Deposundan', left(coalesce(nullif(trim(p_note), ''), a.name || ' gönderdi'), 200),
            jsonb_build_array(jsonb_build_object('id', p_item, 'n', p_n)), 'clan');
  end if;
  insert into public.clan_log (clan_id, text) values (c.id, a.name || ', ' || m.name || ' adlı üyeye depodan ' || p_n || '× ' || p_item || ' gönderdi');
  return 'Gönderildi';
end $function$;
