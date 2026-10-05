package com.moltrax.personalnoteapp

/**
 * Drive sync status.
 *
 * [DRIVE_SYNC_ENABLED] is ON: release SHA-1 is registered on the release
 * OAuth client and the debug SHA-1 on the debug OAuth client, so both
 * release and debug builds should sign in. If debug sign-in still fails
 * (ApiException 10), the debug fingerprint never landed on the debug
 * client — re-check Cloud Console → Credentials → that client's SHA-1 list.
 */
object FeatureFlags {
    const val DRIVE_SYNC_ENABLED = true

    /**
     * Supabase managed-account layer (Phase 3+: auth, profiles, friends, spaces,
     * projects, GitHub, auto-update). Independent from [DRIVE_SYNC_ENABLED]:
     * both can be on/off separately. Requires SUPABASE_URL/ANON_KEY in
     * git-ignored local.properties (see local.properties.example).
     */
    const val SUPABASE_ENABLED = true
}
