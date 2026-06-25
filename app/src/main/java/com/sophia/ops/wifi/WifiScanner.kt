package com.sophia.ops.wifi

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager

class WifiScanner(
    private val context: Context
) {

    fun scan(): List<ScanResult> {

        val wifi =
            context.getSystemService(
                Context.WIFI_SERVICE
            ) as WifiManager

        @Suppress("DEPRECATION")
        wifi.startScan()

        return wifi.scanResults
    }
}
