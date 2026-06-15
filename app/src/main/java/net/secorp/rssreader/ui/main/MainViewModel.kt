package net.secorp.rssreader.ui.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import net.secorp.rssreader.auth.AuthRepository
import net.secorp.rssreader.auth.AuthState

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.state

    fun signOut() = authRepository.signOut()
}
