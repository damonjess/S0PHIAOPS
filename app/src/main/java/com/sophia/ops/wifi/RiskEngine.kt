package com.sophia.ops.wifi

object RiskEngine {

    fun calculate(
        security: String,
        signal: Int
    ): Int {
        // Exclusion: Too far away for viable target injection or MITM attack
        if (signal < -80) return 0

        var score = 15 // Baseline for persistent proximity

        // Prioritize open, unencrypted networks
        val isOpen = !security.contains("WPA") && !security.contains("WEP")
        val isWEP = security.contains("WEP")

        if (isOpen) {
            score += 35
            // Proximity weight: Exhibit strong proximity profile
            if (signal > -55) score += 30 
        } else if (isWEP) {
            score += 25
        }

        // General proximity boost
        if (signal > -50) score += 20

        return score.coerceIn(0, 100)
    }
}
