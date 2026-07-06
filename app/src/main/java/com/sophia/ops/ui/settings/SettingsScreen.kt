package com.sophia.ops.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sophia.ops.viewmodel.DashboardViewModel

@Composable
fun SettingsScreen(viewModel: DashboardViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Cyber Analyst", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatically analyze threats after scans",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = viewModel.autoAiAnalysisEnabled,
                    onCheckedChange = { viewModel.toggleAutoAiAnalysis(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "More features coming soon...",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
