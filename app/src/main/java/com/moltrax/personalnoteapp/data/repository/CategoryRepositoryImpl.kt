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
            // Mezar taşını dirilt: aynı ad yeniden kullanılıyorsa canlı kayda çevir.
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
        // Görev taşıma + kategori yazımı tek transaction içinde: yarıda kesilme,
        // görevlerin silinmiş ada işaret etmesine yol açmasın.
        db.withTransaction {
            val existing = dao.getByName(old) ?: return@withTransaction

            val now = System.currentTimeMillis()
            // Bağlı görevleri yeni ada taşı
            taskDao.reassignCategory(old, new, now)

            // Hedef ad zaten varsa kalıcılığı koru/yükselt, yoksa (veya mezar taşıysa) eskinin
            // kalıcılığıyla canlı oluştur.
            val target = dao.getByName(new)
            if (target == null || target.isDeleted) {
                dao.upsert(CategoryEntity(new, existing.isPermanent))
            } else if (existing.isPermanent && !target.isPermanent) {
                dao.upsert(target.copy(isPermanent = true))
            }
            // Eski adı mezar taşına çevir (hard-delete değil — silme sync ile yayılsın, dirilmesin).
            dao.upsert(existing.copy(isDeleted = true))
        }
    }

    override suspend fun delete(name: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            taskDao.clearCategory(name, now)
            // Mezara taşı: kayıt saklanır, görünür listelerde gizlenir, sync ile yayılır.
            dao.getByName(name)?.let { dao.upsert(it.copy(isDeleted = true)) }
        }
    }

    override suspend fun cleanupTemporary() = dao.deleteOrphanTemporary()
}
