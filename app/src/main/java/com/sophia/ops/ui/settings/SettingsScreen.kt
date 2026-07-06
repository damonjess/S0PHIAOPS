package com.sophia.ops.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sophia.ops.services.ScanForegroundService
import com.sophia.ops.viewmodel.DashboardViewModel

@Composable
fun SettingsScreen(vm: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    var isBackgroundScanEnabled by remember {
        mutableStateOf(isServiceRunning(context, ScanForegroundService::class.java))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Background Scanning
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background Scanning",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Keep scanning for devices while the app is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isBackgroundScanEnabled,
                    onCheckedChange = { enabled ->
                        isBackgroundScanEnabled = enabled
                        if (enabled) {
                            ScanForegroundService.startService(context)
                        } else {
                            ScanForegroundService.stopService(context)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto Cyber Analyst
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Cyber Analyst",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Automatically analyze threats after scans",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = vm.autoAiAnalysisEnabled,
                    onCheckedChange = { vm.toggleAutoAiAnalysis(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Subsystem
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "AI Subsystem",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (vm.isAiLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.Green)
                    Text(
                        "Initializing cognitive core...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!vm.isAiReady) {
                    Button(
                        onClick = { vm.activateOnDeviceAI() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
                    ) {
                        Text("INITIALIZE COGNITIVE CORE")
                    }
                    if (vm.aiInitializationFailed) {
                        Text(
                            text = "Last initialization failed. Check model weights.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text(
                        "Cognitive AI Core is active and monitoring.",
                        color = Color.Green,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Status: ${vm.aiAdviceText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "More settings coming soon...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Suppress("DEPRECATION")
private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}
