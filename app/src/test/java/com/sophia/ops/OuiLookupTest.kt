package com.sophia.ops

import android.content.Context
import android.content.res.AssetManager
import com.sophia.ops.data.OuiLookup
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.ByteArrayInputStream

class OuiLookupTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var assetManager: AssetManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(context.assets).thenReturn(assetManager)
    }

    @Test
    fun `test getVendor returns correct vendor for known MAC`() {
        val json = """{"000C29": "VMware, Inc.", "0005CD": "Apple, Inc."}"""
        `when`(assetManager.open(anyString())).thenReturn(ByteArrayInputStream(json.toByteArray()))

        val vendor = OuiLookup.getVendor(context, "00:0C:29:12:34:56")
        assertEquals("VMware, Inc.", vendor)
    }

    @Test
    fun `test getVendor returns Unknown Vendor for unknown MAC`() {
        val json = """{"000C29": "VMware, Inc."}"""
        `when`(assetManager.open(anyString())).thenReturn(ByteArrayInputStream(json.toByteArray()))

        val vendor = OuiLookup.getVendor(context, "AA:BB:CC:DD:EE:FF")
        assertEquals("Unknown Vendor", vendor)
    }
}
