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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sophia.ops.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onNavigateToRadar: () -> Unit
) {

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

        items(vm.networks) {

            Card {

                Column {

                    Text(it.ssid)

                    Text(
                        "Signal: ${it.signal}"
                    )

                    Text(
                        "Risk: ${it.riskScore}"
                    )
                }
            }
        }
    }
}
