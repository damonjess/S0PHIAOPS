package com.sophia.ops.ui.radar

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sophia.ops.model.DeviceType
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class AtlasGestureMode { ROTATE, PAN }

/**
 * A calm, static tactical signal map. It has no automated camera orbit or
 * sweep: every movement is initiated by the user through gestures or controls.
 */
@Composable
fun InteractiveRadarDisplay(
    vm: DashboardViewModel,
    devices: List<NetworkDevice>,
    modifier: Modifier = Modifier,
) {
    var heading by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }
    var gestureMode by remember { mutableStateOf(AtlasGestureMode.ROTATE) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Signal Atlas",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF72F5B2),
                )
                Text(
                    text = "Manual tactical map · no automatic movement",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = {
                    heading = 0f
                    zoom = 1f
                    mapOffset = Offset.Zero
                    gestureMode = AtlasGestureMode.ROTATE
                },
            ) {
                Text("Reset")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { gestureMode = AtlasGestureMode.ROTATE },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (gestureMode == AtlasGestureMode.ROTATE) Color(0xFF176B48) else Color(0xFF202B27),
                ),
            ) {
                Text("Rotate")
            }
            Button(
                onClick = { gestureMode = AtlasGestureMode.PAN },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (gestureMode == AtlasGestureMode.PAN) Color(0xFF176B48) else Color(0xFF202B27),
                ),
            ) {
                Text("Pan")
            }
            Text(
                text = "${devices.size} signals",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF8EDBFF),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { heading = normalizeAngle(heading - 15f) }) { Text("← 15°") }
            OutlinedButton(onClick = { heading = 0f }) { Text("North") }
            OutlinedButton(onClick = { heading = normalizeAngle(heading + 15f) }) { Text("15° →") }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { zoom = (zoom - 0.15f).coerceAtLeast(0.72f) }) { Text("−") }
            Text(
                text = "${(zoom * vm.radarRangeMultiplier * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF72F5B2),
            )
            OutlinedButton(onClick = { zoom = (zoom + 0.15f).coerceAtMost(1.75f) }) { Text("+") }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp)
                .padding(top = 12.dp)
                .pointerInput(gestureMode) {
                    detectTransformGestures { _, pan, zoomChange, rotationChange ->
                        zoom = (zoom * zoomChange).coerceIn(0.72f, 1.75f)
                        when (gestureMode) {
                            AtlasGestureMode.ROTATE -> heading = normalizeAngle(heading + pan.x * 0.32f + rotationChange)
                            AtlasGestureMode.PAN -> {
                                mapOffset = Offset(
                                    x = (mapOffset.x + pan.x).coerceIn(-110f, 110f),
                                    y = (mapOffset.y + pan.y).coerceIn(-110f, 110f),
                                )
                            }
                        }
                    }
                }
                .pointerInput(devices, heading, zoom, mapOffset, vm.radarRangeMultiplier) {
                    detectTapGestures { tap ->
                        val viewport = Size(size.width.toFloat(), size.height.toFloat())
                        val selected = devices.minByOrNull { device ->
                            val point = atlasPoint(device, viewport, heading, zoom * vm.radarRangeMultiplier, mapOffset)
                            hypot((tap.x - point.x).toDouble(), (tap.y - point.y).toDouble())
                        }?.takeIf { device ->
                            val point = atlasPoint(device, viewport, heading, zoom * vm.radarRangeMultiplier, mapOffset)
                            hypot((tap.x - point.x).toDouble(), (tap.y - point.y).toDouble()) <= 42.0
                        }
                        vm.selectDevice(selected)
                    }
                },
        ) {
            val displayZoom = zoom * vm.radarRangeMultiplier
            val center = Offset(size.width / 2f + mapOffset.x, size.height / 2f + mapOffset.y)
            val radius = size.minDimension * 0.40f * displayZoom
            val grid = Color(0xFF72F5B2).copy(alpha = 0.25f)
            val dimGrid = Color(0xFF72F5B2).copy(alpha = 0.10f)

            drawRoundRect(
                color = Color(0xFF09110E),
                topLeft = Offset(0f, 0f),
                size = size,
            )
            for (ring in 1..5) {
                val ringRadius = radius * ring / 5f
                drawCircle(
                    color = if (ring == 5) grid else dimGrid,
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = if (ring == 5) 2f else 1f),
                )
            }

            for (bearing in 0 until 360 step 30) {
                val radians = Math.toRadians((bearing + heading).toDouble())
                val outer = Offset(
                    center.x + sin(radians).toFloat() * radius,
                    center.y - cos(radians).toFloat() * radius,
                )
                drawLine(
                    color = if (bearing % 90 == 0) grid else dimGrid,
                    start = center,
                    end = outer,
                    strokeWidth = if (bearing % 90 == 0) 1.6f else 0.8f,
                )
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11.dp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
            }
            val cardinalPaint = Paint(textPaint).apply { color = Color(0xFFB9FFD9).toArgb() }
            val cardinalOffset = radius + 15.dp.toPx()
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText("N", center.x - 4.dp.toPx(), center.y - cardinalOffset, cardinalPaint)
                canvas.nativeCanvas.drawText("E", center.x + cardinalOffset, center.y + 4.dp.toPx(), cardinalPaint)
                canvas.nativeCanvas.drawText("S", center.x - 4.dp.toPx(), center.y + cardinalOffset + 10.dp.toPx(), cardinalPaint)
                canvas.nativeCanvas.drawText("W", center.x - cardinalOffset - 10.dp.toPx(), center.y + 4.dp.toPx(), cardinalPaint)
            }

            drawCircle(color = Color(0xFFB9FFD9), radius = 6.dp.toPx(), center = center)
            drawCircle(color = Color(0xFF72F5B2).copy(alpha = 0.22f), radius = 18.dp.toPx(), center = center)
            drawLine(
                color = Color(0xFFB9FFD9),
                start = center,
                end = Offset(center.x, center.y - radius * 0.24f),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )

            devices.sortedBy { it.signal }.forEach { device ->
                val position = atlasPoint(device, size, heading, displayZoom, mapOffset)
                val color = markerColor(device)
                val selected = vm.selectedRadarDevice?.id == device.id
                val markerRadius = if (device.favourite) 9.dp.toPx() else 6.5.dp.toPx()

                if (selected) {
                    drawCircle(color = Color.White.copy(alpha = 0.55f), radius = markerRadius * 2.35f, center = position, style = Stroke(width = 2.5f))
                }
                drawCircle(color = color.copy(alpha = 0.16f), radius = markerRadius * 2.2f, center = position)
                drawCircle(color = color, radius = markerRadius, center = position)

                if (vm.radarLabelsEnabled) {
                    textPaint.color = color.toArgb()
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            device.name.take(18),
                            position.x + markerRadius + 5.dp.toPx(),
                            position.y - markerRadius,
                            textPaint,
                        )
                    }
                }
            }
        }

        Text(
            text = "Heading ${heading.toInt().toString().padStart(3, '0')}°  •  ${if (gestureMode == AtlasGestureMode.ROTATE) "drag to rotate" else "drag to pan"}  •  pinch to zoom",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun atlasPoint(
    device: NetworkDevice,
    viewport: Size,
    heading: Float,
    zoom: Float,
    mapOffset: Offset,
): Offset {
    val center = Offset(viewport.width / 2f + mapOffset.x, viewport.height / 2f + mapOffset.y)
    val radius = minOf(viewport.width, viewport.height) * 0.40f * zoom
    val normalizedSignal = ((device.signal + 100).coerceIn(0, 100)) / 100f
    val distance = (0.96f - normalizedSignal * 0.78f).coerceIn(0.12f, 0.96f)
    val radians = Math.toRadians((device.radarAngle + heading).toDouble())
    return Offset(
        x = center.x + sin(radians).toFloat() * radius * distance,
        y = center.y - cos(radians).toFloat() * radius * distance,
    )
}

private fun markerColor(device: NetworkDevice): Color = when {
    device.favourite -> Color(0xFFFFDD78)
    device.type == DeviceType.WIFI && device.riskScore >= 60 -> Color(0xFFFF716B)
    device.type == DeviceType.WIFI -> Color(0xFF72F5B2)
    device.riskScore >= 60 -> Color(0xFFFFA76B)
    else -> Color(0xFF70C8FF)
}

private fun normalizeAngle(value: Float): Float = ((value % 360f) + 360f) % 360f

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
