-- Ліміт частоти — атомарно: паралельні виклики одного користувача чекають один на одного (блокування на транзакцію),
-- інакше всі встигли б порахувати count(*) до чужого insert і обійти ліміт.
create or replace function public.push_allow(p_uid text, p_limit int) returns boolean
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  perform pg_advisory_xact_lock(hashtext('push_allow:' || p_uid));
  delete from push_log where at < now() - interval '1 day';
  select count(*) into n from push_log where uid = p_uid and at > now() - interval '1 minute';
  if n >= p_limit then return false; end if;
  insert into push_log(uid) values (p_uid);
  return true;
end $$;
revoke execute on function public.push_allow(text, int) from public, anon, authenticated;
grant execute on function public.push_allow(text, int) to service_role;
