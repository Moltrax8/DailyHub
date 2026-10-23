package com.moltrax.personalnoteapp.data.local.db

import androidx.room.TypeConverter
import com.moltrax.personalnoteapp.domain.model.SubTask
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun fromStringList(value: List<String>): String = json.encodeToString(value)

    // A corrupt row must not kill the whole stream: unparseable JSON counts as an empty list.
    @TypeConverter fun toStringList(value: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    // Subtasks (checklist) are stored as JSON embedded in the task row.
    @TypeConverter fun fromSubTaskList(value: List<SubTask>): String = json.encodeToString(value)
    @TypeConverter fun toSubTaskList(value: String): List<SubTask> =
        runCatching { json.decodeFromString<List<SubTask>>(value) }.getOrDefault(emptyList())

    // Weekly recurrence days (ISO 1..7) stored as a JSON list.
    @TypeConverter fun fromIntList(value: List<Int>): String = json.encodeToString(value)
    @TypeConverter fun toIntList(value: String): List<Int> =
        runCatching { json.decodeFromString<List<Int>>(value) }.getOrDefault(emptyList())
}
