package com.sophia.ops.ui.statistics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sophia.ops.ui.components.DetailRow
import com.sophia.ops.ui.components.InfoCard
import com.sophia.ops.ui.components.ScreenHeader
import com.sophia.ops.ui.components.SectionLabel
import com.sophia.ops.ui.theme.SophiaThemeColors
import com.sophia.ops.ui.components.SophiaUi
import com.sophia.ops.viewmodel.StatisticsViewModel

@Composable
fun StatisticsScreen(
    vm: StatisticsViewModel,
    onBack: (() -> Unit)? = null,
) {
    val totalScans by vm.totalScans.collectAsState()
    val wifiNetworks by vm.wifiNetworks.collectAsState()
    val bluetoothDevices by vm.bluetoothDevices.collectAsState()
    val historyEntries by vm.historyEntries.collectAsState()
    val lastScanTime by vm.lastScanTime.collectAsState()
    val lastScanDate by vm.lastScanDate.collectAsState()
    val databaseSize by vm.databaseSize.collectAsState()

    StatisticsContent(
        totalScans = totalScans,
        wifiNetworks = wifiNetworks,
        bluetoothDevices = bluetoothDevices,
        historyEntries = historyEntries,
        lastScanTime = lastScanTime,
        lastScanDate = lastScanDate,
        databaseSize = databaseSize,
        onRefresh = { vm.refresh() },
        onBack = onBack,
    )
}

@Composable
fun StatisticsContent(
    totalScans: Int = 0,
    wifiNetworks: Int = 0,
    bluetoothDevices: Int = 0,
    historyEntries: Int = 0,
    lastScanTime: String = "Never",
    lastScanDate: String = "",
    databaseSize: String = "0.0 MB",
    onRefresh: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Statistics",
            subtitle = "Local database overview",
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SophiaUi.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SophiaUi.ItemGap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SophiaUi.ItemGap)) {
                StatisticCard(
                    title = "Total scans",
                    value = totalScans,
                    icon = Icons.Default.Radar,
                    modifier = Modifier.weight(1f),
                )
                StatisticCard(
                    title = "Wi-Fi",
                    value = wifiNetworks,
                    icon = Icons.Default.Wifi,
                    modifier = Modifier.weight(1f),
                    tint = SophiaThemeColors.statusBlue,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(SophiaUi.ItemGap)) {
                StatisticCard(
                    title = "Bluetooth",
                    value = bluetoothDevices,
                    icon = Icons.Default.Bluetooth,
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                StatisticCard(
                    title = "History",
                    value = historyEntries,
                    icon = Icons.Default.History,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last scan card
            InfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Last scan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = lastScanTime,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (lastScanDate.isNotEmpty()) {
                            Text(
                                text = lastScanDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SectionLabel(text = "Database")
            InfoCard {
                DetailRow("History entries", historyEntries.toString())
                DetailRow("Bluetooth devices", bluetoothDevices.toString())
                DetailRow("Wi-Fi networks", wifiNetworks.toString())
                DetailRow("Database size", databaseSize)
            }

            Button(
                onClick = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Refresh statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(SophiaUi.SectionGap))
    }
}

@Composable
fun StatisticCard(
    title: String,
    value: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        label = "StatValue",
    )

    InfoCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedContent(
            targetState = animatedValue,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                } else {
                    slideInVertically { -it } togetherWith slideOutVertically { it }
                }
            },
            label = "NumberAnimation",
        ) { targetValue ->
            Text(
                text = targetValue.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatisticsScreenPreview() {
    MaterialTheme {
        StatisticsContent()
    }
}
