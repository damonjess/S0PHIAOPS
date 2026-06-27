package com.sophia.ops.ui.devices

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.viewmodel.DevicesViewModel
import java.util.*

@Composable
fun DevicesScreen(
    vm: DevicesViewModel,
    onDeviceClick: (BluetoothDeviceEntity) -> Unit = {}
) {
    val devices by vm.devices.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Discovered Devices",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "Devices in ViewModel: ${devices.size}",
                        color = Color.Red,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Button(
                    onClick = {
                        showClearDialog = true
                    }
                ) {
                    Text("Clear Devices")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices) { device ->
                    DeviceItem(
                        device = device,
                        onClick = { onDeviceClick(device) },
                        onFavouriteClick = { vm.toggleFavourite(device) }
                    )
                }
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showClearDialog = false
                    },
                    title = {
                        Text("Clear Devices")
                    },
                    text = {
                        Text(
                            "This will remove all discovered Bluetooth devices."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                vm.clearDevices()
                                showClearDialog = false
                            }
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                showClearDialog = false
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDeviceEntity,
    onClick: () -> Unit,
    onFavouriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.nickname ?: device.name,
                    style = MaterialTheme.typography.titleLarge
                )
                if (device.nickname != null) {
                    Text(
                        text = "Real Name: ${device.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                Text(
                    text = getDeviceType(device.deviceType),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Risk: ${device.riskScore}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (device.riskScore > 30) Color.Red else if (device.riskScore > 0) Color.Yellow else Color.Green
                    )
                    Text(
                        text = "Seen ${device.timesSeen} Times",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            IconButton(onClick = onFavouriteClick) {
                Icon(
                    imageVector = if (device.favourite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (device.favourite) "Unfavourite" else "Favourite",
                    tint = if (device.favourite) Color.Yellow else Color.Gray
                )
            }
        }
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
