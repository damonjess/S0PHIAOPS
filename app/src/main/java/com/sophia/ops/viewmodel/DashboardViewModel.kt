package com.sophia.ops.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.wifi.RiskEngine
import com.sophia.ops.wifi.WifiScanner

class DashboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    val networks =
        mutableStateListOf<WifiNetwork>()

    fun scan() {

        val scanner =
            WifiScanner(getApplication())

        val results =
            scanner.scan()

        networks.clear()

        results.forEach {

            val risk =
                RiskEngine.calculate(
                    it.capabilities,
                    it.level
                )

            networks.add(
                WifiNetwork(
                    ssid = it.SSID,
                    bssid = it.BSSID,
                    signal = it.level,
                    security = it.capabilities,
                    riskScore = risk,
                    timestamp =
                    System.currentTimeMillis()
                )
            )
        }
    }
}
