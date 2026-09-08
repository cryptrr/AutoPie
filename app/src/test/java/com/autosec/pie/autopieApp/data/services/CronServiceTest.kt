package com.autopi.autopieapp.data.services

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DispatcherProvider
import com.autopi.core.TestDispatchers
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class CronServiceTest {
    @After fun tearDown() {
        stopKoin()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test fun overflowingIntervalAndEnqueueFailureDoNotPreventLaterSchedulingAttempts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val application = mockk<Application>(relaxed = true)
        every { application.getSharedPreferences(any(), any()).getBoolean(any(), false) } returns true
        val main = mockk<MainViewModel>(relaxed = true)
        every { main.eventFlow } returns kotlinx.coroutines.flow.MutableSharedFlow()
        val workManager = mockk<WorkManager>()
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(application) } returns workManager
        every { workManager.getWorkInfosByTagFlow(any()) } returns flowOf(emptyList())
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } throws IllegalStateException("enqueue failed")
        startKoin {
            modules(module {
                single<Context> { application }
                single { main }
                single<DispatcherProvider> { TestDispatchers(testScheduler) }
            })
        }
        val json = mockk<JsonService>()
        every { json.readCommandsConfig() } returns JsonParser.parseString(
            """{"bad":{"type":"CRON","cronInterval":"9223372036854775807h"},
                "failed":{"type":"CRON","cronInterval":"15m"},
                "good":{"type":"CRON","cronInterval":"15m"}}"""
        ).asJsonObject

        CronService(json).setUpCronJobs()
        advanceUntilIdle()

        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork("autopie.cron.bad", any(), any()) }
        verify(exactly = 1) { workManager.enqueueUniquePeriodicWork("autopie.cron.failed", any(), any()) }
        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork("autopie.cron.good", ExistingPeriodicWorkPolicy.UPDATE, any())
        }
        verify(exactly = 1) { workManager.getWorkInfosByTagFlow("autopie.cron") }
    }
}
