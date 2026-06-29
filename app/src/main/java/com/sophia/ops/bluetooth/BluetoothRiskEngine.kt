package com.sophia.ops.bluetooth

object BluetoothRiskEngine {

    /**
     * Calculates risk based on persistence and proximity.
     * Transient devices (seen once) or those with weak signal are dropped to zero.
     */
    fun calculate(
        name: String?,
        rssi: Int,
        timesSeen: Int
    ): Int {
        // Persistence Metric: Only escalate if seen in 3+ intervals
        // Signal Saturation: RSSI must be greater than -70 dBm
        if (timesSeen < 3 || rssi < -70) {
            return 0
        }

        var score = 20 // Baseline for persistent proximity

        // If the device has no name or a generic name, it's more suspicious
        if (name.isNullOrBlank() || name.contains("Unknown", true)) {
            score += 25
        }

        // Proximity scaling
        if (rssi > -55) {
            score += 35
        }

        // Additional boost for long-term persistent devices
        if (timesSeen > 10) {
            score += 10
        }

        return score.coerceIn(0, 100)
    }
}
