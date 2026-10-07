-- ============================================================================
-- DailyHub: give the server-side service_role normal access to public tables.
-- This project does NOT auto-grant privileges on new tables (secure-by-default),
-- and migrations 003-008 only granted to `authenticated`. Edge Functions use the
-- service role (github-connect, github-repos, github-webhook, push-dispatch), so
-- every write failed with "permission denied for table ...".
-- service_role is server-only and bypasses RLS; anon/authenticated are NOT changed.
-- Safe to re-run.
-- ============================================================================
grant usage on schema public to service_role;
grant select, insert, update, delete on all tables in schema public to service_role;
grant usage, select on all sequences in schema public to service_role;

-- Future tables created by postgres in public get the same access.
alter default privileges for role postgres in schema public
  grant select, insert, update, delete on tables to service_role;
alter default privileges for role postgres in schema public
  grant usage, select on sequences to service_role;
