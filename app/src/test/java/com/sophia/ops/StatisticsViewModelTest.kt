package com.sophia.ops

import com.sophia.ops.viewmodel.StatisticsViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test

class StatisticsViewModelTest {
    @Test
    fun `test statistics view model initialization`() {
        val viewModel = StatisticsViewModel()
        assertNotNull(viewModel)
    }
}
