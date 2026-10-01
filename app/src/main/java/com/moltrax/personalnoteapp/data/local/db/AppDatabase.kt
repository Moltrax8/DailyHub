package com.moltrax.personalnoteapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moltrax.personalnoteapp.data.local.db.dao.CategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.ExerciseDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.dao.WorkoutDao
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import com.moltrax.personalnoteapp.data.local.db.entity.ExerciseEntity
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

// Vault AES/GCM geçişi: kayıt başına rastgele salt sütunu (eski CBC kayıtlarında boş kalır)
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_entries ADD COLUMN salt TEXT NOT NULL DEFAULT ''")
    }
}

// Kasa (vault) özelliği kaldırıldı: tabloyu tamamen düşür.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS vault_entries")
    }
}

// Görevlere manuel sıralama (drag-and-drop) için sortOrder sütunu. Mevcut görevlere
// -createdAt atanır: sortOrder ARTAN sırada okunduğunda en yeni görev üstte kalır,
// yani eski "createdAt DESC" davranışı korunur.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE tasks SET sortOrder = -createdAt")
    }
}

// Kategori yaşam döngüsü: categories tablosu (name PK + isPermanent). Mevcut görevlerdeki
// kategoriler geçici (isPermanent = 0) olarak tohumlanır.
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

// Besin kayıtlarına iliştirilen fotoğrafın iç depolama yolu için imagePath sütunu.
// Mevcut kayıtlarda NULL kalır (fotoğrafsız).
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_entries ADD COLUMN imagePath TEXT")
    }
}

// Besin kayıtları için soft-delete: isDeleted sütunu. Mevcut kayıtlar silinmemiş (0) kabul edilir.
// Silinen öğeler mezar taşı olarak kalır, böylece Drive senkronizasyonu onları diriltmez.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_entries ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

// Egzersiz tipi (ağırlık/kardiyo): workout_exercises tablosuna type sütunu. Mevcut hareketler
// "WEIGHTLIFTING" varsayılır. Set verileri (kardiyo adım/mesafe dahil) JSON sütunlarda tutulduğundan
// ek şema değişikliği gerekmez (eski JSON, varsayılan alanlarla geriye dönük uyumludur).
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN type TEXT NOT NULL DEFAULT 'WEIGHTLIFTING'")
    }
}

// Görevlere tüm program (WorkoutGroup) bağlama: linkedProgramId + döngü başlangıç günü
// (programStartIndex). Mevcut görevlerde program bağı yoktur (NULL / 0).
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN linkedProgramId TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN programStartIndex INTEGER NOT NULL DEFAULT 0")
    }
}

// Vücut ağırlığı geçmişi: gelişim raporundaki çizgi grafiğin veri kaynağı. Günde tek kayıt
// (epochDay birincil anahtar). Mevcut kullanıcılarda tablo boş başlar; ilk açılışta StatsViewModel
// güncel kiloyu tek bir başlangıç noktası olarak tohumlar.
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

// Ceza Bölgesi: görevlere isPenalty sütunu. Bitiş süresi (deadline) geçtiği halde tamamlanmayan
// spora linkli görevler için sistem otomatik kırmızı/silinemeyen ceza görevi üretir. Mevcut
// görevler ceza değildir (0).
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN isPenalty INTEGER NOT NULL DEFAULT 0")
    }
}

// Alt görevler (checklist) + zengin tekrar biçimi. subtasks/recurrenceDaysOfWeek JSON metin olarak
// saklanır (Converters); recurrenceType eski görevlerde NULL kalır → intervalDays davranışı korunur.
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceType TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceDaysOfWeek TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN subtasks TEXT NOT NULL DEFAULT '[]'")
    }
}

// Senkronizasyon silme hatası düzeltmesi: workout_groups tablosuna updatedAt (LWW zaman damgası)
// ve isDeleted (mezar taşı) sütunları. Mevcut gruplara updatedAt = createdAt verilir; hiçbiri
// silinmiş değildir (0). Böylece silinen bir antrenman/grup Drive senkronizasyonundan sonra geri
// dirilmez.
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_groups ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_groups ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE workout_groups SET updatedAt = createdAt")
    }
}

// Besin (food) özelliği tamamen kaldırıldı: tabloyu düşür. Mevcut kullanıcılarda kayıtlı besin
// verisi varsa silinir; uygulamanın geri kalanı bu tablodan bağımsızdır.
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS food_entries")
    }
}

// 1) Antrenman seansını tamamlayan spor görevine bağlamak için workout_sessions.taskId sütunu
//    (eski seanslarda NULL → göreve bağlı değil). 2) Fiziksel parametreler/kilo trend grafiği
//    kaldırıldığı için vücut ağırlığı geçmişi (body_weight_entries) tablosu tamamen düşürülür.
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN taskId TEXT")
        db.execSQL("DROP TABLE IF EXISTS body_weight_entries")
    }
}

// Silme mezar taşları: tasks/categories/workout_sessions tablolarına isDeleted sütunu.
// Mevcut hiçbir kayıt silinmiş değildir (0). Bilinen sınırlama: v18 ÖNCESİ yapılmış silmeler
// uzaktan bir kez geri gelebilir (yerelde mezar taşı yoktur); v18 sonrası tüm silmeler
// tombstone ile yayılır ve diriltilmez.
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE categories ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

// Sık filtrelenen sütunlara indeks (kategori/link/oturum aramaları tam taramaydı).
// İsimler Room'un varsayılan indeks adlarıyla birebir aynı olmalıdır (şema doğrulaması için).
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_category ON tasks (category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_linkedWorkoutId ON tasks (linkedWorkoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_linkedProgramId ON tasks (linkedProgramId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_workoutId ON workout_sessions (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_taskId ON workout_sessions (taskId)")
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
    ],
    version = 19,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
}
