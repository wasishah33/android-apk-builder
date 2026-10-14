-- MY INVEST Supabase schema
-- Run this file in Supabase SQL Editor for a NEW project.
-- This schema stores configured plan rates; it does not guarantee or manufacture investment returns.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null,
  full_name text not null default '',
  username text,
  avatar_image_path text,
  country text,
  date_of_birth date,
  address text,
  phone text,
  role text not null default 'investor' check (role in ('investor','admin')),
  account_status text not null default 'pending' check (account_status in ('pending','approved','rejected','resubmission_required','suspended')),
  rejection_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists profiles_username_lower_unique
on public.profiles (lower(username)) where username is not null and username <> '';

create table if not exists public.investment_plans (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  icon text not null,
  minimum_amount numeric(14,2) not null check (minimum_amount > 0),
  fixed_profit_rate numeric(5,2) not null default 60.00 check (fixed_profit_rate >= 0 and fixed_profit_rate <= 100),
  profit_rule text not null default 'fixed_once' check (profit_rule = 'fixed_once'),
  is_active boolean not null default true,
  updated_at timestamptz not null default now()
);

insert into public.investment_plans (slug,name,icon,minimum_amount,fixed_profit_rate,profit_rule)
values
 ('bronze','Bronze','🥉',250.00,60.00,'fixed_once'),
 ('silver','Silver','🥈',500.00,60.00,'fixed_once'),
 ('gold','Gold','🥇',1000.00,60.00,'fixed_once'),
 ('platinum','Platinum','💎',5000.00,60.00,'fixed_once'),
 ('diamond','Diamond','💠',10000.00,60.00,'fixed_once'),
 ('elite','Elite','👑',25000.00,60.00,'fixed_once')
on conflict (slug) do update set
 name=excluded.name, icon=excluded.icon, minimum_amount=excluded.minimum_amount,
 fixed_profit_rate=excluded.fixed_profit_rate, profit_rule=excluded.profit_rule;

create table if not exists public.wallet_accounts (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  deposited_amount numeric(14,2) not null default 0 check (deposited_amount >= 0),
  capital_amount numeric(14,2) not null default 0 check (capital_amount >= 0),
  profit_amount numeric(14,2) not null default 0 check (profit_amount >= 0),
  withdrawals_amount numeric(14,2) not null default 0 check (withdrawals_amount >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.investments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  plan_id uuid not null references public.investment_plans(id),
  principal_amount numeric(14,2) not null check (principal_amount > 0),
  fixed_profit_rate numeric(5,2) not null default 60.00 check (fixed_profit_rate >= 0 and fixed_profit_rate <= 100),
  fixed_profit_amount numeric(14,2) not null default 0 check (fixed_profit_amount >= 0),
  status text not null default 'pending' check (status in ('pending','active','completed','rejected','cancelled')),
  profit_verification_status text not null default 'not_verified' check (profit_verification_status in ('not_verified','verified','rejected')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  transaction_type text not null check (transaction_type in ('deposit','investment','profit_credit','withdrawal_request','withdrawal','admin_adjustment','refund')),
  amount numeric(14,2) not null check (amount >= 0),
  status text not null default 'pending' check (status in ('pending','posted','rejected','cancelled')),
  description text not null default '',
  reference text unique,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);

create table if not exists public.kyc_submissions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  document_type text not null,
  id_image_path text not null,
  face_image_path text not null,
  status text not null default 'pending' check (status in ('pending','approved','rejected','resubmission_required')),
  review_reason text,
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.admin_audit_log (
  id bigint generated always as identity primary key,
  admin_user_id uuid not null references auth.users(id),
  target_user_id uuid references auth.users(id),
  action text not null,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create or replace function public.is_admin()
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'admin' and account_status = 'approved'
  );
$$;

create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  insert into public.profiles (id,email,full_name,username)
  values (new.id, coalesce(new.email,''), coalesce(new.raw_user_meta_data->>'full_name',''),
          coalesce(new.raw_user_meta_data->>'username',''))
  on conflict (id) do nothing;
  insert into public.wallet_accounts (user_id) values (new.id) on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_myinvest on auth.users;
create trigger on_auth_user_created_myinvest
after insert on auth.users
for each row execute procedure public.handle_new_user();

create or replace function public.admin_adjust_wallet(
  p_user_id uuid, p_field text, p_new_amount numeric, p_reason text
)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  old_amount numeric(14,2);
begin
  if not public.is_admin() then raise exception 'Administrator access required'; end if;
  if p_field not in ('deposited_amount','capital_amount','profit_amount','withdrawals_amount') then
    raise exception 'Unsupported balance field';
  end if;
  if p_new_amount is null or p_new_amount < 0 then raise exception 'Amount must be non-negative'; end if;
  if p_reason is null or length(trim(p_reason)) < 4 then raise exception 'A reason is required'; end if;
  insert into public.wallet_accounts(user_id) values (p_user_id) on conflict (user_id) do nothing;
  select case p_field
    when 'deposited_amount' then deposited_amount
    when 'capital_amount' then capital_amount
    when 'profit_amount' then profit_amount
    else withdrawals_amount
  end into old_amount
  from public.wallet_accounts where user_id = p_user_id for update;
  if p_field = 'deposited_amount' then
    update public.wallet_accounts set deposited_amount=p_new_amount,updated_at=now() where user_id=p_user_id;
  elsif p_field = 'capital_amount' then
    update public.wallet_accounts set capital_amount=p_new_amount,updated_at=now() where user_id=p_user_id;
  elsif p_field = 'profit_amount' then
    update public.wallet_accounts set profit_amount=p_new_amount,updated_at=now() where user_id=p_user_id;
  else
    update public.wallet_accounts set withdrawals_amount=p_new_amount,updated_at=now() where user_id=p_user_id;
  end if;
  insert into public.admin_audit_log(admin_user_id,target_user_id,action,details)
  values(auth.uid(),p_user_id,'wallet_balance_changed',
    jsonb_build_object('field',p_field,'old_amount',old_amount,'new_amount',p_new_amount,'reason',trim(p_reason)));
  insert into public.transactions(user_id,transaction_type,amount,status,description,created_by)
  values(p_user_id,'admin_adjustment',p_new_amount,'posted',
    p_field || ' set by administrator: ' || trim(p_reason),auth.uid());
end;
$$;

create or replace function public.admin_set_profile_status(
  p_user_id uuid, p_status text, p_reason text default null
)
returns void
language plpgsql security definer
set search_path = public
as $$
declare old_status text;
begin
  if not public.is_admin() then raise exception 'Administrator access required'; end if;
  if p_status not in ('pending','approved','rejected','resubmission_required','suspended') then
    raise exception 'Unsupported account status';
  end if;
  select account_status into old_status from public.profiles where id=p_user_id for update;
  if old_status is null then raise exception 'Profile not found'; end if;
  update public.profiles set account_status=p_status,rejection_reason=p_reason,updated_at=now() where id=p_user_id;
  insert into public.admin_audit_log(admin_user_id,target_user_id,action,details)
  values(auth.uid(),p_user_id,'account_status_changed',
    jsonb_build_object('old_status',old_status,'new_status',p_status,'reason',p_reason));
end;
$$;

alter table public.profiles enable row level security;
alter table public.investment_plans enable row level security;
alter table public.wallet_accounts enable row level security;
alter table public.investments enable row level security;
alter table public.transactions enable row level security;
alter table public.kyc_submissions enable row level security;
alter table public.admin_audit_log enable row level security;

drop policy if exists "profiles read own or admin" on public.profiles;
create policy "profiles read own or admin" on public.profiles for select to authenticated
using (id=auth.uid() or public.is_admin());
drop policy if exists "profiles update own profile" on public.profiles;
create policy "profiles update own profile" on public.profiles for update to authenticated
using (id=auth.uid() and role='investor' and account_status <> 'suspended')
with check (id=auth.uid() and role='investor');
revoke update on public.profiles from authenticated;
grant update (full_name,country,date_of_birth,address,phone,avatar_image_path) on public.profiles to authenticated;

drop policy if exists "plans visible to signed in users" on public.investment_plans;
create policy "plans visible to signed in users" on public.investment_plans for select to authenticated using (is_active or public.is_admin());
drop policy if exists "plans admin writes" on public.investment_plans;
create policy "plans admin writes" on public.investment_plans for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists "wallet read own or admin" on public.wallet_accounts;
create policy "wallet read own or admin" on public.wallet_accounts for select to authenticated using (user_id=auth.uid() or public.is_admin());
revoke insert, update, delete on public.wallet_accounts from authenticated;

drop policy if exists "investments read own or admin" on public.investments;
create policy "investments read own or admin" on public.investments for select to authenticated using (user_id=auth.uid() or public.is_admin());
drop policy if exists "investor may request investment" on public.investments;
create policy "investor may request investment" on public.investments for insert to authenticated
with check (
  user_id=auth.uid() and status='pending' and fixed_profit_rate=60.00
  and exists(select 1 from public.profiles where id=auth.uid() and account_status='approved')
  and exists(select 1 from public.investment_plans p where p.id=investments.plan_id and p.is_active and investments.principal_amount >= p.minimum_amount)
);
drop policy if exists "admin manages investments" on public.investments;
create policy "admin manages investments" on public.investments for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists "transactions read own or admin" on public.transactions;
create policy "transactions read own or admin" on public.transactions for select to authenticated using (user_id=auth.uid() or public.is_admin());
drop policy if exists "investor creates withdrawal request" on public.transactions;
create policy "investor creates withdrawal request" on public.transactions for insert to authenticated
with check (user_id=auth.uid() and transaction_type='withdrawal_request' and status='pending' and amount > 0);
drop policy if exists "admin manages transactions" on public.transactions;
create policy "admin manages transactions" on public.transactions for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists "KYC read own or admin" on public.kyc_submissions;
create policy "KYC read own or admin" on public.kyc_submissions for select to authenticated using (user_id=auth.uid() or public.is_admin());
drop policy if exists "investor submits KYC" on public.kyc_submissions;
create policy "investor submits KYC" on public.kyc_submissions for insert to authenticated
with check (user_id=auth.uid() and status='pending');
drop policy if exists "admin reviews KYC" on public.kyc_submissions;
create policy "admin reviews KYC" on public.kyc_submissions for update to authenticated
using (public.is_admin()) with check (public.is_admin());

drop policy if exists "audit log admin read only" on public.admin_audit_log;
create policy "audit log admin read only" on public.admin_audit_log for select to authenticated using (public.is_admin());
revoke insert, update, delete on public.admin_audit_log from authenticated;

revoke all on function public.admin_adjust_wallet(uuid,text,numeric,text) from public, anon;
revoke all on function public.admin_set_profile_status(uuid,text,text) from public, anon;
grant execute on function public.admin_adjust_wallet(uuid,text,numeric,text) to authenticated;
grant execute on function public.admin_set_profile_status(uuid,text,text) to authenticated;

-- Private KYC storage bucket. Create the bucket via Dashboard or Storage API:
-- id/name: kyc-documents; Public bucket: OFF.
drop policy if exists "KYC objects read own or admin" on storage.objects;
create policy "KYC objects read own or admin" on storage.objects for select to authenticated
using (bucket_id='kyc-documents' and (name like auth.uid()::text || '/%' or public.is_admin()));
drop policy if exists "investor uploads own KYC objects" on storage.objects;
create policy "investor uploads own KYC objects" on storage.objects for insert to authenticated
with check (bucket_id='kyc-documents' and name like auth.uid()::text || '/%');
drop policy if exists "admin manages KYC objects" on storage.objects;
create policy "admin manages KYC objects" on storage.objects for all to authenticated
using (bucket_id='kyc-documents' and public.is_admin())
with check (bucket_id='kyc-documents' and public.is_admin());

-- IMPORTANT: promote exactly one trusted user to admin manually in the Supabase SQL Editor
-- only after that user has signed up. Replace the email with the owner's exact login email:
-- update public.profiles set role='admin', account_status='approved'
-- where id=(select id from auth.users where email='YOUR_ADMIN_EMAIL');
-- Verify exactly one row was updated. Never expose the service_role key in the Android app.


-- Authenticated investors must request investments and withdrawals through these
-- validated RPCs; they cannot write wallet balances directly.
create or replace function public.request_investment(p_plan_slug text, p_amount numeric)
returns uuid
language plpgsql security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_plan public.investment_plans%rowtype;
  v_wallet public.wallet_accounts%rowtype;
  v_reserved numeric(14,2);
  v_id uuid;
begin
  if v_uid is null then raise exception 'Sign in required'; end if;
  if not exists(select 1 from public.profiles where id=v_uid and role='investor' and account_status='approved') then
    raise exception 'An approved investor account is required';
  end if;
  if p_amount is null or p_amount <= 0 then raise exception 'Enter a valid investment amount'; end if;
  select * into v_plan from public.investment_plans where slug=p_plan_slug and is_active;
  if not found then raise exception 'Investment plan is unavailable'; end if;
  if p_amount < v_plan.minimum_amount then raise exception 'Amount is below this plan minimum'; end if;
  select * into v_wallet from public.wallet_accounts where user_id=v_uid for update;
  if not found then raise exception 'Wallet not found'; end if;
  select coalesce(sum(amount),0) into v_reserved from public.transactions
    where user_id=v_uid and transaction_type='withdrawal_request' and status='pending';
  v_reserved := v_reserved + coalesce((select sum(principal_amount) from public.investments
    where user_id=v_uid and status='pending'),0);
  if p_amount > v_wallet.deposited_amount - v_wallet.capital_amount - v_wallet.withdrawals_amount - v_reserved then
    raise exception 'Insufficient available balance after pending requests';
  end if;
  insert into public.investments(user_id,plan_id,principal_amount,fixed_profit_rate,fixed_profit_amount,status)
  values(v_uid,v_plan.id,p_amount,v_plan.fixed_profit_rate,round(p_amount*v_plan.fixed_profit_rate/100,2),'pending')
  returning id into v_id;
  insert into public.transactions(user_id,transaction_type,amount,status,description,reference)
  values(v_uid,'investment',p_amount,'pending','Investment request: '||v_plan.name,v_id::text);
  return v_id;
end;
$$;

create or replace function public.request_withdrawal(p_amount numeric)
returns uuid
language plpgsql security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_wallet public.wallet_accounts%rowtype;
  v_reserved numeric(14,2);
  v_id uuid;
begin
  if v_uid is null then raise exception 'Sign in required'; end if;
  if not exists(select 1 from public.profiles where id=v_uid and role='investor' and account_status='approved') then
    raise exception 'An approved investor account is required';
  end if;
  if p_amount is null or p_amount <= 0 then raise exception 'Enter a valid withdrawal amount'; end if;
  select * into v_wallet from public.wallet_accounts where user_id=v_uid for update;
  if not found then raise exception 'Wallet not found'; end if;
  select coalesce(sum(amount),0) into v_reserved from public.transactions
    where user_id=v_uid and transaction_type='withdrawal_request' and status='pending';
  v_reserved := v_reserved + coalesce((select sum(principal_amount) from public.investments
    where user_id=v_uid and status='pending'),0);
  if p_amount > v_wallet.deposited_amount - v_wallet.capital_amount - v_wallet.withdrawals_amount - v_reserved then
    raise exception 'Insufficient available balance after pending requests';
  end if;
  insert into public.transactions(user_id,transaction_type,amount,status,description)
  values(v_uid,'withdrawal_request',p_amount,'pending','Withdrawal request awaiting administrator review')
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.admin_review_investment(p_investment_id uuid, p_decision text, p_reason text default null)
returns void
language plpgsql security definer
set search_path = public
as $$
declare v_inv public.investments%rowtype;
begin
  if not public.is_admin() then raise exception 'Administrator access required'; end if;
  if p_decision not in ('active','rejected') then raise exception 'Decision must be active or rejected'; end if;
  select * into v_inv from public.investments where id=p_investment_id for update;
  if not found or v_inv.status <> 'pending' then raise exception 'Pending investment not found'; end if;
  if p_decision='active' then
    update public.investments set status='active',updated_at=now() where id=v_inv.id;
    update public.wallet_accounts set capital_amount=capital_amount+v_inv.principal_amount,updated_at=now() where user_id=v_inv.user_id;
    update public.transactions set status='posted',description=description||' — approved' where reference=v_inv.id::text;
  else
    update public.investments set status='rejected',updated_at=now() where id=v_inv.id;
    update public.transactions set status='rejected',description=description||' — rejected: '||coalesce(p_reason,'') where reference=v_inv.id::text;
  end if;
  insert into public.admin_audit_log(admin_user_id,target_user_id,action,details)
  values(auth.uid(),v_inv.user_id,'investment_reviewed',jsonb_build_object('investment_id',v_inv.id,'decision',p_decision,'reason',p_reason));
end;
$$;

revoke insert, update, delete on public.investments from authenticated;
revoke all on function public.request_investment(text,numeric) from public, anon;
revoke all on function public.request_withdrawal(numeric) from public, anon;
revoke all on function public.admin_review_investment(uuid,text,text) from public, anon;
grant execute on function public.request_investment(text,numeric) to authenticated;
grant execute on function public.request_withdrawal(numeric) to authenticated;
grant execute on function public.admin_review_investment(uuid,text,text) to authenticated;


create or replace function public.admin_review_withdrawal(p_transaction_id uuid, p_decision text, p_reason text default null)
returns void
language plpgsql security definer
set search_path = public
as $$
declare v_tx public.transactions%rowtype;
begin
  if not public.is_admin() then raise exception 'Administrator access required'; end if;
  if p_decision not in ('approved','rejected') then raise exception 'Decision must be approved or rejected'; end if;
  select * into v_tx from public.transactions where id=p_transaction_id for update;
  if not found or v_tx.transaction_type <> 'withdrawal_request' or v_tx.status <> 'pending' then
    raise exception 'Pending withdrawal request not found';
  end if;
  if p_decision='approved' then
    update public.wallet_accounts set withdrawals_amount=withdrawals_amount+v_tx.amount,updated_at=now()
      where user_id=v_tx.user_id;
    update public.transactions set transaction_type='withdrawal',status='posted',
      description=description||' — approved; payout must be completed manually',created_by=auth.uid()
      where id=v_tx.id;
  else
    update public.transactions set status='rejected',
      description=description||' — rejected: '||coalesce(p_reason,''),created_by=auth.uid()
      where id=v_tx.id;
  end if;
  insert into public.admin_audit_log(admin_user_id,target_user_id,action,details)
  values(auth.uid(),v_tx.user_id,'withdrawal_reviewed',
    jsonb_build_object('transaction_id',v_tx.id,'decision',p_decision,'amount',v_tx.amount,'reason',p_reason));
end;
$$;

revoke all on function public.admin_review_withdrawal(uuid,text,text) from public, anon;
grant execute on function public.admin_review_withdrawal(uuid,text,text) to authenticated;


-- Audited administrator-only investment plan editor.
create or replace function public.admin_update_investment_plan(
  p_plan_id uuid, p_name text, p_minimum_amount numeric,
  p_fixed_profit_rate numeric, p_is_active boolean
)
returns void
language plpgsql security definer
set search_path = public
as $$
declare v_old public.investment_plans%rowtype;
begin
  if not public.is_admin() then raise exception 'Administrator access required'; end if;
  if p_name is null or length(trim(p_name)) < 2 then raise exception 'Plan name is required'; end if;
  if p_minimum_amount is null or p_minimum_amount <= 0 then raise exception 'Minimum amount must be greater than zero'; end if;
  if p_fixed_profit_rate is null or p_fixed_profit_rate < 0 or p_fixed_profit_rate > 100 then
    raise exception 'Configured rate must be between 0 and 100';
  end if;
  select * into v_old from public.investment_plans where id=p_plan_id for update;
  if not found then raise exception 'Investment plan not found'; end if;
  update public.investment_plans
    set name=trim(p_name), minimum_amount=p_minimum_amount,
        fixed_profit_rate=p_fixed_profit_rate, is_active=p_is_active, updated_at=now()
    where id=p_plan_id;
  insert into public.admin_audit_log(admin_user_id,target_user_id,action,details)
  values(auth.uid(),null,'investment_plan_updated',jsonb_build_object(
    'plan_id',p_plan_id,'slug',v_old.slug,
    'old',jsonb_build_object('name',v_old.name,'minimum_amount',v_old.minimum_amount,
      'fixed_profit_rate',v_old.fixed_profit_rate,'is_active',v_old.is_active),
    'new',jsonb_build_object('name',trim(p_name),'minimum_amount',p_minimum_amount,
      'fixed_profit_rate',p_fixed_profit_rate,'is_active',p_is_active)));
end;
$$;
revoke insert, update, delete on public.investment_plans from authenticated;
grant select on public.investment_plans to authenticated;
revoke all on function public.admin_update_investment_plan(uuid,text,numeric,numeric,boolean) from public, anon;
grant execute on function public.admin_update_investment_plan(uuid,text,numeric,numeric,boolean) to authenticated;
