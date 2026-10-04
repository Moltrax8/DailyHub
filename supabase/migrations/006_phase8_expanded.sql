-- ============================================================================
-- DailyHub Phase 8 — expanded shared content (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / OR REPLACE only, no drops).
-- Requires: 003_phase5_spaces.sql (spaces, is_space_member helpers).
--
-- events (single instances for now; recurrence rules deferred), messages
-- (chat; true Realtime subscription arrives in Phase 9, pull+poll until
-- then), files (metadata table + space-files Storage bucket), activity_feed
-- (server-side event log the app writes best-effort, reads as the feed).
-- ORDER: tables (+bucket) first, then policies.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Tables (+bucket).
-- ----------------------------------------------------------------------------
create table if not exists public.events (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  title      text not null,
  start_at   bigint not null,
  end_at     bigint,
  rrule      text,
  created_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now()
);

create table if not exists public.messages (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  author     uuid references public.profiles (id) on delete set null,
  body       text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.files (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  path       text not null,
  size       bigint not null default 0,
  created_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  unique (space_id, path)
);

create table if not exists public.activity_feed (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  kind       text not null,
  ref        jsonb,
  actor      uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now()
);

alter table public.events enable row level security;
alter table public.messages enable row level security;
alter table public.files enable row level security;
alter table public.activity_feed enable row level security;

-- Privileges (RLS policies alone never grant access on raw-SQL tables).
grant select, insert, update, delete on public.events to authenticated;
grant select, insert, delete on public.messages to authenticated;
grant select, insert, delete on public.files to authenticated;
grant select, insert on public.activity_feed to authenticated;

-- space-files bucket: private (member-only via path = <space_id>/...).
insert into storage.buckets (id, name, public)
values ('space-files', 'space-files', false)
on conflict (id) do nothing;

-- ----------------------------------------------------------------------------
-- 2. Policies.
-- ----------------------------------------------------------------------------
drop policy if exists "members read events" on public.events;
create policy "members read events" on public.events for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write events" on public.events;
create policy "members write events" on public.events for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members edit events" on public.events;
create policy "members edit events" on public.events for update
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members delete events" on public.events;
create policy "members delete events" on public.events for delete
  to authenticated using (public.is_space_member(space_id));

drop policy if exists "members read messages" on public.messages;
create policy "members read messages" on public.messages for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write messages" on public.messages;
create policy "members write messages" on public.messages for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members delete own messages" on public.messages;
create policy "members delete own messages" on public.messages for delete
  to authenticated using (author = auth.uid() or public.is_space_owner(space_id));

drop policy if exists "members read files" on public.files;
create policy "members read files" on public.files for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write files" on public.files;
create policy "members write files" on public.files for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members delete files" on public.files;
create policy "members delete files" on public.files for delete
  to authenticated using (public.is_space_member(space_id));

-- Feed: members read; any member may append (writers are app-side, best-effort).
drop policy if exists "members read feed" on public.activity_feed;
create policy "members read feed" on public.activity_feed for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members append feed" on public.activity_feed;
create policy "members append feed" on public.activity_feed for insert
  to authenticated with check (public.is_space_member(space_id));

-- Bucket objects: path is <space_id>/<file>; member-only read/write/delete.
drop policy if exists "members read space files" on storage.objects;
create policy "members read space files"
  on storage.objects for select
  to authenticated
  using (
    bucket_id = 'space-files'
    and public.is_space_member(((storage.foldername(name))[1])::uuid)
  );

drop policy if exists "members write space files" on storage.objects;
create policy "members write space files"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'space-files'
    and public.is_space_member(((storage.foldername(name))[1])::uuid)
  );

drop policy if exists "members delete space files" on storage.objects;
create policy "members delete space files"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'space-files'
    and public.is_space_member(((storage.foldername(name))[1])::uuid)
  );
