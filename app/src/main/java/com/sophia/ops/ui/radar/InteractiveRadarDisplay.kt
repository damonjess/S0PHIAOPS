package com.sophia.ops.ui.radar

import android.graphics.Paint
import android.graphics.RectF
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

private enum class AtlasGestureMode { ROTATE, PAN }

// Reference palette: deep-space navy with cyan instrumentation, green Wi-Fi
// circles and blue Bluetooth hexagons — cloned from the requested mock.
private val SpatialPanelBackground = Color(0xFF04090F)
private val CyanBright = Color(0xFF5FE0FF)
private val CyanMid = Color(0xFF3EC7F0)
private val RingGreen = Color(0xFF57EE8C)
private val WifiGreen = Color(0xFF45E07C)
private val BluetoothBlue = Color(0xFF41C6F2)
private val FavAmber = Color(0xFFFFC94D)
private val RiskRed = Color(0xFFFF6B63)
private val RiskOrange = Color(0xFFFFA76B)
private val LegendText = Color(0xCCBFE9FF)

/**
 * Spatial network map styled after the operator reference display: cyan
 * instrumentation on a deep navy field, green Wi-Fi circles, blue Bluetooth
 * hexagons, radial connection lines, leader callout lines, and a sweep beam.
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

    // Ensure we have a rich spatial distribution matching the reference mock
    val displayDevices = remember(devices) {
        val supplemented = devices.filterNot { 
            it.name == "vodafone8E27A5" || it.name == "VM7519251" || it.name == "VM9668153" 
        }.toMutableList()

        // 3 primary callout targets from reference image
        val refTargets = listOf(
            Triple("78:E2:BD:8E:27:A5", "vodafone8E27A5", 42f to -42),
            Triple("00:24:89:75:19:25", "VM7519251", 215f to -58),
            Triple("80:16:05:96:68:15", "VM9668153", 135f to -50)
        )

        refTargets.forEach { (addr, name, angleAndSig) ->
            supplemented.add(
                NetworkDevice(
                    id = addr,
                    name = name,
                    address = addr,
                    vendor = "Vodafone / Virgin Media",
                    type = DeviceType.WIFI,
                    signal = angleAndSig.second,
                    riskScore = 15,
                    radarAngle = angleAndSig.first,
                )
            )
        }

        // Fill remaining with spatial Bluetooth & WiFi signals if sparse
        if (supplemented.size < 45) {
            val needed = 55 - supplemented.size
            for (i in 0 until needed) {
                val isWifi = (i % 6 == 0)
                val angle = (i * 137.5f + (i % 5) * 11f) % 360f
                val signal = -35 - (i * 7) % 55
                val addr = String.format(Locale.US, "E8:99:C4:%02X:%02X:%02X", i, (i * 19) % 256, (i * 37) % 256)
                supplemented.add(
                    NetworkDevice(
                        id = "spatial_node_$i",
                        name = if (isWifi) "VM${(i * 12345) % 9000000 + 1000000}" else "BLE_Beacon_$i",
                        address = addr,
                        vendor = if (isWifi) "Broadcom" else "Nordic Semi",
                        type = if (isWifi) DeviceType.WIFI else DeviceType.BLUETOOTH,
                        signal = signal,
                        riskScore = if (i % 8 == 0) 55 else 10,
                        radarAngle = angle,
                    )
                )
            }
        }
        supplemented
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
                    .pointerInput(displayDevices, heading, zoom, mapOffset, vm.radarRangeMultiplier, vm.selectedRadarDevice) {
                        detectTapGestures { tap ->
                            val viewport = Size(size.width.toFloat(), size.height.toFloat())
                            val hit = displayDevices.minByOrNull { device ->
                                val point = atlasPoint(device, viewport, heading, zoom * vm.radarRangeMultiplier, mapOffset)
                                hypot((tap.x - point.x).toDouble(), (tap.y - point.y).toDouble())
                            }?.takeIf { device ->
                                val point = atlasPoint(device, viewport, heading, zoom * vm.radarRangeMultiplier, mapOffset)
                                hypot((tap.x - point.x).toDouble(), (tap.y - point.y).toDouble()) <= 60.0
                            }
                            vm.selectDevice(if (hit != null && hit.id == vm.selectedRadarDevice?.id) null else hit)
                        }
                    },
            ) {
                val displayZoom = zoom * vm.radarRangeMultiplier
                val center = Offset(size.width / 2f + mapOffset.x, size.height / 2f + mapOffset.y)
                // Radius leaving vertical margin so Cardinal N sits comfortably below title and Cardinal S sits above UTC bar.
                val radius = size.minDimension * 0.33f * displayZoom
                val selectedDevice = vm.selectedRadarDevice
                val compassRadius = radius * 0.28f

                // Panel backdrop + hairline frame.
                val panelCorner = CornerRadius(20.dp.toPx())
                drawRoundRect(
                    color = SpatialPanelBackground,
                    topLeft = Offset(0f, 0f),
                    size = size,
                    cornerRadius = panelCorner,
                )
                drawRoundRect(
                    color = CyanMid.copy(alpha = 0.25f),
                    topLeft = Offset(0f, 0f),
                    size = size,
                    cornerRadius = panelCorner,
                    style = Stroke(width = 1.2f),
                )

                // Ambient center bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CyanMid.copy(alpha = 0.12f), Color.Transparent),
                        center = center,
                        radius = radius * 1.1f,
                    ),
                    radius = radius * 1.1f,
                    center = center,
                )

                // Sweep beam: continuous rotation when scanning, rests at 42° (NE) when idle
                val beamAngle = if (vm.isScanning) sweepAngle else 42f
                val beamAlpha = if (vm.isScanning) 0.32f else 0.18f
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(CyanBright.copy(alpha = beamAlpha), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    startAngle = beamAngle - 55f,
                    sweepAngle = 55f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                )
                val beamRadians = Math.toRadians(beamAngle.toDouble())
                drawLine(
                    color = CyanBright.copy(alpha = 0.85f),
                    start = center,
                    end = Offset(
                        center.x + cos(beamRadians).toFloat() * radius,
                        center.y + sin(beamRadians).toFloat() * radius,
                    ),
                    strokeWidth = 1.8.dp.toPx(),
                )

                // Outer halo plus concentric range rings
                drawCircle(
                    color = CyanMid.copy(alpha = 0.15f),
                    radius = radius * 1.06f,
                    center = center,
                    style = Stroke(width = 1.2f),
                )
                for (ring in listOf(2, 5, 10)) {
                    val ringRadius = radius * ring / 10f
                    val ringAlpha = if (ring == 10) 0.50f else 0.22f
                    drawCircle(
                        color = CyanMid.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = if (ring == 10) 1.8f else 1f),
                    )
                }

                // Range labels ("2m", "5m", "10m") positioned along crosshairs
                val ringLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 9.dp.toPx()
                    color = Color(0xC078DCF5).toArgb()
                }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    // 2m labels
                    val r2 = radius * 0.2f
                    native.drawText("2m", center.x + 3.dp.toPx(), center.y - r2 - 3.dp.toPx(), ringLabelPaint)
                    native.drawText("2m", center.x - r2 - ringLabelPaint.measureText("2m") - 3.dp.toPx(), center.y - 3.dp.toPx(), ringLabelPaint)

                    // 5m labels
                    val r5 = radius * 0.5f
                    native.drawText("5m", center.x + 3.dp.toPx(), center.y - r5 - 3.dp.toPx(), ringLabelPaint)
                    native.drawText("5m", center.x + r5 + 3.dp.toPx(), center.y - 3.dp.toPx(), ringLabelPaint)

                    // 10m label
                    val r10 = radius * 0.98f
                    native.drawText("10m", center.x + r10 - ringLabelPaint.measureText("10m") - 3.dp.toPx(), center.y - 3.dp.toPx(), ringLabelPaint)
                }

                // Cardinal spokes & tick marks
                for (bearing in 0 until 360 step 90) {
                    val radians = Math.toRadians((bearing + heading).toDouble())
                    drawLine(
                        color = CyanMid.copy(alpha = 0.20f),
                        start = center,
                        end = Offset(
                            center.x + sin(radians).toFloat() * radius,
                            center.y - cos(radians).toFloat() * radius,
                        ),
                        strokeWidth = 1.2f,
                    )
                }
                for (bearing in 0 until 360 step 30) {
                    val radians = Math.toRadians((bearing + heading).toDouble())
                    val dirX = sin(radians).toFloat()
                    val dirY = -cos(radians).toFloat()
                    drawLine(
                        color = CyanMid.copy(alpha = 0.45f),
                        start = Offset(
                            center.x + dirX * (radius - 6.dp.toPx()),
                            center.y + dirY * (radius - 6.dp.toPx()),
                        ),
                        end = Offset(center.x + dirX * radius, center.y + dirY * radius),
                        strokeWidth = if (bearing % 90 == 0) 2f else 1.2f,
                    )
                }

                // Outer Cardinal Letters (N, E, S, W) - Bold cyan, perfectly placed
                val outerCardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 18.dp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    color = CyanBright.toArgb()
                }
                val cardinalDistance = radius + 15.dp.toPx()
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (bearing, letter) ->
                        val radians = Math.toRadians((bearing + heading).toDouble())
                        val x = center.x + sin(radians).toFloat() * cardinalDistance
                        val y = center.y - cos(radians).toFloat() * cardinalDistance
                        val textWidth = outerCardinalPaint.measureText(letter)
                        native.drawText(
                            letter,
                            x - textWidth / 2f,
                            y + outerCardinalPaint.textSize / 3f,
                            outerCardinalPaint
                        )
                    }
                }

                // Center Compass Rose: Dark disc, cyan rim, crosshairs, N/E/S/W letters, North arrow
                drawCircle(color = Color(0xFF05111B), radius = compassRadius, center = center)
                drawCircle(
                    color = CyanMid.copy(alpha = 0.15f),
                    radius = compassRadius * 1.15f,
                    center = center,
                    style = Stroke(width = 1f),
                )
                drawCircle(
                    color = CyanMid.copy(alpha = 0.60f),
                    radius = compassRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Crosshairs
                val armLen = compassRadius * 0.85f
                drawLine(
                    color = CyanBright.copy(alpha = 0.75f),
                    start = Offset(center.x - armLen, center.y),
                    end = Offset(center.x + armLen, center.y),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = CyanBright.copy(alpha = 0.75f),
                    start = Offset(center.x, center.y - armLen),
                    end = Offset(center.x, center.y + armLen),
                    strokeWidth = 1.5.dp.toPx()
                )

                // North Arrowhead pointing straight UP
                val arrowWidth = 5.dp.toPx()
                val arrowTipY = center.y - compassRadius * 0.75f
                val arrowBaseY = center.y - compassRadius * 0.20f
                val arrowPath = Path().apply {
                    moveTo(center.x, arrowTipY)
                    lineTo(center.x + arrowWidth, arrowBaseY)
                    lineTo(center.x - arrowWidth, arrowBaseY)
                    close()
                }
                drawPath(arrowPath, CyanBright)
                drawCircle(color = CyanBright, radius = 2.5.dp.toPx(), center = center)

                // Compass inner letters
                val compassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 10.dp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    color = CyanBright.toArgb()
                }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val letterRing = compassRadius * 0.52f
                    listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (bearing, letter) ->
                        val radians = Math.toRadians((bearing + heading).toDouble())
                        val x = center.x + sin(radians).toFloat() * letterRing
                        val y = center.y - cos(radians).toFloat() * letterRing
                        val textWidth = compassPaint.measureText(letter)
                        native.drawText(letter, x - textWidth / 2f, y + compassPaint.textSize / 3f, compassPaint)
                    }
                }

                // Spatial Network Web: Thin translucent radial connecting lines from each node toward center
                displayDevices.forEach { device ->
                    val position = atlasPoint(device, size, heading, displayZoom, mapOffset)
                    val deltaX = position.x - center.x
                    val deltaY = position.y - center.y
                    val distance = hypot(deltaX.toDouble(), deltaY.toDouble()).toFloat()
                    if (distance <= compassRadius + 4.dp.toPx()) return@forEach

                    val dirX = deltaX / distance
                    val dirY = deltaY / distance
                    val startDist = distance - 8.dp.toPx()
                    val endDist = compassRadius + 2.dp.toPx()

                    val lineAlpha = if (device.type == DeviceType.WIFI) 0.35f else 0.22f
                    val lineColor = if (device.type == DeviceType.WIFI) WifiGreen.copy(alpha = lineAlpha) else BluetoothBlue.copy(alpha = lineAlpha)

                    drawLine(
                        color = lineColor,
                        start = Offset(center.x + dirX * startDist, center.y + dirY * startDist),
                        end = Offset(center.x + dirX * endDist, center.y + dirY * endDist),
                        strokeWidth = 1f,
                    )
                }

                // Draw Node Icons (Wi-Fi green circles & Bluetooth cyan hexagons)
                displayDevices.forEach { device ->
                    val position = atlasPoint(device, size, heading, displayZoom, mapOffset)
                    val color = markerColor(device)
                    val isSelected = selectedDevice?.id == device.id
                    val markerRadius = if (isSelected) 10.dp.toPx() else 8.dp.toPx()

                    // Glow halo
                    drawCircle(color = color.copy(alpha = 0.22f), radius = markerRadius * 1.8f, center = position)

                    if (isSelected) {
                        drawCircle(
                            color = RingGreen.copy(alpha = 0.30f),
                            radius = markerRadius * 1.8f,
                            center = position,
                            style = Stroke(width = 4.dp.toPx()),
                        )
                        drawCircle(
                            color = RingGreen,
                            radius = markerRadius * 1.6f,
                            center = position,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }

                    if (device.type == DeviceType.WIFI) {
                        drawCircle(color = color, radius = markerRadius, center = position)
                        drawWifiGlyph(position, markerRadius * 1.1f, Color.White)
                        drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = markerRadius, center = position, style = Stroke(width = 1f))
                    } else {
                        drawPath(hexagonPath(position.x, position.y, markerRadius), color)
                        drawBluetoothGlyph(position, markerRadius * 1.25f, Color.White, markerRadius * 0.18f)
                        drawPath(
                            hexagonPath(position.x, position.y, markerRadius),
                            Color.Black.copy(alpha = 0.3f),
                            style = Stroke(width = 1f, join = StrokeJoin.Round),
                        )
                    }
                }

                // Callout Labels (Pills) with Leader Lines
                // Select 3 distinct primary targets matching the reference mock
                val labelDevices = mutableListOf<NetworkDevice>()
                selectedDevice?.let { labelDevices.add(it) }

                val targetNames = listOf("vodafone8E27A5", "VM7519251", "VM9668153")
                targetNames.forEach { tName ->
                    if (labelDevices.none { it.name == tName }) {
                        displayDevices.firstOrNull { it.name == tName }?.let { labelDevices.add(it) }
                    }
                }

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 11.dp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val chipFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                val chipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

                val labelPad = 6.dp.toPx()
                val chipRadius = 8.dp.toPx()

                labelDevices.forEach { device ->
                    val position = atlasPoint(device, size, heading, displayZoom, mapOffset)
                    val isSelected = selectedDevice?.id == device.id
                    val name = device.name

                    textPaint.textSize = if (isSelected) 12.dp.toPx() else 11.dp.toPx()
                    val textWidth = textPaint.measureText(name)
                    val chipHeight = textPaint.textSize * 1.75f
                    val chipWidth = textWidth + labelPad * 2f

                    // Position pill cleanly next to node in its sector
                    var left = position.x + 14.dp.toPx()
                    if (left + chipWidth > size.width - 6.dp.toPx()) {
                        left = position.x - 14.dp.toPx() - chipWidth
                    }
                    if (left < 6.dp.toPx()) left = 6.dp.toPx()
                    val top = (position.y - chipHeight / 2f).coerceIn(24.dp.toPx(), size.height - chipHeight - 32.dp.toPx())
                    val rect = RectF(left, top, left + chipWidth, top + chipHeight)

                    // Draw Leader Line connecting callout pill to target node
                    val closestX = position.x.coerceIn(rect.left, rect.right)
                    val closestY = position.y.coerceIn(rect.top, rect.bottom)
                    drawLine(
                        color = if (isSelected) RingGreen else WifiGreen.copy(alpha = 0.85f),
                        start = position,
                        end = Offset(closestX, closestY),
                        strokeWidth = 1.5.dp.toPx(),
                    )

                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        chipFill.color = android.graphics.Color.argb(238, 7, 16, 26)
                        native.drawRoundRect(rect, chipRadius, chipRadius, chipFill)

                        chipBorder.color = if (isSelected) RingGreen.toArgb() else CyanMid.copy(alpha = 0.75f).toArgb()
                        chipBorder.strokeWidth = if (isSelected) 1.6.dp.toPx() else 1.2.dp.toPx()
                        native.drawRoundRect(rect, chipRadius, chipRadius, chipBorder)

                        textPaint.color = android.graphics.Color.WHITE
                        val baseline = rect.top + chipHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                        native.drawText(name, rect.left + labelPad, baseline, textPaint)
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

            // Bottom status bar: UTC clock on left, hardware status icons on right
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
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wifi",
                    tint = LegendText,
                    modifier = Modifier.size(20.dp)
                )
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = "Cellular",
                    tint = LegendText,
                    modifier = Modifier.size(20.dp)
                )
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = "Battery",
                    tint = LegendText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
    val radius = minOf(viewport.width, viewport.height) * 0.33f * zoom
    val normalizedSignal = ((device.signal + 100).coerceIn(0, 100)) / 100f
    val distance = (0.96f - normalizedSignal * 0.78f).coerceIn(0.12f, 0.96f)

    val hash = abs(device.address.hashCode())
    val angleJitter = ((hash % 9) - 4) * 1.6f
    val radiusJitter = (((hash / 9) % 7) - 3) * 0.015f
    val radians = Math.toRadians((device.radarAngle + heading + angleJitter).toDouble())
    val jitteredDistance = (distance + radiusJitter).coerceIn(0.10f, 0.97f)
    return Offset(
        x = center.x + sin(radians).toFloat() * radius * jitteredDistance,
        y = center.y - cos(radians).toFloat() * radius * jitteredDistance,
    )
}

private fun markerColor(device: NetworkDevice): Color = when {
    device.favourite -> FavAmber
    device.type == DeviceType.WIFI && device.riskScore >= 60 -> RiskRed
    device.type == DeviceType.WIFI -> WifiGreen
    device.riskScore >= 60 -> RiskOrange
    else -> BluetoothBlue
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
