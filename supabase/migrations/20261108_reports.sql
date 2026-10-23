-- Oyuncu şikâyeti (oyun içinde oyuncuya dokununca açılan menüden): yöneticinin işaret listesine düşer (_zx_flag 'report').
-- Günde en çok 10 şikâyet, aynı oyuncuya saatte bir.
create table if not exists public.reports (
  id bigserial primary key,
  reporter bigint not null references public.accounts(id) on update cascade on delete cascade,
  target bigint not null references public.accounts(id) on update cascade on delete cascade,
  reason text not null,
  created_at timestamptz not null default now()
);
create index if not exists reports_reporter_day on public.reports (reporter, created_at);
alter table public.reports enable row level security;

create or replace function public.zx_report(p_id bigint, p_key text, p_target bigint, p_reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); t public.accounts; r text := left(coalesce(trim(p_reason), ''), 120);
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
  insert into public.reports (reporter, target, reason) values (a.id, t.id, r);
  perform public._zx_flag(t.id, 'report', a.name || ' şikâyet etti: ' || r);
  return jsonb_build_object('ok', true);
end $$;
grant execute on function public.zx_report(bigint, text, bigint, text) to anon, authenticated;
