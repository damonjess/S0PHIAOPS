package com.sophia.ops.ui.devices

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sophia.ops.model.DeviceType
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.data.OuiLookup
import com.sophia.ops.viewmodel.DevicesViewModel

@Composable
fun DevicesScreen(
    vm: DevicesViewModel,
    dashboardVm: com.sophia.ops.viewmodel.DashboardViewModel,
    onDeviceClick: (NetworkDevice) -> Unit = {}
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
    devices: List<NetworkDevice>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onClearDevicesClick: () -> Unit,
    onDeviceClick: (NetworkDevice) -> Unit
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
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filter == "All",
                            onClick = { filter = "All" },
                            label = { Text("All") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == "Wi-Fi",
                            onClick = { filter = "Wi-Fi" },
                            label = { Text("Wi-Fi") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == "Bluetooth",
                            onClick = { filter = "Bluetooth" },
                            label = { Text("Bluetooth") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == "Favourite",
                            onClick = { filter = "Favourite" },
                            label = { Text("Favourite") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == "Unknown",
                            onClick = { filter = "Unknown" },
                            label = { Text("Unknown") }
                        )
                    }
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
                    val matchesSearch = query.isEmpty() ||
                            device.name.contains(query, ignoreCase = true) ||
                            device.address.contains(query, ignoreCase = true) ||
                            device.vendor?.contains(query, ignoreCase = true) == true

                    val matchesFilter = when(filter) {
                        "Favourite" -> device.favourite
                        "Wi-Fi" -> device.type == DeviceType.WIFI
                        "Bluetooth" -> device.type == DeviceType.BLUETOOTH
                        "Unknown" -> device.name.contains("Unknown", ignoreCase = true) || device.name.isBlank()
                        else -> true
                    }

                    matchesSearch && matchesFilter
                }

                val sortedDevices = when (sortOption) {
                    "Newest" -> filteredDevices.sortedByDescending { it.lastSeen }
                    "Oldest" -> filteredDevices.sortedBy { it.lastSeen }
                    "Name A-Z" -> filteredDevices.sortedBy { it.name }
                    "Name Z-A" -> filteredDevices.sortedByDescending { it.name }
                    "Strongest Signal" -> filteredDevices.sortedByDescending { it.signal }
                    "Weakest Signal" -> filteredDevices.sortedBy { it.signal }
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
    device: NetworkDevice,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    DeviceIcon(device.name)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1
                    )
                }
                if (device.favourite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favourite",
                        tint = Color.Yellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeIcon = if (device.type == DeviceType.WIFI) "📶" else "🔵"
                val typeName = if (device.type == DeviceType.WIFI) "Wi-Fi" else "Bluetooth"

                Text(
                    text = "$typeIcon $typeName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(16.dp))

                val signalPercent = (2 * (device.signal + 100)).coerceIn(0, 100)
                val signalColor = when {
                    signalPercent > 70 -> Color.Green
                    signalPercent > 40 -> Color.Yellow
                    else -> Color.Red
                }
                
                Text(
                    text = "Signal: $signalPercent%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = signalColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "MAC: ${device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )

                val context = LocalContext.current
                val cleanVendorName = remember(device.address) {
                    OuiLookup.getVendor(context, device.address)
                }

                Text(
                    text = "Vendor: $cleanVendorName",
                    color = Color(0xFF00BCD4),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Last Seen: ${DateUtils.getRelativeTimeSpanString(device.lastSeen)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}
