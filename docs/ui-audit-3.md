# UI Audit 3 — runtime verification of the redesign (emulator)

Branch: `merge/ui-audit-1`. Re-checks the redesigned app (design-system
snapshot `03c7a9d` merged with the Round 1 fixes) on a real emulator.
Follows up `docs/ui-audit-1.md` (static fixes) and `docs/ui-audit-2.md`
(runtime verification before the redesign).

## Environment
- AVD `dailyhub-e2e` (1280x2856, 480dpi), booted with
  `-no-snapshot -no-audio -gpu swiftshader_indirect`;
  `adb wait-for-device` + `sys.boot_completed` poll (booted on first try).
- Build: `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL;
  installed `app/build/outputs/apk/debug/DailyHub-v1.1.5.apk`
  (`versionCode=10105`), package `com.moltrax.personalnoteapp`.
- Screenshots + `uiautomator dump` XMLs (outside repo):
  `C:/Work/DailyHub-orch/shots/` (`a3_*.png/.xml`: launch gate, all five
  tabs, calendar, New Task form top/scrolled/keyboard, manage-categories
  sheet, workout New-Program dialog, settings top/bottom, and the same
  set again at font scale 1.3 + dark mode, plus the SupabaseAuth screen).
- No login used: launch gate and Profile → Sign in were viewed only;
  "Continue offline" used everywhere. No secrets read.

## Screens checked
- Login gate (new since audit-2: DailyHub logo, offline/sync cards,
  "Sign in with Google", Telegram request note, "Continue offline"):
  clean at font 1.0, light.
- Tasks list (+ notification-permission snackbar + "Open settings" +
  FAB), Calendar (October 2026 grid, selected day, date header),
  Workout ("No programs yet" + "New Program" + FAB), Projects empty
  card + "New project" + FAB, Social (search + friends + spaces),
  Profile (avatar/name/edit, birth-date, signed-out Account card,
  Sign in, Sign Out), Settings (Appearance / Language / Notifications /
  Updates / Exercise Demo Videos / Synchronization, scrolled top to
  bottom), SupabaseAuth ("Welcome back", Email, Password, Sign in,
  create-one link, Continue offline): no clipped / overlapping /
  off-screen text at font 1.0 light or font 1.3 dark.
- New Task form (`TaskDetailScreen`): top (Title/Notes/Subtasks/
  Scheduling/Category), scrolled (Permanent-category checkbox,
  Advanced options, Recurring toggle); with IME open (soft keyboard at
  1.0, Gboard handwriting panel at 1.3 dark) the focused Title field
  stays above the IME → Round 1 `imePadding`+scroll fix still works
  after the redesign (verified in code: `imePadding()` +
  `verticalScroll` intact in `TaskDetailScreen`,
  `SupabaseAuthScreen`, `LoginScreen`, `HomeScreen`; `maxLines`
  ellipsis intact in `CalendarScreen`).
- Bottom nav (Tasks/Workout/Projects/Social/Profile) present and
  working on all screens at both scales; dark-mode FABs (navy on
  Tasks/Projects, lavender on Workout) keep a legible "+" glyph.

## Automated bounds sweep
- 24 `uiautomator` dumps swept for off-screen rects, clickable <
  48dp (144px), and >50% text-over-text overlap: **0 off-screen,
  0 overlaps**. 3 "small-click" flags, all investigated and dismissed
  as Compose→UIAutomator fidelity artifacts (inner glyph/path nodes,
  not layout touch areas): a 144x95 `View` (trailing "+" of the
  New-category field at 1.3 — the `IconButton` is default 48dp in
  code and the same screen at 1.0 exposes it as 144x144), and a
  144x39 `CheckBox` node (M3 `Checkbox` enforces 48dp via
  `minimumInteractiveComponentSize`; at 1.3 the same row is exposed
  as a 229x144 clickable). Screenshots show all of them rendering at
  normal size.
- `adb logcat -d -s AndroidRuntime:E`: **empty**; no FATAL/app crashes
  all session.

## Findings
- **No real layout or crash problems found. No code changes made.**
- Non-blocking observations (not fixed, out of "layout/crash" scope,
  same as audit-2): FAB container color still varies per screen
  (navy vs lavender, swapped between light/dark) — legible everywhere,
  design choice, left as-is. Settings still shows "1 hours before"
  (pluralization), pre-existing, no layout impact.
- Could not check: logged-in-only surfaces (friend search results,
  Duo-hub tabs/chat, board with real cards, GitHub tab, LiveWorkout
  session, sync/auth flows), light mode at font 1.3 (session covered
  1.0 light + 1.3 dark per instructions), landscape rotation.
