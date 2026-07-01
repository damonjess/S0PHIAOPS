package com.sophia.ops.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    navController: NavController,
    vm: DashboardViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radar") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
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

                // Draw network points
                vm.networks.forEach { network ->
                    val normalizedSignal = ((network.signal + 100).coerceIn(0, 100)) / 100f
                    val radius = (size.minDimension / 2f) * (1f - normalizedSignal)

                    val angle = (network.bssid.hashCode().constant().toFloat() % 360) * (Math.PI / 180).toFloat()

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
            }
        }
    }
}