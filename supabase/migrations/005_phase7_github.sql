-- ============================================================================
-- DailyHub Phase 7 — GitHub integration tables (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / OR REPLACE only, no drops).
-- Requires: 003_phase5_spaces.sql (spaces, is_space_member helpers).
--
-- No long-lived GitHub PAT on device: the OAuth token lives server-side
-- (Supabase Vault / encrypted column, managed by Edge Functions); Android
-- holds only its Supabase session. ORDER: tables, then policies.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Tables.
-- ----------------------------------------------------------------------------
-- Which GitHub identity connected (token itself stays in Vault, never here).
create table if not exists public.github_connections (
  user_id      uuid primary key references public.profiles (id) on delete cascade,
  github_login text,
  scopes       text[] not null default '{}',
  connected_at timestamptz not null default now()
);

-- Repos linked into spaces (installed/linking user recorded for audit).
create table if not exists public.github_repos (
  id           bigint primary key,
  space_id     uuid not null references public.spaces (id) on delete cascade,
  full_name    text not null,
  private      boolean not null default false,
  installed_by uuid references public.profiles (id) on delete set null
);

-- Normalized activity rows written ONLY by the github-webhook function
-- (service_role bypasses RLS; clients never write here).
create table if not exists public.github_activity (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  repo_full  text,
  kind       text not null,
  ref        jsonb,
  created_at timestamptz not null default now()
);

-- Per-user, per-space, per-kind push toggles (global default ON).
create table if not exists public.notification_prefs (
  user_id  uuid not null references public.profiles (id) on delete cascade,
  space_id uuid not null references public.spaces (id) on delete cascade,
  kind     text not null,
  enabled  boolean not null default true,
  primary key (user_id, space_id, kind)
);

-- FCM device tokens for push-dispatch (one row per user, latest wins).
create table if not exists public.fcm_tokens (
  user_id    uuid primary key references public.profiles (id) on delete cascade,
  token      text not null,
  updated_at timestamptz not null default now()
);

alter table public.github_connections enable row level security;
alter table public.github_repos enable row level security;
alter table public.github_activity enable row level security;
alter table public.notification_prefs enable row level security;
alter table public.fcm_tokens enable row level security;

-- ----------------------------------------------------------------------------
-- 2. Policies.
-- ----------------------------------------------------------------------------
-- Own connection row only.
drop policy if exists "users see own github connection" on public.github_connections;
create policy "users see own github connection"
  on public.github_connections for select
  to authenticated using (user_id = auth.uid());
drop policy if exists "service manages github connections" on public.github_connections;
create policy "service manages github connections"
  on public.github_connections for all
  to authenticated using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- Linked repos visible to space members; owners link/unlink.
drop policy if exists "members see github repos" on public.github_repos;
create policy "members see github repos"
  on public.github_repos for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "owners link github repos" on public.github_repos;
create policy "owners link github repos"
  on public.github_repos for insert
  to authenticated with check (public.is_space_owner(space_id));
drop policy if exists "owners unlink github repos" on public.github_repos;
create policy "owners unlink github repos"
  on public.github_repos for delete
  to authenticated using (public.is_space_owner(space_id));

-- Activity is read-only for members (written by the webhook function).
drop policy if exists "members read github activity" on public.github_activity;
create policy "members read github activity"
  on public.github_activity for select
  to authenticated using (public.is_space_member(space_id));

-- Own prefs only.
drop policy if exists "users manage own notification prefs" on public.notification_prefs;
create policy "users manage own notification prefs"
  on public.notification_prefs for all
  to authenticated using (user_id = auth.uid())
  with check (user_id = auth.uid());

-- Own FCM token only.
drop policy if exists "users manage own fcm token" on public.fcm_tokens;
create policy "users manage own fcm token"
  on public.fcm_tokens for all
  to authenticated using (user_id = auth.uid())
  with check (user_id = auth.uid());
