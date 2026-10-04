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
```

## Deploy

```powershell
supabase functions deploy github-webhook --no-verify-jwt
supabase functions deploy push-dispatch
# Phase 9 (later):
# supabase functions deploy github-release-webhook --no-verify-jwt
```

`--no-verify-jwt` is REQUIRED on `github-webhook`: GitHub signs with the
shared webhook secret, not a Supabase JWT. `push-dispatch` keeps JWT
verification (only the service role calls it internally).

## GitHub repo → Settings → Webhooks → Add webhook

- Payload URL: `https://mhpwatvnvdrvviqifzkv.supabase.co/functions/v1/github-webhook`
- Content type: `application/json`
- Secret: same value as `GITHUB_WEBHOOK_SECRET`
- Events: let me select individual events → Issues, Pull requests,
  Issue comments, Pull request reviews, Releases
- Active: on. Use Redeliver to replay while testing.

## Testing without GitHub (HMAC self-check)

```powershell
$secret = "test-secret"
$body = '{"repository":{"id":1,"full_name":"o/r"},"action":"opened","issue":{"number":1,"title":"t"},"sender":{"login":"u"}}'
# compute sha256=... with any tool, then:
# POST to the function URL with x-hub-signature-256 + x-github-event: issues
```

## Logs

Dashboard → Edge Functions → function → Logs (or `supabase functions logs`).
