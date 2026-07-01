package com.sophia.ops.data

import android.content.Context
import org.json.JSONObject
import kotlin.jvm.Volatile

object OuiLookup {
    @Volatile
    private var database: JSONObject? = null
    private val lock = Any()

    fun getVendor(context: Context, macAddress: String): String {
        // Remove common separators and take the first 6 hex characters
        val prefix = macAddress.replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .take(6)
            .uppercase()

        if (database == null) {
            // If database is not loaded, we might be on a thread that shouldn't block.
            // But we need the result. For now, let's keep the synchronization but 
            // ensure it's at least not reloading if it fails once.
            synchronized(lock) {
                if (database == null) {
                    try {
                        val jsonString = context.assets.open("oui_database.json").bufferedReader().use { it.readText() }
                        database = JSONObject(jsonString)
                    } catch (e: Exception) {
                        android.util.Log.e("OuiLookup", "Failed to load OUI database", e)
                        database = JSONObject() // Empty object to avoid re-trying and crashing
                    }
                }
            }
        }

        return database?.optString(prefix, "Unknown Vendor") ?: "Unknown Vendor"
    }
}
