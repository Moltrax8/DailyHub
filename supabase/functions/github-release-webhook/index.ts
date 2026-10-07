// DailyHub github-release-webhook (Phase 9).
// GitHub repo webhook (Releases only) → validate HMAC → accept
// release/published → upsert the version row in app_releases.
// The app learns via Realtime postgres_changes (open) or a single Supabase
// fetch at startup (closed) — it never polls GitHub.
//
// Secrets (supabase secrets set): GITHUB_WEBHOOK_SECRET (same value as the
// Phase 7 secret — one shared GitHub webhook secret for the project).
// Deploy: supabase functions deploy github-release-webhook --no-verify-jwt
// (GitHub signs with the shared secret, not a Supabase JWT).
// GitHub repo → Settings → Webhooks: SAME webhook entry as Phase 7 needs
// Releases checked (issues/PRs/comments/reviews already covered there).

import { createClient } from "npm:@supabase/supabase-js@2";

const WEBHOOK_SECRET = Deno.env.get("GITHUB_WEBHOOK_SECRET") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const TEXT = { headers: { "Content-Type": "application/json" } };

/** Only this repo's releases may feed the in-app updater (an App webhook can deliver any installed repo). */
const APP_REPO = "Moltrax8/DailyHub";

/** Constant-time string compare (length mismatch -> false). */
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function hmacValid(raw: string, signature: string | null): Promise<boolean> {
  // Fail closed: with an empty secret anyone could compute a valid HMAC.
  if (!WEBHOOK_SECRET) return false;
  if (!signature || !signature.startsWith("sha256=")) return false;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(WEBHOOK_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(raw));
  const hex = [...new Uint8Array(mac)].map((b) => b.toString(16).padStart(2, "0")).join("");
  return constantTimeEqual(hex, signature.slice("sha256=".length));
}

function apkAssetUrl(body: any): string | null {
  const assets: any[] = body?.release?.assets ?? [];
  // Prefer a signed -ish release APK; fall back to the first .apk asset.
  const pick =
    assets.find((a) => typeof a?.name === "string" && a.name.endsWith(".apk")) ?? null;
  const url: string | undefined =
    pick?.browser_download_url ?? assets?.[0]?.browser_download_url;
  return url ?? null;
}

function versionCodeFromTag(tag: string): number | null {
  // v1.2.3 / 1.2.3 / v12 → best-effort integer (major*10000 + minor*100 + patch).
  const m = tag.trim().replace(/^v/i, "").match(/^(\d+)(?:\.(\d+))?(?:\.(\d+))?/);
  if (!m) return null;
  const major = parseInt(m[1] ?? "0", 10);
  const minor = parseInt(m[2] ?? "0", 10);
  const patch = parseInt(m[3] ?? "0", 10);
  if (major > 200) return null; // overflow guard, still fits int comfortably
  return major * 10000 + minor * 100 + patch;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "POST only" }), { status: 405, ...TEXT });
  }
  const raw = await req.text();
  const signature = req.headers.get("x-hub-signature-256");
  if (!(await hmacValid(raw, signature))) {
    return new Response(JSON.stringify({ error: "bad signature" }), { status: 401, ...TEXT });
  }
  const event = req.headers.get("x-github-event") ?? "";
  if (event === "ping") {
    return new Response(JSON.stringify({ ok: true }), { status: 200, ...TEXT });
  }
  if (event !== "release") {
    return new Response(JSON.stringify({ ok: true, skipped: true }), { status: 200, ...TEXT });
  }
  let body: any;
  try {
    body = JSON.parse(raw);
  } catch {
    return new Response(JSON.stringify({ error: "bad json" }), { status: 400, ...TEXT });
  }
  if (body?.action !== "published") {
    return new Response(JSON.stringify({ ok: true, skipped: true }), { status: 200, ...TEXT });
  }
  if (body?.repository?.full_name !== APP_REPO || body?.release?.draft || body?.release?.prerelease) {
    return new Response(JSON.stringify({ ok: true, skipped: true, reason: "not this app's stable release" }), {
      status: 200,
      ...TEXT,
    });
  }

  const tag: string | undefined = body?.release?.tag_name;
  const apkUrl = apkAssetUrl(body);
  const code = tag ? versionCodeFromTag(tag) : null;
  if (!tag || !apkUrl || !apkUrl.startsWith("https://github.com/") || code === null) {
    return new Response(JSON.stringify({ ok: true, skipped: true, reason: "no apk asset" }), {
      status: 200,
      ...TEXT,
    });
  }

  const supa = createClient(SUPABASE_URL, SERVICE_KEY);
  const { error } = await supa.from("app_releases").upsert(
    {
      version_name: tag,
      version_code: code,
      apk_url: apkUrl,
      notes: body?.release?.body?.slice(0, 2000) ?? null,
      published_at: body?.release?.published_at ?? new Date().toISOString(),
    },
    { onConflict: "version_code" },
  );
  if (error) {
    return new Response(JSON.stringify({ error: "upsert failed" }), { status: 500, ...TEXT });
  }
  return new Response(JSON.stringify({ ok: true, version_code: code }), { status: 200, ...TEXT });
});
