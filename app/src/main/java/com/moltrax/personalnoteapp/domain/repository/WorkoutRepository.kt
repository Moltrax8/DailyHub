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
    /** Senkronizasyon için TÜM seanslar — silinmiş (mezar taşı) kayıtlar dahil. */
    suspend fun getSessionsForSync(): List<WorkoutSession>
    suspend fun getSessionById(id: String): WorkoutSession?
    /** Bir spor görevine bağlı en son tamamlanmış seans (varsa). */
    suspend fun getLatestSessionForTask(taskId: String): WorkoutSession?

    // Senkronizasyon için toplu erişim
    suspend fun getGroups(): List<WorkoutGroup>
    /** Senkronizasyon için TÜM gruplar — silinmiş (mezar taşı) kayıtlar dahil. */
    suspend fun getGroupsForSync(): List<WorkoutGroup>
    suspend fun replaceGroups(groups: List<WorkoutGroup>)
    suspend fun replaceSessions(sessions: List<WorkoutSession>)

    // Exercise database
    fun observeExercises(): Flow<List<Exercise>>
    suspend fun upsertExercise(exercise: Exercise)
    suspend fun searchExercises(query: String): List<Exercise>
    suspend fun getExercisesByBodyPart(bodyPart: String): List<Exercise>

    /**
     * Artık hiçbir antrenmanda kullanılmayan (yetim) önbellek hareketlerinin lokal demo
     * medya dosyalarını ve kayıtlarını siler. Bir hareket/antrenman/program silindikten sonra
     * çağrılır; cihaz hafızasının şişmesini önler.
     */
    suspend fun cleanupOrphanedExerciseMedia()
}
