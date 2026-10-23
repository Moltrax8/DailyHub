package com.moltrax.personalnoteapp.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Exercise streaming and cleanup without accessing GitHub or launching an installer. */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val url = "https://github.com/Moltrax8/DailyHub/releases/download/v1.2.3/test.apk"

    private fun repository(body: ResponseBody) = UpdateRepository(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build(), AppPreferences(context),
    )

    @Test fun validChecksumStreamsFileAndReportsProgress() = runBlocking {
        val bytes = "test update content".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val file = File(context.cacheDir, "download-test.apk")
        var progress = 0L
        try {
            repository(bytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                .downloadApk(url, file, digest) { done, _ -> progress = done }
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(bytes.size.toLong(), progress)
        } finally { file.delete() }
    }

    @Test fun mismatchAndEmptyBodyDeletePartialFile() = runBlocking {
        val file = File(context.cacheDir, "download-bad-test.apk")
        try {
            try {
                repository("tampered".toResponseBody()).downloadApk(url, file, "0".repeat(64))
                fail("A bad checksum must fail")
            } catch (_: IOException) { assertFalse(file.exists()) }
            try {
                repository("".toResponseBody()).downloadApk(url, file)
                fail("An empty APK must fail")
            } catch (_: IOException) { assertFalse(file.exists()) }
        } finally { file.delete() }
    }

    @Test fun foreignRepositoryRejectedBeforeDownload() = runBlocking {
        val file = File(context.cacheDir, "download-untrusted-test.apk")
        try {
            repository("x".toResponseBody()).downloadApk("https://github.com/other/app/releases/download/v1/a.apk", file)
            fail("A foreign repository must fail")
        } catch (_: IllegalArgumentException) { assertFalse(file.exists()) }
    }
}
