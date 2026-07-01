package com.sophia.ops.viewmodel

import android.app.Application
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
import android.util.Log
import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.random.Random
import kotlin.jvm.Volatile

class DashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val tag = "DashboardViewModel"
    
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
    
    private var lastScanRequestTime = 0L
    private val minScanInterval = 8000L 

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    var selectedRadarDevice by mutableStateOf<NetworkDevice?>(null)
        private set

    var selectedDevice by mutableStateOf<BluetoothDeviceEntity?>(null)
        private set

    var aiResponse by mutableStateOf<String?>(null)
        private set

    @Volatile
    private var tacticalAgent: Any? = null 

    val isAiReady: Boolean
        get() = tacticalAgent != null

    var aiAdviceText by mutableStateOf("AI Engine Standby. Click to initialize.")
        private set

    var aiInitializationFailed by mutableStateOf(value = false)
        private set

    var isAiLoading by mutableStateOf(value = false)
        private set

    var isAnalyzing by mutableStateOf(value = false)
        private set

    private val analysisInProgress = AtomicBoolean(false)

    var strategicBrief by mutableStateOf<String?>(null)
        private set

    fun activateOnDeviceAI() {
        if (tacticalAgent != null) return 
        
        viewModelScope.launch(exceptionHandler) {
            isAiLoading = true
            aiInitializationFailed = false
            
            val targetPath = "/data/local/tmp/gemma-2b-it-cpu.bin"
            
            val fileExists = withContext(Dispatchers.IO) { File(targetPath).exists() }
            if (!fileExists) {
                aiInitializationFailed = true
                aiAdviceText = "AI weights file missing at: $targetPath"
                isAiLoading = false
                return@launch
            }

            val initializedInstance = withContext(Dispatchers.IO) {
                try {
                    Log.d(tag, "Attempting to load SecureActionAgent via reflection...")
                    val agentClass = Class.forName("com.sophia.ops.ai.SecureActionAgent")
                    val constructor = agentClass.getConstructor(Context::class.java, String::class.java)
                    val instance = constructor.newInstance(getApplication(), targetPath)
                    
                    val initMethod = agentClass.getMethod("initializeEngine")
                    val result = initMethod.invoke(instance) as Boolean
                    
                    if (result) {
                        Log.i(tag, "SecureActionAgent initialized successfully via reflection.")
                        instance
                    } else {
                        Log.e(tag, "SecureActionAgent reports failure during initialization.")
                        null
                    }
                } catch (t: Throwable) {
                    Log.e(tag, "Reflection-based AI initialization failed", t)
                    null
                }
            }

            if (initializedInstance != null) {
                tacticalAgent = initializedInstance
                aiInitializationFailed = false
                aiAdviceText = "SOPHIA AI Engine Online. Awaiting threat metrics..."
            } else {
                aiInitializationFailed = true
                aiAdviceText = "AI Failed to initialize (check weights or logcat)"
            }
            isAiLoading = false
        }
    }

    fun analyzeThreat() {
        if (!analysisInProgress.compareAndSet(false, true)) {
            Log.i(tag, "analyzeThreat() skipped - already in progress.")
            return
        }

        Log.i(tag, "analyzeThreat() triggered. AI Ready: $isAiReady")
        viewModelScope.launch(exceptionHandler) {
            isAnalyzing = true
            
            try {
                val wifiCount = networks.size
                val bleCount = bluetoothDevices.size
                val totalCount = wifiCount + bleCount
                val currentThreatScore = threatScore
                Log.d(tag, "Analyzing threat: $wifiCount WiFi, $bleCount BLE. Score: $currentThreatScore")

                val environmentType = when {
                    totalCount > 1500 -> "Ultra-Dense Urban / Electronic Saturation Zone"
                    totalCount > 500  -> "Standard Congestion Zone"
                    else              -> "Low-Noise / Isolated Perimeter"
                }

                val telemetryPayload = """
                    Density Context: $environmentType
                    Raw Environment Signals: $wifiCount Wi-Fi, $bleCount Bluetooth nodes.
                    Active Countermeasures Status: Adaptive attenuation active. Persistent targets identified.
                """.trimIndent()
                
                val agentInstance = tacticalAgent
                if (agentInstance != null) {
                    val agentClass = agentInstance.javaClass
                    Log.d(tag, "Invoking AI agent: ${agentClass.simpleName}")
                    
                    val analyzeMethod = agentClass.getMethod(
                        "generateActionAdvice", 
                        Int::class.javaPrimitiveType, 
                        String::class.java
                    )
                    
                    Log.d(tag, "Method found, executing on IO thread...")
                    val generatedBrief = withContext(Dispatchers.IO) {
                        analyzeMethod.invoke(agentInstance, currentThreatScore, telemetryPayload) as String
                    }
                    
                    Log.i(tag, "AI generation complete: ${generatedBrief.take(20)}...")
                    aiAdviceText = generatedBrief
                    aiResponse = generatedBrief
                    
                    strategicBrief = if (currentThreatScore > 70) {
                        generatedBrief
                    } else {
                        null
                    }
                } else {
                    Log.w(tag, "AI agent not initialized, skipping analysis.")
                    aiAdviceText = "AI Engine Standby. Click to initialize."
                    strategicBrief = null
                }
            } catch (t: Throwable) {
                Log.e("CRASH_DEBUG", "AI analysis failed internally", t)
                aiAdviceText = "Tactical generation suspended: Subsystem error."
                strategicBrief = "Strategic analysis failed."
            } finally {
                isAnalyzing = false
                analysisInProgress.set(false)
            }
        }
    }

    fun selectDevice(device: NetworkDevice?) {
        selectedRadarDevice = device
    }

    fun selectBluetoothDevice(entity: BluetoothDeviceEntity?) {
        selectedDevice = entity
        selectedRadarDevice = entity?.toNetworkDevice(getApplication())
    }

    @Suppress("unused")
    fun selectWifiNetwork(network: WifiNetwork?) {
        selectedRadarDevice = network?.toNetworkDevice(getApplication())
    }

    private fun BluetoothDeviceEntity.toNetworkDevice(app: Application): NetworkDevice {
        val baseAngle = (this.address.hashCode().toFloat() % 360f)
        return NetworkDevice(
            id = this.address,
            name = this.nickname ?: this.name ?: "Unknown Bluetooth Device",
            address = this.address,
            vendor = OuiLookup.getVendor(app, this.address),
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
        return NetworkDevice(
            id = this.bssid,
            name = this.ssid,
            address = this.bssid,
            vendor = OuiLookup.getVendor(app, this.bssid),
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
                            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
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

                        // FIX: Safely mutate Compose state tracking atomically 
                        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
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
                isBluetoothScanning = false
                isScanning = isWifiScanning || isBluetoothScanning
                Log.i(tag, "Bluetooth discovery finished.")
            }
        )
        
        scanner.startScan { results ->
            Log.i(tag, "Callback: Received ${results.size} results.")
            isThrottled = false 
            lastScanWasLive = true
            
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

            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                networks.clear()
                networks.addAll(updatedList)
            }

            // FIX: Explicitly run database updates on Dispatchers.IO
            viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                try {
                    wifiDao.insertAll(updatedList)
                    saveScanSession()
                    analyzeThreat()
                } catch (e: Exception) {
                    Log.e(tag, "Failed to persist WiFi networks", e)
                } finally {
                    isWifiScanning = false
                    isScanning = isWifiScanning || isBluetoothScanning
                }
            }
        }
    }

    private suspend fun saveScanSession() {
        try {
            val wifiCount = networks.size
            val bleCount = bluetoothDevices.size
            val currentThreat = threatScore
            
            val session = ScanSession(
                timestamp = System.currentTimeMillis(),
                wifiCount = wifiCount,
                bluetoothCount = bleCount,
                threatScore = currentThreat
            )
            scanDao.insert(session)
        } catch (e: Exception) {
            Log.e(tag, "Failed to save scan session", e)
        }
    }

    private fun fuzzExistingSignals() {
        if (networks.isEmpty()) return
        isThrottled = true 
        lastScanWasLive = false
        
        Log.d(tag, "Applying aggressive signal fuzz to keep radar alive.")
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
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
        }
        
        viewModelScope.launch(exceptionHandler) {
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
}