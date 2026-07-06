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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.sophia.ops.data.OuiLookup
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.viewmodel.DashboardViewModel
import com.sophia.ops.viewmodel.DeviceDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    type: String,
    address: String,
    vm: DeviceDetailsViewModel,
    dashboardVm: DashboardViewModel,
    onBack: () -> Unit
) {
    if (type == "BLUETOOTH") {
        BluetoothDetails(address, vm, dashboardVm, onBack)
    } else {
        WifiDetails(address, vm, dashboardVm, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDetails(address: String, vm: DeviceDetailsViewModel, dashboardVm: DashboardViewModel, onBack: () -> Unit) {
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
                DetailRow("Manufacturer", OuiLookup.getVendor(LocalContext.current, device.address))
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

                Spacer(modifier = Modifier.height(16.dp))

                val context = LocalContext.current
                Button(
                    onClick = { 
                        dashboardVm.startGattExploration(address)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
                ) {
                    Text("GATT EXPLORATION")
                }
                Text(
                    text = "Note: GATT exploration only works on devices that advertise connectable services (many don't).",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { 
                        dashboardVm.performFullRecon(with(dashboardVm) { device.toNetworkDevice(context.applicationContext as android.app.Application) })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    enabled = !dashboardVm.isReconRunning
                ) {
                    Text(if (dashboardVm.isReconRunning) "RECON IN PROGRESS..." else "DEEP RECON + PORT SCAN")
                }
                
                if (dashboardVm.gattReport != null) {
                    val gattReport = dashboardVm.gattReport!!
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("GATT EXPLORATION", style = MaterialTheme.typography.titleMedium, color = Color(0xFF00E676))
                            Spacer(modifier = Modifier.height(8.dp))

                            if (gattReport.contains("Complete")) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("GATT Exploration Complete", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                                }
                                
                                Text(
                                    text = gattReport.lines().drop(1).joinToString("\n"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .verticalScroll(rememberScrollState()),
                                    color = Color.White
                                )
                            } else if (gattReport.contains("❌") || gattReport.contains("timeout") || gattReport.contains("Error")) {
                                Text(gattReport, color = Color.Red, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tip: Many devices (phones, earbuds, etc.) block GATT connections for privacy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else {
                                Text(gattReport, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { 
                                    dashboardVm.startGattExploration(address)
                                },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text("RE-EXPLORE", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
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
fun WifiDetails(address: String, vm: DeviceDetailsViewModel, dashboardVm: DashboardViewModel, onBack: () -> Unit) {
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
                DetailRow("Manufacturer", OuiLookup.getVendor(LocalContext.current, network.bssid))
                DetailRow("Security", network.security)
                DetailRow("Last Seen", formatTimestamp(network.timestamp))
                DetailRow("Signal", "${network.signal} dBm")
                
                Spacer(modifier = Modifier.height(24.dp))

                val context = LocalContext.current
                Button(
                    onClick = { 
                        dashboardVm.performFullRecon(with(dashboardVm) { network.toNetworkDevice(context.applicationContext as android.app.Application) })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    enabled = !dashboardVm.isReconRunning
                ) {
                    Text(if (dashboardVm.isReconRunning) "RECON IN PROGRESS..." else "DEEP RECON + PORT SCAN")
                }

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
