package com.moltrax.personalnoteapp.data.remote.github

import com.moltrax.personalnoteapp.domain.model.GithubPublicRepo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Public GitHub REST (api.github.com) — no token. Lists a user's PUBLIC repos
 * so spaces can track/untrack them with checkboxes instead of typing numeric
 * IDs. Private repos still need the manual ID entry below the picker.
 */
interface GitHubPublicApi {
    @Headers("Accept: application/vnd.github+json")
    @GET("users/{username}/repos")
    suspend fun publicRepos(
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
        /** owner = theirs only (no member/collab repos); forks filtered client-side. */
        @Query("type") type: String = "owner",
    ): Response<List<GithubPublicRepo>>
}
