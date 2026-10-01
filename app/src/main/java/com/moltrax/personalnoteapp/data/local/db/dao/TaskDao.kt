package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // Görünür sorgular mezar taşlarını (isDeleted = 1) hariç tutar; sync ham listeyi kullanır.
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<TaskEntity>

    /** Senkronizasyon için TÜM görevler — silinmiş (mezar taşı) kayıtlar dahil. */
    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAllRaw(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    /**
     * Yumuşak silme (mezar taşı): kayıt saklanır ama isDeleted=1 + updatedAt güncellenir,
     * böylece silme Drive senkronizasyonuyla yayılır ve uzaktan geri diriltilmez.
     */
    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    /**
     * Atomik değiştirme: sil + toplu yaz tek transaction içinde. Yarıda kesilme (kill/crash)
     * eski veriyi korur; aksi halde sonraki sync boş listeyi Drive yedeğine basardı.
     */
    @Transaction
    suspend fun replaceAllAtomic(tasks: List<TaskEntity>) {
        deleteAll()
        upsertAll(tasks)
    }

    // Kategori yeniden adlandırıldığında: ilgili görevleri yeni ada taşı (sync için updatedAt'i de tazele).
    // NOCASE: "Work"/"work" yazım farkları aynı kategori sayılır.
    @Query("UPDATE tasks SET category = :newName, updatedAt = :now WHERE category = :oldName COLLATE NOCASE")
    suspend fun reassignCategory(oldName: String, newName: String, now: Long)

    // Kategori silindiğinde: bağlı görevlerin kategorisini boşalt.
    @Query("UPDATE tasks SET category = NULL, updatedAt = :now WHERE category = :name COLLATE NOCASE")
    suspend fun clearCategory(name: String, now: Long)
}
