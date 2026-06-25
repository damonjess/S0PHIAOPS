package com.sophia.ops.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onNavigateToRadar: () -> Unit
) {
    LaunchedEffect(Unit) {
        while (true) {
            vm.scan()
            delay(5000)
        }
    }

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
                    text = "Networks: ${vm.networks.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall
                )
                Button(onClick = { vm.scan() }) {
                    Text("Refresh")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onNavigateToRadar) {
                    Text("Radar")
                }
            }
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
                        text = "Risk: ${network.riskScore}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (network.riskScore > 50) Color.Red else Color.Unspecified
                    )
                }
            }
        }
    }
}
