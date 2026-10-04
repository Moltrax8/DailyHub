package com.moltrax.personalnoteapp.data.remote.supabase

import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * PostgREST surface used by Phase 3 (profiles). Auth: apikey (interceptor) +
 * per-call Bearer token. RLS on the server decides what succeeds.
 */
interface SupabaseDbApi {
    @GET("profiles?select=*")
    suspend fun getProfile(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<List<SupabaseProfile>>

    @GET("profiles?select=username")
    suspend fun searchProfiles(
        @Header("Authorization") bearer: String,
        @Query("username") usernameLike: String,
        @Query("limit") limit: Int = 20,
    ): Response<List<SupabaseProfile>>

    @POST("profiles")
    suspend fun upsertProfile(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body body: SupabaseProfile,
    ): Response<List<SupabaseProfile>>

    @PATCH("profiles")
    suspend fun updateProfile(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
        @Body body: Map<String, String?>,
    ): Response<Unit>
}
