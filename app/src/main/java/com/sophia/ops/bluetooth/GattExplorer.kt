package com.sophia.ops.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.UUID

private const val BLUETOOTH_BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

data class UuidDefinition(
    val name: String,
    val identifier: String,
    val source: String,
    val category: String,
)

data class GattDescriptorInfo(
    val uuid: String,
    val label: String,
)

data class GattCharacteristicInfo(
    val uuid: String,
    val label: String,
    val properties: List<String>,
    val descriptors: List<GattDescriptorInfo>,
)

data class GattServiceInfo(
    val uuid: String,
    val label: String,
    val primary: Boolean,
    val characteristics: List<GattCharacteristicInfo>,
)

data class GattDiagnostics(
    val attempts: Int,
    val lastStatus: Int? = null,
    val message: String,
)

data class GattReadValue(
    val label: String,
    val uuid: String,
    val value: String,
    val available: Boolean,
)

sealed interface GattExplorationState {
    data object Idle : GattExplorationState
    data class Connecting(val attempt: Int, val maximumAttempts: Int) : GattExplorationState
    data class Retrying(val nextAttempt: Int, val maximumAttempts: Int, val reason: String) : GattExplorationState
    data class Discovering(val attempt: Int) : GattExplorationState
    data class Complete(
        val services: List<GattServiceInfo>,
        val diagnostics: GattDiagnostics,
        val values: List<GattReadValue> = emptyList(),
        /** True only after the user explicitly asked to read the allowlisted values. */
        val valuesInspected: Boolean = false,
    ) : GattExplorationState
    data class ReadingValues(val completed: Int, val total: Int) : GattExplorationState
    data class Error(val message: String, val diagnostics: GattDiagnostics? = null) : GattExplorationState
}

/**
 * Explores the GATT topology of one explicitly selected BLE device. It never
 * pairs, bonds, subscribes, writes, or changes the device. The optional value
 * inspector reads only a small allowlist of standard, read-enabled attributes.
 */
class GattExplorer(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeGatt: BluetoothGatt? = null
    private var currentDevice: BluetoothDevice? = null
    private var stateListener: ((GattExplorationState) -> Unit)? = null
    private var services: List<GattServiceInfo> = emptyList()
    private var lastDiagnostics = GattDiagnostics(0, message = "Not started")
    private var attempt = 0
    private var discoveryFinished = false
    private var timeoutTask: Runnable? = null
    private var readQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private val readValues = mutableListOf<GattReadValue>()

    @SuppressLint("MissingPermission")
    fun discover(address: String, onState: (GattExplorationState) -> Unit) {
        close()
        stateListener = onState
        if (!hasConnectPermission()) {
            emit(GattExplorationState.Error("Bluetooth connection permission is required for GATT discovery."))
            return
        }
        val device = runCatching { android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            emit(GattExplorationState.Error("This device address is no longer available to the Bluetooth adapter."))
            return
        }
        currentDevice = device
        attempt = 0
        services = emptyList()
        discoveryFinished = false
        readValues.clear()
        startConnectionAttempt()
    }

    /** Starts a new connection only after an explicit user action. */
    fun retry(onState: (GattExplorationState) -> Unit) {
        val device = currentDevice
        if (device == null) {
            onState(GattExplorationState.Error("Select the Bluetooth device again before retrying."))
            return
        }
        closeGattOnly()
        stateListener = onState
        attempt = 0
        services = emptyList()
        discoveryFinished = false
        readValues.clear()
        startConnectionAttempt()
    }

    /** Reads only known standard attributes that advertise the GATT Read property. */
    @SuppressLint("MissingPermission")
    fun readStandardValues() {
        val gatt = activeGatt
        if (gatt == null || !discoveryFinished) {
            emit(GattExplorationState.Error("Reconnect and discover services before inspecting standard values.", lastDiagnostics))
            return
        }
        val readable = gatt.services.flatMap { service ->
            service.characteristics.filter { characteristic ->
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 &&
                    standardReadLabel(characteristic.uuid) != null
            }
        }
        if (readable.isEmpty()) {
            emit(GattExplorationState.Complete(services, lastDiagnostics, emptyList(), valuesInspected = true))
            return
        }
        readQueue = ArrayDeque(readable)
        readValues.clear()
        emit(GattExplorationState.ReadingValues(0, readable.size))
        readNext()
    }

    @SuppressLint("MissingPermission")
    private fun startConnectionAttempt() {
        val device = currentDevice ?: return
        closeGattOnly()
        attempt += 1
        lastDiagnostics = GattDiagnostics(attempt, message = "Connection attempt $attempt of $MAX_ATTEMPTS")
        emit(GattExplorationState.Connecting(attempt, MAX_ATTEMPTS))
        val callback = callbackForCurrentSession()
        activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        scheduleTimeout()
    }

    private fun callbackForCurrentSession(): BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt != activeGatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleConnectionFailure(status, "GATT connection failed")
                return
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                cancelTimeout()
                emit(GattExplorationState.Discovering(attempt))
                if (!gatt.discoverServices()) {
                    finishWithError("The device did not start service discovery.")
                } else {
                    scheduleTimeout()
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED && !discoveryFinished) {
                handleConnectionFailure(status, "The device disconnected before service discovery finished")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt != activeGatt) return
            cancelTimeout()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishWithError("Service discovery failed${statusSuffix(status)}.", status)
                return
            }
            services = gatt.services.map(::mapService)
            discoveryFinished = true
            lastDiagnostics = GattDiagnostics(attempt, status, "Connected and discovered ${services.size} service(s).")
            emit(GattExplorationState.Complete(services, lastDiagnostics))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt == activeGatt) handleCharacteristicRead(characteristic, characteristic.value, status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (gatt == activeGatt) handleCharacteristicRead(characteristic, value, status)
        }
    }

    private fun handleConnectionFailure(status: Int, prefix: String) {
        cancelTimeout()
        lastDiagnostics = GattDiagnostics(attempt, status, diagnosticHint(status))
        closeGattOnly()
        if (attempt < MAX_ATTEMPTS && retryableStatus(status)) {
            val nextAttempt = attempt + 1
            emit(GattExplorationState.Retrying(nextAttempt, MAX_ATTEMPTS, diagnosticHint(status)))
            mainHandler.postDelayed({ startConnectionAttempt() }, retryDelayFor(nextAttempt))
        } else {
            emit(GattExplorationState.Error("$prefix${statusSuffix(status)}. ${diagnosticHint(status)}", lastDiagnostics))
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleCharacteristicRead(characteristic: BluetoothGattCharacteristic, bytes: ByteArray?, status: Int) {
        val label = standardReadLabel(characteristic.uuid) ?: UuidCatalog.lookup(context, characteristic.uuid, "Characteristic").name
        readValues += if (status == BluetoothGatt.GATT_SUCCESS) {
            GattReadValue(label, characteristic.uuid.toString(), formatStandardValue(characteristic.uuid, bytes ?: byteArrayOf()), true)
        } else {
            GattReadValue(label, characteristic.uuid.toString(), "Not available${statusSuffix(status)}", false)
        }
        readNext()
    }

    @SuppressLint("MissingPermission")
    private fun readNext() {
        val gatt = activeGatt
        val next: BluetoothGattCharacteristic? = if (readQueue.isEmpty()) null else readQueue.removeFirst()
        if (gatt == null || next == null) {
            emit(GattExplorationState.Complete(services, lastDiagnostics, readValues.toList(), valuesInspected = true))
            return
        }
        emit(GattExplorationState.ReadingValues(readValues.size, readValues.size + readQueue.size + 1))
        if (!gatt.readCharacteristic(next)) {
            val label = standardReadLabel(next.uuid) ?: "Standard characteristic"
            readValues += GattReadValue(label, next.uuid.toString(), "Read request was not accepted by the device", false)
            readNext()
        }
    }

    private fun scheduleTimeout() {
        cancelTimeout()
        timeoutTask = Runnable {
            if (!discoveryFinished && activeGatt != null) {
                finishWithError("GATT discovery timed out. Move closer, wake the device, and ensure it is not already connected to another app.")
            }
        }.also { mainHandler.postDelayed(it, DISCOVERY_TIMEOUT_MS) }
    }

    private fun cancelTimeout() {
        timeoutTask?.let(mainHandler::removeCallbacks)
        timeoutTask = null
    }

    fun close() {
        cancelTimeout()
        closeGattOnly()
        currentDevice = null
        services = emptyList()
        discoveryFinished = false
        readQueue.clear()
        readValues.clear()
        stateListener = null
    }

    @SuppressLint("MissingPermission")
    private fun closeGattOnly() {
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
    }

    private fun finishWithError(message: String, status: Int? = null) {
        cancelTimeout()
        lastDiagnostics = GattDiagnostics(attempt, status, message)
        closeGattOnly()
        emit(GattExplorationState.Error(message, lastDiagnostics))
    }

    private fun emit(state: GattExplorationState) {
        mainHandler.post { stateListener?.invoke(state) }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun mapService(service: BluetoothGattService): GattServiceInfo = GattServiceInfo(
        uuid = service.uuid.toString(),
        label = UuidCatalog.lookup(context, service.uuid, "Service").name,
        primary = service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY,
        characteristics = service.characteristics.map(::mapCharacteristic),
    )

    private fun mapCharacteristic(characteristic: BluetoothGattCharacteristic): GattCharacteristicInfo = GattCharacteristicInfo(
        uuid = characteristic.uuid.toString(),
        label = UuidCatalog.lookup(context, characteristic.uuid, "Characteristic").name,
        properties = propertyLabels(characteristic.properties),
        descriptors = characteristic.descriptors.map(::mapDescriptor),
    )

    private fun mapDescriptor(descriptor: BluetoothGattDescriptor): GattDescriptorInfo = GattDescriptorInfo(
        uuid = descriptor.uuid.toString(),
        label = UuidCatalog.lookup(context, descriptor.uuid, "Descriptor").name,
    )

    private fun propertyLabels(properties: Int): List<String> = buildList {
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("Read")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("Write")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("Write no response")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("Notify")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("Indicate")
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("Broadcast")
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("Signed write")
        if (isEmpty()) add("No standard properties reported")
    }

    private fun standardReadLabel(uuid: UUID): String? = when (standardId(uuid)) {
        "2a00" -> "Device Name"
        "2a01" -> "Appearance"
        "2a19" -> "Battery Level"
        "2a23" -> "System ID"
        "2a24" -> "Model Number"
        "2a25" -> "Serial Number"
        "2a26" -> "Firmware Revision"
        "2a27" -> "Hardware Revision"
        "2a28" -> "Software Revision"
        "2a29" -> "Manufacturer Name"
        "2a50" -> "PnP ID"
        else -> null
    }

    private fun formatStandardValue(uuid: UUID, bytes: ByteArray): String = when (standardId(uuid)) {
        "2a19" -> bytes.firstOrNull()?.let { "${it.toInt() and 0xFF}%" } ?: "No value"
        "2a01" -> bytes.takeIf { it.size >= 2 }?.let { "Appearance code ${((it[1].toInt() and 0xFF) shl 8) or (it[0].toInt() and 0xFF)}" } ?: "No value"
        "2a23", "2a50" -> bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        else -> bytes.toString(StandardCharsets.UTF_8).trim().take(96).ifBlank { "No printable text reported" }
    }

    private fun standardId(uuid: UUID): String? {
        val value = uuid.toString().lowercase()
        return Regex("^0000([0-9a-f]{4})-0000-1000-8000-00805f9b34fb$").find(value)?.groupValues?.get(1)
    }

    private fun retryableStatus(status: Int): Boolean = status == 133 || status == 8 || status == 19 || status == 22

    private fun diagnosticHint(status: Int): String = when (status) {
        133 -> "Android reported a generic connection failure. Move closer, wake the device, close any app already using it, or toggle Bluetooth before trying again."
        8 -> "The connection timed out. Move closer and wake the device before trying again."
        19 -> "The device terminated the connection. It may be busy, asleep, or connected elsewhere."
        22 -> "The device declined the local connection attempt. Try again after a short pause."
        else -> "Check range, Bluetooth permission, and whether another app is connected to the device."
    }

    private fun statusSuffix(status: Int): String = if (status == BluetoothGatt.GATT_SUCCESS) "" else " (status $status)"

    private fun retryDelayFor(nextAttempt: Int): Long = if (nextAttempt == 2) 450L else 1_000L

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val DISCOVERY_TIMEOUT_MS = 12_000L
    }
}

object UuidCatalog {
    @Volatile private var definitions: Map<String, UuidDefinition>? = null

    fun lookup(context: Context, uuid: UUID, fallbackCategory: String): UuidDefinition {
        val key = normalize(uuid.toString())
        return load(context)[key] ?: UuidDefinition(
            name = "Unknown or proprietary $fallbackCategory",
            identifier = "unrecognized",
            source = "device",
            category = fallbackCategory,
        )
    }

    private fun load(context: Context): Map<String, UuidDefinition> {
        definitions?.let { return it }
        return synchronized(this) {
            definitions ?: buildMap {
                addAsset(context, "uuid_catalog/service_uuids.json", "Service", this)
                addAsset(context, "uuid_catalog/characteristic_uuids.json", "Characteristic", this)
                addAsset(context, "uuid_catalog/descriptor_uuids.json", "Descriptor", this)
            }.also { definitions = it }
        }
    }

    private fun addAsset(
        context: Context,
        assetName: String,
        category: String,
        destination: MutableMap<String, UuidDefinition>,
    ) {
        val raw = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val array = JSONArray(raw)
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val uuid = item.optString("uuid")
            if (uuid.isBlank()) continue
            destination[normalize(uuid)] = UuidDefinition(
                name = item.optString("name", "Unknown $category"),
                identifier = item.optString("identifier", ""),
                source = item.optString("source", ""),
                category = category,
            )
        }
    }

    private fun normalize(uuid: String): String {
        val compact = uuid.lowercase()
        return when {
            compact.matches(Regex("^[0-9a-f]{4}$")) -> compact
            compact.matches(Regex("^0000[0-9a-f]{4}${Regex.escape(BLUETOOTH_BASE_SUFFIX)}\$")) -> compact.substring(4, 8)
            else -> compact
        }
    }
}
