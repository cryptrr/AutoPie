package com.autopi.autopieapp.data.services

import com.autopi.autopieapp.data.JobType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceCommandStartedTest {
    @Test
    fun `standalone commands send started notifications`() {
        assertTrue(shouldSendStartedNotification(42, JobType.STANDALONE, listOf(42)))
    }

    @Test
    fun `cron and inactive commands do not send started notifications`() {
        assertFalse(shouldSendStartedNotification(42, JobType.CRON, listOf(42)))
        assertFalse(shouldSendStartedNotification(42, JobType.STANDALONE, listOf(7)))
    }
}
