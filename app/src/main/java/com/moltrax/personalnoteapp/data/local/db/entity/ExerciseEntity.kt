package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moltrax.personalnoteapp.domain.model.Exercise

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String?,
    val isUnilateral: Boolean,
    val description: String?,
    val mediaUrl: String?,
    val localMediaPath: String?,
)

fun ExerciseEntity.toDomain() = Exercise(
    id, name, bodyPart, equipment, isUnilateral, description, mediaUrl, localMediaPath,
)

fun Exercise.toEntity() = ExerciseEntity(
    id, name, bodyPart, equipment, isUnilateral, description, mediaUrl, localMediaPath,
)
