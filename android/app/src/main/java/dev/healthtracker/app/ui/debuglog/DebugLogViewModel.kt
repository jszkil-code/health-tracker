package dev.healthtracker.app.ui.debuglog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.healthtracker.app.data.local.LogEntryEntity
import dev.healthtracker.app.data.log.AppLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugLogViewModel @Inject constructor(private val logger: AppLogger) : ViewModel() {
    val entries: StateFlow<List<LogEntryEntity>> =
        logger.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { logger.clear() }
    }
}
