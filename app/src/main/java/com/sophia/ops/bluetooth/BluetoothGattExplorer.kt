package com.sophia.ops.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class BluetoothGattExplorer(
    private val context: Context,
    private val onProgress: (String) -> Unit = {},
    private val onComplete: (List<String>, Map<Int, String>) -> Unit = { _, _ -> },
    private val onError: (Int, String) -> Unit = { _, _ -> }
) {

    private val tag = "BluetoothGattExplorer"
    private var bluetoothGatt: BluetoothGatt? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isConnecting = false
    private val retryCount = AtomicInteger(0)
    private val maxRetries = 2

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(tag, "onConnectionStateChange: status=$status, newState=$newState")

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            retryCount.set(0)
                            isConnecting = false
                            onProgress("Connected → Discovering services...")
                            gatt.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            onProgress("Disconnected")
                            close()
                        }
                    }
                }
                else -> {
                    val errorMsg = when (status) {
                        147 -> "Connection timeout"
                        133 -> "GATT error - device refused connection"
                        else -> "GATT failure (code $status)"
                    }

                    if (retryCount.get() < maxRetries && status in listOf(147, 133)) {
                        retryCount.incrementAndGet()
                        onProgress("Retrying connection (${retryCount.get()}/$maxRetries)...")
                        serviceScope.launch {
                            delay(1500)
                            reconnect(gatt.device)
                        }
                    } else {
                        onError(status, "$errorMsg. Device may not support GATT connections.")
                        close()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onProgress("Services discovered.")
                val services = gatt.services.map { service ->
                    "Service: ${service.uuid.toString().take(8)} (${service.characteristics.size} chars)"
                }
                onComplete(services, emptyMap())
                close()
            } else {
                onError(status, "Service discovery failed")
                close()
            }
        }
    }

    fun connectAndExplore(device: BluetoothDevice, timeoutMs: Long = 12000) {
        if (!hasConnectPermission()) {
            onError(-1, "Missing BLUETOOTH_CONNECT permission")
            return
        }
        if (isConnecting) return

        isConnecting = true
        retryCount.set(0)
        onProgress("Connecting to ${device.address}...")

        connectGatt(device, timeoutMs)
    }

    private fun connectGatt(device: BluetoothDevice, timeoutMs: Long) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        serviceScope.launch {
            delay(timeoutMs)
            if (isConnecting) {
                onError(147, "Connection timeout")
                close()
            }
        }
    }

    private fun reconnect(device: BluetoothDevice) {
        close()
        connectGatt(device, 10000)
    }

    private fun hasConnectPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun close() {
        isConnecting = false
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {}
        bluetoothGatt = null
    }
}
