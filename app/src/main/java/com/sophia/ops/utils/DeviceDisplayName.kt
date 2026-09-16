package com.sophia.ops.utils

/**
 * Shared device display-name resolution logic.
 * Used by both [com.sophia.ops.viewmodel.DashboardViewModel] and
 * [com.sophia.ops.viewmodel.DevicesViewModel] to avoid duplication.
 */
object DeviceDisplayName {

    /**
     * Resolve a human-readable name for a Bluetooth device.
     *
     * @param rawName the device's raw name or nickname (may be null/blank)
     * @param vendor the OUI vendor string (may be "Unknown Vendor" or
     *               "Private Address (Randomized)")
     */
    fun forBluetooth(rawName: String?, vendor: String): String = when {
        !rawName.isNullOrBlank() &&
        !rawName.startsWith("Discovered Device") &&
        !rawName.contains("Unknown", true) -> rawName

        vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)" -> vendor

        else -> "Unknown Bluetooth Device"
    }

    /**
     * Resolve a human-readable name for a Wi-Fi network.
     *
     * @param ssid the network's SSID (may be blank or "<unknown ssid>")
     * @param vendor the OUI vendor string (may be "Unknown Vendor" or
     *               "Private Address (Randomized)")
     */
    fun forWifi(ssid: String, vendor: String): String =
        if (ssid.isBlank() || ssid == "<unknown ssid>") {
            if (vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)") {
                vendor
            } else {
                "Hidden Network"
            }
        } else {
            ssid
        }
}
