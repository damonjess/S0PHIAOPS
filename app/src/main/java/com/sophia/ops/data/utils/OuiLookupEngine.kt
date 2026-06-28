package com.sophia.ops.data.utils

import android.content.Context
import org.json.JSONObject
import java.io.IOException

object OuiLookupEngine {
    private val vendorCache = mutableMapOf<String, String>()
    private var isLoaded = false

    fun initialize(context: Context) {
        if (isLoaded) return
        try {
            val jsonString = context.assets.open("oui_database.json")
                .bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            jsonObject.keys().forEach { key ->
                vendorCache[key.uppercase()] = jsonObject.getString(key)
            }
            isLoaded = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun resolveVendor(macAddress: String?): String {
        if (macAddress.isNullOrBlank()) return "Unknown Entity"
        val cleanPrefix = macAddress.replace(":", "")
                                    .replace("-", "")
                                    .take(6)
                                    .uppercase()
        return vendorCache[cleanPrefix] ?: "Unknown Vendor"
    }
}