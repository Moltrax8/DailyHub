package com.moltrax.personalnoteapp.di

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the update check: the shared client is used for
 * `GET api.github.com/repos/.../releases/latest`, and `api.github.com` is not an
 * APK host. A network interceptor pinning the base client to APK hosts made every
 * check fail locally with "Refusing untrusted update host" (v1.2.0–v1.3.0).
 *
 * The APK download path has its own guarded client inside UpdateRepository.
 */
class NetworkModuleTest {

    @Test
    fun `base client is not pinned to apk download hosts`() {
        val client = NetworkModule.provideOkHttp()
        assertTrue(
            "Base client must stay generic; add the APK host guard to the download client only.",
            client.networkInterceptors.isEmpty(),
        )
    }
}
