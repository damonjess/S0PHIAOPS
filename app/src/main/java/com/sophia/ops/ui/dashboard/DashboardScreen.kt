package com.sophia.ops.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sophia.ops.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    vm: DashboardViewModel
) {
    LaunchedEffect(Unit) {
        vm.startAutoRefresh(10000)
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn {

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineMedium
                )
                Button(onClick = { vm.scan() }) {
                    Text("Scan")
                }
            }
        }

        item {
            StatsSection(vm)
        }

        items(vm.networks) { network ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Signal: ${network.signal} dBm",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Security: ${network.security}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Risk: ${network.riskScore}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (network.riskScore > 50) Color.Red else Color.Unspecified
                    )
                    Text(
                        text = "Updated: ${timeFormatter.format(Date(network.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun StatsSection(vm: DashboardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "WiFi Networks",
                value = vm.networks.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "BT Devices",
                value = vm.bluetoothDevices.size.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        StatCard(
            title = "Combined Threat Score",
            value = vm.totalThreatScore.toString(),
            color = if (vm.totalThreatScore > 50) Color.Red else Color.Green,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
        }
    }
}
