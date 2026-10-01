package com.moltrax.personalnoteapp.data.local.db

import androidx.room.TypeConverter
import com.moltrax.personalnoteapp.domain.model.SubTask
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun fromStringList(value: List<String>): String = json.encodeToString(value)

    // Bozuk bir satır tüm akışı öldürmesin: çözülemeyen JSON boş liste sayılır.
    @TypeConverter fun toStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    // Alt görevler (checklist) görev satırına gömülü JSON olarak saklanır.
    @TypeConverter fun fromSubTaskList(value: List<SubTask>): String = json.encodeToString(value)
    @TypeConverter fun toSubTaskList(value: String): List<SubTask> =
        runCatching { json.decodeFromString<List<SubTask>>(value) }.getOrDefault(emptyList())

    // Haftalık tekrar günleri (ISO 1..7) JSON liste olarak saklanır.
    @TypeConverter fun fromIntList(value: List<Int>): String = json.encodeToString(value)
    @TypeConverter fun toIntList(value: String): List<Int> =
        runCatching { json.decodeFromString<List<Int>>(value) }.getOrDefault(emptyList())
}
