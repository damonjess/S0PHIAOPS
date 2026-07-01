package com.sophia.ops.data

import android.content.Context
import org.json.JSONObject
import kotlin.jvm.Volatile

object OuiLookup {
    @Volatile
    private var database: JSONObject? = null
    private val lock = Any()

    private fun loadDatabase(context: Context): JSONObject {
        database?.let { return it }
        synchronized(lock) {
            database?.let { return it }
            val loaded = try {
                val jsonString = context.assets.open("oui_database.json").bufferedReader().use { it.readText() }
                JSONObject(jsonString)
            } catch (e: Exception) {
                android.util.Log.e("OuiLookup", "Failed to load OUI database", e)
                JSONObject()
            }
            database = loaded
            return loaded
        }
    }

    fun getVendor(context: Context, macAddress: String): String {
        val cleaned = macAddress.replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .uppercase()

        if (cleaned.length < 6) return "Unknown Vendor"

        val firstOctet = cleaned.substring(0, 2).toIntOrNull(16)
        if (firstOctet != null) {
            // Bit 1 (0x02) of the first octet = locally administered.
            // iOS/Android privacy MACs and BLE privacy addresses always set this,
            // so there is no real vendor to look up — no database will ever resolve these.
            if (firstOctet and 0x02 != 0) {
                return "Private Address (Randomized)"
            }
            // Bit 0 (0x01) = multicast/broadcast, not a real device NIC.
            if (firstOctet and 0x01 != 0) {
                return "Non-Unicast Address"
            }
        }

        val prefix = cleaned.take(6)
        val db = loadDatabase(context)
        return db.optString(prefix, "Unknown Vendor")
    }
}
