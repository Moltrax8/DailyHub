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

    // Groups — görünür sorgular mezar taşlarını (isDeleted = 1) hariç tutar.
    @Query("SELECT * FROM workout_groups WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeGroups(): Flow<List<WorkoutGroupEntity>>

    @Query("SELECT * FROM workout_groups WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllGroups(): List<WorkoutGroupEntity>

    /** Senkronizasyon için TÜM gruplar — silinmiş (mezar taşı) kayıtlar dahil. */
    @Query("SELECT * FROM workout_groups ORDER BY createdAt DESC")
    suspend fun getAllGroupsRaw(): List<WorkoutGroupEntity>

    @Upsert suspend fun upsertGroup(group: WorkoutGroupEntity)

    /**
     * Yumuşak silme (mezar taşı): grup kaydı saklanır ama isDeleted=1 + updatedAt güncellenir.
     * Alt antrenman/hareket kayıtları ayrıca temizlenir (silinen grubun çocuğu kalmaz).
     */
    @Transaction
    suspend fun softDeleteGroup(id: String, now: Long) {
        markGroupDeleted(id, now)
        // Gruba bağlı görevlerin spor bağlarını kopar (sarkan linkedWorkoutId/ProgramId kalmasın);
        // updatedAt tazelenir ki kopuş sync ile yayılsın. Çocuklar silinmeden ÖNCE çalışmalıdır
        // (alt sorgu grubun workout'larını kullanır).
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
     * Atomik grup değiştirme: tüm grupları sil + birleşmiş listeyi tek transaction içinde yaz.
     * Yarıda kesilme eski veriyi korur; aksi halde sonraki sync boş listeyi yedeğe basardı.
     * CASCADE sayesinde deleteAllGroups alt workout/exercise kayıtlarını da temizler.
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
     * Grubu ve tüm alt kayıtlarını (workout + exercise) tek bir transaction içinde değiştirir.
     * Atomik olduğu için reaktif Flow yalnızca son tutarlı durumu yayar (titreme/ara durum olmaz).
     * Eski workout'lar silindiğinde CASCADE ile ilgili workout_exercises kayıtları da temizlenir.
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

    /** Halen herhangi bir antrenmanda kullanılan benzersiz hareket id'leri (yetim medya temizliği için). */
    @Query("SELECT DISTINCT exerciseId FROM workout_exercises")
    suspend fun getReferencedExerciseIds(): List<String>

    @Upsert suspend fun upsertWorkoutExercise(exercise: WorkoutExerciseEntity)
    @Upsert suspend fun upsertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)

    // Sessions
    @Upsert suspend fun upsertSession(session: WorkoutSessionEntity)
    @Upsert suspend fun upsertSessions(sessions: List<WorkoutSessionEntity>)
    // Görünür sorgular mezar taşlarını (isDeleted = 1) hariç tutar; sync ham listeyi kullanır.
    @Query("SELECT * FROM workout_sessions WHERE isDeleted = 0 ORDER BY startedAt DESC")
    suspend fun getSessions(): List<WorkoutSessionEntity>

    /** Senkronizasyon için TÜM seanslar — silinmiş (mezar taşı) kayıtlar dahil. */
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    suspend fun getSessionsRaw(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND isDeleted = 0")
    suspend fun getSessionById(id: String): WorkoutSessionEntity?

    /** Bir spor görevine bağlı en son tamamlanmış seans (tamamlanan görevin özetini açmak için). */
    @Query("SELECT * FROM workout_sessions WHERE taskId = :taskId AND isDeleted = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSessionForTask(taskId: String): WorkoutSessionEntity?

    /**
     * Yumuşak silme (mezar taşı): seans kaydı saklanır ama isDeleted=1 olur, böylece silme
     * Drive senkronizasyonuyla yayılır ve uzaktan geri diriltilmez.
     */
    @Query("UPDATE workout_sessions SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteSession(id: String)
    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    /**
     * Atomik seans değiştirme: sil + toplu yaz tek transaction içinde (gerekçe yukarıda).
     */
    @Transaction
    suspend fun replaceSessionsAtomic(sessions: List<WorkoutSessionEntity>) {
        deleteAllSessions()
        upsertSessions(sessions)
    }
}
