-- ============================================================================
-- DailyHub Phase 3 — profiles + avatar storage (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / OR REPLACE only, no drops).
-- ============================================================================

-- citext for case-insensitive unique usernames ("Work" == "work", one owner).
create extension if not exists citext with schema extensions;

-- ----------------------------------------------------------------------------
-- profiles: one row per user, created at sign-up (app upserts on first login).
-- ----------------------------------------------------------------------------
create table if not exists public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  username     citext unique not null,
  display_name text,
  avatar_url   text,
  created_at   timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Privileges first: RLS policies alone are not enough — raw-SQL tables carry
-- no grants (Dashboard-created tables get them automatically).
grant select, insert, update on public.profiles to authenticated;

-- Everyone signed in can search by username / see display names + avatars
-- (public identifier is the username, never the email).
drop policy if exists "profiles readable by authenticated" on public.profiles;
create policy "profiles readable by authenticated"
  on public.profiles for select
  to authenticated
  using (true);

-- Users can insert their own row (id must match their auth uid).
drop policy if exists "users insert own profile" on public.profiles;
create policy "users insert own profile"
  on public.profiles for insert
  to authenticated
  with check (auth.uid() = id);

-- Users can update only their own row (username included, uniqueness enforced).
drop policy if exists "users update own profile" on public.profiles;
create policy "users update own profile"
  on public.profiles for update
  to authenticated
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- ----------------------------------------------------------------------------
-- avatars Storage bucket: user-scoped paths avatars/<uid>/<file>.
-- Create the bucket here; wire its policies below.
-- ----------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

-- Public read (avatars are shown next to usernames everywhere).
drop policy if exists "avatars publicly readable" on storage.objects;
create policy "avatars publicly readable"
  on storage.objects for select
  to anon, authenticated
  using (bucket_id = 'avatars');

-- Owner write only: path must start with the user's own uid.
drop policy if exists "users manage own avatar" on storage.objects;
create policy "users manage own avatar"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "users update own avatar" on storage.objects;
create policy "users update own avatar"
  on storage.objects for update
  to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  )
  with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "users delete own avatar" on storage.objects;
create policy "users delete own avatar"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
