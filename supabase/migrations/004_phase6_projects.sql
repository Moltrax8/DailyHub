-- ============================================================================
-- DailyHub Phase 6 — projects on top of spaces (Supabase/Postgres)
-- Run this in the Supabase Dashboard → SQL Editor → New query → Run.
-- Safe to re-run (IF NOT EXISTS / OR REPLACE only, no drops).
-- Requires: 003_phase5_spaces.sql (spaces, is_space_member helpers).
--
-- Projects are containers, not categories: spaces(type=PROJECT) + extension
-- tables below. Same member-only RLS shape as Phase 5.
-- ORDER: tables first, then policies (Postgres validates at CREATE).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Tables.
-- ----------------------------------------------------------------------------
create table if not exists public.projects (
  space_id       uuid primary key references public.spaces (id) on delete cascade,
  description_md text,
  board_columns  text[] not null default '{Idea,Planned,Developing,Finished}'
);

create table if not exists public.project_items (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  title      text not null,
  status     text not null default 'Idea'
             check (status in ('Idea', 'Planned', 'Developing', 'Finished')),
  body_md    text,
  linked_url text,
  sort_order bigint not null default 0,
  updated_at timestamptz not null default now()
);

create table if not exists public.comments (
  id         uuid primary key default gen_random_uuid(),
  space_id   uuid not null references public.spaces (id) on delete cascade,
  ref_type   text,
  ref_id     uuid,
  author     uuid references public.profiles (id) on delete set null,
  body_md    text,
  created_at timestamptz not null default now()
);

alter table public.projects enable row level security;
alter table public.project_items enable row level security;
alter table public.comments enable row level security;

-- Privileges (RLS policies alone never grant access on raw-SQL tables).
grant select, insert, update, delete on public.projects to authenticated;
grant select, insert, update, delete on public.project_items to authenticated;
grant select, insert, update, delete on public.comments to authenticated;

-- ----------------------------------------------------------------------------
-- 2. Policies (member-only, same shape as Phase 5).
-- ----------------------------------------------------------------------------
drop policy if exists "members read projects" on public.projects;
create policy "members read projects" on public.projects for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write projects" on public.projects;
create policy "members write projects" on public.projects for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members edit projects" on public.projects;
create policy "members edit projects" on public.projects for update
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members delete projects" on public.projects;
create policy "members delete projects" on public.projects for delete
  to authenticated using (public.is_space_member(space_id));

drop policy if exists "members read project_items" on public.project_items;
create policy "members read project_items" on public.project_items for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write project_items" on public.project_items;
create policy "members write project_items" on public.project_items for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members edit project_items" on public.project_items;
create policy "members edit project_items" on public.project_items for update
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members delete project_items" on public.project_items;
create policy "members delete project_items" on public.project_items for delete
  to authenticated using (public.is_space_member(space_id));

drop policy if exists "members read comments" on public.comments;
create policy "members read comments" on public.comments for select
  to authenticated using (public.is_space_member(space_id));
drop policy if exists "members write comments" on public.comments;
create policy "members write comments" on public.comments for insert
  to authenticated with check (public.is_space_member(space_id));
drop policy if exists "members delete comments" on public.comments;
create policy "members delete comments" on public.comments for delete
  to authenticated using (public.is_space_member(space_id));
