-- Kayıt doğrulama düzeltmesi: elmas/altın harcamaları (sandık, bilet) grants'a eksi yazılır. Bir kayıt geri
-- alındığında istemci harcamayı kaybedip eski miktarı gönderiyordu; eksi kayıt doğrulama penceresinde kaldığı için
-- her kayıt ("elmas +0") hile sayılıp sonsuza dek reddediliyordu. Artık yalnız gerçek artış (pozitif ödüllerin
-- üstü) reddedilir; uygulanmamış harcama sunucuda düşülür ve düzeltilmiş kayıt istemciye geri gönderilir.
create or replace function public.zx_save3(p_id bigint, p_key text, p_sid uuid, p_seq bigint, p_sig text,
  p_level int, p_data text, p_kills int, p_xp bigint) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
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
    select coalesce(sum(gems), 0), coalesce(sum(gold), 0), coalesce(sum(levels), 0) into g_gems, g_gold, g_lv
      from public.grants where account_id = a.id and created_at > old.updated_at - interval '5 seconds';
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
