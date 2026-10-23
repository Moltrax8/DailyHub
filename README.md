# DailyHub

A local-first Android app for tasks, notes and subtasks, built with Kotlin and Jetpack Compose.

## Features

- Tasks with notes, tags, deadlines, subtasks and daily/weekly/monthly/interval recurrence.
- Standalone Notes with title, body, pinning and search.
- Calendar, reminders and a per-task Focus timer.
- Configurable widgets: task lists/tag filters or one pinned task. Tap a task to expand its checklist, toggle subtasks without opening the app, and see completed/total plus the unfinished count.
- Customize each widget independently: tasks-only/note previews, compact layout, manual/deadline/title sorting, light/dark/system theme, default subtask expansion and completed-subtask visibility.
- Optional automatic update checks, enabled by default, using public GitHub Releases only.

No accounts, backend, cloud sync, social features, projects, repository integration or workouts.
Tasks and notes stay on the device. The updater is the only in-app network feature.

## Build (Windows)

Requires JDK 17 and Android SDK 35. Set `sdk.dir` in a git-ignored `local.properties`
(see `local.properties.example`), or configure the SDK through Android Studio.
Debug builds need no credentials or API keys.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/DailyHub-v1.2.0.apk` (testing only).
Published updates use the signed release APK, `DailyHub-v1.2.0.apk`, with version code `10200`.
Instrumentation tests require a device/emulator; compilation alone does not run them.

## Releases and updates

The app checks `https://api.github.com/repos/Moltrax8/DailyHub/releases/latest` on startup/foreground,
at most once per 15 minutes. Settings also offers a manual check. Disabling automatic checks
stops automatic requests. No background polling or push service is used.

Publish a non-draft, non-prerelease GitHub release with a version tag and a signed `.apk` asset.
Version codes follow `major * 10000 + minor * 100 + patch`. Use the **same signing key** as
the installed app. Downloads require HTTPS, have a 200 MB limit and are checked for matching
package name, exact release version and signing certificate before Android's installer is launched.
Initial download URLs must belong to this repository, redirects are allowlisted, and asset SHA-256
digests are verified when supplied by GitHub. Installation always
requires user consent. A release-signed APK cannot update a debug-signed installation.

Release signing settings are documented in `local.properties.example`; never commit the keystore
or passwords. Build a signed release with `:app:assembleRelease` only when you intend to release it.

## Existing installations

Room migration 22 → 23 preserves visible tasks, notes within tasks, subtasks, recurrence and tags.
It removes workout data and obsolete shared-space/project caches and adds a local `notes` table.
Previously deleted task/category tombstones are not resurrected. No cloud data is imported.

See [PRIVACY.md](PRIVACY.md) and [TERMS.md](TERMS.md).
