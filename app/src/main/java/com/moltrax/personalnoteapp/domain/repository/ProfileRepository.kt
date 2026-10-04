package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import kotlinx.coroutines.flow.Flow

/**
 * Own profile (Phase 3). Search/friends arrive in Phase 4.
 */
interface ProfileRepository {
    /** Own profile, null when never created. Errors (e.g. offline) throw. */
    suspend fun getMyProfile(userId: String): SupabaseProfile?

    /** Creates or replaces the own row (username unique, enforced server-side). */
    suspend fun upsertMyProfile(profile: SupabaseProfile)

    /** Uploads `avatars/<uid>/<file>` and returns its public URL. */
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, extension: String): String

    /** Signed-in state helper for UI gating. */
    fun isSignedIn(): Flow<Boolean>
}
