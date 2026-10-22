-- Elmas Sandığı: zar sunucuda atılır; kaplama sahipliği sunucuda tutulur; 50 açılışta efsanevi garanti.
-- Ödül kodları data.rs SKINS / eşya kimlikleriyle aynı.

create table if not exists public.skins_owned (
  account_id bigint not null references public.accounts(id) on delete cascade,
  skin text not null,
  created_at timestamptz not null default now(),
  primary key (account_id, skin)
);
alter table public.skins_owned enable row level security;

create table if not exists public.crate_pity (
  account_id bigint primary key references public.accounts(id) on delete cascade,
  opens int not null default 0,
  since_legend int not null default 0
);
alter table public.crate_pity enable row level security;

create or replace function public.zx_crate_open(p_id bigint, p_key text, p_sid uuid, p_n int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; d jsonb;
        cost int := case when p_n = 10 then 1080 else 120 end; n int := case when p_n = 10 then 10 else 1 end;
        legend text[] := array['sk_night', 'sk_blood', 'sk_frost', 'sk_gold', 'vs_gold', 'vs_flame'];
        epic text[] := array['sk_toxic', 'sk_shadow', 'vs_camo', 'vs_neon'];
        pity int; r int; got text; out text[] := '{}'; gold bigint := 0; i int; pick text[]; legend_hit boolean;
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  select data::jsonb into d from public.characters where account_id = a.id and server = s.server;
  if coalesce((d ->> 'gems')::int, 0) < cost then raise exception 'Yetersiz elmas (% gerekli)', cost; end if;
  insert into public.crate_pity (account_id) values (a.id) on conflict do nothing;
  select since_legend into pity from public.crate_pity where account_id = a.id for update;
  for i in 1..n loop
    pity := pity + 1;
    r := floor(random() * 10000)::int;
    legend_hit := r < 150 or pity >= 50;
    if legend_hit then
      -- garanti/şans: önce sahip olunmayan efsanevi
      select array_agg(x) into pick from unnest(legend) x where not exists (select 1 from public.skins_owned o where o.account_id = a.id and o.skin = x);
      if pick is null then pick := legend; end if;
      got := pick[1 + floor(random() * array_length(pick, 1))::int];
      pity := 0;
    elsif r < 550 then got := epic[1 + floor(random() * 4)::int];
    elsif r < 650 then got := 'ring_titan:1';
    elsif r < 1150 then got := 'titan_shard:2';
    elsif r < 1500 then got := 'neck_dark:1';
    elsif r < 2400 then got := 'dark_crystal:2';
    elsif r < 3500 then got := 'potion_power:3';
    elsif r < 4600 then got := 'chest_military:1';
    elsif r < 6100 then got := 'medkit:5';
    elsif r < 7800 then got := 'potion_heal:3';
    else got := 'gold:1000';
    end if;
    if got not like '%:%' then
      -- kaplama: zaten varsa altına döner
      if exists (select 1 from public.skins_owned o where o.account_id = a.id and o.skin = got) then
        got := case when got = any(legend) then 'gold:2000' else 'gold:800' end;
      else
        insert into public.skins_owned (account_id, skin) values (a.id, got);
      end if;
    end if;
    if got like 'gold:%' then gold := gold + split_part(got, ':', 2)::int; end if;
    out := out || got;
  end loop;
  update public.crate_pity set opens = opens + n, since_legend = pity where account_id = a.id;
  insert into public.grants (account_id, gems, gold, note) values (a.id, -cost, gold, 'elmas sandığı ×' || n);
  perform public._zx_log(a.id, 'shop', 'Elmas Sandığı açtı ×' || n || ': ' || array_to_string(out, ', '));
  return jsonb_build_object('cost', cost, 'rewards', to_jsonb(out), 'pity', 50 - pity);
end $$;

create or replace function public.zx_skins(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key);
begin
  return jsonb_build_object('owned', coalesce((select jsonb_agg(skin) from public.skins_owned where account_id = a.id), '[]'::jsonb),
    'pity', 50 - coalesce((select since_legend from public.crate_pity where account_id = a.id), 0));
end $$;

grant execute on function public.zx_crate_open(bigint, text, uuid, int) to anon, authenticated;
grant execute on function public.zx_skins(bigint, text) to anon, authenticated;
