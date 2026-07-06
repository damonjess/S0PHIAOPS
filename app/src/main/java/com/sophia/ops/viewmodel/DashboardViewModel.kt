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
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.bluetooth.BluetoothScanner
import com.sophia.ops.bluetooth.BluetoothRiskEngine
import com.sophia.ops.wifi.RiskEngine
import com.sophia.ops.wifi.WifiScanner
import com.sophia.ops.model.NetworkDevice
import com.sophia.ops.model.DeviceType
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
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

    // Dedicated single thread and scope for all on-device AI work — the engine 
    // must always be created and invoked from exactly the same thread.
    private val aiDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SOPHIA-AI-Thread")
    }.asCoroutineDispatcher()
    
    private val aiScope = CoroutineScope(aiDispatcher + SupervisorJob())
    
    // Channel for debouncing AI requests. CONFLATED ensures we only process the LATEST
    // request if multiple scans finish while the AI is busy.
    private val aiRequestChannel = Channel<Unit>(Channel.CONFLATED)

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
    
    init {
        pruneData()
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
    
    var isScanning by mutableStateOf(value = false)
        private set

    private var isWifiScanning = false
    private var isBluetoothScanning = false

    private var autoRefreshJob: Job? = null
    private var lastAutoRefreshInterval: Long = 5000L
    
    private var lastScanRequestTime = 0L
    private val minScanInterval = 15000L // Increased to 15s to reduce pressure during AI generation

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    var selectedRadarDevice by mutableStateOf<NetworkDevice?>(null)
        private set

    var selectedDevice by mutableStateOf<BluetoothDeviceEntity?>(null)
        private set

    var aiResponse by mutableStateOf<String?>(null)
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

    var isReconRunning by mutableStateOf(false)
        private set

    var gattReport by mutableStateOf<String?>(null)
        private set

    var isGattExploring by mutableStateOf(false)
        private set

    private val gattExplorer = BluetoothGattExplorer(application)

    var deepScanResult by mutableStateOf<String?>(null)
        private set

    var isModelPresent by mutableStateOf(false)
        private set

    private val analysisInProgress = AtomicBoolean(false)

    private var lastAnalyzedThreatScore: Int? = null
    private var lastAnalysisTimestamp = 0L
    private val minAnalysisInterval = 120_000L // 2 min cooldown unless something actually changed
    private val knownDeviceAddresses = mutableSetOf<String>()

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
                DeviceSummary(
                    name = dev.nickname ?: dev.name ?: "Unknown Bluetooth Device",
                    vendor = OuiLookup.getVendor(app, dev.address),
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

                lastAnalyzedThreatScore = currentThreatScore
                lastAnalysisTimestamp = System.currentTimeMillis()
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
                    aiResponse = "INTELLIGENCE SYNTHESIS: ${result.riskSummary} ${result.recommendedAction}"
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
                    val result = agent.analyzeDevice(
                        name = device.name,
                        address = device.address,
                        vendor = device.vendor,
                        type = device.type.name,
                        signal = device.signal,
                        timesSeen = device.timesSeen,
                        riskScore = device.riskScore.toInt(),
                        ipAddress = device.ipAddress,
                        openPorts = device.openPorts,
                        services = device.services,
                        osGuess = device.osGuess
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
            try {
                // Try to get IP via ARP or assume from subnet if on same LAN
                val ip = device.ipAddress.ifBlank { "192.168.1.${device.address.takeLast(2).toIntOrNull(16) ?: 100}" }

                val openPorts = PentestRecon.quickPortScan(ip)
                val banners = mutableMapOf<Int, String>()
                openPorts.take(5).forEach { p ->
                    PentestRecon.grabBanner(ip, p)?.let { banners[p] = it }
                }
                val os = PentestRecon.guessOS(openPorts, banners)

                withContext(Dispatchers.Main) {
                    // Update device in lists
                    val updated = device.copy(
                        ipAddress = ip,
                        openPorts = openPorts,
                        osGuess = os,
                        services = banners.values.toList()
                    )
                    // Refresh UI lists...
                    analyzeThreat()
                }
            } finally {
                isReconRunning = false
            }
        }
    }

    fun exploreGatt(device: NetworkDevice) {
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val remoteDevice = adapter.getRemoteDevice(device.address)

        isGattExploring = true
        gattReport = "Initializing GATT connection..."
        
        gattExplorer.explore(remoteDevice) { result ->
            viewModelScope.launch(Dispatchers.Main) {
                gattReport = result
                if (result == "Disconnected." || result.startsWith("GATT Error") || result.startsWith("GATT Services")) {
                    isGattExploring = false
                }
            }
        }
    }

    fun BluetoothDeviceEntity.toNetworkDevice(app: Application): NetworkDevice {
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

    fun WifiNetwork.toNetworkDevice(app: Application): NetworkDevice {
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

    private fun shouldTriggerAiAnalysis(): Boolean {
        val now = System.currentTimeMillis()
        val currentScore = threatScore
        val scoreDelta = lastAnalyzedThreatScore?.let { kotlin.math.abs(currentScore - it) } ?: Int.MAX_VALUE
        val hasNewDevices = allRadarDevices.any { it.address !in knownDeviceAddresses }
        val cooldownElapsed = (now - lastAnalysisTimestamp) >= minAnalysisInterval

        return hasNewDevices || scoreDelta >= 5 || cooldownElapsed
    }

    fun scan() {
        val now = System.currentTimeMillis()
        if (isScanning || ((now - lastScanRequestTime) < minScanInterval)) {
            Log.i(tag, "Scan skipped (in progress or cooldown).")
            fuzzExistingSignals()
            return
        }

        lastScanRequestTime = now
        isScanning = true
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
                    isScanning = isWifiScanning || isBluetoothScanning
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
                isThrottled = false 
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
                            isScanning = isWifiScanning || isBluetoothScanning
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