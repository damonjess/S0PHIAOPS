package com.sophia.ops.viewmodel

import android.app.Application
import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.ScanSession
import com.sophia.ops.data.entities.SignalPoint
import com.sophia.ops.data.OuiLookup
import com.sophia.ops.data.DeviceDisposition
import com.sophia.ops.data.InvestigationStore
import com.sophia.ops.data.DailyBrief
import com.sophia.ops.data.IncidentRecord
import com.sophia.ops.data.RiskTuning
import com.sophia.ops.data.RiskTuningStore
import com.sophia.ops.alerts.LocalAlertNotifier
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.bluetooth.BluetoothScanner
import com.sophia.ops.bluetooth.BluetoothRiskEngine
import com.sophia.ops.wifi.RiskEngine
import com.sophia.ops.wifi.WifiScanner
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.model.DeviceType
import com.sophia.ops.ai.SecureActionAgent
import com.sophia.ops.ai.DeviceSummary
import com.sophia.ops.ai.AiAssessment
import com.sophia.ops.ai.AssessmentEvidence
import com.sophia.ops.ai.ScanChange
import android.util.Log
import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.abs
import kotlin.random.Random
import kotlin.jvm.Volatile

class DashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val tag = "DashboardViewModel"

    // Dedicated single thread and scope for all on-device AI work — the engine 
    // must always be created and invoked from exactly the same thread.
    private val aiDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SOPHIA-AI-Thread")
    }.asCoroutineDispatcher()
    
    private val aiScope = CoroutineScope(aiDispatcher + SupervisorJob())
    
    // Channel for debouncing AI requests. CONFLATED ensures we only process the LATEST
    // request if multiple scans finish while the AI is busy.
    private val aiRequestChannel = Channel<Boolean>(Channel.CONFLATED)

    private data class ScanDataSnapshot(
        val networks: List<WifiNetwork>,
        val bluetoothDevices: List<BluetoothDeviceEntity>,
        val threatScore: Int
    )
    
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("CRASH_DEBUG", "Uncaught Exception in ViewModel scope", throwable)
    }

    private val db = SophiaDatabase.getInstance(application)
    
    private val bluetoothDao = db.bluetoothDao()
    private val scanDao = db.scanSessionDao()
    private val wifiDao = db.wifiDao()
    
    private val scanner = WifiScanner(application)
    private val bluetoothScanner = BluetoothScanner(application)
    private val radarPreferences = application.getSharedPreferences("radar_preferences", Context.MODE_PRIVATE)
    private val investigationStore = InvestigationStore(application)
    private val localAlertNotifier = LocalAlertNotifier(application)
    private val riskTuningStore = RiskTuningStore(application)

    var radarAutoRotate by mutableStateOf(radarPreferences.getBoolean("radar_auto_rotate", false))
        private set
    var radarLabelsEnabled by mutableStateOf(radarPreferences.getBoolean("radar_labels_enabled", true))
        private set
    var signalHistoryVisible by mutableStateOf(radarPreferences.getBoolean("signal_history_visible", true))
        private set
    var signalHistoryLimit by mutableStateOf(radarPreferences.getInt("signal_history_limit", 6).coerceIn(3, 12))
        private set
    var radarRangeMultiplier by mutableStateOf(radarPreferences.getFloat("radar_range_multiplier", 1f).coerceIn(0.75f, 1.50f))
        private set
    var autoAnalystEnabled by mutableStateOf(radarPreferences.getBoolean("auto_analyst_enabled", false))
        private set
    var scanIntervalMillis by mutableStateOf(radarPreferences.getLong("scan_interval_millis", 15_000L).coerceIn(15_000L, 60_000L))
        private set
    var continuousScanningEnabled by mutableStateOf(radarPreferences.getBoolean("continuous_scanning_enabled", false))
        private set
    var deviceDispositions by mutableStateOf(investigationStore.loadDispositions())
        private set
    var riskTuning by mutableStateOf(riskTuningStore.load())
        private set
    var inDeviceAlertsEnabled by mutableStateOf(radarPreferences.getBoolean("in_device_alerts_enabled", true))
        private set
    var alertCooldownMillis by mutableStateOf(radarPreferences.getLong("alert_cooldown_millis", 15 * 60_000L).coerceIn(5 * 60_000L, 60 * 60_000L))
        private set
    var dailyBrief by mutableStateOf(investigationStore.buildDailyBrief())
        private set
    var lastAlertStatus by mutableStateOf("No local alert has been sent in this session.")
        private set
    private var lastAlertTimestamp = radarPreferences.getLong("last_local_alert_timestamp", 0L)
    
    init {
        pruneData()
        observePersistedBluetoothSignals()
        preloadOuiDatabase()
        startAiRequestObserver()
        checkModelExists()
    }

    private fun checkModelExists() {
        viewModelScope.launch(Dispatchers.IO) {
            val exists = File("${getApplication<Application>().filesDir}/model.task").exists() || 
                         File("/data/local/tmp/llm/model.task").exists()
            withContext(Dispatchers.Main) {
                isModelPresent = exists
            }
        }
    }

    private fun startAiRequestObserver() {
        aiScope.launch(exceptionHandler) {
            while (isActive) {
                try {
                    val fromLiveScan = aiRequestChannel.receive()
                    processAiAnalysis(fromLiveScan)
                } catch (e: Exception) {
                    if (isActive) Log.e(tag, "AI observer error", e)
                }
            }
        }
    }

    private fun preloadOuiDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            OuiLookup.getVendor(getApplication(), "00:00:00:00:00:00")
        }
    }

    private fun pruneData() {
        viewModelScope.launch(Dispatchers.IO) {
            val twentyFourHoursAgo = System.currentTimeMillis() - 86400000L
            bluetoothDao.pruneTransientOldSignals(twentyFourHoursAgo)
            Log.i(tag, "Automated Cleanup: Dropped unverified/low-risk signals older than 24 hours.")
        }
    }

    /**
     * Hydrates the live radar from Room as well as from new discovery callbacks.
     * This keeps the radar history panel populated after an app restart or a
     * background scan completed while the radar screen was not open.
     */
    private fun observePersistedBluetoothSignals() {
        viewModelScope.launch(Dispatchers.IO) {
            bluetoothDao.getAll().collectLatest { storedDevices ->
                val visibleDevices = storedDevices.filterNot { it.ignored }
                withContext(Dispatchers.Main) {
                    bluetoothDevices.clear()
                    bluetoothDevices.addAll(visibleDevices)
                    selectedDevice?.address?.let { selectedAddress ->
                        selectedDevice = visibleDevices.firstOrNull { it.address == selectedAddress }
                        selectedRadarDevice = selectedDevice?.toNetworkDevice(getApplication())
                    }
                }
            }
        }
    }
    
    var isScanning by mutableStateOf(value = false)
        private set

    private var isWifiScanning = false
    private var isBluetoothScanning = false

    private var autoRefreshJob: Job? = null
    private var lastAutoRefreshInterval: Long = 5000L
    
    private var lastScanRequestTime = 0L
    private var scanCanceledByUser = false

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    var selectedRadarDevice by mutableStateOf<NetworkDevice?>(null)
        private set

    var selectedDevice by mutableStateOf<BluetoothDeviceEntity?>(null)
        private set

    var aiResponse by mutableStateOf<String?>(null)
        private set

    var aiAssessment by mutableStateOf<AiAssessment?>(null)
        private set

    @Volatile
    private var tacticalAgent: SecureActionAgent? = null 

    val isAiReady: Boolean
        get() = tacticalAgent != null

    var aiAdviceText by mutableStateOf("AI Engine Standby. Click to initialize.")
        private set

    var aiInitializationFailed by mutableStateOf(value = false)
        private set

    var isAiLoading by mutableStateOf(value = false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var downloadProgress by mutableStateOf(0f)
        private set

    var isAnalyzing by mutableStateOf(value = false)
        private set

    var isDeepScanning by mutableStateOf(false)
        private set

    var deepScanResult by mutableStateOf<String?>(null)
        private set

    var isModelPresent by mutableStateOf(false)
        private set

    private val analysisInProgress = AtomicBoolean(false)

    private var lastAnalyzedThreatScore: Int? = null
    private var lastAnalysisTimestamp = 0L
    private val minAnalysisInterval = 120_000L // 2 min cooldown unless something actually changed
    private val knownDeviceAddresses = mutableSetOf<String>()
    private val previousSignalByAddress = mutableMapOf<String, Int>()

    var strategicBrief by mutableStateOf<String?>(null)
        private set

    var chatAnswer by mutableStateOf<String?>(null)
        private set

    var isChatLoading by mutableStateOf(false)
        private set

    fun activateOnDeviceAI() {
        if (tacticalAgent != null || isAiLoading) return
        
        aiScope.launch(exceptionHandler) {
            withContext(Dispatchers.Main) {
                isAiLoading = true
                aiInitializationFailed = false
            }

            val candidateModelPaths = listOf(
                "${getApplication<Application>().filesDir}/model.task",
                "/data/local/tmp/llm/model.task",
                "/sdcard/Download/model.task",
                "/sdcard/Downloads/model.task",
                "/storage/emulated/0/Download/model.task"
            )

            val targetPath = candidateModelPaths.find { File(it).exists() }

            if (targetPath == null) {
                withContext(Dispatchers.Main) {
                    aiInitializationFailed = true
                    aiAdviceText = "AI weights file missing. Click 'Download Model' in settings or use ADB."
                    isAiLoading = false
                }
                return@launch
            }

            try {
                if (tacticalAgent != null) {
                    withContext(Dispatchers.Main) { isAiLoading = false }
                    return@launch
                }
                
                val agent = SecureActionAgent(getApplication(), targetPath)
                val result = agent.initializeEngine()

                withContext(Dispatchers.Main) {
                    if (result) {
                        Log.i(tag, "SecureActionAgent initialized successfully.")
                        tacticalAgent = agent
                        aiInitializationFailed = false
                        aiAdviceText = "SOPHIA AI Engine Online. Awaiting threat metrics..."
                    } else {
                        Log.e(tag, "SecureActionAgent reports failure during initialization.")
                        aiInitializationFailed = true
                        aiAdviceText = "AI Failed to initialize (check weights or logcat)"
                    }
                    isAiLoading = false
                }
            } catch (t: Throwable) {
                Log.e(tag, "AI initialization failed", t)
                withContext(Dispatchers.Main) {
                    aiInitializationFailed = true
                    aiAdviceText = "AI Subsystem Error: ${t.localizedMessage}"
                    isAiLoading = false
                }
            }
        }
    }

    fun downloadModel(modelUrl: String = "https://storage.googleapis.com/mediapipe-models/llm/gemma-2b-it-cpu-int4.task") {
        if (isDownloading) return
        
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            withContext(Dispatchers.Main) {
                isDownloading = true
                aiAdviceText = "Downloading local AI model securely…"
                downloadProgress = 0f
            }

            val targetFile = File(getApplication<Application>().filesDir, "model.task")
            val temporaryFile = File(getApplication<Application>().filesDir, "model.task.download")
            var connection: HttpURLConnection? = null
            try {
                val url = URL(modelUrl)
                require(url.protocol.equals("https", ignoreCase = true)) { "Model download must use HTTPS." }
                if (temporaryFile.exists()) temporaryFile.delete()

                val httpConnection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    connect()
                }
                connection = httpConnection
                require(httpConnection.responseCode in 200..299) { "Model server returned HTTP ${httpConnection.responseCode}." }
                val fileLength = httpConnection.contentLengthLong
                require(fileLength > 0L) { "Model server did not provide a valid file length." }

                httpConnection.inputStream.use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        var read: Int
                        var lastReportedPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            total += read
                            val percent = ((total * 100L) / fileLength).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                withContext(Dispatchers.Main) { downloadProgress = percent / 100f }
                            }
                        }
                        output.flush()
                    }
                }

                require(temporaryFile.length() >= 50L * 1024L * 1024L) { "Downloaded model is unexpectedly small and was rejected." }
                if (targetFile.exists() && !targetFile.delete()) {
                    throw IllegalStateException("Existing model could not be replaced.")
                }
                if (!temporaryFile.renameTo(targetFile)) {
                    temporaryFile.copyTo(targetFile, overwrite = true)
                    temporaryFile.delete()
                }

                withContext(Dispatchers.Main) {
                    isDownloading = false
                    isModelPresent = true
                    aiAdviceText = "Local model validated. Initializing analyst…"
                    activateOnDeviceAI()
                }
            } catch (e: Exception) {
                Log.e(tag, "Model download failed", e)
                if (temporaryFile.exists()) temporaryFile.delete()
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    aiAdviceText = "Model download failed safely: ${e.localizedMessage ?: "unknown error"}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun analyzeThreat(fromLiveScan: Boolean = false) {
        Log.i(tag, "analyzeThreat() requested. AI Ready: $isAiReady")

        if (!isAiReady) {
            aiAdviceText = "AI Engine Standby. Click to initialize."
            aiResponse = null
            strategicBrief = null
            return
        }

        if (!hasEnoughMemoryForAi()) {
            aiAdviceText = "AI unavailable: not enough free memory for on-device analysis."
            aiResponse = aiAdviceText
            strategicBrief = null
            return
        }

        aiRequestChannel.trySend(fromLiveScan)
    }

    fun askSophia(question: String) {
        if (question.isBlank() || isChatLoading) return

        val agent = tacticalAgent
        if (agent == null) {
            chatAnswer = "AI engine not active. Tap 'Activate AI' first."
            return
        }

        isChatLoading = true
        aiScope.launch(exceptionHandler) {
            val environmentContext = withContext(Dispatchers.Main) { buildBoundedChatContext() }

            chatAnswer = try {
                agent.askQuestion(question.take(500), environmentContext)
            } catch (t: Throwable) {
                "Error: ${t.localizedMessage ?: t.javaClass.simpleName}"
            }
            isChatLoading = false
        }
    }

    private fun buildBoundedChatContext(): String {
        val selected = selectedRadarDevice
        val scanLines = buildList {
            add("THREAT_SCORE=${threatScore}/100")
            add("COUNTS wifi=${networks.size}; bluetooth=${bluetoothDevices.size}")
            selected?.let { device ->
                add("SELECTED name=${sanitizePromptValue(device.name)}; type=${device.type}; signal=${device.signal}; risk=${device.riskScore}")
            }
            allRadarDevices
                .sortedByDescending { it.riskScore }
                .take(5)
                .forEach { device ->
                    add("DEVICE name=${sanitizePromptValue(device.name)}; type=${device.type}; signal=${device.signal}; risk=${device.riskScore}")
                }
        }
        return scanLines.joinToString("\n")
    }

    private fun sanitizePromptValue(value: String): String = value
        .replace(Regex("[\\r\\n<>]"), " ")
        .replace(Regex("\\s+"), " ")
        .take(80)

    private suspend fun processAiAnalysis(fromLiveScan: Boolean) {
        if (!analysisInProgress.compareAndSet(false, true)) {
            Log.i(tag, "processAiAnalysis() skipped - already in progress.")
            return
        }

        withContext(Dispatchers.Main) { isAnalyzing = true }

        try {
            if (!hasEnoughMemoryForAi()) {
                withContext(Dispatchers.Main) {
                    aiAdviceText = "AI unavailable: not enough free memory for on-device analysis."
                    aiResponse = aiAdviceText
                    strategicBrief = null
                }
                return
            }

            // Snapshot MUST be taken on Main thread for SnapshotStateList consistency
            val snapshot = withContext(Dispatchers.Main) {
                ScanDataSnapshot(
                    networks = networks.toList(),
                    bluetoothDevices = bluetoothDevices.toList(),
                    threatScore = threatScore
                )
            }

            val app = getApplication<Application>()
            val newAddressesSnapshot = mutableSetOf<String>()

            val wifiSummaries = snapshot.networks.map { net ->
                newAddressesSnapshot.add(net.bssid)
                DeviceSummary(
                    address = net.bssid,
                    name = net.ssid.ifBlank { "Hidden Network" },
                    vendor = OuiLookup.getVendor(app, net.bssid),
                    type = "WIFI",
                    riskScore = net.riskScore,
                    isNew = net.bssid !in knownDeviceAddresses,
                    signal = net.signal,
                    security = net.security,
                )
            }

            val bleSummaries = snapshot.bluetoothDevices.map { dev ->
                newAddressesSnapshot.add(dev.address)
                DeviceSummary(
                    address = dev.address,
                    name = dev.nickname ?: dev.name ?: "Unknown Bluetooth Device",
                    vendor = OuiLookup.getVendor(app, dev.address),
                    type = "BLUETOOTH",
                    riskScore = dev.riskScore,
                    isNew = dev.address !in knownDeviceAddresses,
                    signal = dev.rssi,
                    timesSeen = dev.timesSeen,
                )
            }

            val allSummaries = (wifiSummaries + bleSummaries).sortedByDescending { it.riskScore }
            val topThreat = allSummaries.firstOrNull()
            val newThreats = allSummaries.filter {
                it.isNew && it.riskScore > 20 &&
                    (!riskTuning.excludeTrustedFromAssessment || deviceDisposition(it.address) != DeviceDisposition.TRUSTED)
            }
            val totalCount = allSummaries.size
            val currentThreatScore = snapshot.threatScore
            val newDeviceCount = allSummaries.count { it.isNew }
            val lostDeviceCount = (knownDeviceAddresses - newAddressesSnapshot).size

            val environmentType = when {
                totalCount > 1500 -> "Ultra-Dense Urban / Electronic Saturation Zone"
                totalCount > 500  -> "Standard Congestion Zone"
                else              -> "Low-Noise / Isolated Perimeter"
            }

            val topDeviceNames = allSummaries.take(3).joinToString(", ") { d ->
                if (d.vendor.isNotBlank() && d.vendor != "Unknown Vendor" && !d.vendor.startsWith("Private")) {
                    "${d.name} (${d.vendor})"
                } else {
                    d.name
                }
            }.ifBlank { "no notable devices" }

            val evidence = buildList {
                topThreat?.takeIf { it.riskScore > 0 }?.let { device ->
                    add(AssessmentEvidence("Highest observed risk", "${device.name} is scored ${device.riskScore}/100 at ${device.signal} dBm."))
                }
                allSummaries.filter { it.signal >= riskTuning.proximityThreshold }.take(2).forEach { device ->
                    add(AssessmentEvidence("Close signal", "${device.name} is currently nearby at ${device.signal} dBm."))
                }
                allSummaries.filter { it.type == "WIFI" && it.security?.contains("WPA", ignoreCase = true) == false && it.security?.contains("WEP", ignoreCase = true) == false }
                    .take(1)
                    .forEach { device -> add(AssessmentEvidence("Open Wi-Fi", "${device.name} reports no WPA or WEP capability marker.")) }
                allSummaries.filter { it.type == "BLUETOOTH" && it.timesSeen >= 5 }.take(1).forEach { device ->
                    add(AssessmentEvidence("Repeated observation", "${device.name} has been observed ${device.timesSeen} times."))
                }
                allSummaries.filter { deviceDisposition(it.address) == DeviceDisposition.WATCHLIST }.take(2).forEach { device ->
                    add(AssessmentEvidence("Watchlist device", "${device.name} is on your local watchlist."))
                }
                if (isEmpty()) add(AssessmentEvidence("Scan coverage", "$totalCount device(s) were available for local analysis."))
            }

            val changes = buildList {
                if (knownDeviceAddresses.isEmpty()) {
                    add(ScanChange("Baseline created", "This is the first analyst comparison for the current session."))
                } else {
                    if (newDeviceCount > 0) add(ScanChange("New devices", "$newDeviceCount device(s) were not present in the previous analyst snapshot."))
                    if (lostDeviceCount > 0) add(ScanChange("No longer visible", "$lostDeviceCount previously tracked device(s) are not visible in this scan."))
                }
                allSummaries.mapNotNull { device ->
                    val previous = previousSignalByAddress[device.address] ?: return@mapNotNull null
                    val delta = device.signal - previous
                    if (abs(delta) >= 10) ScanChange("Signal movement", "${device.name} changed by ${if (delta > 0) "+" else ""}$delta dBm since the previous analyst snapshot.") else null
                }.take(3).forEach(::add)
                lastAnalyzedThreatScore?.let { previousScore ->
                    val delta = currentThreatScore - previousScore
                    if (abs(delta) >= 10) add(ScanChange("Threat score movement", "Overall score changed by ${if (delta > 0) "+" else ""}$delta points."))
                }
                if (isEmpty()) add(ScanChange("No material change", "No large device-count, signal, or threat-score movement was detected."))
            }

            val primaryConcern = when {
                topThreat != null && topThreat.riskScore > 70 ->
                    "High-risk observation: ${topThreat.name} has a local score of ${topThreat.riskScore}/100."
                newThreats.isNotEmpty() ->
                    "${newThreats.size} newly observed device(s) have elevated local risk factors."
                currentThreatScore > 50 ->
                    "Environmental score is elevated at $currentThreatScore/100. Notable nearby signals: $topDeviceNames."
                else ->
                    "Scan completed with $totalCount device(s); $newDeviceCount are new relative to the analyst baseline."
            }

            Log.d(tag, "Executing AI analysis on thread: ${Thread.currentThread().name}")

            val agentInstance = tacticalAgent
            if (agentInstance != null) {
                val result = agentInstance.assessTacticalConcern(
                    primaryConcern = primaryConcern,
                    environment = environmentType,
                    threatLevel = currentThreatScore,
                    evidence = evidence,
                    changes = changes,
                )

                Log.i(tag, "Structured AI assessment complete: ${result.recommendedAction.take(40)}...")
                
                withContext(Dispatchers.Main) {
                    aiAssessment = result.assessment
                    aiAdviceText = result.recommendedAction
                    aiResponse = "${result.riskSummary} ${result.recommendedAction}"
                    strategicBrief = if (result.assessment.severity.name == "CRITICAL") result.recommendedAction else null
                }

                val incident = investigationStore.addIncident(result.assessment)
                val alertStatus = dispatchInDeviceAlert(incident, fromLiveScan)
                val refreshedBrief = investigationStore.buildDailyBrief()
                withContext(Dispatchers.Main) {
                    dailyBrief = refreshedBrief
                    lastAlertStatus = alertStatus
                }
                lastAnalyzedThreatScore = currentThreatScore
                lastAnalysisTimestamp = System.currentTimeMillis()
                knownDeviceAddresses.clear()
                knownDeviceAddresses.addAll(newAddressesSnapshot)
                previousSignalByAddress.clear()
                allSummaries.forEach { previousSignalByAddress[it.address] = it.signal }
            } else {
                Log.w(tag, "AI agent not initialized, skipping analysis.")
                withContext(Dispatchers.Main) {
                    aiAssessment = null
                    aiAdviceText = "AI Engine Standby. Click to initialize."
                    strategicBrief = null
                }
            }
        } catch (t: Throwable) {
            Log.e("CRASH_DEBUG", "AI analysis failed internally", t)
            withContext(Dispatchers.Main) {
                aiAdviceText = "Tactical generation suspended: Subsystem error."
                strategicBrief = "Strategic analysis failed."
            }
        } finally {
            withContext(Dispatchers.Main) { isAnalyzing = false }
            analysisInProgress.set(false)
        }
    }

    private fun dispatchInDeviceAlert(incident: IncidentRecord, fromLiveScan: Boolean): String {
        if (!fromLiveScan) return "No alert sent: this was a manual assessment."
        if (!inDeviceAlertsEnabled) return "No alert sent: in-device alerts are disabled."
        val watchlistSignal = riskTuning.alertWatchlistDevices &&
            incident.evidenceSummary.contains("Watchlist device", ignoreCase = true)
        val alertEligible = incident.severity.name == "HIGH" ||
            incident.severity.name == "CRITICAL" || watchlistSignal
        if (!alertEligible) return "No alert sent: assessment did not meet the focused alert rule."

        val now = System.currentTimeMillis()
        val remaining = alertCooldownMillis - (now - lastAlertTimestamp)
        if (remaining > 0L) {
            return "No alert sent: cooldown active for ${(remaining / 60_000L).coerceAtLeast(1)} more minute(s)."
        }
        val sent = localAlertNotifier.notifyIncident(incident)
        if (!sent) return "Alert ready, but Android notification permission is not granted."

        lastAlertTimestamp = now
        radarPreferences.edit().putLong("last_local_alert_timestamp", now).apply()
        return "Local alert sent for this active scan."
    }

    fun refreshDailyBrief() {
        dailyBrief = investigationStore.buildDailyBrief()
    }

    fun updateInDeviceAlertsEnabled(enabled: Boolean) {
        inDeviceAlertsEnabled = enabled
        radarPreferences.edit().putBoolean("in_device_alerts_enabled", enabled).apply()
    }

    fun updateAlertCooldownMillis(value: Long) {
        val normalized = value.coerceIn(5 * 60_000L, 60 * 60_000L)
        alertCooldownMillis = normalized
        radarPreferences.edit().putLong("alert_cooldown_millis", normalized).apply()
    }

    fun updateRiskSensitivity(value: Int) {
        saveRiskTuning(riskTuning.copy(sensitivityAdjustment = value.coerceIn(-20, 20)))
    }

    fun updateProximityThreshold(value: Int) {
        saveRiskTuning(riskTuning.copy(proximityThreshold = value.coerceIn(-75, -40)))
    }

    fun updateExcludeTrustedFromAssessment(enabled: Boolean) {
        saveRiskTuning(riskTuning.copy(excludeTrustedFromAssessment = enabled))
    }

    fun updateAlertWatchlistDevices(enabled: Boolean) {
        saveRiskTuning(riskTuning.copy(alertWatchlistDevices = enabled))
    }

    private fun saveRiskTuning(value: RiskTuning) {
        riskTuning = value
        riskTuningStore.save(value)
    }

    fun performGlobalIntelligenceSearch() {
        // This build deliberately does not imply a live threat-intelligence feed.
        // External enrichment belongs behind an explicit, cited connector in a later release.
        aiAdviceText = "Offline guidance only: no live intelligence source is connected."
        aiResponse = "This analyst is using local scan evidence only. A future verified intelligence connector will show its source and retrieval time before it can enrich an assessment."
        isAnalyzing = false
    }

    private fun hasEnoughMemoryForAi(minAvailableMb: Long = 1024): Boolean {
        val am = getApplication<Application>()
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val availableMb = info.availMem / (1024 * 1024)
        Log.i(tag, "AI memory check: ${availableMb}MB available, lowMemory=${info.lowMemory}")
        return !info.lowMemory && availableMb >= minAvailableMb
    }

    fun selectDevice(device: NetworkDevice?) {
        selectedRadarDevice = device
        deepScanResult = null
    }

    fun selectBluetoothDevice(entity: BluetoothDeviceEntity?) {
        selectedDevice = entity
        selectedRadarDevice = entity?.toNetworkDevice(getApplication())
        deepScanResult = null
    }

    @Suppress("unused")
    fun selectWifiNetwork(network: WifiNetwork?) {
        selectedRadarDevice = network?.toNetworkDevice(getApplication())
    }

    fun performDeepScan(device: NetworkDevice) {
        if (!isAiReady) return

        if (!hasEnoughMemoryForAi()) {
            deepScanResult = "Deep Scan aborted: Insufficient device memory."
            return
        }

        aiScope.launch(exceptionHandler) {
            withContext(Dispatchers.Main) {
                isDeepScanning = true
                deepScanResult = "SOPHIA analyzing target signature..."
            }

            val agent = tacticalAgent
            if (agent != null) {
                try {
                    val result = agent.analyzeDevice(
                        name = device.name,
                        address = device.address,
                        vendor = device.vendor,
                        type = device.type.name,
                        signal = device.signal,
                        timesSeen = device.timesSeen,
                        riskScore = device.riskScore.toInt()
                    )
                    withContext(Dispatchers.Main) {
                        deepScanResult = result
                        isDeepScanning = false
                    }
                } catch (e: Throwable) {
                    Log.e(tag, "Deep scan failed", e)
                    withContext(Dispatchers.Main) {
                        deepScanResult = "Deep scan failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                        isDeepScanning = false
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    isDeepScanning = false
                    deepScanResult = "AI Engine unavailable for Deep Scan."
                }
            }
        }
    }

    private fun BluetoothDeviceEntity.toNetworkDevice(app: Application): NetworkDevice {
        val baseAngle = (this.address.hashCode().toFloat() % 360f)
        val vendor = OuiLookup.getVendor(app, this.address)
        
        val rawName = this.nickname ?: this.name
        val displayName = when {
            !rawName.isNullOrBlank() && !rawName.startsWith("Discovered Device") && !rawName.contains("Unknown", true) -> rawName
            vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)" -> vendor
            else -> "Unknown Bluetooth Device"
        }

        return NetworkDevice(
            id = this.address,
            name = displayName,
            address = this.address,
            vendor = vendor,
            type = DeviceType.BLUETOOTH,
            signal = this.rssi,
            favourite = this.favourite,
            lastSeen = this.lastSeen,
            firstSeen = this.firstSeen,
            riskScore = this.riskScore,
            timesSeen = this.timesSeen,
            threatScore = this.riskScore.toFloat(),
            radarAngle = baseAngle,
        )
    }

    private fun WifiNetwork.toNetworkDevice(app: Application): NetworkDevice {
        val baseAngle = (this.bssid.hashCode().toFloat() % 360f)
        val vendor = OuiLookup.getVendor(app, this.bssid)
        
        val displayName = if (this.ssid.isBlank() || this.ssid == "<unknown ssid>") {
            if (vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)") {
                vendor
            } else {
                "Hidden Network"
            }
        } else {
            this.ssid
        }

        return NetworkDevice(
            id = this.bssid,
            name = displayName,
            address = this.bssid,
            vendor = vendor,
            type = DeviceType.WIFI,
            signal = this.signal,
            favourite = false,
            lastSeen = this.timestamp,
            firstSeen = this.timestamp,
            riskScore = this.riskScore,
            timesSeen = 1,
            threatScore = this.riskScore.toFloat(),
            radarAngle = baseAngle + this.angularOffset
        )
    }

    val allRadarDevices: List<NetworkDevice>
        get() {
            val app = getApplication<Application>()
            val wifiSnapshot = networks.toList()
            val bleSnapshot = bluetoothDevices.toList()
            
            return wifiSnapshot.map { it.toNetworkDevice(app) } + 
                   bleSnapshot.map { it.toNetworkDevice(app) }
        }
    
    val historyCount: StateFlow<Int> = scanDao.getCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val startOfDay: Long
        get() = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    val todaySessions: StateFlow<List<ScanSession>> = scanDao.getSessionsSince(startOfDay)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val scansToday: StateFlow<Int> = todaySessions
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val wifiFoundToday: StateFlow<Int> = todaySessions
        .map { it.sumOf { s -> s.wifiCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val bluetoothFoundToday: StateFlow<Int> = todaySessions
        .map { it.sumOf { s -> s.bluetoothCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val highestThreatToday: StateFlow<String> = todaySessions
        .map { sessions ->
            val maxScore = sessions.maxOfOrNull { it.threatScore } ?: 0
            when {
                maxScore > 50 -> "HIGH"
                maxScore > 20 -> "MEDIUM"
                maxScore > 0 -> "LOW"
                else -> "NONE"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NONE")
    
    fun calculateAdaptiveThreatScore(wifiCount: Int, bleCount: Int, highRiskDevices: Int): Int {
        val totalSignalDensity = wifiCount + bleCount
        
        val densityAttenuationMultiplier = when {
            totalSignalDensity > 1500 -> 0.15f  
            totalSignalDensity > 500  -> 0.40f  
            else                      -> 1.00f  
        }

        val adjustedWifiScore = (wifiCount * 0.2f) * densityAttenuationMultiplier
        val adjustedBleScore = (bleCount * 0.1f) * densityAttenuationMultiplier
        
        val criticalVectorScore = highRiskDevices * 25 

        val finalCalculatedScore = (adjustedWifiScore + adjustedBleScore + criticalVectorScore).toInt()
        return finalCalculatedScore.coerceIn(0, 100)
    }

    val threatScore: Int
        get() {
            val (wifiSnapshot, bleSnapshot) = androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                networks.toList() to bluetoothDevices.toList()
            }
            val highRiskWifi = wifiSnapshot.count { it.riskScore > 60 }
            val highRiskBle = bleSnapshot.count { it.riskScore > 60 }
            
            return calculateAdaptiveThreatScore(
                wifiCount = wifiSnapshot.size,
                bleCount = bleSnapshot.size,
                highRiskDevices = highRiskWifi + highRiskBle
            )
        }

    val threatLevel: String
        get() = when (threatScore) {
            in 0..20 -> "LOW"
            in 21..50 -> "MEDIUM"
            else -> "HIGH"
        }

    var isThrottled by mutableStateOf(false)
        private set

    var lastScanWasLive by mutableStateOf(false)
        private set

    var lastScanTime by mutableStateOf("Never")
        private set

    var scanStatusMessage by mutableStateOf("Ready to scan nearby Wi-Fi and Bluetooth devices.")
        private set

    val status: String
        get() = when {
            isScanning -> "SCANNING"
            lastScanWasLive -> "LIVE"
            else -> "READY"
        }

    fun updateRadarAutoRotate(enabled: Boolean) {
        radarAutoRotate = enabled
        radarPreferences.edit().putBoolean("radar_auto_rotate", enabled).apply()
    }

    fun updateRadarLabelsEnabled(enabled: Boolean) {
        radarLabelsEnabled = enabled
        radarPreferences.edit().putBoolean("radar_labels_enabled", enabled).apply()
    }

    fun updateSignalHistoryVisible(enabled: Boolean) {
        signalHistoryVisible = enabled
        radarPreferences.edit().putBoolean("signal_history_visible", enabled).apply()
    }

    fun updateSignalHistoryLimit(limit: Int) {
        signalHistoryLimit = limit.coerceIn(3, 12)
        radarPreferences.edit().putInt("signal_history_limit", signalHistoryLimit).apply()
    }

    fun updateRadarRangeMultiplier(multiplier: Float) {
        radarRangeMultiplier = multiplier.coerceIn(0.75f, 1.50f)
        radarPreferences.edit().putFloat("radar_range_multiplier", radarRangeMultiplier).apply()
    }

    fun updateAutoAnalystEnabled(enabled: Boolean) {
        autoAnalystEnabled = enabled
        radarPreferences.edit().putBoolean("auto_analyst_enabled", enabled).apply()
    }

    fun updateScanIntervalMillis(interval: Long) {
        scanIntervalMillis = interval.coerceIn(15_000L, 60_000L)
        radarPreferences.edit().putLong("scan_interval_millis", scanIntervalMillis).apply()
        if (continuousScanningEnabled && autoRefreshJob != null) {
            stopAutoRefresh()
            startAutoRefresh()
        }
    }

    fun updateContinuousScanning(enabled: Boolean) {
        continuousScanningEnabled = enabled
        radarPreferences.edit().putBoolean("continuous_scanning_enabled", enabled).apply()
        if (enabled) startAutoRefresh() else stopAutoRefresh()
    }

    fun stopCurrentScan() {
        if (!isScanning) return
        scanCanceledByUser = true
        scanner.cancelScan()
        bluetoothScanner.cancelDiscovery()
        isWifiScanning = false
        isBluetoothScanning = false
        isScanning = false
        isThrottled = false
        lastScanWasLive = false
        scanStatusMessage = "Scan stopped by operator."
        Log.i(tag, "Scan canceled by operator.")
    }

    fun deviceDisposition(address: String): DeviceDisposition =
        deviceDispositions[address] ?: DeviceDisposition.UNREVIEWED

    fun setDeviceDisposition(address: String, disposition: DeviceDisposition) {
        investigationStore.saveDisposition(address, disposition)
        deviceDispositions = investigationStore.loadDispositions()
        scanStatusMessage = when (disposition) {
            DeviceDisposition.TRUSTED -> "Device marked trusted."
            DeviceDisposition.WATCHLIST -> "Device added to the watchlist."
            DeviceDisposition.UNREVIEWED -> "Device review state cleared."
        }
    }

    fun clearStoredHistory() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            bluetoothDao.deleteAllDevices()
            wifiDao.deleteAllNetworks()
            scanDao.deleteAllSessions()
            withContext(Dispatchers.Main) {
                networks.clear()
                bluetoothDevices.clear()
                selectedDevice = null
                selectedRadarDevice = null
                aiResponse = null
                aiAssessment = null
                strategicBrief = null
                knownDeviceAddresses.clear()
                previousSignalByAddress.clear()
                lastAnalyzedThreatScore = null
                lastAnalysisTimestamp = 0L
                investigationStore.clearAll()
                deviceDispositions = emptyMap()
                aiAdviceText = "History cleared. Ready for a fresh scan."
            }
        }
    }

    fun startAutoRefresh(intervalMs: Long = scanIntervalMillis) {
        if (!continuousScanningEnabled) return
        lastAutoRefreshInterval = intervalMs
        if (autoRefreshJob != null) return
        
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                scan()
                delay(intervalMs.milliseconds)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun shouldTriggerAiAnalysis(): Boolean {
        val now = System.currentTimeMillis()
        val currentScore = threatScore
        val scoreDelta = lastAnalyzedThreatScore?.let { kotlin.math.abs(currentScore - it) } ?: Int.MAX_VALUE
        val hasNewDevices = allRadarDevices.any { it.address !in knownDeviceAddresses }
        val cooldownElapsed = (now - lastAnalysisTimestamp) >= minAnalysisInterval

        return hasNewDevices || scoreDelta >= 5 || cooldownElapsed
    }

    fun scan(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (isScanning) {
            scanStatusMessage = "A scan is already in progress."
            return
        }
        if (!force && ((now - lastScanRequestTime) < scanIntervalMillis)) {
            val secondsRemaining = ((scanIntervalMillis - (now - lastScanRequestTime)) / 1_000L).coerceAtLeast(1L)
            isThrottled = true
            scanStatusMessage = "Scan cooldown: try again in about $secondsRemaining seconds."
            return
        }

        scanCanceledByUser = false
        lastScanRequestTime = now
        isScanning = true
        isWifiScanning = true
        isBluetoothScanning = true

        Log.i(tag, "Initiating scan at $now...")
        scanStatusMessage = "Scanning Wi-Fi and Bluetooth…"
        
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        lastScanTime = sdf.format(Date(now))
        
        bluetoothScanner.startDiscovery(
            onDeviceFound = { device, rssi ->
                val name = getDisplayName(device)

                // FIX: Run background operations entirely on Dispatchers.IO
                viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                    try {
                        val existing = bluetoothDao.getDeviceByAddress(device.address)
                        val timestampNow = System.currentTimeMillis()

                        if (existing?.ignored == true) {
                            withContext(Dispatchers.Main) {
                                val uiIndex = bluetoothDevices.indexOfFirst { it.address == device.address }
                                if (uiIndex != -1) {
                                    bluetoothDevices.removeAt(uiIndex)
                                }
                            }
                            return@launch
                        }

                        val timesSeen = (existing?.timesSeen ?: 0) + 1
                        val risk = BluetoothRiskEngine.calculate(
                            name = name,
                            rssi = rssi,
                            timesSeen = timesSeen,
                            sensitivityAdjustment = riskTuning.sensitivityAdjustment,
                            proximityThreshold = riskTuning.proximityThreshold,
                        )

                        val newHistory = (existing?.signalHistory ?: emptyList()) + SignalPoint(rssi, timestampNow)
                        val trimmedHistory = newHistory.takeLast(10)

                        @SuppressLint("MissingPermission")
                        val entity = if (existing == null) {
                            val sanitizedName = if (name?.startsWith("Discovered Device") == true) null else name
                            val newEntity = BluetoothDeviceEntity(
                                name = sanitizedName,
                                address = device.address,
                                deviceType = device.type,
                                firstSeen = timestampNow,
                                lastSeen = timestampNow,
                                riskScore = risk,
                                rssi = rssi,
                                timesSeen = 1,
                                signalHistory = trimmedHistory
                            )
                            bluetoothDao.insert(newEntity)
                            newEntity
                        } else {
                            val isNewNameAvailable = !name.isNullOrBlank() && !name.startsWith("Discovered Device")
                            val finalName = if (isNewNameAvailable) {
                                name
                            } else {
                                if (existing.name?.startsWith("Discovered Device") == true) null else existing.name
                            }

                            val updatedEntity = existing.copy(
                                name = finalName,
                                lastSeen = timestampNow,
                                riskScore = risk,
                                rssi = rssi,
                                timesSeen = timesSeen,
                                signalHistory = trimmedHistory
                            )

                            bluetoothDao.updateDevice(updatedEntity)
                            updatedEntity
                        }

                        // FIX: Ensure all Compose state mutations happen on the Main thread
                        withContext(Dispatchers.Main) {
                            lastScanWasLive = true
                            scanStatusMessage = "Bluetooth signal detected. Continuing scan…"
                            val currentList = bluetoothDevices
                            val uiIndex = currentList.indexOfFirst { it.address == device.address }
                            if (uiIndex != -1) {
                                if (currentList[uiIndex].rssi != entity.rssi || currentList[uiIndex].name != entity.name) {
                                    currentList[uiIndex] = entity
                                }
                            } else {
                                currentList.add(entity)
                            }

                            if (selectedDevice?.address == entity.address) {
                                selectedDevice = entity
                                selectedRadarDevice = entity.toNetworkDevice(getApplication())
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BT", "Database sync failed", e)
                    }
                }
            },
            onDiscoveryFinished = {
                viewModelScope.launch {
                    isBluetoothScanning = false
                    isScanning = isWifiScanning || isBluetoothScanning
                    if (!isScanning && lastScanWasLive && !scanCanceledByUser) {
                        scanStatusMessage = "Scan complete. ${networks.size} Wi-Fi and ${bluetoothDevices.size} Bluetooth device(s) available."
                    }
                    Log.i(tag, "Bluetooth discovery finished.")
                }
            },
            onFailure = { message ->
                viewModelScope.launch {
                    scanStatusMessage = message
                    Log.w(tag, "Bluetooth scan issue: $message")
                }
            },
        )
        
        scanner.startScan(
            onResults = { results ->
            Log.i(tag, "Callback: Received ${results.size} results.")
            
            val updatedList = results.map {
                val risk = RiskEngine.calculate(
                    security = it.capabilities,
                    signal = it.level,
                    sensitivityAdjustment = riskTuning.sensitivityAdjustment,
                    proximityThreshold = riskTuning.proximityThreshold,
                )

                WifiNetwork(
                    ssid = @Suppress("DEPRECATION") it.SSID,
                    bssid = it.BSSID,
                    signal = it.level + Random.nextInt(-1, 2),
                    security = it.capabilities,
                    riskScore = risk,
                    timestamp = System.currentTimeMillis(),
                    angularOffset = (Random.nextFloat() * 10f) - 5f
                )
            }

            viewModelScope.launch {
                isThrottled = false 
                lastScanWasLive = true
                scanStatusMessage = "Wi-Fi scan found ${updatedList.size} network(s)."
                networks.clear()
                networks.addAll(updatedList)
                
                // Explicitly run database updates on Dispatchers.IO
                withContext(Dispatchers.IO) {
                    try {
                        wifiDao.insertAll(updatedList)
                        saveScanSession()
                        if (autoAnalystEnabled && shouldTriggerAiAnalysis()) {
                            analyzeThreat(fromLiveScan = true)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to persist WiFi networks", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            isWifiScanning = false
                            isScanning = isWifiScanning || isBluetoothScanning
                        }
                    }
                }
            }
            },
            onFailure = { message ->
                viewModelScope.launch {
                isThrottled = true
                lastScanWasLive = false
                scanStatusMessage = if (scanCanceledByUser) "Scan stopped by operator." else message
                isWifiScanning = false
                isScanning = isWifiScanning || isBluetoothScanning
                    Log.w(tag, "Wi-Fi scan issue: $message")
                }
            },
        )
    }

    private suspend fun saveScanSession() {
        try {
            val (wifiCount, bleCount, currentThreat) = withContext(Dispatchers.Main) {
                Triple(networks.size, bluetoothDevices.size, threatScore)
            }
            
            val session = ScanSession(
                timestamp = System.currentTimeMillis(),
                wifiCount = wifiCount,
                bluetoothCount = bleCount,
                threatScore = currentThreat
            )
            withContext(Dispatchers.IO) {
                scanDao.insert(session)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to save scan session", e)
        }
    }

    private fun fuzzExistingSignals() {
        if (networks.isEmpty()) return
        
        viewModelScope.launch {
            isThrottled = true 
            lastScanWasLive = false
            
            Log.d(tag, "Applying aggressive signal fuzz to keep radar alive.")
            val currentList = networks
            for (i in currentList.indices) {
                val net = currentList[i]
                val newSignal = net.signal + Random.nextInt(-2, 3) 
                val newAngle = net.angularOffset + (Random.nextFloat() * 4f - 2f)
                
                currentList[i] = net.copy(
                    signal = newSignal.coerceIn(-100, -20),
                    angularOffset = newAngle.coerceIn(-15f, 15f),
                    timestamp = System.currentTimeMillis() 
                )
            }

            delay(1000.milliseconds)
            isThrottled = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDisplayName(device: BluetoothDevice): String? {
        val manager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        
        val hasConnectPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getApplication<Application>().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasConnectPermission) {
            val bondedMatch = adapter?.bondedDevices?.firstOrNull { it.address == device.address }
            val bondedName = bondedMatch?.name
            if (!bondedName.isNullOrBlank()) return bondedName

            val name = device.name
            if (!name.isNullOrBlank()) return name

            val alias = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) device.alias else null
            if (!alias.isNullOrBlank()) return alias
        }

        return null
    }

    @Suppress("unused")
    fun toggleFavourite(device: BluetoothDeviceEntity) {
        viewModelScope.launch(exceptionHandler) {
            val newState = !device.favourite
            withContext(Dispatchers.IO) {
                bluetoothDao.updateFavourite(device.address, newState)
            }
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                val index = bluetoothDevices.indexOfFirst { it.address == device.address }
                if (index != -1) {
                    bluetoothDevices[index] = bluetoothDevices[index].copy(favourite = newState)
                }
            }
        }
    }

    @Suppress("unused")
    fun updateNickname(device: BluetoothDeviceEntity, nickname: String?) {
        viewModelScope.launch(exceptionHandler) {
            withContext(Dispatchers.IO) {
                bluetoothDao.updateNickname(device.address, nickname)
            }
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                val index = bluetoothDevices.indexOfFirst { it.address == device.address }
                if (index != -1) {
                    bluetoothDevices[index] = bluetoothDevices[index].copy(nickname = nickname)
                }
            }
        }
    }

    @Suppress("unused")
    fun updateNotes(device: BluetoothDeviceEntity, notes: String?) {
        viewModelScope.launch(exceptionHandler) {
            withContext(Dispatchers.IO) {
                bluetoothDao.updateNotes(device.address, notes)
            }
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                val index = bluetoothDevices.indexOfFirst { it.address == device.address }
                if (index != -1) {
                    bluetoothDevices[index] = bluetoothDevices[index].copy(notes = notes)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
        
        // Execute teardown on the dedicated AI thread to maintain thread affinity
        aiScope.launch {
            try {
                tacticalAgent?.let {
                    it.close()
                    tacticalAgent = null
                }
            } catch (e: Exception) {
                Log.e(tag, "Error closing AI agent on cleared", e)
            } finally {
                // Shut down the dispatcher's underlying executor
                aiDispatcher.close()
            }
        }
    }
}
