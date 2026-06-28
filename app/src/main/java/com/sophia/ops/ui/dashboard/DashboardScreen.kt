package com.sophia.ops.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sophia.ops.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onNavigateToRadar: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    DisposableEffect(vm) {
        vm.startAutoRefresh(10000)
        onDispose {
            vm.stopAutoRefresh()
        }
    }

    val historyCount by vm.historyCount.collectAsState()
    val scansToday by vm.scansToday.collectAsState()
    val wifiFoundToday by vm.wifiFoundToday.collectAsState()
    val bluetoothFoundToday by vm.bluetoothFoundToday.collectAsState()
    val highestThreatToday by vm.highestThreatToday.collectAsState()

    val scanButtonScale by animateFloatAsState(
        targetValue = if (vm.isScanning) 1.05f else 1f,
        label = "ScanButtonScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Section
        Text(
            text = "SOPHIA OPS",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = vm.status,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Last Scan",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
        Text(
            text = vm.lastScanTime,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))

        // Main Stats Grid
        Row(modifier = Modifier.fillMaxWidth()) {
            DashboardStatItem(
                label = "📶 Wi-Fi",
                value = vm.networks.size,
                modifier = Modifier.weight(1f)
            )
            DashboardStatItem(
                label = "🔵 Bluetooth",
                value = vm.bluetoothDevices.size,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DashboardStatItem(
                label = "⚠ Threat",
                valueString = vm.threatLevel,
                valueColor = when (vm.threatLevel) {
                    "HIGH" -> Color.Red
                    "MEDIUM" -> Color.Yellow
                    else -> Color.Green
                },
                modifier = Modifier.weight(1f)
            )
            DashboardStatItem(
                label = "📜 History",
                value = historyCount,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(32.dp))

        // Navigation / Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { vm.scan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(scanButtonScale),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.isScanning) Color.DarkGray else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (vm.isScanning) "SCANNING..." else "SCAN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardNavButton(
                    text = "RADAR",
                    onClick = onNavigateToRadar,
                    modifier = Modifier.weight(1f)
                )
                DashboardNavButton(
                    text = "DEVICES",
                    onClick = onNavigateToDevices,
                    modifier = Modifier.weight(1f)
                )
            }
            
            DashboardNavButton(
                text = "HISTORY",
                onClick = onNavigateToHistory,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Spacer(modifier = Modifier.height(32.dp))

        // Today's Activity Section
        Text(
            text = "Today's Activity",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DashboardStatItem(
                label = "Scans Today",
                value = scansToday,
                modifier = Modifier.weight(1f),
                small = true
            )
            DashboardStatItem(
                label = "Wi-Fi Seen",
                value = wifiFoundToday,
                modifier = Modifier.weight(1f),
                small = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DashboardStatItem(
                label = "Bluetooth Seen",
                value = bluetoothFoundToday,
                modifier = Modifier.weight(1f),
                small = true
            )
            DashboardStatItem(
                label = "Highest Threat",
                valueString = highestThreatToday,
                valueColor = when (highestThreatToday) {
                    "HIGH" -> Color.Red
                    "MEDIUM" -> Color.Yellow
                    else -> Color.Green
                },
                modifier = Modifier.weight(1f),
                small = true
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun DashboardStatItem(
    label: String,
    modifier: Modifier = Modifier,
    value: Int = 0,
    valueString: String? = null,
    valueColor: Color = Color.White,
    small: Boolean = false
) {
    val animatedValue by animateIntAsState(targetValue = value, label = "StatValue")
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = valueString ?: animatedValue.toString(),
            style = if (small) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
fun DashboardNavButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.DarkGray.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
