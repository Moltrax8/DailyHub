package com.moltrax.personalnoteapp.di

import android.content.Context
import androidx.room.Room
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_1_2
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_2_3
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_3_4
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_4_5
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_5_6
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_6_7
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_7_8
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_8_9
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_9_10
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_10_11
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_11_12
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_12_13
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_13_14
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_14_15
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_15_16
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_16_17
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_17_18
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_18_19
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_19_20
import com.moltrax.personalnoteapp.data.local.db.MIGRATION_20_21
import com.moltrax.personalnoteapp.data.local.db.dao.CategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.ExerciseDao
import com.moltrax.personalnoteapp.data.local.db.dao.SpaceDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskCategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "personal_note_app.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                MIGRATION_12_13, MIGRATION_13_14,                 MIGRATION_14_15, MIGRATION_15_16,
                MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                MIGRATION_20_21,
            )
            .build()

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao             = db.taskDao()
    @Provides fun provideSpaceDao(db: AppDatabase): SpaceDao           = db.spaceDao()
    @Provides fun provideTaskCategoryDao(db: AppDatabase): TaskCategoryDao = db.taskCategoryDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao     = db.categoryDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao       = db.workoutDao()
    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao     = db.exerciseDao()
}
