package com.sophia.ops.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sophia.ops.data.entities.ScanSession
import com.sophia.ops.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val history by vm.sessions.collectAsState()
    val context = LocalContext.current
    var showClearHistoryDialog by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Scan History", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { vm.generatePdfReport(context) }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (history.isNotEmpty()) {
                item {
                    ThreatTrendGraph(history.takeLast(10))
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                vm.exportCsv(context)
                            }
                        ) {
                            Text("Export CSV")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showClearHistoryDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Clear History")
                        }
                    }
                }
            }

            items(history) { session ->
                HistoryItem(session)
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showClearHistoryDialog = false
            },
            title = {
                Text("Clear Scan History")
            },
            text = {
                Text(
                    "This will permanently remove all scan history."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ThreatTrendGraph(sessions: List<ScanSession>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(200.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Threat Trend (Last 10)", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxThreat = (sessions.maxOfOrNull { it.threatScore } ?: 100).coerceAtLeast(10).toFloat()
                val width = size.width
                val height = size.height
                val step = width / (sessions.size.coerceAtLeast(2) - 1).toFloat()

                sessions.asReversed().forEachIndexed { index, session ->
                    if (index < sessions.size - 1) {
                        val nextSession = sessions.asReversed()[index + 1]
                        val x1 = index * step
                        val y1 = height - (session.threatScore / maxThreat * height)
                        val x2 = (index + 1) * step
                        val y2 = height - (nextSession.threatScore / maxThreat * height)

                        drawLine(
                            color = Color.Red,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 4f
                        )
                        drawCircle(
                            color = Color.Red,
                            radius = 6f,
                            center = Offset(x1, y1)
                        )
                    } else {
                        val x1 = index * step
                        val y1 = height - (session.threatScore / maxThreat * height)
                        drawCircle(
                            color = Color.Red,
                            radius = 6f,
                            center = Offset(x1, y1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(session: ScanSession) {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateSdf = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = sdf.format(Date(session.timestamp)),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dateSdf.format(Date(session.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatItem("WiFi", session.wifiCount.toString())
                Spacer(modifier = Modifier.width(12.dp))
                StatItem("BT", session.bluetoothCount.toString())
                Spacer(modifier = Modifier.width(12.dp))
                StatItem("Threat", session.threatScore.toString(), color = if (session.threatScore > 50) Color.Red else Color.Green)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
