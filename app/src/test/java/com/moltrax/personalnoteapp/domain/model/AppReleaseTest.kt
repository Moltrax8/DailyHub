package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tag → version-code scheme (Phase 9, mirrored by the webhook function):
 * major*10000 + minor*100 + patch. The app's own versionCode follows it
 * (v1.0 == 10000), so plain integer comparison decides updates.
 */
class AppReleaseTest {

    @Test
    fun `tags parse to codes`() {
        assertEquals(10000, versionCodeFromTag("v1.0"))
        assertEquals(10000, versionCodeFromTag("1.0"))
        assertEquals(10100, versionCodeFromTag("v1.1"))
        assertEquals(10203, versionCodeFromTag("1.2.3"))
        assertEquals(120000, versionCodeFromTag("v12"))
    }

    @Test
    fun `garbage and overflow return null`() {
        assertNull(versionCodeFromTag(""))
        assertNull(versionCodeFromTag("beta"))
        assertNull(versionCodeFromTag("v999.0"))
    }

    @Test
    fun `strictly newer wins`() {
        val installed = 10000
        assertTrue(isUpdateAvailable(AppRelease(versionName = "v1.1", versionCode = 10100, apkUrl = "https://x"), installed))
        assertFalse(isUpdateAvailable(AppRelease(versionName = "v1.0", versionCode = 10000, apkUrl = "https://x"), installed))
        assertFalse(isUpdateAvailable(AppRelease(versionName = "v0.9", versionCode = 900, apkUrl = "https://x"), installed))
    }

    @Test
    fun `only github hosts trusted for apk`() {
        assertTrue(isAllowedApkHost("https://github.com/Moltrax8/DailyHub/releases/download/v1.1.1/a.apk"))
        assertTrue(isAllowedApkHost("https://objects.githubusercontent.com/abc/a.apk?token=x"))
        assertTrue(isAllowedApkHost("https://release-assets.githubusercontent.com/abc/a.apk"))
        assertFalse(isAllowedApkHost("https://evil.com/a.apk"))
        assertFalse(isAllowedApkHost("https://github.com.evil.com/a.apk"))
        // Scheme is enforced separately in downloadApk; the allowlist is host-only.
        assertTrue(isAllowedApkHost("http://github.com/a.apk"))
        assertFalse(isAllowedApkHost("not a url"))
    }
}
