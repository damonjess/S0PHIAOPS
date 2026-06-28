package com.sophia.ops.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BluetoothScanner(
    private val context: Context
) {
    private val tag = "BluetoothScanner"
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun startDiscovery(
        onDeviceFound: (BluetoothDevice, Int) -> Unit,
        onDiscoveryFinished: () -> Unit = {}
    ) {
        if (adapter == null) {
            Log.e(tag, "BluetoothAdapter is null")
            return
        }

        if (!adapter.isEnabled) {
            Log.e(tag, "Bluetooth is disabled. Cannot start discovery.")
            return
        }

        // Final permission check before interacting with adapter to avoid crashes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(tag, "Cannot start discovery: BLUETOOTH_SCAN permission not granted")
                return
            }
        }

        // 1. BLE Scan (Maximum Power Mode)
        val leScanner = adapter.bluetoothLeScanner
        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d("BT", "LE Found: ${result.device.address} [${result.rssi}]")
                onDeviceFound(result.device, result.rssi)
            }
        }

        if (leScanner != null) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            
            try {
                leScanner.startScan(null, settings, leCallback)
                Log.i(tag, "LE Scan started in LOW_LATENCY (Max Power) mode")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start LE scan", e)
            }
        }

        // 2. Classic Discovery
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("BT", "Receiver triggered: ${intent?.action}")
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        
                        val rssi: Int = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                        Log.d("BT", "Found: ${device?.name} [${device?.address}] RSSI: $rssi")
                        device?.let {
                            onDeviceFound(it, rssi)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.i(tag, "Discovery finished")
                        try {
                            leScanner?.stopScan(leCallback)
                            context?.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Already unregistered
                        }
                        onDiscoveryFinished()
                    }
                }
            }
        }

        // Safety timeout to stop LE scan if discovery finished broadcast is missed
        handler.postDelayed({
            try {
                leScanner?.stopScan(leCallback)
            } catch (e: Exception) {}
        }, 15000)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to register receiver", e)
        }

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            val started = adapter.startDiscovery()
            Log.d("BT", "Discovery started: $started")

            if (!started) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException during discovery start: ${e.message}")
            try {
                context.unregisterReceiver(receiver)
            } catch (ex: Exception) { }
        }
    }
}
