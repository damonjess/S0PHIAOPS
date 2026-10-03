package com.sophia.ops.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sophia.ops.ai.AssessmentSeverity
import com.sophia.ops.data.entities.ScanSession
import com.sophia.ops.ui.components.InfoCard
import com.sophia.ops.ui.components.ScreenHeader
import com.sophia.ops.ui.components.SectionLabel
import com.sophia.ops.ui.theme.SophiaThemeColors
import com.sophia.ops.ui.components.SophiaUi
import com.sophia.ops.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val history by vm.sessions.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshIncidents() }
    val incidents = vm.incidents
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "History",
            subtitle = "Incidents and scan sessions",
            actions = {
                IconButton(onClick = { vm.generatePdfReport(context) }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF report")
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SophiaUi.ScreenPadding)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { vm.exportCsv(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
            ) {
                Text("Export CSV")
            }
            OutlinedButton(
                onClick = { showClearHistoryDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear")
            }
        }

        if (history.isEmpty() && incidents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "No history yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Completed scans and local incidents will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = SophiaUi.ScreenPadding,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(SophiaUi.ItemGap),
            ) {
                if (history.isNotEmpty()) {
                    item { ThreatTrendGraph(history.takeLast(10)) }
                }

                if (incidents.isNotEmpty()) {
                    item {
                        SectionLabel(
                            text = "Investigation timeline",
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(incidents) { incident ->
                        IncidentHistoryItem(incident, vm)
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        SectionLabel(
                            text = "Scan sessions",
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(history) { session ->
                        HistoryItem(session)
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear scan history") },
            text = { Text("This will permanently remove all scan history.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun ThreatTrendGraph(sessions: List<ScanSession>) {
    val graphColor = SophiaThemeColors.statusRed
    InfoCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Threat trend · last ${sessions.size} scans",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
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
                        color = graphColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 4f,
                    )
                    drawCircle(color = graphColor, radius = 6f, center = Offset(x1, y1))
                } else {
                    val x1 = index * step
                    val y1 = height - (session.threatScore / maxThreat * height)
                    drawCircle(color = graphColor, radius = 6f, center = Offset(x1, y1))
                }
            }
        }
    }
}

@Composable
fun HistoryItem(session: ScanSession) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = timeFormat.format(Date(session.timestamp)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dateFormat.format(Date(session.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatItem("Wi-Fi", session.wifiCount.toString())
                Spacer(modifier = Modifier.width(16.dp))
                StatItem("BT", session.bluetoothCount.toString())
                Spacer(modifier = Modifier.width(16.dp))
                StatItem(
                    label = "Threat",
                    value = session.threatScore.toString(),
                    color = if (session.threatScore > 50) SophiaThemeColors.statusRed
                    else SophiaThemeColors.statusGreen,
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun IncidentHistoryItem(incident: com.sophia.ops.data.IncidentRecord, vm: HistoryViewModel) {
    val severityColor = when (incident.severity) {
        AssessmentSeverity.CRITICAL -> SophiaThemeColors.statusRed
        AssessmentSeverity.HIGH -> SophiaThemeColors.statusRed
        AssessmentSeverity.MEDIUM -> SophiaThemeColors.statusYellow
        AssessmentSeverity.LOW -> SophiaThemeColors.statusGreen
    }
    val date = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(incident.createdAt))
    val context = LocalContext.current

    InfoCard(
        borderColor = severityColor.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.sophia.ops.ui.components.StatusChip(
                text = incident.severity.name,
                color = severityColor,
            )
            Text(
                text = "$date · ${incident.confidence.lowercase()} confidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = incident.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        if (incident.changeSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Change: ${incident.changeSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = SophiaThemeColors.statusGreen,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Next step: ${incident.recommendedAction}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        when (incident.status) {
            com.sophia.ops.data.IncidentStatus.OPEN -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        vm.updateIncidentStatus(incident.id, com.sophia.ops.data.IncidentStatus.ACKNOWLEDGED)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Acknowledge") }
                Button(
                    onClick = {
                        vm.updateIncidentStatus(incident.id, com.sophia.ops.data.IncidentStatus.RESOLVED)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Resolve") }
            }
            com.sophia.ops.data.IncidentStatus.ACKNOWLEDGED -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.sophia.ops.ui.components.StatusChip(
                    text = "ACKNOWLEDGED",
                    color = SophiaThemeColors.statusYellow,
                )
                Button(
                    onClick = {
                        vm.updateIncidentStatus(incident.id, com.sophia.ops.data.IncidentStatus.RESOLVED)
                    },
                ) { Text("Resolve") }
            }
            com.sophia.ops.data.IncidentStatus.RESOLVED -> Text(
                text = "RESOLVED LOCALLY",
                style = MaterialTheme.typography.labelSmall,
                color = SophiaThemeColors.statusGreen,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(
            onClick = { vm.generateIncidentPdf(context, incident) },
            modifier = Modifier.align(Alignment.End),
        ) { Text("Export local PDF") }
    }
}
