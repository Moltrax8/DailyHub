package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Upsert suspend fun upsert(exercise: ExerciseEntity)
    @Upsert suspend fun upsertAll(exercises: List<ExerciseEntity>)

    /** All cached movements — for cleaning up orphaned media no longer used in any workout. */
    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :q || '%' OR bodyPart LIKE '%' || :q || '%' ORDER BY name")
    suspend fun search(q: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE bodyPart = :bodyPart ORDER BY name")
    suspend fun getByBodyPart(bodyPart: String): List<ExerciseEntity>

    @Query("SELECT DISTINCT bodyPart FROM exercises ORDER BY bodyPart")
    suspend fun getBodyParts(): List<String>
}
