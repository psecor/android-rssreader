package net.secorp.rssreader.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.secorp.rssreader.data.api.AuthApi
import net.secorp.rssreader.data.api.AuthUser
import net.secorp.rssreader.data.api.MobileAuthRequest
import net.secorp.rssreader.data.sync.SyncScheduler

sealed interface AuthState {
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser?) : AuthState
}

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val syncScheduler: SyncScheduler,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        _state.value = if (tokenStore.getToken() != null) {
            AuthState.SignedIn(user = null)
        } else {
            AuthState.SignedOut
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String) {
        val response = authApi.signInWithGoogle(MobileAuthRequest(idToken))
        tokenStore.setToken(response.token)
        _state.value = AuthState.SignedIn(user = response.user)
        syncScheduler.enqueueOneShot()
    }

    fun signOut() {
        tokenStore.clear()
        _state.value = AuthState.SignedOut
        syncScheduler.cancelAll()
    }
}
