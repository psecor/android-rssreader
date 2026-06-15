package net.secorp.rssreader.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.secorp.rssreader.auth.AuthRepository
import net.secorp.rssreader.auth.GoogleSignInClient

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object SigningIn : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val googleSignInClient: GoogleSignInClient,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun signIn(activityContext: Context) {
        if (_state.value is LoginUiState.SigningIn) return
        _state.value = LoginUiState.SigningIn
        viewModelScope.launch {
            _state.value = try {
                val idToken = googleSignInClient.requestIdToken(activityContext)
                authRepository.signInWithGoogleIdToken(idToken)
                LoginUiState.Idle
            } catch (t: Throwable) {
                LoginUiState.Error(t.message ?: t.javaClass.simpleName)
            }
        }
    }
}
