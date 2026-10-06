# UI Audit 4 — light mode + landscape (emulator)

Branch: `polish/round4`. Follows up `docs/ui-audit-3.md`
(1.0-light + 1.3-dark, portrait). This round covers LIGHT mode at
font scale 1.0 and 1.3 in portrait, then LANDSCAPE, offline only.

## Environment
- AVD `dailyhub-e2e`, booted with
  `-no-snapshot -no-audio -gpu swiftshader_indirect`;
  `sys.boot_completed` poll (booted immediately, build time overlapped).
- Build: `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL;
  installed `app/build/outputs/apk/debug/DailyHub-v1.1.5.apk`,
  package `com.moltrax.personalnoteapp`.
- Light mode forced (`cmd uimode night no`), font scale 1.0 then 1.3
  (`settings put system font_scale`), rotation via
  `settings put system accelerometer_rotation 0` +
  `user_rotation 0/1`.
- Screenshots + `uiautomator dump` XMLs (outside repo):
  `C:/Work/DailyHub-orch/shots/pol/pol_*.png/.xml`: launch gate,
  Tasks, Workout, Projects, Social, Profile, Settings top/bottom,
  Calendar, New Task (1.0 portrait); Tasks/Workout/Projects/Social/
  Profile/Settings (1.3 portrait); Tasks/Workout/Projects/Social/
  Profile/Settings/Calendar (landscape 1.0), plus post-fix
  Calendar re-checks (`pol_28/29_*`).
- No login used: "Continue offline" everywhere. No secrets read.

## Screens checked
- Launch gate, Tasks (+ notification snackbar + FAB), Calendar
  (October 2026 grid, selected 6 Oct), New Task form, Workout
  ("No programs yet" + FAB), Projects empty card + FAB, Social
  (search + friends + spaces), Profile (signed-out Account card),
  Settings top (Appearance/Language/Notifications) + bottom
  (Updates/GitHub/ExerciseDB/Sync): no clipped / overlapping /
  off-screen text at 1.0 or 1.3 portrait; landscape left-rail nav
  (Tasks/Workout/Projects/Social/Profile) works, content uses full
  width, FABs bottom-right, no low-contrast text.
- Plural fix verified on-device: Settings shows "1 hour before"
  (was "1 hours before") at both scales; minutes use quantity
  strings too.
- FABs verified on-device: Tasks/Projects/Workout all bright
  `primary`/`onPrimary` via new `DhFab` (no more muted
  `primaryContainer` outliers).
- At 1.3 the Settings "Open / Manage Notification Permissions"
  button wraps to two lines and grows — fits, no clipping.

## Automated bounds sweep
- 23 dumps swept for off-screen rects and clickable < 48dp
  (144px at 480dpi): **0 off-screen**. 3 small-click flags, all
  investigated and dismissed as artifacts (same class as audit-3):
  `1100x9`/`1100x31` NAF slider/scroll-edge slices in Settings
  bottom (scroll-boundary, screen scrolls), and one `144x39`
  NAF `CheckBox` inner-glyph node at the bottom of the New Task
  form (M3 enforces 48dp touch; form scrolls; same artifact as
  audit-3). Text-over-text overlap >50%: **0**.
- `adb logcat -d -s AndroidRuntime:E`: **empty**; no crashes.

## Findings
- **Fixed 1 real layout bug: Calendar overlapped in landscape.**
  `CalendarContent` was a fixed `Column(fillMaxSize)` with square
  `aspectRatio(1f)` day cells + a weighted `LazyColumn` below; in
  landscape (wide cells, short height) the grid overflowed and the
  bottom week rows rendered garbled/overlapping with no scroll.
  Fix (`CalendarScreen.kt`, minimal, no data/sync/auth change):
  root `Column` is now `verticalScroll`, `DayTaskList` is a plain
  `Column` (per-day lists are small; no nested lazy) with a fixed
  `24.dp` empty-state padding. Verified after rebuild +
  reinstall: landscape calendar top (`pol_28`) and scrolled
  (`pol_29`) render clean rows, no overlap. Portrait unchanged
  (content fits, scroll is a no-op).
- No other code changes from the audit. Kanban board layout
  untouched per instructions.
- Could not check: logged-in-only surfaces (friend results,
  Duo-hub tabs/chat, board with real cards, GitHub tab,
  LiveWorkout, sync/auth flows), dark mode (covered in audit-3),
  IME-open states (covered in audit-3, code unchanged:
  `imePadding()` + `verticalScroll` intact).
