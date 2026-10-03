package com.sophia.ops.ui.devices

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sophia.ops.data.OuiLookup
import com.sophia.ops.model.DeviceType
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.ui.components.InfoCard
import com.sophia.ops.ui.components.ScreenHeader
import com.sophia.ops.ui.theme.SophiaThemeColors
import com.sophia.ops.ui.components.SophiaUi
import com.sophia.ops.ui.components.StatusChip
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
    var searchText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var sortOption by remember { mutableStateOf("Newest") }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredDevices = devices.filter { device ->
        val query = searchText.trim()
        val matchesSearch = query.isEmpty() ||
                device.name.contains(query, ignoreCase = true) ||
                device.address.contains(query, ignoreCase = true) ||
                device.vendor?.contains(query, ignoreCase = true) == true

        val matchesFilter = when (filter) {
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

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Devices",
            subtitle = "${devices.size} discovered",
            actions = {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort devices")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    listOf(
                        "Newest", "Oldest", "Name A-Z", "Name Z-A",
                        "Strongest Signal", "Weakest Signal",
                    ).forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                sortOption = option
                                showSortMenu = false
                            },
                        )
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SophiaUi.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search by name, address, or vendor") },
                singleLine = true,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Wi-Fi", "Bluetooth", "Favourite", "Unknown").forEach { option ->
                    item {
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(if (isScanning) "Scanning…" else "Scan")
                }
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear")
                }
            }

            Text(
                text = "Sorted by $sortOption · showing ${sortedDevices.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (sortedDevices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "No devices found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (devices.isEmpty()) "Run a scan to discover nearby Wi-Fi and Bluetooth devices."
                        else "Nothing matches the current search or filter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = SophiaUi.ScreenPadding,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SophiaUi.ItemGap),
                ) {
                    items(sortedDevices) { device ->
                        DeviceItem(
                            device = device,
                            onClick = { onDeviceClick(device) },
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete all discovered devices?") },
            text = {
                Text("This will remove all Wi-Fi and Bluetooth devices. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearDevicesClick()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun DeviceItem(
    device: NetworkDevice,
    onClick: () -> Unit
) {
    val signalPercent = (2 * (device.signal + 100)).coerceIn(0, 100)
    val signalColor = when {
        signalPercent > 70 -> SophiaThemeColors.statusGreen
        signalPercent > 40 -> SophiaThemeColors.statusYellow
        else -> SophiaThemeColors.statusRed
    }
    val riskColor = when {
        device.riskScore >= 60 -> SophiaThemeColors.statusRed
        device.riskScore >= 30 -> SophiaThemeColors.statusYellow
        else -> SophiaThemeColors.statusGreen
    }
    val context = LocalContext.current
    val cleanVendorName = remember(device.address) {
        OuiLookup.getVendor(context, device.address)
    }

    InfoCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeviceIcon(device.name, device.type)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = if (device.type == DeviceType.WIFI) "Wi-Fi network" else "Bluetooth device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (device.favourite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favourite",
                    tint = SophiaThemeColors.statusYellow,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
            }
            StatusChip(text = "Risk ${device.riskScore}", color = riskColor)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = "Signal $signalPercent%", color = signalColor)
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(
                text = device.status,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = device.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Vendor: $cleanVendorName",
            style = MaterialTheme.typography.bodySmall,
            color = SophiaThemeColors.statusBlue,
        )
        Text(
            text = "Last seen ${DateUtils.getRelativeTimeSpanString(device.lastSeen)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
