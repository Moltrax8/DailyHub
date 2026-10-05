# DailyHub — Architecture v5

> Buildable system specification for this repository.
> App module: `PersonalNoteApp` · package `com.moltrax.personalnoteapp`
> Version `1.0` (`versionCode 10`) · `minSdk 26`, `targetSdk 35`, `compileSdk 35`, Java/Kotlin JVM `17`.
> Single-module native Android app: Kotlin + Jetpack Compose (Material 3) + Hilt + Room + DataStore + Retrofit + WorkManager + Glance.

Related artefacts in this repo:

- `openapi.yaml` — HTTP surface the app consumes (ExerciseDB via RapidAPI + Google Drive v3 subset). DailyHub exposes no server.
- `schema.sql` — SQLite DDL for Room `AppDatabase` v19 (`personal_note_app.db`).
- `.editorconfig` — canonical formatting (Kotlin `official` style, 4-space indent).
- `local.properties.example` — template for git-ignored `local.properties`. Only `DRIVE_*` keys are read by the build (see §12); the template also shows `GOOGLE_CLIENT_ID` and `EXERCISEDB_KEY`, which no build script reads.

---

## 1. What the app is

Personal productivity hub:

- **Tasks** — title, notes, due date, priority, category, manual order (drag-and-drop), subtask checklist, rich recurrence (daily/weekly/monthly/interval), focus-timer duration, links to workouts/programs.
- **Calendar** — agenda view over task due dates. Implemented as the `CalendarContent` composable (`ui/screen/calendar/CalendarScreen.kt` + `CalendarViewModel`), embedded as a sub-tab inside the Home Tasks tab. There is no standalone Calendar navigation route.
- **Workouts** — program groups → workouts → exercises → planned sets; live session logging; session summary; exercise library with demo media (GIF/video, cached offline); weightlifting / bodyweight / duration / cardio exercise types.
- **Focus timer** — per-task countdown (`FocusScreen` + `FocusTimerViewModel`).
- **Profile / Settings** — theme, language (`tr`/`en`), reminder lead time, system alerts, display name, birth date, ExerciseDB key entry, sign-in / backup status.
- **System integration** — deadline reminder notifications (exact alarms + reboot reschedule), home-screen Glance widget (toggle subtasks, complete, undo, new task deep-link), Drive backup/sync (currently disabled by kill-switch, see §9).

No backend owned by this project. Persistence is local-first (Room); sync is a single JSON snapshot file (`tasks_sync.json`) in the user's Google Drive `appDataFolder`.

---

## 2. High-level diagram

```text
┌───────────────────────────────────────────────────────────────┐
│ UI (Compose + ViewModels)                                     │
│ Home (Tasks + embedded Calendar) · TaskDetail · Focus ·       │
│ Workout* · Profile · Settings · Login · SyncBanner ·          │
│ AppNavHost (type-safe routes) · BottomNavBar (3 tabs)         │
└───────────────┬───────────────────────────────┬───────────────┘
                │ observes StateFlow/Flow       │ events
┌───────────────▼───────────────┐ ┌─────────────▼───────────────┐
│ Domain                        │ │ System                      │
│ Task/Workout/Category models  │ │ SyncWorker · RescheduleNotifs│
│ + recurrence math, repositories│ │ NotificationService/Receivers│
│ interfaces, SyncStatus        │ │ BootReceiver · TaskWidget    │
└───────────────┬───────────────┘ └─────────────┬───────────────┘
                │ via Hilt interfaces           │
┌───────────────▼───────────────────────────────▼───────────────┐
│ Data                                                          │
│ Room (7 tables, 4 DAOs) · DataStore prefs · Retrofit          │
│ ExerciseDB · DriveApiService/DriveAuthService (OkHttp) ·      │
│ ExerciseVideoStorage + WorkoutFileManager                     │
└───────────────┬───────────────────────────────┬───────────────┘
                │                               │
        ┌───────▼────────┐             ┌────────▼────────┐
        │ On-device      │             │ External HTTP   │
        │ SQLite + files │             │ ExerciseDB API  │
        │ + DataStore    │             │ Google Drive v3 │
        └────────────────┘             └─────────────────┘
```

Dependency rule: `ui → domain ← data`. UI never touches Room/OkHttp directly; Data implements `domain.repository.*` interfaces, bound via Hilt (`RepositoryModule`). Navigation is type-safe with `@Serializable` route objects.

---

## 3. Tech stack (pinned in `gradle/libs.versions.toml`, except `kotlin.code.style` which lives in `gradle.properties:6`)

| Concern | Library / version |
|---|---|
| Language / build | Kotlin `2.0.21`, AGP `8.7.0`, KSP `2.0.21-1.0.28`, `kotlin.code.style=official` (in `gradle.properties`, not TOML) |
| UI | Compose BOM `2024.12.01`, Material 3, `material-icons-extended`, activity-compose `1.9.3`, navigation-compose `2.8.4`, `reorderable 2.4.0` (drag-and-drop) |
| DI / background | Hilt `2.52` (+ `hilt-navigation-compose`, `hilt-work`), WorkManager `2.10.0` |
| Local data | Room `2.6.1`, DataStore Preferences `1.1.1`, Security-Crypto `1.1.0-alpha06` |
| Network | Retrofit `2.11.0` + kotlinx-serialization converter, OkHttp `4.12.0`, kotlinx-serialization-json `1.7.3`, coroutines `1.9.0` |
| Google | `play-services-auth 21.2.0`, `google-services 4.4.2` (requires git-ignored `app/google-services.json`) |
| Media | Coil `2.7.0` (+ `coil-gif`), Media3 `1.4.1` (ExoPlayer + UI) |
| Widget | Glance `1.1.0` (`appwidget` + `material3`) |
| Testing | JUnit `4.13.2`, Espresso `3.6.1` |

`compileSdk/targetSdk 35`, R8 minify + shrink-resources on for `release` only, WorkManager initialisation customised via `PersonalNoteApp` (`androidx.startup` provider for `WorkManagerInitializer` is removed in the manifest).

---

## 4. Project layout

Top-level source root: `app/src/main/`.

```text
app/src/main/
  AndroidManifest.xml
  res/ (values/strings.xml, values/themes.xml,
        values-en/strings.xml,
        drawable/ic_notification.xml + launcher foreground/background,
        mipmap-anydpi-v26/ic_launcher.xml + ic_launcher_round.xml,
        xml/backup_rules.xml,
        xml/network_security_config.xml, xml/task_widget_info.xml)
  java/com/moltrax/personalnoteapp/
    MainActivity.kt · PersonalNoteApp.kt · FeatureFlags.kt
    ui/
      navigation/ AppNavHost.kt Screen.kt
      screen/
        home/ HomeScreen.kt HomeViewModel.kt        // BottomNavBar lives here; Home hosts Tasks + Calendar sub-tabs
        task/ TaskDetailScreen.kt TaskDetailViewModel.kt
        calendar/ CalendarScreen.kt CalendarViewModel.kt   // exposes CalendarContent, embedded in Home, no route
        focus/ FocusScreen.kt FocusTimerViewModel.kt
        workout/ WorkoutScreen.kt WorkoutViewModel.kt WorkoutDetailScreen.kt
                 LiveWorkoutScreen.kt WorkoutSummaryScreen.kt WorkoutSummaryViewModel.kt
        profile/ ProfileScreen.kt ProfileViewModel.kt
        settings/ SettingsScreen.kt SettingsViewModel.kt
        auth/ LoginScreen.kt AuthViewModel.kt
      components/ SyncBanner.kt ExerciseMediaPlayer.kt
      theme/ AppTheme.kt AppColors.kt
      i18n/ Localization.kt EnumLabels.kt
      AppViewModel.kt SyncViewModel.kt
    domain/
      model/ Task.kt SubTask.kt Priority.kt Recurrence.kt Category.kt
             Workout.kt WorkoutSession.kt ExerciseType.kt SyncStatus.kt
             // NB: Exercise data class lives in Workout.kt:56-65; there is no Exercise.kt
      repository/ TaskRepository.kt WorkoutRepository.kt CategoryRepository.kt SyncRepository.kt
      util/ BirthdayUtils.kt
    data/
      json/ WorkoutJsonCodec.kt
      local/db/ AppDatabase.kt Converters.kt
                entity/ TaskEntity.kt CategoryEntity.kt WorkoutEntities.kt
                        ExerciseEntity.kt WorkoutSessionEntity.kt
                dao/ TaskDao.kt CategoryDao.kt WorkoutDao.kt ExerciseDao.kt
      local/preferences/ AppPreferences.kt
      local/storage/ ExerciseVideoStorage.kt WorkoutFileManager.kt
      remote/exercisedb/ ExerciseDbApi.kt
      remote/drive/ DriveApiService.kt DriveAuthService.kt model/SyncMetadata.kt
      repository/ TaskRepositoryImpl.kt WorkoutRepositoryImpl.kt
                  CategoryRepositoryImpl.kt SyncRepositoryImpl.kt
    di/ DatabaseModule.kt NetworkModule.kt RepositoryModule.kt
    service/ NotificationService.kt NotificationBroadcastReceiver.kt BootReceiver.kt
    worker/ SyncWorker.kt RescheduleNotificationsWorker.kt
    widget/ TaskWidget.kt TaskWidgetReceiver.kt TaskWidgetConfigActivity.kt
            CompleteTaskAction.kt ToggleSubtaskAction.kt UndoTaskAction.kt
            DismissUndoAction.kt RefreshTaskWidgetAction.kt
```

Single Gradle module (`:app`); `rootProject.name = "PersonalNoteApp"`.

`data/json/WorkoutJsonCodec.kt` centralises planned/logged set JSON encode/decode.
`data/local/storage/WorkoutFileManager.kt` manages workout-related file lifecycle alongside `ExerciseVideoStorage`.

---

## 5. UI + navigation

Entry: `MainActivity` (singleTop launcher) → `AppNavHost`.

`ui/navigation/Screen.kt` defines exactly 10 route objects. There is no Calendar route:

| Route | Screen | Notes |
|---|---|---|
| `Login` | `LoginScreen` | Google sign-in; start destination only when `DRIVE_SYNC_ENABLED=true`, otherwise skipped |
| `Home` | `HomeScreen` | Task list + filters + embedded `CalendarContent` sub-tab; widget deep-link target (`ACTION_NEW_TASK`) |
| `TaskDetail(taskId="new"\|id)` | `TaskDetailScreen` | Create/edit, subtasks, recurrence, workout/program link |
| `FocusTimer(taskId)` | `FocusScreen` | Per-task countdown, uses `focusDurationSeconds` |
| `WorkoutList` | `WorkoutScreen` | Program groups |
| `WorkoutDetail(groupId)` | `WorkoutDetailScreen` | Workouts inside a group |
| `LiveWorkout(workoutId, groupId)` | `LiveWorkoutScreen` | Set logging → `WorkoutSession` (+ optional `taskId`) |
| `WorkoutSummary(sessionId)` | `WorkoutSummaryScreen` | Read-only result of a session |
| `Profile` / `Settings` | `ProfileScreen` / `SettingsScreen` | Account, prefs, ExerciseDB key, backup status |

Bottom navigation: `DailyHubScaffold` (in `ui/shell`, never inside a feature screen) owns the 5 root destinations — Tasks (`Home`), Workouts (`WorkoutList`), Profile (`Profile`). It is used as `bottomBar` by `HomeScreen`, `WorkoutScreen`, `WorkoutDetailScreen`, and `ProfileScreen`. `SettingsScreen` has a top app bar with back navigation and no bottom bar. Calendar is not a bottom tab; it is a `TabRow` sub-tab (index 0 = Tasks list, index 1 = Calendar) inside Home. The FAB (new task) shows only on the Tasks sub-tab.

Cross-cutting UI: `SyncBanner` (Drive status, composed only when sync enabled), 90 ms fade transitions, i18n via `Localization`/`EnumLabels` (`tr`/`en`, default `en`), theming via `AppTheme`/`AppColors` + `theme_mode` pref (`system` default). Each screen owns its own window insets; `AppNavHost` deliberately adds no `statusBarsPadding` (avoids double top spacing).

ViewModels are Hilt-injected (`hiltViewModel()`), expose `StateFlow`, and call suspend repository functions from `viewModelScope` (Main). Threading for I/O lives below: Room handles its own query threading, `DriveApiService` confines blocking OkHttp `execute()` calls with `withContext(Dispatchers.IO)`, and `ExerciseVideoStorage` downloads with `withContext(Dispatchers.IO)`.

---

## 6. Domain layer

Pure-Kotlin models + repository interfaces; no `android.*` imports (only `java.time`, `java.util`, `kotlinx.serialization`).

- `Task` — id (UUID), title, notes, `dueDate` (epoch-ms), `priority` (`LOW/MEDIUM/HIGH`) + `priorityRaw: String?` (preserves unknown names for round-trip), `isDone`, `isRecurring` + `intervalDays` (legacy) / `recurrenceType` (`DAILY/WEEKLY/MONTHLY/INTERVAL`, null = legacy interval) + `recurrenceDaysOfWeek` (ISO 1–7, empty = task's own weekday), `focusDurationSeconds` (default 1500), `category` (name reference, nullable), `subtasks: List<SubTask>` (embedded), `createdAt/updatedAt/completedAt`, `linkedWorkoutId` vs `linkedProgramId` (+ `programStartIndex`) — mutually exclusive, `sortOrder` (ascending; Home creates new tasks at `min-1` so newest is on top, reorder rewrites `0..n`), `isDeleted` tombstone. Helpers: `nextRecurrenceDue()` (calendar-day stepping, DST-safe; advances past `now`), `withCompletion()` (recurring tasks roll forward instead of closing).
- `SubTask(id, title, isDone)` — `@Serializable`, stored as JSON inside the task row; no separate table.
- `Category(name PK, isPermanent, isDeleted)` — permanent = user-pinned, always shown; temporary = auto-created from task text and garbage-collected when orphaned.
- `WorkoutGroup(id, name, workouts, currentIndex, createdAt, updatedAt, isDeleted)` — `updatedAt` = LWW clock, `isDeleted` = tombstone so deletes propagate instead of resurrecting.
- `Workout / WorkoutExercise / PlannedSet` — `PlannedSet(reps, weightKg, durationSeconds, steps, distanceMeters)` covers weight + cardio with one shape. `WorkoutExercise` carries `type: ExerciseType` + `typeRaw: String?` for unknown-value round-trip.
- `WorkoutSession / LoggedExercise / LoggedSet` — immutable log; `taskId` links a session back to the task that produced it (null for free runs); `LoggedSet.isMeaningful()` drops empty sets. Exercise entries carry `type` + `typeRaw` same as above.
- `Exercise(id, name, bodyPart, equipment, isUnilateral, description, mediaUrl, localMediaPath)` — library cache; `ExerciseType.classify()` derives `WEIGHTLIFTING/BODYWEIGHT/DURATION/CARDIO` from bodyPart/equipment/name keywords (cardio bodyPart → CARDIO; duration keywords such as plank/hold → DURATION; body-weight/assisted equipment → BODYWEIGHT; else WEIGHTLIFTING).
- `SyncStatus = Idle | Syncing | Synced | Error(message)` — surfaced via `SyncRepository.syncStatus`.

Repositories (interfaces in `domain/repository`, impls in `data/repository`): `TaskRepository` (observe/getById/upsert/delete/getAll/getAllForSync/replaceAll + `getTaskOrderTimestamp`/`setTaskOrderTimestamp`), `WorkoutRepository` (groups + sessions + exercise cache + `cleanupOrphanedExerciseMedia()`), `CategoryRepository`, `SyncRepository` (`sync/pushToDrive/pullFromDrive`, `acknowledgeStatus()` clears only `Synced`).

---

## 7. Data layer — local

### 7.1 Room (`personal_note_app.db`, v19, `exportSchema=false`)

Entities/DAOs (7 tables, 4 DAOs — `WorkoutDao` covers groups, workouts, exercises-link rows and sessions):

| Table | Entity | DAO highlights |
|---|---|---|
| `tasks` | `TaskEntity` (22 cols incl. `subtasks`, `recurrenceDaysOfWeek` as JSON `TEXT`, `sortOrder`, `linkedWorkoutId/ProgramId`, legacy `isPenalty` always `false`, `isDeleted` tombstone) | `observeAll()`/`getAll()` ordered `sortOrder ASC, createdAt DESC`, visible queries filter `isDeleted=0` (`getAllRaw()` for sync); `softDelete()` tombstones with fresh `updatedAt`; `replaceAllAtomic()` in one `@Transaction`; `reassignCategory()` / `clearCategory()` use `COLLATE NOCASE` and keep `updatedAt` fresh |
| `categories` | `CategoryEntity(name PK, isPermanent, isDeleted)` | visible lists ordered `name COLLATE NOCASE ASC` and filter tombstones (`getAllRaw()` for sync); orphan-temporary cleanup tombstones instead of deleting; lookups compare `COLLATE NOCASE` while preserving display casing |
| `workout_groups` | `WorkoutGroupEntity(+ updatedAt, isDeleted)` | visible queries filter `isDeleted=0` ordered `createdAt DESC`; `getAllGroupsRaw()` for sync; `softDeleteGroup()` = tombstone + child cleanup in one `@Transaction`; `replaceGroupsAtomic()` for sync merge |
| `workouts` | `WorkoutEntity(FK groupId CASCADE)` | `getWorkoutsForGroup()` ordered `orderIndex` |
| `workout_exercises` | `WorkoutExerciseEntity(FK workoutId CASCADE, plannedSetsJson TEXT, type)` | ordered `orderIndex`; `getReferencedExerciseIds()` for orphan-media GC |
| `exercises` | `ExerciseEntity` | `getAll()` ordered `name`, `search()` (`name`/`bodyPart LIKE`), `getByBodyPart()`, `getBodyParts()` distinct ordered |
| `workout_sessions` | `WorkoutSessionEntity(loggedExercisesJson TEXT, taskId, isDeleted)` | visible queries filter tombstones ordered `startedAt DESC` (`getSessionsRaw()` for sync); `softDeleteSession()`; `getLatestSessionForTask()` for task→summary deep-link; `replaceSessionsAtomic()` for sync merge |

`Converters` serialises `List<String>`, `List<Int>` (weekly days), `List<SubTask>` with kotlinx-serialization. Planned/logged sets are serialised via `WorkoutJsonCodec` and inside `WorkoutEntities.kt` / `WorkoutSessionEntity.kt` (`PlannedSetJson` / `LoggedSetJson` / `LoggedExerciseJson`, `ignoreUnknownKeys=true` for forward compat). Unknown `Priority`/`ExerciseType` names persist verbatim via `priorityRaw`/`typeRaw` and round-trip without coercion.

Migration history — 18 migrations, all in `AppDatabase.kt`, wired in `DatabaseModule`:

`1→2` add `tasks.linkedWorkoutId` · `2→3` create `food_entries` · `3→4` vault salt · `4→5` drop vault · `5→6` `tasks.sortOrder` (backfill `-createdAt`) · `6→7` create `categories` + seed from tasks · `7→8` `food_entries.imagePath` · `8→9` `food_entries.isDeleted` · `9→10` `workout_exercises.type` (default `WEIGHTLIFTING`) · `10→11` `tasks.linkedProgramId/programStartIndex` · `11→12` create `body_weight_entries` · `12→13` `tasks.isPenalty` · `13→14` `tasks.recurrenceType/recurrenceDaysOfWeek/subtasks` · `14→15` `workout_groups.updatedAt/isDeleted` (backfill `updatedAt=createdAt`) · `15→16` drop `food_entries` · `16→17` `workout_sessions.taskId` + drop `body_weight_entries` · `17→18` `isDeleted` tombstones on `tasks`/`categories`/`workout_sessions` · `18→19` indices on `tasks.category/linked*`, `workout_sessions.workoutId/taskId`.

Schema evolution policy: ADDITIVE migrations only (new columns/tables/indices with safe defaults + backfill). Destructive drops require an export/opt-in path — never silently `DROP` user data again. Category access is case-insensitive at the query layer (`COLLATE NOCASE`, display casing preserved).

Removed features leave no tables: vault, food/nutrition, body-weight history are gone; `tasks.isPenalty` column is retained only for compat and always written `false`. See `schema.sql` for the exact v19 DDL.

### 7.2 DataStore (`app_prefs`)

`AppPreferences` keys (all exposed as `Flow`, setters via `edit{}`):

| Key | Type | Default |
|---|---|---|
| `theme_mode` | String | `system` |
| `language` | String (`tr`/`en`) | `en` |
| `reminder_minutes` | Int | `60` |
| `system_alerts_enabled` | Boolean | `true` |
| `is_signed_in` | Boolean | `false` |
| `drive_file_id` | String? | `null` |
| `last_sync_at` | String? (ISO instant) | `null` |
| `birth_date` | String? (ISO `yyyy-MM-dd`) | `null` |
| `birthday_shown_on` | String? (ISO `yyyy-MM-dd`) | `null` |
| `display_name` | String? (blank → null, falls back to Google name) | `null` |
| `workout_drafts` | String? (JSON map task-id → draft sets) | `null` |
| `task_order_updated_at` | Long (LWW clock for `taskOrder` vector) | `0L` |
| `exercisedb_key` | String? (per-user RapidAPI key, blank → null) | `null` (videos disabled) |

### 7.3 Files

`ExerciseVideoStorage` (`@Singleton @Inject`) caches ExerciseDB GIF/MP4 under `filesDir/exercise_media/<exerciseId>.<ext>` (skips re-download when the file exists, cleans stale same-id files with a different extension, sends `X-RapidAPI-Key` with the per-user DataStore key for RapidAPI hosts, returns `null` on failure so UI falls back to remote URL). Repositories delete the file when its exercise becomes orphaned. `WorkoutFileManager` handles adjacent workout file lifecycle tasks.

---

## 8. Data layer — remote

- **ExerciseDB** (`https://exercisedb.p.rapidapi.com/`, Retrofit in `NetworkModule`): `GET /exercises?limit=100&offset=0`, `GET /exercises/bodyPartList`, `GET /exercises/bodyPart/{bodyPart}?limit=100`, `GET /exercises/name/{name}?limit=20` → `ExerciseDbItem(id, name, bodyPart, equipment, gifUrl, instructions)`. Auth uses the per-user key stored in DataStore `exercisedb_key` (entered in Settings → Exercise Demo Videos, read per-request in the OkHttp interceptor via `runBlocking(prefs.exerciseDbKey.first())`); empty key = no `X-RapidAPI-Key` header and no videos, but app still runs. There is no `BuildConfig` key for ExerciseDB. OkHttp logs `BASIC` in debug only.
- **Google Drive backup/sync** (`DriveApiService` + `DriveAuthService`, raw OkHttp, `Dispatchers.IO`): single JSON snapshot `tasks_sync.json` (`SyncMetadata version=4`: `lastModifiedUtc`, `tasks: TaskJson[]`, `categories`, `workoutGroups` incl. tombstones, `workoutSessions`, `taskOrder` + `taskOrderUpdatedAt`). Calls used: `GET /drive/v3/files?spaces=&q=name='tasks_sync.json' and trashed=false&fields=files(id)` (find, with cross-space fallback when the scope changes), `GET /drive/v3/files/{id}?alt=media` (download, 404 = treat as absent; empty body throws instead of wiping remote), `POST /upload/drive/v3/files?uploadType=multipart` (create in `appDataFolder` via `multipart/related`), `PATCH /upload/drive/v3/files/{id}?uploadType=media` (update, 404 → recreate). No ETag/`If-Match` — conflicts resolved client-side by pull-merge-push.
- **Auth**: Google Sign-In (`GoogleSignInOptions.DEFAULT_SIGN_IN` + `requestEmail` + `requestScopes(Scope(BuildConfig.DRIVE_SCOPE))`, no server auth code), `GoogleAuthUtil.getToken("oauth2:<scope>")`, `UserRecoverableAuthException` → consent intent, `getFreshToken()` null when signed out, `EncryptedSharedPreferences` available for tokens. Requires `app/google-services.json` (git-ignored) and a registered Android SHA-1 in Google Cloud Console. No `GOOGLE_CLIENT_ID` build field is read by the app.

---

## 9. Sync logic (`SyncRepositoryImpl`)

- `sync(manual=false)` = `pullInternal()` then `pushInternal()` (pull-first so an empty device never wipes the cloud backup). `pushToDrive()` delegates to `sync(manual=false)`, i.e. it also pull-merge-pushes (silent background path), it is not a push-only write. `pullFromDrive(manual=false)` = fetch-and-merge only.
- Merge (`mergeById` over `*ForSync()` raw lists incl. tombstones):
  - Tasks and workout groups: LWW on `updatedAt`: remote wins when `remote.updatedAt > local.updatedAt`, otherwise a deterministic lexical tiebreak on `toString()` decides (so equal timestamps can still adopt remote; both devices converge to the same winner).
  - Categories: no clock. Tombstone-wins (deleted on either side stays deleted), else permanent-wins (either side permanent → permanent), else local.
  - Sessions: union by id, tombstone-wins, else local.
  - List order travels as a `taskOrder` id vector with its own LWW clock (`taskOrderUpdatedAt` in DataStore). When the remote clock is newer and the vector is non-empty, it is adopted and `sortOrder` is rewritten `0..n` without touching row `updatedAt`; otherwise local order is published. Unknown ids stay at the end in current relative order.
  - All replacements run in single `@Transaction`s (`replaceAllAtomic` / `replaceGroupsAtomic` / `replaceSessionsAtomic`); category rename/delete wrap task+category writes in `db.withTransaction`. Outcomes surface as `SyncStatus` (UI) + `SyncResult` (`Ok`/`Failed(retryable)`, drives `SyncWorker` success/retry/failure). After a pull that changes done/deleted/due dates (alarm signature `id → (isDone, dueDate, isDeleted)`), a unique reschedule work refreshes alarms (widget follows the DB observer). Stale cached `drive_file_id` falls back to re-discovery; discovery adds `trashed=false` + cross-space fallback; empty download bodies throw instead of wiping remote. Unknown `Priority`/`ExerciseType` names round-trip via `priorityRaw`/`typeRaw` instead of being coerced on write.
- Status: `Syncing` shown only for manual sync or recovery from `Error`; background success returns silently to `Idle`; `Error(detail)` always shown until next success; `acknowledgeStatus()` clears only `Synced`. Startup sync skips silently when no signed-in account exists; retryable = `IOException` (or caused by one).
- Bookkeeping: `drive_file_id` + `last_sync_at` in DataStore; foreground re-sync on every `ON_START` after the first (in `AppNavHost`, skipped entirely when the kill-switch is off), startup pull-merge-push in `HomeViewModel.init`, fire-and-forget `SyncWorker` (retry on transient failure, failure on permanent).
- **Current kill-switch**: `FeatureFlags.DRIVE_SYNC_ENABLED = false` — login screen skipped (start destination becomes `Home`), `SyncBanner` / ON_START re-sync / account / backup UI hidden, because the release SHA-1 is not registered in Google Cloud Console. All sync code stays in place; flipping the flag back to `true` re-enables it.

---

## 10. System: notifications, workers, widget

- `NotificationService` + `NotificationBroadcastReceiver` — deadline reminders (`reminder_minutes` lead, `system_alerts_enabled` toggle, exact alarms via `setExactAndAllowWhileIdle` with `canScheduleExactAlarms()` gate). Manifest declares `SCHEDULE_EXACT_ALARM` (capped with `maxSdkVersion=32`) for API ≤32 and `USE_EXACT_ALARM` for API 33+.
- `RescheduleNotificationsWorker` — re-arms alarms on every cold start (deduplicated `KEEP` unique work from `PersonalNoteApp`); `BootReceiver` (`RECEIVE_BOOT_COMPLETED`) additionally covers reboots plus `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, and OEM quickboot actions.
- `SyncWorker` (`HiltWorker`, `sync()` → `success`, `retry()` on transient `IOException`, `failure()` on permanent errors) — background Drive sync.
- Glance widget (`TaskWidget` + `TaskWidgetReceiver` + config activity + five action receivers `CompleteTaskAction` / `ToggleSubtaskAction` / `UndoTaskAction` / `DismissUndoAction` / `RefreshTaskWidgetAction`): today list, complete, subtask toggle, undo (with dismiss), refresh, `+` new-task deep-link (`MainActivity.ACTION_NEW_TASK`). `PersonalNoteApp` observes `TaskRepository.observeAll()` and calls `TaskWidget.requestUpdate()` when the signature changes (id + `isDone` + title + notes + `sortOrder` + `dueDate` + category + `linkedWorkoutId/ProgramId` + subtask id/done/title), with 500 ms debounce, `distinctUntilChanged`, and `drop(1)` to skip the initial emission.
- `PersonalNoteApp` also cancels the stale `penalty_check_periodic` unique work (gamification feature was removed).

Permissions (`AndroidManifest.xml`): `INTERNET`, `POST_NOTIFICATIONS`, exact alarms (see above), `RECEIVE_BOOT_COMPLETED`, `CAMERA` + `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE(maxSdkVersion=32)` (gallery goes through the permission-free Photo Picker; media perms are for legacy `MediaStore` paths only). Application flags: `allowBackup=true` with `backup_rules.xml`, `usesCleartextTraffic=false` with `network_security_config.xml`.

---

## 11. DI graph (Hilt, `SingletonComponent`)

- `DatabaseModule` — `Room.databaseBuilder(..., "personal_note_app.db")` + all 18 migrations (`1→2` through `18→19`); provides the four DAOs (`TaskDao`, `CategoryDao`, `WorkoutDao`, `ExerciseDao`).
- `NetworkModule` — shared `Json { ignoreUnknownKeys; isLenient }`, base `OkHttpClient` (debug-only `BASIC` logging), `ExerciseDbApi` Retrofit instance (`https://exercisedb.p.rapidapi.com/`) with per-request RapidAPI-key interceptor reading DataStore `exercisedb_key` (not `BuildConfig`).
- `RepositoryModule` — `@Binds` `*Impl → *Repository` (Task, Workout, Category, Sync) as `@Singleton`.
- `PersonalNoteApp` supplies `HiltWorkerFactory` to WorkManager; `DriveAuthService` and `ExerciseVideoStorage` are `@Singleton @Inject` constructors.

---

## 12. Build, secrets, release

Only `DRIVE_*` keys become `BuildConfig` fields. The ExerciseDB key is runtime per-user input, and Google sign-in is configured via `google-services.json`, not via `local.properties` client-ID keys.

| Key | Required | Effect |
|---|---|---|
| `sdk.dir` | yes | Android SDK path |
| `DRIVE_FOLDER_NAME` | no | Informational label; code fallback `PersonalNoteAppBackup` when unset (code uses `appDataFolder` regardless) |
| `DRIVE_SCOPE` | no | Defaults to `https://www.googleapis.com/auth/drive.appdata`; the `spaces=` lookup (`appDataFolder` vs `drive`) follows whether the scope contains `appdata` |
| `app/google-services.json` | for Drive sync | Git-ignored Firebase/Google config; requires matching SHA-1 registration or sign-in fails |
| `RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD` | release only | `release.jks` signing (back it up — required for updates) |

`local.properties.example` additionally shows `GOOGLE_CLIENT_ID` and `EXERCISEDB_KEY`; neither is read by `app/build.gradle.kts` — do not add new build wiring for them. Exercise demo videos need no build secret: each user pastes their own RapidAPI ExerciseDB key in Settings → Exercise Demo Videos (stored as DataStore `exercisedb_key`; free tier suffices; empty = videos off).

Commands (Windows): `gradlew.bat :app:assembleDebug` (auto-signed, unoptimised) vs `gradlew.bat :app:assembleRelease` (R8 + ProGuard, signed APK in `app\build\outputs\apk\release\`, renamed to `DailyHub-v1.0.apk`). Debug and release are different Android apps (different signatures) and cannot update over each other.

---

## 13. Conventions & gotchas for contributors

- Kotlin `official` code style (`gradle.properties`), 4-space indent — enforced by `.editorconfig`; Compose + coroutines + `StateFlow`; Turkish comments are common in code, UI strings are `tr`/`en` localised (default `en`).
- IDs are UUID strings; timestamps are epoch-ms (`Long`); JSON-in-`TEXT` columns default to `'[]'`; booleans are SQLite `INTEGER 0/1`.
- Never resurrect deletes: workout-group deletes are tombstones (`isDeleted=1`, children removed), task/category/session deletes are tombstones (`isDeleted=1`, retained for sync); `food/vault/body-weight` tables are intentionally dropped — do not reintroduce them without a migration.
- `linkedWorkoutId` and `linkedProgramId` are mutually exclusive; completing a program-linked task resolves that day's workout via group `currentIndex` (rotation advances on workout-task completion; `programStartIndex` is the cycle start offset).
- Drive sync has no server timestamps — `updatedAt`/`lastModifiedUtc` are client clocks; tasks/groups use strict `>` with a lexical `toString()` tiebreak for convergence, categories/sessions use tombstone-wins rules (see §9). Do not replace this with "keep local on tie" without breaking convergence.
- `google-services.json` and `release.jks` are git-ignored secrets (`*.jks`, `*.apk`, `*.aab` in `.gitignore`); release APKs (e.g. `DailyHub-v1.0.apk`) are published via GitHub Releases, never committed to the repo.
