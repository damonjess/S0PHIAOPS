package com.sophia.ops.ui.radar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val isAutoScanByAi by viewModel.isAutoScanEnabled.collectAsState() // Observe Auto Scan State
    
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "SweepAngle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A09))
    ) {
        // ==========================================
        // 1. FIXED TOP HUD & CONTROL PANEL
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TACTICAL RADAR",
                    style = TextStyle(
                        color = Color(0xFF00FF66),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                )
                
                Surface(
                    color = Color(0xFF00FF66).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = " ${viewModel.status} ",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFF00FF66),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.scan() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.isScanning) Color(0xFFFF2A2A) else Color(0xFF00C853)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        enabled = !isAutoScanByAi // Disable manual button interactions if auto scan owns the loop
                    ) {
                        Text(
                            text = if (viewModel.isScanning) "SCANNING..." else "START SCAN",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // --- AUTO SCAN SETTING TOGGLE SWITCH ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "AUTO",
                            color = if (isAutoScanByAi) Color(0xFF00FF66) else Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = isAutoScanByAi,
                            onCheckedChange = { viewModel.toggleAutoScan(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00FF66),
                                checkedTrackColor = Color(0xFF00FF66).copy(alpha = 0.2f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF1F2421)
                            ),
                            modifier = Modifier.scale(0.75f) // Scale size to fit neatly into layout header bar
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "THREAT LEVEL: ${viewModel.threatLevel}",
                        style = TextStyle(
                            color = if (viewModel.threatLevel == "HIGH") Color(0xFFFF3B30) else Color(0xFF00FF66),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF00FF66).copy(alpha = 0.15f), thickness = 1.dp)
        }

        // ==========================================
        // 2. SCROLLABLE MIDDLE TO LOWER VIEWPORT
        // ==========================================
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            
            // --- ITEM A: ISOMETRIC 3D RADAR CORE ---
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(280.dp)
                            .graphicsLayer {
                                rotationX = 58f
                                cameraDistance = 14f
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                    ) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val maxRadius = size.minDimension / 2

                        // Targets
                        for (i in 1..4) {
                            drawCircle(
                                color = Color(0xFF00FF66).copy(alpha = 0.08f * i),
                                radius = maxRadius * (i.toFloat() / 4f),
                                center = center,
                                style = Stroke(width = 1.5f)
                            )
                        }

                        drawLine(Color(0xFF00FF66).copy(alpha = 0.15f), Offset(center.x - maxRadius, center.y), Offset(center.x + maxRadius, center.y))
                        drawLine(Color(0xFF00FF66).copy(alpha = 0.15f), Offset(center.x, center.y - maxRadius), Offset(center.x, center.y + maxRadius))

                        // Sweeper Cone
                        drawArc(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF00FF66).copy(alpha = 0.22f), Color.Transparent),
                                center = center,
                                radius = maxRadius
                            ),
                            startAngle = sweepAngle,
                            sweepAngle = 50f,
                            useCenter = true,
                            size = Size(maxRadius * 2, maxRadius * 2),
                            topLeft = Offset(center.x - maxRadius, center.y - maxRadius)
                        )

                        // Network Targets
                        devices.forEach { device ->
                            val distancePx = maxRadius * (device.distancePercent / 100f)
                            val angleRad = Math.toRadians(device.angle.toDouble())

                            val groundX = (center.x + distancePx * cos(angleRad)).toFloat()
                            val groundY = (center.y + distancePx * sin(angleRad)).toFloat()

                            // Height mapped to signal strength
                            val elevationPx = ((device.dBm + 100).coerceIn(0, 70) * 2.8f)
                            val blipX = groundX
                            val blipY = groundY - elevationPx

                            // Shadow Ground Anchor
                            drawCircle(color = Color.Black.copy(alpha = 0.8f), radius = 5f, center = Offset(groundX, groundY))

                            // Elevation Pin Stem Line
                            val systemColor = if (device.isBluetooth) Color(0xFF00E5FF) else Color(0xFF00FF66)

                            drawLine(
                                color = systemColor.copy(alpha = 0.5f),
                                start = Offset(groundX, groundY),
                                end = Offset(blipX, blipY),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )

                            // Blip Radiance Nodes
                            drawCircle(color = systemColor.copy(alpha = 0.3f), radius = 11f, center = Offset(blipX, blipY))
                            drawCircle(color = systemColor, radius = 5f, center = Offset(blipX, blipY))
                        }
                    }
                }
            }

            // --- ITEM B: SIGNAL HISTORY SEPARATOR LABEL ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SIGNAL HISTORY",
                        style = TextStyle(
                            color = Color(0xFF00FF66),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF00FF66).copy(alpha = 0.15f), thickness = 1.dp)
                }
            }

            // --- ITEM C: DYNAMIC FEED ITERATOR LIST ---
            items(devices.sortedByDescending { it.dBm }) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = device.name,
                            style = TextStyle(
                                color = if (device.isBluetooth) Color(0xFF00E5FF) else Color(0xFF00FF66),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = device.macAddress.take(17) + "...",
                            style = TextStyle(
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    
                    Text(
                        text = "${device.dBm} dBm",
                        style = TextStyle(
                            color = if (device.dBm > -75) Color(0xFF00FF66) else Color(0xFFFF3B30),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            // --- ITEM D: THE SOPHIA DEEP INTEL & LOCAL AI ENGINE BLOCK ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1C1C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SOPHIA SECURE ACTION AGENT", 
                            color = Color(0xFF00FF66), 
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Engine Online", 
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val displayAdvice = viewModel.chatAnswer ?: viewModel.aiAdviceText
                        Text(
                            text = displayAdvice,
                            color = if (viewModel.chatAnswer != null) Color.White else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        if (viewModel.isAnalyzing || viewModel.isChatLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                color = Color(0xFF00FF66),
                                trackColor = Color(0xFF0F1C1C)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Action Core Analysis Trigger Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.analyzeThreat() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                shape = RoundedCornerShape(6.dp),
                                enabled = viewModel.isAiReady && !viewModel.isAnalyzing
                            ) {
                                Text("LOCAL ADVICE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.performGlobalIntelligenceSearch() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                                shape = RoundedCornerShape(6.dp),
                                enabled = viewModel.isAiReady && !viewModel.isAnalyzing
                            ) {
                                Text("GLOBAL INTEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Ask SOPHIA", 
                            color = Color(0xFF00FF66), 
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        var questionText by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            placeholder = { Text("e.g. Is it safe to connect?", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FF66),
                                unfocusedBorderColor = Color(0xFF00FF66).copy(alpha = 0.3f),
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        viewModel.askSophia(questionText)
                                        questionText = ""
                                    },
                                    enabled = questionText.isNotBlank() && !viewModel.isChatLoading
                                ) {
                                    Icon(
                                        Icons.Default.Send, 
                                        contentDescription = "Send", 
                                        tint = if (questionText.isNotBlank()) Color(0xFF00FF66) else Color.Gray
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
