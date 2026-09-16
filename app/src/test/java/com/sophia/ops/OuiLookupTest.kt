package com.sophia.ops

import com.sophia.ops.data.OuiLookup
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import java.io.ByteArrayInputStream
import org.mockito.ArgumentMatchers.anyString

class OuiLookupTest {

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Reset the OuiLookup singleton's cached database between tests
        val field = OuiLookup::class.java.getDeclaredField("database")
        field.isAccessible = true
        field.set(OuiLookup, null)
    }

    @Test
    fun `test getVendor returns correct vendor for known MAC`() {
        val context = mock(android.content.Context::class.java)
        val assetManager = mock(android.content.res.AssetManager::class.java)
        val json = """{"000C29": "VMware, Inc.", "0005CD": "Apple, Inc."}""".trimIndent()

        `when`(assetManager.open(anyString())).thenReturn(ByteArrayInputStream(json.toByteArray()))
        `when`(context.assets).thenReturn(assetManager)

        // Reset database again right before calling getVendor to ensure fresh state
        val field = OuiLookup::class.java.getDeclaredField("database")
        field.isAccessible = true
        field.set(OuiLookup, null)

        val vendor = OuiLookup.getVendor(context, "00:0C:29:12:34:56")
        assertEquals("VMware, Inc.", vendor)
    }

    @Test
    fun `test getVendor returns Unknown Vendor for unknown unicast MAC`() {
        val context = mock(android.content.Context::class.java)
        val assetManager = mock(android.content.res.AssetManager::class.java)
        val json = """{"000C29": "VMware, Inc."}""".trimIndent()

        `when`(assetManager.open(anyString())).thenReturn(ByteArrayInputStream(json.toByteArray()))
        `when`(context.assets).thenReturn(assetManager)

        val field = OuiLookup::class.java.getDeclaredField("database")
        field.isAccessible = true
        field.set(OuiLookup, null)

        val vendor = OuiLookup.getVendor(context, "00:1A:2B:3C:4D:5E")
        assertEquals("Unknown Vendor", vendor)
    }

    @Test
    fun `test getVendor returns Private Address for locally administered MAC`() {
        val context = mock(android.content.Context::class.java)
        val assetManager = mock(android.content.res.AssetManager::class.java)
        val json = """{"000C29": "VMware, Inc."}""".trimIndent()

        `when`(assetManager.open(anyString())).thenReturn(ByteArrayInputStream(json.toByteArray()))
        `when`(context.assets).thenReturn(assetManager)

        // 0xAA = 10101010, bit 1 (0x02) is set → locally administered
        val vendor = OuiLookup.getVendor(context, "AA:BB:CC:DD:EE:FF")
        assertEquals("Private Address (Randomized)", vendor)
    }

    @Test
    fun `test getVendor returns Non-Unicast Address for multicast MAC`() {
        val context = mock(android.content.Context::class.java)
        val assetManager = mock(android.content.res.AssetManager::class.java)
        val json = """{"000C29": "VMware, Inc."}""".trimIndent()

        `when`(assetManager.open(anyString())).thenReturn(ByteArrayInputStream(json.toByteArray()))
        `when`(context.assets).thenReturn(assetManager)

        // 0x01 = 00000001, bit 0 (0x01) is set → multicast, bit 1 (0x02) NOT set
        val vendor = OuiLookup.getVendor(context, "01:BB:CC:DD:EE:FF")
        assertEquals("Non-Unicast Address", vendor)
    }
}
