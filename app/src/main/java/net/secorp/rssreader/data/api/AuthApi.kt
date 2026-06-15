package net.secorp.rssreader.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class MobileAuthRequest(val idToken: String)

@Serializable
data class AuthUser(
    val id: Long,
    val email: String,
    val name: String? = null,
)

@Serializable
data class MobileAuthResponse(
    val token: String,
    val user: AuthUser,
)

interface AuthApi {
    @POST("auth/google/mobile")
    suspend fun signInWithGoogle(@Body body: MobileAuthRequest): MobileAuthResponse
}
