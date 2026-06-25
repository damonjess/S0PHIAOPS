package com.sophia.ops.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BluetoothScanner(
    private val context: Context
) {
    private val tag = "BluetoothScanner"
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    @SuppressLint("MissingPermission")
    fun startDiscovery(onDeviceFound: (BluetoothDevice) -> Unit) {
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

                        Log.d("BT", "Found: ${device?.name} [${device?.address}]")
                        device?.let {
                            onDeviceFound(it)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.i(tag, "Discovery finished")
                        try {
                            context?.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Already unregistered
                        }
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
