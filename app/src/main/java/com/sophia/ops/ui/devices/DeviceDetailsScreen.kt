package com.sophia.ops.ui.devices

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (device == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

                Text(
                    text = device.name.ifBlank { "Unknown Device" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                DetailCard {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        DetailItem("Device Type", getDeviceType(device.deviceType))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                DetailItem("First Seen", sdf.format(Date(device.firstSeen)))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                DetailItem("Last Seen", sdf.format(Date(device.lastSeen)))
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        
                        DetailItem("Seen", "${device.timesSeen} Times")
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        
                        DetailItem(
                            label = "Risk",
                            value = when {
                                device.riskScore > 50 -> "High"
                                device.riskScore > 20 -> "Medium"
                                else -> "Low"
                            },
                            valueColor = when {
                                device.riskScore > 50 -> Color.Red
                                device.riskScore > 20 -> Color.Yellow
                                else -> Color.Green
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.DarkGray.copy(alpha = 0.2f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        content()
    }
}

@Composable
fun DetailItem(label: String, value: String, valueColor: Color = Color.White) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
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
