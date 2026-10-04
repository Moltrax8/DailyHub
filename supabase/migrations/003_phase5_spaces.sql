-- ============================================================================
-- DailyHub Phase 5 — shared spaces / Duo Hub MVP (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / OR REPLACE only, no drops).
-- Requires: 001_phase3_profiles.sql (profiles table).
--
-- One schema for Duo + Projects: spaces(type DUO|PROJECT) + space_members
-- serves both (Phase 6 adds project extensions on top, no second system).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- spaces: a Duo hub or a project container. Personal tasks are NEVER moved
-- here automatically — sharing is always an explicit per-object copy.
-- ----------------------------------------------------------------------------
create table if not exists public.spaces (
  id         uuid primary key default gen_random_uuid(),
  type       text not null check (type in ('DUO', 'PROJECT')),
  name       text,
  created_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now()
);

alter table public.spaces enable row level security;

-- Helper: is the caller a member of the space?
-- (SECURITY DEFINER so RLS on space_members does not recurse infinitely.)
create or replace function public.is_space_member(space uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.space_members m
    where m.space_id = space and m.user_id = auth.uid()
  );
$$;

-- Helper: is the caller an owner of the space?
create or replace function public.is_space_owner(space uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.space_members m
    where m.space_id = space and m.user_id = auth.uid() and m.role = 'owner'
  );
$$;

-- Members see their spaces.
drop policy if exists "members see spaces" on public.spaces;
create policy "members see spaces"
  on public.spaces for select
  to authenticated
  using (public.is_space_member(id));

-- Anyone signed in can create a space (they become a member via members insert).
drop policy if exists "users create spaces" on public.spaces;
create policy "users create spaces"
  on public.spaces for insert
  to authenticated
  with check (created_by is null or created_by = auth.uid());

-- Creator (or owner) can rename; creator can delete the space.
drop policy if exists "creator updates spaces" on public.spaces;
create policy "creator updates spaces"
  on public.spaces for update
  to authenticated
  using (created_by = auth.uid() or public.is_space_owner(id))
  with check (created_by = auth.uid() or public.is_space_owner(id));

drop policy if exists "creator deletes spaces" on public.spaces;
create policy "creator deletes spaces"
  on public.spaces for delete
  to authenticated
  using (created_by = auth.uid());

-- ----------------------------------------------------------------------------
-- space_members: exactly one membership system for Duo + Projects.
-- ----------------------------------------------------------------------------
create table if not exists public.space_members (
  space_id  uuid not null references public.spaces (id) on delete cascade,
  user_id   uuid not null references public.profiles (id) on delete cascade,
  role      text not null default 'member' check (role in ('owner', 'member')),
  joined_at timestamptz not null default now(),
  primary key (space_id, user_id)
);

alter table public.space_members enable row level security;

drop policy if exists "members see membership" on public.space_members;
create policy "members see membership"
  on public.space_members for select
  to authenticated
  using (public.is_space_member(space_id));

-- Creator (at create time, before any row exists) or an existing owner adds.
drop policy if exists "creator or owner adds members" on public.space_members;
create policy "creator or owner adds members"
  on public.space_members for insert
  to authenticated
  with check (
    exists (select 1 from public.spaces s where s.id = space_id and s.created_by = auth.uid())
    or public.is_space_owner(space_id)
  );

-- Owners change roles; last-owner demotion is app-guarded (see SpaceRepository).
drop policy if exists "owners change roles" on public.space_members;
create policy "owners change roles"
  on public.space_members for update
  to authenticated
  using (public.is_space_owner(space_id))
  with check (public.is_space_owner(space_id));

-- Leave yourself, or owners kick anyone.
drop policy if exists "leave or owners kick" on public.space_members;
create policy "leave or owners kick"
  on public.space_members for delete
  to authenticated
  using (user_id = auth.uid() or public.is_space_owner(space_id));

-- ----------------------------------------------------------------------------
-- notes / shared_tasks / links: MVP content. Member-only on every operation.
-- ----------------------------------------------------------------------------
create table if not exists public.notes (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  author     uuid references public.profiles (id) on delete set null,
  title      text,
  body_md    text,
  updated_at timestamptz not null default now()
);

create table if not exists public.shared_tasks (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  title      text not null,
  is_done    boolean not null default false,
  assignee   uuid references public.profiles (id) on delete set null,
  due_at     bigint,
  sort_order bigint not null default 0,
  updated_at timestamptz not null default now()
);

create table if not exists public.links (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  url        text not null,
  title      text,
  created_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now()
);

alter table public.notes enable row level security;
alter table public.shared_tasks enable row level security;
alter table public.links enable row level security;

-- One member-only policy per operation per table (9 total, same shape).
drop policy if exists "members read notes" on public.notes;
create policy "members read notes" on public.notes for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write notes" on public.notes;
create policy "members write notes" on public.notes for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members edit notes" on public.notes;
create policy "members edit notes" on public.notes for update
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members delete notes" on public.notes;
create policy "members delete notes" on public.notes for delete
  to authenticated using (public.is_space_member(space_id));

drop policy if exists "members read shared_tasks" on public.shared_tasks;
create policy "members read shared_tasks" on public.shared_tasks for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write shared_tasks" on public.shared_tasks;
create policy "members write shared_tasks" on public.shared_tasks for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members edit shared_tasks" on public.shared_tasks;
create policy "members edit shared_tasks" on public.shared_tasks for update
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members delete shared_tasks" on public.shared_tasks;
create policy "members delete shared_tasks" on public.shared_tasks for delete
  to authenticated using (public.is_space_member(space_id));

drop policy if exists "members read links" on public.links;
create policy "members read links" on public.links for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write links" on public.links;
create policy "members write links" on public.links for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members delete links" on public.links;
create policy "members delete links" on public.links for delete
  to authenticated using (public.is_space_member(space_id));
