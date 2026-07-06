package com.sophia.ops.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
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
    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnecting = false

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(tag, "onConnectionStateChange: status=$status, newState=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                onProgress("Connected → Discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onProgress("Disconnected")
                close()
            } else {
                val errorMsg = when (status) {
                    147 -> "Connection timeout"
                    133 -> "GATT error - device refused connection"
                    else -> "GATT failure (code $status)"
                }
                onError(status, errorMsg)
                close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onProgress("Services discovered. Reading device info...")
                
                val report = mutableListOf<String>()
                gatt.services.forEach { service ->
                    val serviceUuid = service.uuid.toString().lowercase()
                    val serviceName = getServiceFriendlyName(serviceUuid)
                    report.add("• $serviceName (${serviceUuid.take(8)})")
                    
                    service.characteristics.forEach { char ->
                        val charUuid = char.uuid.toString().lowercase()
                        val charName = getCharacteristicName(charUuid)
                        val props = getPropertiesString(char.properties)
                        report.add("  - $charName (${charUuid.take(8)}) [$props]")
                    }
                }

                // To keep it simple and avoid async queue complexity, we'll complete here
                // listing all found services and characteristics.
                onComplete(report, emptyMap())
                close()
            } else {
                onError(status, "Service discovery failed")
                close()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value?.let { String(it, Charsets.UTF_8).trim() } ?: "N/A"
                val name = getCharacteristicName(characteristic.uuid.toString().lowercase())
                onProgress("$name: $value")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectAndExplore(device: BluetoothDevice, timeoutMs: Long = 12000) {
        if (isConnecting) return
        isConnecting = true

        onProgress("Connecting to ${device.address}...")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        
        scope.launch {
            delay(timeoutMs)
            if (isConnecting) {
                onError(147, "Connection timeout")
                close()
            }
        }
    }

    private fun getServiceFriendlyName(uuid: String): String = when {
        uuid.startsWith("00001800") -> "Generic Access"
        uuid.startsWith("00001801") -> "Generic Attribute"
        uuid.startsWith("0000180a") -> "Device Information"
        uuid.startsWith("0000180f") -> "Battery Service"
        uuid.startsWith("0000180d") -> "Heart Rate"
        uuid.startsWith("00001816") -> "Cycling Power"
        uuid.startsWith("0000fe59") -> "Google Fast Pair"
        else -> "Unknown Service"
    }

    private fun getCharacteristicName(uuid: String): String = when {
        uuid.startsWith("00002a00") -> "Device Name"
        uuid.startsWith("00002a01") -> "Appearance"
        uuid.startsWith("00002a19") -> "Battery Level"
        uuid.startsWith("00002a24") -> "Model Number"
        uuid.startsWith("00002a25") -> "Serial Number"
        uuid.startsWith("00002a26") -> "Firmware Revision"
        uuid.startsWith("00002a27") -> "Hardware Revision"
        uuid.startsWith("00002a29") -> "Manufacturer Name"
        else -> "Characteristic"
    }
    
    private fun getPropertiesString(props: Int): String {
        val list = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) list.add("R")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) list.add("W")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) list.add("N")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) list.add("I")
        return list.joinToString(",")
    }

    fun close() {
        isConnecting = false
        try {
            gatt?.close()
        } catch (e: Exception) {}
        gatt = null
    }
}
