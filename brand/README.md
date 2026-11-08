# DailyHub brand assets

This folder is the source of truth for the DailyHub identity. The vector masters here
are the originals; everything shipped in the app is derived from them.

## The Junction mark

A single vertical stem joins three asymmetric tracks (lime, lavender, coral) with a
detached lavender diamond as the anchor. It reads as one hub where different contexts
meet, without borrowing calendar, checkmark, ribbon or network-of-dots clichés.

Palette (`dailyhub-junction/07-guides/tokens.json`):

| Token | Hex | Role |
| --- | --- | --- |
| navy | `#111726` | icon background, dark surfaces |
| night | `#0C1120` | deepest surface |
| lime | `#C5F26B` | first track, highlights |
| lav | `#B4AEF4` | second track, diamond anchor |
| coral | `#FF8475` | third track, emphasis |
| cream | `#F6F3EA` | stem, light surfaces |
| ink | `#151A28` | single-color mark on light surfaces |

## Layout

```
dailyhub-junction/
  00-previews/      presentation boards from the pack (design reference only)
  01-core-svg/      editable masters of the mark (colored, mono, ink, light)
  02-app-icons/     launcher/store PNGs 16–1024 px for three themes + Windows .ico
  03-notifications/ notification-type glyphs (default, unread, deadline, reminder,
                    activity, friend-request, update, completed), light + dark
  04-features/      navigation glyphs (tasks, projects, workouts, developer, friends,
                    calendar, settings, inbox, analytics, automation), light + dark
  05-wordmarks/     wide lockups for dark and light surfaces
  06-social/        repository/social preview
  07-guides/        palette tokens and usage rules
```

`00-previews/` keeps the pack's presentation boards as a visual record of the system.
They are renders of the same vectors — never ship or crop them; use the SVGs or a
generated icon size instead.

## Where the Android app uses it

| Resource | Source master | Notes |
| --- | --- | --- |
| `res/drawable/ic_launcher_foreground.xml` | `01-core-svg/dailyhub-dark.svg` | Mark only; the master's navy plate becomes the adaptive background. |
| `res/drawable/ic_launcher_background.xml` | `tokens.json` → `navy` | Flat `#111726`, no gradient — launchers may animate the layers independently. |
| `res/drawable/ic_launcher_monochrome.xml` | same geometry as the foreground | Android 13+ themed icons; the system supplies the color, so the layer is flat white. |
| `res/mipmap-anydpi-v26/ic_launcher*.xml` | — | Adaptive icon with foreground, background and monochrome layers. |

Geometry is kept in the master's 256-unit coordinate space and placed with a single
`<group>` transform (scale `0.28677`, translate `18.034, 16.675`). That reproduces the
pack's 62 % fill of the visible 72 dp square and keeps the whole mark inside the 66 dp
safe circle, so no launcher mask clips a track end. Use the same numbers if you
regenerate the vectors; do not nudge individual paths.

Only the launcher icon was changed. The app's Compose palette stays graphite/slate with a
single indigo accent on purpose — the brand colors are identity colors, not UI chrome, and
`ui/theme/AppColors.kt` deliberately forbids accent flooding. Replacing UI colors with the
lime/lavender/coral palette would be a redesign, not a brand integration.

The Android status-bar reminder icon (`res/drawable/ic_notification.xml`) stays the
conventional bell: at 18–24 dp, rendered as a single-color silhouette, the multi-stroke mark
stops resolving. The glyphs in `03-notifications/` are for in-app notification surfaces
(list rows, future activity feed), not for the system status bar.

## Rules

- Keep at least 12 % clear space around the mark; never add a container, outline or shadow.
- Minimum size for the colored mark is 24 dp / 24 px. Below that use a single-color variant.
- Do not re-color, re-order, rotate or separate the tracks, and do not detach the stem from
  the tracks' junction point.
- Do not substitute a bell or calendar for the mark: those are contextual pictograms.
- Wordmark SVGs rely on a system font stack (`Inter, system-ui`). Convert the lettering to
  outlines if you need byte-identical output on every machine.

## Provenance and legal note

The Junction identity was designed for DailyHub; the vectors here are the editable
originals from the supplied identity pack. The design is an original proposal, not a legal
guarantee of uniqueness. A limited web review considered productivity/software-logo
conventions and looked for similar multi-bar constructions, but that is not a worldwide
image search or trademark clearance. Before a public release in a new market, run
reverse-image similarity checks and search WIPO, TÜRKPATENT and the relevant national
registries. Stacked-bar motifs are common; distinctiveness comes from using the whole
combination — three unequal tracks, one stem, one detached diamond — consistently.
