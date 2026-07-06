package com.sophia.ops.ui.radar

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sophia.ops.model.DeviceType
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun RadarScreen(viewModel: DashboardViewModel) {
    val devices by viewModel.devices.collectAsState()
    var rotation by remember { mutableStateOf(0f) }
    var zoom by remember { mutableStateOf(1f) }
    val selectedDevice = viewModel.selectedRadarDevice
    
    var filterType by remember { mutableStateOf<DeviceType?>(null) } // null = All

    val textMeasurer = rememberTextMeasurer()

    // Sweep animation
    LaunchedEffect(Unit) {
        while (true) {
            rotation = (rotation + 2f) % 360f
            delay(16)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TACTICAL RADAR",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF88)
                    )
                    Text(
                        text = "${devices.size} TARGETS IDENTIFIED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00FF88).copy(alpha = 0.6f)
                    )
                }
                
                // Threat Level
                Surface(
                    color = when (viewModel.threatLevel) {
                        "HIGH" -> Color.Red.copy(alpha = 0.2f)
                        "MEDIUM" -> Color.Yellow.copy(alpha = 0.2f)
                        else -> Color(0xFF00FF88).copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, when (viewModel.threatLevel) {
                        "HIGH" -> Color.Red
                        "MEDIUM" -> Color.Yellow
                        else -> Color(0xFF00FF88)
                    })
                ) {
                    Text(
                        text = "THREAT: ${viewModel.threatLevel}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (viewModel.threatLevel) {
                            "HIGH" -> Color.Red
                            "MEDIUM" -> Color.Yellow
                            else -> Color(0xFF00FF88)
                        }
                    )
                }
            }

            // Tabs / Filters
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("ALL") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00FF88).copy(alpha = 0.2f), selectedLabelColor = Color(0xFF00FF88))
                )
                FilterChip(
                    selected = filterType == DeviceType.WIFI,
                    onClick = { filterType = DeviceType.WIFI },
                    label = { Text("WI-FI") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF00FF88).copy(alpha = 0.2f), selectedLabelColor = Color(0xFF00FF88))
                )
                FilterChip(
                    selected = filterType == DeviceType.BLUETOOTH,
                    onClick = { filterType = DeviceType.BLUETOOTH },
                    label = { Text("BLUETOOTH") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Cyan.copy(alpha = 0.2f), selectedLabelColor = Color.Cyan)
                )
            }

            // Radar Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(0.6f, 3.0f)
                        }
                    }
                    .pointerInput(devices, filterType) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val baseRadius = minOf(size.width, size.height) * 0.45f * zoom
                            
                            var closest: NetworkDevice? = null
                            var minDist = 50f
                            
                            devices.forEach { device ->
                                if (filterType != null && device.type != filterType) return@forEach
                                
                                val angle = device.radarAngle
                                val normalizedSignal = ((device.signal + 100f) / 120f).coerceIn(0.1f, 0.95f)
                                val radius = baseRadius * (1 - normalizedSignal)

                                val x = center.x + radius * cos(angle * PI / 180).toFloat()
                                val y = center.y + radius * sin(angle * PI / 180).toFloat()
                                
                                val dist = sqrt((offset.x - x).pow(2) + (offset.y - y).pow(2))
                                if (dist < minDist) {
                                    minDist = dist
                                    closest = device
                                }
                            }
                            viewModel.selectDevice(closest)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val baseRadius = minOf(size.width, size.height) * 0.45f * zoom

                    drawTacticalBackground(center, baseRadius)

                    // Sweep
                    rotate(rotation) {
                        drawLine(
                            color = Color(0xFF00FFAA),
                            start = center,
                            end = Offset(center.x, center.y - baseRadius),
                            strokeWidth = 3f,
                            alpha = 0.6f
                        )
                    }

                    // Dots
                    devices.forEach { device ->
                        if (filterType != null && device.type != filterType) return@forEach

                        val angle = device.radarAngle
                        val normalizedSignal = ((device.signal + 100f) / 120f).coerceIn(0.1f, 0.95f)
                        val radius = baseRadius * (1 - normalizedSignal)

                        val x = center.x + radius * cos(angle * PI / 180).toFloat()
                        val y = center.y + radius * sin(angle * PI / 180).toFloat()

                        val isSelected = selectedDevice?.address == device.address
                        val blipColor = if (isSelected) Color.White else if (device.type == DeviceType.BLUETOOTH) 
                            Color.Cyan else Color(0xFF00FF88)

                        drawCircle(blipColor.copy(alpha = 0.4f), if (isSelected) 18f else 12f, Offset(x, y))
                        drawCircle(blipColor, if (isSelected) 8f else 6f, Offset(x, y))
                        
                        if (isSelected || zoom > 1.5f) {
                            val label = device.name.take(12)
                            drawText(
                                textMeasurer = textMeasurer,
                                text = label,
                                topLeft = Offset(x + 12, y - 10),
                                style = TextStyle(color = Color.White, fontSize = 10.sp, background = Color.Black.copy(alpha = 0.5f))
                            )
                        }
                    }

                    drawCircle(Color.White, 6f, center)
                }
            }

            // AI Analyst Panel (Always at the bottom)
            AiAnalystPanel(viewModel)
            
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Detailed Overlay (When dot is clicked)
        if (selectedDevice != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.selectDevice(null) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                DeviceDetailOverlay(
                    device = selectedDevice,
                    viewModel = viewModel,
                    onClose = { viewModel.selectDevice(null) }
                )
            }
        }
    }
}

@Composable
fun AiAnalystPanel(viewModel: DashboardViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A100D).copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF00FF88),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SOPHIA AI ANALYST",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF00FF88),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (viewModel.isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF00FF88), strokeWidth = 2.dp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.performGlobalIntelligenceSearch() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Public, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.analyzeThreat() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.AutoGraph, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!viewModel.isAiReady) {
                Text(
                    text = viewModel.aiAdviceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Start
                )
                TextButton(onClick = { viewModel.activateOnDeviceAI() }) {
                    Text("ACTIVATE ENGINE", color = Color(0xFF00FF88))
                }
            } else {
                Text(
                    text = viewModel.aiAdviceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun DeviceDetailOverlay(
    device: NetworkDevice,
    viewModel: DashboardViewModel,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Target: ${device.name}", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF00FF88), fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Address: ${device.address}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text("Vendor: ${device.vendor}", color = Color(0xFF00BCD4), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("Threat Level: ${device.riskScore}%", color = Color.Yellow, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailColumn("First Seen", DateUtils.getRelativeTimeSpanString(device.firstSeen).toString(), Modifier.weight(1f))
                DetailColumn("Last Seen", DateUtils.getRelativeTimeSpanString(device.lastSeen).toString(), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.performDeepScan(device) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B8D4))) {
                Text("DEEP SCAN TARGET", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.performFullRecon(device) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA))) {
                Text("DEEP RECON (PORT SCAN)", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            if (viewModel.reconStatus.isNotEmpty() || !viewModel.deepScanResult.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                    Text(text = viewModel.deepScanResult ?: viewModel.reconStatus, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun DetailColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

private fun DrawScope.drawTacticalBackground(center: Offset, radius: Float) {
    val gridColor = Color(0xFF00FF88).copy(alpha = 0.15f)
    for (i in 1..4) drawCircle(gridColor, radius * i / 4f, center, style = Stroke(1f))
    drawLine(gridColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1f)
    drawLine(gridColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1f)
}
