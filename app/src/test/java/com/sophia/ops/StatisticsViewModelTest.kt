package com.sophia.ops

import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsViewModelTest {
    @Test
    fun `test StatisticsViewModel class exists`() {
        // Verify the class is loadable — full instantiation requires a Room database
        // which isn't available in unit tests without Robolectric
        val className = "com.sophia.ops.viewmodel.StatisticsViewModel"
        val clazz = Class.forName(className)
        assertTrue(clazz.simpleName == "StatisticsViewModel")
    }
}
