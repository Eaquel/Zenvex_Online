-- V15: sıralamada oyuncunun kendi yeri (ilk 10'da olmasa da "Mevcut Sıralaman #x")
create or replace function public.zx_board_me(p_id bigint, p_key text, p_kind text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); mv bigint; mt timestamptz; rk bigint;
begin
  if not (p_kind = any(public._zx_board_kinds())) then return jsonb_build_object('rank', 0, 'v', 0); end if;
  select s.v, s.t into mv, mt from public._zx_cat(p_kind) s where s.account_id = a.id;
  if mv is null or mv <= 0 then return jsonb_build_object('rank', 0, 'v', 0); end if;
  select count(*) + 1 into rk from public._zx_cat(p_kind) s where s.v > mv or (s.v = mv and s.t < mt);
  return jsonb_build_object('rank', rk, 'v', mv);
end $$;
grant execute on function public.zx_board_me(bigint, text, text) to anon, authenticated;
