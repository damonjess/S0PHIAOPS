package com.sophia.ops.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.OuiLookup
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
            
            val displayName = when {
                !entity.nickname.isNullOrBlank() -> entity.nickname
                !entity.name.isNullOrBlank() && 
                    !entity.name.contains("Unknown", ignoreCase = true) && 
                    !entity.name.startsWith("Discovered") -> entity.name
                vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)" -> "$vendor Device"
                else -> "Unknown Bluetooth Device"
            }

            val distFactor = ((entity.rssi.toFloat() + 105f) / 125f).coerceIn(0.15f, 0.9f)
            val distancePercent = (1f - distFactor) * 100f

            NetworkDevice(
                name = displayName,
                macAddress = entity.address,
                angle = (entity.address.hashCode().toFloat().let { if (it < 0) -it else it } % 360f),
                distancePercent = distancePercent,
                dBm = entity.rssi,
                isBluetooth = true
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

            val distFactor = ((network.signal.toFloat() + 105f) / 125f).coerceIn(0.15f, 0.9f)
            val distancePercent = (1f - distFactor) * 100f

            NetworkDevice(
                name = displayName,
                macAddress = network.bssid,
                angle = (network.bssid.hashCode().toFloat().let { if (it < 0) -it else it } % 360f),
                distancePercent = distancePercent,
                dBm = network.signal,
                isBluetooth = false
            )
        }

        (btList + wifiList).sortedByDescending { it.dBm }
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
        if (device.isBluetooth) {
            viewModelScope.launch {
                val entity = bluetoothDao.getDeviceByAddress(device.macAddress)
                entity?.let {
                    bluetoothDao.updateFavourite(it.address, !it.favourite)
                }
            }
        }
    }

    fun updateNickname(device: NetworkDevice, nickname: String?) {
        if (device.isBluetooth) {
            viewModelScope.launch {
                bluetoothDao.updateNickname(device.macAddress, nickname)
            }
        }
    }

    fun toggleIgnored(device: NetworkDevice) {
        if (device.isBluetooth) {
            viewModelScope.launch {
                bluetoothDao.updateIgnored(device.macAddress, true)
            }
        }
    }
}
