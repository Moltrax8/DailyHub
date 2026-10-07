package com.moltrax.personalnoteapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoardAndUpdateTest {

    // Regression: server values are "Planned"/"Developing"/"Finished"; a case-sensitive valueOf() mapped them
    // all to IDEA, so a moved card always came back in the Idea column.
    @Test fun projectStatusOf_mapsServerValues() {
        assertEquals(ProjectStatus.IDEA, projectStatusOf("Idea"))
        assertEquals(ProjectStatus.PLANNED, projectStatusOf("Planned"))
        assertEquals(ProjectStatus.DEVELOPING, projectStatusOf("Developing"))
        assertEquals(ProjectStatus.FINISHED, projectStatusOf("Finished"))
    }

    @Test fun projectStatusOf_isCaseInsensitiveAndSafe() {
        assertEquals(ProjectStatus.FINISHED, projectStatusOf("FINISHED"))
        assertEquals(ProjectStatus.PLANNED, projectStatusOf(" planned "))
        assertEquals(ProjectStatus.IDEA, projectStatusOf(null))
        assertEquals(ProjectStatus.IDEA, projectStatusOf("nonsense"))
    }

    private val goodJson = """
        {"tag_name":"v1.1.5","body":"Notes here","published_at":"2026-10-07T10:00:00Z",
         "assets":[{"name":"checksums.txt","browser_download_url":"https://github.com/x/y/releases/download/v1.1.5/checksums.txt"},
                   {"name":"DailyHub-v1.1.5.apk","browser_download_url":"https://github.com/Moltrax8/DailyHub/releases/download/v1.1.5/DailyHub-v1.1.5.apk"}]}
    """.trimIndent()

    @Test fun parseGithubLatestRelease_readsTagApkAndNotes() {
        val r = parseGithubLatestRelease(goodJson)
        assertNotNull(r)
        assertEquals("1.1.5", r!!.versionName)
        assertEquals(10105, r.versionCode)
        assertEquals("https://github.com/Moltrax8/DailyHub/releases/download/v1.1.5/DailyHub-v1.1.5.apk", r.apkUrl)
        assertEquals("Notes here", r.notes)
    }

    @Test fun parseGithubLatestRelease_rejectsUntrustedHostNoApkAndGarbage() {
        val evil = goodJson.replace("https://github.com/Moltrax8", "https://evil.example.com/Moltrax8")
        assertNull(parseGithubLatestRelease(evil))
        assertNull(parseGithubLatestRelease("""{"tag_name":"v1.1.5","assets":[]}"""))
        assertNull(parseGithubLatestRelease("""{"tag_name":"nightly","assets":[]}"""))
        assertNull(parseGithubLatestRelease("not json"))
    }
}
