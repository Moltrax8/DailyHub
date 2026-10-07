package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of Supabase `app_releases` (Phase 9). Written only by the
 * github-release-webhook function; read publicly, pushed via Realtime.
 */
@Serializable
data class AppRelease(
    val id: String = "",
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("apk_url") val apkUrl: String,
    val notes: String? = null,
    @SerialName("published_at") val publishedAt: String = "",
)

/**
 * Tag → version code, mirroring the webhook function exactly
 * (major*10000 + minor*100 + patch; null when unparsable/overflowing).
 * Since v1.1 the app's own versionCode follows this scheme
 * (v1.0 retrofitted as 10000), so plain integer comparison decides updates.
 */
fun versionCodeFromTag(tag: String): Int? {
    val m = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(tag.trim()) ?: return null
    val major = m.groupValues[1].toIntOrNull() ?: return null
    val minor = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
    val patch = m.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
    if (major > 200) return null
    return major * 10000 + minor * 100 + patch
}

/** True when the release is strictly newer than the installed build. */
fun isUpdateAvailable(release: AppRelease, installedCode: Int): Boolean =
    release.versionCode > installedCode

/**
 * APK downloads are only trusted from GitHub's own hosts over HTTPS, so a tampered
 * `app_releases` row cannot point users at an evil server. Pure (JVM-tested).
 */
fun isAllowedApkHost(url: String): Boolean {
    if (!url.startsWith("https://")) return false
    val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    if (host.isBlank()) return false
    return host == "github.com" || host.endsWith(".github.com") ||
        host == "objects.githubusercontent.com" ||
        host == "release-assets.githubusercontent.com"
}

/**
 * Maps GitHub's `GET /repos/{owner}/{repo}/releases/latest` JSON to an [AppRelease] for the manual
 * "Check for updates" fallback. Null when the tag is unparsable, there is no .apk asset, or the asset
 * URL is not on an allowed GitHub host. Pure (JVM-tested).
 */
fun parseGithubLatestRelease(body: String): AppRelease? {
    val root = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
    }.getOrNull() ?: return null
    fun kotlinx.serialization.json.JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    val tag = root.str("tag_name") ?: return null
    val code = versionCodeFromTag(tag) ?: return null
    val assets = root["assets"] as? kotlinx.serialization.json.JsonArray ?: return null
    val apkUrl = assets.asSequence()
        .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        .filter { it.str("name")?.endsWith(".apk", ignoreCase = true) == true }
        .mapNotNull { it.str("browser_download_url") }
        .firstOrNull { isAllowedApkHost(it) && it.startsWith("https://") }
        ?: return null
    return AppRelease(
        versionName = tag.trim().removePrefix("v"),
        versionCode = code,
        apkUrl = apkUrl,
        notes = root.str("body")?.takeIf { it.isNotBlank() },
        publishedAt = root.str("published_at").orEmpty(),
    )
}
