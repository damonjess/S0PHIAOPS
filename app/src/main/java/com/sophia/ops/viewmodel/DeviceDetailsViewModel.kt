package com.sophia.ops.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.model.DeviceType
import com.sophia.ops.model.NetworkDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.room.Room

class DeviceDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        SophiaDatabase::class.java, "sophia-db"
    ).fallbackToDestructiveMigration().build()

    private val bluetoothDao = db.bluetoothDao()
    private val wifiDao = db.wifiDao()

    fun getBluetoothDevice(address: String) = bluetoothDao.getDeviceByAddressFlow(address)
    fun getWifiNetwork(bssid: String) = wifiDao.getNetworkByBssidFlow(bssid)

    fun toggleFavourite(address: String, currentState: Boolean) {
        viewModelScope.launch {
            bluetoothDao.updateFavourite(address, !currentState)
        }
    }

    fun updateNickname(address: String, nickname: String?) {
        viewModelScope.launch {
            bluetoothDao.updateNickname(address, nickname)
        }
    }

    fun updateNotes(address: String, notes: String?) {
        viewModelScope.launch {
            bluetoothDao.updateNotes(address, notes)
        }
    }
}
