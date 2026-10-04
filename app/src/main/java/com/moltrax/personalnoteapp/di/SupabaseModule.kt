package com.moltrax.personalnoteapp.di

import com.moltrax.personalnoteapp.BuildConfig
import com.moltrax.personalnoteapp.FeatureFlags
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthApi
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/** Build-time Supabase config (git-ignored local.properties, never committed). */
data class SupabaseConfig(
    val url: String,
    val anonKey: String,
) {
    val isConfigured: Boolean =
        FeatureFlags.SUPABASE_ENABLED && url.isNotBlank() && anonKey.isNotBlank()
}

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides @Singleton
    fun provideConfig(): SupabaseConfig = SupabaseConfig(
        url = BuildConfig.SUPABASE_URL.trimEnd('/'),
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /** apikey header on every Supabase call; user tokens travel per-call (they rotate). */
    @Provides @Singleton @javax.inject.Named("supabase")
    fun provideHttp(config: SupabaseConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("apikey", config.anonKey)
                        .build()
                )
            })
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
        return builder.build()
    }

    private fun retrofit(http: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /**
     * GoTrue client. Null when unconfigured (placeholder base URL would crash
     * Retrofit) — every consumer treats null as "Supabase unavailable".
     */
    @Provides @Singleton
    fun provideAuthApi(config: SupabaseConfig, @javax.inject.Named("supabase") http: OkHttpClient): SupabaseAuthApi? {
        if (!config.isConfigured) return null
        return retrofit(http, "${config.url}/auth/v1/").create(SupabaseAuthApi::class.java)
    }

    /** PostgREST client. Null when unconfigured (see above). */
    @Provides @Singleton
    fun provideDbApi(config: SupabaseConfig, @javax.inject.Named("supabase") http: OkHttpClient): SupabaseDbApi? {
        if (!config.isConfigured) return null
        return retrofit(http, "${config.url}/rest/v1/").create(SupabaseDbApi::class.java)
    }
}
