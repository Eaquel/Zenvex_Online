-- Sohbet geçmişi: gönderenin profil resmi ve çerçevesi (sohbette çerçeveler animasyonlu görünür)
create or replace function public.zx_chat_recent(p_server text) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', s.id, 'u', s.account_id, 'n', s.name, 'g', s.clan, 'm', s.text,
    'av', a.avatar, 'fr', a.frame,
    'ts', (extract(epoch from s.created_at) * 1000)::bigint) order by s.id), '[]'::jsonb)
  from (select * from public.chat where server = p_server order by id desc limit 30) s
  left join public.accounts a on a.id = s.account_id
$$;
