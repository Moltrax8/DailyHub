package com.moltrax.personalnoteapp.di

import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.exercisedb.ExerciseDbApi
import com.moltrax.personalnoteapp.data.remote.github.GitHubPublicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides @Singleton
    fun provideExerciseDbApi(json: Json, http: OkHttpClient, prefs: AppPreferences): ExerciseDbApi {
        val exerciseHttp = http.newBuilder()
            .addInterceptor { chain ->
                // NOTE: Using runBlocking here because interceptor is not suspend.
                // The key is read from DataStore on each request (fast in-memory read).
                val key = runBlocking { prefs.exerciseDbKey.first() ?: "" }
                val req = chain.request().newBuilder()
                    .apply { if (key.isNotBlank()) header("X-RapidAPI-Key", key) }
                    .build()
                chain.proceed(req)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://exercisedb.p.rapidapi.com/")
            .client(exerciseHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ExerciseDbApi::class.java)
    }

    @Provides @Singleton
    fun provideGitHubPublicApi(json: Json, http: OkHttpClient): GitHubPublicApi =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubPublicApi::class.java)
}
