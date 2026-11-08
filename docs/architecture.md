# DailyHub architecture

Single Android module, package `com.moltrax.personalnoteapp`. Kotlin/JVM 17,
minSdk 26, target/compileSdk 35. Compose/Material 3, Hilt, Room, DataStore,
WorkManager, Glance, Kotlin serialization and OkHttp.

## Local data

`AppDatabase` v23 (`personal_note_app.db`) owns `tasks`, `categories`,
`task_category_cross_ref` and `notes`. Task subtasks and recurrence weekdays are
JSON columns. Cross-references retain per-category order; task `sortOrder` retains
global order. Deletes are hard deletes; no synchronization or tombstones remain.
Repositories expose observable local data to Hilt ViewModels.

Historical migrations are retained so existing installations can upgrade.
`MIGRATION_22_23` rebuilds task/category tables without obsolete columns, retains
non-deleted rows, cleans links, drops removed feature tables and creates notes.
Workout history removal is intentional. No destructive migration fallback is used.

`TaskRepositoryImpl.upsert/delete` refresh/cancel reminders through
`NotificationScheduler`. WorkManager reschedules alarms at startup; `BootReceiver`
handles reboot, clock changes and package replacement. Reminders remain local.

## Navigation

`DailyHubScaffold` owns the five root tabs: Tasks, Notes, Calendar, Focus, Settings.
Detail/editor and focus-timer routes hide the root bar. Wide screens use a rail.
No login gate exists. Notification taps open task details.

Notes are standalone local records with title/body, pin state and timestamps.
Search filters locally. Editors preserve drafts in SavedStateHandle and use an
explicit Save action. Task notes remain separate from standalone notes.

## Widgets

Each widget stores its filter, selected task, expanded task IDs and undo state
in its own Glance preferences. List/tag modes are preserved alongside one-task
mode. A pinned task remains visible after completion; missing tasks show an
explicit reconfiguration message. Legacy selected-task lists continue to display.
Appearance and content settings are per-instance too: dark/light/system palette, compact density,
note previews (or tasks only), manual/deadline/title sorting, default expansion and completed subtasks.

Tapping a task expands/collapses its checklist inline. Checklist items are separate
scrollable lazy rows and can be toggled locally. The pill is completed/total
(`2/5`); adjacent text shows unfinished subtasks. Undo uses a local serializable
snapshot, never a cloud format, and does not resurrect deleted tasks.

## Updates and networking

Only `UpdateRepository` uses the network: public GitHub Releases metadata and
APK downloads. Automatic checks default on and are throttled at foreground/startup.
There is no authentication, API key, backend subscription, polling worker or push.

The installer verifies package name, a newer version matching release metadata, and the complete signing-certificate set.
Initial asset URLs must reference this repository; every redirect hop is checked against an exact
HTTPS host allowlist. APK downloads have a 200 MB cap, reject incomplete bodies, and verify SHA-256
when GitHub provides a digest. FileProvider exposes
only `cache/updates`. Android owns installation/unknown-source consent.

## Verification

JVM tests cover recurrence, reminder planning, focus state, subtasks, update
parsing and widget filtering. Instrumentation tests cover Room/tag behavior,
migration, reminders and root navigation. See `docs/DEPLOYMENT_CHECKLIST.md` for
checks still requiring a device and signed releases.
