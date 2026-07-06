package com.sophia.ops.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

class BluetoothGattExplorer(
    private val context: Context,
    private val onProgress: (String) -> Unit = {},
    private val onComplete: (List<String>, Map<Int, String>) -> Unit = { _, _ -> },
    private val onError: (Int, String) -> Unit = { _, _ -> }
) {

    private val tag = "BluetoothGattExplorer"
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(tag, "onConnectionStateChange: status=$status, newState=$newState")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        onProgress("Connected → Discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        onProgress("Disconnected")
                        close()
                    }
                }
                147, BluetoothGatt.GATT_FAILURE, 133 -> { // Common failure codes
                    onError(status, "Connection failed (Error $status). Device may be out of range or not connectable.")
                    close()
                }
                else -> {
                    onError(status, "Unknown GATT error: $status")
                    close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onProgress("Services discovered. Reading characteristics...")
                val services = gatt.services
                val serviceList = mutableListOf<String>()
                val characteristicData = mutableMapOf<Int, String>()

                services.forEach { service ->
                    val serviceName = service.uuid.toString().take(8)
                    serviceList.add("Service: $serviceName (${service.characteristics.size} chars)")
                    
                    service.characteristics.forEach { char ->
                        try {
                            gatt.readCharacteristic(char)
                            // Note: Real reading needs proper permission & queueing
                        } catch (e: Exception) {}
                    }
                }

                onComplete(serviceList, characteristicData)
                close()
            } else {
                onError(status, "Service discovery failed: $status")
                close()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value?.let { String(it) } ?: "Empty"
                Log.d(tag, "Read: ${characteristic.uuid} → $value")
            }
        }
    }

    fun connectAndExplore(device: BluetoothDevice, timeoutMs: Long = 15000) {
        if (!hasConnectPermission()) {
            onError(-1, "BLUETOOTH_CONNECT permission missing")
            return
        }

        serviceScope.launch {
            try {
                onProgress("Connecting to ${device.address}...")
                
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

                // Safety timeout
                delay(timeoutMs)
                if (bluetoothGatt != null) {
                    onError(147, "Connection timeout")
                    close()
                }
            } catch (e: Exception) {
                Log.e(tag, "GATT error", e)
                onError(-1, e.localizedMessage ?: "Unknown error")
                close()
            }
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun close() {
        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing GATT", e)
        }
    }
}
