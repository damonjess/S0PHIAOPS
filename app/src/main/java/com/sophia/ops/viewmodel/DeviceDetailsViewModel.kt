package com.sophia.ops.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.room.Room

class DeviceDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        SophiaDatabase::class.java, "sophia-db"
    ).fallbackToDestructiveMigration().build()

    private val bluetoothDao = db.bluetoothDao()

    fun getDevice(address: String): Flow<BluetoothDeviceEntity?> = flow {
        // For simplicity, we just fetch it once. In a real app, this might be a Flow from Room.
        emit(bluetoothDao.getDeviceByAddress(address))
    }
}
