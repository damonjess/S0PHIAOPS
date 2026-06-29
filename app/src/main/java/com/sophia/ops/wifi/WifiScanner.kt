package com.sophia.ops.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat

class WifiScanner(
    private val context: Context
) {
    private val tag = "WifiScanner"

    private val wifiManager =
        context.applicationContext
            .getSystemService(Context.WIFI_SERVICE)
                as WifiManager

    @SuppressLint("MissingPermission")
    fun startScan(
        onResults: (List<ScanResult>) -> Unit
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                try {
                    context?.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.e(tag, "Error unregistering receiver", e)
                }
                
                val results = try {
                    wifiManager.scanResults
                } catch (e: SecurityException) {
                    Log.e(tag, "SecurityException accessing scanResults", e)
                    emptyList()
                }
                Log.i(tag, "Scan results received via broadcast: ${results.size} items")
                onResults(results)
            }
        }

        try {
            // TargetSdk 34+ requires RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED for most broadcasts.
            // SCAN_RESULTS_AVAILABLE_ACTION is a system broadcast, so RECEIVER_EXPORTED is usually correct.
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to register receiver", e)
            val currentResults = try {
                wifiManager.scanResults
            } catch (se: SecurityException) {
                emptyList()
            }
            onResults(currentResults)
            return
        }

        @Suppress("DEPRECATION")
        val success = try {
            wifiManager.startScan()
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException calling startScan", e)
            false
        }
        Log.i(tag, "wifiManager.startScan() called at ${System.currentTimeMillis()}. Success: $success")

        if (!success) {
            // If scan failed or was throttled, unregister and return current results immediately
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
            val currentResults = try {
                wifiManager.scanResults
            } catch (e: SecurityException) {
                emptyList()
            }
            Log.d(tag, "Scan throttled. Returning current results: ${currentResults.size}")
            onResults(currentResults)
        }
    }
}
