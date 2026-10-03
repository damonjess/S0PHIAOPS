package com.sophia.ops.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sophia.ops.ui.components.InfoCard
import com.sophia.ops.ui.components.ScreenHeader
import com.sophia.ops.ui.components.SectionLabel
import com.sophia.ops.ui.theme.SophiaThemeColors
import com.sophia.ops.ui.components.SophiaUi
import com.sophia.ops.ui.components.StatusChip
import com.sophia.ops.ui.components.StatTile
import com.sophia.ops.ui.components.threatColorFor
import com.sophia.ops.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onNavigateToAtlas: () -> Unit = {},
) {
    DisposableEffect(vm) {
        vm.startAutoRefresh()
        onDispose {
            vm.stopAutoRefresh()
        }
    }

    val historyCount by vm.historyCount.collectAsState()
    val scansToday by vm.scansToday.collectAsState()
    val wifiFoundToday by vm.wifiFoundToday.collectAsState()
    val bluetoothFoundToday by vm.bluetoothFoundToday.collectAsState()
    val highestThreatToday by vm.highestThreatToday.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "S0PHIA OPS",
            subtitle = "Local network awareness",
            actions = {
                StatusChip(
                    text = if (vm.isScanning) "SCANNING" else "IDLE",
                    color = if (vm.isScanning) SophiaThemeColors.statusGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Scan control
        InfoCard(modifier = Modifier.padding(horizontal = SophiaUi.ScreenPadding)) {
            Text(
                text = if (vm.isScanning) "Scan in progress" else "Ready to scan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = vm.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last scan · ${vm.lastScanTime}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { vm.scan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.isScanning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = if (vm.isScanning) "Scanning…" else "Scan now",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(SophiaUi.SectionGap))

        // Live overview
        SectionLabel(
            text = "Live overview",
            modifier = Modifier
                .padding(start = SophiaUi.ScreenPadding)
                .padding(bottom = 8.dp),
        )
        OverviewRow {
            StatTile(
                label = "Wi-Fi",
                value = vm.networks.size.toString(),
                icon = Icons.Default.Wifi,
                tint = SophiaThemeColors.statusBlue,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(SophiaUi.ItemGap))
            StatTile(
                label = "Bluetooth",
                value = vm.bluetoothDevices.size.toString(),
                icon = Icons.Default.Bluetooth,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(SophiaUi.ItemGap))
        OverviewRow {
            StatTile(
                label = "Threat",
                value = vm.threatLevel,
                icon = Icons.Default.Shield,
                tint = threatColorFor(vm.threatLevel),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(SophiaUi.ItemGap))
            StatTile(
                label = "History",
                value = historyCount.toString(),
                icon = Icons.Default.History,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(SophiaUi.ItemGap))

        OutlinedButton(
            onClick = onNavigateToAtlas,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = SophiaUi.ScreenPadding),
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open Signal Atlas",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(SophiaUi.SectionGap))

        // Today's activity
        SectionLabel(
            text = "Today",
            modifier = Modifier
                .padding(start = SophiaUi.ScreenPadding)
                .padding(bottom = 8.dp),
        )
        OverviewRow {
            StatTile(
                label = "Scans",
                value = scansToday.toString(),
                icon = Icons.Default.Radar,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            Spacer(modifier = Modifier.width(SophiaUi.ItemGap))
            StatTile(
                label = "Wi-Fi seen",
                value = wifiFoundToday.toString(),
                icon = Icons.Default.Wifi,
                tint = SophiaThemeColors.statusBlue,
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
        Spacer(modifier = Modifier.height(SophiaUi.ItemGap))
        OverviewRow {
            StatTile(
                label = "Bluetooth seen",
                value = bluetoothFoundToday.toString(),
                icon = Icons.Default.Bluetooth,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            Spacer(modifier = Modifier.width(SophiaUi.ItemGap))
            StatTile(
                label = "Highest threat",
                value = highestThreatToday,
                icon = Icons.Default.Shield,
                tint = threatColorFor(highestThreatToday),
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }

        Spacer(modifier = Modifier.height(SophiaUi.SectionGap))
    }
}

@Composable
private fun OverviewRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SophiaUi.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(SophiaUi.ItemGap),
        content = content,
    )
}
