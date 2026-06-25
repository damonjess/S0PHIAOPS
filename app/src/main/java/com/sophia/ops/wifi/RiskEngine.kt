package com.sophia.ops.wifi

object RiskEngine {

    fun calculate(
        security: String,
        signal: Int
    ): Int {

        var score = 0

        if (security.contains("WEP"))
            score += 40

        if (!security.contains("WPA"))
            score += 30

        if (signal > -50)
            score += 10

        return score.coerceIn(0, 100)
    }
}
