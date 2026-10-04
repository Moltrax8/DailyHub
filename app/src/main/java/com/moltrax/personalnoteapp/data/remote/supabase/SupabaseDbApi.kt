package com.moltrax.personalnoteapp.data.remote.supabase

import com.moltrax.personalnoteapp.domain.model.FriendRequest
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * PostgREST surface (Phases 3-4: profiles + friends). Auth: apikey
 * (interceptor) + per-call Bearer token. RLS on the server decides.
 */
interface SupabaseDbApi {
    @GET("profiles?select=*")
    suspend fun getProfile(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
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

    // ---- Phase 4: friends -------------------------------------------------

    @GET("profiles?select=id,username,display_name,avatar_url")
    suspend fun searchProfiles(
        @Header("Authorization") bearer: String,
        @Query("username") usernameLike: String,
        @Query("limit") limit: Int = 20,
    ): Response<List<SupabaseProfile>>

    @GET("profiles?select=id,username,display_name,avatar_url")
    suspend fun getProfileByUsername(
        @Header("Authorization") bearer: String,
        @Query("username") usernameEq: String,
    ): Response<List<SupabaseProfile>>

    @POST("friend_requests")
    suspend fun sendRequest(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String>,
    ): Response<List<FriendRequest>>

    @GET("friend_requests?select=*")
    suspend fun sentRequests(
        @Header("Authorization") bearer: String,
        @Query("from_id") fromEq: String,
    ): Response<List<FriendRequest>>

    @GET("friend_requests?select=*")
    suspend fun receivedRequests(
        @Header("Authorization") bearer: String,
        @Query("to_id") toEq: String,
    ): Response<List<FriendRequest>>

    @PATCH("friend_requests")
    suspend fun answerRequest(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
        @Body body: Map<String, String>,
    ): Response<List<FriendRequest>>

    @DELETE("friend_requests")
    suspend fun deleteRequest(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>
}
