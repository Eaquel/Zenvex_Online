-- V20 düzeltmeleri.
--  1. zx_photos: hesap kimlikleriyle profil fotoğrafları (sohbet, arkadaş, klan ve sıralama listeleri yalnız resim
--     numarası taşıdığı için fotoğraf değişince görünmüyordu). Fotoğraflar zaten herkese açık profilde görünür.

create or replace function public.zx_photos(p_ids bigint[]) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_object_agg(a.id::text, a.photo), '{}'::jsonb)
  from public.accounts a
  where a.id = any (p_ids[1:50]) and a.deleted_at is null and coalesce(a.photo, '') <> ''
$$;
grant execute on function public.zx_photos(bigint[]) to anon, authenticated;

--  2. Yönetici panelinden oyuncu seviyesi (set_level) ve seçili karakterin seviyesi (set_char_level). Oyuncunun istemcisi
--     kuyruktan uygular; seviye artışı kayıt doğrulamasına (zx_save3) yönetici izni olarak yazılır (grants.levels).
create or replace function public.zx_admin_action(p_id bigint, p_key text, p_target bigint, p_action jsonb) returns text
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key); t public.accounts; k text := p_action ->> 't'; ch record; d jsonb;
        slots jsonb; e jsonb; item int := (p_action ->> 'item')::int; lft int; c int; lv int;
begin
  select * into t from public.accounts where id = p_target;
  if t.id is null then raise exception 'Oyuncu bulunamadı'; end if;
  if k not in ('remove_item', 'clear_bag', 'set_gold', 'set_gems', 'kick', 'msg', 'clear_photo', 'set_wings', 'set_level', 'set_char_level') then
    raise exception 'Geçersiz işlem';
  end if;
  insert into public.admin_actions (account_id, action, by_name) values (t.id, p_action, a.name);
  if k = 'clear_photo' then
    update public.accounts set photo = '' where id = t.id;
    perform public._zx_log(t.id, 'admin', a.name || ' profil fotoğrafını kaldırdı');
    return 'Profil fotoğrafı kaldırıldı';
  end if;
  if k = 'set_wings' then
    perform public._zx_log(t.id, 'admin', a.name || ' kanat işlemi: ' || p_action::text);
    return 'Kanat işlemi oyuncuya iletildi';
  end if;
  if k = 'set_level' then
    lv := least(greatest(coalesce((p_action ->> 'v')::int, 1), 1), 50);
    -- kayıt doğrulaması bu artışı hile saymasın
    insert into public.grants (account_id, levels, note)
    select t.id, greatest(lv - coalesce(max(level), 1), 0), 'admin_level' from public.characters where account_id = t.id;
    perform public._zx_log(t.id, 'admin', a.name || ' seviyeyi ' || lv || ' yaptı');
    return 'Seviye ' || lv || ' oyuncuya iletildi (girişte uygulanır)';
  end if;
  if k = 'set_char_level' then
    perform public._zx_log(t.id, 'admin', a.name || ' karakter seviyesini ' || coalesce(p_action ->> 'v', '?') || ' yaptı');
    return 'Karakter seviyesi oyuncuya iletildi (girişte uygulanır)';
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
    exception when others then null;
    end;
  end loop;
  perform public._zx_log(t.id, 'admin', a.name || ' yönetici işlemi: ' || p_action::text);
  return case k when 'remove_item' then 'Eşya silindi' when 'clear_bag' then 'Çanta boşaltıldı' when 'set_gold' then 'Altın ayarlandı'
                when 'set_gems' then 'Elmas ayarlandı' when 'kick' then 'Oyuncu oyundan atılacak' else 'Mesaj gönderildi' end;
end $$;

--  3. Kayıt doğrulaması: yönetici seviye izni oyuncu o arada başka kayıt yapmış olsa da 7 gün geçerli (oyuncu çevrimdışıyken
--     verilen seviye ilk girişte uygulanır; eskiden yalnız son kayıttan sonraki izinler sayılıyordu)
create or replace function public.zx_save3(p_id bigint, p_key text, p_sid uuid, p_seq bigint, p_sig text, p_level integer, p_data text, p_kills integer, p_xp bigint)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; old public.characters; d jsonb; od jsonb;
        mins double precision; g_gems bigint; g_gold bigint; g_lv bigint; bad text := ''; r jsonb; pt bigint;
        dg bigint; dgo bigint; og bigint; ogo bigint; fixed boolean := false;
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  if p_seq <= s.seq then raise exception 'Eski kayıt'; end if;
  if encode(hmac(p_seq::text || ':' || p_data, s.secret, 'sha256'), 'hex') <> lower(coalesce(p_sig, '')) then
    perform public._zx_flag(a.id, 'sig', 'Kayıt imzası tutmadı');
    raise exception 'HILE|Kayıt imzası geçersiz';
  end if;
  begin d := p_data::jsonb; exception when others then raise exception 'Bozuk kayıt'; end;
  select * into old from public.characters where account_id = a.id and server = s.server;
  if old.account_id is not null and old.data ~ '^\s*\{' and a.id <> 0 then
    od := old.data::jsonb;
    mins := greatest(extract(epoch from now() - greatest(old.updated_at, s.started_at - interval '10 minutes')) / 60.0, 0.25);
    select coalesce(sum(gems), 0), coalesce(sum(gold), 0) into g_gems, g_gold
      from public.grants where account_id = a.id and created_at > old.updated_at - interval '5 seconds';
    select coalesce(sum(levels), 0) into g_lv from public.grants where account_id = a.id and (created_at > old.updated_at - interval '5 seconds'
      or (note = 'admin_level' and created_at > now() - interval '7 days'));
    og := coalesce((od ->> 'gems')::bigint, 0); ogo := coalesce((od ->> 'gold')::bigint, 0);
    dg := coalesce((d ->> 'gems')::bigint, 0) - og;
    dgo := coalesce((d ->> 'gold')::bigint, 0) - ogo;
    if dg > greatest(g_gems, 0) + 3 + mins * 2 then
      bad := bad || 'elmas +' || dg || '; ';
    elsif dg > g_gems + 3 + mins * 2 then
      d := jsonb_set(d, '{gems}', to_jsonb(greatest(og + g_gems, 0))); fixed := true;
    end if;
    if dgo > greatest(g_gold, 0) + 3000 + mins * 4000 then
      bad := bad || 'altın +' || dgo || '; ';
    elsif dgo > g_gold + 3000 + mins * 4000 then
      d := jsonb_set(d, '{gold}', to_jsonb(greatest(ogo + g_gold, 0))); fixed := true;
    end if;
    if p_level - old.level > g_lv + 2 + floor(mins) then
      bad := bad || 'seviye ' || old.level || '→' || p_level || '; ';
    end if;
    if bad <> '' then
      perform public._zx_flag(a.id, 'save', bad || round(mins::numeric, 1) || ' dk');
      update public.sessions set seq = p_seq where account_id = a.id;
      return jsonb_build_object('ok', false, 'rollback', old.data, 'msg', 'Kayıt sunucuda doğrulanamadı, son geçerli kayda dönüldü');
    end if;
  end if;
  pt := greatest(coalesce((d ->> 'play_time')::double precision, 0)::bigint, 0);
  update public.sessions set seq = p_seq, last_save = now(), last_beat = now() where account_id = a.id;
  r := public.zx_save2(p_id, p_key, s.server, p_level, case when fixed then d::text else p_data end, p_kills, p_xp);
  update public.characters set play_seconds = greatest(play_seconds, pt) where account_id = a.id and server = s.server;
  perform public._zx_char_counters(a.id, s.server, d);
  if fixed then
    return jsonb_build_object('ok', false, 'rollback', d::text, 'msg', 'Harcaman Sunucuyla Eşitlendi');
  end if;
  return r;
end $$;

--  4. Yönetici destek listesi: bildirilen fotoğraf sayısı da (yüklenemeyenler görünsün)
create or replace function public.zx_admin_support(p_id bigint, p_key text, p_open boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_admin(p_id, p_key);
begin
  return coalesce((select jsonb_agg(jsonb_build_object('id', t.id, 'topic', t.topic, 'body', t.body, 'paths', to_jsonb(t.paths), 'status', t.status,
      'photos', t.photos, 'reply', t.reply, 'device', t.device, 'at', (extract(epoch from t.created_at) * 1000)::bigint, 'from', public._zx_card(t.account_id))
      order by t.created_at desc)
    from (select * from public.support_tickets where (status = 'open') = p_open order by created_at desc limit 60) t), '[]');
end $$;
grant execute on function public.zx_admin_support(bigint, text, boolean) to anon, authenticated;
