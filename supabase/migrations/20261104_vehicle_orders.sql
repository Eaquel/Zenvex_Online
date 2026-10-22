-- Araç üretimi (Tamirci Viktor): oyuncu parçaları verir, araç belirli sürede hazırlanır ve postayla anahtar gelir.
-- Zamanlayıcı yok: hazır olan siparişler oyuncunun nabzında (15 sn) ve posta listesinde teslim edilir (_zx_deliver).
create table if not exists public.vehicle_orders (
  id bigserial primary key,
  account_id bigint not null references public.accounts(id) on update cascade on delete cascade,
  item text not null,
  name text not null,
  ready_at timestamptz not null,
  delivered boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists vehicle_orders_pending on public.vehicle_orders (account_id) where not delivered;
alter table public.vehicle_orders enable row level security;

-- Hazır siparişleri postaya bırakır (anahtar eşyası ekli)
create or replace function public._zx_deliver(p bigint) returns void
language plpgsql security definer set search_path = public as $$
declare o record;
begin
  for o in update public.vehicle_orders set delivered = true
           where account_id = p and not delivered and ready_at <= now() returning * loop
    insert into public.mail (account_id, sender, title, body, items, kind)
    values (p, 'Tamirci Viktor', o.name || ' Hazır!',
            'Aracın hazır. Anahtarı çantana al, kullanınca aracın yanına gelir. Bozulursa bana getir, onarırım.',
            jsonb_build_array(jsonb_build_object('id', o.item, 'n', 1)), 'reward');
  end loop;
end $$;
revoke all on function public._zx_deliver(bigint) from public, anon, authenticated;

-- Sipariş: araç türü (0 motosiklet 10 dk, 1 araba 30 dk). Parçalar istemcide düşülür; aynı anda en çok 2 bekleyen sipariş.
create or replace function public.zx_vehicle_order(p_id bigint, p_key text, p_sid uuid, p_kind int)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); it text; nm text; mins int; at timestamptz;
begin
  perform public._zx_maint_check(a);
  perform public._zx_session(a, p_sid);
  if p_kind = 0 then it := 'veh_moto'; nm := 'Motosiklet'; mins := 10;
  elsif p_kind = 1 then it := 'veh_car'; nm := 'Araba'; mins := 30;
  else raise exception 'Araç yok'; end if;
  if (select count(*) from public.vehicle_orders where account_id = a.id and not delivered) >= 2 then
    raise exception 'Viktor şu an 2 araç üretiyor, biri bitince gel';
  end if;
  at := now() + make_interval(mins => mins);
  insert into public.vehicle_orders (account_id, item, name, ready_at) values (a.id, it, nm, at);
  return jsonb_build_object('ok', true, 'ready', (extract(epoch from at) * 1000)::bigint);
end $$;

-- Bekleyen siparişler (Viktor ekranı)
create or replace function public.zx_vehicle_orders(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  perform public._zx_deliver(a.id);
  return coalesce((select jsonb_agg(jsonb_build_object('name', name, 'item', item, 'ready', (extract(epoch from ready_at) * 1000)::bigint) order by ready_at)
                   from public.vehicle_orders where account_id = a.id and not delivered), '[]'::jsonb);
end $$;

-- Posta listesi ve nabız: önce hazır siparişleri teslim et
create or replace function public.zx_mail_list(p_id bigint, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); r jsonb;
begin
  perform public._zx_deliver(a.id);
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'from', sender, 'title', title, 'body', body, 'gold', gold, 'xp', xp,
    'gems', gems, 'items', items, 'read', read, 'claimed', claimed, 'favorite', favorite, 'kind', kind,
    'ts', (extract(epoch from created_at) * 1000)::bigint,
    'expires', case when favorite then null else (extract(epoch from created_at + interval '30 days') * 1000)::bigint end) order by id desc), '[]'::jsonb)
  into r from (select * from public.mail where account_id = a.id and not deleted
               and (favorite or created_at > now() - interval '30 days') order by id desc limit 60) m;
  update public.mail set read = true where account_id = a.id and not read and not deleted;
  return r;
end $$;

create or replace function public.zx_heartbeat(p_id bigint, p_key text, p_sid uuid, p_game_time double precision)
returns jsonb language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; wall double precision; dg double precision;
begin
  perform public._zx_maint_check(a);
  select * into s from public.sessions where account_id = a.id;
  if s.account_id is null or s.sid <> p_sid then
    raise exception 'OTURUM|Hesabına başka bir cihazdan girildi' using errcode = '28000';
  end if;
  wall := extract(epoch from now() - coalesce(s.speed_at, s.started_at));
  dg := p_game_time - s.game_time;
  if s.speed_at is not null and s.game_time > 0 and dg > wall * 1.25 + 8 and a.id <> 0 then
    perform public._zx_flag(a.id, 'speed', 'Oyun saati ' || round(dg::numeric, 1) || ' sn, gerçek ' || round(wall::numeric, 1) || ' sn');
  end if;
  update public.sessions set last_beat = now(), speed_at = now(), game_time = greatest(p_game_time, 0) where account_id = a.id;
  perform public._zx_deliver(a.id);
  return jsonb_build_object('ok', true, 'time', (extract(epoch from now()) * 1000)::bigint,
    'actions', (select count(*) from public.admin_actions where account_id = a.id and done_at is null),
    'unread', (select count(*) from public.mail m where m.account_id = a.id and not m.read and not m.deleted
               and (m.favorite or m.created_at > now() - interval '30 days')),
    'trade', (select t.id from public.trades t where ((t.a = a.id and not t.a_claimed) or (t.b = a.id and not t.b_claimed))
               and t.state in ('done', 'cancel') order by t.id desc limit 1));
end $$;
