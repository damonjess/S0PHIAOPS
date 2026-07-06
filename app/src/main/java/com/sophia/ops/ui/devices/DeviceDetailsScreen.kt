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
import com.sophia.ops.ui.theme.SophiaOpsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    device: NetworkDevice,
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    SophiaOpsTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Device Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("←")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(device.name, style = MaterialTheme.typography.headlineMedium)
                        Text(device.address, color = Color.Gray)
                        Text("Signal: ${device.signal}dBm | Vendor: ${device.vendor}")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // DEEP SCAN TARGET
                Button(
                    onClick = { /* TODO: Implement full deep scan */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B8D4))
                ) {
                    Text("DEEP SCAN TARGET")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DEEP RECON (PORT SCAN)
                Button(
                    onClick = { 
                        viewModel.performFullRecon(device)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA))
                ) {
                    Text("DEEP RECON (PORT SCAN)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // GATT EXPLORATION
                Button(
                    onClick = { 
                        viewModel.startGattExploration(device.address)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("GATT EXPLORATION")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status areas
                if (viewModel.reconStatus.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f))) {
                        Text(
                            text = viewModel.reconStatus,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (!viewModel.gattReport.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))) {
                        Text(
                            text = viewModel.gattReport!!,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
