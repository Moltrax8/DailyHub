package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Exercise
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeGroups(): Flow<List<WorkoutGroup>>
    suspend fun upsertGroup(group: WorkoutGroup)
    suspend fun deleteGroup(id: String)
    suspend fun saveSession(session: WorkoutSession)
    suspend fun deleteSession(id: String)
    suspend fun getSessions(): List<WorkoutSession>
    /** ALL sessions for sync — including deleted (tombstone) records. */
    suspend fun getSessionsForSync(): List<WorkoutSession>
    suspend fun getSessionById(id: String): WorkoutSession?
    /** The latest completed session linked to a sport task (if any). */
    suspend fun getLatestSessionForTask(taskId: String): WorkoutSession?

    // Bulk access for sync
    suspend fun getGroups(): List<WorkoutGroup>
    /** ALL groups for sync — including deleted (tombstone) records. */
    suspend fun getGroupsForSync(): List<WorkoutGroup>
    suspend fun replaceGroups(groups: List<WorkoutGroup>)
    suspend fun replaceSessions(sessions: List<WorkoutSession>)

    // Exercise database
    fun observeExercises(): Flow<List<Exercise>>
    suspend fun upsertExercise(exercise: Exercise)
    suspend fun searchExercises(query: String): List<Exercise>
    suspend fun getExercisesByBodyPart(bodyPart: String): List<Exercise>

    /**
     * Deletes local demo media files and records of cached moves no longer used by any workout
     * (orphans). Called after a move/workout/program is deleted; prevents device storage bloat.
     */
    suspend fun cleanupOrphanedExerciseMedia()
}
