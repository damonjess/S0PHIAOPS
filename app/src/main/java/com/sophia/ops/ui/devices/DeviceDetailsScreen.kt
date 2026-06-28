package com.sophia.ops.ui.devices

import android.bluetooth.BluetoothDevice
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sophia.ops.data.utils.OuiLookupEngine
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.viewmodel.DeviceDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    type: String,
    address: String,
    vm: DeviceDetailsViewModel,
    onBack: () -> Unit
) {
    if (type == "BLUETOOTH") {
        BluetoothDetails(address, vm, onBack)
    } else {
        WifiDetails(address, vm, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDetails(address: String, vm: DeviceDetailsViewModel, onBack: () -> Unit) {
    val deviceState = vm.getBluetoothDevice(address).collectAsState(initial = null)
    val device = deviceState.value

    var showRenameDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var renameText by remember(device?.nickname) { mutableStateOf(device?.nickname ?: "") }
    var notesText by remember(device?.notes) { mutableStateOf(device?.notes ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Details") },
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
                val displayName = device.nickname ?: device.name ?: "Unknown Bluetooth Device"
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.toggleFavourite(device.address, device.favourite) }) {
                        Icon(
                            imageVector = if (device.favourite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Toggle Favourite",
                            tint = if (device.favourite) Color.Yellow else Color.Gray
                        )
                    }
                    Text(
                        text = if (device.favourite) "Favourite" else "Not Favourite",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (device.favourite) Color.Yellow else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                DetailRow("Full MAC", device.address)
                DetailRow("Manufacturer", OuiLookupEngine.resolveVendor(device.address))
                DetailRow("First Seen", formatTimestamp(device.firstSeen))
                DetailRow("Last Seen", formatTimestamp(device.lastSeen))
                DetailRow("Signal", "${device.rssi} dBm")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Signal History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (device.signalHistory.isEmpty()) {
                    Text("No signal history recorded.", color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        device.signalHistory.takeLast(15).forEach { point ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        color = getSignalColor(point.rssi).copy(alpha = 0.6f)
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    onClick = { showNotesDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Text(
                        text = device.notes ?: "Add notes...",
                        modifier = Modifier.padding(16.dp),
                        color = if (device.notes != null) Color.White else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rename Device")
                }
            }
        }
    }

    if (showRenameDialog && device != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Device") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nickname") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateNickname(device.address, renameText.ifBlank { null })
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNotesDialog && device != null) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text("Edit Notes") },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.height(150.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateNotes(device.address, notesText.ifBlank { null })
                    showNotesDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDetails(address: String, vm: DeviceDetailsViewModel, onBack: () -> Unit) {
    val networkState = vm.getWifiNetwork(address).collectAsState(initial = null)
    val network = networkState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (network == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                DetailRow("Full BSSID (MAC)", network.bssid)
                DetailRow("Manufacturer", OuiLookupEngine.resolveVendor(network.bssid))
                DetailRow("Security", network.security)
                DetailRow("Last Seen", formatTimestamp(network.timestamp))
                DetailRow("Signal", "${network.signal} dBm")
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Wi-Fi details are limited compared to Bluetooth devices.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateUtils.getRelativeDateTimeString(
        null, timestamp, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
    ).toString()
}

private fun getSignalColor(rssi: Int): Color {
    return when {
        rssi > -60 -> Color.Green
        rssi > -80 -> Color.Yellow
        else -> Color.Red
    }
}
