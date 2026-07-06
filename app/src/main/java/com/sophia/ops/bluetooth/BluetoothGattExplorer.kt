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
    private val onComplete: (List<String>) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    private val tag = "BluetoothGattExplorer"
    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isConnecting = false
    private val retryCount = AtomicInteger(0)
    private val maxRetries = 2

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(tag, "Connection state: $status -> $newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                retryCount.set(0)
                onProgress("Connected → Discovering services...")
                gatt.discoverServices()
            } else {
                if (retryCount.getAndIncrement() < maxRetries) {
                    onProgress("Retrying connection...")
                    scope.launch { delay(1200); reconnect(gatt.device) }
                } else {
                    onError("Connection failed (code $status). Device may not support GATT.")
                    close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onProgress("Reading device information...")
                readCommonCharacteristics(gatt)
            } else {
                onError("Service discovery failed")
                close()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val value = characteristic.value?.let { String(it, Charsets.UTF_8).trim() } ?: "N/A"
                val name = getCharacteristicName(characteristic.uuid.toString())
                onProgress("✓ $name: $value")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(device: BluetoothDevice) {
        if (isConnecting) return
        isConnecting = true
        onProgress("Connecting to ${device.address}...")

        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun reconnect(device: BluetoothDevice) {
        close()
        start(device)
    }

    @SuppressLint("MissingPermission")
    private fun readCommonCharacteristics(gatt: BluetoothGatt) {
        val report = mutableListOf<String>()

        gatt.services.forEach { service ->
            val serviceName = getServiceFriendlyName(service.uuid.toString())
            report.add("Service: $serviceName")

            service.characteristics.forEach { char ->
                if ((char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    @Suppress("DEPRECATION")
                    gatt.readCharacteristic(char)
                }
            }
        }

        onComplete(report)
        close()
    }

    private fun getServiceFriendlyName(uuid: String): String {
        val lowerUuid = uuid.lowercase()
        return when {
            lowerUuid.startsWith("00001800") -> "Generic Access"
            lowerUuid.startsWith("00001801") -> "Generic Attribute"
            lowerUuid.startsWith("0000180a") -> "Device Information"
            lowerUuid.startsWith("0000180f") -> "Battery Service"
            lowerUuid.startsWith("0000180d") -> "Heart Rate Service"
            lowerUuid.startsWith("0000fe59") -> "Google Fast Pair"
            lowerUuid.startsWith("00001816") -> "Cycling Power"
            lowerUuid.startsWith("14839ac4") -> "Custom Vendor Service"
            else -> "Unknown Service (${lowerUuid.take(8)})"
        }
    }

    private fun getCharacteristicName(uuid: String): String {
        val lower = uuid.lowercase()
        return when {
            lower.startsWith("00002a00") -> "Device Name"
            lower.startsWith("00002a01") -> "Appearance"
            lower.startsWith("00002a19") -> "Battery Level"
            lower.startsWith("00002a24") -> "Model Number"
            lower.startsWith("00002a25") -> "Serial Number"
            lower.startsWith("00002a26") -> "Firmware Revision"
            lower.startsWith("00002a29") -> "Manufacturer Name"
            else -> "Characteristic"
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        isConnecting = false
        gatt?.close()
        gatt = null
    }
}
