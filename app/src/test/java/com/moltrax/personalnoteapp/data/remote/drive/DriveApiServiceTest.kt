package com.moltrax.personalnoteapp.data.remote.drive

import com.moltrax.personalnoteapp.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Regression: with the narrow drive.appdata scope, a first sync (no backup file yet) must NOT fall
 * back to searching the full "drive" space, which Google rejects with
 * "HTTP 403 The granted scopes do not give access to all of the requested spaces".
 */
class DriveApiServiceTest {

    private val requested = mutableListOf<String>()

    private fun fakeClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            requested += url
            val json = "application/json".toMediaType()
            if (url.contains("spaces=appDataFolder")) {
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("""{"files":[]}""".toResponseBody(json)).build()
            } else {
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(403).message("Forbidden")
                    .body(
                        """{"error":{"message":"The granted scopes do not give access to all of the requested spaces."}}"""
                            .toResponseBody(json),
                    ).build()
            }
        })
        .build()

    @Test
    fun firstSyncWithAppDataScope_returnsNull_withoutQueryingFullDrive() = runBlocking {
        assumeTrue(BuildConfig.DRIVE_SCOPE.contains("appdata")) // scope is configurable in local.properties
        val file = DriveApiService(fakeClient()).findOrNull("token")
        assertNull(file)
        assertEquals(1, requested.size)
        assert(requested.single().contains("spaces=appDataFolder"))
    }
}
