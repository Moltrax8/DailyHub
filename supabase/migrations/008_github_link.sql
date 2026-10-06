-- ============================================================================
-- DailyHub GitHub account linking — server-side token storage (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS only, no drops).
-- Requires: 005_phase7_github.sql (github_connections).
--
-- Tokens live ONLY on the server, never on the phone, never in git/logs.
-- Preferred store is Supabase Vault; this migration uses a locked-down table
-- fallback (readable ONLY by the service role) because Vault is impractical
-- to manage from Edge Functions here: RLS is ON, there are NO policies, and
-- all privileges are revoked from anon/authenticated, so only the
-- service_role (which bypasses RLS) used by the Edge Functions can read/write.
-- ORDER: columns, then token table, then hardening.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Missing columns on github_connections (contract: login + github_user_id).
-- ----------------------------------------------------------------------------
alter table public.github_connections
  add column if not exists github_user_id bigint;

alter table public.github_connections
  add column if not exists updated_at timestamptz not null default now();

-- ----------------------------------------------------------------------------
-- 2. Server-side token store (service role only).
-- ----------------------------------------------------------------------------
create table if not exists public.github_tokens (
  user_id       uuid primary key references public.profiles (id) on delete cascade,
  access_token  text not null,
  refresh_token text,
  expires_at    timestamptz,
  scope         text not null default '',
  token_type    text not null default 'bearer',
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

alter table public.github_tokens enable row level security;

-- Locked down: no policies are created on purpose, so anon/authenticated
-- get nothing even if granted; service_role bypasses RLS.
revoke all on public.github_tokens from anon, authenticated, public;
