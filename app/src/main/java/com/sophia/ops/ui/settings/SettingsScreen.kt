package com.sophia.ops.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sophia.ops.services.ScanForegroundService
import com.sophia.ops.ui.components.InfoCard
import com.sophia.ops.ui.components.ScreenHeader
import com.sophia.ops.ui.components.SectionLabel
import com.sophia.ops.ui.theme.SophiaThemeColors
import com.sophia.ops.ui.components.SophiaUi
import com.sophia.ops.viewmodel.DashboardViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: DashboardViewModel,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var isBackgroundScanEnabled by remember {
        mutableStateOf(isServiceRunning(context, ScanForegroundService::class.java))
    }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Scanning, alerts, risk tuning, and AI",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SophiaUi.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(SophiaUi.SectionGap),
        ) {
            SettingsSection(
                title = "Scanning",
                description = "Control how the scanner collects and retains nearby signal observations.",
            ) {
                SettingToggle(
                    title = "Background scanning",
                    description = "Continue collecting supported Wi-Fi and Bluetooth signals while the app is not open.",
                    checked = isBackgroundScanEnabled,
                    onCheckedChange = { enabled ->
                        isBackgroundScanEnabled = enabled
                        if (enabled) ScanForegroundService.startService(context)
                        else ScanForegroundService.stopService(context)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingToggle(
                    title = "Continuous scanning",
                    description = "Automatically start another foreground scan after the selected interval. Leave this off for manual Start/Stop control.",
                    checked = vm.continuousScanningEnabled,
                    onCheckedChange = vm::updateContinuousScanning,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingSlider(
                    title = "Active scan cadence",
                    description = "Controls the minimum interval between live radar scans.",
                    value = vm.scanIntervalMillis / 1_000f,
                    valueText = "${(vm.scanIntervalMillis / 1_000L)} seconds",
                    valueRange = 15f..60f,
                    steps = 2,
                    onValueChange = { seconds ->
                        val snapped = (seconds / 15f).roundToInt() * 15_000L
                        vm.updateScanIntervalMillis(snapped)
                    },
                )
            }

            SettingsSection(
                title = "Signal Atlas",
                description = "Customize the stable, manually controlled tactical map.",
            ) {
                SettingToggle(
                    title = "Target labels",
                    description = "Show the device name next to each signal marker.",
                    checked = vm.radarLabelsEnabled,
                    onCheckedChange = vm::updateRadarLabelsEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingSlider(
                    title = "Map range",
                    description = "Scales the tactical map without changing the underlying signal values.",
                    value = vm.radarRangeMultiplier,
                    valueText = "${(vm.radarRangeMultiplier * 100).roundToInt()}%",
                    valueRange = 0.75f..1.50f,
                    steps = 2,
                    onValueChange = vm::updateRadarRangeMultiplier,
                )
            }

            SettingsSection(
                title = "Signal history",
                description = "The timeline is stored locally and is restored after you return to the radar screen.",
            ) {
                SettingToggle(
                    title = "Show radar history",
                    description = "Display the saved signal timeline below the radar display.",
                    checked = vm.signalHistoryVisible,
                    onCheckedChange = vm::updateSignalHistoryVisible,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingSlider(
                    title = "Timeline entries",
                    description = "Choose how many recently seen devices appear in the radar history panel.",
                    value = vm.signalHistoryLimit.toFloat(),
                    valueText = "${vm.signalHistoryLimit} devices",
                    valueRange = 3f..12f,
                    steps = 8,
                    onValueChange = { vm.updateSignalHistoryLimit(it.roundToInt()) },
                )
                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear stored history")
                }
            }

            SettingsSection(
                title = "Auto cyber analyst",
                description = "Optionally run the on-device analyst after a new live scan is saved.",
            ) {
                SettingToggle(
                    title = "Analyze after live scans",
                    description = "The analyst only runs when its on-device model is initialized and the environment changes.",
                    checked = vm.autoAnalystEnabled,
                    onCheckedChange = vm::updateAutoAnalystEnabled,
                )
            }

            SettingsSection(
                title = "Local alerts",
                description = "Private notifications are only considered after an active S0PHIA OPS scan produces a high-severity or watchlist finding.",
            ) {
                SettingToggle(
                    title = "In-device alerts",
                    description = "Show focused Android notifications during active scans. Alerts never run when the app is not scanning.",
                    checked = vm.inDeviceAlertsEnabled,
                    onCheckedChange = vm::updateInDeviceAlertsEnabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingSlider(
                    title = "Alert cooldown",
                    description = "Minimum time between qualifying local alerts, preventing repeated notifications during a scan cycle.",
                    value = vm.alertCooldownMillis / 60_000f,
                    valueText = "${vm.alertCooldownMillis / 60_000L} minutes",
                    valueRange = 5f..60f,
                    steps = 10,
                    onValueChange = { minutes -> vm.updateAlertCooldownMillis(minutes.roundToInt() * 60_000L) },
                )
                Text(
                    text = vm.lastAlertStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SettingsSection(
                title = "Risk tuning",
                description = "Tune how local observations are prioritized. These controls do not alter raw scan data or act on any device.",
            ) {
                SettingSlider(
                    title = "Risk sensitivity",
                    description = "Applies a local score adjustment to Wi-Fi and Bluetooth risk estimates.",
                    value = vm.riskTuning.sensitivityAdjustment.toFloat(),
                    valueText = if (vm.riskTuning.sensitivityAdjustment >= 0) "+${vm.riskTuning.sensitivityAdjustment}" else vm.riskTuning.sensitivityAdjustment.toString(),
                    valueRange = -20f..20f,
                    steps = 7,
                    onValueChange = { vm.updateRiskSensitivity(it.roundToInt()) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingSlider(
                    title = "Nearby signal threshold",
                    description = "Signals at or above this level are treated as nearby for scoring and analyst evidence.",
                    value = vm.riskTuning.proximityThreshold.toFloat(),
                    valueText = "${vm.riskTuning.proximityThreshold} dBm",
                    valueRange = -75f..-40f,
                    steps = 6,
                    onValueChange = { vm.updateProximityThreshold(it.roundToInt()) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingToggle(
                    title = "Exclude trusted devices from new-risk findings",
                    description = "Retains observations but prevents locally trusted devices from being treated as newly elevated-risk findings.",
                    checked = vm.riskTuning.excludeTrustedFromAssessment,
                    onCheckedChange = vm::updateExcludeTrustedFromAssessment,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingToggle(
                    title = "Alert for watchlist devices",
                    description = "Allow a watchlist observation to qualify for an in-device alert during active scans.",
                    checked = vm.riskTuning.alertWatchlistDevices,
                    onCheckedChange = vm::updateAlertWatchlistDevices,
                )
            }

            SettingsSection(
                title = "AI subsystem",
                description = "Manage the local cognitive analysis engine.",
            ) {
                if (vm.isAiLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Initializing on-device model…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!vm.isAiReady) {
                    Button(
                        onClick = { vm.activateOnDeviceAI() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("Initialize on-device AI")
                    }
                    if (vm.aiInitializationFailed) {
                        Text(
                            text = "Last initialization failed. Check that the model file is available before trying again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    Text(
                        "On-device AI is active and ready to assess changing signal conditions.",
                        color = SophiaThemeColors.statusGreen,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Status: ${vm.aiAdviceText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(SophiaUi.SectionGap))
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear stored radar history?") },
            text = {
                Text("This permanently removes saved Wi-Fi, Bluetooth, and scan-session observations from this device. Your display preferences are retained.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearStoredHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear history")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column {
        SectionLabel(text = title)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        InfoCard {
            content()
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    description: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Suppress("DEPRECATION")
private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE).any { service ->
        serviceClass.name == service.service.className
    }
}
