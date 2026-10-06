# DailyHub — Deployment / Verification Checklist

Code-complete in the repo is **not** the same as launched. This list tracks
everything that still needs credentials, consoles, SQL, or a physical
device/emulator. Check items off only with real evidence (dashboard
screenshot, device run, passing CI).

## CI (no credentials needed)

- [x] `:app:assembleDebug` green
- [x] `:app:testDebugUnitTest` green (domain/database/network JVM tests)
- [x] `:app:lintDebug` green (`app/lint.xml` carries the Media3 opt-in)
- [x] `:app:assembleDebugAndroidTest` green (incl. `RootNavigationUiTest`)
- [x] `:app:assembleRelease` green (R8 + resource shrink, unsigned locally)
- [ ] GitHub Actions run of `.github/workflows/ci.yml` green on `main`

## Supabase (needs project credentials — NOT validated here)

`local.properties` on this machine has no `SUPABASE_URL`/`SUPABASE_ANON_KEY`,
so everything below is untested against a live backend:

- [ ] Create/confirm the Supabase project; store URL + **anon** key only in
      `local.properties` (never commit; never use `service_role` in the app)
- [ ] Apply pending migrations in `supabase/` (incl. `007_phase9_releases.sql`)
      via the dashboard SQL editor, in order
- [ ] Dashboard → Auth → URL Configuration: Site URL =
      `dailyhub://auth/callback`, plus listed under Redirect URLs (otherwise
      confirm-email links die in the browser)
- [ ] Confirm-email OFF for device testing (or test the callback flow)
- [ ] Deploy Edge Function `github-release-webhook` + set its secret
- [ ] Run the Supabase E2E androidTests on a VM phone (`supabase/` suite,
      social/spaces/projects/GitHub/update)

## Google / Drive (console part done for release)

- [x] Release SHA-1 (`93:CD:…:CA:6C`) registered on OAuth client
      `144662485161-…tg0d`; debug SHA-1 (`E5:E3:…:D3:C8`) registered on debug
      OAuth client `144662485161-…pp8e`; `DRIVE_SYNC_ENABLED = true`
- [ ] Verify on a **debug** build on device: sign-in → Drive consent → sync
- [ ] Verify on a **release** build on device: same flow
- [ ] `google-services.json` present for FCM (consumed at build; push-to-
      project-open flow still needs a device run)

## GitHub integration (needs device + token — NOT validated here)

- [ ] Per-project connection + repo picker on a signed-in device
- [ ] Issues/PR/activity feed rendering with long labels
- [ ] Notification preferences round-trip

## Manual visual QA (needs emulator/device — NOT done here)

Fresh install + populated + loading + error states; light/dark/system +
font-scale up; keyboard-open forms; long titles; many categories/projects;
Duo Hub with content in all 7 capabilities; widget add/complete/undo;
notification + exact-alarm + unknown-sources update flows; offline;
Drive-disabled and Supabase-unconfigured startups; logout/account-expiry;
corrupt workout JSON import; failed avatar upload; interrupted update.

## Release signing

- [ ] `RELEASE_*` entries in a private `local.properties`; never commit keys
- [ ] Version bump follows the tag scheme (`versionCode = major*10000 +
      minor*100 + patch`, APK named `DailyHub-v{version}.apk`)
