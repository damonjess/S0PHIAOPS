package com.sophia.ops.data

import android.content.Context
import com.sophia.ops.bluetooth.GattReadValue
import com.sophia.ops.bluetooth.GattServiceInfo
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork
import org.json.JSONArray
import org.json.JSONObject

enum class ProfileDeviceType {
    BLUETOOTH,
    WIFI,
}

data class GattServiceSnapshot(
    val uuid: String,
    val label: String,
    val primary: Boolean,
)

data class StandardValueSnapshot(
    val uuid: String,
    val label: String,
    val value: String,
)

data class LocalDeviceProfile(
    val identifier: String,
    val deviceType: ProfileDeviceType,
    val displayName: String,
    val localLabel: String?,
    val notes: String?,
    val disposition: DeviceDisposition,
    val rssi: Int,
    /** Most recent locally observed Bluetooth RSSI samples, capped by the scan store. */
    val signalPattern: List<Int> = emptyList(),
    val riskScore: Int,
    val savedAt: Long,
    val bluetoothServices: List<GattServiceSnapshot> = emptyList(),
    val standardValues: List<StandardValueSnapshot> = emptyList(),
    val wifiSsid: String? = null,
    val wifiSecurity: String? = null,
)

data class ChangedStandardValue(
    val label: String,
    val previous: String,
    val current: String,
)

/**
 * A comparison uses only evidence that was actually collected in the current
 * screen session. In particular, GATT services are never called missing after
 * a failed, skipped, or closed discovery operation.
 */
data class DeviceProfileComparison(
    val profile: LocalDeviceProfile,
    val nameChanged: Pair<String, String>? = null,
    val securityChanged: Pair<String, String>? = null,
    val addedServices: List<GattServiceSnapshot> = emptyList(),
    val removedServices: List<GattServiceSnapshot> = emptyList(),
    val changedValues: List<ChangedStandardValue> = emptyList(),
    val signalDelta: Int = 0,
    val riskDelta: Int = 0,
    val dispositionChanged: Pair<DeviceDisposition, DeviceDisposition>? = null,
    val gattCompared: Boolean = false,
    val valuesCompared: Boolean = false,
) {
    val hasMaterialChanges: Boolean
        get() = nameChanged != null || securityChanged != null || addedServices.isNotEmpty() ||
            removedServices.isNotEmpty() || changedValues.isNotEmpty() ||
            signalDelta != 0 || riskDelta != 0 || dispositionChanged != null
}

/**
 * Holds an explicit local baseline per observed address. SharedPreferences is
 * private to the app; profile data is neither transmitted nor synchronized.
 */
class DeviceProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sophia_device_profiles",
        Context.MODE_PRIVATE,
    )

    fun load(identifier: String): LocalDeviceProfile? {
        val raw = preferences.getString(profileKey(identifier), null) ?: return null
        return runCatching { decodeProfile(JSONObject(raw)) }.getOrNull()
    }

    fun saveBluetooth(
        device: BluetoothDeviceEntity,
        disposition: DeviceDisposition,
        services: List<GattServiceInfo> = emptyList(),
        values: List<GattReadValue> = emptyList(),
    ): LocalDeviceProfile {
        val profile = LocalDeviceProfile(
            identifier = device.address,
            deviceType = ProfileDeviceType.BLUETOOTH,
            displayName = device.name ?: "Unknown Bluetooth Device",
            localLabel = device.nickname,
            notes = device.notes,
            disposition = disposition,
            rssi = device.rssi,
            signalPattern = device.signalHistory.map { it.rssi }.takeLast(MAX_SIGNAL_SAMPLES).ifEmpty { listOf(device.rssi) },
            riskScore = device.riskScore,
            savedAt = System.currentTimeMillis(),
            bluetoothServices = services.map { GattServiceSnapshot(it.uuid, it.label, it.primary) },
            standardValues = values.filter { it.available }.map { StandardValueSnapshot(it.uuid, it.label, it.value) },
        )
        persist(profile)
        return profile
    }

    fun saveWifi(network: WifiNetwork, disposition: DeviceDisposition): LocalDeviceProfile {
        val profile = LocalDeviceProfile(
            identifier = network.bssid,
            deviceType = ProfileDeviceType.WIFI,
            displayName = network.ssid.ifBlank { "Hidden Wi-Fi Network" },
            localLabel = null,
            notes = null,
            disposition = disposition,
            rssi = network.signal,
            signalPattern = listOf(network.signal),
            riskScore = network.riskScore,
            savedAt = System.currentTimeMillis(),
            wifiSsid = network.ssid,
            wifiSecurity = network.security,
        )
        persist(profile)
        return profile
    }

    fun compareBluetooth(
        device: BluetoothDeviceEntity,
        disposition: DeviceDisposition,
        discoveredServices: List<GattServiceInfo>?,
        readValues: List<GattReadValue>?,
    ): DeviceProfileComparison? {
        val profile = load(device.address) ?: return null
        if (profile.deviceType != ProfileDeviceType.BLUETOOTH) return null

        val gattCompared = discoveredServices != null
        val currentServices = discoveredServices.orEmpty().map { GattServiceSnapshot(it.uuid, it.label, it.primary) }
        val savedByUuid = profile.bluetoothServices.associateBy { it.uuid.lowercase() }
        val currentByUuid = currentServices.associateBy { it.uuid.lowercase() }

        val valuesCompared = readValues != null
        val savedValues = profile.standardValues.associateBy { it.uuid.lowercase() }
        val currentValues = readValues.orEmpty().filter { it.available }.associateBy { it.uuid.lowercase() }
        val changedValues = if (valuesCompared) {
            currentValues.mapNotNull { (uuid, current) ->
                val previous = savedValues[uuid] ?: return@mapNotNull null
                if (previous.value == current.value) null else ChangedStandardValue(
                    label = current.label,
                    previous = previous.value,
                    current = current.value,
                )
            }.sortedBy { it.label.lowercase() }
        } else {
            emptyList()
        }

        return DeviceProfileComparison(
            profile = profile,
            nameChanged = profile.displayName.takeIf { it != (device.name ?: "Unknown Bluetooth Device") }
                ?.let { it to (device.name ?: "Unknown Bluetooth Device") },
            addedServices = if (gattCompared) currentServices.filter { it.uuid.lowercase() !in savedByUuid } else emptyList(),
            removedServices = if (gattCompared) profile.bluetoothServices.filter { it.uuid.lowercase() !in currentByUuid } else emptyList(),
            changedValues = changedValues,
            signalDelta = device.rssi - profile.rssi,
            riskDelta = device.riskScore - profile.riskScore,
            dispositionChanged = profile.disposition.takeIf { it != disposition }?.let { it to disposition },
            gattCompared = gattCompared,
            valuesCompared = valuesCompared,
        )
    }

    fun compareWifi(network: WifiNetwork, disposition: DeviceDisposition): DeviceProfileComparison? {
        val profile = load(network.bssid) ?: return null
        if (profile.deviceType != ProfileDeviceType.WIFI) return null
        val displayName = network.ssid.ifBlank { "Hidden Wi-Fi Network" }
        val currentSecurity = network.security.ifBlank { "Not reported" }
        val savedSecurity = profile.wifiSecurity.orEmpty().ifBlank { "Not reported" }
        return DeviceProfileComparison(
            profile = profile,
            nameChanged = profile.displayName.takeIf { it != displayName }?.let { it to displayName },
            securityChanged = savedSecurity.takeIf { it != currentSecurity }?.let { it to currentSecurity },
            signalDelta = network.signal - profile.rssi,
            riskDelta = network.riskScore - profile.riskScore,
            dispositionChanged = profile.disposition.takeIf { it != disposition }?.let { it to disposition },
        )
    }

    private fun persist(profile: LocalDeviceProfile) {
        preferences.edit().putString(profileKey(profile.identifier), encodeProfile(profile).toString()).apply()
    }

    private fun profileKey(identifier: String): String = "profile_v1_${identifier.lowercase()}"

    private fun encodeProfile(profile: LocalDeviceProfile): JSONObject = JSONObject().apply {
        put("identifier", profile.identifier)
        put("deviceType", profile.deviceType.name)
        put("displayName", profile.displayName)
        put("localLabel", profile.localLabel)
        put("notes", profile.notes)
        put("disposition", profile.disposition.name)
        put("rssi", profile.rssi)
        put("signalPattern", JSONArray(profile.signalPattern))
        put("riskScore", profile.riskScore)
        put("savedAt", profile.savedAt)
        put("wifiSsid", profile.wifiSsid)
        put("wifiSecurity", profile.wifiSecurity)
        put("services", JSONArray().apply {
            profile.bluetoothServices.forEach { service -> put(JSONObject().apply {
                put("uuid", service.uuid)
                put("label", service.label)
                put("primary", service.primary)
            }) }
        })
        put("values", JSONArray().apply {
            profile.standardValues.forEach { value -> put(JSONObject().apply {
                put("uuid", value.uuid)
                put("label", value.label)
                put("value", value.value)
            }) }
        })
    }

    private fun decodeProfile(json: JSONObject): LocalDeviceProfile = LocalDeviceProfile(
        identifier = json.getString("identifier"),
        deviceType = ProfileDeviceType.valueOf(json.getString("deviceType")),
        displayName = json.getString("displayName"),
        localLabel = json.optString("localLabel").takeUnless { it == "null" },
        notes = json.optString("notes").takeUnless { it == "null" },
        disposition = runCatching { DeviceDisposition.valueOf(json.optString("disposition")) }.getOrDefault(DeviceDisposition.UNREVIEWED),
        rssi = json.optInt("rssi", -100),
        signalPattern = json.optJSONArray("signalPattern").toIntList(),
        riskScore = json.optInt("riskScore", 0),
        savedAt = json.optLong("savedAt", 0L),
        bluetoothServices = json.optJSONArray("services").toServiceSnapshots(),
        standardValues = json.optJSONArray("values").toValueSnapshots(),
        wifiSsid = json.optString("wifiSsid").takeUnless { it == "null" },
        wifiSecurity = json.optString("wifiSecurity").takeUnless { it == "null" },
    )

    private fun JSONArray?.toIntList(): List<Int> = buildList {
        val source = this@toIntList ?: return@buildList
        for (index in 0 until source.length()) add(source.optInt(index, -100))
    }

    private fun JSONArray?.toServiceSnapshots(): List<GattServiceSnapshot> = buildList {
        val source = this@toServiceSnapshots ?: return@buildList
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val uuid = item.optString("uuid")
            if (uuid.isBlank()) continue
            add(GattServiceSnapshot(uuid, item.optString("label", "Unknown service"), item.optBoolean("primary", true)))
        }
    }

    private fun JSONArray?.toValueSnapshots(): List<StandardValueSnapshot> = buildList {
        val source = this@toValueSnapshots ?: return@buildList
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            val uuid = item.optString("uuid")
            if (uuid.isBlank()) continue
            add(StandardValueSnapshot(uuid, item.optString("label", "Standard value"), item.optString("value")))
        }
    }

    private companion object {
        const val MAX_SIGNAL_SAMPLES = 10
    }
}
