package com.sophia.ops.ui.devices

import android.bluetooth.BluetoothDevice
import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.viewmodel.DeviceDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    address: String,
    vm: DeviceDetailsViewModel,
    onBack: () -> Unit
) {
    val deviceState = vm.getDevice(address).collectAsState(initial = null)
    val device = deviceState.value
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(device?.nickname) { mutableStateOf(device?.nickname ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (device == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Separator
                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Device Name & Favourite Star
                Text(
                    text = device.nickname ?: device.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                if (device.favourite) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Favourite", style = MaterialTheme.typography.labelLarge, color = Color.Yellow)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content Separator
                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Grid/List of details
                DetailRow("Name", device.name)
                DetailRow("Nickname", device.nickname ?: "None")
                DetailRow("MAC", device.address)
                DetailRow("Type", getDeviceType(device.deviceType))
                DetailRow("First Seen", formatFirstSeen(device.firstSeen))
                DetailRow("Last Seen", formatLastSeen(device.lastSeen))
                DetailRow("Seen", "${device.timesSeen} Times")
                DetailRow("Signal", "${device.rssi} dBm")
                DetailRow(
                    "Risk", 
                    getRiskLabel(device.riskScore), 
                    color = getRiskColor(device.riskScore)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { },
                    label = { Text(device.notes ?: "None") },
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(24.dp))
                
                // Actions Separator
                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = { showRenameDialog = true }) {
                        Text("[ Rename ]")
                    }
                    OutlinedButton(onClick = { vm.toggleFavourite(device.address, device.favourite) }) {
                        Text(if (device.favourite) "[ Unfavourite ]" else "[ Favourite ]")
                    }
                    OutlinedButton(
                        onClick = { 
                            vm.toggleIgnored(device.address, device.ignored)
                            onBack()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (device.ignored) Color.White else Color.Red.copy(alpha = 0.8f)
                        )
                    ) {
                        Text(if (device.ignored) "[ Unignore ]" else "[ Ignore ]")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
            }
        }
    }

    if (showRenameDialog && device != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Nickname") },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Nickname") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateNickname(device.address, renameText.ifBlank { null })
                    showRenameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, color: Color = Color.White) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun getDeviceType(type: Int): String {
    return when (type) {
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE Device"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode Device"
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic Device"
        else -> "Unknown Type"
    }
}

private fun formatFirstSeen(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.DAY_IN_MILLIS
    ).toString()
}

private fun formatLastSeen(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getRiskLabel(score: Int): String {
    return when {
        score > 50 -> "HIGH"
        score > 20 -> "MEDIUM"
        else -> "LOW"
    }
}

private fun getRiskColor(score: Int): Color {
    return when {
        score > 50 -> Color.Red
        score > 20 -> Color.Yellow
        else -> Color.Green
    }
}
