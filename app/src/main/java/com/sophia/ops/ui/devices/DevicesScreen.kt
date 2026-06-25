package com.sophia.ops.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    Scaffold(
        topBar = {
            Text(
                "Discovered Devices",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(devices) { device ->
                DeviceItem(device)
            }
        }
    }
}

@Composable
fun DeviceItem(device: BluetoothDeviceEntity) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
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
