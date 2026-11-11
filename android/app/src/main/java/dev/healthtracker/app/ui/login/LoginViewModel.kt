package dev.healthtracker.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.healthtracker.app.data.auth.AuthRepository
import dev.healthtracker.app.data.auth.SupabaseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val configured: Boolean = SupabaseConfig.isConfigured,
) {
    val canSubmit: Boolean get() = configured && !busy && email.isNotBlank() && password.isNotEmpty()
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v.trim(), error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }

    fun signIn() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = auth.signIn(s.email, s.password)
            _state.update {
                it.copy(
                    busy = false,
                    password = if (result.isSuccess) "" else it.password,
                    error = result.exceptionOrNull()?.let { "Sign-in failed. Check your email, password and connection." },
                )
            }
        }
    }
}
