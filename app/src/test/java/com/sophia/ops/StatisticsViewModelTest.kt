package com.sophia.ops

import android.app.Application
import com.sophia.ops.viewmodel.StatisticsViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

class StatisticsViewModelTest {
    @Test
    fun `test statistics view model initialization`() {
        val application = mock(Application::class.java)
        val viewModel = StatisticsViewModel(application)
        assertNotNull(viewModel)
    }
}