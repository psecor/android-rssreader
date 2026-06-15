package net.secorp.rssreader.auth

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val token = tokenStore.getToken()
        val newReq = if (token != null) {
            req.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            req
        }
        return chain.proceed(newReq)
    }
}
