-- ============================================================================
-- DailyHub Phase 4 — friend requests (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / OR REPLACE only, no drops).
-- Requires: 001_phase3_profiles.sql (profiles table).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- friend_requests: directed requests; friendship = accepted request (view).
-- No double-write friends table. Email is never exposed; matching is by id.
-- ----------------------------------------------------------------------------
create table if not exists public.friend_requests (
  id         uuid primary key default gen_random_uuid(),
  from_id    uuid not null references public.profiles (id) on delete cascade,
  to_id      uuid not null references public.profiles (id) on delete cascade,
  status     text not null default 'pending'
             check (status in ('pending', 'accepted', 'rejected')),
  created_at timestamptz not null default now(),
  unique (from_id, to_id),
  check (from_id <> to_id)
);

alter table public.friend_requests enable row level security;

-- Parties see their own rows (both directions); nobody else sees anything.
drop policy if exists "parties see own requests" on public.friend_requests;
create policy "parties see own requests"
  on public.friend_requests for select
  to authenticated
  using (auth.uid() = from_id or auth.uid() = to_id);

-- Sender creates the request (from_id must be self, starts pending).
drop policy if exists "users send requests" on public.friend_requests;
create policy "users send requests"
  on public.friend_requests for insert
  to authenticated
  with check (auth.uid() = from_id and status = 'pending');

-- Only the recipient accepts/rejects (sender cannot self-accept).
drop policy if exists "recipient answers requests" on public.friend_requests;
create policy "recipient answers requests"
  on public.friend_requests for update
  to authenticated
  using (auth.uid() = to_id)
  with check (auth.uid() = to_id);

-- Sender can cancel (delete) their pending request; recipient can delete too
-- (decline-by-delete keeps no row; rejected status is the explicit alternative).
drop policy if exists "parties delete requests" on public.friend_requests;
create policy "parties delete requests"
  on public.friend_requests for delete
  to authenticated
  using (auth.uid() = from_id or auth.uid() = to_id);

-- ----------------------------------------------------------------------------
-- friends: accepted requests as a symmetric view (one row per direction).
-- Read-only convenience for clients; writes go to friend_requests.
-- ----------------------------------------------------------------------------
create or replace view public.friends as
select from_id as user_id, to_id as friend_id, created_at
  from public.friend_requests where status = 'accepted'
union
select to_id as user_id, from_id as friend_id, created_at
  from public.friend_requests where status = 'accepted';
