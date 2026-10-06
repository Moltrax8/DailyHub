# DailyHub Design Guide (binding for every UI task)

Goal: a complete, professional, calm app. Reference apps studied: Things 3 (calm hierarchy, restraint, no alarming red for overdue), Todoist (fast capture, uncluttered), TickTick (dense but organized, themes), Hevy/Strong (compact set rows, clear checked states, one-session flow), Android/AOSP settings guidelines, Material 3 lists/empty-state guidance.
Sources: developer.android.com/design/ui/mobile/guides/patterns/settings, source.android.com/docs/core/settings/settings-guidelines, m3.material.io/components/lists, m2.material.io/design/communication/empty-states.

## Principles (what the best apps do)
1. HIERARCHY over decoration: one clear title per screen, section labels small and quiet, content in a few well-spaced groups. No heavy bordered card around every single element.
2. RESTRAINT: one accent colour for primary actions; colour carries meaning (error red only for real errors/destructive). Do NOT use alarming red/orange for ordinary states (e.g. overdue, notification-off): use a subtle neutral/tonal chip with an action.
3. STATUS, NOT PARAGRAPHS: rows show the current value ("Dark", "Every day 18:00", "Connected as @name") in secondary text instead of long explanations. Long help goes behind an info action or a second screen. Max ONE line of supporting text per row (two only if unavoidable); truncate with ellipsis.
4. CONSISTENCY: the same component for the same job everywhere (top bar, section header, list row, card, chip, FAB, dialog, empty state, button). If a component is missing, add it to ui/components/DhComponents.kt and reuse it - never restyle per screen.
5. FRICTIONLESS CAPTURE: the primary action (add task / start workout / add project) is one tap, always reachable (FAB or prominent button).
6. GOOD EMPTY STATES: neutral icon/illustration (restrained), a short headline that says what the screen is for, one line of why/how, ONE clear button when there is a next step. Never just "No data".
7. FEEDBACK: pressed/checked states visible, completing a task/set gives a brief satisfying animation (checkmark, strike-through fade), snackbars for undo.
8. NO SURPRISES: destructive actions (sign out, delete, disconnect) need confirmation or undo; sign out lives at the bottom of Profile in a quiet destructive style.

## Layout tokens (Material 3 grid)
- 8dp grid. Screen horizontal margin 16dp. Vertical rhythm between sections 24dp; between rows inside a group 0 (use hairline dividers inset 56dp from the leading icon).
- Exactly ONE status-bar inset and ONE navigation-bar inset per screen (no double padding, no empty bands). Top bar: small 64dp, title titleLarge, back arrow at start, actions at end, same on every screen.
- List row: one-line 56dp, two-line 72dp. Leading icon 24dp in a 40dp tonal circle OR plain 24dp icon (pick one style app-wide: tonal circle for Settings/Profile). Trailing: switch / value text / chevron.
- Touch targets >= 48dp. Bottom nav 80dp with labels. FAB 56dp, bottom-end, 16dp margin, above nav bar and above any banner.
- Corner radii: small 8dp (chips, inputs), medium 12dp (cards/groups), large 16-28dp (sheets/dialogs), FAB 16dp. Elevation: prefer tonal surface + 1dp outline over shadows.
- Typography (Material 3): screen title titleLarge; section label labelLarge in onSurfaceVariant (sentence case, NOT all caps); row title bodyLarge; supporting text bodyMedium onSurfaceVariant; captions labelSmall. Never more than 3 text styles in one row.
- Colour: use MaterialTheme/Dh tokens only; no hard-coded colours. Contrast >= 4.5:1 for text in light AND dark. Surfaces: background -> surface -> surfaceContainer for grouped sections.
- Works at font scale 1.3 (no clipping; rows grow), light + dark, landscape (content scrolls), RTL-safe start/end paddings.

## Component recipes
- GROUP: a section label above a single rounded (12dp) surfaceContainer block containing rows separated by inset dividers. Not one card per row.
- SETTINGS ROW: [icon] Title / status line .......... [switch | value | chevron]. Tapping the row does the main action. Dependent setting sits right below its parent and is disabled with a short reason.
- CHOICE: 2-3 options -> compact segmented control; 4-6 presets -> chips; many options -> dialog with radio buttons. Sliders only for true ranges, with the current value shown.
- CHIP/STATUS: small tonal pill ("Offline", "Connected", "Overdue 2d") - never a big red paragraph.
- BUTTONS: one filled primary per screen section; secondary = tonal/outlined; text button for tertiary; destructive = error-coloured text/outlined, confirm in dialog.
- DIALOGS/SHEETS: title + 1-2 lines + max two actions; bottom sheets for pickers.
- BANNERS (sync errors, permission): inline, dismissible, tonal (not full red), sit in the content flow above the list, never floating over the FAB.
- TASK ROW (Things/Todoist style): round checkbox, title (1-2 lines), meta line with due date/tag chips in secondary colour, swipe actions optional; completed = faded + strikethrough. Overdue shown as a subtle coloured date, not a red block.
- WORKOUT SET ROW (Hevy style): compact row [set #] [previous] [kg] [reps] [check] with clear checked state; rest timer pill; big primary "Finish".
- AVATAR: 64-96dp in profile header, initials fallback, edit affordance as a small pencil badge, name + email next to/below it.

## Screen blueprints
- TASKS: top bar "Tasks" + calendar toggle/filter in actions; filter chips (All/Active/Completed) as one scrollable row; grouped list (Overdue / Today / Upcoming / No date) with section labels; FAB add; good empty state.
- CALENDAR: month grid with clear today/selected states, task dots; agenda list below; scrolls as one page in landscape.
- TASK DETAIL: title field large, then grouped rows (Due date, Reminder, Repeat, Tags, Notes), subtasks list with add row; sticky Save/Done.
- WORKOUT: list of programs/groups as cards with exercise/set counts; clear "Start" action; Live workout in Hevy style.
- PROJECTS: cards with name, repo/status chips, member avatars; project hub tabs (Board, Notes, Chat, GitHub...) as a clean scrollable tab row. DO NOT change kanban board column layout.
- SOCIAL: friends list rows with avatar/name/status, requests as a clearly separated group with Accept/Reject, search at top.
- PROFILE: header (avatar, name, email, status chip), then groups: Account (Sign in / Sign out), Sync & backup status, Integrations (GitHub status), Personal (birth date); app version footer.
- SETTINGS: groups in order Account, Appearance, Language, Notifications, Sync & backup, Integrations, Updates, About (app name, version name+code, build, Check for updates, Privacy, Terms, Releases). Settings reachable signed out.
- LOGIN: logo, one-line value statement, primary Google button, secondary "Continue offline", small legal links.
