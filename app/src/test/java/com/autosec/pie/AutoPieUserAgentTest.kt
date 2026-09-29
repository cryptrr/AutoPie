package com.autopi

import com.autopi.autopieapp.data.apiService.AutoPieUserAgent
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoPieUserAgentTest {
    @Test
    fun `formats app and Android versions`() {
        assertEquals(
            "AutoPie/0.19.0 Android/16",
            AutoPieUserAgent.format(appVersion = "0.19.0", androidVersion = "16")
        )
    }
}
