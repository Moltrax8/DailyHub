package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moltrax.personalnoteapp.domain.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val isPermanent: Boolean = false,
)

fun CategoryEntity.toDomain() = Category(name = name, isPermanent = isPermanent)

fun Category.toEntity() = CategoryEntity(name = name, isPermanent = isPermanent)
