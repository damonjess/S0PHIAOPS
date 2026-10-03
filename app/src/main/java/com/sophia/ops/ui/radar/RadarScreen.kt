package com.sophia.ops.ui.radar

import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.data.OuiLookup
import com.sophia.ops.data.DeviceDisposition
import com.sophia.ops.ai.AiAssessment
import com.sophia.ops.ai.AssessmentSeverity
import com.sophia.ops.ai.ChatTurn
import com.sophia.ops.ui.theme.SophiaThemeColors
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun RadarScreen(
    vm: DashboardViewModel,
    onDeviceClick: (NetworkDevice) -> Unit = {},
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    DisposableEffect(vm) {
        vm.startAutoRefresh()
        onDispose {
            vm.stopAutoRefresh()
        }
    }

    val scansToday by vm.scansToday.collectAsState()
    val wifiFoundToday by vm.wifiFoundToday.collectAsState()
    val bluetoothFoundToday by vm.bluetoothFoundToday.collectAsState()
    val highestThreatToday by vm.highestThreatToday.collectAsState()

    val scanButtonScale by animateFloatAsState(
        targetValue = if (vm.isScanning) 1.1f else 1f,
        label = "ScanButtonScale"
    )
    
    val threatProgress by animateFloatAsState(
        targetValue = vm.threatScore / 100f,
        label = "ThreatProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Signal Atlas",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Scan, map, and assess nearby signals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = { },
                label = {
                    Text(vm.status)
                }
            )
        }

        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (vm.isScanning) vm.stopCurrentScan()
                    else vm.scan(force = true)
                },
                modifier = Modifier.scale(scanButtonScale),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (vm.isScanning) "STOP SCAN" else "START SCAN")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Threat: ${vm.threatScore}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { threatProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = when {
                        vm.threatScore > 50 -> SophiaThemeColors.statusRed
                        vm.threatScore > 20 -> SophiaThemeColors.statusYellow
                        else -> SophiaThemeColors.statusGreen
                    },
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            LegendItem("Wi-Fi", SophiaThemeColors.statusGreen)
            Spacer(modifier = Modifier.width(8.dp))
            LegendItem("BT", SophiaThemeColors.statusBlue)
            Spacer(modifier = Modifier.width(8.dp))
            LegendItem("Fav", SophiaThemeColors.statusYellow)
        }

        val devicesList = vm.allRadarDevices
        InteractiveRadarDisplay(
            vm = vm,
            devices = devicesList,
        )

        // Today's Activity Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            if (vm.signalHistoryVisible) {
                SignalHistoryPanel(vm)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "Today's Activity",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.size(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Scans Today", scansToday)
                StatItem("Wi-Fi Found", wifiFoundToday)
            }
            
            Spacer(modifier = Modifier.size(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Bluetooth Found", bluetoothFoundToday)
                StatItem(
                    label = "Highest Threat",
                    value = highestThreatToday,
                    color = when(highestThreatToday) {
                        "HIGH" -> SophiaThemeColors.statusRed
                        "MEDIUM" -> SophiaThemeColors.statusYellow
                        else -> SophiaThemeColors.statusGreen
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SOPHIA Secure Action Agent Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (vm.aiInitializationFailed) MaterialTheme.colorScheme.errorContainer else SophiaThemeColors.cardContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (vm.isAiLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SophiaThemeColors.statusGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Loading Engine...", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (vm.isDownloading) {
                        Text(
                            text = "Downloading on-device model",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { vm.downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = SophiaThemeColors.statusGreen,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "${(vm.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    } else if (!vm.isAiReady) {
                        Text(
                            text = if (vm.aiInitializationFailed) "AI initialization failed" else "AI engine on standby",
                            color = if (vm.aiInitializationFailed) SophiaThemeColors.statusRed else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = vm.aiAdviceText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { vm.activateOnDeviceAI() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Initialize")
                            }
                            
                            if (!vm.isModelPresent) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { vm.downloadModel() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline)
                                ) {
                                    Text("Download")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "On-device assistant",
                            color = SophiaThemeColors.statusGreen,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = vm.aiAdviceText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { vm.analyzeThreat() },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                enabled = !vm.isAnalyzing
                            ) {
                                Text(if (vm.isAnalyzing) "Analyzing..." else "Local advice")
                            }

                            Button(
                                onClick = { vm.performGlobalIntelligenceSearch() },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                enabled = !vm.isAnalyzing && vm.isAiReady
                            ) {
                                Text("GUIDANCE", color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            AskSophiaCard(vm)
        }

        // Device Timeline / Detail Card Section
        vm.selectedRadarDevice?.let { device ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target: ${device.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = SophiaThemeColors.statusGreen,
                            modifier = Modifier.clickable { onDeviceClick(device) }
                        )
                        IconButton(onClick = { vm.selectDevice(null) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                    
                    Text(
                        text = "Address: ${device.address}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "IP Address: ${device.ipAddress}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    val context = LocalContext.current
                    val vendorName = remember(device.address) {
                        OuiLookup.getVendor(context, device.address)
                    }
                    Text(
                        text = "Vendor: $vendorName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Threat Level: ${device.threatScore.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.threatScore > 50) SophiaThemeColors.statusRed else SophiaThemeColors.statusYellow
                    )
                    
                    Text(
                        text = "Status: ${device.status}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    val disposition = vm.deviceDisposition(device.address)
                    Text(
                        text = "Review state: ${disposition.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (disposition) {
                            DeviceDisposition.TRUSTED -> SophiaThemeColors.statusGreen
                            DeviceDisposition.WATCHLIST -> SophiaThemeColors.statusYellow
                            DeviceDisposition.UNREVIEWED -> Color.LightGray
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = { vm.setDeviceDisposition(device.address, DeviceDisposition.TRUSTED) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SophiaThemeColors.statusGreen.copy(alpha = 0.18f), contentColor = SophiaThemeColors.statusGreen),
                        ) { Text("Trust") }
                        Button(
                            onClick = { vm.setDeviceDisposition(device.address, DeviceDisposition.WATCHLIST) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SophiaThemeColors.statusYellow.copy(alpha = 0.18f), contentColor = SophiaThemeColors.statusYellow),
                        ) { Text("Watch") }
                        TextButton(
                            onClick = { vm.setDeviceDisposition(device.address, DeviceDisposition.UNREVIEWED) },
                            modifier = Modifier.weight(0.7f),
                        ) { Text("Clear") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimelineItem(
                            "First Seen", 
                            DateUtils.getRelativeTimeSpanString(device.firstSeen).toString()
                        )
                        TimelineItem(
                            "Last Seen", 
                            DateUtils.getRelativeTimeSpanString(device.lastSeen).toString()
                        )
                        TimelineItem(
                            "Seen", 
                            "${device.timesSeen} Times"
                        )
                    }

                    // Deep Scan Section
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (vm.deepScanResult == null && !vm.isDeepScanning) {
                        Button(
                            onClick = { vm.performDeepScan(device) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = vm.isAiReady
                        ) {
                            Text("Deep scan target")
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (vm.isDeepScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = "Target analysis",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = vm.deepScanResult ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Structured local assessment
        if (vm.aiAssessment != null || vm.aiResponse != null || vm.isAnalyzing) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Local Assessment",
                    style = MaterialTheme.typography.titleMedium,
                    color = SophiaThemeColors.statusGreen,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.analyzeThreat() }, enabled = !vm.isAnalyzing) {
                    Text("Refresh", color = SophiaThemeColors.statusGreen, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (vm.isAnalyzing) {
                AnalystLoadingCard()
            } else {
                vm.aiAssessment?.let { assessment -> StructuredAssessmentCard(assessment) } ?: Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SophiaThemeColors.cardContainer),
                ) {
                    Text(
                        text = vm.aiResponse ?: "No assessment is available.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }

        // Strategic Brief Section (High Threat Alert)
        if (vm.strategicBrief != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Strategic brief",
                style = MaterialTheme.typography.titleSmall,
                color = SophiaThemeColors.statusRed,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, SophiaThemeColors.statusRed),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = vm.strategicBrief ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}


@Composable
private fun SignalHistoryPanel(vm: DashboardViewModel) {
    val context = LocalContext.current
    val devices = vm.bluetoothDevices
        .sortedByDescending { it.lastSeen }
        .take(vm.signalHistoryLimit)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SophiaThemeColors.cardContainer),
        border = BorderStroke(1.dp, SophiaThemeColors.statusGreen.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Signal History",
                    style = MaterialTheme.typography.titleMedium,
                    color = SophiaThemeColors.statusGreen,
                )
                Text(
                    text = "Persisted",
                    style = MaterialTheme.typography.labelSmall,
                    color = SophiaThemeColors.statusBlue,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "No saved signals yet. Run a scan to begin the timeline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                devices.forEach { device ->
                    val vendor = remember(device.address) { OuiLookup.getVendor(context, device.address) }
                    val displayName = when {
                        !device.nickname.isNullOrBlank() -> device.nickname
                        !device.name.isNullOrBlank() && !device.name.startsWith("Discovered Device") && !device.name.contains("Unknown", true) -> device.name
                        vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)" -> vendor
                        else -> "Unknown Bluetooth Device"
                    }
                    val latestRssi = device.signalHistory.lastOrNull()?.rssi ?: device.rssi
                    val riskColor = when {
                        device.riskScore > 50 -> SophiaThemeColors.statusRed
                        device.riskScore > 20 -> SophiaThemeColors.statusYellow
                        else -> SophiaThemeColors.statusGreen
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectBluetoothDevice(device) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SignalSparkline(
                            points = device.signalHistory.map { it.rssi },
                            color = riskColor,
                            modifier = Modifier
                                .width(74.dp)
                                .height(28.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                            )
                            Text(
                                text = "${DateUtils.getRelativeTimeSpanString(device.lastSeen)}  •  ${device.signalHistory.size} samples",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$latestRssi dBm",
                                style = MaterialTheme.typography.labelLarge,
                                color = riskColor,
                            )
                            Text(
                                text = "Risk ${device.riskScore}",
                                style = MaterialTheme.typography.labelSmall,
                                color = riskColor.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalSparkline(
    points: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val mutedLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (points.isEmpty()) {
            drawLine(
                color = mutedLineColor.copy(alpha = 0.35f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2f,
            )
            return@Canvas
        }

        val minRssi = -100f
        val maxRssi = -30f
        val step = if (points.size == 1) 0f else size.width / (points.size - 1)
        points.forEachIndexed { index, value ->
            if (index == 0) return@forEachIndexed
            val previous = points[index - 1]
            val x1 = step * (index - 1)
            val x2 = step * index
            val y1 = size.height - ((previous.coerceIn(-100, -30) - minRssi) / (maxRssi - minRssi)) * size.height
            val y2 = size.height - ((value.coerceIn(-100, -30) - minRssi) / (maxRssi - minRssi)) * size.height
            drawLine(
                color = color,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        val finalValue = points.last().coerceIn(-100, -30)
        val finalY = size.height - ((finalValue - minRssi) / (maxRssi - minRssi)) * size.height
        drawCircle(color = color, radius = 3.5f, center = Offset(size.width, finalY))
    }
}

@Composable
fun TimelineItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

@Composable
fun StatItem(label: String, value: Int, color: Color = Color.White) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        label = "StatValue"
    )
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = animatedValue.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AskSophiaCard(vm: DashboardViewModel) {
    var questionText by remember { mutableStateOf("") }
    // When the keyboard opens, scroll the Ask button above the IME so it stays tappable.
    val askButtonView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = SophiaThemeColors.cardContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ask S0PHIA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            scope.launch {
                                delay(250)
                                askButtonView.bringIntoView()
                            }
                        }
                    },
                placeholder = { Text("e.g. Is it safe to use public Wi-Fi?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = !vm.isChatLoading,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    vm.askSophia(questionText)
                    questionText = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(askButtonView),
                enabled = !vm.isChatLoading && questionText.isNotBlank() && vm.isAiReady,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (vm.isChatLoading) "Thinking..." else "Ask")
            }

            if (vm.chatHistory.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.chatHistory.takeLast(6).forEach { turn ->
                        val isUser = turn.role == ChatTurn.ROLE_USER
                        Text(
                            text = if (isUser) "You: ${turn.text}" else "S0PHIA: ${turn.text}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (vm.isChatLoading) {
                        Text(
                            text = "S0PHIA: thinking…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (vm.isChatLoading) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "S0PHIA: thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnalystLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SophiaThemeColors.cardContainer),
        border = BorderStroke(1.dp, SophiaThemeColors.statusGreen.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = SophiaThemeColors.statusGreen,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Comparing the current scan with the local baseline…",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun StructuredAssessmentCard(assessment: AiAssessment) {
    val severityColor = when (assessment.severity) {
        AssessmentSeverity.CRITICAL -> SophiaThemeColors.statusRed
        AssessmentSeverity.HIGH -> SophiaThemeColors.statusOrange
        AssessmentSeverity.MEDIUM -> SophiaThemeColors.statusYellow
        AssessmentSeverity.LOW -> SophiaThemeColors.statusGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SophiaThemeColors.cardContainer),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = assessment.severity.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = severityColor,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Confidence: ${assessment.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SophiaThemeColors.statusGreen,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = assessment.headline,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )

            AssessmentSection("Evidence") {
                assessment.evidence.forEach { item ->
                    EvidenceRow(item.label, item.detail, SophiaThemeColors.statusBlue)
                }
            }
            AssessmentSection("What changed") {
                assessment.changes.forEach { item ->
                    EvidenceRow(item.label, item.detail, SophiaThemeColors.statusGreen)
                }
            }
            AssessmentSection("Uncertainty") {
                Text(
                    text = assessment.uncertainty,
                    style = MaterialTheme.typography.bodySmall,
                    color = SophiaThemeColors.statusYellow,
                )
            }
            AssessmentSection("Recommended next step") {
                Text(
                    text = assessment.recommendedAction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                if (assessment.requiresConfirmation) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Review required before taking any action that changes a device or connection.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SophiaThemeColors.statusGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = SophiaThemeColors.statusGreen,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(5.dp))
    content()
}

@Composable
private fun EvidenceRow(label: String, detail: String, accent: Color) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "•",
            color = accent,
            modifier = Modifier.padding(end = 6.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
            )
        }
    }
}
