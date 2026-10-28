-- V17 takas güvenliği: "Hazırım" diyen taraf, o an gördüğü karşı teklifi (p_expect) bildirir. İkinci taraf hazır olurken
-- kendi teklifi birincinin gördüğünden farklıysa takas bitmez (son anda teklifi değiştirme dolandırıcılığı engellenir);
-- ilk taraf iptal edip emanetini geri alır. Eşya kayıtlarındaki "dur" (dayanıklılık) olduğu gibi taşınır.
alter table public.trades add column if not exists a_expect jsonb;
alter table public.trades add column if not exists b_expect jsonb;

drop function if exists public.zx_trade_ready(bigint, text, uuid, bigint, jsonb, bigint, text, int, text, int, bigint);
create or replace function public.zx_trade_ready(p_id bigint, p_key text, p_sid uuid, p_trade bigint, p_items jsonb,
  p_seq bigint, p_sig text, p_level int, p_data text, p_kills int, p_xp bigint, p_expect jsonb default null) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; t public.trades; old public.characters;
        od jsonb; nd jsonb; it jsonb; idx int; n bigint; side text; have bigint; now_have bigint; seen jsonb;
begin
  s := public._zx_session(a, p_sid);
  select * into t from public.trades where id = p_trade for update;
  if t.id is null or t.state <> 'open' then raise exception 'Takas kapandı'; end if;
  if t.a = a.id then side := 'a'; elsif t.b = a.id then side := 'b'; else raise exception 'Bu takas senin değil'; end if;
  if (side = 'a' and t.a_ready) or (side = 'b' and t.b_ready) then raise exception 'Teklifin zaten kilitli'; end if;
  if jsonb_typeof(coalesce(p_items, '[]')) <> 'array' or jsonb_array_length(coalesce(p_items, '[]')) > 12 then raise exception 'Geçersiz teklif'; end if;
  -- karşı taraf önce hazır olduysa: onun gördüğü teklif benim teklifim olmalı; ben de onun kilitli teklifini görmüş olmalıyım
  seen := case when side = 'a' then t.b_expect else t.a_expect end;
  if (side = 'a' and t.b_ready) or (side = 'b' and t.a_ready) then
    if seen is not null and seen <> coalesce(p_items, '[]') then
      raise exception 'Teklifin karşı tarafın onayladığından farklı: takası iptal edip yeniden başlatın';
    end if;
    if p_expect is not null and p_expect <> (case when side = 'a' then t.b_items else t.a_items end) then
      raise exception 'Karşı tarafın teklifi değişti: takası iptal edip yeniden başlatın';
    end if;
  end if;
  select * into old from public.characters where account_id = a.id and server = s.server;
  if old.account_id is null then raise exception 'Önce oyunu kaydet'; end if;
  od := old.data::jsonb;
  begin nd := p_data::jsonb; exception when others then raise exception 'Bozuk kayıt'; end;
  for it in select * from jsonb_array_elements(coalesce(p_items, '[]')) loop
    idx := (it ->> 'item')::int; n := (it ->> 'n')::bigint;
    if idx is null or n is null or n <= 0 or n > 9999999 then raise exception 'Geçersiz eşya'; end if;
    -- -1 = altın
    if idx = -1 then
      have := coalesce((od ->> 'gold')::bigint, 0); now_have := coalesce((nd ->> 'gold')::bigint, 0);
    else
      have := public._zx_item_total(od, idx); now_have := public._zx_item_total(nd, idx);
    end if;
    if have < n then
      perform public._zx_flag(a.id, 'trade', 'Olmayan eşya teklif edildi: #' || idx || ' x' || n);
      raise exception 'Teklif ettiğin eşya çantanda yok';
    end if;
    if now_have <> have - n then raise exception 'Eşya emanete alınamadı, tekrar dene'; end if;
  end loop;
  perform public.zx_save3(p_id, p_key, p_sid, p_seq, p_sig, p_level, p_data, p_kills, p_xp);
  if side = 'a' then update public.trades set a_items = coalesce(p_items, '[]'), a_ready = true, a_expect = p_expect, updated_at = now() where id = t.id returning * into t;
  else update public.trades set b_items = coalesce(p_items, '[]'), b_ready = true, b_expect = p_expect, updated_at = now() where id = t.id returning * into t; end if;
  if t.a_ready and t.b_ready then
    update public.trades set state = 'done', updated_at = now() where id = t.id returning * into t;
    perform public._zx_log(t.a, 'trade', 'Takas #' || t.id || ' tamamlandı: verdi ' || t.a_items::text || ', aldı ' || t.b_items::text);
    perform public._zx_log(t.b, 'trade', 'Takas #' || t.id || ' tamamlandı: verdi ' || t.b_items::text || ', aldı ' || t.a_items::text);
  end if;
  return jsonb_build_object('state', t.state, 'a_ready', t.a_ready, 'b_ready', t.b_ready);
end $$;
grant execute on function public.zx_trade_ready(bigint, text, uuid, bigint, jsonb, bigint, text, int, text, int, bigint, jsonb) to anon, authenticated;
