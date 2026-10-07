package com.moltrax.personalnoteapp.domain.model

/**
 * Projects-list "Import from GitHub" helpers. Pure JVM logic (no Android
 * classes) so it stays unit-testable. The dialog/ViewModel in
 * `ui.screen.project` reuse these instead of duplicating name/selection
 * logic.
 */

/** Import target choice for the Projects-list GitHub import flow. */
enum class GithubImportTarget {
    /** Create one new project per selected repo. */
    NEW_PROJECT_EACH,

    /** Link every selected repo into one existing project. */
    EXISTING_PROJECT,
}

/**
 * Project name derived from a repo: the repo name without the owner.
 * "octocat/Hello-World" -> "Hello-World". Blank-safe: falls back to the
 * trimmed input so callers never get an empty name.
 */
fun importProjectName(fullName: String): String {
    val clean = fullName.trim()
    return clean.substringAfterLast('/').trim().ifBlank { clean }
}

/** Owner part of a repo ("octocat/Hello-World" -> "octocat", "" when absent). */
fun importRepoOwner(fullName: String): String {
    val clean = fullName.trim()
    if ('/' !in clean) return ""
    return clean.substringBeforeLast('/').trim()
}

/**
 * Deduplicates a derived project name against existing project names
 * (case-insensitive, trimmed). First collision appends the repo owner
 * ("API" -> "API (octocat)"), further collisions append a counter
 * ("API (octocat) 2", ...). Never returns blank.
 */
fun dedupeImportProjectName(
    base: String,
    owner: String,
    existing: Collection<String>,
): String {
    val taken = existing.map { it.trim().lowercase() }.toSet()
    val cleanBase = base.trim().ifBlank { owner.trim() }.ifBlank { "Repo" }
    if (cleanBase.lowercase() !in taken) return cleanBase
    val cleanOwner = owner.trim()
    val withOwner = if (cleanOwner.isNotEmpty()) "$cleanBase ($cleanOwner)" else cleanBase
    if (withOwner.lowercase() !in taken) return withOwner
    var n = 2
    while ("$withOwner $n".lowercase() in taken) n++
    return "$withOwner $n"
}

/**
 * Union of repo ids already linked to ANY project. Shown as "Already added"
 * and not selectable in the import picker.
 */
fun importAlreadyAddedIds(linkedPerProject: Collection<Collection<Long>>): Set<Long> =
    linkedPerProject.flatten().toSet()

/** Toggle helper for the multi-select checkbox state. */
fun toggleImportSelection(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id

/**
 * Selectable repos for import: the visible list minus already-added ones.
 * Already-added repos stay visible (with an "Already added" badge) but are
 * excluded from the confirm count.
 */
fun importSelectableRepos(
    repos: List<GithubAppRepo>,
    alreadyAdded: Set<Long>,
): List<GithubAppRepo> = repos.filter { it.id !in alreadyAdded }
