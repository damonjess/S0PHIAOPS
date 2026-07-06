package com.sophia.ops.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log

class BluetoothGattExplorer(private val context: Context) {

    private val tag = "BluetoothGattExplorer"

    @SuppressLint("MissingPermission")
    fun explore(device: BluetoothDevice, onResult: (String) -> Unit) {
        Log.i(tag, "Starting GATT exploration for ${device.address}")
        onResult("Connecting to GATT...")

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(tag, "Connected to GATT server. Discovering services...")
                        onResult("Connected. Discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(tag, "Disconnected from GATT server.")
                        onResult("Disconnected.")
                        gatt.close()
                    }
                } else {
                    Log.e(tag, "GATT error: $status")
                    onResult("GATT Error: $status")
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val report = StringBuilder()
                    report.append("GATT Services for ${device.address}:\n")
                    
                    gatt.services.forEach { service ->
                        val serviceName = resolveServiceName(service.uuid.toString())
                        report.append("\n[S] $serviceName\n")
                        report.append("    UUID: ${service.uuid}\n")
                        
                        service.characteristics.forEach { char ->
                            val charName = resolveCharName(char.uuid.toString())
                            val props = getPropertiesString(char.properties)
                            report.append("    ├─[C] $charName\n")
                            report.append("    │  UUID: ${char.uuid}\n")
                            report.append("    │  Props: $props\n")
                        }
                    }
                    
                    val finalReport = report.toString()
                    Log.i(tag, "Exploration complete:\n$finalReport")
                    onResult(finalReport)
                    gatt.disconnect()
                } else {
                    Log.e(tag, "Service discovery failed: $status")
                    onResult("Service discovery failed.")
                    gatt.disconnect()
                }
            }
        }

        device.connectGatt(context, false, callback)
    }

    private fun resolveServiceName(uuid: String): String {
        return when {
            uuid.startsWith("00001800") -> "Generic Access"
            uuid.startsWith("00001801") -> "Generic Attribute"
            uuid.startsWith("0000180a") -> "Device Information"
            uuid.startsWith("0000180f") -> "Battery Service"
            uuid.startsWith("0000180d") -> "Heart Rate"
            uuid.startsWith("00001812") -> "Human Interface Device"
            else -> "Unknown Service"
        }
    }

    private fun resolveCharName(uuid: String): String {
        return when {
            uuid.startsWith("00002a00") -> "Device Name"
            uuid.startsWith("00002a01") -> "Appearance"
            uuid.startsWith("00002a24") -> "Model Number String"
            uuid.startsWith("00002a25") -> "Serial Number String"
            uuid.startsWith("00002a26") -> "Firmware Revision String"
            uuid.startsWith("00002a27") -> "Hardware Revision String"
            uuid.startsWith("00002a28") -> "Software Revision String"
            uuid.startsWith("00002a29") -> "Manufacturer Name String"
            uuid.startsWith("00002a37") -> "Heart Rate Measurement"
            uuid.startsWith("00002a19") -> "Battery Level"
            else -> "Characteristic"
        }
    }

    private fun getPropertiesString(props: Int): String {
        val list = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) list.add("READ")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) list.add("WRITE")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) list.add("NOTIFY")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) list.add("INDICATE")
        if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) list.add("BROADCAST")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) list.add("WRITE_NR")
        return if (list.isEmpty()) "NONE" else list.joinToString("|")
    }
}
