package com.sophia.ops

import com.sophia.ops.utils.DeviceDisplayName
import com.sophia.ops.wifi.RiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDisplayNameTest {

    @Test
    fun `forBluetooth returns raw name when valid`() {
        val result = DeviceDisplayName.forBluetooth("My Headphones", "Apple, Inc.")
        assertEquals("My Headphones", result)
    }

    @Test
    fun `forBluetooth falls back to vendor when name is generic`() {
        val result = DeviceDisplayName.forBluetooth("Discovered Device", "Apple, Inc.")
        assertEquals("Apple, Inc.", result)
    }

    @Test
    fun `forBluetooth falls back to Unknown when no name and unknown vendor`() {
        val result = DeviceDisplayName.forBluetooth(null, "Unknown Vendor")
        assertEquals("Unknown Bluetooth Device", result)
    }

    @Test
    fun `forBluetooth falls back to Unknown when name contains Unknown`() {
        val result = DeviceDisplayName.forBluetooth("Unknown Device", "Unknown Vendor")
        assertEquals("Unknown Bluetooth Device", result)
    }

    @Test
    fun `forWifi returns SSID when present`() {
        val result = DeviceDisplayName.forWifi("MyNetwork", "Unknown Vendor")
        assertEquals("MyNetwork", result)
    }

    @Test
    fun `forWifi returns Hidden Network for blank SSID`() {
        val result = DeviceDisplayName.forWifi("", "Unknown Vendor")
        assertEquals("Hidden Network", result)
    }

    @Test
    fun `forWifi returns vendor for hidden SSID with known vendor`() {
        val result = DeviceDisplayName.forWifi("<unknown ssid>", "Cisco Systems")
        assertEquals("Cisco Systems", result)
    }

    @Test
    fun `forWifi returns Hidden Network for unknown ssid`() {
        val result = DeviceDisplayName.forWifi("<unknown ssid>", "Unknown Vendor")
        assertEquals("Hidden Network", result)
    }
}

class RiskEngineTest {

    @Test
    fun `open network with strong signal scores high`() {
        val score = RiskEngine.calculate(security = "[ESS]", signal = -50)
        assertTrue("Expected score > 60 for open network with strong signal, got $score", score > 60)
    }

    @Test
    fun `WPA2 network with weak signal scores low`() {
        val score = RiskEngine.calculate(security = "[WPA2-PSK-CCMP]", signal = -90)
        assertEquals(0, score)
    }

    @Test
    fun `WEP network has moderate risk`() {
        val score = RiskEngine.calculate(security = "[WEP]", signal = -50)
        assertTrue("Expected score >= 40 for WEP network, got $score", score >= 40)
    }

    @Test
    fun `signal below cutoff returns zero`() {
        val score = RiskEngine.calculate(security = "[WPA2-PSK-CCMP]", signal = -100)
        assertEquals(0, score)
    }

    @Test
    fun `score is clamped to 0-100`() {
        val score = RiskEngine.calculate(security = "[ESS]", signal = -30, sensitivityAdjustment = 50)
        assertTrue("Expected score <= 100, got $score", score <= 100)
    }

    @Test
    fun `sensitivity adjustment increases score`() {
        val base = RiskEngine.calculate(security = "[WPA2-PSK-CCMP]", signal = -50, sensitivityAdjustment = 0)
        val adjusted = RiskEngine.calculate(security = "[WPA2-PSK-CCMP]", signal = -50, sensitivityAdjustment = 10)
        assertTrue("Adjusted score ($adjusted) should be >= base ($base)", adjusted >= base)
    }
}
