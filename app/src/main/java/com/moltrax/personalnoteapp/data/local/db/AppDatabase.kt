package com.moltrax.personalnoteapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moltrax.personalnoteapp.data.local.db.dao.CategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.ExerciseDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskCategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.dao.WorkoutDao
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import com.moltrax.personalnoteapp.data.local.db.entity.ExerciseEntity
import com.moltrax.personalnoteapp.data.local.db.entity.TaskCategoryCrossRef
import com.moltrax.personalnoteapp.data.local.db.entity.TaskEntity
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutEntity
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutExerciseEntity
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutGroupEntity
import com.moltrax.personalnoteapp.data.local.db.entity.WorkoutSessionEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN linkedWorkoutId TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_entries (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                calories INTEGER NOT NULL,
                protein INTEGER NOT NULL,
                carbs INTEGER NOT NULL,
                fat INTEGER NOT NULL,
                comment TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

// Vault AES/GCM transition: per-record random salt column (left empty in legacy CBC records)
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_entries ADD COLUMN salt TEXT NOT NULL DEFAULT ''")
    }
}

// Vault feature removed: drop the table entirely.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS vault_entries")
    }
}

// sortOrder column for manual ordering (drag-and-drop) of tasks. Existing tasks get
// -createdAt: when read in ASCENDING sortOrder, the newest task stays on top,
// preserving the old "createdAt DESC" behavior.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE tasks SET sortOrder = -createdAt")
    }
}

// Category lifecycle: categories table (name PK + isPermanent). Categories found in
// existing tasks are seeded as temporary (isPermanent = 0).
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories (
                name TEXT NOT NULL PRIMARY KEY,
                isPermanent INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO categories (name, isPermanent)
            SELECT DISTINCT category, 0 FROM tasks
            WHERE category IS NOT NULL AND category != ''
            """.trimIndent()
        )
    }
}

// imagePath column for the internal-storage path of the photo attached to nutrition records.
// Stays NULL in existing records (no photo).
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_entries ADD COLUMN imagePath TEXT")
    }
}

// Soft-delete for nutrition records: isDeleted column. Existing records are considered not deleted (0).
// Deleted items remain as tombstones so Drive sync does not resurrect them.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_entries ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

// Exercise type (strength/cardio): type column on workout_exercises. Existing movements
// default to "WEIGHTLIFTING". Since set data (including cardio steps/distance) is kept in JSON
// columns, no further schema change is needed (old JSON stays backward compatible via default fields).
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN type TEXT NOT NULL DEFAULT 'WEIGHTLIFTING'")
    }
}

// Attaching a whole program (WorkoutGroup) to tasks: linkedProgramId + cycle start day
// (programStartIndex). Existing tasks have no program attachment (NULL / 0).
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN linkedProgramId TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN programStartIndex INTEGER NOT NULL DEFAULT 0")
    }
}

// Body-weight history: data source for the line chart in the progress report. One record per day
// (epochDay primary key). Starts empty for existing users; on first launch StatsViewModel
// seeds the current weight as a single starting point.
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS body_weight_entries (
                epochDay INTEGER NOT NULL PRIMARY KEY,
                weightKg REAL NOT NULL,
                recordedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

// Penalty Zone: isPenalty column on tasks. For sport-linked tasks left incomplete past their
// deadline, the system auto-generates a red/non-deletable penalty task. Existing
// tasks are not penalties (0).
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN isPenalty INTEGER NOT NULL DEFAULT 0")
    }
}

// Subtasks (checklist) + rich recurrence format. subtasks/recurrenceDaysOfWeek are stored as JSON
// text (Converters); recurrenceType stays NULL on old tasks → intervalDays behavior is preserved.
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceType TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceDaysOfWeek TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN subtasks TEXT NOT NULL DEFAULT '[]'")
    }
}

// Sync deletion-bug fix: updatedAt (LWW timestamp) and isDeleted (tombstone) columns on
// workout_groups. Existing groups get updatedAt = createdAt; none is deleted (0). This way a
// deleted workout/group is not resurrected after Drive sync.
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_groups ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_groups ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE workout_groups SET updatedAt = createdAt")
    }
}

// Nutrition (food) feature fully removed: drop the table. Any stored nutrition data on
// existing users is deleted; the rest of the app is independent of this table.
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS food_entries")
    }
}

// 1) workout_sessions.taskId column to link a session to the sport task that completed it
//    (NULL on old sessions → not linked to a task). 2) The body-weight history
//    (body_weight_entries) table is dropped entirely since the body metrics/weight trend chart was removed.
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN taskId TEXT")
        db.execSQL("DROP TABLE IF EXISTS body_weight_entries")
    }
}

// Deletion tombstones: isDeleted column on tasks/categories/workout_sessions tables.
// No existing record is deleted (0). Known limitation: deletions made BEFORE v18 may
// come back once from remote (no local tombstone); after v18 all deletions propagate
// via tombstone and are never resurrected.
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE categories ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

// Indexes on frequently filtered columns (category/link/session lookups were full scans).
// Names must exactly match Room's default index names (for schema validation).
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_category ON tasks (category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_linkedWorkoutId ON tasks (linkedWorkoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_linkedProgramId ON tasks (linkedProgramId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_workoutId ON workout_sessions (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_taskId ON workout_sessions (taskId)")
    }
}

// Multi-category tags (Phase 2): per-category positions + backfill from the legacy
// single tasks.category column (kept one release as read-fallback).
// Index names must exactly match Room's defaults (see MIGRATION_18_19 note).
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS task_category_cross_ref (" +
                "taskId TEXT NOT NULL, categoryName TEXT NOT NULL, " +
                "sortOrder INTEGER NOT NULL DEFAULT 0, addedAt INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (taskId, categoryName))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_task_category_cross_ref_taskId " +
                "ON task_category_cross_ref (taskId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_task_category_cross_ref_categoryName " +
                "ON task_category_cross_ref (categoryName)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO task_category_cross_ref (taskId, categoryName, sortOrder, addedAt) " +
                "SELECT id, category, sortOrder, createdAt FROM tasks " +
                "WHERE category IS NOT NULL AND category <> ''"
        )
    }
}

@Database(
    entities = [
        TaskEntity::class,
        CategoryEntity::class,
        WorkoutGroupEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        TaskCategoryCrossRef::class,
    ],
    version = 20,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun taskCategoryDao(): TaskCategoryDao
}
