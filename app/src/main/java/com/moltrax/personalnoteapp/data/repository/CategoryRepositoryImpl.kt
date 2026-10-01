package com.moltrax.personalnoteapp.data.repository

import androidx.room.withTransaction
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.data.local.db.dao.CategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import com.moltrax.personalnoteapp.data.local.db.entity.toDomain
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val taskDao: TaskDao,
    private val db: AppDatabase,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }

    override suspend fun getAllForSync(): List<Category> = dao.getAllRaw().map { it.toDomain() }

    override suspend fun replaceAll(categories: List<Category>) {
        dao.replaceAllAtomic(categories.map { it.toEntity() })
    }

    override suspend fun ensureExists(name: String, isPermanent: Boolean) {
        val n = name.trim()
        if (n.isBlank()) return
        val existing = dao.getByName(n)
        when {
            existing == null -> dao.upsert(CategoryEntity(n, isPermanent))
            // Resurrect the tombstone: if the same name is reused, turn it back into a live record.
            existing.isDeleted -> dao.upsert(
                existing.copy(isPermanent = existing.isPermanent || isPermanent, isDeleted = false)
            )
            isPermanent && !existing.isPermanent -> dao.upsert(existing.copy(isPermanent = true))
            else -> Unit
        }
    }

    override suspend fun rename(oldName: String, newName: String) {
        val old = oldName.trim()
        val new = newName.trim()
        if (new.isBlank() || new == old) return
        // Task move + category write in a single transaction: an interruption must not leave
        // tasks pointing at the deleted name.
        db.withTransaction {
            val existing = dao.getByName(old) ?: return@withTransaction

            val now = System.currentTimeMillis()
            // Move linked tasks to the new name
            taskDao.reassignCategory(old, new, now)

            // If the target name already exists keep/promote permanence, otherwise create it live
            // with the old entry's permanence (or if it is a tombstone).
            val target = dao.getByName(new)
            if (target == null || target.isDeleted) {
                dao.upsert(CategoryEntity(new, existing.isPermanent))
            } else if (existing.isPermanent && !target.isPermanent) {
                dao.upsert(target.copy(isPermanent = true))
            }
            // Turn the old name into a tombstone (not a hard delete — so the deletion propagates via sync and is not resurrected).
            dao.upsert(existing.copy(isDeleted = true))
        }
    }

    override suspend fun delete(name: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            taskDao.clearCategory(name, now)
            // Move to trash: the record is kept, hidden from visible lists, propagated via sync.
            dao.getByName(name)?.let { dao.upsert(it.copy(isDeleted = true)) }
        }
    }

    override suspend fun cleanupTemporary() = dao.deleteOrphanTemporary()
}
