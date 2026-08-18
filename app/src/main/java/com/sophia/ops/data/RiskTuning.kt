package com.sophia.ops.data

import android.content.Context

data class RiskTuning(
    val sensitivityAdjustment: Int = 0,
    val proximityThreshold: Int = -55,
    val excludeTrustedFromAssessment: Boolean = true,
    val alertWatchlistDevices: Boolean = true,
)

class RiskTuningStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sophia_risk_tuning",
        Context.MODE_PRIVATE,
    )

    fun load(): RiskTuning = RiskTuning(
        sensitivityAdjustment = preferences.getInt("sensitivity_adjustment", 0).coerceIn(-20, 20),
        proximityThreshold = preferences.getInt("proximity_threshold", -55).coerceIn(-75, -40),
        excludeTrustedFromAssessment = preferences.getBoolean("exclude_trusted_from_assessment", true),
        alertWatchlistDevices = preferences.getBoolean("alert_watchlist_devices", true),
    )

    fun save(tuning: RiskTuning) {
        preferences.edit()
            .putInt("sensitivity_adjustment", tuning.sensitivityAdjustment.coerceIn(-20, 20))
            .putInt("proximity_threshold", tuning.proximityThreshold.coerceIn(-75, -40))
            .putBoolean("exclude_trusted_from_assessment", tuning.excludeTrustedFromAssessment)
            .putBoolean("alert_watchlist_devices", tuning.alertWatchlistDevices)
            .apply()
    }
}
