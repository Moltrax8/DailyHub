// DailyHub github-webhook (Phase 7).
// GitHub repo webhook → validate HMAC → map event to activity kind → look up
// the linked space → honor notification_prefs → insert github_activity →
// chain into push-dispatch for FCM delivery.
//
// Secrets (supabase secrets set): GITHUB_WEBHOOK_SECRET.
// Deploy: supabase functions deploy github-webhook --no-verify-jwt
// (GitHub signs with the shared secret, not a Supabase JWT).

import { createClient } from "npm:@supabase/supabase-js@2";

const WEBHOOK_SECRET = Deno.env.get("GITHUB_WEBHOOK_SECRET") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const TEXT = { headers: { "Content-Type": "application/json" } };

async function hmacValid(raw: string, signature: string | null): Promise<boolean> {
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
  return hex === signature.slice("sha256=".length);
}

type Mapped = { kind: string; repoFull: string; ref: Record<string, unknown> } | null;

/** The only repo whose releases may feed the in-app updater (the GitHub App webhook delivers every installed repo). */
const APP_REPO = "Moltrax8/DailyHub";

/** Tag -> version code, same scheme as the app and github-release-webhook (major*10000 + minor*100 + patch). */
function versionCodeFromTag(tag: string): number | null {
  const m = /^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?/.exec(tag.trim());
  if (!m) return null;
  const major = parseInt(m[1], 10);
  const minor = m[2] ? parseInt(m[2], 10) : 0;
  const patch = m[3] ? parseInt(m[3], 10) : 0;
  if (major > 200) return null;
  return major * 10000 + minor * 100 + patch;
}

/**
 * release/published of THIS app's repo -> app_releases (what the updater reads). The repo webhook only points
 * at this function, so github-release-webhook never received releases and the table stayed empty.
 * Best effort: never blocks the activity feed.
 */
async function recordAppRelease(supa: any, body: any): Promise<void> {
  try {
    const rel = body?.release;
    if (body?.repository?.full_name !== APP_REPO || rel?.draft || rel?.prerelease) return;
    const tag: string | undefined = rel?.tag_name;
    const code = tag ? versionCodeFromTag(tag) : null;
    const apk = (rel?.assets ?? []).find((a: any) => typeof a?.name === "string" && a.name.endsWith(".apk"));
    const apkUrl: string | undefined = apk?.browser_download_url;
    if (!tag || code === null || !apkUrl || !apkUrl.startsWith("https://github.com/")) return;
    await supa.from("app_releases").upsert(
      {
        version_name: tag,
        version_code: code,
        apk_url: apkUrl,
        notes: typeof rel?.body === "string" ? rel.body.slice(0, 2000) : null,
        published_at: rel?.published_at ?? new Date().toISOString(),
      },
      { onConflict: "version_code" },
    );
  } catch (_e) {
    // swallow: the release table is secondary to the activity feed
  }
}

/** First event slice (workflow_failed arrives in a later slice). */
function mapEvent(event: string, action: string | null, body: any): Mapped {
  const repoFull: string | undefined = body?.repository?.full_name;
  if (!repoFull) return null;
  const num = body?.issue?.number ?? body?.pull_request?.number ?? body?.release?.tag_name;
  const title = body?.issue?.title ?? body?.pull_request?.title ?? body?.release?.name;
  const sender: string | undefined = body?.sender?.login;
  const base = { number: num, title, sender };
  if (event === "issues" && action === "opened") {
    return { kind: "issue.opened", repoFull, ref: base };
  }
  if (event === "pull_request" && action === "opened") {
    return { kind: "pr.opened", repoFull, ref: base };
  }
  if (event === "issue_comment" && action === "created") {
    return { kind: "comment", repoFull, ref: { ...base, body: body?.comment?.body?.slice(0, 500) } };
  }
  if (event === "pull_request_review" && action === "submitted") {
    return {
      kind: "review",
      repoFull,
      ref: { ...base, state: body?.review?.state, body: body?.review?.body?.slice(0, 500) },
    };
  }
  if (
    (event === "issues" || event === "pull_request") &&
    (action === "closed" || action === "reopened")
  ) {
    return { kind: "state_changed", repoFull, ref: { ...base, action } };
  }
  if (event === "release" && action === "published") {
    return { kind: "release", repoFull, ref: { tag: body?.release?.tag_name, name: title, sender } };
  }
  return null;
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
  // Ping on webhook setup: acknowledge without inserting.
  if (event === "ping") {
    return new Response(JSON.stringify({ ok: true }), { status: 200, ...TEXT });
  }
  let body: any;
  try {
    body = JSON.parse(raw);
  } catch {
    return new Response(JSON.stringify({ error: "bad json" }), { status: 400, ...TEXT });
  }
  const supa = createClient(SUPABASE_URL, SERVICE_KEY);

  if (event === "release" && body?.action === "published") {
    await recordAppRelease(supa, body);
  }

  const mapped = mapEvent(event, body?.action ?? null, body);
  if (!mapped) {
    return new Response(JSON.stringify({ ok: true, skipped: true }), { status: 200, ...TEXT });
  }

  // Repo id → linked space.
  const repoId: number | undefined = body?.repository?.id;
  const { data: links, error: linkErr } = await supa
    .from("github_repos")
    .select("space_id")
    .eq("id", repoId)
    .limit(1);
  if (linkErr || !links || links.length === 0) {
    return new Response(JSON.stringify({ ok: true, skipped: true, reason: "repo not linked" }), {
      status: 200,
      ...TEXT,
    });
  }
  const spaceId: string = links[0].space_id;

  const { data: inserted, error: insErr } = await supa
    .from("github_activity")
    .insert({ space_id: spaceId, repo_full: mapped.repoFull, kind: mapped.kind, ref: mapped.ref })
    .select("id")
    .limit(1);
  if (insErr || !inserted || inserted.length === 0) {
    return new Response(JSON.stringify({ error: "insert failed" }), { status: 500, ...TEXT });
  }

  // Chain into push-dispatch (same project, service-role call).
  const dispatch = await fetch(`${SUPABASE_URL}/functions/v1/push-dispatch`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      apikey: SERVICE_KEY,
      Authorization: `Bearer ${SERVICE_KEY}`,
    },
    body: JSON.stringify({ activity_id: inserted[0].id }),
  }).catch(() => null);

  return new Response(
    JSON.stringify({ ok: true, activity_id: inserted[0].id, dispatched: dispatch?.ok ?? false }),
    { status: 200, ...TEXT },
  );
});
