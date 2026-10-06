# UI Audit 2 — runtime verification (emulator)

Branch: `agent/ui-audit-1`. Verifies the Round 1 static fixes (`docs/ui-audit-1.md`)
on a real emulator. No login available/used; all screens below are reachable signed-out
(the app is fully usable offline).

## Environment
- AVD `dailyhub-e2e` (1280x2856, 480dpi / 3.0), booted with `-no-snapshot -no-audio
  -gpu swiftshader_indirect`; `adb wait-for-device` + `sys.boot_completed` poll.
- Build: `./gradlew :app:assembleDebug --offline` → BUILD SUCCESSFUL;
  installed `app/build/outputs/apk/debug/DailyHub-v1.1.3.apk`, package
  `com.moltrax.personalnoteapp`, launched `.MainActivity`.
- Screenshots + `uiautomator dump` XMLs (outside repo):
  `C:/Work/DailyHub-orch/shots/` (`02_home` … `07_tasks` at font scale 1.0;
  `10–14` per-tab, `15_calendar`, `17–19` TaskDetail, `21_settings`,
  `22/24/25` keyboard/auth at font scale 1.3 + dark mode `night yes`).
- Automated bounds sweep over all 20 dumps (off-screen rects, clickable < 48dp
  = 144px, >50% text-over-text overlap): **0 issues**.
- `adb logcat -d -s AndroidRuntime:E`: **empty**; no FATAL/app crashes all session.

## Screens checked (font 1.0 and 1.3, dark mode)
- Tasks list + notification-permission banner + "Open settings": no overlap at
  either scale; banner text wraps, action stays visible.
- Calendar tab (October 2026 grid, prev/next month actions with descriptions):
  grid aligned, "6 October 2026, Tuesday" + empty state correct at 1.3.
- New Task form (`TaskDetailScreen`): all sections render at 1.3; form scrolls
  (swipe reveals Recurring row fully); with soft keyboard open the focused Title
  field stays above the keyboard → Round 1 fix #1 (`imePadding`+scroll) works.
- Account / sign-in (`SupabaseAuthScreen`): clean at 1.3; with keyboard open the
  focused Email field, Sign in button and links all stay visible → fix #2 works.
- Workout ("No programs yet"), Projects empty card + "New project", Social
  (search + friends + spaces sections), Profile (panels + Account card +
  Sign in), Settings (Language / Notifications / Updates sections): no
  clipped/overlapping/off-screen text at 1.3; Settings wraps cleanly.
- Bottom nav (Tasks/Workout/Projects/Social/Profile) + FABs present on all tabs.

## Round 1 fix verification
- P1 keyboard/scroll (#1 TaskDetail, #2 SupabaseAuth): PASS (keyboard tests above).
- P1 overflow/ellipsis + P2 strings/states/a11y + P3 theme: no counter-evidence
  at runtime (empty states centered, titles single-line, no off-screen nodes).
- Kanban board columns untouched (no projects existed to open a board; per scope,
  board layout was not modified).

## Findings
- **No real layout or crash problems found.** No code changes made.
- Non-blocking observation (not fixed, out of "layout/crash" scope): the "+"
  FAB on Projects / Project-detail / Duo-hub screens uses the default M3
  `FloatingActionButton` color (`primaryContainer`, dark navy in dark theme),
  while Tasks uses `AppColors.Accent` and Workout uses `primary` (bright purple).
  The "+" glyph remains legible; normalizing is a design choice, left as-is.
- Minor copy nit (not fixed): Settings shows "1 hours before" (pluralization),
  pre-existing, no layout impact.
- Could not check: logged-in-only surfaces (friend search results, Duo-hub
  tabs/chat, board with real cards, GitHub tab, LiveWorkout session, sync/auth
  flows), light mode (device session stayed dark), landscape rotation.
