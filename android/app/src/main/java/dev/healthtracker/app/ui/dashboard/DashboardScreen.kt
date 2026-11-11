package dev.healthtracker.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

private val emptySections = listOf(
    "Today" to "Activity, sleep and HRV appear here after Health Connect sync (Phase 2).",
    "Body" to "Weight and body composition from the Withings scale.",
    "Meals" to "Logged meals and daily macros (Phase 3).",
    "Coach" to "Daily readiness and guidance (Phase 5).",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpenDebugLog: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    TextButton(onClick = onOpenDebugLog) { Text("Log") }
                    TextButton(onClick = viewModel::signOut) { Text("Sign out") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("No data yet", style = MaterialTheme.typography.titleMedium)
            emptySections.forEach { (title, body) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
