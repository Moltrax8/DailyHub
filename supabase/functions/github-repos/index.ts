// DailyHub github-repos (GitHub account linking).
// Lists repos the linked GitHub account can access through the GitHub App
// installation(s), so the user picks repos by name (no numeric ID entry).
// Private repos are included when the App is installed on them.
//
// GET or POST with the user's Supabase JWT in `Authorization: Bearer`.
// Success: {"login":"octocat","repos":[{"id":123,"full_name":"owner/name",
//   "private":true,"fork":false,"description":"..."}],
//   "install_url":"https://github.com/apps/<slug>/installations/new"}
// No connection: 404 {"error":"not_connected"}
//
// Tokens stay server-side (github_tokens, service role); expiring tokens are
// refreshed here. Tokens are never logged and never returned to the client.
//
// Secrets (supabase secrets set): GITHUB_APP_CLIENT_ID,
// GITHUB_APP_CLIENT_SECRET, plus the standard SUPABASE_URL /
// SUPABASE_ANON_KEY / SUPABASE_SERVICE_ROLE_KEY. Optional: GITHUB_APP_SLUG
// (used for install_url when the installations API does not report one).
// Deploy: supabase functions deploy github-repos (JWT verification stays ON).

import { createClient } from "npm:@supabase/supabase-js@2";

const CLIENT_ID = Deno.env.get("GITHUB_APP_CLIENT_ID") ?? "";
const CLIENT_SECRET = Deno.env.get("GITHUB_APP_CLIENT_SECRET") ?? "";
const APP_SLUG = Deno.env.get("GITHUB_APP_SLUG") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const TEXT = { headers: { "Content-Type": "application/json" } };

type Repo = {
  id: number;
  full_name: string;
  private: boolean;
  fork: boolean;
  description: string | null;
};

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

function ghHeaders(token: string): Record<string, string> {
  return {
    Accept: "application/vnd.github+json",
    Authorization: `Bearer ${token}`,
    "User-Agent": "DailyHub",
    "X-GitHub-Api-Version": "2022-11-28",
  };
}

function mapRepo(r: any): Repo | null {
  if (typeof r?.id !== "number" || typeof r?.full_name !== "string") return null;
  return {
    id: r.id,
    full_name: r.full_name,
    private: r.private === true,
    fork: r.fork === true,
    description: typeof r.description === "string" ? r.description : null,
  };
}

/** Attempt a user-token refresh; returns the new row fields or null. */
async function tryRefresh(refreshToken: string): Promise<{
  access_token: string;
  refresh_token: string | null;
  expires_at: string;
  scope: string;
} | null> {
  const res = await fetch("https://github.com/login/oauth/access_token", {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      grant_type: "refresh_token",
      refresh_token: refreshToken,
    }),
  }).catch(() => null);
  if (!res || !res.ok) return null;
  const body = await res.json().catch(() => null) as {
    access_token?: string;
    refresh_token?: string;
    expires_in?: number;
    scope?: string;
  } | null;
  if (!body?.access_token) return null;
  const expiresIn = typeof body.expires_in === "number" ? body.expires_in : 8 * 3600;
  return {
    access_token: body.access_token,
    refresh_token: body.refresh_token ?? null,
    expires_at: new Date(Date.now() + expiresIn * 1000).toISOString(),
    scope: body.scope ?? "",
  };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET" && req.method !== "POST") {
    return json(405, { error: "GET or POST only" });
  }
  if (!CLIENT_ID || !CLIENT_SECRET || !SUPABASE_URL || !SERVICE_KEY) {
    return json(500, { error: "server_misconfigured" });
  }
  const userId = await getUserId(req);
  if (!userId) {
    return json(401, { error: "unauthorized" });
  }

  const admin = createClient(SUPABASE_URL, SERVICE_KEY);

  const { data: tokenRows } = await admin
    .from("github_tokens")
    .select("access_token,refresh_token,expires_at,scope")
    .eq("user_id", userId)
    .limit(1);
  const tokenRow = tokenRows?.[0] as {
    access_token: string;
    refresh_token: string | null;
    expires_at: string | null;
    scope: string;
  } | undefined;

  const { data: connRows } = await admin
    .from("github_connections")
    .select("github_login")
    .eq("user_id", userId)
    .limit(1);
  const login = (connRows?.[0] as { github_login?: string } | undefined)?.github_login;

  if (!tokenRow?.access_token || !login) {
    return json(404, { error: "not_connected" });
  }

  let accessToken: string = tokenRow.access_token;

  // Refresh when expired or expiring within 60s.
  const expiresMs = tokenRow.expires_at ? Date.parse(tokenRow.expires_at) : NaN;
  if (Number.isFinite(expiresMs) && Date.now() > expiresMs - 60_000) {
    if (!tokenRow.refresh_token) {
      return json(404, { error: "not_connected" });
    }
    const refreshed = await tryRefresh(tokenRow.refresh_token);
    if (!refreshed) {
      return json(404, { error: "not_connected" });
    }
    accessToken = refreshed.access_token;
    await admin.from("github_tokens").update({
      access_token: refreshed.access_token,
      refresh_token: refreshed.refresh_token ?? tokenRow.refresh_token,
      expires_at: refreshed.expires_at,
      scope: refreshed.scope,
      updated_at: new Date().toISOString(),
    }).eq("user_id", userId);
  }

  // List via App installations (paginate); fall back to /user/repos.
  const repos: Repo[] = [];
  let appSlug = APP_SLUG;

  const instRes = await fetch("https://api.github.com/user/installations?per_page=100", {
    headers: ghHeaders(accessToken),
  }).catch(() => null);

  let usedInstallations = false;
  if (instRes?.ok) {
    const instBody = await instRes.json().catch(() => null) as {
      installations?: Array<{ id: number; app_slug?: string }>;
    } | null;
    const installations = instBody?.installations ?? [];
    for (const inst of installations) {
      if (!appSlug && typeof inst?.app_slug === "string") appSlug = inst.app_slug;
      if (typeof inst?.id !== "number") continue;
      for (let page = 1; page <= 10; page++) {
        const rRes = await fetch(
          `https://api.github.com/user/installations/${inst.id}/repositories?per_page=100&page=${page}`,
          { headers: ghHeaders(accessToken) },
        ).catch(() => null);
        if (!rRes || !rRes.ok) break;
        const rBody = await rRes.json().catch(() => null) as {
          repositories?: unknown[];
        } | null;
        const list = rBody?.repositories ?? [];
        if (list.length === 0) break;
        for (const r of list) {
          const mapped = mapRepo(r);
          if (mapped) repos.push(mapped);
        }
        usedInstallations = true;
        if (list.length < 100) break;
      }
    }
  }

  async function persistRefresh(refreshed: {
    access_token: string;
    refresh_token: string | null;
    expires_at: string;
    scope: string;
  }): Promise<void> {
    accessToken = refreshed.access_token;
    await admin.from("github_tokens").update({
      access_token: refreshed.access_token,
      refresh_token: refreshed.refresh_token ?? tokenRow.refresh_token,
      expires_at: refreshed.expires_at,
      scope: refreshed.scope,
      updated_at: new Date().toISOString(),
    }).eq("user_id", userId);
  }

  if (!usedInstallations) {
    for (let page = 1; page <= 10; page++) {
      const pageUrl =
        `https://api.github.com/user/repos?per_page=100&page=${page}&sort=updated`;
      let rRes = await fetch(pageUrl, { headers: ghHeaders(accessToken) }).catch(() => null);
      if (!rRes) {
        return json(502, { error: "github_failed" });
      }
      if (rRes.status === 401) {
        // Token may have just expired: try one refresh, then retry this page.
        if (tokenRow.refresh_token) {
          const refreshed = await tryRefresh(tokenRow.refresh_token);
          if (!refreshed) return json(404, { error: "not_connected" });
          await persistRefresh(refreshed);
          rRes = await fetch(pageUrl, { headers: ghHeaders(accessToken) }).catch(() => null);
          if (!rRes) {
            return json(502, { error: "github_failed" });
          }
          if (rRes.status === 401) {
            return json(404, { error: "not_connected" });
          }
        } else {
          return json(404, { error: "not_connected" });
        }
      }
      if (!rRes.ok) {
        return json(502, { error: "github_failed" });
      }
      const list = await rRes.json().catch(() => null) as unknown[] | null;
      if (!list || list.length === 0) break;
      for (const r of list) {
        const mapped = mapRepo(r);
        if (mapped) repos.push(mapped);
      }
      if (list.length < 100) break;
    }
  }

  const installUrl = appSlug
    ? `https://github.com/apps/${appSlug}/installations/new`
    : "https://github.com/settings/installations";

  return json(200, { login, repos, install_url: installUrl });
});
