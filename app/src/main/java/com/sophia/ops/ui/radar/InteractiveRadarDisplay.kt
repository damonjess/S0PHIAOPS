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
import androidx.compose.ui.geometry.CornerRadius
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
import com.sophia.ops.ui.theme.SophiaThemeColors
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
                    containerColor = if (gestureMode == AtlasGestureMode.ROTATE) SophiaThemeColors.statusGreen else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (gestureMode == AtlasGestureMode.ROTATE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("Rotate")
            }
            Button(
                onClick = { gestureMode = AtlasGestureMode.PAN },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (gestureMode == AtlasGestureMode.PAN) SophiaThemeColors.statusGreen else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (gestureMode == AtlasGestureMode.PAN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("Pan")
            }
            Text(
                text = "${devices.size} signals",
                style = MaterialTheme.typography.labelLarge,
                color = SophiaThemeColors.statusBlue,
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
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { zoom = (zoom - 0.15f).coerceAtLeast(0.72f) }) { Text("−") }
            Text(
                text = "${(zoom * vm.radarRangeMultiplier * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = SophiaThemeColors.statusGreen,
            )
            OutlinedButton(onClick = { zoom = (zoom + 0.15f).coerceAtMost(1.75f) }) { Text("+") }
        }

        // Hoisted theme colors: the draw scope below is not composable.
        val mapBackground = MaterialTheme.colorScheme.background
        val gridColor = SophiaThemeColors.statusGreen
        val frameColor = SophiaThemeColors.statusBlue
        val labelTint = MaterialTheme.colorScheme.onSurfaceVariant

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
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
            val dimGrid = gridColor.copy(alpha = 0.10f)
            val ringColor = gridColor.copy(alpha = 0.30f)

            drawRoundRect(
                color = mapBackground,
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = CornerRadius(16.dp.toPx()),
            )

            // Range rings — outer ring brighter.
            for (ring in 1..5) {
                drawCircle(
                    color = if (ring == 5) ringColor else dimGrid,
                    radius = radius * ring / 5f,
                    center = center,
                    style = Stroke(width = if (ring == 5) 1.6f else 1f),
                )
            }

            // Cardinal spokes only: a full spoke every 30° made the field noisy.
            for (bearing in 0 until 360 step 90) {
                val radians = Math.toRadians((bearing + heading).toDouble())
                drawLine(
                    color = dimGrid,
                    start = center,
                    end = Offset(
                        center.x + sin(radians).toFloat() * radius,
                        center.y - cos(radians).toFloat() * radius,
                    ),
                    strokeWidth = 1.4f,
                )
            }

            // Short bearing ticks on the outer ring every 30°.
            for (bearing in 0 until 360 step 30) {
                val radians = Math.toRadians((bearing + heading).toDouble())
                val dirX = sin(radians).toFloat()
                val dirY = -cos(radians).toFloat()
                drawLine(
                    color = ringColor,
                    start = Offset(
                        center.x + dirX * (radius - 7.dp.toPx()),
                        center.y + dirY * (radius - 7.dp.toPx()),
                    ),
                    end = Offset(center.x + dirX * radius, center.y + dirY * radius),
                    strokeWidth = if (bearing % 90 == 0) 2f else 1.2f,
                )
            }

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11.dp.toPx()
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val cardinalPaint = Paint(textPaint).apply {
                textSize = 12.dp.toPx()
                color = frameColor.toArgb()
            }
            val cardinalOffset = radius + 15.dp.toPx()
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText("N", center.x - 4.dp.toPx(), center.y - cardinalOffset, cardinalPaint)
                canvas.nativeCanvas.drawText("E", center.x + cardinalOffset, center.y + 4.dp.toPx(), cardinalPaint)
                canvas.nativeCanvas.drawText("S", center.x - 4.dp.toPx(), center.y + cardinalOffset + 10.dp.toPx(), cardinalPaint)
                canvas.nativeCanvas.drawText("W", center.x - cardinalOffset - 10.dp.toPx(), center.y + 4.dp.toPx(), cardinalPaint)
            }

            // Own position: halo, dot, heading line.
            drawCircle(color = frameColor.copy(alpha = 0.22f), radius = 18.dp.toPx(), center = center)
            drawCircle(color = frameColor, radius = 5.dp.toPx(), center = center)
            drawLine(
                color = frameColor.copy(alpha = 0.8f),
                start = center,
                end = Offset(center.x, center.y - radius * 0.24f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )

            // Signals that earn a label: the selected target, the strongest few,
            // and favourites. Everything else stays label-free to keep the field clean.
            val selectedDevice = vm.selectedRadarDevice
            val labelIds = linkedSetOf<String>()
            selectedDevice?.let { labelIds.add(it.id) }
            if (vm.radarLabelsEnabled) {
                devices.sortedByDescending { it.signal }.take(6).forEach { labelIds.add(it.id) }
                devices.filter { it.favourite }.take(3).forEach { labelIds.add(it.id) }
            }

            devices.sortedBy { it.signal }.forEach { device ->
                val position = atlasPoint(device, size, heading, displayZoom, mapOffset)
                val color = markerColor(device)
                val isSelected = selectedDevice?.id == device.id
                val markerRadius = if (device.favourite) 8.dp.toPx() else 6.dp.toPx()

                if (isSelected) {
                    drawCircle(color = Color.White.copy(alpha = 0.55f), radius = markerRadius * 2.4f, center = position, style = Stroke(width = 2.5f))
                }
                drawCircle(color = color.copy(alpha = 0.14f), radius = markerRadius * 2.1f, center = position)
                drawCircle(color = color, radius = markerRadius, center = position)
            }

            // Labels drawn after the markers as chips, skipping any that would
            // overlap a label already placed (the selected target always draws).
            val chipFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val placedLabels = mutableListOf<android.graphics.RectF>()
            var placedCount = 0
            val labelPad = 4.dp.toPx()
            val chipRadius = 6.dp.toPx()

            devices.sortedByDescending { it.signal }.forEach { device ->
                if (device.id !in labelIds) return@forEach

                val position = atlasPoint(device, size, heading, displayZoom, mapOffset)
                val color = markerColor(device)
                val isSelected = selectedDevice?.id == device.id
                val name = device.name.take(if (isSelected) 24 else 16)
                val markerRadius = if (device.favourite) 8.dp.toPx() else 6.dp.toPx()

                textPaint.textSize = if (isSelected) 12.dp.toPx() else 11.dp.toPx()
                val textWidth = textPaint.measureText(name)
                val chipHeight = textPaint.textSize * 1.5f
                val chipWidth = textWidth + labelPad * 2

                var left = position.x + markerRadius + 7.dp.toPx()
                if (left + chipWidth > size.width - 3.dp.toPx()) {
                    left = position.x - markerRadius - 7.dp.toPx() - chipWidth
                }
                if (left < 3.dp.toPx()) left = 3.dp.toPx()
                val top = (position.y - chipHeight / 2f).coerceIn(3.dp.toPx(), size.height - chipHeight - 3.dp.toPx())
                val rect = android.graphics.RectF(left, top, left + chipWidth, top + chipHeight)

                if (!isSelected && placedLabels.any { android.graphics.RectF.intersects(it, rect) }) {
                    return@forEach
                }
                if (!isSelected) placedLabels.add(rect)
                placedCount++

                drawIntoCanvas { canvas ->
                    chipFill.color = android.graphics.Color.argb(if (isSelected) 232 else 175, 9, 13, 19)
                    canvas.nativeCanvas.drawRoundRect(rect, chipRadius, chipRadius, chipFill)
                    if (isSelected) {
                        chipFill.style = Paint.Style.STROKE
                        chipFill.strokeWidth = 1.5.dp.toPx()
                        chipFill.color = color.toArgb()
                        canvas.nativeCanvas.drawRoundRect(rect, chipRadius, chipRadius, chipFill)
                        chipFill.style = Paint.Style.FILL
                    }
                    textPaint.color = if (isSelected) android.graphics.Color.WHITE else color.toArgb()
                    val baseline = rect.top + chipHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                    canvas.nativeCanvas.drawText(name, rect.left + labelPad, baseline, textPaint)
                }
            }

            val hidden = devices.size - placedCount
            if (hidden > 0) {
                val hint = "+$hidden not labeled"
                textPaint.textSize = 10.dp.toPx()
                textPaint.color = labelTint.copy(alpha = 0.55f).toArgb()
                val hintWidth = textPaint.measureText(hint)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(hint, size.width - hintWidth - 8.dp.toPx(), size.height - 8.dp.toPx(), textPaint)
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
