package com.moltrax.personalnoteapp.data.remote.supabase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.repository.ProfileRepositoryImpl
import com.moltrax.personalnoteapp.di.SupabaseConfig
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import com.moltrax.personalnoteapp.domain.model.isValidUsername
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

/**
 * PREPARED — runs on the VM phone when the backend is ready. Requires:
 * 1. `supabase/migrations/001_phase3_profiles.sql` applied in the dashboard,
 * 2. Auth → Providers → Email → Confirm email OFF (dev project),
 * 3. network access from the emulator.
 *
 * Flow (real backend, throwaway user): signUp → profile upsert → fetch →
 * avatar upload → signOut. Skipped gracefully when unconfigured.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseProfileE2ETest {

    private lateinit var auth: SupabaseAuthService
    private lateinit var profiles: ProfileRepositoryImpl

    @Before
    fun buildAgainstRealBackend() {
        // Same BuildConfig keys the app uses (local.properties, git-ignored).
        val url = com.moltrax.personalnoteapp.BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = com.moltrax.personalnoteapp.BuildConfig.SUPABASE_ANON_KEY
        assumeTrue("Supabase keys missing", url.isNotBlank() && key.isNotBlank())

        val ctx: Context = ApplicationProvider.getApplicationContext()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("apikey", key).build())
            }
            .build()
        fun retrofit(base: String): Retrofit = Retrofit.Builder()
            .baseUrl(base)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val prefs = AppPreferences(ctx)
        val tokens = SupabaseTokenStore(ctx)
        auth = SupabaseAuthService(
            retrofit("$url/auth/v1/").create(SupabaseAuthApi::class.java),
            tokens,
            prefs,
        )
        profiles = ProfileRepositoryImpl(
            retrofit("$url/rest/v1/").create(SupabaseDbApi::class.java),
            auth,
            SupabaseConfig(url, key),
            http,
        )
    }

    @Test
    fun signup_profile_avatar_signout() {
        runBlocking {
            val stamp = System.currentTimeMillis() % 100000
            val email = "e2e$stamp@gmail.com"
            val username = "e2e$stamp"
            assertTrue(isValidUsername(username))

            val signup = auth.signUp(email, "pass1234")
            assertTrue(signup.exceptionOrNull()?.message ?: "signup ok", signup.isSuccess)
            val uid = auth.currentUserId()
            assertNotNull(uid)

            profiles.upsertMyProfile(SupabaseProfile(id = uid!!, username = username))
            assertEquals(username, profiles.getMyProfile(uid)?.username)

            val url = profiles.uploadAvatar(uid, ONE_PX_PNG, "png")
            assertTrue(url.contains("/avatars/$uid/avatar.png"))

            auth.signOut()
            assertEquals(null, auth.currentUserId())
            // NOTE: delete the e2e user in Dashboard → Authentication → Users (dev hygiene).
        }
    }

    private companion object {
        // 1x1 transparent PNG.
        val ONE_PX_PNG = byteArrayOf(
            137.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60,
            -119, 0, 0, 0, 13, 73, 68, 65, 84, 120, -100, 99, 96, 0, 0, 0,
            2, 0, 1, -27, 39, -4, -21, 0, 0, 0, 0, 73, 69, 78, 68,
            -82, 66, 96, -126,
        )
    }
}
