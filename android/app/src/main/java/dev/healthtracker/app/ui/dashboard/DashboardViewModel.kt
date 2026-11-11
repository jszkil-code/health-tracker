package dev.healthtracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.healthtracker.app.data.auth.AuthRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
