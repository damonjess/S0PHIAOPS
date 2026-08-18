package com.sophia.ops.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class WifiScanner(
    private val context: Context,
) {
    private val tag = "WifiScanner"
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeCancellation: (() -> Unit)? = null

    fun cancelScan() {
        activeCancellation?.invoke()
    }

    /**
     * Requests a fresh Wi-Fi scan and always completes with either results or a
     * human-readable reason. Android can reject scans when Wi-Fi, Location,
     * permission, or the platform scan-rate limit is unavailable.
     */
    @SuppressLint("MissingPermission")
    fun startScan(
        onResults: (List<ScanResult>) -> Unit,
        onFailure: (String) -> Unit = {},
    ) {
        when (val preflightError = preflightError()) {
            null -> Unit
            else -> {
                Log.w(tag, preflightError)
                onFailure(preflightError)
                return
            }
        }

        val completed = AtomicBoolean(false)
        lateinit var receiver: BroadcastReceiver

        fun unregisterReceiverSafely() {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            } catch (error: Exception) {
                Log.w(tag, "Unable to unregister Wi-Fi receiver", error)
            }
        }

        fun completeWithResults(results: List<ScanResult>) {
            if (!completed.compareAndSet(false, true)) return
            activeCancellation = null
            handler.removeCallbacksAndMessages(receiver)
            unregisterReceiverSafely()
            Log.i(tag, "Wi-Fi scan completed with ${results.size} result(s).")
            onResults(results)
        }

        fun completeWithFailure(message: String) {
            if (!completed.compareAndSet(false, true)) return
            activeCancellation = null
            handler.removeCallbacksAndMessages(receiver)
            unregisterReceiverSafely()
            Log.w(tag, message)
            onFailure(message)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                val results = readCurrentResults()
                if (results.isNotEmpty()) {
                    completeWithResults(results)
                } else if (updated) {
                    completeWithFailure("Wi-Fi scan finished with no nearby networks. Verify that Wi-Fi and Location are enabled.")
                } else {
                    completeWithFailure("Wi-Fi scan did not return fresh results. Android may be temporarily limiting scans.")
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (error: Exception) {
            Log.e(tag, "Failed to register Wi-Fi receiver", error)
            val cachedResults = readCurrentResults()
            if (cachedResults.isNotEmpty()) onResults(cachedResults)
            else onFailure("Wi-Fi receiver could not be registered: ${error.localizedMessage ?: "unknown error"}")
            return
        }

        val timeout = Runnable {
            completeWithFailure("Wi-Fi scan timed out. Toggle Wi-Fi or Location, then try again.")
        }
        handler.postDelayed(timeout, WIFI_SCAN_TIMEOUT_MS)
        activeCancellation = {
            completeWithFailure("Wi-Fi scan canceled.")
        }

        val started = try {
            wifiManager.startScan()
        } catch (error: SecurityException) {
            Log.e(tag, "SecurityException calling startScan", error)
            false
        } catch (error: Exception) {
            Log.e(tag, "Unexpected Wi-Fi scan error", error)
            false
        }

        Log.i(tag, "wifiManager.startScan() returned $started")
        if (!started) {
            val cachedResults = readCurrentResults()
            if (cachedResults.isNotEmpty()) {
                completeWithResults(cachedResults)
            } else {
                completeWithFailure("Wi-Fi scan was not started. Android may be throttling scans; wait 20 seconds and try again.")
            }
        }
    }

    private fun preflightError(): String? {
        if (!wifiManager.isWifiEnabled) {
            return "Wi-Fi is turned off. Enable Wi-Fi and try again."
        }

        val locationPermissionGranted =
            appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!locationPermissionGranted) {
            return "Location permission is required to read nearby Wi-Fi networks."
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Nearby Wi-Fi devices permission is required for scanning."
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            return "Device Location is turned off. Enable Location to scan nearby Wi-Fi networks."
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private fun readCurrentResults(): List<ScanResult> = try {
        wifiManager.scanResults
    } catch (error: SecurityException) {
        Log.e(tag, "SecurityException reading Wi-Fi results", error)
        emptyList()
    }

    private companion object {
        const val WIFI_SCAN_TIMEOUT_MS = 12_000L
    }
}
