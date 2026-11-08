package com.moltrax.personalnoteapp.di

import com.moltrax.personalnoteapp.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Plain client for the release metadata call (`api.github.com`).
     *
     * Do NOT add the APK host guard here: it only allows download hosts and would
     * reject the API request before it leaves the device (that is exactly how the
     * update check silently broke). The guarded client is built inside
     * `UpdateRepository.downloadHttp`, which keeps the download path pinned to
     * GitHub's release hosts on every hop.
     */
    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        })
        .build()
}
