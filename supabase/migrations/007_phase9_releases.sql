-- ============================================================================
-- DailyHub Phase 9 — automatic update releases (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / OR REPLACE only, no drops).
--
-- app_releases: single latest-row-per-version log written ONLY by the
-- github-release-webhook Edge Function (service role bypasses RLS).
-- The app reads it publicly (signed-out update checks must work) and
-- subscribes via Realtime postgres_changes — it NEVER polls GitHub.
-- ORDER: table first, then policies.
-- ============================================================================

create table if not exists public.app_releases (
  id           uuid primary key default gen_random_uuid(),
  version_name text not null,
  version_code integer not null,
  apk_url      text not null,
  notes        text,
  published_at timestamptz not null default now(),
  created_at   timestamptz not null default now(),
  unique (version_code)
);

alter table public.app_releases enable row level security;

-- Public read for everyone (anon included): update checks work signed-out.
drop policy if exists "anyone reads releases" on public.app_releases;
create policy "anyone reads releases"
  on public.app_releases for select
  to anon, authenticated
  using (true);

-- No client writes: only the service-role webhook function inserts.
-- (No insert/update/delete policies → denied for anon/authenticated.)
