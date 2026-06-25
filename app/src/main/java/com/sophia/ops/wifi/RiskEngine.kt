package com.sophia.ops.wifi

object RiskEngine {

    fun calculate(
        security: String,
        signal: Int
    ): Int {

        var score = 0

        if (security.contains("WEP"))
            score += 30

        if (security.isBlank())
            score += 40

        if (signal > -40)
            score += 10

        return score.coerceIn(0, 100)
    }
}
