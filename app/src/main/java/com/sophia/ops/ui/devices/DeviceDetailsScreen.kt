package com.sophia.ops.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sophia.ops.bluetooth.GattCharacteristicInfo
import com.sophia.ops.bluetooth.GattExplorationState
import com.sophia.ops.bluetooth.GattServiceInfo
import com.sophia.ops.data.DeviceDisposition
import com.sophia.ops.data.DeviceProfileComparison
import com.sophia.ops.data.LocalDeviceProfile
import com.sophia.ops.data.OuiLookup
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.viewmodel.DeviceDetailsViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DeviceDetailsScreen(
    type: String,
    address: String,
    vm: DeviceDetailsViewModel,
    onBack: () -> Unit,
) {
    if (type == "BLUETOOTH") BluetoothDetails(address, vm, onBack) else WifiDetails(address, vm, onBack)
}

@Composable
private fun BluetoothDetails(address: String, vm: DeviceDetailsViewModel, onBack: () -> Unit) {
    val device by vm.getBluetoothDevice(address).collectAsState(initial = null)
    var showRenameDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var renameText by remember(device?.nickname) { mutableStateOf(device?.nickname ?: "") }
    var notesText by remember(device?.notes) { mutableStateOf(device?.notes ?: "") }

    DetailScaffold(title = "Bluetooth Investigation", onBack = onBack) { padding ->
        val current = device
        if (current == null) {
            LoadingState(padding)
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                DeviceHeading(
                    name = current.nickname ?: current.name ?: "Unknown Bluetooth Device",
                    subtitle = "Bluetooth · ${signalDistanceLabel(current.rssi)} · ${riskLabel(current.riskScore)} risk",
                    favourite = current.favourite,
                    onFavourite = { vm.toggleFavourite(current.address, current.favourite) },
                )
                Spacer(Modifier.height(14.dp))
                ReviewStatePanel(
                    address = current.address,
                    disposition = vm.deviceDisposition(current.address),
                    ignored = current.ignored,
                    onDisposition = { vm.setDeviceDisposition(current.address, it) },
                    onIgnore = { vm.toggleIgnored(current.address, current.ignored) },
                )
                Spacer(Modifier.height(14.dp))
                BluetoothSignalPanel(current)
                Spacer(Modifier.height(14.dp))
                MetadataPanel(
                    entries = listOf(
                        "Full MAC" to current.address,
                        "Manufacturer" to OuiLookup.getVendor(LocalContext.current, current.address),
                        "First seen" to formatTimestamp(current.firstSeen),
                        "Last seen" to formatTimestamp(current.lastSeen),
                        "Observation count" to "${current.timesSeen} scan(s)",
                    ),
                )
                Spacer(Modifier.height(14.dp))
                GattExplorerPanel(
                    address = current.address,
                    vm = vm,
                    hasSavedProfile = vm.localProfile(current.address) != null,
                    onSaveProfile = { vm.saveBluetoothProfile(current) },
                )
                Spacer(Modifier.height(14.dp))
                ProfileComparisonPanel(
                    profile = vm.localProfile(current.address),
                    comparison = vm.bluetoothProfileComparison(current),
                    emptyMessage = "Complete a successful GATT discovery, then save this device as a private local baseline.",
                )
                Spacer(Modifier.height(14.dp))
                NotesPanel(
                    notes = current.notes,
                    onEdit = { showNotesDialog = true },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showRenameDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("RENAME DEVICE")
                }
            }
        }
    }

    if (showRenameDialog && device != null) {
        TextEntryDialog(
            title = "Rename device",
            label = "Local label",
            value = renameText,
            onValueChange = { renameText = it },
            onConfirm = {
                vm.updateNickname(device!!.address, renameText.ifBlank { null })
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
    if (showNotesDialog && device != null) {
        TextEntryDialog(
            title = "Investigation notes",
            label = "Notes stored on this device",
            value = notesText,
            onValueChange = { notesText = it },
            onConfirm = {
                vm.updateNotes(device!!.address, notesText.ifBlank { null })
                showNotesDialog = false
            },
            onDismiss = { showNotesDialog = false },
            multiline = true,
        )
    }
}

@Composable
private fun WifiDetails(address: String, vm: DeviceDetailsViewModel, onBack: () -> Unit) {
    val network by vm.getWifiNetwork(address).collectAsState(initial = null)
    DetailScaffold(title = "Wi-Fi Investigation", onBack = onBack) { padding ->
        val current = network
        if (current == null) {
            LoadingState(padding)
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                DeviceHeading(
                    name = current.ssid.ifBlank { "Hidden Wi-Fi Network" },
                    subtitle = "Wi-Fi · ${signalDistanceLabel(current.signal)} · ${riskLabel(current.riskScore)} risk",
                )
                Spacer(Modifier.height(14.dp))
                ReviewStatePanel(
                    address = current.bssid,
                    disposition = vm.deviceDisposition(current.bssid),
                    ignored = false,
                    onDisposition = { vm.setDeviceDisposition(current.bssid, it) },
                    onIgnore = null,
                )
                Spacer(Modifier.height(14.dp))
                WifiPosturePanel(current)
                Spacer(Modifier.height(14.dp))
                MetadataPanel(
                    entries = listOf(
                        "Full BSSID" to current.bssid,
                        "Manufacturer" to OuiLookup.getVendor(LocalContext.current, current.bssid),
                        "Security capability" to current.security.ifBlank { "Not reported" },
                        "Last seen" to formatTimestamp(current.timestamp),
                    ),
                )
                Spacer(Modifier.height(14.dp))
                ProfileComparisonPanel(
                    profile = vm.localProfile(current.bssid),
                    comparison = vm.wifiProfileComparison(current),
                    emptyMessage = "Save this observed network as a private local baseline, then review changes on later scans.",
                    onSaveProfile = { vm.saveWifiProfile(current) },
                )
                Spacer(Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151C19)),
                    border = BorderStroke(1.dp, Color(0xFF72F5B2).copy(alpha = 0.28f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("LOCAL INVESTIGATION NOTE", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72F5B2))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Use Trust or Watch to influence future local assessments. Wi-Fi details do not initiate a connection, authentication, or traffic capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color(0xFF45F08A)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF45F08A)) }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun LoadingState(padding: androidx.compose.foundation.layout.PaddingValues) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(color = Color(0xFF45F08A)) }
}

@Composable
private fun DeviceHeading(name: String, subtitle: String, favourite: Boolean? = null, onFavourite: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9FFD9))
        if (favourite != null && onFavourite != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavourite) {
                    Icon(if (favourite) Icons.Default.Star else Icons.Default.StarBorder, "Favourite", tint = if (favourite) Color(0xFFFFD166) else Color.LightGray)
                }
                Text(if (favourite) "Favourite" else "Not favourite", color = if (favourite) Color(0xFFFFD166) else Color.LightGray)
            }
        }
    }
}

@Composable
private fun ReviewStatePanel(
    address: String,
    disposition: DeviceDisposition,
    ignored: Boolean,
    onDisposition: (DeviceDisposition) -> Unit,
    onIgnore: (() -> Unit)?,
) {
    val stateColor = when (disposition) {
        DeviceDisposition.TRUSTED -> Color(0xFF72F5B2)
        DeviceDisposition.WATCHLIST -> Color(0xFFFFD166)
        DeviceDisposition.UNREVIEWED -> Color.LightGray
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C19)),
        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("REVIEW STATE", style = MaterialTheme.typography.labelMedium, color = stateColor, fontWeight = FontWeight.Bold)
            Text("${disposition.name.lowercase().replaceFirstChar { it.uppercase() }} · ${if (ignored) "Ignored from future Bluetooth updates" else "Locally tracked"}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onDisposition(DeviceDisposition.TRUSTED) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B48))) { Text("TRUST") }
                Button(onClick = { onDisposition(DeviceDisposition.WATCHLIST) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF825D18))) { Text("WATCH") }
                TextButton(onClick = { onDisposition(DeviceDisposition.UNREVIEWED) }, modifier = Modifier.weight(0.7f)) { Text("CLEAR") }
            }
            onIgnore?.let {
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (ignored) "RESUME TRACKING" else "IGNORE FUTURE UPDATES")
                }
            }
        }
    }
}

@Composable
private fun BluetoothSignalPanel(device: BluetoothDeviceEntity) {
    val values = device.signalHistory.map { it.rssi }
    val average = values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val trend = signalTrend(values)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C19)),
        border = BorderStroke(1.dp, getSignalColor(device.rssi).copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("SIGNAL ANALYSIS", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72F5B2), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Current", "${device.rssi} dBm")
                Metric("Proximity", signalDistanceLabel(device.rssi))
                Metric("Risk", "${device.riskScore}/100")
            }
            Spacer(Modifier.height(10.dp))
            Text("Trend: $trend${average?.let { " · Recent average $it dBm" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            if (values.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    values.takeLast(12).forEach { point ->
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).fillMaxSize().background(getSignalColor(point).copy(alpha = 0.72f)))
                    }
                }
                Text("Recent sampled signal history", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun WifiPosturePanel(network: WifiNetwork) {
    val security = when {
        network.security.contains("WPA", true) -> "Encrypted network capability reported"
        network.security.contains("WEP", true) -> "Legacy WEP capability reported"
        else -> "No WPA or WEP capability marker reported"
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF151C19))) {
        Column(Modifier.padding(14.dp)) {
            Text("WI-FI POSTURE", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72F5B2), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Signal", "${network.signal} dBm")
                Metric("Proximity", signalDistanceLabel(network.signal))
                Metric("Risk", "${network.riskScore}/100")
            }
            Spacer(Modifier.height(10.dp))
            Text(security, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetadataPanel(entries: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF151C19))) {
        Column(Modifier.padding(14.dp)) {
            Text("DEVICE METADATA", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72F5B2), fontWeight = FontWeight.Bold)
            entries.forEach { (label, value) ->
                HorizontalDivider(modifier = Modifier.padding(top = 10.dp), color = Color.DarkGray)
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfileComparisonPanel(
    profile: LocalDeviceProfile?,
    comparison: DeviceProfileComparison?,
    emptyMessage: String,
    onSaveProfile: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12191B)),
        border = BorderStroke(1.dp, Color(0xFF72DFFF).copy(alpha = 0.42f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("LOCAL DEVICE PROFILE", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72DFFF), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (profile == null) {
                Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                onSaveProfile?.let { save ->
                    Button(
                        onClick = save,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B48)),
                    ) { Text("SAVE LOCAL PROFILE") }
                }
                return@Column
            }

            Text(
                "Baseline saved ${formatTimestamp(profile.savedAt)} · private to this phone",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB9FFD9),
            )
            profile.localLabel?.takeIf { it.isNotBlank() }?.let {
                Text("Local label: $it", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
            }
            profile.notes?.takeIf { it.isNotBlank() }?.let {
                Text("Notes retained in baseline", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
            }
            if (profile.signalPattern.isNotEmpty()) {
                val signalSummary = if (profile.signalPattern.size == 1) {
                    "Baseline signal: ${profile.signalPattern.first()} dBm"
                } else {
                    "Baseline signal pattern: ${profile.signalPattern.size} samples · average ${profile.signalPattern.average().roundToInt()} dBm · range ${profile.signalPattern.min()} to ${profile.signalPattern.max()} dBm"
                }
                Text(signalSummary, style = MaterialTheme.typography.labelSmall, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
            }
            if (comparison == null) return@Column

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(Modifier.height(8.dp))
            Text("CURRENT COMPARISON", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)

            var showedChange = false
            comparison.nameChanged?.let { (before, after) ->
                ComparisonLine("Name changed", "$before → $after", Color(0xFFFFD166))
                showedChange = true
            }
            comparison.securityChanged?.let { (before, after) ->
                ComparisonLine("Security capability changed", "$before → $after", Color(0xFFFF8A65))
                showedChange = true
            }
            comparison.addedServices.forEach { service ->
                ComparisonLine("New GATT service", service.label, Color(0xFF72F5B2))
                showedChange = true
            }
            comparison.removedServices.forEach { service ->
                ComparisonLine("GATT service not found", service.label, Color(0xFFFF8A65))
                showedChange = true
            }
            comparison.changedValues.forEach { value ->
                ComparisonLine("Safe value changed · ${value.label}", "${value.previous} → ${value.current}", Color(0xFFFFD166))
                showedChange = true
            }
            comparison.dispositionChanged?.let { (before, after) ->
                ComparisonLine(
                    "Review state changed",
                    "${before.name.lowercase().replaceFirstChar { it.uppercase() }} → ${after.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    Color(0xFF72DFFF),
                )
                showedChange = true
            }
            if (comparison.signalDelta != 0) {
                val signalText = if (comparison.signalDelta > 0) "${comparison.signalDelta} dBm stronger" else "${-comparison.signalDelta} dBm weaker"
                ComparisonLine("Signal movement", signalText, if (comparison.signalDelta > 0) Color(0xFF72DFFF) else Color(0xFFFFD166))
                showedChange = true
            }
            if (comparison.riskDelta != 0) {
                val riskText = if (comparison.riskDelta > 0) "+${comparison.riskDelta} points" else "${comparison.riskDelta} points"
                ComparisonLine("Risk posture", riskText, if (comparison.riskDelta > 0) Color(0xFFFF8A65) else Color(0xFF72F5B2))
                showedChange = true
            }
            if (!showedChange) {
                Text("No differences in the currently observed fields.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9FFD9), modifier = Modifier.padding(top = 4.dp))
            }
            if (profile.deviceType.name == "BLUETOOTH" && !comparison.gattCompared) {
                ComparisonLine("GATT topology", "Not compared yet — complete a new read-only discovery to check services.", Color.Gray)
            }
            if (profile.standardValues.isNotEmpty() && !comparison.valuesCompared) {
                ComparisonLine("Safe standard values", "Not compared yet — use the optional read-only value inspection.", Color.Gray)
            }
            if (abs(comparison.signalDelta) < 5) {
                Text("Signal is broadly stable within a 5 dBm tolerance.", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun ComparisonLine(label: String, detail: String, tint: Color) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
    }
}

@Composable
private fun GattExplorerPanel(
    address: String,
    vm: DeviceDetailsViewModel,
    hasSavedProfile: Boolean,
    onSaveProfile: () -> Unit,
) {
    val state = vm.gattState
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101B1B)),
        border = BorderStroke(1.dp, Color(0xFF00BCD4).copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("READ-ONLY BLE GATT EXPLORER", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72DFFF), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Uses up to three controlled connection attempts. Service discovery is read-only; optional inspection reads only a small list of standard attributes that the device marks as readable. It never writes, subscribes, pairs, or alters the device.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Spacer(Modifier.height(10.dp))
            when (state) {
                GattExplorationState.Idle -> Button(onClick = { vm.startGattDiscovery(address) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F))) { Text("DISCOVER GATT SERVICES") }
                is GattExplorationState.Connecting -> GattProgress("Connecting — attempt ${state.attempt} of ${state.maximumAttempts}…")
                is GattExplorationState.Retrying -> GattProgress("Retrying: ${state.reason}")
                is GattExplorationState.Discovering -> GattProgress("Discovering service topology — attempt ${state.attempt}…")
                is GattExplorationState.ReadingValues -> GattProgress("Reading approved standard values: ${state.completed} of ${state.total}…")
                is GattExplorationState.Error -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF8A65))
                    state.diagnostics?.let { Text("Diagnostics: ${it.message}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 6.dp)) }
                    OutlinedButton(onClick = { vm.retryGattDiscovery() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("TRY AGAIN") }
                }
                is GattExplorationState.Complete -> {
                    Text("${state.services.size} service(s) discovered · ${state.diagnostics.message}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF72F5B2))
                    OutlinedButton(onClick = { vm.inspectGattStandardValues() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(if (state.values.isEmpty()) "READ SAFE STANDARD VALUES" else "RE-READ SAFE STANDARD VALUES")
                    }
                    if (state.values.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        GattReadValuesPanel(state.values)
                    }
                    Button(
                        onClick = onSaveProfile,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B48)),
                    ) {
                        Text(if (hasSavedProfile) "UPDATE LOCAL PROFILE" else "SAVE TO LOCAL PROFILE")
                    }
                    Text(
                        "Saves this read-only discovery result only on this phone. It does not send data or alter the device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    state.services.forEach { service -> GattServiceCard(service) }
                    OutlinedButton(onClick = { vm.clearGattResult() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("CLOSE GATT RESULTS") }
                }
            }
        }
    }
}

@Composable
private fun GattReadValuesPanel(values: List<com.sophia.ops.bluetooth.GattReadValue>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071616)),
        border = BorderStroke(1.dp, Color(0xFF72F5B2).copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("READ-ONLY STANDARD VALUES", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72F5B2), fontWeight = FontWeight.Bold)
            values.forEach { result ->
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = Color.DarkGray)
                Text(result.label, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.padding(top = 7.dp))
                Text(result.value, style = MaterialTheme.typography.bodySmall, color = if (result.available) Color(0xFFB9FFD9) else Color(0xFFFFB199))
            }
        }
    }
}

@Composable
private fun GattProgress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF72DFFF))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Composable
private fun GattServiceCard(service: GattServiceInfo) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.22f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(service.label, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (service.primary) "PRIMARY SERVICE" else "SECONDARY SERVICE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF72DFFF), modifier = Modifier.padding(top = 3.dp))
            UuidIdentifierRow(service.uuid, "Standard assigned service identifier")
            service.characteristics.forEach { characteristic -> GattCharacteristicRow(characteristic) }
        }
    }
}

@Composable
private fun GattCharacteristicRow(characteristic: GattCharacteristicInfo) {
    Column(modifier = Modifier.padding(top = 12.dp, start = 4.dp)) {
        Text(characteristic.label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9FFD9), fontWeight = FontWeight.Medium)
        UuidIdentifierRow(characteristic.uuid, "Standard assigned characteristic identifier")
        Text("Properties: ${characteristic.properties.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, modifier = Modifier.padding(top = 3.dp))
        characteristic.descriptors.forEach { descriptor ->
            Column(modifier = Modifier.padding(start = 10.dp, top = 8.dp)) {
                Text(descriptor.label, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                UuidIdentifierRow(descriptor.uuid, "Standard assigned descriptor identifier", compact = true)
            }
        }
    }
}

@Composable
private fun UuidIdentifierRow(uuid: String, label: String, compact: Boolean = false) {
    val context = LocalContext.current
    var expanded by remember(uuid) { mutableStateOf(false) }
    val short = shortUuid(uuid)
    Column(modifier = Modifier.fillMaxWidth().padding(top = if (compact) 2.dp else 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (uuid.isStandardUuid()) "Standard Bluetooth assigned identifier" else "Unknown or proprietary identifier",
                style = MaterialTheme.typography.labelSmall,
                color = if (uuid.isStandardUuid()) Color(0xFF72DFFF) else Color(0xFFFFD166),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "HIDE ID" else "VIEW ID", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(short, style = MaterialTheme.typography.labelSmall, color = Color(0xFF72DFFF), fontWeight = FontWeight.Medium)
                    Text(uuid, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, uuid))
                }) { Text("COPY", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun String.isStandardUuid(): Boolean = Regex("^0000[0-9a-fA-F]{4}-0000-1000-8000-00805f9b34fb$").matches(this)

private fun shortUuid(uuid: String): String {
    val match = Regex("0000([0-9a-fA-F]{4})-0000-1000-8000-00805f9b34fb").find(uuid)
    return if (match != null) "0x${match.groupValues[1].uppercase()}" else "128-bit custom UUID"
}

@Composable
private fun NotesPanel(notes: String?, onEdit: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF151C19))) {
        Column(Modifier.padding(14.dp)) {
            Text("INVESTIGATION NOTES", style = MaterialTheme.typography.labelMedium, color = Color(0xFF72F5B2), fontWeight = FontWeight.Bold)
            Text(notes ?: "Tap to add local notes for this device.", style = MaterialTheme.typography.bodySmall, color = if (notes.isNullOrBlank()) Color.Gray else Color.White, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    multiline: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                minLines = if (multiline) 4 else 1,
                maxLines = if (multiline) 8 else 1,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

private fun signalDistanceLabel(rssi: Int): String = when {
    rssi >= -55 -> "Near"
    rssi >= -70 -> "Moderate"
    else -> "Distant"
}

private fun riskLabel(score: Int): String = when {
    score >= 70 -> "High"
    score >= 30 -> "Elevated"
    else -> "Low"
}

private fun signalTrend(values: List<Int>): String {
    if (values.size < 2) return "Not enough samples"
    val split = values.size / 2
    val older = values.take(split).average()
    val newer = values.drop(split).average()
    return when {
        newer - older >= 6 -> "Signal strengthening"
        older - newer >= 6 -> "Signal weakening"
        else -> "Signal stable"
    }
}

private fun formatTimestamp(timestamp: Long): String = DateUtils.getRelativeDateTimeString(
    null, timestamp, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0,
).toString()

private fun getSignalColor(rssi: Int): Color = when {
    rssi >= -55 -> Color(0xFF2BE574)
    rssi >= -75 -> Color(0xFFFFD166)
    else -> Color(0xFFFF6B6B)
}
