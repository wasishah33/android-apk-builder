# MY INVEST — Supabase backend setup

This guide covers the live Supabase steps that cannot be performed by committing Android code alone. The Android client already contains cloud authentication, account/profile and wallet requests, investment and withdrawal request submission, private KYC upload, admin review, and transient connection retries.

## 1. Database schema
1. Open the MY INVEST project at https://supabase.com/dashboard.
2. Open SQL Editor.
3. Open `supabase/schema.sql` in this repository, copy the entire latest file, paste it into a new SQL query, and run it.
4. Confirm it finishes without errors. The latest schema includes the six plans, row-level security policies, validated request/review functions, admin balance/status functions, and the audited `admin_update_investment_plan` function.
5. Run `supabase/migrations/202610110001_private_kyc_bucket.sql` in the SQL Editor to create or enforce the private `kyc-documents` bucket. Confirm the bucket is private in Storage.

If you previously ran an earlier version, rerun the full latest schema. Review any SQL error instead of ignoring it.

## 2. Secure the owner administrator
1. In Authentication → Users, create your owner login or sign up through the app.
2. In SQL Editor, run the following statement after replacing the email with the exact owner email:
   
   ```sql
   update public.profiles
   set role = 'admin', account_status = 'approved', updated_at = now()
   where id = (select id from auth.users where email = 'YOUR_ADMIN_EMAIL');
   ```

3. Verify exactly one row was updated. Do not expose a service-role key in the Android app, repository, or chat. Do not promote investor accounts.
4. Sign in with that owner account and verify the administrator tools appear.

## 3. Android cloud credentials
The repository's GitHub Actions workflow reads repository secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY` and checks that both are present before building. Use only the project URL and publishable/anon client key. Never put the service-role secret in the APK.

## 4. Password recovery emails
1. In Supabase Authentication → URL Configuration, add `myinvest://reset-password` to the allowed redirect URLs.
2. Configure Authentication email settings. For dependable delivery, configure custom SMTP; Supabase's default sender is rate-limited and best-effort.
3. In the app, use **Forgot password?**, open the reset email on the Android phone with MY INVEST installed, follow the link, and set a new password. This needs live verification because the redirect and email template depend on your Supabase project settings.

## 5. Account status notification emails
The repository includes `supabase/functions/admin-notify/index.ts`. It checks the caller's Supabase session and approved admin role before sending an account status email via Resend.

To enable it:
1. Create a Resend account and verify a sender domain/email.
2. Deploy the function named `admin-notify` with JWT verification enabled. Use the Supabase CLI or the Edge Functions section of your project dashboard.
3. In Edge Function Secrets, set `RESEND_API_KEY` and `FROM_EMAIL` (the verified sender address). Supabase supplies the project URL and legacy anon key to the function runtime.
4. Test approval, rejection, resubmission-required and suspension notifications using a test account. A successful account status change does not prove the email was delivered.

## 6. Investment plan administration
After the latest schema has run, the administrator tools include **Manage investment plans**. The owner can change each plan's name, minimum amount, configured rate and active status. Changes go through a server-side admin-only RPC and are written to the audit log. A configured rate is not a guarantee of profit.

## 7. Identity document storage
The `kyc-documents` bucket must remain private. The schema defines row-level policies so an investor can access only their own objects and an approved administrator can review submissions. Admin document links are short-lived. Do not make the bucket public.

## 8. Scope of payments
As requested, there is **no payment provider integration**. Investors can submit investment and withdrawal requests, and an administrator can review them. Approving a withdrawal does not transfer money; no real deposit or payout processing occurs.

## 9. Test checklist after setup
- [ ] New account signup and email confirmation
- [ ] Password-reset email opens the app and permits a password change
- [ ] Pending account cannot use the investor dashboard before approval
- [ ] Only the designated owner account can open administrator tools
- [ ] ID and face images upload to private storage; another investor cannot access them
- [ ] Admin can approve/reject KYC and investment requests
- [ ] Admin can review withdrawal requests without a false claim that money was sent
- [ ] Admin can change plan settings; a plan update is audit logged
- [ ] Wallet, transactions and account state load consistently after signing in on another device
- [ ] Connection retries and error messages work with network disabled and restored
- [ ] Android navigation works on the target phone

A successful APK build proves compilation and packaging only. The live database schema, private bucket, owner promotion, email settings, function deployment and on-device tests must all be verified separately.
