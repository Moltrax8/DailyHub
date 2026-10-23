package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Position of a task inside ONE category (Phase 2, Room v20).
 *
 * A task can carry many tags; the same task may be #1 in `DailyHub` and #4 in
 * `Development`. The unfiltered Home list keeps using `tasks.sortOrder`.
 * `categoryName` preserves display casing; every lookup compares NOCASE
 * (same convention as `tasks.category` / `categories.name`).
 * No FK constraints by design (name matching is NOCASE, PKs are BINARY).
 */
@Entity(
    tableName = "task_category_cross_ref",
    primaryKeys = ["taskId", "categoryName"],
    indices = [Index("taskId"), Index("categoryName")],
)
data class TaskCategoryCrossRef(
    val taskId: String,
    val categoryName: String,
    /** Position inside this category (ascending; rewritten 0..n on reorder). */
    val sortOrder: Long = 0L,
    val addedAt: Long = 0L,
)
