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
import kotlinx.coroutines.launch
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
    
    private val scanner = WifiScanner(getApplication())
    private val bluetoothScanner = BluetoothScanner(getApplication())
    private var isScanning = false
    private var autoRefreshJob: Job? = null
    
    private var lastScanRequestTime = 0L
    private val minScanInterval = 8000L // 8 seconds

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    val totalThreatScore: Int
        get() = (networks.size + bluetoothDevices.size).coerceIn(0, 100)
    
    // UI indicator for throttling
    var isThrottled by mutableStateOf(false)
        private set

    var lastScanWasLive by mutableStateOf(false)
        private set

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
        
        // Remove immediate clear to avoid UI flickering
        // bluetoothDevices.clear() 
        
        bluetoothScanner.startDiscovery { device ->
            val name = getDisplayName(device)
            Log.d("BT", "Adding device: ${device.address} ($name)")
            val risk = BluetoothRiskEngine.calculate(name)
            
            val existingIndex = bluetoothDevices.indexOfFirst { it.address == device.address }
            if (existingIndex != -1) {
                val existingDevice = bluetoothDevices[existingIndex]
                
                // Update existing device
                val updatedDevice = existingDevice.copy(
                    name = name,
                    lastSeen = System.currentTimeMillis(),
                    riskScore = risk
                )
                bluetoothDevices[existingIndex] = updatedDevice

                viewModelScope.launch {
                    try {
                        @SuppressLint("MissingPermission")
                        val newRealName = device.name
                        if (existingDevice.name.startsWith("Discovered Device") && !newRealName.isNullOrBlank()) {
                            bluetoothDao.updateName(existingDevice.id, newRealName)
                            Log.d("BT", "Updated generic name to: $newRealName")
                        }
                        
                        // Also update lastSeen and risk regardless
                        bluetoothDao.insert(updatedDevice)
                    } catch (e: Exception) {
                        Log.e("BT", "Failed to update DB", e)
                    }
                }
            } else {
                // Add new device
                @SuppressLint("MissingPermission")
                val entity = BluetoothDeviceEntity(
                    name = name,
                    address = device.address,
                    deviceType = device.type, 
                    firstSeen = System.currentTimeMillis(),
                    lastSeen = System.currentTimeMillis(),
                    riskScore = risk
                )
                bluetoothDevices.add(entity)
                
                // Save to database
                viewModelScope.launch {
                    try {
                        bluetoothDao.insert(entity)
                        Log.d("BT", "Device persisted to DB: ${entity.address}")
                    } catch (e: Exception) {
                        Log.e("BT", "Failed to save to DB", e)
                    }
                }
            }

            Log.d("BT", "Total devices in VM: ${bluetoothDevices.size}")
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

            // Save scan session summary
            saveScanSession()
        }
    }

    private fun saveScanSession() {
        viewModelScope.launch {
            try {
                val session = ScanSession(
                    timestamp = System.currentTimeMillis(),
                    wifiCount = networks.size,
                    bluetoothCount = bluetoothDevices.size,
                    threatScore = totalThreatScore
                )
                scanDao.insert(session)
                Log.d(tag, "Scan session saved: ${session.threatScore} threat score")
            } catch (e: Exception) {
                Log.e(tag, "Failed to save scan session", e)
            }
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
    private fun getDisplayName(device: BluetoothDevice): String {
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

        // 4. Fallback to generic label
        return "Discovered Device (${device.address.takeLast(5)})"
    }
}
