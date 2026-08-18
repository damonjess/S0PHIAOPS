package com.sophia.ops.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothScanner(
    private val context: Context,
) {
    private val tag = "BluetoothScanner"
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeCancellation: (() -> Unit)? = null

    fun cancelDiscovery() {
        activeCancellation?.invoke()
    }

    /**
     * Runs BLE and classic Bluetooth discovery in parallel. Every exit path
     * invokes [onDiscoveryFinished] so the UI never remains in a stuck
     * “Scanning…” state when Bluetooth is unavailable or permission is denied.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(
        onDeviceFound: (BluetoothDevice, Int) -> Unit,
        onDiscoveryFinished: () -> Unit = {},
        onFailure: (String) -> Unit = {},
    ) {
        val preflightError = preflightError()
        if (preflightError != null) {
            Log.w(tag, preflightError)
            onFailure(preflightError)
            onDiscoveryFinished()
            return
        }

        val bluetoothAdapter = adapter ?: run {
            val message = "Bluetooth hardware is not available on this device."
            onFailure(message)
            onDiscoveryFinished()
            return
        }
        val completed = AtomicBoolean(false)
        val leScanner = try {
            bluetoothAdapter.bluetoothLeScanner
        } catch (error: SecurityException) {
            Log.e(tag, "Unable to access Bluetooth LE scanner", error)
            null
        }

        lateinit var receiver: BroadcastReceiver
        lateinit var leCallback: ScanCallback
        lateinit var timeout: Runnable

        fun finishDiscovery() {
            if (!completed.compareAndSet(false, true)) return
            activeCancellation = null
            handler.removeCallbacks(timeout)
            try {
                leScanner?.stopScan(leCallback)
            } catch (_: Exception) {
                // The scanner may already be stopped by the system.
            }
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // The receiver may already be unregistered.
            } catch (error: Exception) {
                Log.w(tag, "Unable to unregister Bluetooth receiver", error)
            }
            onDiscoveryFinished()
        }

        leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    onDeviceFound(result.device, result.rssi)
                } catch (error: SecurityException) {
                    Log.w(tag, "Unable to read a Bluetooth LE result", error)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val message = "Bluetooth LE scan failed (error $errorCode). Toggle Bluetooth and try again."
                Log.w(tag, message)
                onFailure(message)
                // Classic discovery may still return results, so do not end it early.
            }
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (device != null && rssi != Short.MIN_VALUE.toInt()) {
                            onDeviceFound(device, rssi)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.i(tag, "Classic Bluetooth discovery finished")
                        finishDiscovery()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (error: Exception) {
            val message = "Bluetooth receiver could not be registered: ${error.localizedMessage ?: "unknown error"}"
            Log.e(tag, message, error)
            onFailure(message)
            onDiscoveryFinished()
            return
        }

        timeout = Runnable {
            try {
                if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
            } catch (_: Exception) {
                // Continue to final cleanup even when the platform scan state changed.
            }
            finishDiscovery()
        }
        handler.postDelayed(timeout, BLUETOOTH_SCAN_TIMEOUT_MS)
        activeCancellation = {
            try {
                if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
            } catch (_: Exception) {
                // Final cleanup below is still safe if the platform state changed.
            }
            finishDiscovery()
        }

        try {
            if (leScanner == null) {
                onFailure("Bluetooth LE scanning is unavailable on this device; trying classic discovery only.")
            } else {
                leScanner.startScan(
                    null,
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    leCallback,
                )
                Log.i(tag, "Bluetooth LE scan started")
            }

            if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
            val started = bluetoothAdapter.startDiscovery()
            if (!started) {
                val message = "Bluetooth discovery was not started. Toggle Bluetooth and try again."
                Log.w(tag, message)
                onFailure(message)
                // Leave LE active briefly; it can still discover nearby BLE devices.
            } else {
                Log.i(tag, "Classic Bluetooth discovery started")
            }
        } catch (error: SecurityException) {
            val message = "Bluetooth permission was denied while starting discovery."
            Log.e(tag, message, error)
            onFailure(message)
            finishDiscovery()
        } catch (error: Exception) {
            val message = "Bluetooth discovery failed: ${error.localizedMessage ?: "unknown error"}"
            Log.e(tag, message, error)
            onFailure(message)
            finishDiscovery()
        }
    }

    private fun preflightError(): String? {
        val bluetoothAdapter = adapter ?: return "Bluetooth hardware is not available on this device."
        if (!bluetoothAdapter.isEnabled) return "Bluetooth is turned off. Enable Bluetooth and try again."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return "Nearby devices permission is required for Bluetooth scanning."
            }
            if (appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return "Nearby devices permission is required to read Bluetooth devices."
            }
        }
        return null
    }

    private companion object {
        const val BLUETOOTH_SCAN_TIMEOUT_MS = 15_000L
    }
}
