package com.sophia.ops.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.model.DeviceType
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
                    onClick = { viewModel.performDeepScan(device) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B8D4)),
                    enabled = !viewModel.isDeepScanning
                ) {
                    Text(if (viewModel.isDeepScanning) "ANALYZING..." else "DEEP SCAN TARGET")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // DEEP RECON (PORT SCAN)
                if (device.type == DeviceType.WIFI) {
                    Button(
                        onClick = {
                            viewModel.performFullRecon(device)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                        enabled = !viewModel.isReconRunning
                    ) {
                        Text(if (viewModel.isReconRunning) "RECON IN PROGRESS..." else "DEEP RECON (PORT SCAN)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // GATT EXPLORATION
                if (device.type == DeviceType.BLUETOOTH) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.startGattExploration(device.address) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        enabled = !viewModel.isGattExploring
                    ) {
                        Text(if (viewModel.isGattExploring) "EXPLORING GATT..." else "GATT EXPLORATION")
                    }
                }

                // Status areas
                if (viewModel.deepScanResult != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF00B8D4).copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = viewModel.deepScanResult!!,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (viewModel.reconStatus.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = viewModel.reconStatus,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (viewModel.gattReport != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF263238),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = viewModel.gattReport!!,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
