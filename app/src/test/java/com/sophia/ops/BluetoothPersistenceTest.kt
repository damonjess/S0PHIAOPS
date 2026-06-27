package com.sophia.ops

import com.sophia.ops.data.entities.BluetoothDeviceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothPersistenceTest {

    @Test
    fun `test bluetooth entity notes property`() {
        val device = BluetoothDeviceEntity(
            name = "Test Device",
            address = "00:11:22:33:44:55",
            deviceType = 1,
            firstSeen = 1000L,
            lastSeen = 2000L,
            riskScore = 10,
            notes = "Medical ECG"
        )

        assertEquals("Medical ECG", device.notes)
        
        val updatedDevice = device.copy(notes = "Trusted")
        assertEquals("Trusted", updatedDevice.notes)
    }
}
