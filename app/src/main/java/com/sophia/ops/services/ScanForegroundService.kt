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
import kotlinx.coroutines.*
import kotlin.random.Random

class ScanForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    private lateinit var wifiScanner: WifiScanner
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var db: SophiaDatabase

    companion object {
        private const val CHANNEL_ID = "ScanServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "ScanForegroundService"
        
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
    }

    override fun onCreate() {
        super.onCreate()
        wifiScanner = WifiScanner(this)
        bluetoothScanner = BluetoothScanner(this)
        db = SophiaDatabase.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Scanning for devices...")
        startForeground(NOTIFICATION_ID, notification)

        startScanning()

        return START_STICKY
    }

    private fun startScanning() {
        if (scanJob?.isActive == true) return

        scanJob = serviceScope.launch {
            while (isActive) {
                Log.d(TAG, "Background scan triggered")
                performWifiScan()
                performBluetoothScan()
                delay(30000) // Scan every 30 seconds in background
            }
        }
    }

    private fun performWifiScan() {
        wifiScanner.startScan { results ->
            val updatedList = results.map {
                val risk = RiskEngine.calculate(it.capabilities, it.level)
                WifiNetwork(
                    ssid = it.SSID,
                    bssid = it.BSSID,
                    signal = it.level,
                    security = it.capabilities,
                    riskScore = risk,
                    timestamp = System.currentTimeMillis()
                )
            }
            serviceScope.launch {
                db.wifiDao().insertAll(updatedList)
                updateNotification("Detected ${updatedList.size} Wi-Fi networks")
            }
        }
    }

    private fun performBluetoothScan() {
        bluetoothScanner.startDiscovery(
            onDeviceFound = { device, rssi ->
                serviceScope.launch {
                    val name = try {
                        if (ActivityCompat.checkSelfPermission(this@ScanForegroundService, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            device.name
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                    val risk = BluetoothRiskEngine.calculate(name)
                    val existing = db.bluetoothDao().getDeviceByAddress(device.address)
                    val now = System.currentTimeMillis()

                    if (existing?.ignored == true) return@launch

                    val newHistory = (existing?.signalHistory ?: emptyList()) + SignalPoint(rssi, now)
                    val trimmedHistory = newHistory.takeLast(10)

                    if (existing == null) {
                        val entity = BluetoothDeviceEntity(
                            name = name,
                            address = device.address,
                            deviceType = device.type,
                            firstSeen = now,
                            lastSeen = now,
                            riskScore = risk,
                            rssi = rssi,
                            timesSeen = 1,
                            signalHistory = trimmedHistory
                        )
                        db.bluetoothDao().insert(entity)
                    } else {
                        val updatedEntity = existing.copy(
                            lastSeen = now,
                            rssi = rssi,
                            timesSeen = existing.timesSeen + 1,
                            signalHistory = trimmedHistory
                        )
                        db.bluetoothDao().updateDevice(updatedEntity)
                    }
                }
            },
            onDiscoveryFinished = {
                Log.d(TAG, "Background Bluetooth scan finished")
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Scan Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("S0PHIA OPS Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_search) // Using system icon for now
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
