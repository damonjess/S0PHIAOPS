package com.sophia.ops.ui.devices

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.viewmodel.DevicesViewModel
import java.util.*

@Composable
fun DevicesScreen(
    vm: DevicesViewModel,
    dashboardVm: com.sophia.ops.viewmodel.DashboardViewModel,
    onDeviceClick: (BluetoothDeviceEntity) -> Unit = {}
) {
    val devices by vm.devices.collectAsState()
    
    DevicesContent(
        devices = devices,
        isScanning = dashboardVm.isScanning,
        onScanClick = { dashboardVm.scan() },
        onClearDevicesClick = { vm.clearDevices() },
        onDeviceClick = onDeviceClick
    )
}

@Composable
fun DevicesContent(
    devices: List<BluetoothDeviceEntity>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onClearDevicesClick: () -> Unit,
    onDeviceClick: (BluetoothDeviceEntity) -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var searchText by remember {
        mutableStateOf("")
    }
    var filter by remember {
        mutableStateOf("All")
    }
    var sortOption by remember {
        mutableStateOf("Newest")
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            HorizontalDivider()
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Discovered Devices",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = {
                            showSortMenu = true
                        }
                    ) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = "Sort"
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = {
                            showSortMenu = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (sortOption == "Newest") "✓ Newest" else "Newest") },
                            onClick = {
                                sortOption = "Newest"
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortOption == "Oldest") "✓ Oldest" else "Oldest") },
                            onClick = {
                                sortOption = "Oldest"
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortOption == "Most Seen") "✓ Most Seen" else "Most Seen") },
                            onClick = {
                                sortOption = "Most Seen"
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortOption == "Name A-Z") "✓ Name A-Z" else "Name A-Z") },
                            onClick = {
                                sortOption = "Name A-Z"
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortOption == "Name Z-A") "✓ Name Z-A" else "Name Z-A") },
                            onClick = {
                                sortOption = "Name Z-A"
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortOption == "Strongest Signal") "✓ Strongest Signal" else "Strongest Signal") },
                            onClick = {
                                sortOption = "Strongest Signal"
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (sortOption == "Weakest Signal") "✓ Weakest Signal" else "Weakest Signal") },
                            onClick = {
                                sortOption = "Weakest Signal"
                                showSortMenu = false
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, null)
                    },
                    placeholder = {
                        Text("Search devices...")
                    },
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sorted by: $sortOption",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    
                    Text(
                        text = "⇅",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.Green
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == "All",
                        onClick = { filter = "All" },
                        label = { Text("All") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = filter == "Favourite",
                        onClick = { filter = "Favourite" },
                        label = { Text("Fav") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = filter == "Unknown",
                        onClick = { filter = "Unknown" },
                        label = { Text("Unk") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onScanClick,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (isScanning) "[ ... ]" else "[ Scan ]", style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = {
                            showClearDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("[ Clear ]", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider()

            Box(modifier = Modifier.weight(1f)) {
                val filteredDevices = devices.filter { device ->
                    val query = searchText.trim()
                    val matchesSearch = query.isEmpty()
                            ||
                    device.name?.contains(query, true) == true
                            ||
                    device.nickname?.contains(query, true) == true
                            ||
                    device.address.contains(query, true)

                    val matchesFilter = when(filter) {
                        "Favourite" -> device.favourite
                        "Unknown" -> device.name.isNullOrBlank()
                        else -> true
                    }

                    matchesSearch && matchesFilter
                }

                val sortedDevices = when (sortOption) {
                    "Newest" -> filteredDevices.sortedByDescending { it.lastSeen }
                    "Oldest" -> filteredDevices.sortedBy { it.firstSeen }
                    "Most Seen" -> filteredDevices.sortedByDescending { it.timesSeen }
                    "Name A-Z" -> filteredDevices.sortedBy { it.name ?: "" }
                    "Name Z-A" -> filteredDevices.sortedByDescending { it.name ?: "" }
                    "Strongest Signal" -> filteredDevices.sortedByDescending { it.rssi }
                    "Weakest Signal" -> filteredDevices.sortedBy { it.rssi }
                    else -> filteredDevices
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sortedDevices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = {
                    showClearDialog = false
                },
                title = {
                    Text("Delete all discovered Bluetooth devices?")
                },
                text = {
                    Text(
                        "This cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearDevicesClick()
                            showClearDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("[ Delete ]")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showClearDialog = false
                        }
                    ) {
                        Text("[ Cancel ]")
                    }
                }
            )
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDeviceEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val displayName = when {
                !device.nickname.isNullOrBlank() -> device.nickname
                !device.name.isNullOrBlank() && !device.name.startsWith("Discovered Device") -> device.name
                else -> "Unknown Bluetooth Device"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceIcon(device.name)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (device.riskScore > 30) Color.Red else if (device.riskScore > 0) Color.Yellow else Color.White
                )
            }

            Text(
                text = getDeviceType(device.deviceType),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            val riskColor = getRiskColor(device.riskScore)
            val riskLabel = getRiskLabel(device.riskScore).uppercase()
            val riskEmoji = when {
                device.riskScore > 50 -> "🔴"
                device.riskScore > 20 -> "🟠"
                else -> "🟢"
            }

            Text(
                text = "$riskEmoji $riskLabel",
                style = MaterialTheme.typography.labelLarge,
                color = riskColor,
                fontWeight = FontWeight.Bold
            )

            val seenText = if (device.timesSeen == 1) "Seen 1 time" else "Seen ${device.timesSeen} times"
            Text(
                text = seenText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

private fun getRiskLabel(score: Int): String {
    return when {
        score > 50 -> "High"
        score > 20 -> "Medium"
        else -> "Low"
    }
}

private fun getRiskColor(score: Int): Color {
    return when {
        score > 50 -> Color.Red
        score > 20 -> Color.Yellow
        else -> Color.Green
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
