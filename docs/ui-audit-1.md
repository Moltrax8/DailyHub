# UI Audit 1 — professionalism findings (DailyHub)

Scope: `app/src/main/java/com/moltrax/personalnoteapp/ui/` + theme + components.
Kanban board column layout (`BoardColumn`/`BoardTab` in `ProjectScreens.kt`) intentionally untouched.
No data/sync/auth/Supabase logic changes.
Status legend: `[x]` = fixed on `agent/ui-audit-1`.

## P1 — keyboard / small-screen overflow
1. [x] `screen/task/TaskDetailScreen.kt` — form `Column` has `verticalScroll` but no `imePadding()`; open keyboard covers notes/category/recurrence fields. Fix: add `imePadding()`.
2. [x] `screen/account/SupabaseAuthScreen.kt` — content `Column` is not scrollable and has no `imePadding()`; keyboard covers password/button on small screens. Fix: `verticalScroll` + `imePadding()`.
3. [x] `screen/auth/LoginScreen.kt` — centered `Column` is not scrollable; can clip on small screens / large fonts. Fix: `verticalScroll`.
4. [x] `screen/focus/FocusScreen.kt` — centered `Column` (260dp timer + 48dp spacer + controls) is not scrollable; overflows in landscape / small screens. Fix: `verticalScroll`.
5. [x] `screen/space/SpaceScreens.kt` (`ChatTab`) — message input `Row` has only `padding(12.dp)`; keyboard covers send field. Fix: add `imePadding()` (+ `navigationBarsPadding` via Scaffold padding already).
6. [x] `screen/home/HomeScreen.kt` (`ManageCategoriesSheet`) — sheet content `Column` is not scrollable and has no `imePadding()`; keyboard covers the new-category field. Fix: `verticalScroll` + `imePadding()`.
7. [x] `screen/project/ProjectScreens.kt` (`ItemDetailDialog`, `EventDialog` in `SpaceScreens.kt`) — dialog content `Column` not scrollable; long comments / small screens clip. Fix: `verticalScroll` (board columns untouched).

## P1 — touch targets < 48dp
8. [x] `screen/home/HomeScreen.kt` (`TaskItem`) — delete `IconButton(modifier = size(36.dp))` and drag-handle `IconButton(size(36.dp))` shrink the 48dp M3 target. Fix: drop the shrink (default 48dp).
9. [x] `screen/task/TaskDetailScreen.kt` (`SubtaskSection`) — remove `IconButton(size(36.dp))` shrinks target. Fix: default 48dp.
10. [x] `screen/workout/WorkoutDetailScreen.kt` — exercise edit `IconButton(size(32.dp))` shrinks target. Fix: default 48dp.
11. [x] `components/SyncBanner.kt` — error `TextButton`s use `contentPadding = PaddingValues(horizontal = 8.dp)`, dropping height below 48dp. Fix: `heightIn(min = 48.dp)`.

## P1 — text overflow / clipping
12. [x] `screen/task/TaskDetailScreen.kt` — deadline `Text` (`Modifier.weight(1f)`, no `maxLines`) and dropdown values can push the clear icon / overflow. Fix: `maxLines = 1` + `Ellipsis`.
13. [x] `screen/workout/WorkoutScreen.kt` (`GroupCard`) — `group.name` has no `maxLines`; long names wrap/push menu. Fix: `maxLines = 1` + `Ellipsis`.
14. [x] `screen/workout/WorkoutDetailScreen.kt` — workout name / exercise names have no `maxLines`; long names push add/delete icons. Fix: `maxLines` + `Ellipsis`.
15. [x] `screen/workout/LiveWorkoutScreen.kt` — TopAppBar title (`session?.workoutName`) has no `maxLines`; long names push Finish action off. Fix: `maxLines = 1` + `Ellipsis`.
16. [x] `screen/focus/FocusScreen.kt` — TopAppBar title (`state.task?.title`) has no `maxLines`. Fix: `maxLines = 1` + `Ellipsis`.
17. [x] `screen/space/SpaceScreens.kt` — `DuoHubScreen` TopAppBar title, `SpaceRow` name, chat/event/file titles have no `maxLines`. Fix: `maxLines = 1` + `Ellipsis`.
18. [x] `screen/social/SocialScreens.kt` — `ProfileRow` username/displayName, `RequestRow` title, `SpaceRow` type label have no `maxLines`; long names push actions off. Fix: `weight(1f)` + `maxLines = 1` + `Ellipsis`.
19. [x] `screen/profile/ProfileScreen.kt` — display name (`weight(1f, fill = false)`) has no `maxLines`; long names push the edit icon. Fix: `maxLines = 1` + `Ellipsis`.
20. [x] `screen/calendar/CalendarScreen.kt` — agenda card `task.title` has no `maxLines`. Fix: `maxLines = 2` + `Ellipsis`.

## P2 — hardcoded strings (should be string resources)
21. `screen/focus/FocusScreen.kt:46` — English snackbar literal ("This task is linked…"). Fix: `focus_linked_notice` string.
22. `screen/task/TaskDetailScreen.kt:243,266` — English literals ("Select at least one day", "Enter a positive number of days"). Fix: `task_weekly_error` / `task_interval_error` strings.
23. `screen/workout/WorkoutDetailScreen.kt:158` — English literal ("Program not found"). Fix: `workout_program_not_found` string.
24. `screen/space/SpaceScreens.kt:294` — English suffix `" (you)"`. Fix: `spaces_you_suffix` / formatted string.
25. `screen/space/SpaceScreens.kt` (`FeedTab`, `kbLabel`, `fmtRange`) — raw `createdAt` ISO timestamp, hardcoded `"B"/"KB"`, `Locale.getDefault()` instead of composition locale. Fix: localized size string + locale-aware date format.

## P2 — missing empty / loading / error states
26. `screen/workout/LiveWorkoutScreen.kt` — when `session.loggedExercises` is empty the screen is a blank list. Fix: centered empty text (`workout_no_exercises` reuse).
27. `screen/social/SocialScreens.kt` (`UserProfileScreen`) — profile resolve is async (`refresh` + `search`) but there is no loading indicator; shows "not found" during load. Fix: show `CircularProgressIndicator` while `state.busy`.
28. [x] `screen/space/SpaceScreens.kt` (`DuoHubScreen`) — bare `CircularProgressIndicator(modifier = padding(16.dp))` is left-aligned, looks broken. Fix: centered full-width indicator.

## P2 — missing content descriptions (a11y)
29. `screen/focus/FocusScreen.kt` — reset `Icon(Icons.Default.Refresh, null)` has no description. Fix: reuse `action_refresh`-style string (`focus_reset`).
30. `screen/workout/WorkoutDetailScreen.kt` — start `Icon(Icons.Default.PlayArrow, null)` has no description. Fix: `workout_start` string.
31. `screen/social/SocialScreens.kt` — back arrows + `PersonAdd`/`PersonRemove` icons have `contentDescription = null`. Fix: `action_back` / `social_add` / `social_remove`.
32. `screen/space/SpaceScreens.kt` / `screen/project/ProjectScreens.kt` — back/refresh/more icons with `null` description. Fix: `action_back` / `action_refresh`.

## P3 — theme consistency (spacing / typography / color)
33. `screen/profile/ProfileScreen.kt` — `StatusPanel` + rows use hardcoded `RoundedCornerShape(6.dp)` and `fontSize = 13/15/22.sp`; theme says cards 20dp, inputs/buttons/chips 12–16dp, typography from M3. Fix: `shapes.medium` for panels, `shapes.small` for rows, `titleLarge/labelMedium/bodyMedium` styles.
34. `screen/settings/SettingsScreen.kt` — `SettingsSection` uses `RoundedCornerShape(16.dp)`; theme `medium` is 20dp. Fix: `shapes.medium`.
35. `screen/home/HomeScreen.kt` — Done badge uses `RoundedCornerShape(6.dp)`; home title uses hardcoded `26.sp ExtraBold`. Fix: badge → `shapes.small`, title → `headlineSmall` + `ExtraBold`.
36. `screen/calendar/CalendarScreen.kt` — `DayCell` uses `RoundedCornerShape(10.dp)` + hardcoded `14.sp`; agenda card uses `12.dp`. Fix: cell → `shapes.small`, card → `shapes.medium`.
37. `screen/auth/LoginScreen.kt` + `screen/account/SupabaseAuthScreen.kt` — titles use hardcoded `28.sp` / `22.sp Bold`. Fix: `headlineSmall`/`titleLarge` styles.
38. `components/ExerciseMediaPlayer.kt` — `12.dp`/`8.dp` radii hardcoded. Fix: `shapes.small` / `shapes.extraSmall`.
39. `screen/profile/ProfileScreen.kt` (`SoloColors`) — aliases `AppColors` but adds a vertical-gradient background + neon borders unique to this screen. Acceptable as intentional identity styling; only radii/typography normalized, palette left alone.
