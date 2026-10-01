package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    // Görünür sorgular mezar taşlarını (isDeleted = 1) hariç tutar; sync ham listeyi kullanır.
    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<CategoryEntity>

    /** Senkronizasyon için TÜM kategoriler — silinmiş (mezar taşı) kayıtlar dahil. */
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllRaw(): List<CategoryEntity>

    // NOCASE: "Work"/"work" yazım farkları aynı kategori sayılır (PK BINARY'dir, tüm erişim
    // yolları NOCASE karşılaştırır; böylece çift kayıt oluşmaz, görünür adın yazımı korunur).
    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE")
    suspend fun getByName(name: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    /**
     * Atomik değiştirme: sil + toplu yaz tek transaction içinde (bkz. TaskDao.replaceAllAtomic).
     */
    @Transaction
    suspend fun replaceAllAtomic(categories: List<CategoryEntity>) {
        deleteAll()
        upsertAll(categories)
    }

    /**
     * Geçici (isPermanent = 0) olup görünür göreve bağlı olmayan kategorileri mezar taşına
     * çevirir (hard-delete DEĞİL — silme bilgisi sync ile yayılsın, uzaktan dirilmesin).
     * Görev silindiğinde / kategorisi değiştiğinde çağrılır.
     */
    @Query(
        """
        UPDATE categories SET isDeleted = 1
        WHERE isDeleted = 0
          AND isPermanent = 0
          AND NOT EXISTS (
            SELECT 1 FROM tasks
            WHERE tasks.category IS NOT NULL
              AND tasks.isDeleted = 0
              AND tasks.category = categories.name COLLATE NOCASE
          )
        """
    )
    suspend fun deleteOrphanTemporary()
}
