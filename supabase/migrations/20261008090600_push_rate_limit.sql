-- Ліміт частоти сповіщень на користувача Firebase (і заразом активність бази, щоб безкоштовний проєкт не засинав).
create table public.push_log (
  id bigint generated always as identity primary key,
  uid text not null,
  at timestamptz not null default now()
);
create index push_log_uid_at on public.push_log (uid, at);
alter table public.push_log enable row level security;
-- Політик немає: читати й писати може лише service_role (функція notify).

create function public.push_allow(p_uid text, p_limit int) returns boolean
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  delete from push_log where at < now() - interval '1 day';
  select count(*) into n from push_log where uid = p_uid and at > now() - interval '1 minute';
  if n >= p_limit then return false; end if;
  insert into push_log(uid) values (p_uid);
  return true;
end $$;
revoke execute on function public.push_allow(text, int) from public, anon, authenticated;
grant execute on function public.push_allow(text, int) to service_role;
