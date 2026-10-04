package com.moltrax.personalnoteapp.data.remote.supabase

import com.moltrax.personalnoteapp.domain.model.FriendRequest
import com.moltrax.personalnoteapp.domain.model.SharedNote
import com.moltrax.personalnoteapp.domain.model.SharedTask
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceLink
import com.moltrax.personalnoteapp.domain.model.SpaceMember
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

    // ---- Phase 5: shared spaces -------------------------------------------

    @GET("spaces?select=*")
    suspend fun mySpaces(
        @Header("Authorization") bearer: String,
    ): Response<List<Space>>
    // NOTE: spaces list is member-filtered server-side by RLS; no filter needed.

    @POST("spaces")
    suspend fun createSpace(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String?>,
    ): Response<List<Space>>

    @PATCH("spaces")
    suspend fun renameSpace(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
        @Body body: Map<String, String?>,
    ): Response<Unit>

    @DELETE("spaces")
    suspend fun deleteSpace(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("space_members?select=*")
    suspend fun spaceMembers(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<SpaceMember>>

    @POST("space_members")
    suspend fun addMember(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String>,
    ): Response<List<SpaceMember>>

    @DELETE("space_members")
    suspend fun removeMember(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
        @Query("user_id") userEq: String,
    ): Response<Unit>

    @GET("notes?select=*&order=updated_at.desc")
    suspend fun spaceNotes(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<SharedNote>>

    @POST("notes")
    suspend fun createNote(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String?>,
    ): Response<List<SharedNote>>

    @PATCH("notes")
    suspend fun updateNote(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
        @Body body: Map<String, String?>,
    ): Response<Unit>

    @DELETE("notes")
    suspend fun deleteNote(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("shared_tasks?select=*&order=sort_order.asc")
    suspend fun spaceTasks(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<SharedTask>>

    @POST("shared_tasks")
    suspend fun createSharedTask(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<List<SharedTask>>

    @PATCH("shared_tasks")
    suspend fun updateSharedTask(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<Unit>

    @DELETE("shared_tasks")
    suspend fun deleteSharedTask(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("links?select=*&order=created_at.desc")
    suspend fun spaceLinks(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<SpaceLink>>

    @POST("links")
    suspend fun createLink(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String?>,
    ): Response<List<SpaceLink>>

    @DELETE("links")
    suspend fun deleteLink(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>
}
