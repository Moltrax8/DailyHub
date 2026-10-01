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

    // Android'de requestServerAuthCode gerekmez — GoogleAuthUtil.getToken
    // cihaz üzerinde doğrudan OAuth token alır.
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

    // GoogleAuthUtil için scope string'i: "oauth2:<tam-scope-url>"
    private val tokenScope = "oauth2:${BuildConfig.DRIVE_SCOPE}"

    suspend fun getAccessToken(account: Account): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account, tokenScope)
    }

    /**
     * Geçerli erişim jetonunu döner. Giriş yapılmamışsa null döner; jeton alınırken hata
     * oluşursa (izin gerekiyor, ağ, vb.) istisnayı YUKARI fırlatır — gerçek neden sessizce
     * yutulmaz, böylece arayüzde gösterilebilir.
     */
    suspend fun getFreshToken(): String? {
        val account = getLastSignedInAccount()?.account ?: return null
        return getAccessToken(account)
    }

    /**
     * Drive erişimi için kullanıcı onayı gerekiyorsa onay ekranını açacak Intent'i döner;
     * onay zaten verilmişse null. Giriş akışında, sign-in sonrası çağrılır ki hassas
     * `drive.appdata` izni mutlaka istensin.
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
