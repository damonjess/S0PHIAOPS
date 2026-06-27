package com.sophia.ops.data.db

import androidx.room.TypeConverter
import com.sophia.ops.data.entities.SignalPoint
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromSignalPointList(value: List<SignalPoint>): String {
        val array = JSONArray()
        value.forEach {
            val obj = JSONObject()
            obj.put("rssi", it.rssi)
            obj.put("ts", it.timestamp)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toSignalPointList(value: String): List<SignalPoint> {
        val list = mutableListOf<SignalPoint>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SignalPoint(
                        rssi = obj.getInt("rssi"),
                        timestamp = obj.getLong("ts")
                    )
                )
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }
        return list
    }
}
