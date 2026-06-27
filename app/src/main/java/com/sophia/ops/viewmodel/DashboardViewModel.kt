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
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.bluetooth.BluetoothScanner
import com.sophia.ops.bluetooth.BluetoothRiskEngine
import com.sophia.ops.wifi.RiskEngine
import com.sophia.ops.wifi.WifiScanner
import android.util.Log
import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class DashboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val tag = "DashboardViewModel"
    
    private val db = Room.databaseBuilder(
        application,
        SophiaDatabase::class.java, "sophia-db"
    ).fallbackToDestructiveMigration().build()
    
    private val bluetoothDao = db.bluetoothDao()
    private val scanDao = db.scanSessionDao()
    private val wifiDao = db.wifiDao()
    
    private val scanner = WifiScanner(getApplication())
    private val bluetoothScanner = BluetoothScanner(getApplication())
    var isScanning by mutableStateOf(false)
        private set

    private var autoRefreshJob: Job? = null
    
    private var lastScanRequestTime = 0L
    private val minScanInterval = 8000L // 8 seconds

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    var selectedDevice by mutableStateOf<BluetoothDeviceEntity?>(null)
        private set

    fun selectDevice(device: BluetoothDeviceEntity?) {
        selectedDevice = device
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
    
    val threatScore: Int
        get() = (networks.size + bluetoothDevices.size).coerceIn(0, 100)

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

    fun scan() {
        val now = System.currentTimeMillis()
        if (isScanning || (now - lastScanRequestTime < minScanInterval)) {
            Log.i(tag, "Scan skipped (in progress or cooldown).")
            fuzzExistingSignals()
            return
        }

        lastScanRequestTime = now
        isScanning = true
        Log.i(tag, "Initiating scan at $now...")
        
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        lastScanTime = sdf.format(Date(now))
        
        // Remove immediate clear to avoid UI flickering
        // bluetoothDevices.clear() 
        
        bluetoothScanner.startDiscovery { device, rssi ->
            val name = getDisplayName(device)
            val risk = BluetoothRiskEngine.calculate(name)
            
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
                            riskScore = if (isNewNameAvailable) risk else existing.riskScore,
                            rssi = rssi,
                            timesSeen = existing.timesSeen + 1,
                            signalHistory = trimmedHistory
                        )
                        
                        bluetoothDao.updateDevice(updatedEntity)
                        Log.d("BT", "Existing device updated: ${updatedEntity.address}")
                        updatedEntity
                    }

                    // Sync with UI list
                    val uiIndex = bluetoothDevices.indexOfFirst { it.address == device.address }
                    if (uiIndex != -1) {
                        bluetoothDevices[uiIndex] = entity
                    } else {
                        bluetoothDevices.add(entity)
                    }

                    if (selectedDevice?.address == entity.address) {
                        selectedDevice = entity
                    }
                } catch (e: Exception) {
                    Log.e("BT", "Database sync failed", e)
                }
            }
        }
        
        scanner.startScan { results ->
            isScanning = false
            // If the scanner returned immediately without a broadcast, it was likely throttled
            // We can infer this by checking if startScan internal logic reported success or if it's returning cached
            // For simplicity in the UI, we'll assume fresh results arrive if they were processed via the scanner's callback
            // after a real trigger. However, let's make it more explicit.
            
            Log.i(tag, "Callback: Received ${results.size} results.")
            isThrottled = false // Reset throttle indicator on new callback
            lastScanWasLive = true
            
            val updatedList = results.map {
                val risk = RiskEngine.calculate(it.capabilities, it.level)
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
                } catch (e: Exception) {
                    Log.e(tag, "Failed to persist WiFi networks", e)
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
        val bondedMatch = adapter?.bondedDevices?.firstOrNull { it.address == device.address }
        val bondedName = bondedMatch?.name
        if (!bondedName.isNullOrBlank()) return bondedName

        // 2. Try the name reported during discovery
        val name = device.name
        if (!name.isNullOrBlank()) return name

        // 3. Try the alias (Android R+)
        val alias = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) device.alias else null
        if (!alias.isNullOrBlank()) return alias

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
