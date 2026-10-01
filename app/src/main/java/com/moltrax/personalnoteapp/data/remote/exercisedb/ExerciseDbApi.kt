package com.moltrax.personalnoteapp.data.remote.exercisedb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class ExerciseDbItem(
    val id: String,
    val name: String,
    @SerialName("bodyPart") val bodyPart: String,
    val equipment: String? = null,
    @SerialName("gifUrl") val gifUrl: String? = null,
    val instructions: List<String> = emptyList(),
)

interface ExerciseDbApi {
    @GET("exercises")
    suspend fun getAll(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): List<ExerciseDbItem>

    @GET("exercises/bodyPart/{bodyPart}")
    suspend fun getByBodyPart(
        @Path("bodyPart") bodyPart: String,
        @Query("limit") limit: Int = 100,
    ): List<ExerciseDbItem>

    @GET("exercises/name/{name}")
    suspend fun searchByName(
        @Path("name") name: String,
        @Query("limit") limit: Int = 20,
    ): List<ExerciseDbItem>

    @GET("exercises/bodyPartList")
    suspend fun getBodyParts(): List<String>
}
