package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.Serializable

/** A GitHub release mapped to the fields the updater needs. */
@Serializable
data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String? = null,
    val publishedAt: String = "",
    val sha256: String? = null,
)

/**
 * Tag → version code (major*10000 + minor*100 + patch; null when unparsable/overflowing).
 * Since v1.1 the app's own versionCode follows this scheme
 * (v1.0 retrofitted as 10000), so plain integer comparison decides updates.
 */
fun versionCodeFromTag(tag: String): Int? {
    val m = Regex("""v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""").matchEntire(tag.trim()) ?: return null
    val major = m.groupValues[1].toIntOrNull() ?: return null
    val minor = m.groupValues[2].let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null }
    val patch = m.groupValues[3].let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null }
    if (major > 200 || minor > 99 || patch > 99) return null
    return major * 10000 + minor * 100 + patch
}

/** True when the release is strictly newer than the installed build. */
fun isUpdateAvailable(release: AppRelease, installedCode: Int): Boolean =
    release.versionCode > installedCode

/**
 * Whether the automatic "is there a new version?" check should run now. It runs every time the app is opened,
 * but never more than once per [minIntervalMs] (so quickly switching apps does not hammer the server).
 * [lastCheckAtMs] is null/0 before the first check. Pure (JVM-tested).
 */
fun shouldRunUpdateCheck(lastCheckAtMs: Long?, nowMs: Long, minIntervalMs: Long = 15 * 60 * 1000L): Boolean =
    lastCheckAtMs == null || lastCheckAtMs <= 0L || nowMs < lastCheckAtMs || nowMs - lastCheckAtMs >= minIntervalMs

/**
 * APK downloads are only trusted from GitHub's own hosts over HTTPS, so a tampered
 * release response cannot point users at an evil server. Pure (JVM-tested).
 */
fun isAllowedApkHost(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null || uri.port !in listOf(-1, 443)) return false
    return uri.host?.lowercase(java.util.Locale.ROOT) in setOf(
        "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com",
    )
}

/** Release metadata may only select an APK belonging to this application's repository. */
fun isDailyHubReleaseApk(url: String): Boolean {
    if (!isAllowedApkHost(url)) return false
    val uri = java.net.URI(url)
    return uri.host.equals("github.com", ignoreCase = true) &&
        uri.rawPath.startsWith("/Moltrax8/DailyHub/releases/download/") &&
        uri.rawPath.endsWith(".apk", ignoreCase = true) && uri.rawPath == uri.normalize().rawPath
}

/**
 * Maps GitHub's `GET /repos/{owner}/{repo}/releases/latest` JSON to an [AppRelease].
 * Null when the tag is unparsable, there is no .apk asset, or the asset
 * URL is not on an allowed GitHub host. Pure (JVM-tested).
 */
fun parseGithubLatestRelease(body: String): AppRelease? {
    val root = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
    }.getOrNull() ?: return null
    fun kotlinx.serialization.json.JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    if ((root["draft"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" ||
        (root["prerelease"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true") return null
    val tag = root.str("tag_name") ?: return null
    val code = versionCodeFromTag(tag) ?: return null
    val assets = root["assets"] as? kotlinx.serialization.json.JsonArray ?: return null
    val asset = assets.asSequence()
        .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        .filter { it.str("name")?.endsWith(".apk", ignoreCase = true) == true }
        .firstOrNull { it.str("browser_download_url")?.let(::isDailyHubReleaseApk) == true }
        ?: return null
    val apkUrl = asset.str("browser_download_url") ?: return null
    val digest = asset.str("digest")?.let {
        if (!Regex("sha256:[0-9a-fA-F]{64}").matches(it)) return null
        it.removePrefix("sha256:").lowercase(java.util.Locale.ROOT)
    }
    return AppRelease(
        versionName = tag.trim().removePrefix("v"),
        versionCode = code,
        apkUrl = apkUrl,
        notes = root.str("body")?.takeIf { it.isNotBlank() },
        publishedAt = root.str("published_at").orEmpty(),
        sha256 = digest,
    )
}
