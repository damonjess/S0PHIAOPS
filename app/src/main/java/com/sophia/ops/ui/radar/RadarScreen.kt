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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.data.OuiLookup
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
        vm.startAutoRefresh(5000)
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tactical Radar",
                style = MaterialTheme.typography.headlineMedium
            )
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
                    vm.scan()
                },
                modifier = Modifier.scale(scanButtonScale)
            ) {
                Text(if (vm.isScanning) "Scanning..." else "Manual Scan")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Threat: ${vm.threatScore}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                LinearProgressIndicator(
                    progress = { threatProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = when {
                        vm.threatScore > 50 -> Color.Red
                        vm.threatScore > 20 -> Color.Yellow
                        else -> Color.Green
                    },
                    trackColor = Color.DarkGray
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            LegendItem("Wi-Fi", Color.Green)
            Spacer(modifier = Modifier.width(8.dp))
            LegendItem("BT", Color.Blue)
            Spacer(modifier = Modifier.width(8.dp))
            LegendItem("Fav", Color.Yellow)
        }

        val density = LocalDensity.current
        val devicesList = vm.allRadarDevices
        
        Spacer(
            modifier = Modifier
                .height(400.dp) // Fixed height for radar when scrolling
                .fillMaxWidth()
                .pointerInput(devicesList) {
                    detectTapGestures { tapOffset ->
                        val center = size.width / 2f
                        val minDim = minOf(size.width, size.height).toFloat()
                        
                        // Find if the tap is close to any device's calculated position
                        val clickedDevice = devicesList.find { device ->
                            // 1. Re-calculate the dot's X and Y based on your radar logic
                            val normalized = ((device.signal + 100).coerceIn(0, 100)).toFloat() / 100f
                            val radius = (minDim / 2.2f) * (1f - normalized)
                            val angleRad = device.radarAngle.toDouble() * PI / 180.0
                            
                            val dotX = center + (radius * cos(angleRad)).toFloat()
                            val dotY = center + (radius * sin(angleRad)).toFloat()
                            
                            // 2. Calculate distance between tap and dot
                            val distance = hypot((tapOffset.x - dotX).toDouble(), (tapOffset.y - dotY).toDouble())
                            
                            // 3. Define a touch target tolerance (e.g., 24dp in pixels)
                            val touchTolerance = with(density) { 24.dp.toPx() }
                            distance <= touchTolerance
                        }
                        
                        // Update the viewmodel with the clicked device (or null if they tapped empty space)
                        vm.selectDevice(clickedDevice)
                    }
                }
                .drawBehind {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.minDimension / 2

                    // Draw concentric circles
                    val circleCount = 4
                    for (i in 1..circleCount) {
                        drawCircle(
                            color = Color.Green.copy(alpha = 0.3f),
                            radius = maxRadius * (i.toFloat() / circleCount),
                            center = center,
                            style = Stroke(width = 2f)
                        )
                    }

                    // Draw radar lines
                    drawLine(
                        color = Color.Green.copy(alpha = 0.3f),
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color.Green.copy(alpha = 0.3f),
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 2f
                    )

                    // Draw sweep line
                    val sweepRad = (sweepAngle.toDouble() * PI / 180.0).toFloat()
                    val sweepX = center.x + maxRadius * cos(sweepRad.toDouble()).toFloat()
                    val sweepY = center.y + maxRadius * sin(sweepRad.toDouble()).toFloat()

                    drawLine(
                        color = Color.Green.copy(alpha = 0.5f),
                        start = center,
                        end = Offset(sweepX, sweepY),
                        strokeWidth = 4f
                    )

                    // Draw all devices (Wi-Fi and Bluetooth)
                    devicesList.forEach { device ->
                        val normalized =
                            ((device.signal + 100)
                                .coerceIn(0, 100))
                                .toFloat() / 100f

                        val radius =
                            (size.minDimension / 2.2f) *
                            (1f - normalized)

                        val angle = Math.toRadians(device.radarAngle.toDouble())

                        val x = center.x + radius * cos(angle).toFloat()
                        val y = center.y + radius * sin(angle).toFloat()

                        val color = if (device.type == com.sophia.ops.model.DeviceType.WIFI) {
                            // Wi-Fi Color based on risk score (0 = Green, 100 = Red)
                            Color(
                                red = (device.riskScore / 100f).coerceIn(0f, 1f),
                                green = (1f - (device.riskScore / 100f)).coerceIn(0f, 1f),
                                blue = 0f
                            )
                        } else {
                            // Bluetooth Color
                            if (device.favourite) {
                                Color.Yellow
                            } else {
                                Color(
                                    red = (device.riskScore / 100f).coerceIn(0f, 1f),
                                    green = 0f,
                                    blue = (1f - (device.riskScore / 100f)).coerceIn(0.5f, 1f)
                                )
                            }
                        }

                        drawCircle(
                            color = color,
                            radius = if (device.favourite || device.type == com.sophia.ops.model.DeviceType.WIFI) 8.dp.toPx() else 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
        )


        // Today's Activity Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(
                text = "Signal History",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.size(8.dp))
            
            if (vm.bluetoothDevices.isEmpty()) {
                Text(
                    text = "No signals detected yet...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else {
                // Show last 3 devices for brevity, or a scrollable list
                val exampleNotes = listOf(null, "Medical ECG", "Trusted", "Kitchen", "Temporary")
                vm.bluetoothDevices.takeLast(3).reversed().forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    vm.selectBluetoothDevice(device)
                                    // Cycle notes logic shifted to a double tap or long press? 
                                    // For now, let's just make click select, and maybe a specific button for notes?
                                    // Or just cycle on click and select.
                                    val currentIndex = exampleNotes.indexOf(device.notes)
                                    val nextIndex = (currentIndex + 1) % exampleNotes.size
                                    vm.updateNotes(device, exampleNotes[nextIndex])
                                }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val (riskText, riskColor) = when {
                                    device.riskScore > 50 -> "🔴 High" to Color.Red
                                    device.riskScore > 20 -> "🟠 Medium" to Color.Yellow
                                    else -> "🟢 Low" to Color.Green
                                }
                                
                                Text(
                                    text = riskText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = riskColor,
                                    modifier = Modifier.padding(end = 8.dp)
                                )

                                val displayName = when {
                                    !device.nickname.isNullOrBlank() -> device.nickname
                                    !device.name.isNullOrBlank() && !device.name.startsWith("Discovered Device") -> device.name
                                    else -> "Unknown Bluetooth Device"
                                }
                                
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                                device.notes?.let { note ->
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(note, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        
                        Row {
                            device.signalHistory.takeLast(5).forEach { point ->
                                Text(
                                    text = point.rssi.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        point.rssi > -60 -> Color.Green
                                        point.rssi > -80 -> Color.Yellow
                                        else -> Color.Red
                                    },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(16.dp))

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
                        "HIGH" -> Color.Red
                        "MEDIUM" -> Color.Yellow
                        else -> Color.Green
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SOPHIA Secure Action Agent Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (vm.aiInitializationFailed) Color(0xFF3A1C1C) else Color(0xFF1E1E1E)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (vm.isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Green)
                    } else if (!vm.isAiReady) {
                        Text(
                            text = if (vm.aiInitializationFailed) "⚠️ AI INITIALIZATION FAILED" else "🤖 AI ENGINE STANDBY",
                            color = if (vm.aiInitializationFailed) Color.Red else Color(0xFF00BCD4),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { vm.activateOnDeviceAI() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
                        ) {
                            Text("INITIALIZE COGNITIVE AI CORE")
                        }
                    } else {
                        Text(
                            text = "🤖 SOPHIA SECURE ACTION AGENT",
                            color = Color.Green,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = vm.aiAdviceText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
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
                            color = Color.Green,
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
                        color = Color.Gray
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
                        color = Color(0xFF00BCD4)
                    )
                    
                    Text(
                        text = "Threat Level: ${device.threatScore.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.threatScore > 50) Color.Red else Color.Yellow
                    )
                    
                    Text(
                        text = "Status: ${device.status}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )

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
                }
            }
        }

        // AI Analyst Section
        if (vm.aiResponse != null || vm.isAnalyzing) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cyber Analyst Countermeasures",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Green
                )
                if (!vm.isAnalyzing) {
                    Text(
                        text = "Refresh",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Green,
                        modifier = Modifier.clickable { vm.analyzeThreat() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (vm.isAnalyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Green,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Analyzing signals and generating strategies...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    } else {
                        Text(
                            text = vm.aiResponse ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Strategic Brief Section (High Threat Alert)
        if (vm.strategicBrief != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "TACTICAL STRATEGIC BRIEF",
                style = MaterialTheme.typography.titleSmall,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Color.Red),
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
fun StatItem(label: String, value: String, color: Color = Color.White) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
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
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
