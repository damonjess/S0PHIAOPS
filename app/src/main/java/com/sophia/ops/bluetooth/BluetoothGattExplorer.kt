package com.sophia.ops.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

class BluetoothGattExplorer(
    private val context: Context,
    private val onProgress: (String) -> Unit = {},
    private val onComplete: (List<String>) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    private val tag = "BluetoothGattExplorer"
    private var gatt: BluetoothGatt? = null
    private var isConnecting = false

    private val callback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(tag, "Connection state: $status -> $newState")

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnecting = false
                onProgress("Connected → Discovering services...")
                gatt.discoverServices()
            } else {
                onError("Connection failed (code $status)")
                close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onProgress("Services discovered.")

                val services = gatt.services.map { service ->
                    val uuid = service.uuid.toString().lowercase()
                    val friendly = getFriendlyName(uuid)
                    "• $friendly (${uuid.take(8)})"
                }

                onComplete(services)
                close()
            } else {
                onError("Service discovery failed")
                close()
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

    private fun getFriendlyName(uuid: String): String = when {
        uuid.startsWith("00001800") -> "Generic Access Profile"
        uuid.startsWith("00001801") -> "Generic Attribute Profile"
        uuid.startsWith("0000180a") -> "Device Information"
        uuid.startsWith("0000180f") -> "Battery Service"
        uuid.startsWith("0000180d") -> "Heart Rate Service"
        uuid.startsWith("0000fe59") -> "Google Fast Pair"
        else -> "Unknown Service"
    }

    @SuppressLint("MissingPermission")
    fun close() {
        isConnecting = false
        try {
            gatt?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing GATT", e)
        }
        gatt = null
    }
}
