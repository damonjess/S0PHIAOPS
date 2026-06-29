package com.sophia.ops.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sophia.ops.services.ScanForegroundService
import com.sophia.ops.viewmodel.DashboardViewModel

@Composable
fun SettingsScreen(vm: DashboardViewModel) {
    val context = LocalContext.current
    var isBackgroundScanEnabled by remember {
        mutableStateOf(isServiceRunning(context, ScanForegroundService::class.java))
    }

    Scaffold(
        topBar = {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Background Scanning", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Keep scanning for devices while the app is closed.",
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

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("AI Subsystem", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            if (vm.isAiLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Initializing cognitive core...", style = MaterialTheme.typography.labelSmall)
            } else if (!vm.isAiReady) {
                Button(
                    onClick = { vm.activateOnDeviceAI() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF00BCD4))
                ) {
                    Text("INITIALIZE COGNITIVE AI CORE")
                }
                if (vm.aiInitializationFailed) {
                    Text(
                        text = "Last initialization failed. Check model weights in /data/local/tmp/",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Text(
                    "Cognitive AI Core is active and monitoring.",
                    color = androidx.compose.ui.graphics.Color.Green,
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
