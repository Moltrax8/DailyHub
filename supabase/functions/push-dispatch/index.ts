// DailyHub push-dispatch (Phase 7).
// Internal callee of github-webhook: { activity_id } → member tokens honoring
// notification_prefs → FCM HTTP v1 data message per device.
//
// Secrets (supabase secrets set): FCM_SERVICE_ACCOUNT (full service-account
// JSON), plus the standard SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY.
// Deploy: supabase functions deploy push-dispatch (JWT required — only the
// service role may call it).

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const SA_JSON = Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "";

const TEXT = { headers: { "Content-Type": "application/json" } };

function b64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const b64 = pem.replace(/-----(BEGIN|END)[^-]*-----/g, "").replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Short-lived Google OAuth2 access token for FCM v1 (RS256 service account). */
async function googleAccessToken(): Promise<string> {
  const sa = JSON.parse(SA_JSON);
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const assertion = `${header}.${claims}.${b64url(new Uint8Array(sig))}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=${encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer")}&assertion=${assertion}`,
  });
  if (!res.ok) throw new Error(`oauth2 token failed: ${res.status}`);
  const body = await res.json();
  return body.access_token as string;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "POST only" }), { status: 405, ...TEXT });
  }
  const { activity_id } = await req.json().catch(() => ({}));
  if (!activity_id) {
    return new Response(JSON.stringify({ error: "activity_id required" }), { status: 400, ...TEXT });
  }
  const supa = createClient(SUPABASE_URL, SERVICE_KEY);

  const { data: acts } = await supa.from("github_activity").select("*").eq("id", activity_id).limit(1);
  const act = acts?.[0];
  if (!act) {
    return new Response(JSON.stringify({ error: "activity not found" }), { status: 404, ...TEXT });
  }
  const spaceId: string = act.space_id;
  const kind: string = act.kind;

  const { data: members } = await supa.from("space_members").select("user_id").eq("space_id", spaceId);
  const userIds: string[] = (members ?? []).map((m: any) => m.user_id);
  if (userIds.length === 0) {
    return new Response(JSON.stringify({ ok: true, sent: 0 }), { status: 200, ...TEXT });
  }
  const { data: prefs } = await supa
    .from("notification_prefs")
    .select("user_id,enabled")
    .eq("space_id", spaceId)
    .eq("kind", kind)
    .in("user_id", userIds);
  const disabled = new Set((prefs ?? []).filter((p: any) => p.enabled === false).map((p: any) => p.user_id));
  const targets = userIds.filter((u) => !disabled.has(u));
  if (targets.length === 0) {
    return new Response(JSON.stringify({ ok: true, sent: 0 }), { status: 200, ...TEXT });
  }
  const { data: tokens } = await supa.from("fcm_tokens").select("token").in("user_id", targets);
  const deviceTokens: string[] = (tokens ?? []).map((t: any) => t.token).filter(Boolean);
  if (deviceTokens.length === 0) {
    return new Response(JSON.stringify({ ok: true, sent: 0 }), { status: 200, ...TEXT });
  }

  const projectId = JSON.parse(SA_JSON).project_id as string;
  const access = await googleAccessToken().catch(() => null);
  if (!access) {
    return new Response(JSON.stringify({ error: "fcm auth failed" }), { status: 500, ...TEXT });
  }
  let sent = 0;
  for (const token of deviceTokens) {
    const res = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${access}` },
        body: JSON.stringify({
          message: {
            token,
            data: {
              type: "github_activity",
              space_id: spaceId,
              activity_id: String(activity_id),
              kind,
              repo_full: String(act.repo_full ?? ""),
            },
          },
        }),
      },
    ).catch(() => null);
    if (res?.ok) sent++;
  }
  return new Response(JSON.stringify({ ok: true, sent }), { status: 200, ...TEXT });
});
