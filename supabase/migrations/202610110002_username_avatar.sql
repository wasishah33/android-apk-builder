-- Add account usernames and private profile-photo paths to existing installations.
alter table public.profiles add column if not exists username text;
alter table public.profiles add column if not exists avatar_image_path text;

update public.profiles
set username = coalesce(nullif(username, ''), nullif(split_part(email, '@', 1), ''), 'investor') || '_' || left(id::text, 6)
where username is null or username = '';

create unique index if not exists profiles_username_lower_unique
on public.profiles (lower(username))
where username is not null and username <> '';

revoke update on public.profiles from authenticated;
grant update (full_name,country,date_of_birth,address,phone,avatar_image_path) on public.profiles to authenticated;

create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  insert into public.profiles (id,email,full_name,username)
  values (
    new.id,
    coalesce(new.email,''),
    coalesce(new.raw_user_meta_data->>'full_name',''),
    coalesce(new.raw_user_meta_data->>'username','')
  )
  on conflict (id) do nothing;
  insert into public.wallet_accounts (user_id) values (new.id) on conflict (user_id) do nothing;
  return new;
end;
$$;
