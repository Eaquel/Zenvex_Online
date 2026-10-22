-- cihaz bildirimleri: arka plandaki işçi 15 dakikada bir sorar (oturum gerekmez)
create or replace function public.zx_notify(p_id bigint, p_key text, p_since bigint) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); since timestamptz := to_timestamp(greatest(p_since, 0) / 1000.0);
begin
  return jsonb_build_object(
    'now', (extract(epoch from now()) * 1000)::bigint,
    'mails', coalesce((select jsonb_agg(jsonb_build_object('id', m.id, 'title', m.title, 'sender', m.sender, 'kind', m.kind,
                 'gems', m.gems, 'gold', m.gold, 'items', coalesce(jsonb_array_length(m.items), 0)) order by m.id desc)
               from (select * from public.mail where account_id = a.id and not deleted and created_at > since order by id desc limit 5) m), '[]'::jsonb),
    'dms', coalesce((select jsonb_agg(jsonb_build_object('from', x.name, 'text', left(x.text, 80), 'n', x.n)) from (
               select f.name, (array_agg(d.text order by d.id desc))[1] as text, count(*) as n
               from public.dm d join public.accounts f on f.id = d.from_id
               where d.to_id = a.id and not d.read and d.created_at > since group by f.name limit 5) x), '[]'::jsonb),
    'event', (select jsonb_build_object('title', e.title, 'kind', e.kind, 'ends', (extract(epoch from e.ends_at) * 1000)::bigint)
              from public.events e where e.active and e.starts_at > since and e.starts_at <= now() order by e.starts_at desc limit 1),
    'friends', (select count(*) from public.friends f where f.b = a.id and not f.accepted and not f.removed and f.created_at > since),
    'invites', (select count(*) from public.clan_invites i where i.account_id = a.id and i.created_at > since));
end $$;
grant execute on function public.zx_notify(bigint, text, bigint) to anon, authenticated;
