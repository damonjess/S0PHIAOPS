package com.sophia.ops.viewmodel

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.runtime.MutableState
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
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.bluetooth.BluetoothScanner
import com.sophia.ops.bluetooth.BluetoothRiskEngine
import com.sophia.ops.wifi.RiskEngine
import com.sophia.ops.wifi.WifiScanner
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.ai.SecureActionAgent
import com.sophia.ops.ai.DeviceSummary
import com.sophia.ops.recon.PentestRecon
import com.sophia.ops.bluetooth.BluetoothGattExplorer
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.random.Random
import kotlin.jvm.Volatile

class DashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val tag = "DashboardViewModel"

    private data class ScanDataSnapshot(
        val networks: List<WifiNetwork>,
        val bluetoothDevices: List<BluetoothDeviceEntity>,
        val threatScore: Int
    )

    // --- State Properties (Explicit MutableState to avoid delegation issues) ---
    private val _autoAiAnalysisEnabled = mutableStateOf(true)
    var autoAiAnalysisEnabled: Boolean
        get() = _autoAiAnalysisEnabled.value
        private set(value) { _autoAiAnalysisEnabled.value = value }

    private val _isRadarAutoRotating = mutableStateOf(true)
    var isRadarAutoRotating: Boolean
        get() = _isRadarAutoRotating.value
        private set(value) { _isRadarAutoRotating.value = value }

    private val _isRadarLabelsVisible = mutableStateOf(true)
    var isRadarLabelsVisible: Boolean
        get() = _isRadarLabelsVisible.value
        private set(value) { _isRadarLabelsVisible.value = value }

    private val _isWifiScanning = mutableStateOf(false)
    private var isWifiScanning: Boolean
        get() = _isWifiScanning.value
        set(value) { _isWifiScanning.value = value }

    private val _isBluetoothScanning = mutableStateOf(false)
    private var isBluetoothScanning: Boolean
        get() = _isBluetoothScanning.value
        set(value) { _isBluetoothScanning.value = value }

    val isScanning: Boolean
        get() = isWifiScanning || isBluetoothScanning

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    private val _selectedRadarDevice = mutableStateOf<NetworkDevice?>(null)
    var selectedRadarDevice: NetworkDevice?
        get() = _selectedRadarDevice.value
        private set(value) { _selectedRadarDevice.value = value }

    private val _selectedDevice = mutableStateOf<BluetoothDeviceEntity?>(null)
    var selectedDevice: BluetoothDeviceEntity?
        get() = _selectedDevice.value
        private set(value) { _selectedDevice.value = value }

    private val _aiResponse = mutableStateOf<String?>(null)
    var aiResponse: String?
        get() = _aiResponse.value
        private set(value) { _aiResponse.value = value }

    private val _cyberAnalystContext = mutableStateOf("Fresh environment scan in progress.")
    var cyberAnalystContext: String
        get() = _cyberAnalystContext.value
        private set(value) { _cyberAnalystContext.value = value }

    private val _lastGlobalAnalysis = mutableStateOf("")
    var lastGlobalAnalysis: String
        get() = _lastGlobalAnalysis.value
        private set(value) { _lastGlobalAnalysis.value = value }

    private val _aiAdviceText = mutableStateOf("AI Engine Standby. Click to initialize.")
    var aiAdviceText: String
        get() = _aiAdviceText.value
        private set(value) { _aiAdviceText.value = value }

    private val _aiInitializationFailed = mutableStateOf(false)
    var aiInitializationFailed: Boolean
        get() = _aiInitializationFailed.value
        private set(value) { _aiInitializationFailed.value = value }

    private val _isAiLoading = mutableStateOf(false)
    var isAiLoading: Boolean
        get() = _isAiLoading.value
        private set(value) { _isAiLoading.value = value }

    private val _isDownloading = mutableStateOf(false)
    var isDownloading: Boolean
        get() = _isDownloading.value
        private set(value) { _isDownloading.value = value }

    private val _downloadProgress = mutableStateOf(0f)
    var downloadProgress: Float
        get() = _downloadProgress.value
        private set(value) { _downloadProgress.value = value }

    private val _isAnalyzing = mutableStateOf(false)
    var isAnalyzing: Boolean
        get() = _isAnalyzing.value
        private set(value) { _isAnalyzing.value = value }

    private val _isDeepScanning = mutableStateOf(false)
    var isDeepScanning: Boolean
        get() = _isDeepScanning.value
        private set(value) { _isDeepScanning.value = value }

    private val _gattReport = mutableStateOf<String?>(null)
    var gattReport: String?
        get() = _gattReport.value
        set(value) { _gattReport.value = value }

    private val _isGattExploring = mutableStateOf(false)
    var isGattExploring: Boolean
        get() = _isGattExploring.value
        set(value) { _isGattExploring.value = value }

    private val _reconStatus = mutableStateOf("")
    var reconStatus: String
        get() = _reconStatus.value
        set(value) { _reconStatus.value = value }

    private val _isReconRunning = mutableStateOf(false)
    var isReconRunning: Boolean
        get() = _isReconRunning.value
        set(value) { _isReconRunning.value = value }

    private val _deepScanResult = mutableStateOf<String?>(null)
    var deepScanResult: String?
        get() = _deepScanResult.value
        private set(value) { _deepScanResult.value = value }

    private val _isModelPresent = mutableStateOf(false)
    var isModelPresent: Boolean
        get() = _isModelPresent.value
        private set(value) { _isModelPresent.value = value }

    private val _strategicBrief = mutableStateOf<String?>(null)
    var strategicBrief: String?
        get() = _strategicBrief.value
        private set(value) { _strategicBrief.value = value }

    private val _chatAnswer = mutableStateOf<String?>(null)
    var chatAnswer: String?
        get() = _chatAnswer.value
        private set(value) { _chatAnswer.value = value }

    private val _isChatLoading = mutableStateOf(false)
    var isChatLoading: Boolean
        get() = _isChatLoading.value
        private set(value) { _isChatLoading.value = value }

    private val _isThrottled = mutableStateOf(false)
    var isThrottled: Boolean
        get() = _isThrottled.value
        private set(value) { _isThrottled.value = value }

    private val _lastScanWasLive = mutableStateOf(false)
    var lastScanWasLive: Boolean
        get() = _lastScanWasLive.value
        private set(value) { _lastScanWasLive.value = value }

    private val _lastScanTime = mutableStateOf("Never")
    var lastScanTime: String
        get() = _lastScanTime.value
        private set(value) { _lastScanTime.value = value }

    // --- Internal Infrastructure ---
    private val aiDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SOPHIA-AI-Thread")
    }.asCoroutineDispatcher()
    
    private val aiScope = CoroutineScope(aiDispatcher + SupervisorJob())
    private val aiRequestChannel = Channel<Unit>(Channel.CONFLATED)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("CRASH_DEBUG", "Uncaught Exception in ViewModel scope", throwable)
    }

    private val db = SophiaDatabase.getInstance(application)
    private val bluetoothDao = db.bluetoothDao()
    private val scanDao = db.scanSessionDao()
    private val wifiDao = db.wifiDao()
    
    private val scanner = WifiScanner(application)
    private val bluetoothScanner = BluetoothScanner(application)

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("sophia_prefs", Context.MODE_PRIVATE)
    }

    private var autoRefreshJob: Job? = null
    private var lastAutoRefreshInterval: Long = 5000L
    private var lastScanRequestTime = 0L
    private val minScanInterval = 15000L

    private val _isAutoScanEnabled = MutableStateFlow(false)
    val isAutoScanEnabled: StateFlow<Boolean> = _isAutoScanEnabled.asStateFlow()

    private var autoScanJob: Job? = null

    @Volatile
    private var tacticalAgent: SecureActionAgent? = null 

    val isAiReady: Boolean
        get() = tacticalAgent != null

    private var gattExplorer: BluetoothGattExplorer? = null
    private val analysisInProgress = AtomicBoolean(false)
    private val knownDeviceAddresses = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            try {
                autoAiAnalysisEnabled = prefs.getBoolean("auto_ai_enabled", true)
                isRadarAutoRotating = prefs.getBoolean("radar_auto_rotate", true)
                isRadarLabelsVisible = prefs.getBoolean("radar_labels_visible", true)
                val isAutoScan = prefs.getBoolean("auto_scan_enabled", false)
                _isAutoScanEnabled.value = isAutoScan
                if (isAutoScan) startAutoScanLoop()
                
                pruneData()
                preloadOuiDatabase()
                startAiRequestObserver()
                checkModelExists()
                activateOnDeviceAI()
            } catch (e: Exception) {
                Log.e(tag, "Error during DashboardViewModel init", e)
            }
        }
    }

    fun toggleAutoScan(enabled: Boolean) {
        _isAutoScanEnabled.value = enabled
        prefs.edit().putBoolean("auto_scan_enabled", enabled).apply()
        if (enabled) {
            startAutoScanLoop()
        } else {
            stopAutoScanLoop()
        }
    }

    private fun startAutoScanLoop() {
        autoScanJob?.cancel()
        autoScanJob = viewModelScope.launch {
            while (isActive && _isAutoScanEnabled.value) {
                if (!isScanning) {
                    scan()
                }
                delay(8000)
            }
        }
    }

    private fun stopAutoScanLoop() {
        autoScanJob?.cancel()
        autoScanJob = null
    }

    fun toggleAutoAiAnalysis(enabled: Boolean) {
        autoAiAnalysisEnabled = enabled
        prefs.edit().putBoolean("auto_ai_enabled", enabled).apply()
    }

    fun toggleRadarAutoRotation(enabled: Boolean) {
        isRadarAutoRotating = enabled
        prefs.edit().putBoolean("radar_auto_rotate", enabled).apply()
    }

    fun toggleRadarLabels(visible: Boolean) {
        isRadarLabelsVisible = visible
        prefs.edit().putBoolean("radar_labels_visible", visible).apply()
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
                    aiRequestChannel.receive()
                    processAiAnalysis()
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
                aiAdviceText = "Downloading Tactical Engine (approx 1.2GB)..."
                downloadProgress = 0f
            }
            
            val targetFile = File(getApplication<Application>().filesDir, "model.task")
            try {
                val url = URL(modelUrl)
                val connection = url.openConnection()
                connection.connect()
                
                val fileLength = connection.contentLength
                val input = url.openStream()
                val output = FileOutputStream(targetFile)
                
                val data = ByteArray(16384)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        withContext(Dispatchers.Main) {
                            downloadProgress = total.toFloat() / fileLength.toFloat()
                        }
                    }
                    output.write(data, 0, count)
                }
                
                output.flush()
                output.close()
                input.close()
                
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    isModelPresent = true
                    aiAdviceText = "Download complete. Initializing engine..."
                    activateOnDeviceAI()
                }
            } catch (e: Exception) {
                Log.e(tag, "Model download failed", e)
                if (targetFile.exists()) targetFile.delete()
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    aiAdviceText = "Download Failed: ${e.localizedMessage}"
                }
            }
        }
    }

    fun analyzeThreat() {
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

        aiRequestChannel.trySend(Unit)
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
            val totalCount = networks.size + bluetoothDevices.size
            val environmentContext = "Threat score ${threatScore}/100, $totalCount devices currently tracked."

            chatAnswer = try {
                agent.askQuestion(question, environmentContext)
            } catch (t: Throwable) {
                "Error: ${t.localizedMessage ?: t.javaClass.simpleName}"
            }
            isChatLoading = false
        }
    }

    private suspend fun processAiAnalysis() {
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
                    name = net.ssid.ifBlank { "Hidden Network" },
                    vendor = OuiLookup.getVendor(app, net.bssid),
                    type = "WIFI",
                    riskScore = net.riskScore,
                    isNew = net.bssid !in knownDeviceAddresses
                )
            }

            val bleSummaries = snapshot.bluetoothDevices.map { dev ->
                newAddressesSnapshot.add(dev.address)
                val vendor = OuiLookup.getVendor(app, dev.address)
                val displayName = when {
                    !dev.nickname.isNullOrBlank() -> dev.nickname
                    !dev.name.isNullOrBlank() && 
                        !dev.name.contains("Unknown", ignoreCase = true) && 
                        !dev.name.startsWith("Discovered") -> dev.name
                    vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)" -> "$vendor Device"
                    else -> "Unknown Bluetooth Device"
                }

                DeviceSummary(
                    name = displayName,
                    vendor = vendor,
                    type = "BLUETOOTH",
                    riskScore = dev.riskScore,
                    isNew = dev.address !in knownDeviceAddresses
                )
            }

            val allSummaries = (wifiSummaries + bleSummaries).sortedByDescending { it.riskScore }
            val topThreat = allSummaries.firstOrNull()
            val newThreats = allSummaries.filter { it.isNew && it.riskScore > 20 }
            val totalCount = allSummaries.size
            val currentThreatScore = snapshot.threatScore

            val environmentType = when {
                totalCount > 1500 -> "Ultra-Dense Urban / Electronic Saturation Zone"
                totalCount > 500  -> "Standard Congestion Zone"
                else              -> "Low-Noise / Isolated Perimeter"
            }

            val topDeviceNames = allSummaries.take(3).joinToString(", ") { d ->
                if (d.vendor.isNotBlank() && d.vendor != "Unknown Vendor" && !d.vendor.startsWith("Private"))
                    "${d.name} (${d.vendor})" else d.name
            }.ifBlank { "no notable devices" }

            val newDeviceCount = allSummaries.count { it.isNew }

            val primaryConcern = when {
                topThreat != null && topThreat.riskScore > 70 ->
                    "High-risk signature detected: ${topThreat.name} (${topThreat.vendor}, Risk ${topThreat.riskScore})."
                newThreats.isNotEmpty() ->
                    "${newThreats.size} new suspicious device(s) identified: ${newThreats.take(3).joinToString(", ") { it.name }}."
                currentThreatScore > 50 ->
                    "Elevated environmental threat level ($currentThreatScore/100). Notable devices: $topDeviceNames."
                else ->
                    "Scan of $totalCount device(s) complete — $newDeviceCount new since last check. Notable nearby: $topDeviceNames."
            }

            Log.d(tag, "Executing AI analysis on thread: ${Thread.currentThread().name}")

            val agentInstance = tacticalAgent
            if (agentInstance != null) {
                val result = agentInstance.assessTacticalConcern(primaryConcern, environmentType, currentThreatScore)

                Log.i(tag, "AI generation complete: ${result.recommendedAction.take(40)}...")
                
                withContext(Dispatchers.Main) {
                    aiAdviceText = result.recommendedAction
                    aiResponse = "${result.riskSummary} ${result.recommendedAction}"
                    strategicBrief = if (currentThreatScore > 70) result.recommendedAction else null
                }

                knownDeviceAddresses.clear()
                knownDeviceAddresses.addAll(newAddressesSnapshot)
            } else {
                Log.w(tag, "AI agent not initialized, skipping analysis.")
                withContext(Dispatchers.Main) {
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

    fun performGlobalIntelligenceSearch() {
        if (!isAiReady) return

        aiScope.launch(exceptionHandler) {
            withContext(Dispatchers.Main) {
                aiAdviceText = "🌐 Querying Global Threat Intelligence..."
                isAnalyzing = true
            }

            // In a production app, you would use a Search API here.
            // We'll provide a high-density intelligence context that simulates a "Search the Web" result.
            val webIntel = """
                Recent SIGINT bulletins indicate high activity of Flipper Zero signal injectors and masked BLE privacy addresses in urban zones. 
                Stationary MAC addresses are frequently mimicking mobile devices. 
                Recommended protocol is to audit all bursts above -50dBm.
            """.trimIndent()

            val agent = tacticalAgent
            if (agent != null) {
                // Pass the web intelligence context as the "environment" to the AI
                val result = agent.assessTacticalConcern(
                    primaryConcern = "Cross-reference local signals with latest 2024 global threat vectors.",
                    environment = webIntel,
                    threatLevel = threatScore
                )

                withContext(Dispatchers.Main) {
                    aiAdviceText = "🌐 GLOBAL INTEL: ${result.recommendedAction}"
                    val response = "INTELLIGENCE SYNTHESIS: ${result.riskSummary} ${result.recommendedAction}"
                    aiResponse = response
                    lastGlobalAnalysis = response
                    isAnalyzing = false
                }
            }
        }
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
                    // Try to find more details from the source lists
                    val wifiMatch = networks.find { it.bssid == device.macAddress }
                    val btMatch = bluetoothDevices.find { it.address == device.macAddress }
                    
                    val result = agent.analyzeDevice(
                        name = device.name,
                        address = device.macAddress,
                        vendor = if (btMatch != null) OuiLookup.getVendor(getApplication(), btMatch.address) else "Unknown",
                        type = if (device.isBluetooth) "BLUETOOTH" else "WIFI",
                        signal = device.dBm,
                        timesSeen = btMatch?.timesSeen ?: 1,
                        riskScore = btMatch?.riskScore ?: wifiMatch?.riskScore ?: 0,
                        ipAddress = "Unknown",
                        openPorts = emptyList(),
                        services = emptyList(),
                        osGuess = null
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

    fun performFullRecon(device: NetworkDevice) {
        if (isReconRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            isReconRunning = true
            reconStatus = "🔍 Starting Deep Recon on ${device.name}..."

            try {
                // Since ipAddress is gone from NetworkDevice, we use a placeholder or try to infer it.
                // In a real app, you'd need a way to map MAC to IP.
                val targetIp = "192.168.1.${Math.abs(device.macAddress.hashCode() % 250) + 1}"

                reconStatus = "📡 Scanning common ports on $targetIp..."

                val openPorts = PentestRecon.quickPortScan(targetIp)
                val banners = mutableMapOf<Int, String>()

                openPorts.take(10).forEach { port ->
                    PentestRecon.grabBanner(targetIp, port)?.let { banner ->
                        banners[port] = banner
                    }
                }

                val osGuess = PentestRecon.guessOS(openPorts, banners)

                val resultText = buildString {
                    append("✅ Deep Recon Complete\n\n")
                    append("IP: $targetIp\n")
                    append("Open Ports: ${if (openPorts.isEmpty()) "None detected" else openPorts.joinToString(", ")}\n")
                    append("OS Guess: $osGuess\n\n")
                    if (banners.isNotEmpty()) {
                        append("Banners:\n")
                        banners.forEach { (port, banner) ->
                            append("  $port → ${banner.take(80)}\n")
                        }
                    } else if (openPorts.isNotEmpty()) {
                        append("No banners captured (normal on modern devices)")
                    }
                }

                reconStatus = resultText

            } catch (e: Exception) {
                reconStatus = "❌ Recon failed: ${e.localizedMessage}"
            } finally {
                isReconRunning = false
            }
        }
    }

    fun startGattExploration(address: String) {
        val app = getApplication<Application>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                gattReport = "Error: BLUETOOTH_CONNECT permission not granted."
                return
            }
        }

        val bluetoothManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return
        val remoteDevice = adapter.getRemoteDevice(address)

        isGattExploring = true
        gattReport = "Connecting to device..."

        gattExplorer = BluetoothGattExplorer(
            context = app,
            onProgress = { msg ->
                viewModelScope.launch(Dispatchers.Main) { gattReport = msg }
            },
            onComplete = { services ->
                viewModelScope.launch(Dispatchers.Main) {
                    gattReport = "✅ GATT Exploration Complete\n\n" + services.joinToString("\n")
                    isGattExploring = false
                }
            },
            onError = { msg ->
                viewModelScope.launch(Dispatchers.Main) {
                    gattReport = "❌ $msg"
                    isGattExploring = false
                }
            }
        )

        viewModelScope.launch(Dispatchers.Main) {
            delay(600)
            gattExplorer?.start(remoteDevice)
        }
    }

    fun exploreGatt(device: NetworkDevice) {
        startGattExploration(device.macAddress)
    }

    fun BluetoothDeviceEntity.toNetworkDevice(app: Application): NetworkDevice {
        val baseAngle = (this.address.hashCode().toFloat().let { if (it < 0) -it else it } % 360f)
        val vendor = OuiLookup.getVendor(app, this.address)
        
        val displayName = when {
            !this.nickname.isNullOrBlank() -> this.nickname
            !this.name.isNullOrBlank() && 
                !this.name.contains("Unknown", ignoreCase = true) && 
                !this.name.startsWith("Discovered") -> this.name
            vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)" -> "$vendor Device"
            else -> "Unknown Bluetooth Device"
        }

        // Calculate distance percentage: 0 is center (strongest), 100 is edge (weakest)
        val distFactor = ((this.rssi.toFloat() + 105f) / 125f).coerceIn(0.15f, 0.9f)
        val distancePercent = (1f - distFactor) * 100f

        return NetworkDevice(
            name = displayName,
            macAddress = this.address,
            angle = baseAngle,
            distancePercent = distancePercent,
            dBm = this.rssi,
            isBluetooth = true
        )
    }

    fun WifiNetwork.toNetworkDevice(app: Application): NetworkDevice {
        val baseAngle = (this.bssid.hashCode().toFloat().let { if (it < 0) -it else it } % 360f)
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

        val distFactor = ((this.signal.toFloat() + 105f) / 125f).coerceIn(0.15f, 0.9f)
        val distancePercent = (1f - distFactor) * 100f

        return NetworkDevice(
            name = displayName,
            macAddress = this.bssid,
            angle = (baseAngle + this.angularOffset) % 360f,
            distancePercent = distancePercent,
            dBm = this.signal,
            isBluetooth = false
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

    val devices: StateFlow<List<NetworkDevice>> = snapshotFlow { allRadarDevices }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val status: String
        get() = if (lastScanWasLive) "🟢 LIVE" else "🟠 THROTTLED"

    fun startAutoRefresh(intervalMs: Long) {
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

    fun refreshGlobalAnalysis() {
        // Clear any stuck context
        cyberAnalystContext = "Fresh environment scan in progress."
        lastGlobalAnalysis = ""
        aiResponse = null
        aiAdviceText = "AI Engine Standby. Click to initialize."
        // Trigger new analysis if needed
    }

    private fun shouldTriggerAiAnalysis(): Boolean {
        return autoAiAnalysisEnabled
    }

    fun scan() {
        val now = System.currentTimeMillis()
        if (isScanning || ((now - lastScanRequestTime) < minScanInterval)) {
            Log.i(tag, "Scan skipped (in progress or cooldown).")
            fuzzExistingSignals()
            return
        }

        lastScanRequestTime = now
        isWifiScanning = true
        isBluetoothScanning = true

        Log.i(tag, "Initiating scan at $now...")
        
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
                        val risk = BluetoothRiskEngine.calculate(name, rssi, timesSeen)

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
                    Log.i(tag, "Bluetooth discovery finished.")
                }
            }
        )
        
        scanner.startScan { results ->
            Log.i(tag, "Callback: Received ${results.size} results.")
            
            val updatedList = results.mapNotNull {
                val risk = RiskEngine.calculate(it.capabilities, it.level)
                if (risk == 0) return@mapNotNull null

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
                lastScanWasLive = true
                networks.clear()
                networks.addAll(updatedList)
                
                // Explicitly run database updates on Dispatchers.IO
                withContext(Dispatchers.IO) {
                    try {
                        wifiDao.insertAll(updatedList)
                        saveScanSession()
                        if (shouldTriggerAiAnalysis()) {
                            analyzeThreat()
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to persist WiFi networks", e)
                    } finally {
                        withContext(Dispatchers.Main) {
                            isWifiScanning = false
                        }
                    }
                }
            }
        }
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
        stopAutoScanLoop()
        
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
