package com.moltrax.personalnoteapp.data.remote.supabase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.repository.UpdateRepository
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.di.SupabaseConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Context
import androidx.test.core.app.ApplicationProvider

/**
 * PREPARED — runs on the VM phone when the backend is ready. Requires:
 * 1. `007_phase9_releases.sql` applied,
 * 2. a published GitHub release (fires the real webhook → seeds the row),
 * 3. network access from the emulator.
 *
 * Reads the latest row publicly (signed-out, like the real startup check)
 * and asserts the update decision against the installed versionCode.
 * Skipped gracefully when unconfigured or when no release exists yet.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseUpdateE2ETest {

    @Test
    fun startupCheck_readsPublicRow() {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        assumeTrue("Supabase keys missing", url.isNotBlank() && key.isNotBlank())

        val ctx: Context = ApplicationProvider.getApplicationContext()
        val repo = UpdateRepository(
            OkHttpClient(),
            SupabaseConfig(url, key),
            RealtimeClient(OkHttpClient(), SupabaseConfig(url, key)),
            AppPreferences(ctx),
        )
        runBlocking {
            val latest = repo.checkNow()
            // No release published yet → null path (no prompt, no crash).
            if (latest == null) return@runBlocking
            assertNotNull(latest.apkUrl)
            assertTrue(latest.apkUrl.startsWith("https://"))
            // Decision is a strict integer comparison either way.
            assertEquals(
                latest.versionCode > BuildConfig.VERSION_CODE,
                repo.isNewerThanInstalled(latest),
            )
        }
    }
}
