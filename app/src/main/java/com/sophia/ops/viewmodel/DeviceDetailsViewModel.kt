package com.sophia.ops.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.room.Room

class DeviceDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        SophiaDatabase::class.java, "sophia-db"
    ).fallbackToDestructiveMigration().build()

    private val bluetoothDao = db.bluetoothDao()

    fun getDevice(address: String): Flow<BluetoothDeviceEntity?> = bluetoothDao.getDeviceByAddressFlow(address)

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

    fun updateName(address: String, name: String) {
        viewModelScope.launch {
            bluetoothDao.updateNameByAddress(address, name)
        }
    }

    fun toggleIgnored(address: String, currentState: Boolean) {
        viewModelScope.launch {
            bluetoothDao.updateIgnored(address, !currentState)
        }
    }

    fun updateNotes(address: String, notes: String?) {
        viewModelScope.launch {
            bluetoothDao.updateNotes(address, notes)
        }
    }
}
