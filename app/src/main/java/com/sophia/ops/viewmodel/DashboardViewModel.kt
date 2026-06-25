package com.sophia.ops.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.bluetooth.BluetoothScanner
import com.sophia.ops.bluetooth.BluetoothRiskEngine
import com.sophia.ops.wifi.RiskEngine
import com.sophia.ops.wifi.WifiScanner
import android.util.Log
import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class DashboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val tag = "DashboardViewModel"
    private val scanner = WifiScanner(getApplication())
    private val bluetoothScanner = BluetoothScanner(getApplication())
    private var isScanning = false
    private var autoRefreshJob: Job? = null
    
    private var lastScanRequestTime = 0L
    private val minScanInterval = 8000L // 8 seconds

    val networks = mutableStateListOf<WifiNetwork>()
    val bluetoothDevices = mutableStateListOf<BluetoothDeviceEntity>()
    
    val totalThreatScore: Int
        get() = networks.sumOf { it.riskScore } + bluetoothDevices.sumOf { it.riskScore }
    
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
        
        bluetoothDevices.clear()
        bluetoothScanner.startDiscovery { device ->
            @SuppressLint("MissingPermission")
            val name = device.name ?: ""
            val risk = BluetoothRiskEngine.calculate(name)
            
            val entity = BluetoothDeviceEntity(
                name = name,
                address = device.address,
                deviceType = 0, // Simplified to avoid extra permission check in VM
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                riskScore = risk
            )
            
            // Avoid duplicates in the live list
            if (bluetoothDevices.none { it.address == entity.address }) {
                bluetoothDevices.add(entity)
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
}
