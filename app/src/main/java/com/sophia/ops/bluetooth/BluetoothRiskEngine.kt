package com.sophia.ops.bluetooth

object BluetoothRiskEngine {

    fun calculate(
        name: String?
    ): Int {

        var score = 0

        if (name.isNullOrBlank())
            score += 20

        if (
            name?.contains(
                "Unknown",
                true
            ) == true
        )
            score += 20

        return score.coerceIn(
            0,
            100
        )
    }
}
