package com.sophia.ops.ui.radar

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sophia.ops.model.DeviceType
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private enum class AtlasGestureMode { ROTATE, PAN }

// ── Palette (sampled from the reference mock) ────────────────────────────────
private val SpatialPanelBackground = Color(0xFF03080E)
private val CyanBright = Color(0xFF6BE6FF)
private val CyanMid = Color(0xFF3EC7F0)
private val RingGreen = Color(0xFF57EE8C)
private val WifiGreen = Color(0xFF3FD878)
private val LimeLine = Color(0xFFB4F26A)
private val BluetoothBlue = Color(0xFF35BDEF)
private val FavAmber = Color(0xFFFFC94D)
private val RiskRed = Color(0xFFFF6B63)
private val RiskOrange = Color(0xFFFFA76B)
private val NodeOutline = Color(0xFF06121C)
private val LegendText = Color(0xCCBFE9FF)

// ── Layout, as fractions of the canvas' smaller side / of the main ring ──────
private const val RING_FRACTION = 0.34f    // main "10m" ring radius
private const val COMPASS_FRACTION = 0.32f // compass disc, relative to main ring
private const val MID_RING = 0.57f         // "5m" ring, relative to main ring
private const val HALO = 1.22f             // thin outer halo ring, relative to main ring

/**
 * Spatial network map styled after the operator reference display.
 * Displays real scanned Wi-Fi and Bluetooth devices.
 */
@Composable
fun InteractiveRadarDisplay(
    vm: DashboardViewModel,
    devices: List<NetworkDevice>,
    modifier: Modifier = Modifier,
    sweepAngle: Float = 220f,
) {
    var heading by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }
    var gestureMode by remember { mutableStateOf(AtlasGestureMode.ROTATE) }
    var utcTime by remember { mutableStateOf("--:--:-- UTC") }

    LaunchedEffect(Unit) {
        val format = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        while (true) {
            utcTime = format.format(Date())
            delay(1000)
        }
    }

    val displayDevices = devices

    // Rank-based radial placement: spreads nodes across the 5m..10m band instead
    // of piling them all on the rim when every real signal is weak (-80..-95 dBm).
    val radialFractions = remember(displayDevices) { computeRadialFractions(displayDevices) }

    // Bluetooth first, Wi-Fi on top (as in the mock), capped to strongest 60 devices.
    val drawOrder = remember(displayDevices) {
        displayDevices.sortedByDescending { it.signal }.take(60)
            .sortedBy { if (it.type == DeviceType.WIFI) 1 else 0 }
    }

    Column(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
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
                    .pointerInput(displayDevices, radialFractions, heading, zoom, mapOffset, vm.radarRangeMultiplier, vm.selectedRadarDevice) {
                        detectTapGestures { tap ->
                            val viewport = Size(size.width.toFloat(), size.height.toFloat())
                            val hitRadius = 48f * (minOf(viewport.width, viewport.height) / 1000f)
                            fun distanceTo(device: NetworkDevice): Double {
                                val point = atlasPoint(
                                    device, viewport, heading, zoom * vm.radarRangeMultiplier, mapOffset,
                                    radialFractions[device.id] ?: 0.8f,
                                )
                                return hypot((tap.x - point.x).toDouble(), (tap.y - point.y).toDouble())
                            }
                            val hit = displayDevices.minByOrNull { distanceTo(it) }
                                ?.takeIf { distanceTo(it) <= hitRadius }
                            vm.selectDevice(if (hit != null && hit.id == vm.selectedRadarDevice?.id) null else hit)
                        }
                    },
            ) {
                // All sizes are expressed in "u": 1u = 1/1000 of the canvas' smaller side,
                // so the whole display scales identically on any screen.
                val u = size.minDimension / 1000f
                val displayZoom = zoom * vm.radarRangeMultiplier
                val center = Offset(size.width / 2f + mapOffset.x, size.height / 2f + 8f * u + mapOffset.y)
                val radius = size.minDimension * RING_FRACTION * displayZoom
                val compassRadius = radius * COMPASS_FRACTION
                val midRadius = radius * MID_RING
                val selectedDevice = vm.selectedRadarDevice

                // Panel backdrop + hairline frame.
                val panelCorner = CornerRadius(20.dp.toPx())
                drawRoundRect(color = SpatialPanelBackground, size = size, cornerRadius = panelCorner)
                drawRoundRect(
                    color = CyanMid.copy(alpha = 0.25f),
                    size = size,
                    cornerRadius = panelCorner,
                    style = Stroke(width = 1.2f),
                )

                // Ambient bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CyanMid.copy(alpha = 0.16f), CyanMid.copy(alpha = 0.05f), Color.Transparent),
                        center = center,
                        radius = radius * HALO,
                    ),
                    radius = radius * HALO,
                    center = center,
                )

                // Fine "ripple" rings between the 5m ring and the halo
                for (i in 0..13) {
                    drawCircle(
                        color = CyanMid.copy(alpha = 0.05f),
                        radius = radius * (0.60f + i * 0.045f),
                        center = center,
                        style = Stroke(width = 1f),
                    )
                }

                // Sweep beam (idle pose is NE, like the mock)
                drawSweep(center, radius, if (vm.isScanning) sweepAngle else -42f, vm.isScanning, u)

                // Halo ring (thin) – outside the main ring
                drawCircle(
                    color = CyanMid.copy(alpha = 0.55f),
                    radius = radius * HALO,
                    center = center,
                    style = Stroke(width = 1.8f * u),
                )

                // Main ring with soft glow (3 strokes: wide faint → crisp)
                drawCircle(CyanMid.copy(alpha = 0.10f), radius, center, style = Stroke(width = 16f * u))
                drawCircle(CyanMid.copy(alpha = 0.22f), radius, center, style = Stroke(width = 7f * u))
                drawCircle(CyanBright.copy(alpha = 0.85f), radius, center, style = Stroke(width = 2.4f * u))

                // 5m ring
                drawCircle(CyanMid.copy(alpha = 0.14f), midRadius, center, style = Stroke(width = 6f * u))
                drawCircle(CyanMid.copy(alpha = 0.60f), midRadius, center, style = Stroke(width = 1.8f * u))

                // Range labels
                val ringLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 21f * u
                    color = Color(0xB078DCF5).toArgb()
                }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val pad = 6f * u
                    native.drawText("2m", center.x - compassRadius - ringLabelPaint.measureText("2m") - pad, center.y - pad, ringLabelPaint)
                    native.drawText("5m", center.x + pad, center.y - midRadius - pad, ringLabelPaint)
                    native.drawText("5m", center.x + midRadius + pad, center.y - pad, ringLabelPaint)
                    native.drawText("10m", center.x + radius - ringLabelPaint.measureText("10m") - pad, center.y - pad, ringLabelPaint)
                }

                // Cardinal spokes & tick marks
                for (bearing in 0 until 360 step 90) {
                    val radians = Math.toRadians((bearing + heading).toDouble())
                    drawLine(
                        color = CyanMid.copy(alpha = 0.20f),
                        start = center,
                        end = Offset(center.x + sin(radians).toFloat() * radius, center.y - cos(radians).toFloat() * radius),
                        strokeWidth = 1.2f,
                    )
                }
                for (bearing in 0 until 360 step 30) {
                    val radians = Math.toRadians((bearing + heading).toDouble())
                    val dirX = sin(radians).toFloat()
                    val dirY = -cos(radians).toFloat()
                    drawLine(
                        color = CyanMid.copy(alpha = 0.45f),
                        start = Offset(center.x + dirX * (radius - 12f * u), center.y + dirY * (radius - 12f * u)),
                        end = Offset(center.x + dirX * radius, center.y + dirY * radius),
                        strokeWidth = if (bearing % 90 == 0) 2f else 1.2f,
                    )
                }

                // Outer cardinal letters
                val outerCardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = radius * 0.10f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    color = CyanBright.toArgb()
                }
                val cardinalDistance = radius * 1.14f
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (bearing, letter) ->
                        val radians = Math.toRadians((bearing + heading).toDouble())
                        val x = center.x + sin(radians).toFloat() * cardinalDistance
                        val y = center.y - cos(radians).toFloat() * cardinalDistance
                        native.drawText(letter, x - outerCardinalPaint.measureText(letter) / 2f, y + outerCardinalPaint.textSize / 3f, outerCardinalPaint)
                    }
                }

                // Which devices get a callout pill / emphasis ring
                val calloutDevices = displayDevices
                    .filter { it.type == DeviceType.WIFI }
                    .sortedByDescending { it.signal }
                    .take(3)
                val labelDevices = buildList {
                    selectedDevice?.let { add(it) }
                    calloutDevices.forEach { d -> if (none { it.id == d.id }) add(d) }
                }
                val ringedIds = labelDevices.map { it.id }.toSet()

                // Curved connection lines (node → compass rim)
                val nodeRadius = (if (drawOrder.size > 45) 19f else 24f) * u
                drawOrder.forEach { device ->
                    val position = atlasPoint(device, size, heading, displayZoom, mapOffset, radialFractions[device.id] ?: 0.8f)
                    drawLink(center, compassRadius, position, nodeRadius, device, u)
                }

                // Compass rose (above the lines, below the nodes)
                drawCompass(center, compassRadius, heading, u)

                // Nodes
                drawOrder.forEach { device ->
                    val position = atlasPoint(device, size, heading, displayZoom, mapOffset, radialFractions[device.id] ?: 0.8f)
                    val isSelected = selectedDevice?.id == device.id
                    val color = markerColor(device)
                    if (device.type == DeviceType.WIFI) {
                        val alpha = if (device.signal < -88) 0.7f else 1f
                        val ringed = isSelected || device.id in ringedIds || device.signal >= -60
                        drawWifiNode(position, nodeRadius * 0.96f, color, alpha, ringed, isSelected, u)
                    } else {
                        drawBluetoothNode(position, nodeRadius, color, isSelected, u)
                    }
                }

                // Callout pills (sit right next to their node, no leader line)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    color = android.graphics.Color.WHITE
                }
                val placed = mutableListOf<androidx.compose.ui.geometry.Rect>().apply {
                    add(
                        androidx.compose.ui.geometry.Rect(
                            center.x - compassRadius * 1.15f, center.y - compassRadius * 1.15f,
                            center.x + compassRadius * 1.15f, center.y + compassRadius * 1.15f,
                        )
                    )
                }
                labelDevices.forEach { device ->
                    val position = atlasPoint(device, size, heading, displayZoom, mapOffset, radialFractions[device.id] ?: 0.8f)
                    val isSelected = selectedDevice?.id == device.id
                    textPaint.textSize = (if (isSelected) 33f else 31f) * u
                    val textWidth = textPaint.measureText(device.name)
                    val chipHeight = textPaint.textSize * 1.65f
                    val chipWidth = textWidth + 38f * u
                    val gap = nodeRadius * 1.42f + 10f * u

                    var left = position.x + gap
                    if (left + chipWidth > size.width - 6f * u) left = position.x - gap - chipWidth
                    left = left.coerceAtLeast(6f * u)

                    var top = (position.y - chipHeight / 2f).coerceIn(60f * u, size.height - chipHeight - 70f * u)
                    fun rectAt(y: Float) = androidx.compose.ui.geometry.Rect(left, y, left + chipWidth, y + chipHeight)
                    var tries = 0
                    while (tries < 8 && placed.any { it.overlaps(rectAt(top)) }) {
                        top += chipHeight + 6f * u
                        tries++
                    }
                    top = top.coerceAtMost(size.height - chipHeight - 70f * u)
                    placed.add(rectAt(top))

                    if (abs(top + chipHeight / 2f - position.y) > chipHeight * 0.6f) {
                        drawLine(
                            color = LimeLine.copy(alpha = 0.6f),
                            start = position,
                            end = Offset(left.coerceAtLeast(position.x).coerceAtMost(left + chipWidth), top + chipHeight / 2f),
                            strokeWidth = 1.6f * u,
                        )
                    }

                    val corner = CornerRadius(chipHeight * 0.42f)
                    val accent = if (isSelected) RingGreen else CyanMid
                    // glow, fill, border
                    drawRoundRect(accent.copy(alpha = 0.12f), Offset(left - 3f * u, top - 3f * u), Size(chipWidth + 6f * u, chipHeight + 6f * u), corner, style = Stroke(width = 6f * u))
                    drawRoundRect(Color(0xEA0A1B26), Offset(left, top), Size(chipWidth, chipHeight), corner)
                    drawRoundRect(accent.copy(alpha = 0.75f), Offset(left, top), Size(chipWidth, chipHeight), corner, style = Stroke(width = 1.8f * u))

                    drawIntoCanvas { canvas ->
                        val baseline = top + chipHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                        canvas.nativeCanvas.drawText(device.name, left + 19f * u, baseline, textPaint)
                    }
                }
            }

            // Header Title Bar
            Text(
                text = "SPATIAL NETWORK MAPPER  |  ${if (vm.isScanning) "SCAN ACTIVE" else "SCAN IDLE"}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = CyanBright,
                maxLines = 1,
            )

            // Bottom status bar
            Text(
                text = utcTime,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.2.sp,
                color = LegendText,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Wifi, "Wifi", tint = LegendText, modifier = Modifier.size(20.dp))
                Icon(Icons.Default.SignalCellularAlt, "Cellular", tint = LegendText, modifier = Modifier.size(20.dp))
                Icon(Icons.Default.BatteryFull, "Battery", tint = LegendText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Placement ────────────────────────────────────────────────────────────────

/**
 * Maps each device to a radial fraction (0..1 of the main ring) by signal RANK, not absolute dBm.
 * Strongest devices land just inside the 5m ring, weakest at the rim, with a little hash jitter.
 */
private fun computeRadialFractions(devices: List<NetworkDevice>): Map<String, Float> {
    if (devices.isEmpty()) return emptyMap()
    val sorted = devices.sortedByDescending { it.signal }
    val denominator = (sorted.size - 1).coerceAtLeast(1).toFloat()
    val result = HashMap<String, Float>(sorted.size)
    sorted.forEachIndexed { index, device ->
        val rank = index / denominator
        val hash = abs(device.address.hashCode())
        val jitter = (((hash / 13) % 11) - 5) * 0.012f
        result[device.id] = (0.52f + 0.52f * sqrt(rank) + jitter).coerceIn(0.50f, 1.06f)
    }
    return result
}

/** Real scan results don't set radarAngle (it defaults to 0), so derive a stable spread from the address. */
private fun nodeAngle(device: NetworkDevice): Float {
    if (device.radarAngle != 0f) return device.radarAngle
    val hash = abs(device.address.hashCode()).toLong()
    return ((hash * 2654435761L) % 360000L) / 1000f
}

private fun atlasPoint(
    device: NetworkDevice,
    viewport: Size,
    heading: Float,
    zoom: Float,
    mapOffset: Offset,
    radialFraction: Float,
): Offset {
    val u = minOf(viewport.width, viewport.height) / 1000f
    val center = Offset(viewport.width / 2f + mapOffset.x, viewport.height / 2f + 8f * u + mapOffset.y)
    val radius = minOf(viewport.width, viewport.height) * RING_FRACTION * zoom
    val hash = abs(device.address.hashCode())
    val angleJitter = ((hash % 9) - 4) * 1.6f
    val radians = Math.toRadians((nodeAngle(device) + heading + angleJitter).toDouble())
    return Offset(
        x = center.x + sin(radians).toFloat() * radius * radialFraction,
        y = center.y - cos(radians).toFloat() * radius * radialFraction,
    )
}

private fun markerColor(device: NetworkDevice): Color = when {
    device.favourite -> FavAmber
    device.type == DeviceType.WIFI && device.riskScore >= 60 -> RiskRed
    device.type == DeviceType.WIFI -> WifiGreen
    device.riskScore >= 60 -> RiskOrange
    else -> BluetoothBlue
}

// ── Drawing helpers ──────────────────────────────────────────────────────────

/** Radar sweep: translucent wedge with angular fade + a bright leading edge that reaches the halo. */
private fun DrawScope.drawSweep(center: Offset, radius: Float, beamAngle: Float, scanning: Boolean, u: Float) {
    val trail = if (scanning) 60f else 30f
    val alpha = if (scanning) 0.38f else 0.28f
    val fraction = trail / 360f
    rotate(degrees = beamAngle, pivot = center) {
        val brush = if (scanning) {
            // trailing behind the (clockwise-moving) beam
            Brush.sweepGradient(
                0f to Color.Transparent,
                (1f - fraction) to Color.Transparent,
                1f to CyanBright.copy(alpha = alpha),
                center = center,
            )
        } else {
            // idle pose: glow on the clockwise side of the line, as in the mock
            Brush.sweepGradient(
                0f to CyanBright.copy(alpha = alpha),
                fraction to Color.Transparent,
                1f to Color.Transparent,
                center = center,
            )
        }
        drawArc(
            brush = brush,
            startAngle = if (scanning) -trail else 0f,
            sweepAngle = trail,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
        val tip = Offset(center.x + radius * HALO, center.y)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(CyanBright.copy(alpha = 0.10f), CyanBright.copy(alpha = 0.95f)),
                start = center,
                end = tip,
            ),
            start = center,
            end = tip,
            strokeWidth = 2.4f * u,
            cap = StrokeCap.Round,
        )
    }
}

/** Curved link from the compass rim to a node. Wi-Fi = lime and brighter, Bluetooth = faint teal. */
private fun DrawScope.drawLink(
    center: Offset,
    compassRadius: Float,
    position: Offset,
    nodeRadius: Float,
    device: NetworkDevice,
    u: Float,
) {
    val dx = position.x - center.x
    val dy = position.y - center.y
    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (distance <= compassRadius + nodeRadius) return

    val dirX = dx / distance
    val dirY = dy / distance
    val start = Offset(center.x + dirX * compassRadius, center.y + dirY * compassRadius)
    val end = Offset(position.x - dirX * nodeRadius * 0.9f, position.y - dirY * nodeRadius * 0.9f)

    val hash = abs(device.address.hashCode())
    val sign = if (hash % 2 == 0) 1f else -1f
    val bend = sign * (0.08f + (hash % 5) * 0.018f) * distance
    val control = Offset(
        (start.x + end.x) / 2f - dirY * bend,
        (start.y + end.y) / 2f + dirX * bend,
    )
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }

    if (device.type == DeviceType.WIFI) {
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(LimeLine.copy(alpha = 0.12f), LimeLine.copy(alpha = 0.70f)),
                start = start,
                end = end,
            ),
            style = Stroke(width = 1.8f * u, cap = StrokeCap.Round),
        )
    } else {
        drawPath(
            path = path,
            color = BluetoothBlue.copy(alpha = 0.26f),
            style = Stroke(width = 1.1f * u, cap = StrokeCap.Round),
        )
    }
}

/** Compass disc, glowing rim, 4-point star, notched north arrow and N/E/S/W letters. */
private fun DrawScope.drawCompass(center: Offset, r: Float, heading: Float, u: Float) {
    drawCircle(Color(0xFF05111B).copy(alpha = 0.94f), r, center)
    drawCircle(CyanMid.copy(alpha = 0.12f), r, center, style = Stroke(width = 12f * u))
    drawCircle(CyanBright.copy(alpha = 0.90f), r, center, style = Stroke(width = 2.6f * u))

    // Star glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CyanBright.copy(alpha = 0.55f), Color.Transparent),
            center = center,
            radius = r * 0.35f,
        ),
        radius = r * 0.35f,
        center = center,
    )

    // 4-point star
    val arm = r * 0.41f
    val thick = r * 0.045f
    val horizontal = Path().apply {
        moveTo(center.x - arm, center.y)
        lineTo(center.x, center.y - thick)
        lineTo(center.x + arm, center.y)
        lineTo(center.x, center.y + thick)
        close()
    }
    val vertical = Path().apply {
        moveTo(center.x, center.y - arm)
        lineTo(center.x + thick, center.y)
        lineTo(center.x, center.y + arm)
        lineTo(center.x - thick, center.y)
        close()
    }
    drawPath(horizontal, CyanBright)
    drawPath(vertical, CyanBright)

    // North arrow: notched arrowhead straddling the rim, rotates with the map
    rotate(degrees = heading, pivot = center) {
        val arrow = Path().apply {
            moveTo(center.x, center.y - r * 1.22f)
            lineTo(center.x + r * 0.17f, center.y - r * 0.86f)
            lineTo(center.x, center.y - r * 0.95f)
            lineTo(center.x - r * 0.17f, center.y - r * 0.86f)
            close()
        }
        drawPath(arrow, CyanBright.copy(alpha = 0.25f), style = Stroke(width = 8f * u, join = StrokeJoin.Round))
        drawPath(arrow, CyanBright)
    }

    // Inner letters
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = r * 0.26f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        color = CyanBright.toArgb()
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val letterRing = r * 0.60f
        listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (bearing, letter) ->
            val radians = Math.toRadians((bearing + heading).toDouble())
            val x = center.x + sin(radians).toFloat() * letterRing
            val y = center.y - cos(radians).toFloat() * letterRing
            native.drawText(letter, x - paint.measureText(letter) / 2f, y + paint.textSize / 3f, paint)
        }
    }
}

/** Glossy green circle with white Wi-Fi glyph; optional lime emphasis ring. */
private fun DrawScope.drawWifiNode(
    c: Offset,
    r: Float,
    base: Color,
    alpha: Float,
    ringed: Boolean,
    selected: Boolean,
    u: Float,
) {
    val light = lerp(base, Color.White, 0.30f).copy(alpha = alpha)
    val dark = lerp(base, Color.Black, 0.30f).copy(alpha = alpha)
    drawCircle(Color.Black.copy(alpha = 0.35f * alpha), r * 1.08f, c + Offset(0f, r * 0.12f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(light, dark),
            center = c - Offset(0f, r * 0.30f),
            radius = r * 1.3f,
        ),
        radius = r,
        center = c,
    )
    drawCircle(NodeOutline.copy(alpha = 0.55f * alpha), r, c, style = Stroke(width = 1.5f * u))
    drawWifiGlyph(c, r * 1.15f, Color.White.copy(alpha = alpha))
    if (ringed) {
        drawCircle(
            color = LimeLine.copy(alpha = 0.80f),
            radius = r * 1.45f,
            center = c,
            style = Stroke(width = if (selected) 2.8f * u else 1.8f * u),
        )
    }
}

/** Crisp gradient hexagon with bold white Bluetooth rune. */
private fun DrawScope.drawBluetoothNode(c: Offset, r: Float, base: Color, selected: Boolean, u: Float) {
    val light = lerp(base, Color.White, 0.28f)
    val dark = lerp(base, Color.Black, 0.22f)
    drawPath(hexagonPath(c.x, c.y + r * 0.10f, r * 1.04f), Color.Black.copy(alpha = 0.35f))
    drawPath(
        path = hexagonPath(c.x, c.y, r),
        brush = Brush.linearGradient(
            colors = listOf(light, dark),
            start = Offset(c.x, c.y - r),
            end = Offset(c.x, c.y + r),
        ),
    )
    drawPath(
        path = hexagonPath(c.x, c.y, r),
        color = NodeOutline.copy(alpha = 0.60f),
        style = Stroke(width = 1.6f * u, join = StrokeJoin.Round),
    )
    drawBluetoothGlyph(c, r * 1.35f, Color.White, r * 0.17f)
    if (selected) {
        drawPath(
            path = hexagonPath(c.x, c.y, r * 1.45f),
            color = RingGreen,
            style = Stroke(width = 2.4f * u, join = StrokeJoin.Round),
        )
    }
}

/** Wi-Fi fan: three arcs opening upward above a dot. */
private fun DrawScope.drawWifiGlyph(center: Offset, size: Float, color: Color) {
    val dot = Offset(center.x, center.y + size * 0.30f)
    drawCircle(color = color, radius = size * 0.10f, center = dot)
    for (ring in 0..2) {
        val r = size * (0.30f + 0.26f * ring)
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(dot.x - r, dot.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = size * 0.14f, cap = StrokeCap.Round),
        )
    }
}

/** Bluetooth rune, drawn as two mirrored strokes. */
private fun DrawScope.drawBluetoothGlyph(center: Offset, size: Float, color: Color, strokeWidth: Float) {
    val w = size * 0.30f
    val h = size * 0.46f
    val path = Path().apply {
        moveTo(center.x, center.y - h)
        lineTo(center.x + w, center.y - h * 0.45f)
        lineTo(center.x - w, center.y + h * 0.45f)
        moveTo(center.x, center.y + h)
        lineTo(center.x + w, center.y + h * 0.45f)
        lineTo(center.x - w, center.y - h * 0.45f)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Pointy-top hexagon used for Bluetooth chips. */
private fun hexagonPath(cx: Float, cy: Float, r: Float): Path {
    val path = Path()
    for (i in 0 until 6) {
        val angle = Math.toRadians((-90 + i * 60).toDouble())
        val x = cx + (cos(angle) * r).toFloat()
        val y = cy + (sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun normalizeAngle(value: Float): Float = ((value % 360f) + 360f) % 360f

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
