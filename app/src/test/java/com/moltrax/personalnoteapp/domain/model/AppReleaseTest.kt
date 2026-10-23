package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tag → version-code scheme:
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
        // Scheme is enforced inside isAllowedApkHost: plain http never passes.
        assertFalse(isAllowedApkHost("http://github.com/a.apk"))
        assertFalse(isAllowedApkHost("not a url"))
    }

    @Test fun `github latest maps apk tag and notes`() {
        val parsed = parseGithubLatestRelease("""{"tag_name":"v1.2.3","body":"Fixes","published_at":"2026-10-23", "assets":[
            {"name":"sources.zip","browser_download_url":"https://github.com/src.zip"},
            {"name":"DailyHub.apk","browser_download_url":"https://github.com/Moltrax8/DailyHub/releases/download/v1.2.3/app.apk"}]}""")!!
        assertEquals(10203, parsed.versionCode)
        assertEquals("1.2.3", parsed.versionName)
        assertEquals("Fixes", parsed.notes)
    }
    @Test fun `missing malformed and untrusted releases are rejected`() {
        assertNull(parseGithubLatestRelease("not json"))
        assertNull(parseGithubLatestRelease("""{"tag_name":"v1.2.3","assets":[]}"""))
        assertNull(parseGithubLatestRelease("""{"tag_name":"v1.2.3","assets":[{"name":"x.apk","browser_download_url":"https://evil.com/x.apk"}]}"""))
    }
    @Test fun `automatic checks are throttled and tolerate clock reset`() {
        assertTrue(shouldRunUpdateCheck(null, 100))
        assertFalse(shouldRunUpdateCheck(100, 101))
        assertTrue(shouldRunUpdateCheck(100, 900100))
        assertTrue(shouldRunUpdateCheck(100, 10))
    }
    @Test fun `overflow and suffix tags are not stable releases`() {
        assertNull(versionCodeFromTag("v1.999999999999.0"))
        assertNull(versionCodeFromTag("v1.2.100"))
        assertNull(versionCodeFromTag("v1.2.3-beta"))
        assertNull(versionCodeFromTag("v1.2.3garbage"))
    }
    @Test fun `download URLs reject credentials ports fragments and foreign repos`() {
        assertFalse(isAllowedApkHost("https://user:pass@github.com/a.apk"))
        assertFalse(isAllowedApkHost("https://github.com:444/a.apk"))
        assertFalse(isAllowedApkHost("https://github.com/a.apk#fragment"))
        assertFalse(isAllowedApkHost("https://untrusted.github.com/a.apk"))
        assertFalse(isDailyHubReleaseApk("https://github.com/other/app/releases/download/v1/a.apk"))
        assertFalse(isDailyHubReleaseApk("https://github.com/Moltrax8/DailyHub/releases/download/../other/a.apk"))
        assertTrue(isDailyHubReleaseApk("https://github.com/Moltrax8/DailyHub/releases/download/v1.2.3/DailyHub.apk"))
    }
    @Test fun `draft prerelease and malformed checksum are rejected`() {
        val asset = """"assets":[{"name":"DailyHub.apk","browser_download_url":"https://github.com/Moltrax8/DailyHub/releases/download/v1.2.3/DailyHub.apk"}]"""
        assertNull(parseGithubLatestRelease("""{"tag_name":"v1.2.3","draft":true,$asset}"""))
        assertNull(parseGithubLatestRelease("""{"tag_name":"v1.2.3","prerelease":true,$asset}"""))
        assertNull(parseGithubLatestRelease("""{"tag_name":"v1.2.3",${asset.replace("\"name\":", "\"digest\":\"sha256:bad\",\"name\":")}}"""))
        val digest = "a".repeat(64)
        val parsed = parseGithubLatestRelease("""{"tag_name":"v1.2.3",${asset.replace("\"name\":", "\"digest\":\"sha256:$digest\",\"name\":")}}""")!!
        assertEquals(digest, parsed.sha256)
    }
}
