package com.moltrax.personalnoteapp.di

import com.moltrax.personalnoteapp.data.repository.CategoryRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.GitHubRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.ProfileRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.ProjectRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.SocialRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.SpaceRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.SyncRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.TaskRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.WorkoutRepositoryImpl
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.GitHubRepository
import com.moltrax.personalnoteapp.domain.repository.ProfileRepository
import com.moltrax.personalnoteapp.domain.repository.ProjectRepository
import com.moltrax.personalnoteapp.domain.repository.SocialRepository
import com.moltrax.personalnoteapp.domain.repository.SpaceRepository
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindTaskRepo(impl: TaskRepositoryImpl): TaskRepository
    @Binds @Singleton abstract fun bindSyncRepo(impl: SyncRepositoryImpl): SyncRepository
    @Binds @Singleton abstract fun bindWorkoutRepo(impl: WorkoutRepositoryImpl): WorkoutRepository
    @Binds @Singleton abstract fun bindCategoryRepo(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds @Singleton abstract fun bindProfileRepo(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds @Singleton abstract fun bindSocialRepo(impl: SocialRepositoryImpl): SocialRepository
    @Binds @Singleton abstract fun bindSpaceRepo(impl: SpaceRepositoryImpl): SpaceRepository
    @Binds @Singleton abstract fun bindProjectRepo(impl: ProjectRepositoryImpl): ProjectRepository
    @Binds @Singleton abstract fun bindGitHubRepo(impl: GitHubRepositoryImpl): GitHubRepository
}
