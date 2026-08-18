package com.sophia.ops.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.sophia.ops.MainActivity
import com.sophia.ops.bluetooth.BluetoothScanner
import com.sophia.ops.bluetooth.BluetoothRiskEngine
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.SignalPoint
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.wifi.RiskEngine
import com.sophia.ops.wifi.WifiScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Optional background collection. It deliberately yields whenever a user starts
 * a live scan, because concurrent Android Wi-Fi/Bluetooth scans can be rate
 * limited or cancel each other at the platform level.
 */
class ScanForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    private lateinit var wifiScanner: WifiScanner
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var db: SophiaDatabase

    @Volatile
    private var backgroundPauseUntilMs = 0L

    companion object {
        private const val CHANNEL_ID = "ScanServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "ScanForegroundService"
        private const val BACKGROUND_SCAN_INTERVAL_MS = 30_000L
        private const val MANUAL_SCAN_PRIORITY_WINDOW_MS = 22_000L
        private const val PAUSE_CHECK_INTERVAL_MS = 1_000L

        @Volatile
        private var activeInstance: ScanForegroundService? = null

        fun startService(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java)
            context.stopService(intent)
        }

        /**
         * Temporarily stops in-flight background collection so a user-initiated
         * scan has one uncontended attempt. Returns false when no background
         * service is active.
         */
        fun yieldToManualScan(): Boolean {
            val service = activeInstance ?: return false
            service.pauseForManualScan()
            return true
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        wifiScanner = WifiScanner(this)
        bluetoothScanner = BluetoothScanner(this)
        db = SophiaDatabase.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Scanning for devices...")
        startForeground(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        startScanning()
        return START_STICKY
    }

    private fun pauseForManualScan() {
        backgroundPauseUntilMs = System.currentTimeMillis() + MANUAL_SCAN_PRIORITY_WINDOW_MS
        wifiScanner.cancelScan()
        bluetoothScanner.cancelDiscovery()
        updateNotification("Manual scan has priority. Background collection will resume shortly.")
        Log.i(TAG, "Background collection yielded to a manual scan")
    }

    private fun isBackgroundPaused(): Boolean = System.currentTimeMillis() < backgroundPauseUntilMs

    private fun startScanning() {
        if (scanJob?.isActive == true) return
        scanJob = serviceScope.launch {
            while (isActive) {
                if (isBackgroundPaused()) {
                    delay(PAUSE_CHECK_INTERVAL_MS)
                    continue
                }
                Log.d(TAG, "Background scan triggered")
                performWifiScan()
                performBluetoothScan()
                delay(BACKGROUND_SCAN_INTERVAL_MS)
            }
        }
    }

    private fun performWifiScan() {
        if (isBackgroundPaused()) return
        wifiScanner.startScan(
            onResults = { results ->
                val updatedList = results.map {
                    val risk = RiskEngine.calculate(it.capabilities, it.level)
                    WifiNetwork(
                        ssid = @Suppress("DEPRECATION") it.SSID,
                        bssid = it.BSSID,
                        signal = it.level,
                        security = it.capabilities,
                        riskScore = risk,
                        timestamp = System.currentTimeMillis(),
                    )
                }
                serviceScope.launch {
                    db.wifiDao().insertAll(updatedList)
                    updateNotification("Detected ${updatedList.size} Wi-Fi networks")
                }
            },
            onFailure = { reason ->
                if (!isBackgroundPaused()) updateNotification(reason)
            },
        )
    }

    private fun performBluetoothScan() {
        if (isBackgroundPaused()) return
        bluetoothScanner.startDiscovery(
            onDeviceFound = { device, rssi ->
                serviceScope.launch {
                    val name = try {
                        if (ActivityCompat.checkSelfPermission(this@ScanForegroundService, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            device.name
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                    val existing = db.bluetoothDao().getDeviceByAddress(device.address)
                    val timesSeen = (existing?.timesSeen ?: 0) + 1
                    val risk = BluetoothRiskEngine.calculate(
                        name = name,
                        rssi = rssi,
                        timesSeen = timesSeen,
                    )
                    val now = System.currentTimeMillis()
                    if (existing?.ignored == true) return@launch

                    val newHistory = (existing?.signalHistory ?: emptyList()) + SignalPoint(rssi, now)
                    val trimmedHistory = newHistory.takeLast(10)
                    if (existing == null) {
                        db.bluetoothDao().insert(
                            BluetoothDeviceEntity(
                                name = name,
                                address = device.address,
                                deviceType = device.type,
                                firstSeen = now,
                                lastSeen = now,
                                riskScore = risk,
                                rssi = rssi,
                                timesSeen = 1,
                                signalHistory = trimmedHistory,
                            ),
                        )
                    } else {
                        db.bluetoothDao().updateDevice(
                            existing.copy(
                                lastSeen = now,
                                riskScore = risk,
                                rssi = rssi,
                                timesSeen = timesSeen,
                                signalHistory = trimmedHistory,
                            ),
                        )
                    }
                }
            },
            onDiscoveryFinished = { Log.d(TAG, "Background Bluetooth scan finished") },
            onFailure = { reason ->
                if (!isBackgroundPaused()) Log.w(TAG, "Background Bluetooth scan issue: $reason")
            },
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Scan Service Channel",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("S0PHIA OPS Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = createNotification(content)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        wifiScanner.cancelScan()
        bluetoothScanner.cancelDiscovery()
        if (activeInstance === this) activeInstance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
