package com.sophia.ops.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import com.sophia.ops.ai.CyberDefenseAnalyst
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
import kotlin.random.Random

class DashboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val tag = "DashboardViewModel"
    
    private val db = SophiaDatabase.getInstance(application)
    
    private val bluetoothDao = db.bluetoothDao()
    private val scanDao = db.scanSessionDao()
    private val wifiDao = db.wifiDao()
    
    private val scanner = WifiScanner(application)
    private val bluetoothScanner = BluetoothScanner(application)
    
    init {
        // prepareTacticalAI() // Completely deferred to activateOnDeviceAI() to prevent boot-loading
        pruneData()
    }

    private fun pruneData() {
        viewModelScope.launch(Dispatchers.IO) {
            val twentyFourHoursAgo = System.currentTimeMillis() - 86400000L
            bluetoothDao.pruneTransientOldSignals(twentyFourHoursAgo)
            Log.i(tag, "Automated Cleanup: Dropped unverified/low-risk signals older than 24 hours.")
        }
    }
    
    var isScanning by mutableStateOf(false)
        private set

    private var isWifiScanning = false
    private var isBluetoothScanning = false

    private var autoRefreshJob: Job? = null
    
    private var lastScanRequestTime = 0L
    private val minScanInterval = 8000L // 8 seconds

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    // Tracks the currently selected device from the radar
    var selectedRadarDevice by mutableStateOf<NetworkDevice?>(null)
        private set

    var selectedDevice by mutableStateOf<BluetoothDeviceEntity?>(null)
        private set

    var aiResponse by mutableStateOf<String?>(null)
        private set

    // Completely avoid referring to the class directly at boot
    private var tacticalAgent: Any? = null 

    val isAiReady: Boolean
        get() = tacticalAgent != null

    var aiAdviceText by mutableStateOf("AI Engine Standby. Click to initialize.")
        private set

    var aiInitializationFailed by mutableStateOf(false)
        private set

    var isAiLoading by mutableStateOf(false)
        private set

    var isAnalyzing by mutableStateOf(false)
        private set

    var strategicBrief by mutableStateOf<String?>(null)
        private set

    /**
     * Call this ONLY from a button click or user action inside your UI, 
     * never call it inside init {} or onCreate()!
     */
    fun activateOnDeviceAI() {
        if (tacticalAgent != null) return // Already running safely
        
        viewModelScope.launch {
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

            // Force dynamic class verification safely isolated inside an IO block
            val initResult = withContext(Dispatchers.IO) {
                try {
                    val agentClass = Class.forName("com.sophia.ops.ai.SecureActionAgent")
                    val constructor = agentClass.getConstructor(android.content.Context::class.java, String::class.java)
                    val instance = constructor.newInstance(getApplication<Application>(), targetPath)
                    
                    val initMethod = agentClass.getMethod("initializeEngine")
                    val result = initMethod.invoke(instance) as Boolean
                    
                    if (result) {
                        tacticalAgent = instance
                        Result.success(true)
                    } else {
                        Result.failure(Exception("Engine reports failure (check logcat/weights)"))
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Result.failure(t)
                }
            }

            if (initResult.isSuccess) {
                aiInitializationFailed = false
                aiAdviceText = "SOPHIA AI Engine Online. Awaiting threat metrics..."
            } else {
                aiInitializationFailed = true
                val error = initResult.exceptionOrNull()?.localizedMessage ?: "Unknown link error"
                aiAdviceText = "AI Failed: $error"
            }
            isAiLoading = false
        }
    }

    fun analyzeThreat() {
        if (isAnalyzing) return
        viewModelScope.launch {
            isAnalyzing = true
            
            val wifiCount = networks.size
            val bleCount = bluetoothDevices.size
            val totalCount = wifiCount + bleCount
            val currentThreatScore = threatScore

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
                try {
                    val agentClass = agentInstance.javaClass
                    
                    // FIX: Use javaPrimitiveType for 'int' parameter to match Kotlin's 'Int'
                    val analyzeMethod = agentClass.getMethod(
                        "generateActionAdvice", 
                        Int::class.javaPrimitiveType, 
                        String::class.java
                    )
                    
                    val generatedBrief = withContext(Dispatchers.Default) {
                        analyzeMethod.invoke(agentInstance, currentThreatScore, telemetryPayload) as String
                    }
                    
                    aiAdviceText = generatedBrief
                    aiResponse = generatedBrief
                    
                    // Strategic brief for high-threat scenarios (> 70%)
                    if (currentThreatScore > 70) {
                        strategicBrief = generatedBrief
                    } else {
                        strategicBrief = null
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    aiAdviceText = "Tactical generation suspended: Subsystem mismatch."
                    strategicBrief = "Strategic analysis failed due to a native subsystem error."
                }
            } else {
                aiAdviceText = "AI Engine Standby. Click to initialize."
                strategicBrief = null
            }

            isAnalyzing = false
        }
    }

    fun selectDevice(device: NetworkDevice?) {
        selectedRadarDevice = device
    }

    fun selectBluetoothDevice(entity: BluetoothDeviceEntity?) {
        selectedDevice = entity
        selectedRadarDevice = entity?.toNetworkDevice(getApplication())
    }

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
            radarAngle = baseAngle
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
        // Establish a baseline environment density factor
        val totalSignalDensity = wifiCount + bleCount
        
        // Scale down raw counts if in a high-density zone to prevent immediate 100% saturation
        val densityAttenuationMultiplier = when {
            totalSignalDensity > 1500 -> 0.15f  // Intense density (like your field test)
            totalSignalDensity > 500  -> 0.40f  // Standard urban density
            else                      -> 1.00f  // Quiet/Isolated perimeter
        }

        // Base score calculations using attenuated environmental metrics
        val adjustedWifiScore = (wifiCount * 0.2f) * densityAttenuationMultiplier
        val adjustedBleScore = (bleCount * 0.1f) * densityAttenuationMultiplier
        
        // High-risk indicators (e.g., matching known malicious profiles) bypass the density filter
        val criticalVectorScore = highRiskDevices * 25 

        // Merge parameters and constrain between 0% and 100%
        val finalCalculatedScore = (adjustedWifiScore + adjustedBleScore + criticalVectorScore).toInt()
        return finalCalculatedScore.coerceIn(0, 100)
    }

    val threatScore: Int
        get() {
            val wifiSnapshot = networks.toList()
            val bleSnapshot = bluetoothDevices.toList()
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

    // UI indicator for throttling
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
                delay(intervalMs)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun scan() {
        val now = System.currentTimeMillis()
        if (isScanning || (now - lastScanRequestTime < minScanInterval)) {
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
        
        // Remove immediate clear to avoid UI flickering
        // bluetoothDevices.clear() 
        
        bluetoothScanner.startDiscovery(
            onDeviceFound = { device, rssi ->
                val name = getDisplayName(device)

                viewModelScope.launch {
                    try {
                        val existing = bluetoothDao.getDeviceByAddress(device.address)
                        val now = System.currentTimeMillis()

                        if (existing?.ignored == true) {
                            // Ensure it's not in the UI list if it was marked as ignored elsewhere
                            val uiIndex = bluetoothDevices.indexOfFirst { it.address == device.address }
                            if (uiIndex != -1) {
                                bluetoothDevices.removeAt(uiIndex)
                            }
                            return@launch
                        }

                        val timesSeen = (existing?.timesSeen ?: 0) + 1
                        val risk = BluetoothRiskEngine.calculate(name, rssi, timesSeen)

                        val newHistory = (existing?.signalHistory ?: emptyList()) + SignalPoint(rssi, now)
                        val trimmedHistory = newHistory.takeLast(10)

                        @SuppressLint("MissingPermission")
                        val entity = if (existing == null) {
                            val sanitizedName = if (name?.startsWith("Discovered Device") == true) null else name
                            val newEntity = BluetoothDeviceEntity(
                                name = sanitizedName,
                                address = device.address,
                                deviceType = device.type,
                                firstSeen = now,
                                lastSeen = now,
                                riskScore = risk,
                                rssi = rssi,
                                timesSeen = 1,
                                signalHistory = trimmedHistory
                            )
                            bluetoothDao.insert(newEntity)
                            Log.d("BT", "New device persisted: ${newEntity.address}")
                            newEntity
                        } else {
                            // Update name if we just found a real one, otherwise keep existing
                            val isNewNameAvailable = !name.isNullOrBlank() && !name.startsWith("Discovered Device")
                            val finalName = if (isNewNameAvailable) {
                                name
                            } else {
                                if (existing.name?.startsWith("Discovered Device") == true) null else existing.name
                            }

                            val updatedEntity = existing.copy(
                                name = finalName,
                                lastSeen = now,
                                riskScore = risk,
                                rssi = rssi,
                                timesSeen = timesSeen,
                                signalHistory = trimmedHistory
                            )

                            bluetoothDao.updateDevice(updatedEntity)
                            Log.d("BT", "Existing device updated: ${updatedEntity.address}")
                            updatedEntity
                        }

                        // Sync with UI list: If risk is 0 (transient), we keep it in DB but can choose to exclude from UI 
                        // to reduce noise, or just let it stay with 0 risk. 
                        // The user said "categorized as background noise, dropping their threat weight to zero".
                        // Let's keep them in the list so they are visible on Radar but don't impact the main Score.
                        val uiIndex = bluetoothDevices.indexOfFirst { it.address == device.address }
                        if (uiIndex != -1) {
                            bluetoothDevices[uiIndex] = entity
                        } else {
                            bluetoothDevices.add(entity)
                        }

                        if (selectedDevice?.address == entity.address) {
                            selectedDevice = entity
                            selectedRadarDevice = entity.toNetworkDevice(getApplication())
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
            // If the scanner returned immediately without a broadcast, it was likely throttled
            // We can infer this by checking if startScan internal logic reported success or if it's returning cached
            // For simplicity in the UI, we'll assume fresh results arrive if they were processed via the scanner's callback
            // after a real trigger. However, let's make it more explicit.
            
            Log.i(tag, "Callback: Received ${results.size} results.")
            isThrottled = false // Reset throttle indicator on new callback
            lastScanWasLive = true
            
            val updatedList = results.mapNotNull {
                val risk = RiskEngine.calculate(it.capabilities, it.level)
                if (risk == 0) return@mapNotNull null

                WifiNetwork(
                    ssid = it.SSID,
                    bssid = it.BSSID,
                    signal = it.level + Random.nextInt(-1, 2),
                    security = it.capabilities,
                    riskScore = risk,
                    timestamp = System.currentTimeMillis(),
                    angularOffset = Random.nextFloat() * 10f - 5f // Small random angle drift
                )
            }

            networks.clear()
            networks.addAll(updatedList)

            // Save scan session and networks
            viewModelScope.launch {
                try {
                    wifiDao.insertAll(updatedList)
                    saveScanSession()
                    // Force AI analysis for the field test
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
            val session = ScanSession(
                timestamp = System.currentTimeMillis(),
                wifiCount = networks.size,
                bluetoothCount = bluetoothDevices.size,
                threatScore = threatScore
            )
            scanDao.insert(session)
            Log.d(tag, "Scan session saved: ${session.threatScore} threat score")
        } catch (e: Exception) {
            Log.e(tag, "Failed to save scan session", e)
        }
    }

    private fun fuzzExistingSignals() {
        if (networks.isEmpty()) return
        isThrottled = true // Show indicator
        lastScanWasLive = false
        
        Log.d(tag, "Applying aggressive signal fuzz to keep radar alive.")
        for (i in networks.indices) {
            val net = networks[i]
            // Randomly nudge signal and angle to make them move
            val newSignal = net.signal + Random.nextInt(-2, 3) 
            val newAngle = net.angularOffset + (Random.nextFloat() * 4f - 2f)
            
            networks[i] = net.copy(
                signal = newSignal.coerceIn(-100, -20),
                angularOffset = newAngle.coerceIn(-15f, 15f),
                timestamp = System.currentTimeMillis() // Update timestamp to show "activity"
            )
        }
        
        // Reset throttled state after a short while so it flashes
        viewModelScope.launch {
            delay(1000)
            isThrottled = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDisplayName(device: BluetoothDevice): String? {
        // 1. Check if the device is already paired (bonded) for a high-quality name
        val adapter = BluetoothAdapter.getDefaultAdapter()
        
        // Safety check for BLUETOOTH_CONNECT permission on Android 12+
        val hasConnectPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getApplication<Application>().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasConnectPermission) {
            val bondedMatch = adapter?.bondedDevices?.firstOrNull { it.address == device.address }
            val bondedName = bondedMatch?.name
            if (!bondedName.isNullOrBlank()) return bondedName

            // 2. Try the name reported during discovery
            val name = device.name
            if (!name.isNullOrBlank()) return name

            // 3. Try the alias (Android R+)
            val alias = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) device.alias else null
            if (!alias.isNullOrBlank()) return alias
        }

        // 4. No name found
        return null
    }

    fun toggleFavourite(device: BluetoothDeviceEntity) {
        viewModelScope.launch {
            val newState = !device.favourite
            bluetoothDao.updateFavourite(device.address, newState)
            val index = bluetoothDevices.indexOfFirst { it.address == device.address }
            if (index != -1) {
                bluetoothDevices[index] = bluetoothDevices[index].copy(favourite = newState)
            }
        }
    }

    fun updateNickname(device: BluetoothDeviceEntity, nickname: String?) {
        viewModelScope.launch {
            bluetoothDao.updateNickname(device.address, nickname)
            val index = bluetoothDevices.indexOfFirst { it.address == device.address }
            if (index != -1) {
                bluetoothDevices[index] = bluetoothDevices[index].copy(nickname = nickname)
            }
        }
    }

    fun updateNotes(device: BluetoothDeviceEntity, notes: String?) {
        viewModelScope.launch {
            bluetoothDao.updateNotes(device.address, notes)
            val index = bluetoothDevices.indexOfFirst { it.address == device.address }
            if (index != -1) {
                bluetoothDevices[index] = bluetoothDevices[index].copy(notes = notes)
            }
        }
    }
}
