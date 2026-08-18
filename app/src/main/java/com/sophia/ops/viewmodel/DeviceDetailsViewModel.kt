package com.sophia.ops.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sophia.ops.bluetooth.GattExplorationState
import com.sophia.ops.bluetooth.GattExplorer
import com.sophia.ops.data.DeviceDisposition
import com.sophia.ops.data.DeviceProfileComparison
import com.sophia.ops.data.DeviceProfileStore
import com.sophia.ops.data.InvestigationStore
import com.sophia.ops.data.LocalDeviceProfile
import com.sophia.ops.data.db.SophiaDatabase
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork
import kotlinx.coroutines.launch

class DeviceDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SophiaDatabase.getInstance(application)
    private val bluetoothDao = db.bluetoothDao()
    private val wifiDao = db.wifiDao()
    private val investigationStore = InvestigationStore(application)
    private val profileStore = DeviceProfileStore(application)
    private val gattExplorer = GattExplorer(application)

    var reviewRevision by mutableIntStateOf(0)
        private set
    var profileRevision by mutableIntStateOf(0)
        private set
    var gattState by mutableStateOf<GattExplorationState>(GattExplorationState.Idle)
        private set

    fun getBluetoothDevice(address: String) = bluetoothDao.getDeviceByAddressFlow(address)
    fun getWifiNetwork(bssid: String) = wifiDao.getNetworkByBssidFlow(bssid)

    fun toggleFavourite(address: String, currentState: Boolean) {
        viewModelScope.launch { bluetoothDao.updateFavourite(address, !currentState) }
    }

    fun toggleIgnored(address: String, currentState: Boolean) {
        viewModelScope.launch { bluetoothDao.updateIgnored(address, !currentState) }
    }

    fun updateNickname(address: String, nickname: String?) {
        viewModelScope.launch { bluetoothDao.updateNickname(address, nickname) }
    }

    fun updateNotes(address: String, notes: String?) {
        viewModelScope.launch { bluetoothDao.updateNotes(address, notes) }
    }

    fun deviceDisposition(address: String): DeviceDisposition {
        reviewRevision
        return investigationStore.loadDispositions()[address] ?: DeviceDisposition.UNREVIEWED
    }

    fun setDeviceDisposition(address: String, disposition: DeviceDisposition) {
        investigationStore.saveDisposition(address, disposition)
        reviewRevision += 1
    }

    fun localProfile(identifier: String): LocalDeviceProfile? {
        profileRevision
        return profileStore.load(identifier)
    }

    fun bluetoothProfileComparison(device: BluetoothDeviceEntity): DeviceProfileComparison? {
        profileRevision
        val completion = gattState as? GattExplorationState.Complete
        return profileStore.compareBluetooth(
            device = device,
            disposition = deviceDisposition(device.address),
            discoveredServices = completion?.services,
            readValues = completion?.takeIf { it.valuesInspected }?.values,
        )
    }

    fun wifiProfileComparison(network: WifiNetwork): DeviceProfileComparison? {
        profileRevision
        return profileStore.compareWifi(network, deviceDisposition(network.bssid))
    }

    /** Saves a baseline only from a successful, user-initiated read-only GATT result. */
    fun saveBluetoothProfile(device: BluetoothDeviceEntity): Boolean {
        val completion = gattState as? GattExplorationState.Complete ?: return false
        profileStore.saveBluetooth(
            device = device,
            disposition = deviceDisposition(device.address),
            services = completion.services,
            values = if (completion.valuesInspected) completion.values else emptyList(),
        )
        profileRevision += 1
        return true
    }

    fun saveWifiProfile(network: WifiNetwork) {
        profileStore.saveWifi(network, deviceDisposition(network.bssid))
        profileRevision += 1
    }

    fun startGattDiscovery(address: String) {
        gattState = GattExplorationState.Connecting(1, 3)
        gattExplorer.discover(address) { state -> gattState = state }
    }

    fun retryGattDiscovery() {
        gattState = GattExplorationState.Connecting(1, 3)
        gattExplorer.retry { state -> gattState = state }
    }

    fun inspectGattStandardValues() {
        gattExplorer.readStandardValues()
    }

    fun clearGattResult() {
        gattExplorer.close()
        gattState = GattExplorationState.Idle
    }

    override fun onCleared() {
        gattExplorer.close()
        super.onCleared()
    }
}
