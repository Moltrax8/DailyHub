package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Public profile row (Phase 3, Supabase `profiles` table).
 * The username (never the email) is the public identifier.
 */
@Serializable
data class SupabaseProfile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * Username rules shared by sign-up validation and the editor (JVM-tested):
 * 3–20 chars, letters/digits/underscore/dot, must start with a letter/digit.
 */
fun isValidUsername(name: String): Boolean {
    if (name.length !in 3..20) return false
    if (!name[0].isLetterOrDigit()) return false
    return name.all { it.isLetterOrDigit() || it == '_' || it == '.' }
}
