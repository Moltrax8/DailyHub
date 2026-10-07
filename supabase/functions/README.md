# DailyHub Edge Functions (Phases 7 + 9)

## One-time setup (your machine, Supabase CLI)

```powershell
npm i -g supabase
supabase login
supabase link --project-ref mhpwatvnvdrvviqifzkv
```

## Secrets (Dashboard → Project Settings → Edge Functions → Secrets, or CLI)

```powershell
# Phase 7: GitHub webhook shared secret (also pasted into GitHub below)
supabase secrets set GITHUB_WEBHOOK_SECRET=paste_random_32_chars
# Phase 7: FCM service-account JSON (Firebase Console → Project settings →
# Service accounts → Generate new private key), single line:
supabase secrets set FCM_SERVICE_ACCOUNT='{"type":"service_account",...}'
# GitHub account linking (GitHub App user-to-server flow):
supabase secrets set GITHUB_APP_CLIENT_ID=paste_github_app_client_id
supabase secrets set GITHUB_APP_CLIENT_SECRET=paste_github_app_client_secret
# Random 32+ char secret used only to sign/verify the OAuth `state` token:
supabase secrets set GITHUB_STATE_SECRET=paste_random_32_chars
```

## Deploy

```powershell
supabase functions deploy github-webhook --no-verify-jwt
supabase functions deploy push-dispatch
supabase functions deploy github-connect
supabase functions deploy github-repos
# Phase 9 (later):
# supabase functions deploy github-release-webhook --no-verify-jwt
```

`--no-verify-jwt` is REQUIRED on `github-webhook`: GitHub signs with the
shared webhook secret, not a Supabase JWT. `push-dispatch`,
`github-connect`, and `github-repos` keep JWT verification ON (never deploy
them with `--no-verify-jwt`): Android calls them with the user's Supabase
JWT in `Authorization: Bearer`, and `push-dispatch` is called only by the
service role internally.

## GitHub repo → Settings → Webhooks → Add webhook

- Payload URL: `https://mhpwatvnvdrvviqifzkv.supabase.co/functions/v1/github-webhook`
- Content type: `application/json`
- Secret: same value as `GITHUB_WEBHOOK_SECRET`
- Events: let me select individual events → Issues, Pull requests,
  Issue comments, Pull request reviews, Releases
- Active: on. Use Redeliver to replay while testing.

## GitHub App for account linking (one app-level webhook covers all repos)

With the GitHub App, one app-level webhook covers all installed repos — no
per-repo webhook setup is needed. Create the App once, then users install
it only on the repos they choose.

Step-by-step (github.com, logged in as the App owner):

1. Profile photo → Settings → Developer settings → GitHub Apps → New GitHub App.
2. GitHub App name: `DailyHub` (must be globally unique on GitHub; if taken,
   use `DailyHub-<yourname>` and note the final slug for the install URL).
3. Homepage URL: your project/site URL (e.g. the repo URL).
4. Callback URL: `dailyhub://github-callback` (exact value; the Android app
   handles this custom scheme and forwards `code` + `state` to
   `github-connect` with `{"action":"finish",...}`).
5. Check ON: "Request user authorization (OAuth) during installation".
6. Webhook → Active: on.
   - Webhook URL: `https://mhpwatvnvdrvviqifzkv.supabase.co/functions/v1/github-webhook`
   - Webhook secret: same value as `GITHUB_WEBHOOK_SECRET`.
7. Repository permissions (read-only least privilege):
   - Contents: Read-only (needed for releases).
   - Issues: Read-only.
   - Pull requests: Read-only.
   - Metadata: Read-only (mandatory, automatic).
   - Everything else: No access.
8. Subscribe to events: Issues, Pull request, Issue comment,
   Pull request review, Release.
9. "Where can this GitHub App be installed?": Any account (so other users
   can install it on their repos; "Only on this account" limits it to you).
10. Create GitHub App. On the App settings page note the Client ID →
    `GITHUB_APP_CLIENT_ID`; Generate a new client secret →
    `GITHUB_APP_CLIENT_SECRET`.
11. Install the App (Install App → choose account → Only select repositories
    → pick repos → Install). The install page URL is
    `https://github.com/apps/<slug>/installations/new` (`github-repos`
    returns it as `install_url`).
12. Set the three new secrets (above) and deploy `github-connect` +
    `github-repos` (above). Do NOT commit secrets to git. Tokens stay
    server-side in the locked-down `github_tokens` table (migration
    `008_github_link.sql`; service role only) — they never reach the phone.

## Testing without GitHub (HMAC self-check)

```powershell
$secret = "test-secret"
$body = '{"repository":{"id":1,"full_name":"o/r"},"action":"opened","issue":{"number":1,"title":"t"},"sender":{"login":"u"}}'
# compute sha256=... with any tool, then:
# POST to the function URL with x-hub-signature-256 + x-github-event: issues
```

## Logs

Dashboard → Edge Functions → function → Logs (or `supabase functions logs`).

## Webhook secrets (two webhooks -> one function)
`github-webhook` accepts signatures from BOTH GitHub webhooks, each with its own secret:
- the GitHub App's webhook -> `GITHUB_WEBHOOK_SECRET` (the secret typed into the App settings), and
- the repo-level webhook on `Moltrax8/DailyHub` -> `GITHUB_REPO_WEBHOOK_SECRET` (set on the repo hook via
  `gh api -X PATCH repos/Moltrax8/DailyHub/hooks/<id> -f "config[secret]=..."`).
Changing only one side makes every delivery fail with 401 (this broke the repo webhook for a day, so releases were
never recorded into `app_releases`). To resend a failed event: `gh api -X POST repos/<owner>/<repo>/hooks/<hook>/deliveries/<delivery>/attempts`.
