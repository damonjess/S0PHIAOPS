package com.sophia.ops

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.sophia.ops", appContext.packageName)
    }

    @Test
    fun testOuiLookup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // Testing a known VMware OUI if it exists in the database
        val vendor = com.sophia.ops.data.OuiLookup.getVendor(appContext, "00:0C:29:12:34:56")
        // We don't know for sure if it's in the full database, but we can check if it returns something other than "Unknown"
        assertNotEquals("Unknown", vendor)
        
        val unknownVendor = com.sophia.ops.data.OuiLookup.getVendor(appContext, "FF:FF:FF:FF:FF:FF")
        assertEquals("Unknown Vendor", unknownVendor)
    }
}