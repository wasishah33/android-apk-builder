-- Run in the Supabase SQL Editor as the project owner.
-- Ensure the KYC bucket exists and is private.
insert into storage.buckets (id, name, public)
values ('kyc-documents', 'kyc-documents', false)
on conflict (id) do update set public = false;
