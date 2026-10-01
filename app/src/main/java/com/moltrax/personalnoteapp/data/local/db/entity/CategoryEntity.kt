package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moltrax.personalnoteapp.domain.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val isPermanent: Boolean = false,
    // Tombstone (v18): deleted categories are kept, visible queries filter them, sync includes them.
    val isDeleted: Boolean = false,
)

fun CategoryEntity.toDomain() = Category(name = name, isPermanent = isPermanent, isDeleted = isDeleted)

fun Category.toEntity() = CategoryEntity(name = name, isPermanent = isPermanent, isDeleted = isDeleted)
