# Project structure

Single Android module, one source root: `app/src/main/java/com/moltrax/personalnoteapp`.
85 Kotlin files, ~7.5k lines. The map below is the rule for where code goes — if a new
file has no obvious home here, the structure is telling you to rethink the change.

```
app/src/main/java/com/moltrax/personalnoteapp/
├── MainActivity.kt · PersonalNoteApp.kt     2   entry point · @HiltAndroidApp
├── di/                                      3   Hilt modules (DB, network, repositories)
├── domain/                                 13   pure Kotlin — no Android imports
│   ├── model/                               8   Task, Note, Category, SubTask, Recurrence, …
│   ├── repository/                          3   contracts the data layer implements
│   └── util/                                2   reminder planning, tag matching
├── data/                                   15   Android/IO implementations of domain
│   ├── local/db/                            2   RoomDatabase, TypeConverters
│   ├── local/db/dao/                        4   queries only, one DAO per aggregate
│   ├── local/db/entity/                     4   tables + relations
│   ├── local/preferences/                   1   DataStore-backed settings
│   └── repository/                          4   Task/Note/Category impls + update repository
├── service/                                 5   notifications, channels, boot/alarm receivers
├── worker/                                  2   WorkManager rescheduling
├── widget/                                 11   Glance home-screen widget
│   ├── (root)                               5   widget UI, receiver, config activity, prefs
│   └── action/                              6   ActionCallbacks (complete, undo, toggle, …)
└── ui/                                     28   Compose only
    ├── app/                                 1   app-wide state (AppViewModel)
    ├── update/                              2   self-update state + prompt dialog
    ├── components/                          9   Dh* design system, one concern per file
    ├── navigation/                          2   routes + NavHost
    ├── shell/                               1   bottom bar / navigation rail chrome
    ├── i18n/                                1   locale helpers
    ├── theme/                               3   colors, theme, design tokens
    └── screen/                             15   one folder per screen
        ├── home/ task/ notes/ calendar/ focus/ settings/   Screen + ViewModel pairs
        └── …
```

## Dependency direction

```
ui ──▶ domain ◀── data
 │                    │
 └──▶ di ─────────────┘        service / worker / widget ──▶ domain (+ data via Hilt)
```

- `domain` never imports `android.*`, Room, Compose or OkHttp.
- `ui` reads `domain` models and calls repository interfaces; it never touches DAOs or Room.
- `data` is the only layer that knows Room, DataStore and the network.
- `di` is the only place that wires implementations to interfaces.
- `service`, `worker` and `widget` are entry points like `ui`; they depend on `domain`.

## Conventions

- One screen per folder: `XScreen.kt` + `XViewModel.kt`; no screen-specific composable
  lives in `ui/components/`.
- `ui/components/` holds only reusable, stateless `Dh*` primitives, grouped by concern
  (states, sections, rows, controls, chips, dialogs, inputs, top bar, previews).
- ViewModels are `@HiltViewModel` + `StateFlow`; screens collect with
  `collectAsStateWithLifecycle()`.
- Strings live in `res/values/strings.xml` (Turkish) and `res/values-en/strings.xml`;
  no user-visible literals in Kotlin.
- Tests mirror the source tree: `app/src/test` (12 JVM files) and `app/src/androidTest`
  (15 device files) use the same package names as the code they cover.
- Root `architecture.md` → `docs/architecture.md` describes runtime behaviour (DB schema,
  migrations, reminders); this file only describes where files live.
