package com.sophia.ops.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.OuiLookup
import com.sophia.ops.model.DeviceType
import com.sophia.ops.model.NetworkDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SophiaDatabase.getInstance(application)

    private val bluetoothDao = db.bluetoothDao()
    private val wifiDao = db.wifiDao()

    val devices: StateFlow<List<NetworkDevice>> = combine(
        bluetoothDao.getAll(),
        wifiDao.getAll()
    ) { bluetoothDevices, wifiNetworks ->
        val btList = bluetoothDevices.filter { !it.ignored }.map { entity ->
            val vendor = OuiLookup.getVendor(getApplication(), entity.address)
            val rawName = entity.nickname ?: entity.name
            val displayName = when {
                !rawName.isNullOrBlank() && !rawName.startsWith("Discovered Device") && !rawName.contains("Unknown", true) -> rawName
                vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)" -> vendor
                else -> "Unknown Bluetooth Device"
            }

            NetworkDevice(
                id = entity.address,
                name = displayName,
                address = entity.address,
                vendor = vendor,
                type = DeviceType.BLUETOOTH,
                signal = entity.rssi,
                favourite = entity.favourite,
                lastSeen = entity.lastSeen
            )
        }

        val wifiList = wifiNetworks.map { network ->
            val vendor = OuiLookup.getVendor(getApplication(), network.bssid)
            val displayName = if (network.ssid.isBlank() || network.ssid == "<unknown ssid>") {
                if (vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)") {
                    vendor
                } else {
                    "Hidden Network"
                }
            } else {
                network.ssid
            }

            NetworkDevice(
                id = network.bssid,
                name = displayName,
                address = network.bssid,
                vendor = vendor,
                type = DeviceType.WIFI,
                signal = network.signal,
                favourite = false,
                lastSeen = network.timestamp
            )
        }

        (btList + wifiList).sortedByDescending { it.lastSeen }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun clearDevices() {
        viewModelScope.launch {
            bluetoothDao.deleteAllDevices()
            wifiDao.deleteAllNetworks()
        }
    }

    fun toggleFavourite(device: NetworkDevice) {
        if (device.type == DeviceType.BLUETOOTH) {
            viewModelScope.launch {
                bluetoothDao.updateFavourite(device.address, !device.favourite)
            }
        }
    }

    fun updateNickname(device: NetworkDevice, nickname: String?) {
        if (device.type == DeviceType.BLUETOOTH) {
            viewModelScope.launch {
                bluetoothDao.updateNickname(device.address, nickname)
            }
        }
    }

    fun toggleIgnored(device: NetworkDevice) {
        if (device.type == DeviceType.BLUETOOTH) {
            viewModelScope.launch {
                bluetoothDao.updateIgnored(device.address, true)
            }
        }
    }
}
