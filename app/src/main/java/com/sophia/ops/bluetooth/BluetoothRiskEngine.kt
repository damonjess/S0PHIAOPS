package com.sophia.ops.bluetooth

object BluetoothRiskEngine {

    /**
     * Calculates risk from local persistence and proximity. User tuning only
     * adjusts the score and signal boundary; raw observations are unchanged.
     */
    fun calculate(
        name: String?,
        rssi: Int,
        timesSeen: Int,
        sensitivityAdjustment: Int = 0,
        proximityThreshold: Int = -55,
    ): Int {
        val tunedProximity = proximityThreshold.coerceIn(-75, -40)
        if (timesSeen < 3 || rssi < tunedProximity - 15) return 0

        var score = 20
        if (name.isNullOrBlank() || name.contains("Unknown", true)) score += 25
        if (rssi > tunedProximity) score += 35
        if (timesSeen > 10) score += 10

        return (score + sensitivityAdjustment.coerceIn(-20, 20)).coerceIn(0, 100)
    }
}
