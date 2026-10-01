package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    suspend fun getById(id: String): Task?
    suspend fun upsert(task: Task)
    suspend fun delete(id: String)
    suspend fun getAll(): List<Task>
    /** Senkronizasyon için TÜM görevler — silinmiş (mezar taşı) kayıtlar dahil. */
    suspend fun getAllForSync(): List<Task>
    /** Liste sırası vektörünün LWW saati (sıralama birleştirmesi için). */
    suspend fun getTaskOrderTimestamp(): Long
    /** Sıra vektörü saatini yazar (yeniden sıralamada şimdi, uzaktan benimserken uzaktaki değer). */
    suspend fun setTaskOrderTimestamp(v: Long)
    suspend fun replaceAll(tasks: List<Task>)
}
