package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutEntity
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutExerciseEntity
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutGroupEntity
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // Groups — visible queries exclude tombstones (isDeleted = 1).
    @Query("SELECT * FROM workout_groups WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeGroups(): Flow<List<WorkoutGroupEntity>>

    @Query("SELECT * FROM workout_groups WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllGroups(): List<WorkoutGroupEntity>

    /** ALL groups for sync — including deleted (tombstone) records. */
    @Query("SELECT * FROM workout_groups ORDER BY createdAt DESC")
    suspend fun getAllGroupsRaw(): List<WorkoutGroupEntity>

    @Upsert suspend fun upsertGroup(group: WorkoutGroupEntity)

    /**
     * Soft delete (tombstone): the group record is kept but marked isDeleted=1 + updatedAt is refreshed.
     * Child workout/exercise records are also cleaned up (no children of the deleted group remain).
     */
    @Transaction
    suspend fun softDeleteGroup(id: String, now: Long) {
        markGroupDeleted(id, now)
        // Detach sport links of tasks bound to the group (no dangling linkedWorkoutId/ProgramId left);
        // updatedAt is refreshed so the detachment propagates via sync. Must run BEFORE children are deleted
        // (the subquery uses the group's workouts).
        unlinkTasksFromGroup(id, now)
        unlinkTasksFromProgram(id, now)
        deleteWorkoutsForGroup(id)
    }

    @Query("UPDATE tasks SET linkedWorkoutId = NULL, updatedAt = :now WHERE linkedWorkoutId IN (SELECT id FROM workouts WHERE groupId = :groupId)")
    suspend fun unlinkTasksFromGroup(groupId: String, now: Long)

    @Query("UPDATE tasks SET linkedProgramId = NULL, updatedAt = :now WHERE linkedProgramId = :groupId")
    suspend fun unlinkTasksFromProgram(groupId: String, now: Long)

    @Query("UPDATE workout_groups SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markGroupDeleted(id: String, now: Long)

    @Query("DELETE FROM workout_groups")
    suspend fun deleteAllGroups()

    /**
     * Atomic group replacement: delete all groups + write the merged list in a single transaction.
     * An interruption preserves the old data; otherwise the next sync would push the empty list to the backup.
     * Thanks to CASCADE, deleteAllGroups also clears child workout/exercise records.
     */
    @Transaction
    suspend fun replaceGroupsAtomic(
        groups: List<WorkoutGroupEntity>,
        workouts: List<WorkoutEntity>,
        exercises: List<WorkoutExerciseEntity>,
    ) {
        deleteAllGroups()
        groups.forEach { upsertGroup(it) }
        upsertWorkouts(workouts)
        upsertWorkoutExercises(exercises)
    }

    /**
     * Replaces a group and all its child records (workout + exercise) in a single transaction.
     * Being atomic, the reactive Flow only emits the final consistent state (no flicker/intermediate state).
     * When old workouts are deleted, related workout_exercises records are also cleared via CASCADE.
     */
    @Transaction
    suspend fun upsertGroupWithChildren(
        group: WorkoutGroupEntity,
        workouts: List<WorkoutEntity>,
        exercises: List<WorkoutExerciseEntity>,
    ) {
        upsertGroup(group)
        deleteWorkoutsForGroup(group.id)
        upsertWorkouts(workouts)
        upsertWorkoutExercises(exercises)
    }

    // Workouts
    @Query("SELECT * FROM workouts WHERE groupId = :groupId ORDER BY orderIndex")
    suspend fun getWorkoutsForGroup(groupId: String): List<WorkoutEntity>

    @Query("SELECT * FROM workouts ORDER BY orderIndex")
    fun observeAllWorkouts(): Flow<List<WorkoutEntity>>

    @Upsert suspend fun upsertWorkout(workout: WorkoutEntity)
    @Upsert suspend fun upsertWorkouts(workouts: List<WorkoutEntity>)
    @Query("DELETE FROM workouts WHERE groupId = :groupId")
    suspend fun deleteWorkoutsForGroup(groupId: String)

    // Exercises within workouts
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY orderIndex")
    suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises ORDER BY orderIndex")
    fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseEntity>>

    /** Distinct movement ids still used in any workout (for orphaned-media cleanup). */
    @Query("SELECT DISTINCT exerciseId FROM workout_exercises")
    suspend fun getReferencedExerciseIds(): List<String>

    @Upsert suspend fun upsertWorkoutExercise(exercise: WorkoutExerciseEntity)
    @Upsert suspend fun upsertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)

    // Sessions
    @Upsert suspend fun upsertSession(session: WorkoutSessionEntity)
    @Upsert suspend fun upsertSessions(sessions: List<WorkoutSessionEntity>)
    // Visible queries exclude tombstones (isDeleted = 1); sync uses the raw list.
    @Query("SELECT * FROM workout_sessions WHERE isDeleted = 0 ORDER BY startedAt DESC")
    suspend fun getSessions(): List<WorkoutSessionEntity>

    /** ALL sessions for sync — including deleted (tombstone) records. */
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    suspend fun getSessionsRaw(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND isDeleted = 0")
    suspend fun getSessionById(id: String): WorkoutSessionEntity?

    /** The latest completed session linked to a sport task (to open the completed task's summary). */
    @Query("SELECT * FROM workout_sessions WHERE taskId = :taskId AND isDeleted = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSessionForTask(taskId: String): WorkoutSessionEntity?

    /**
     * Soft delete (tombstone): the session record is kept but marked isDeleted=1, so the deletion
     * propagates via Drive sync and is not resurrected from remote.
     */
    @Query("UPDATE workout_sessions SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteSession(id: String)
    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    /**
     * Atomic session replacement: delete + batch write in a single transaction (rationale above).
     */
    @Transaction
    suspend fun replaceSessionsAtomic(sessions: List<WorkoutSessionEntity>) {
        deleteAllSessions()
        upsertSessions(sessions)
    }
}
