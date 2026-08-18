package com.sophia.ops.wifi

object RiskEngine {

    fun calculate(
        security: String,
        signal: Int,
        sensitivityAdjustment: Int = 0,
        proximityThreshold: Int = -55,
    ): Int {
        val tunedProximity = proximityThreshold.coerceIn(-75, -40)
        val weakSignalCutoff = tunedProximity - 25
        if (signal < weakSignalCutoff) return 0

        var score = 15
        val isOpen = !security.contains("WPA", ignoreCase = true) && !security.contains("WEP", ignoreCase = true)
        val isWep = security.contains("WEP", ignoreCase = true)

        if (isOpen) {
            score += 35
            if (signal > tunedProximity) score += 30
        } else if (isWep) {
            score += 25
        }

        if (signal > tunedProximity + 5) score += 20

        return (score + sensitivityAdjustment.coerceIn(-20, 20)).coerceIn(0, 100)
    }
}
