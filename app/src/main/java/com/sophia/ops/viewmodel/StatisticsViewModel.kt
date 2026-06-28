package com.sophia.ops.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.sophia.ops.data.db.SophiaDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SophiaDatabase.getInstance(application)

    private val scanDao = db.scanSessionDao()
    private val wifiDao = db.wifiDao()
    private val bluetoothDao = db.bluetoothDao()

    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun refresh() {
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    val totalScans: StateFlow<Int> = scanDao.getCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val wifiNetworks: StateFlow<Int> = wifiDao.getCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val bluetoothDevices: StateFlow<Int> = bluetoothDao.getCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val historyEntries: StateFlow<Int> = scanDao.getCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastScanTime: StateFlow<String> = scanDao.getLatestSession()
        .map { it?.timestamp?.let { ts -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)) } ?: "Never" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Never")

    val lastScanDate: StateFlow<String> = scanDao.getLatestSession()
        .map { it?.timestamp?.let { ts -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts)) } ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val databaseSize: StateFlow<String> = combine(
        scanDao.getCount(),
        refreshTrigger.onStart { emit(Unit) }
    ) { _, _ ->
        val dbFile = getApplication<Application>().getDatabasePath("sophia-db")
        if (dbFile.exists()) {
            val sizeInBytes = dbFile.length()
            val sizeInMb = sizeInBytes.toDouble() / (1024 * 1024)
            String.format(Locale.getDefault(), "%.1f MB", sizeInMb)
        } else {
            "0.0 MB"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0.0 MB")
}
