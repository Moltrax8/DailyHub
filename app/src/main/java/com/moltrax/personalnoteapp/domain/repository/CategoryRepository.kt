package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    /** ALL categories for sync — including deleted (tombstone) records. */
    suspend fun getAllForSync(): List<Category>

    /** Replaces the whole category table with the given list (after sync merge). */
    suspend fun replaceAll(categories: List<Category>)

    /** Creates if missing. If it exists and [isPermanent] true is requested, promotes to permanent; otherwise leaves untouched. */
    suspend fun ensureExists(name: String, isPermanent: Boolean = false)

    /** Renames the category; moves linked tasks to the new name. */
    suspend fun rename(oldName: String, newName: String)

    /** Deletes the category; nulls the category of linked tasks. */
    suspend fun delete(name: String)

    /** Cleans up temporary categories left with no linked tasks. */
    suspend fun cleanupTemporary()
}
