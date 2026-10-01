package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    /** Senkronizasyon için TÜM kategoriler — silinmiş (mezar taşı) kayıtlar dahil. */
    suspend fun getAllForSync(): List<Category>

    /** Tüm kategori tablosunu verilen listeyle değiştirir (sync birleştirmesi sonrası). */
    suspend fun replaceAll(categories: List<Category>)

    /** Yoksa oluşturur. Varsa ve [isPermanent] true istenmişse kalıcıya yükseltir; aksi halde dokunmaz. */
    suspend fun ensureExists(name: String, isPermanent: Boolean = false)

    /** Kategoriyi yeniden adlandırır; bağlı görevleri yeni ada taşır. */
    suspend fun rename(oldName: String, newName: String)

    /** Kategoriyi siler; bağlı görevlerin kategorisini null yapar. */
    suspend fun delete(name: String)

    /** Geçici olup bağlı görevi kalmayan kategorileri temizler. */
    suspend fun cleanupTemporary()
}
