# DailyHub — Architecture

> Source of truth reverse-engineered from code (September 2026).
> App module: `PersonalNoteApp` · package `com.moltrax.personalnoteapp`
> Version `1.0` (`versionCode 10`) · `minSdk 26`, `targetSdk/compileSdk 35`, Java/Kotlin JVM `17`.
> Single-module native Android app: Kotlin + Jetpack Compose (Material 3) + Hilt + Room + DataStore + Retrofit + WorkManager + Glance.

Related artefacts in this repo:

- `openapi.yaml` — HTTP surface the app **consumes** (ExerciseDB via RapidAPI + the Google Drive v3 calls used for backup/sync). DailyHub exposes no server of its own.
- `schema.sql` — SQLite DDL for Room `AppDatabase` **v19** (`personal_note_app.db`).
- `.editorconfig` — canonical formatting (Kotlin `official` style, 4-space indent).

---

## 1. What the app is

Personal productivity hub:

- **Tasks/quests** — title, notes, due date, priority, category, manual order (drag-and-drop), subtask checklist, rich recurrence (daily/weekly/monthly/interval), focus-timer duration, links to workouts/programs.
- **Calendar** — agenda view over task due dates (`CalendarScreen` + `CalendarViewModel`).
- **Workouts** — program groups → workouts → exercises → planned sets; live session logging; session summary; exercise library with demo media (GIF/video, cached offline); bodyweight / duration / cardio / weightlifting exercise types.
- **Focus timer** — per-task countdown (`FocusScreen` + `FocusTimerViewModel`).
- **Profile / Settings** — theme, language (`tr`/`en`), reminder lead time, system alerts, display name, birth date, sign-in / backup status.
- **System integration** — deadline reminder notifications (exact alarms + reboot reschedule), home-screen Glance widget (toggle subtasks, complete, undo, new task deep-link), Drive backup/sync (currently **disabled**, see §9).

No backend owned by this project. Persistence is local-first (Room); sync is a JSON snapshot file (`tasks_sync.json`) in the user's Google Drive `appDataFolder`.

---

## 2. High-level diagram

```text
┌───────────────────────────────────────────────────────────────┐
│ UI (Compose + ViewModels)                                     │
│ Home · TaskDetail · Calendar · Focus · Workout* · Profile ·   │
│ Settings · Login · SyncBanner · AppNavHost (type-safe routes) │
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
│ Room (7 tables) · DataStore prefs · Retrofit ExerciseDB ·     │
│ DriveApiService/DriveAuthService (OkHttp) · VideoStorage      │
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

## 3. Tech stack (pinned in `gradle/libs.versions.toml`)

| Concern | Library / version |
|---|---|
| Language / build | Kotlin `2.0.21`, AGP `8.7.0`, KSP `2.0.21-1.0.28`, `kotlin.code.style=official` |
| UI | Compose BOM `2024.12.01`, Material 3, `material-icons-extended`, activity-compose `1.9.3`, navigation-compose `2.8.4`, `reorderable 2.4.0` (drag-and-drop) |
| DI / background | Hilt `2.52` (+ `hilt-navigation-compose`, `hilt-work`), WorkManager `2.10.0` |
| Local data | Room `2.6.1`, DataStore Preferences `1.1.1`, Security-Crypto `1.1.0-alpha06` |
| Network | Retrofit `2.11.0` + kotlinx-serialization converter, OkHttp `4.12.0`, kotlinx-serialization-json `1.7.3`, coroutines `1.9.0` |
| Google | `play-services-auth 21.2.0`, `google-services 4.4.2` (`app/google-services.json`, git-ignored) |
| Media | Coil `2.7.0` (+ `coil-gif`), Media3 `1.4.1` (ExoPlayer + UI) |
| Widget | Glance `1.1.0` (`appwidget` + `material3`) |
| Testing | JUnit `4.13.2`, Espresso `3.6.1` |

`compileSdk/targetSdk 35`, R8 minify on for `release` only, app startup via `androidx.startup` (WorkManager init customised in `PersonalNoteApp`).

---

## 4. Project layout (`app/src/main`)

```text
app/src/main/
  AndroidManifest.xml
  java/com/moltrax/personalnoteapp/
    MainActivity.kt · PersonalNoteApp.kt · FeatureFlags.kt
    ui/
      navigation/ AppNavHost.kt Screen.kt
      screen/
        home/ HomeScreen.kt HomeViewModel.kt
        task/ TaskDetailScreen.kt TaskDetailViewModel.kt
        calendar/ CalendarScreen.kt CalendarViewModel.kt
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
      repository/ TaskRepository.kt WorkoutRepository.kt CategoryRepository.kt SyncRepository.kt
      util/ BirthdayUtils.kt
    data/
      local/db/ AppDatabase.kt Converters.kt
                entity/ TaskEntity.kt CategoryEntity.kt WorkoutEntities.kt
                        ExerciseEntity.kt WorkoutSessionEntity.kt
                dao/ TaskDao.kt CategoryDao.kt WorkoutDao.kt ExerciseDao.kt
      local/preferences/ AppPreferences.kt
      local/storage/ ExerciseVideoStorage.kt
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
  res/ · AndroidManifest.xml
```

Single Gradle module (`:app`); `rootProject.name = "PersonalNoteApp"`.

---

## 5. UI + navigation

Entry: `MainActivity` (singleTop launcher) → `AppNavHost`.

| Route (`ui/navigation/Screen.kt`) | Screen | Notes |
|---|---|---|
| `Login` | `LoginScreen` | Google sign-in; skipped when `DRIVE_SYNC_ENABLED=false` |
| `Home` | `HomeScreen` | Task list, filters, widget deep-link target (`ACTION_NEW_TASK`) |
| `TaskDetail(taskId="new"\|id)` | `TaskDetailScreen` | Create/edit, subtasks, recurrence, workout/program link |
| `FocusTimer(taskId)` | `FocusScreen` | Per-task countdown, uses `focusDurationSeconds` |
| `WorkoutList` | `WorkoutScreen` | Program groups |
| `WorkoutDetail(groupId)` | `WorkoutDetailScreen` | Workouts inside a group |
| `LiveWorkout(workoutId, groupId)` | `LiveWorkoutScreen` | Set logging → `WorkoutSession` (+ optional `taskId`) |
| `WorkoutSummary(sessionId)` | `WorkoutSummaryScreen` | Read-only result of a session |
| `Profile` / `Settings` | `ProfileScreen` / `SettingsScreen` | Account, prefs, backup status |

Cross-cutting UI: `SyncBanner` (Drive status, only when sync enabled), `statusBarsPadding` applied once in `AppNavHost`, 90 ms fade transitions, i18n via `Localization`/`EnumLabels` (`tr`/`en`, default `en`), theming via `AppTheme`/`AppColors` + `theme_mode` pref (`system` default).

ViewModels are Hilt-injected (`hiltViewModel()`), expose `StateFlow`, do I/O via repositories on `Dispatchers.IO`.

---

## 6. Domain layer

Pure-Kotlin models + repository interfaces; no Android imports (except `Task.kt` uses `java.time`).

- `Task` — id (UUID), title, notes, `dueDate` (epoch-ms), `priority` (`LOW/MEDIUM/HIGH`), `isDone`, `isRecurring` + `intervalDays` (legacy) / `recurrenceType` (`DAILY/WEEKLY/MONTHLY/INTERVAL`, null = legacy interval) + `recurrenceDaysOfWeek` (ISO 1–7), `focusDurationSeconds` (default 1500), `category` (name FK, nullable), `subtasks: List<SubTask>` (embedded), `createdAt/updatedAt/completedAt`, `linkedWorkoutId` vs `linkedProgramId` (+ `programStartIndex`) — mutually exclusive, `sortOrder` (asc; new tasks get `min-1` so newest is on top). Helpers: `nextRecurrenceDue()`, `withCompletion()` (recurring tasks roll forward instead of closing).
- `SubTask(id, title, isDone)` — `@Serializable`, stored as JSON inside the task row; no separate table.
- `Category(name PK, isPermanent)` — permanent = user-pinned, always shown; temporary = auto-created from task text and garbage-collected when orphaned (`CategoryDao.deleteOrphanTemporary()`).
- `WorkoutGroup(id, name, workouts, currentIndex, createdAt, updatedAt, isDeleted)` — `updatedAt` = LWW clock, `isDeleted` = tombstone so deletes propagate instead of resurrecting.
- `Workout / WorkoutExercise / PlannedSet` — `PlannedSet(reps, weightKg, durationSeconds, steps, distanceMeters)` covers weight + cardio with one shape.
- `WorkoutSession / LoggedExercise / LoggedSet` — immutable log; `taskId` links a session back to the sport task that produced it; `LoggedSet.isMeaningful()` drops empty sets.
- `Exercise(id, name, bodyPart, equipment, isUnilateral, description, mediaUrl, localMediaPath)` — library cache; `ExerciseType.classify()` derives `WEIGHTLIFTING/BODYWEIGHT/DURATION/CARDIO` from bodyPart/equipment/name keywords.
- `SyncStatus = Idle | Syncing | Synced | Error(message)` — surfaced via `SyncRepository.syncStatus`.

Repositories (interfaces in `domain/repository`, impls in `data/repository`): `TaskRepository` (observe/upsert/delete/replaceAll), `WorkoutRepository` (groups + sessions + exercise cache + `cleanupOrphanedExerciseMedia()`), `CategoryRepository`, `SyncRepository` (`sync/pushToDrive/pullFromDrive`, `acknowledgeStatus()` clears only `Synced`).

---

## 7. Data layer — local

### 7.1 Room (`personal_note_app.db`, v19, `exportSchema=false`)

Entities/DAOs:

| Table | Entity | DAO highlights |
|---|---|---|
| `tasks` | `TaskEntity` (20 cols incl. `subtasks`, `recurrenceDaysOfWeek` as JSON `TEXT`, `sortOrder`, `linkedWorkoutId/ProgramId`, legacy `isPenalty` always `false`, `isDeleted` tombstone) | `observeAll()` ordered `sortOrder ASC, createdAt DESC`, all visible queries filter `isDeleted=0` (`getAllRaw()` for sync); `softDelete()` tombstones; `replaceAllAtomic()` in one `@Transaction`; `reassignCategory()` / `clearCategory()` keep `updatedAt` fresh |
| `categories` | `CategoryEntity(name PK, isPermanent, isDeleted)` | ordered `NOCASE`, visible filters tombstones (`getAllRaw()` for sync); orphan-temporary cleanup tombstones instead of deleting; `ensureExists()` revives tombstones |
| `workout_groups` | `WorkoutGroupEntity(+ updatedAt, isDeleted)` | visible queries filter `isDeleted=0`; `getAllGroupsRaw()` for sync; `softDeleteGroup()` = tombstone + delete children in one `@Transaction`; `upsertGroupWithChildren()` atomically replaces a group's workouts/exercises |
| `workouts` | `WorkoutEntity(FK groupId CASCADE)` | `getWorkoutsForGroup()` ordered `orderIndex` |
| `workout_exercises` | `WorkoutExerciseEntity(FK workoutId CASCADE, plannedSetsJson TEXT, type)` | `getReferencedExerciseIds()` for orphan-media GC |
| `exercises` | `ExerciseEntity` | `search()` (`name`/`bodyPart LIKE`), `getByBodyPart()`, `getBodyParts()` |
| `workout_sessions` | `WorkoutSessionEntity(loggedExercisesJson TEXT, taskId, isDeleted)` | visible queries filter tombstones (`getSessionsRaw()` for sync); `softDeleteSession()`; `getLatestSessionForTask()` for task→summary deep-link |

`Converters` serialises `List<String>`, `List<Int>` (weekly days), `List<SubTask>` with kotlinx-serialization. Planned/logged sets are serialised inside `WorkoutEntities.kt` / `WorkoutSessionEntity.kt` (`PlannedSetJson` / `LoggedSetJson` / `LoggedExerciseJson`, `ignoreUnknownKeys=true` for forward compat).

Migration history (all in `AppDatabase.kt`, wired in `DatabaseModule`):

`1→2` add `tasks.linkedWorkoutId` · `2→3` create `food_entries` · `3→4` vault salt · `4→5` drop vault · `5→6` `tasks.sortOrder` (backfill `-createdAt`) · `6→7` create `categories` + seed from tasks · `7→8` `food_entries.imagePath` · `8→9` `food_entries.isDeleted` · `9→10` `workout_exercises.type` (default `WEIGHTLIFTING`) · `10→11` `tasks.linkedProgramId/programStartIndex` · `11→12` create `body_weight_entries` · `12→13` `tasks.isPenalty` · `13→14` `tasks.recurrenceType/recurrenceDaysOfWeek/subtasks` · `14→15` `workout_groups.updatedAt/isDeleted` (backfill `updatedAt=createdAt`) · `15→16` drop `food_entries` · `16→17` `workout_sessions.taskId` + drop
`body_weight_entries` · `17→18` `isDeleted` tombstones on `tasks`/`categories`/`workout_sessions` · `18→19` indices on `tasks.category/linked*`, `workout_sessions.workoutId/taskId`.

Schema evolution policy: ADDITIVE migrations only (new columns/tables/indices with safe defaults + backfill). Destructive drops require an export/opt-in path — never silently `DROP` user data again. Category access is case-insensitive at the query layer (`COLLATE NOCASE`, display casing preserved).

Removed features leave no tables: vault, food/nutrition, body-weight history are gone; `tasks.isPenalty` column is retained only for compat and always written `false`. See `schema.sql` for the exact v17 DDL.

### 7.2 DataStore (`app_prefs`)

`AppPreferences` keys: `theme_mode`, `language`, `reminder_minutes` (default 60), `system_alerts_enabled` (default true), `is_signed_in`, `drive_file_id`, `last_sync_at`, `birth_date` / `birthday_shown_on` (ISO dates), `display_name` (overrides Google name), `workout_drafts` (unconfirmed set inputs per task id, JSON). All exposed as `Flow`, setters via `edit{}`.

### 7.3 Files

`ExerciseVideoStorage` caches ExerciseDB GIF/MP4 under `filesDir/exercise_media/<exerciseId>.<ext>` (skips re-download, adds `X-RapidAPI-Key` for RapidAPI hosts, returns `null` on failure so UI falls back to remote URL). Repositories delete the file when its exercise becomes orphaned.

---

## 8. Data layer — remote (see `openapi.yaml` for full contract)

- **ExerciseDB** (`https://exercisedb.p.rapidapi.com/`, Retrofit in `NetworkModule`): `GET /exercises`, `GET /exercises/bodyPartList`, `GET /exercises/bodyPart/{bodyPart}`, `GET /exercises/name/{name}` → `ExerciseDbItem(id, name, bodyPart, equipment, gifUrl, instructions)`. Auth via `X-RapidAPI-Key: $EXERCISEDB_KEY` (optional; empty key = no videos but app still runs). OkHttp logs `BASIC` in debug only.
- **Google Drive backup/sync** (`DriveApiService` + `DriveAuthService`, raw OkHttp, `Dispatchers.IO`): single JSON snapshot `tasks_sync.json` (`SyncMetadata version=3`: `lastModifiedUtc`, `tasks: TaskJson[]`, `categories`, `workoutGroups` incl. tombstones, `workoutSessions`). Calls used: `GET /drive/v3/files?q=name='tasks_sync.json'` (find), `GET /drive/v3/files/{id}?alt=media` (download, 404 = treat as absent), `POST /upload/drive/v3/files?uploadType=multipart` (create in `appDataFolder`), `PATCH /upload/drive/v3/files/{id}?uploadType=media` (update, 404 → recreate). No ETag/`If-Match` — conflicts resolved client-side by pull-merge-push.
- **Auth**: Google Sign-In (`GoogleSignInOptions` + `drive.appdata` scope from `BuildConfig.DRIVE_SCOPE`), `GoogleAuthUtil.getToken("oauth2:<scope>")`, `UserRecoverableAuthException` → consent intent, `getFreshToken()` null when signed out, `EncryptedSharedPreferences` available for tokens.

---

## 9. Sync logic (`SyncRepositoryImpl`)

- `sync(manual)` = `pullInternal()` then `pushInternal()` (pull-first so an empty device never wipes the cloud backup); `pushToDrive()` = push-only background send; `pullFromDrive()` = fetch-only.
- Merge (`mergeById` over `*ForSync()` raw lists incl. tombstones): tasks and workout groups LWW on `updatedAt` with deterministic lexical tiebreak (convergent across devices); categories union by name with tombstone-wins + permanent-wins; sessions union by id with tombstone-wins, else local. List order travels as a `taskOrder` id vector with its own LWW clock (`taskOrderUpdatedAt` in DataStore; adopted without touching row clocks). All `replaceAll`/`replaceGroups`/`replaceSessions` run in single `@Transaction`s; category rename/delete wrap task+category writes in `db.withTransaction`; `pushToDrive()` pull-merge-pushes like `sync(manual=false)`; outcomes surface as `SyncStatus` (UI) + `SyncResult` (`Ok`/`Failed(retryable)`, drives `SyncWorker` success/retry/failure). After a pull that changes done/deleted/due dates, a unique reschedule work refreshes alarms (widget follows the DB observer). Stale cached `drive_file_id` falls back to re-discovery; discovery adds `trashed=false` + cross-space fallback; empty download bodies throw instead of wiping remote. Unknown `Priority`/`ExerciseType` names round-trip via `priorityRaw`/`typeRaw` instead of being coerced on write.
- Status: `Syncing` shown only for manual sync or recovery from `Error`; background success returns silently to `Idle`; `Error(detail)` always shown until next success; `acknowledgeStatus()` clears only `Synced`.
- Bookkeeping: `drive_file_id` + `last_sync_at` in DataStore; foreground re-sync on every `ON_START` after the first (in `AppNavHost`), startup sync in `HomeViewModel`, fire-and-forget `SyncWorker` (retry on failure).
- **Current kill-switch**: `FeatureFlags.DRIVE_SYNC_ENABLED = false` — login screen skipped, sync banner / account / backup UI hidden, because the release SHA-1 is not registered in Google Cloud Console. All sync code stays in place; flipping the flag back to `true` re-enables it.

---

## 10. System: notifications, workers, widget

- `NotificationService` + `NotificationBroadcastReceiver` — deadline reminders (`reminder_minutes` lead, `system_alerts_enabled` toggle, exact alarms via `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`).
- `RescheduleNotificationsWorker` — re-arms alarms on every cold start; `BootReceiver` (`RECEIVE_BOOT_COMPLETED`) covers reboots.
- `SyncWorker` (`HiltWorker`, `sync()` → `retry()` on throw) — background Drive sync.
- Glance widget (`TaskWidget` + `TaskWidgetReceiver` + config activity + five action receivers): today list, complete, subtask toggle, undo (with dismiss), refresh, `+` new-task deep-link (`MainActivity.ACTION_NEW_TASK`). `PersonalNoteApp` observes `TaskRepository` and calls `TaskWidget.requestUpdate()` on signature change (title/done/subtasks only, skipping the initial emission).
- `PersonalNoteApp` also cancels the stale `penalty_check_periodic` unique work (gamification feature was removed).

Permissions (`AndroidManifest.xml`): `INTERNET`, `POST_NOTIFICATIONS`, exact alarms, `RECEIVE_BOOT_COMPLETED`, `CAMERA` + media read (gallery goes through the permission-free Photo Picker; media perms are for legacy `MediaStore` paths only).

---

## 11. DI graph (Hilt, `SingletonComponent`)

- `DatabaseModule` — `Room.databaseBuilder(..., "personal_note_app.db")` + all 16 migrations; provides the four DAOs.
- `NetworkModule` — shared `Json { ignoreUnknownKeys; isLenient }`, base `OkHttpClient`, `ExerciseDbApi` Retrofit instance with RapidAPI-key interceptor.
- `RepositoryModule` — binds `*Impl → *Repository` (Task, Workout, Category, Sync) as `@Singleton`.
- `PersonalNoteApp` supplies `HiltWorkerFactory` to WorkManager; `DriveAuthService` and `ExerciseVideoStorage` are `@Singleton @Inject` constructors.

---

## 12. Build, secrets, release

Secrets come from git-ignored `local.properties` (see `README.md` + `local.properties.example`), injected as `BuildConfig` fields:

| Key | Required | Effect |
|---|---|---|
| `sdk.dir` | yes | Android SDK path |
| `EXERCISEDB_KEY` | no | Enables exercise demo videos |
| `DRIVE_FOLDER_NAME` | no | Defaults to `PersonalNoteAppBackup` (informational; code uses `appDataFolder`) |
| `DRIVE_SCOPE` | no | Defaults to `https://www.googleapis.com/auth/drive.appdata`; also switches `spaces=` between `appDataFolder` and `drive` |
| `RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD` | release only | `release.jks` signing (back it up — required for updates) |

Commands (Windows): `gradlew.bat :app:assembleDebug` (auto-signed, unoptimised) vs `gradlew.bat :app:assembleRelease` (R8 + ProGuard, signed APK in `app\build\outputs\apk\release\`). Debug and release are different Android apps (different signatures) and cannot update over each other.

---

## 13. Conventions & gotchas for contributors

- Kotlin `official` code style (`gradle.properties`), 4-space indent — enforced by `.editorconfig`; Compose + coroutines + `StateFlow`; Turkish comments are common in code, UI strings are `tr`/`en` localised (default `en`).
- IDs are UUID strings; timestamps are epoch-ms (`Long`); JSON-in-`TEXT` columns default to `'[]'`; booleans are SQLite `INTEGER 0/1`.
- Never resurrect deletes: workout-group deletes are tombstones (`isDeleted=1`, children removed), sessions are append-only, `food/vault/body-weight` tables are intentionally dropped — do not reintroduce them without a migration.
- `linkedWorkoutId` and `linkedProgramId` are mutually exclusive; completing a program-linked task resolves that day's workout via `programStartIndex` + group `currentIndex`.
- Drive sync has no server timestamps — `updatedAt`/`lastModifiedUtc` are client clocks; keep LWW comparisons as strict `>` so equal timestamps keep local.
- `google-services.json` and `release.jks` are git-ignored secrets; release APKs (e.g. `DailyHub-v1.0.apk`) are published via GitHub Releases, never committed to the repo.
