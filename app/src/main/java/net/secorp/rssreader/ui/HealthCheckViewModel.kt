package net.secorp.rssreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.secorp.rssreader.data.api.RssApi

data class HealthUiState(val label: String = "checking…")

@HiltViewModel
class HealthCheckViewModel @Inject constructor(
    private val api: RssApi,
) : ViewModel() {

    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                HealthUiState(label = api.health().status)
            } catch (t: Throwable) {
                HealthUiState(label = "unreachable (${t.javaClass.simpleName})")
            }
        }
    }
}
