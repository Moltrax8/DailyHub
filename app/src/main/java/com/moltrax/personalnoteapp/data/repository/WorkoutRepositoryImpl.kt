package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.local.db.dao.ExerciseDao
import com.moltrax.personalnoteapp.data.local.db.dao.WorkoutDao
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutGroupEntity
import com.moltrax.personalnoteapp.data.local.db.entity.toDomain
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.data.local.storage.ExerciseVideoStorage
import com.moltrax.personalnoteapp.domain.model.Exercise
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val videoStorage: ExerciseVideoStorage,
) : WorkoutRepository {

    // Listens reactively to all three tables (groups, workouts, workout_exercises); whenever
    // anything is inserted/deleted the Flow re-emits and the UI updates instantly.
    override fun observeGroups(): Flow<List<WorkoutGroup>> =
        combine(
            workoutDao.observeGroups(),
            workoutDao.observeAllWorkouts(),
            workoutDao.observeAllWorkoutExercises(),
        ) { groups, allWorkouts, allExercises ->
            val workoutsByGroup = allWorkouts.groupBy { it.groupId }
            val exercisesByWorkout = allExercises.groupBy { it.workoutId }
            groups.map { groupEntity ->
                val workouts = workoutsByGroup[groupEntity.id].orEmpty()
                    .sortedBy { it.orderIndex }
                    .map { workoutEntity ->
                        val exercises = exercisesByWorkout[workoutEntity.id].orEmpty()
                            .sortedBy { it.orderIndex }
                            .map { it.toDomain() }
                        workoutEntity.toDomain(exercises)
                    }
                groupEntity.toDomain(workouts)
            }
        }

    // User edit: bump updatedAt to now (LWW). Every change, including adding/removing
    // workouts, goes through this path, so a deleted workout never comes back via sync.
    override suspend fun upsertGroup(group: WorkoutGroup) =
        persistGroup(group.copy(updatedAt = System.currentTimeMillis()))

    /** Writes the group and its children PRESERVING the given timestamps (sync merge). */
    private suspend fun persistGroup(group: WorkoutGroup) {
        val workoutEntities = group.workouts.mapIndexed { idx, workout ->
            workout.toEntity(group.id, idx)
        }
        val exerciseEntities = group.workouts.flatMap { workout ->
            workout.exercises.map { it.toEntity(workout.id) }
        }
        workoutDao.upsertGroupWithChildren(group.toEntity(), workoutEntities, exerciseEntities)
    }

    // Soft delete (tombstone) — so sync does not resurrect the deleted group. Since deleting
    // a group also drops its child moves, clean up now-unused demo media files in passing.
    override suspend fun deleteGroup(id: String) {
        workoutDao.softDeleteGroup(id, System.currentTimeMillis())
        cleanupOrphanedExerciseMedia()
    }

    override suspend fun saveSession(session: WorkoutSession) = workoutDao.upsertSession(session.toEntity())

    // Soft delete (tombstone) — so sync does not resurrect the deleted session.
    override suspend fun deleteSession(id: String) = workoutDao.softDeleteSession(id)

    override suspend fun getSessions(): List<WorkoutSession> =
        workoutDao.getSessions().map { it.toDomain() }

    override suspend fun getSessionsForSync(): List<WorkoutSession> =
        workoutDao.getSessionsRaw().map { it.toDomain() }

    override suspend fun getSessionById(id: String): WorkoutSession? =
        workoutDao.getSessionById(id)?.toDomain()

    override suspend fun getLatestSessionForTask(taskId: String): WorkoutSession? =
        workoutDao.getLatestSessionForTask(taskId)?.toDomain()

    override suspend fun getGroups(): List<WorkoutGroup> =
        workoutDao.getAllGroups().map { it.withChildren() }

    // All groups including tombstones. Deleted groups have no children, so they come with an empty list.
    override suspend fun getGroupsForSync(): List<WorkoutGroup> =
        workoutDao.getAllGroupsRaw().map { groupEntity ->
            if (groupEntity.isDeleted) groupEntity.toDomain(emptyList())
            else groupEntity.withChildren()
        }

    private suspend fun WorkoutGroupEntity.withChildren(): WorkoutGroup {
        val workouts = workoutDao.getWorkoutsForGroup(id).map { workoutEntity ->
            val exercises = workoutDao.getExercisesForWorkout(workoutEntity.id).map { it.toDomain() }
            workoutEntity.toDomain(exercises)
        }
        return toDomain(workouts)
    }

    override suspend fun replaceGroups(groups: List<WorkoutGroup>) {
        // Write merged groups PRESERVING timestamps (do not bump) — otherwise every pull would
        // reset all updatedAt values to now and break LWW and tombstones.
        // Single transaction: an interruption keeps the old data (see replaceGroupsAtomic).
        val groupEntities = groups.map { it.toEntity() }
        val workoutEntities = groups.flatMap { group ->
            group.workouts.mapIndexed { idx, workout -> workout.toEntity(group.id, idx) }
        }
        val exerciseEntities = groups.flatMap { group ->
            group.workouts.flatMap { workout -> workout.exercises.map { it.toEntity(workout.id) } }
        }
        workoutDao.replaceGroupsAtomic(groupEntities, workoutEntities, exerciseEntities)
    }

    override suspend fun replaceSessions(sessions: List<WorkoutSession>) {
        workoutDao.replaceSessionsAtomic(sessions.map { it.toEntity() })
    }

    override fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun upsertExercise(exercise: Exercise) = exerciseDao.upsert(exercise.toEntity())

    override suspend fun searchExercises(query: String): List<Exercise> =
        exerciseDao.search(query).map { it.toDomain() }

    override suspend fun getExercisesByBodyPart(bodyPart: String): List<Exercise> =
        exerciseDao.getByBodyPart(bodyPart).map { it.toDomain() }

    // Reference counting: exerciseIds still present in workout_exercises are "live". The remaining
    // cached moves are orphans — first delete physical media files (all extension variants),
    // then the DB record. File I/O is always on the IO dispatcher (independent of the caller).
    override suspend fun cleanupOrphanedExerciseMedia() {
        withContext(Dispatchers.IO) {
            val referenced = workoutDao.getReferencedExerciseIds().toSet()
            exerciseDao.getAll().forEach { ex ->
                if (ex.id !in referenced) {
                    videoStorage.deleteVariants(ex.id)
                    exerciseDao.deleteById(ex.id)
                }
            }
        }
    }
}
