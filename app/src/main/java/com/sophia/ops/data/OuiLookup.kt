package com.sophia.ops.data

import android.content.Context
import org.json.JSONObject

object OuiLookup {
    private var database: JSONObject? = null

    fun getVendor(context: Context, macAddress: String): String {
        // Remove common separators and take the first 6 hex characters
        val prefix = macAddress.replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .take(6)
            .uppercase()

        return try {
            if (database == null) {
                val jsonString = context.assets.open("oui_database.json").bufferedReader().use { it.readText() }
                database = JSONObject(jsonString)
            }
            database?.optString(prefix, "Unknown Vendor") ?: "Unknown Vendor"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
