-- Yönetici: uygunsuz profil fotoğrafını kaldırma

create or replace function public.zx_admin_action(p_id bigint, p_key text, p_target bigint, p_action jsonb) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts; k text := p_action ->> 't'; ch record; d jsonb;
        slots jsonb; e jsonb; item int := (p_action ->> 'item')::int; lft int; c int;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if k not in ('remove_item', 'clear_bag', 'set_gold', 'set_gems', 'kick', 'msg', 'clear_photo') then raise exception 'Geçersiz işlem'; end if;
  insert into public.admin_actions (account_id, action, by_name) values (t.id, p_action, a.name);
  if k = 'clear_photo' then
    update public.accounts set photo = '' where id = t.id;
    perform public._zx_log(t.id, 'admin', a.name || ' profil fotoğrafını kaldırdı');
    return 'Profil fotoğrafı kaldırıldı';
  end if;
  for ch in select * from public.characters where account_id = t.id loop
    begin
      d := ch.data::jsonb;
      if k = 'set_gold' then
        d := jsonb_set(d, '{gold}', to_jsonb(greatest((p_action ->> 'v')::bigint, 0)));
      elsif k = 'set_gems' then
        d := jsonb_set(d, '{gems}', to_jsonb(greatest((p_action ->> 'v')::bigint, 0)));
      elsif k = 'clear_bag' then
        slots := (select jsonb_agg('null'::jsonb) from jsonb_array_elements(d #> '{inv,slots}'));
        d := jsonb_set(d, '{inv,slots}', coalesce(slots, '[]'::jsonb));
      elsif k = 'remove_item' then
        -- adet verilmezse o eşyanın tamamı; verilirse yığınlardan o kadar düşülür
        lft := coalesce((p_action ->> 'n')::int, 2147483647);
        slots := '[]'::jsonb;
        for e in select x from jsonb_array_elements(d #> '{inv,slots}') x loop
          if lft > 0 and jsonb_typeof(e) = 'object' and (e ->> 'item')::int = item then
            c := (e ->> 'count')::int;
            if c <= lft then lft := lft - c; slots := slots || jsonb_build_array(null::jsonb);
            else slots := slots || jsonb_build_array(jsonb_set(e, '{count}', to_jsonb(c - lft))); lft := 0; end if;
          else
            slots := slots || jsonb_build_array(e);
          end if;
        end loop;
        d := jsonb_set(d, '{inv,slots}', slots);
      end if;
      if k in ('set_gold', 'set_gems', 'clear_bag', 'remove_item') then
        update public.characters set data = d::text where account_id = ch.account_id and server = ch.server;
      end if;
    exception when others then null;  -- bozuk kayıt: yalnız kuyruk uygulanır
    end;
  end loop;
  perform public._zx_log(t.id, 'admin', a.name || ' yönetici işlemi: ' || p_action::text);
  return case k when 'remove_item' then 'Eşya silindi' when 'clear_bag' then 'Çanta boşaltıldı' when 'set_gold' then 'Altın ayarlandı'
                when 'set_gems' then 'Elmas ayarlandı' when 'kick' then 'Oyuncu oyundan atılacak' else 'Mesaj gönderildi' end;
end $$;

