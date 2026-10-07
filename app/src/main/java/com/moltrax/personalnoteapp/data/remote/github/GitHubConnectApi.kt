package com.moltrax.personalnoteapp.data.remote.github

import com.moltrax.personalnoteapp.domain.model.GithubReposResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Supabase Edge Functions for GitHub App linking (spec: github-link-spec.md).
 *
 * Base URL is `<SUPABASE_URL>/functions/v1/`. Every call carries the user's
 * Supabase JWT as `Authorization: Bearer` (plus the shared apikey header from
 * the OkHttp client). The GitHub token itself NEVER reaches the device — the
 * functions hold it server-side.
 */
interface GitHubConnectApi {
    @POST("github-connect")
    suspend fun connect(
        @Header("Authorization") bearer: String,
        @Body body: ConnectActionBody,
    ): Response<ConnectActionResponse>

    @GET("github-repos")
    suspend fun repos(
        @Header("Authorization") bearer: String,
    ): Response<GithubReposResult>
}

@Serializable
data class ConnectActionBody(
    val action: String,
    val code: String? = null,
    val state: String? = null,
    /** start only: "install" = one-screen install+authorize (first-time connect); absent = plain authorize. */
    val mode: String? = null,
)

@Serializable
data class ConnectActionResponse(
    /** From `start`: open in a browser/Custom Tab. */
    val url: String? = null,
    /** From `finish`: the linked GitHub login. */
    val login: String? = null,
    val ok: Boolean? = null,
    /** Present instead of data on errors, e.g. `not_connected`. */
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
