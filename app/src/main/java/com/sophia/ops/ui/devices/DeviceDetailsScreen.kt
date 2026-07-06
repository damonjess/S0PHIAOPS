package com.sophia.ops.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.viewmodel.DashboardViewModel

@Composable
fun DeviceDetailsScreen(
    device: NetworkDevice,
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = device.name,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(device.macAddress, color = Color.Gray)
        Text("Signal: ${device.dBm}dBm")

        Spacer(modifier = Modifier.height(24.dp))

        // DEEP SCAN TARGET
        Button(
            onClick = { viewModel.performDeepScan(device) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B8D4))
        ) {
            Text("DEEP SCAN TARGET")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // DEEP RECON (PORT SCAN)
        Button(
            onClick = { viewModel.performFullRecon(device) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA))
        ) {
            Text("DEEP RECON (PORT SCAN)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GATT EXPLORATION
        Button(
            onClick = { viewModel.startGattExploration(device.macAddress) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
        ) {
            Text("GATT EXPLORATION")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show results
        if (viewModel.reconStatus.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = viewModel.reconStatus,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (viewModel.gattReport?.isNotEmpty() == true) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = viewModel.gattReport!!,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
