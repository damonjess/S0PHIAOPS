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

@Composable
fun SettingsScreen() {
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
