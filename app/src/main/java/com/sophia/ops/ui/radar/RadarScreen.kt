package com.sophia.ops.ui.radar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScreen(
    vm: DashboardViewModel
) {
    var sweepAngle by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(Unit) {
        while (true) {
            sweepAngle += 3f
            delay(16)
        }
    }

    LaunchedEffect(Unit) {
        vm.startAutoRefresh(5000)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tactical Radar",
                style = MaterialTheme.typography.headlineMedium
            )
            Button(
                onClick = {
                    vm.scan()
                }
            ) {
                Text("Scan")
            }
        }

        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Status: ",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = if (vm.lastScanWasLive) "LIVE" else "THROTTLED",
                style = MaterialTheme.typography.bodyMedium,
                color = if (vm.lastScanWasLive) Color.Green else Color.Red,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            LegendItem("Wi-Fi", Color.Green)
            Spacer(modifier = Modifier.width(8.dp))
            LegendItem("BT", Color.Blue)
        }

        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
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
            val sweepRad = (sweepAngle * Math.PI / 180).toFloat()
            val sweepX = center.x + maxRadius * cos(sweepRad)
            val sweepY = center.y + maxRadius * sin(sweepRad)

            drawLine(
                color = Color.Green.copy(alpha = 0.5f),
                start = center,
                end = Offset(sweepX, sweepY),
                strokeWidth = 4f
            )

            // Draw network points
            vm.networks.forEach { network ->
                val normalized =
                    ((network.signal + 100)
                        .coerceIn(0, 100))
                        .toFloat() / 100f

                val radius =
                    (size.minDimension / 2.2f) *
                    (1f - normalized)

                val baseAngle = (network.bssid.hashCode().toFloat() % 360)
                val angle = (baseAngle + network.angularOffset) * (Math.PI / 180).toFloat()

                val x = center.x + radius * cos(angle)
                val y = center.y + radius * sin(angle)

                // Color based on risk score (0 = Green, 100 = Red)
                val color = Color(
                    red = (network.riskScore / 100f).coerceIn(0f, 1f),
                    green = (1f - (network.riskScore / 100f)).coerceIn(0f, 1f),
                    blue = 0f
                )

                drawCircle(
                    color = color,
                    radius = 8.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            // Draw Bluetooth devices
            vm.bluetoothDevices.forEach { device ->
                val radius = (size.minDimension / 2.5f) * (0.5f + (device.address.hashCode().toFloat() % 50) / 100f)
                val angle = (device.address.hashCode().toFloat() % 360) * (Math.PI / 180).toFloat()

                val x = center.x + radius * cos(angle)
                val y = center.y + radius * sin(angle)

                val color = Color(
                    red = (device.riskScore / 100f).coerceIn(0f, 1f),
                    green = 0f,
                    blue = (1f - (device.riskScore / 100f)).coerceIn(0.5f, 1f)
                )

                drawCircle(
                    color = color,
                    radius = 6.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
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
