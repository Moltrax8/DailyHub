package com.moltrax.personalnoteapp.di

import com.moltrax.personalnoteapp.data.repository.CategoryRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.NoteRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.TaskRepositoryImpl
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.NoteRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindTaskRepo(impl: TaskRepositoryImpl): TaskRepository
    @Binds @Singleton abstract fun bindCategoryRepo(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds @Singleton abstract fun bindNoteRepo(impl: NoteRepositoryImpl): NoteRepository
}
