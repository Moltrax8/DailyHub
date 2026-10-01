package com.moltrax.personalnoteapp.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Bir ana görevin (Quest) altındaki tek bir kontrol-listesi (checklist) maddesi. Görevle birlikte
 * gömülü JSON liste olarak saklanır (ayrı tablo yok); böylece Drive senkronizasyonunda görevle
 * birlikte taşınır ve mevcut LWW birleştirmesi olduğu gibi çalışır.
 */
@Serializable
data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false,
)
