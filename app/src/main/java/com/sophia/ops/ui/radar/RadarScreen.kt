package com.sophia.ops.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tactical Radar", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF00FF88))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))) {
                Text(" LIVE ", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.Green)
            }
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { viewModel.scan() }, colors = ButtonDefaults.buttonColors(Color(0xFF00E676))) {
                Text("Scanning...")
            }
            Spacer(Modifier.width(16.dp))
            Text("Current Threat: 4%", style = MaterialTheme.typography.bodyLarge)
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(Color(0xFF00FF88), "Wi-Fi")
            LegendDot(Color.Blue, "BT")
            LegendDot(Color.Yellow, "Fav")
        }

        Box(modifier = Modifier.fillMaxWidth().height(340.dp).padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = minOf(size.width, size.height) * 0.45f

                drawRadarBackground(center, radius)

                rotate(System.currentTimeMillis() % 360f) {
                    drawLine(Color(0xFF00FF88), center, Offset(center.x, center.y - radius * 0.9f), strokeWidth = 3f)
                }

                devices.forEachIndexed { index, device ->
                    val angle = index * 45f
                    val distance = radius * 0.65f
                    val x = center.x + distance * cos(angle * PI / 180f).toFloat()
                    val y = center.y + distance * sin(angle * PI / 180f).toFloat()

                    val color = if (device.type == com.sophia.ops.model.DeviceType.BLUETOOTH) Color.Blue else Color(0xFF00FF88)
                    drawCircle(color.copy(alpha = 0.4f), 18f, Offset(x, y))
                    drawCircle(color, 11f, Offset(x, y))
                }
            }
        }

        Text("Signal History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(devices) { device ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(device.name)
                        Text(device.address, color = Color.Gray)
                    }
                    Text("${device.signal}dBm", color = Color.Red)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2526))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SOPHIA SECURE ACTION AGENT", color = Color(0xFF00FF88), style = MaterialTheme.typography.titleMedium)
                Text("SOPHIA AI Engine Online. Awaiting threat metrics...", color = Color.Gray)

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.analyzeThreat() }, colors = ButtonDefaults.buttonColors(Color(0xFF00E676)), modifier = Modifier.weight(1f)) {
                        Text("LOCAL ADVICE")
                    }
                    Button(onClick = { viewModel.performGlobalIntelligenceSearch() }, colors = ButtonDefaults.buttonColors(Color(0xFF0288D1)), modifier = Modifier.weight(1f)) {
                        Text("GLOBAL INTEL")
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text("Ask SOPHIA", color = Color(0xFF00FF88))
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("e.g. Is it safe to use public Wi-Fi?") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(14.dp)) { drawCircle(color, 7f) }
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

private fun DrawScope.drawRadarBackground(center: Offset, radius: Float) {
    val gridColor = Color(0xFF00FF88).copy(alpha = 0.25f)
    for (i in 1..4) {
        drawCircle(gridColor, radius * i / 4f, center, style = Stroke(1.8f))
    }
    drawLine(gridColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.5f)
    drawLine(gridColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.5f)
}
