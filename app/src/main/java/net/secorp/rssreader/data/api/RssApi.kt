package net.secorp.rssreader.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class HealthResponse(
    val status: String,
)

interface RssApi {
    @GET("health")
    suspend fun health(): HealthResponse
}
