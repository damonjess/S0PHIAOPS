package com.sophia.ops.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DevicesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = androidx.room.Room.databaseBuilder(
        application,
        SophiaDatabase::class.java, "sophia-db"
    ).fallbackToDestructiveMigration().build()

    private val bluetoothDao = db.bluetoothDao()

    val devices: StateFlow<List<BluetoothDeviceEntity>> = bluetoothDao.getAll()
        .map { list -> list.filter { !it.ignored } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearDevices() {
        viewModelScope.launch {
            bluetoothDao.deleteAllDevices()
        }
    }

    fun toggleFavourite(device: BluetoothDeviceEntity) {
        viewModelScope.launch {
            bluetoothDao.updateFavourite(device.address, !device.favourite)
        }
    }

    fun updateNickname(device: BluetoothDeviceEntity, nickname: String?) {
        viewModelScope.launch {
            bluetoothDao.updateNickname(device.address, nickname)
        }
    }

    fun toggleIgnored(device: BluetoothDeviceEntity) {
        viewModelScope.launch {
            bluetoothDao.updateIgnored(device.address, !device.ignored)
        }
    }
}
