package com.sophia.ops.ui.devices

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.viewmodel.DevicesViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DevicesScreen(
    vm: DevicesViewModel,
) {
    val devices by vm.devices.collectAsState()
    var selectedDevice by remember { mutableStateOf<BluetoothDeviceEntity?>(null) }

    Scaffold(
        topBar = {
            Column {
                Text(
                    "Discovered Devices",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                )
                Text(
                    "Devices in ViewModel: ${devices.size}",
                    color = Color.Red,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices) { device ->
                    DeviceItem(device, onClick = { selectedDevice = device })
                }
            }

            if (selectedDevice != null) {
                DeviceDetailsDialog(
                    device = selectedDevice!!,
                    onDismiss = { selectedDevice = null }
                )
            }
        }
    }
}

@Composable
fun DeviceItem(device: BluetoothDeviceEntity, onClick: () -> Unit) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = device.name.ifBlank { "Unknown Device" },
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "MAC: ${device.address}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Last Seen: ${sdf.format(Date(device.lastSeen))}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Risk: ${device.riskScore}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (device.riskScore > 0) Color.Red else Color.Green
            )
        }
    }
}

@Composable
fun DeviceDetailsDialog(device: BluetoothDeviceEntity, onDismiss: () -> Unit) {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = device.name.ifBlank { "Unknown Device" }) },
        text = {
            Column {
                DetailRow("Address", device.address)
                DetailRow("Type", getFriendlyType(device.deviceType))
                DetailRow("First Seen", sdf.format(Date(device.firstSeen)))
                DetailRow("Last Seen", sdf.format(Date(device.lastSeen)))
                DetailRow("Risk Score", device.riskScore.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

fun getFriendlyType(type: Int): String {
    return when (type) {
        1 -> "Classic (BR/EDR)"
        2 -> "Low Energy (LE)"
        3 -> "Dual Mode (BR/EDR/LE)"
        else -> "Unknown"
    }
}
