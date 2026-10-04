package com.moltrax.personalnoteapp.data.remote.supabase

import com.moltrax.personalnoteapp.domain.model.FriendRequest
import com.moltrax.personalnoteapp.domain.model.FeedEntry
import com.moltrax.personalnoteapp.domain.model.GithubActivity
import com.moltrax.personalnoteapp.domain.model.GithubConnection
import com.moltrax.personalnoteapp.domain.model.GithubRepo
import com.moltrax.personalnoteapp.domain.model.NotifPrefRow
import com.moltrax.personalnoteapp.domain.model.Project
import com.moltrax.personalnoteapp.domain.model.ProjectComment
import com.moltrax.personalnoteapp.domain.model.ProjectItem
import com.moltrax.personalnoteapp.domain.model.SpaceEvent
import com.moltrax.personalnoteapp.domain.model.SpaceFile
import com.moltrax.personalnoteapp.domain.model.SpaceMessage
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

    // ---- Phase 6: projects ----------------------------------------------

    @GET("projects?select=*")
    suspend fun getProject(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<Project>>

    @POST("projects")
    suspend fun upsertProject(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<List<Project>>

    @GET("project_items?select=*&order=sort_order.asc")
    suspend fun projectItems(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<ProjectItem>>

    @POST("project_items")
    suspend fun createProjectItem(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<List<ProjectItem>>

    @PATCH("project_items")
    suspend fun updateProjectItem(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<Unit>

    @DELETE("project_items")
    suspend fun deleteProjectItem(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("comments?select=*&order=created_at.asc")
    suspend fun itemComments(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
        @Query("ref_id") refEq: String,
    ): Response<List<ProjectComment>>

    @POST("comments")
    suspend fun createComment(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String?>,
    ): Response<List<ProjectComment>>

    @DELETE("comments")
    suspend fun deleteComment(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    // ---- Phase 7: GitHub --------------------------------------------------

    @GET("github_connections?select=*")
    suspend fun myGithubConnection(
        @Header("Authorization") bearer: String,
        @Query("user_id") userEq: String,
    ): Response<List<GithubConnection>>

    @GET("github_repos?select=*")
    suspend fun spaceRepos(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<GithubRepo>>

    @POST("github_repos")
    suspend fun linkRepo(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<List<GithubRepo>>

    @DELETE("github_repos")
    suspend fun unlinkRepo(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("github_activity?select=*&order=created_at.desc")
    suspend fun spaceActivity(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
        @Query("limit") limit: Int = 50,
    ): Response<List<GithubActivity>>

    @GET("notification_prefs?select=*")
    suspend fun myNotifPrefs(
        @Header("Authorization") bearer: String,
        @Query("user_id") userEq: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<NotifPrefRow>>

    @POST("notification_prefs")
    suspend fun setNotifPref(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<Unit>

    @POST("fcm_tokens")
    suspend fun registerFcmToken(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body body: Map<String, String>,
    ): Response<Unit>

    // ---- Phase 8: expanded shared -----------------------------------------

    @GET("events?select=*&order=start_at.asc")
    suspend fun spaceEvents(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<SpaceEvent>>

    @POST("events")
    suspend fun createEvent(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<List<SpaceEvent>>

    @DELETE("events")
    suspend fun deleteEvent(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("messages?select=*&order=created_at.asc")
    suspend fun spaceMessages(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
        @Query("limit") limit: Int = 100,
    ): Response<List<SpaceMessage>>

    @POST("messages")
    suspend fun sendMessage(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: Map<String, String?>,
    ): Response<List<SpaceMessage>>

    @DELETE("messages")
    suspend fun deleteMessage(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("files?select=*&order=created_at.desc")
    suspend fun spaceFiles(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
    ): Response<List<SpaceFile>>

    @POST("files")
    suspend fun createFileRow(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<List<SpaceFile>>

    @DELETE("files")
    suspend fun deleteFileRow(
        @Header("Authorization") bearer: String,
        @Query("id") idEq: String,
    ): Response<Unit>

    @GET("activity_feed?select=*&order=created_at.desc")
    suspend fun spaceFeed(
        @Header("Authorization") bearer: String,
        @Query("space_id") spaceEq: String,
        @Query("limit") limit: Int = 50,
    ): Response<List<FeedEntry>>

    @POST("activity_feed")
    suspend fun appendFeed(
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=minimal",
        @Body body: kotlinx.serialization.json.JsonObject,
    ): Response<Unit>
}
