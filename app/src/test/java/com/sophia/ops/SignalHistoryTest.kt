package com.sophia.ops

import com.sophia.ops.data.entities.SignalPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalHistoryTest {

    @Test
    fun `test signal history grows and stays within limit`() {
        var signalHistory = emptyList<SignalPoint>()
        val limit = 10

        // Add 15 signals
        for (i in 1..15) {
            val now = System.currentTimeMillis()
            signalHistory = (signalHistory + SignalPoint(rssi = -60 - i, timestamp = now)).takeLast(limit)
        }

        assertEquals(10, signalHistory.size)
        assertEquals(-75, signalHistory.last().rssi)
        assertEquals(-66, signalHistory.first().rssi)
    }
}
