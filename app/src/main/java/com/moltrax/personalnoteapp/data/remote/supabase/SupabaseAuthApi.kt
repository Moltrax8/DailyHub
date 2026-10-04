package com.moltrax.personalnoteapp.data.remote.supabase

import com.moltrax.personalnoteapp.data.remote.supabase.model.EmailCredentials
import com.moltrax.personalnoteapp.data.remote.supabase.model.GoTrueSession
import com.moltrax.personalnoteapp.data.remote.supabase.model.GoTrueUser
import com.moltrax.personalnoteapp.data.remote.supabase.model.RefreshRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * GoTrue (Supabase Auth) REST surface. `apikey` rides an OkHttp interceptor;
 * user tokens travel per-call (they rotate on refresh).
 */
interface SupabaseAuthApi {
    @POST("signup")
    suspend fun signUp(@Body body: EmailCredentials): Response<GoTrueSession>

    @POST("token?grant_type=password")
    suspend fun signIn(@Body body: EmailCredentials): Response<GoTrueSession>

    @POST("token?grant_type=refresh_token")
    suspend fun refresh(@Body body: RefreshRequest): Response<GoTrueSession>

    @POST("logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

    @GET("user")
    suspend fun me(@Header("Authorization") bearer: String): Response<GoTrueUser>
}
