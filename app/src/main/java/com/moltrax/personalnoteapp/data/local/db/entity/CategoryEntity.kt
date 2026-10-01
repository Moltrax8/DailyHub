package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moltrax.personalnoteapp.domain.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val isPermanent: Boolean = false,
    // Mezar taşı (v18): silinen kategori saklanır, görünür sorgular filtreler, sync dahil eder.
    val isDeleted: Boolean = false,
)

fun CategoryEntity.toDomain() = Category(name = name, isPermanent = isPermanent, isDeleted = isDeleted)

fun Category.toEntity() = CategoryEntity(name = name, isPermanent = isPermanent, isDeleted = isDeleted)
