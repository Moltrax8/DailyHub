# DailyHub — Implementation Phases

Living build plan. Status is updated per merged commit.
Canonical architecture of the current app: `../architecture.md`.

Conventions across all phases:

- Local-first preserved: Room stays the truth for personal data. Supabase holds
  only identity + explicitly shared objects. No silent upload of tasks/workouts.
- Additive Room migrations only (new tables/columns + backfill, safe defaults).
- One membership foundation: `spaces + space_members` serves Duo Hubs AND
  Projects (`type = DUO | PROJECT`). No parallel invite/role systems.
- Server authoritative for shared data (Postgres + RLS); Room caches a read model.
- Never poll GitHub from the phone: webhook → Edge Function → DB + Realtime/push.

## Phase 1 — Stabilize task experience

### 1.1 Deadline notification fix — DONE (`3e358b7`)

Single choke point `NotificationScheduler.refresh(task)` wired into
`TaskRepositoryImpl` (`upsert`/`delete`/`replaceAll` + orphan sweep).
Pure timing math in `domain/util/ReminderPlan.kt` (JVM-tested); bulk paths keep
using `RescheduleNotificationsWorker`. E2E `NotificationReminderE2ETest` 4/4
green on emulator (real Room → repo → scheduler → AlarmManager).

### 1.2 Remove Priority + Focus Time (UI-first, schema later) — IN PROGRESS

- Hide priority chips + focus slider in `TaskDetailScreen`, timer entry in Home
  task rows (`TaskList`/`TaskItem` `onFocus` threading removed).
- Keep `FocusTimer` route working (no dead deep-links), just unlinked from Home.
- Domain keeps `Task.priority` / `priorityRaw` / `focusDurationSeconds` but
  `@Deprecated`; Room columns + Drive `TaskJson` fields retained for compat.
- Manual `sortOrder` stays the explicit prioritization mechanism.

## Phase 2 — Task flexibility (the breaking Room change) — DONE

Room v20 `task_category_cross_ref` + backfill migration (`MIGRATION_19_20`,
`Migration19To20Test` 1/1, `TaskCategoryDaoTest` 4/4 on emulator).
`Task.categoryNames/orders` in domain, `setTaskCategories` /
`reorderInCategory/Global` / ANY+ALL views / untagged bucket in
`TaskRepositoryImpl` (`TaskRepositoryTagsTest` 5/5), `TaskJson.categories` +
`categoryOrders` with union merge (`CategoryTagsTest` 3/3 JVM), multi-select
detail chips, Home ANY-match + untagged filter bar, per-widget `WidgetFilter`
(title/tags/show-done/limit/matchAll), UI E2E `TaskTagUiTest` 1/1 green.
Legacy `tasks.category` dual-written as read-fallback.

- Keep both orderings: `tasks.sortOrder` (global Home list) and
  `task_category_cross_ref.sortOrder` (position inside a category).
- Room v20: `task_category_cross_ref(taskId, categoryName, sortOrder, addedAt)`,
  backfilled from legacy `tasks.category`. New `TaskCategoryCrossRef` entity +
  `TaskCategoryDao`. `tasks.category` stays one release as read-fallback.
- Domain: `Task.categoryNames: Set<String>`; repo gets
  `observeTasksForCategories`, `setTaskCategories`, `reorderInCategory/Global`.
- UI: multi-select chips in detail screen, ANY-match filter bar, untagged bucket.
- Widgets: per-widget `WidgetFilter` (tags + show-done + limit + matchAll).
- Sync: `TaskJson` gains `categories` + `categoryOrders`, deprecated `category`
  kept; merge unions tag sets.
- Tests: v19→v20 migration test, global vs per-category ordering tests,
  multi-instance widget test.

## Phase 3 — Managed account foundation (Supabase) — CODE DONE, needs SQL + device run

No new SDK: Supabase over the existing Retrofit/OkHttp stack (supabase-kt
3.8.0 needs Kotlin 2.4 metadata + browser 1.10.0 → would force a
compiler/AGP cascade, so raw GoTrue/PostgREST/Storage REST instead).
`SupabaseModule` (URL+anon key from git-ignored local.properties),
`SupabaseTokenStore` (encrypted), `SupabaseAuthService` (REST sessions +
transparent refresh), `ProfileRepository` (profiles + avatar upload),
`SupabaseProfile` + `isValidUsername` (JVM-tested), Auth screen (email →
username steps) + AppNavHost entry (offline stays fully usable).
Tests: `SupabaseModelsTest` 5/5 + `UsernameValidationTest` 3/3 JVM green;
`SupabaseProfileE2ETest` prepared (signup→profile→avatar→signout, runs on
the VM phone once `001_phase3_profiles.sql` is applied + Confirm-email OFF).
Assemble + androidTest-compile green; device run pending.

- Deps: `supabase-kt` (Auth/Postgrest/Realtime/Storage) + `di/SupabaseModule`
  (URL + anon key from git-ignored `local.properties`, never committed).
- `FeatureFlags.SUPABASE_ENABLED`; Drive login stays independently gated.
- `profiles(id, username, display_name, avatar_url)` + `avatars` Storage bucket
  (`avatars/<uid>/<file>`, owner write / public read).
- New `AuthScreen` (email+password, OAuth later); `supabase_user_id` in
  DataStore, session tokens in EncryptedSharedPreferences.
- SQL lives in `../supabase/migrations/`; run it in the project SQL editor.

## Phase 4 — Social foundation — CODE DONE, needs SQL + device run

`002_phase4_friends.sql` (requests table + party-only RLS, friends view;
run in dashboard SQL editor). `SocialRepository` (search/send/cancel/
accept/reject/incoming/outgoing/friends/remove + 30s incoming poll; true push
in Phase 7), SocialGraph/FriendRequests/UserProfile screens, Profile
AccountCard (sign-in state, friends entry with pending badge, sign-out).
Tests: `SocialContractTest` 3/3 JVM green; `SupabaseSocialE2ETest` prepared
(two-user send→incoming→self-accept-DENIED→accept→remove incl. RLS negative,
runs on the VM phone once both SQL files are applied + Confirm-email OFF).
Assemble + androidTest-compile green; device run pending.

- `friend_requests(from_id, to_id, status, unique(from_id,to_id))`; friends =
  accepted-requests view. Username (never email) is the public identifier.
- `SocialRepository`: search/prefix, send/cancel/accept/reject, observe
  incoming/outgoing/friends. RLS: requests visible only to parties, accept only
  by `to_id`. UI: `SocialGraph | UserProfile | FriendRequests`.

## Phase 5 — Shared spaces / Duo Hub MVP — CODE DONE, needs SQL + device run

`003_phase5_spaces.sql` (spaces + members + notes + shared_tasks + links,
member-only RLS via SECURITY DEFINER helpers; run in dashboard SQL editor).
Room v21 offline cache mirror (`SpaceDao`, replace-on-fetch, no tombstones —
server is truth) + `MIGRATION_20_21`. `SpaceRepository` (createDuo with
accepted-friendship-as-invite, find-or-create open, invite/kick/leave,
notes/tasks/links CRUD). DuoHub screen (Notes|Tasks|Links tabs + dialogs),
Spaces section in Social + per-friend Open-hub. Pull-on-open + refresh;
Realtime push arrives in Phase 9.
Tests: `SpaceContractTest` 3/3 JVM green; `SupabaseSpacesE2ETest` prepared
(Duo create → note visible to member → invisible to outsider + cascade
delete, runs on the VM phone once 001–003 are applied + Confirm-email OFF).
Assemble + androidTest-compile green; device run pending.

- `spaces(id, type DUO|PROJECT, name, created_by)` + `space_members` +
  `notes` + `shared_tasks` + `links`. Member-only RLS on every content table.
- Explicit `createDuo(friendId)` (friendship alone never creates a hub).
- MVP scope lock: notes + shared_tasks + links only.
- Android `SpaceRepository` with offline Room cache mirrors + Realtime
  subscribe; sharing is always an explicit per-object copy, never auto-upload.

## Phase 6 — Project Manager — CODE DONE, needs SQL + device run

`004_phase6_projects.sql` (projects + project_items + comments, member-only
RLS; run in dashboard SQL editor). Room v22 cache mirror (`ProjectDao`,
replace-on-fetch) + `MIGRATION_21_22`. `ProjectRepository` (create/update/
delete project, items drag-column via arrows, comments). Projects list +
board detail (Board columns, item dialog with comments, Hub shortcut),
bottom bar now 5 tabs (Tasks·Workouts·Projects·Social·Profile).
Tests: `ProjectBoardTest` 2/2 JVM green; `SupabaseProjectsE2ETest` prepared
(two-user board flow + outsider-denied + cascade, runs on the VM phone once
001–004 are applied + Confirm-email OFF). Assemble + androidTest-compile
green; device run pending.

- `projects(space_id, description_md, board_columns)` +
  `project_items(space_id, title, status Idea|Planned|Developing|Finished, ...)` +
  `comments`. Reuses `spaces(type=PROJECT)`.
- `ProjectRepository`: CRUD, members/roles, drag-column, links.
- UI: `Projects | ProjectDetail { Board | Tasks | Links | Members | GitHub }`.

## Phase 7 — GitHub integration

- No long-lived PAT on device: OAuth via Supabase Auth GitHub provider, token
  in Vault/encrypted column; Android holds only the Supabase session.
- `github_connections` + `github_repos` + `github_activity` +
  `notification_prefs`. Webhook → `github-webhook` Edge Function (HMAC check →
  space lookup → pref check → insert) → `push-dispatch` → FCM → Android
  (`dev_activity` channel → tap opens `ProjectDetail`).

## Phase 8 — Expanded shared

- `events`, `messages` (Realtime), `files` (Storage `space-files`),
  `activity_feed`. Same RLS + Room-cache pattern as Phase 5.

## Phase 9 — Automatic update (opt-in, default OFF)

Goal: users get new GitHub releases without the app polling GitHub.

- Supabase table `app_releases(id, version_name, version_code,
  apk_url, notes, published_at)` — public read for all (anon SELECT, update
  checks must work signed-out); writes service-role only (Edge Function).
- Edge Function `github-release-webhook`: validates `X-Hub-Signature-256`
  (secret in function secrets), accepts `release/published` events only,
  upserts the latest row in `app_releases`.
- GitHub repo → Settings → Webhooks: function URL, JSON, secret, Release events.
- App (`UpdateRepository`, gated by user setting `auto_update`, default OFF):
  - App open: Realtime `postgres_changes` subscription on `app_releases`
    INSERT → instant in-app notice, no polling.
  - App was closed: single Supabase fetch at startup (never GitHub).
  - `version_code > BuildConfig.VERSION_CODE` → prompt (download?);
    on accept: download APK from `apk_url` (GitHub release asset) to cache via
    OkHttp, install via FileProvider + `ACTION_INSTALL_PACKAGE`
    (`REQUEST_INSTALL_PACKAGES` permission + unknown-sources consent).
- Tests: version-compare unit tests; startup-check + prompt flow E2E on emulator
  with a seeded `app_releases` row; webhook HMAC vector test for the function
  (deno test or documented vector).
