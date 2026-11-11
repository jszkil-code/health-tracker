package dev.healthtracker.app.ui.debuglog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.healthtracker.app.data.local.LogEntryEntity
import dev.healthtracker.app.data.local.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit, viewModel: DebugLogViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug log") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = viewModel::clear) { Text("Clear") } },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Text("No events yet.", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(entries, key = { it.id }) { entry ->
                    LogRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

@Composable
private fun LogRow(entry: LogEntryEntity) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val timing = entry.durationMs?.let { " · ${it}ms" }.orEmpty()
        Text(
            "${timeFormat.format(Date(entry.timestamp))} ${entry.level} [${entry.category}]$timing",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        Text(entry.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
