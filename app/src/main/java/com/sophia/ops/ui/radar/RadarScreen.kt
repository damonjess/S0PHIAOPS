package com.sophia.ops.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlin.math.*

@Composable
fun RadarScreen(viewModel: DashboardViewModel) {
    val devices by viewModel.devices.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // === FIXED TOP HUD ===
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TACTICAL RADAR", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF00FF88))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF00C853).copy(alpha = 0.2f))) {
                    Text(
                        text = " ${viewModel.status} ",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color(0xFF00FF41)
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.scan() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isScanning) Color.Red else Color(0xFF00C853))
                ) {
                    Text(if (viewModel.isScanning) "SCANNING..." else "START SCAN")
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "THREAT LEVEL: ${viewModel.threatLevel}",
                    color = if (viewModel.threatLevel == "HIGH") Color.Red else Color(0xFF00FF88),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Legend
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendDot(Color(0xFF00FF88), "Wi-Fi")
                LegendDot(Color(0xFF00B0FF), "BT")
                LegendDot(Color.Yellow, "Fav")
            }

            // Main Radar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(16.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = minOf(size.width, size.height) * 0.44f

                    drawTacticalRadarBackground(center, radius)

                    // Rotating Sweep (sector style like requested)
                    rotate(System.currentTimeMillis() % 2000 / 2000f * 360f) {
                        drawArc(
                            color = Color(0xFF00FF88).copy(alpha = 0.35f),
                            startAngle = -90f,
                            sweepAngle = 45f,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )
                        drawLine(Color(0xFF00FF88), center, Offset(center.x, center.y - radius * 0.95f), strokeWidth = 4f)
                    }

                    // Devices with pins
                    devices.forEach { device ->
                        val distFactor = ((device.signal.toFloat() + 105f) / 125f).coerceIn(0.15f, 0.9f)
                        val distance = radius * (1f - distFactor)
                        val angleRad = (device.radarAngle * PI / 180f).toFloat()

                        val x = center.x + distance * cos(angleRad)
                        val y = center.y + distance * sin(angleRad)

                        val color = when {
                            device.favourite -> Color.Yellow
                            device.type == com.sophia.ops.model.DeviceType.BLUETOOTH -> Color(0xFF00B0FF)
                            else -> Color(0xFF00FF88)
                        }

                        // Pin line
                        drawLine(color.copy(alpha = 0.6f), Offset(x, y + 8f), Offset(x, y - 25f), strokeWidth = 2f)
                        // Blip
                        drawCircle(color.copy(alpha = 0.9f), 11f, Offset(x, y))
                        drawCircle(Color.White, 4f, Offset(x, y))
                    }
                }
            }
        }

        // === SCROLLABLE BOTTOM SECTION ===
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("SIGNAL HISTORY", style = MaterialTheme.typography.titleMedium, color = Color(0xFF00FF88), modifier = Modifier.padding(bottom = 8.dp))

            devices.sortedByDescending { it.signal }.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(device.name, color = Color(0xFF00FF88), style = MaterialTheme.typography.bodyLarge)
                        Text(device.address.take(17) + "...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "${device.signal} dBm",
                        color = if (device.signal > -75) Color.Red else Color(0xFFFFC107)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // SOPHIA AI Section (Maintained from previous turns)
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1C1C))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SOPHIA SECURE ACTION AGENT", color = Color(0xFF00FF88), style = MaterialTheme.typography.titleLarge)
                    Text("AI Engine Online", color = Color.Gray)

                    Spacer(Modifier.height(16.dp))
                    
                    val displayAdvice = viewModel.chatAnswer ?: viewModel.aiAdviceText
                    Text(
                        text = displayAdvice,
                        color = if (viewModel.chatAnswer != null) Color.White else Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (viewModel.isAnalyzing || viewModel.isChatLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = Color(0xFF00FF88)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.analyzeThreat() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            enabled = viewModel.isAiReady && !viewModel.isAnalyzing
                        ) {
                            Text("LOCAL ADVICE")
                        }
                        Button(
                            onClick = { viewModel.performGlobalIntelligenceSearch() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                            enabled = viewModel.isAiReady && !viewModel.isAnalyzing
                        ) {
                            Text("GLOBAL INTEL")
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text("Ask SOPHIA", color = Color(0xFF00FF88), style = MaterialTheme.typography.titleMedium)

                    var questionText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        placeholder = { Text("e.g. Is it safe to use public Wi-Fi?") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                onClick = { 
                                    viewModel.askSophia(questionText)
                                    questionText = ""
                                },
                                enabled = questionText.isNotBlank() && !viewModel.isChatLoading
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF00FF88))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) { drawCircle(color, 6f) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

private fun DrawScope.drawTacticalRadarBackground(center: Offset, radius: Float) {
    val gridColor = Color(0xFF00FF88).copy(alpha = 0.25f)
    for (i in 1..5) {
        drawCircle(gridColor, radius * i / 5f, center, style = Stroke(2f))
    }
    drawLine(gridColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.5f)
    drawLine(gridColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.5f)
}
