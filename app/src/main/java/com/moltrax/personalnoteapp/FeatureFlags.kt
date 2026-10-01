package com.moltrax.personalnoteapp

/**
 * Temporary feature flags.
 *
 * [DRIVE_SYNC_ENABLED] is OFF for now: the release APK's signing SHA-1 is not registered in
 * Google Cloud Console (OAuth client not verified), so Google sign-in failed and the app could
 * not be entered at all. While this flag is off:
 *  - the app skips the login screen and opens directly on the home screen,
 *  - Google Drive sync and the account/backup section in settings are hidden.
 *
 * Related code (LoginScreen, AuthViewModel, Drive services, SyncRepository, SyncWorker)
 * stays in place; setting the flag back to `true` after the SHA-1 registration re-enables it.
 */
object FeatureFlags {
    const val DRIVE_SYNC_ENABLED = false
}
