// DailyHub github-connect (GitHub account linking).
// Links a GitHub account to the already signed-in (Google) user. GitHub is
// NEVER a login method here — the caller's Supabase JWT is verified and the
// GitHub tokens live only on the server (github_tokens table, service role).
//
// POST JSON with field `action` (Supabase JWT in Authorization: Bearer):
//   {"action":"start"} ->
//     {"url":"<GitHub authorize URL>"} (state = HMAC-signed, 10-min, user-bound)
//   {"action":"finish","code":"...","state":"..."} ->
//     verifies state, exchanges the code (GitHub App user-to-server flow),
//     stores tokens server-side, upserts github_connections -> {"login":"..."}
//   {"action":"disconnect"} ->
//     deletes stored tokens + connection row -> {"ok":true}
//
// Redirect after authorize: custom scheme `dailyhub://github-callback`
// (set as the callback URL on the GitHub App).
//
// Secrets (supabase secrets set): GITHUB_APP_CLIENT_ID,
// GITHUB_APP_CLIENT_SECRET, GITHUB_STATE_SECRET, plus the standard
// SUPABASE_URL / SUPABASE_ANON_KEY / SUPABASE_SERVICE_ROLE_KEY.
// Deploy: supabase functions deploy github-connect (JWT verification stays
// ON — never deploy with --no-verify-jwt).

import { createClient } from "npm:@supabase/supabase-js@2";

const CLIENT_ID = Deno.env.get("GITHUB_APP_CLIENT_ID") ?? "";
const CLIENT_SECRET = Deno.env.get("GITHUB_APP_CLIENT_SECRET") ?? "";
const STATE_SECRET = Deno.env.get("GITHUB_STATE_SECRET") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const TEXT = { headers: { "Content-Type": "application/json" } };
const STATE_TTL_SECONDS = 10 * 60;

function b64urlEncodeText(s: string): string {
  const bytes = new TextEncoder().encode(s);
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function b64urlDecodeText(s: string): string {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return new TextDecoder().decode(out);
}

function b64urlEncodeBytes(bytes: Uint8Array): string {
  let bin = "";
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Constant-time string compare (length mismatch -> false). */
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

async function hmacSign(data: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(STATE_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  return b64urlEncodeBytes(new Uint8Array(mac));
}

/** state = b64url("uid:exp") + "." + b64url(HMAC(state_secret, data_part)). */
async function signState(uid: string): Promise<string> {
  const exp = Math.floor(Date.now() / 1000) + STATE_TTL_SECONDS;
  const data = b64urlEncodeText(`${uid}:${exp}`);
  const sig = await hmacSign(data);
  return `${data}.${sig}`;
}

async function verifyState(state: string, expectedUid: string): Promise<boolean> {
  const parts = state.split(".");
  if (parts.length !== 2 || !parts[0] || !parts[1]) return false;
  const [data, sig] = parts;
  const expectedSig = await hmacSign(data);
  if (!constantTimeEqual(sig, expectedSig)) return false;
  let raw: string;
  try {
    raw = b64urlDecodeText(data);
  } catch {
    return false;
  }
  const sep = raw.lastIndexOf(":");
  if (sep < 0) return false;
  const uid = raw.slice(0, sep);
  const exp = parseInt(raw.slice(sep + 1), 10);
  if (!uid || !Number.isFinite(exp)) return false;
  if (!constantTimeEqual(uid, expectedUid)) return false;
  if (Math.floor(Date.now() / 1000) > exp) return false;
  return true;
}

/** Verify the Supabase JWT via Auth and return the calling user id. */
async function getUserId(req: Request): Promise<string | null> {
  const auth = req.headers.get("Authorization") ?? "";
  const m = auth.match(/^Bearer\s+(.+)$/i);
  if (!m) return null;
  const jwt = m[1].trim();
  if (!jwt || !SUPABASE_URL || !ANON_KEY) return null;
  const supa = createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });
  const { data, error } = await supa.auth.getUser(jwt);
  if (error || !data?.user) return null;
  return data.user.id;
}

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, ...TEXT });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json(405, { error: "POST only" });
  }
  if (!CLIENT_ID || !CLIENT_SECRET || !STATE_SECRET || !SUPABASE_URL || !SERVICE_KEY) {
    return json(500, { error: "server_misconfigured" });
  }
  const userId = await getUserId(req);
  if (!userId) {
    return json(401, { error: "unauthorized" });
  }

  const body = await req.json().catch(() => null) as {
    action?: string;
    code?: string;
    state?: string;
  } | null;
  const action = body?.action;
  if (!action) {
    return json(400, { error: "action required" });
  }

  const admin = createClient(SUPABASE_URL, SERVICE_KEY);

  // ---- start: issue a signed authorize URL ----
  if (action === "start") {
    const state = await signState(userId);
    const url = `https://github.com/login/oauth/authorize?client_id=${encodeURIComponent(CLIENT_ID)}&state=${encodeURIComponent(state)}`;
    return json(200, { url });
  }

  // ---- finish: verify state, exchange code, store tokens ----
  if (action === "finish") {
    const code = body?.code;
    const state = body?.state;
    if (!code || !state) {
      return json(400, { error: "code and state required" });
    }
    if (!(await verifyState(state, userId))) {
      return json(400, { error: "bad state" });
    }

    const tokenRes = await fetch("https://github.com/login/oauth/access_token", {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ client_id: CLIENT_ID, client_secret: CLIENT_SECRET, code }),
    }).catch(() => null);
    if (!tokenRes || !tokenRes.ok) {
      return json(502, { error: "exchange_failed" });
    }
    const tokenBody = await tokenRes.json().catch(() => null) as {
      access_token?: string;
      refresh_token?: string;
      expires_in?: number;
      scope?: string;
      token_type?: string;
      error?: string;
    } | null;
    const accessToken = tokenBody?.access_token;
    if (!accessToken) {
      return json(502, { error: "exchange_failed" });
    }
    const refreshToken: string | null = tokenBody?.refresh_token ?? null;
    const expiresIn: number = typeof tokenBody?.expires_in === "number"
      ? tokenBody.expires_in as number
      : 8 * 3600;
    const scope: string = tokenBody?.scope ?? "";
    const tokenType: string = tokenBody?.token_type ?? "bearer";
    const expiresAt = new Date(Date.now() + expiresIn * 1000).toISOString();

    const userRes = await fetch("https://api.github.com/user", {
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${accessToken}`,
        "User-Agent": "DailyHub",
        "X-GitHub-Api-Version": "2022-11-28",
      },
    }).catch(() => null);
    if (!userRes || !userRes.ok) {
      return json(502, { error: "github_user_failed" });
    }
    const ghUser = await userRes.json().catch(() => null) as {
      login?: string;
      id?: number;
    } | null;
    const login = ghUser?.login;
    const githubUserId = ghUser?.id;
    if (!login || typeof githubUserId !== "number") {
      return json(502, { error: "github_user_failed" });
    }

    const { error: tokErr } = await admin.from("github_tokens").upsert(
      {
        user_id: userId,
        access_token: accessToken,
        refresh_token: refreshToken,
        expires_at: expiresAt,
        scope,
        token_type: tokenType,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id" },
    );
    if (tokErr) {
      return json(500, { error: "store_failed" });
    }

    const { error: connErr } = await admin.from("github_connections").upsert(
      {
        user_id: userId,
        github_login: login,
        github_user_id: githubUserId,
        connected_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      },
      { onConflict: "user_id" },
    );
    if (connErr) {
      return json(500, { error: "store_failed" });
    }

    return json(200, { login });
  }

  // ---- disconnect: delete tokens + connection row ----
  if (action === "disconnect") {
    await admin.from("github_tokens").delete().eq("user_id", userId);
    await admin.from("github_connections").delete().eq("user_id", userId);
    return json(200, { ok: true });
  }

  return json(400, { error: "unknown action" });
});
