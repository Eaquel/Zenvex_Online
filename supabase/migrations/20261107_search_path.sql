-- Güvenlik denetçisi (function_search_path_mutable): search_path'i sabitlenmemiş zx fonksiyonlarına public verilir.
do $$
declare f record;
begin
  for f in select p.oid::regprocedure as sig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname like '%zx%' and p.prokind = 'f'
             and not exists (select 1 from unnest(coalesce(p.proconfig, '{}'::text[])) c where c like 'search_path=%') loop
    execute format('alter function %s set search_path = public', f.sig);
  end loop;
end $$;
