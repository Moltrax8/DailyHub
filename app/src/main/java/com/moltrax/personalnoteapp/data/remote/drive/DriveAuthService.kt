package com.moltrax.personalnoteapp.data.remote.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.moltrax.personalnoteapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val driveScope = Scope(BuildConfig.DRIVE_SCOPE)

    // No requestServerAuthCode needed on Android — GoogleAuthUtil.getToken
    // fetches the OAuth token directly on the device.
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(driveScope)
        .build()

    val signInClient: GoogleSignInClient get() = GoogleSignIn.getClient(context, gso)

    val signInIntent: Intent get() = signInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(): Boolean = getLastSignedInAccount()?.let {
        GoogleSignIn.hasPermissions(it, driveScope)
    } ?: false

    // Scope string for GoogleAuthUtil: "oauth2:<full-scope-url>"
    private val tokenScope = "oauth2:${BuildConfig.DRIVE_SCOPE}"

    suspend fun getAccessToken(account: Account): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account, tokenScope)
    }

    /**
     * Returns the current access token. Returns null when not signed in; if fetching the token
     * fails (consent required, network, etc.) the exception is rethrown UP — the real cause is
     * never swallowed silently, so it can be shown in the UI.
     */
    suspend fun getFreshToken(): String? {
        val account = getLastSignedInAccount()?.account ?: return null
        return getAccessToken(account)
    }

    /**
     * Returns the Intent that opens the consent screen when user approval is required for
     * Drive access; null when consent was already granted. Called after sign-in in the login
     * flow so the sensitive `drive.appdata` permission is definitely requested.
     */
    suspend fun getConsentIntentOrNull(): Intent? = withContext(Dispatchers.IO) {
        val account = getLastSignedInAccount()?.account ?: return@withContext null
        try {
            GoogleAuthUtil.getToken(context, account, tokenScope)
            null
        } catch (e: UserRecoverableAuthException) {
            e.intent
        }
    }

    suspend fun signOut() {
        signInClient.signOut()
    }
}
