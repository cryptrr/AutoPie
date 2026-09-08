package com.autopi.autopieapp.data.services

import android.app.Application
import android.content.Intent
import com.autopi.autopieapp.data.CommandResult
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DispatcherProvider
import com.autopi.core.TestDispatchers
import com.autopi.use_case.AutoPieUseCases
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundShareExecutionTest {
    @After fun tearDown() {
        stopKoin()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test fun serviceTracksWholeRequestsAndCleansUpEmptyOrFailedFlows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val useCases = mockk<AutoPieUseCases>(relaxed = true)
        val notification = mockk<AutoPieNotification>(relaxed = true)
        startKoin { modules(module {
            single { useCases }
            single { notification }
            single { mockk<MainViewModel>(relaxed = true) }
            single<DispatcherProvider> { TestDispatchers(testScheduler) }
        }) }
        val service = spyk(ForegroundService())
        val app = mockk<Application>()
        every { app.cacheDir } returns File("/tmp")
        every { service.application } returns app
        every { service.stopSelfResult(any()) } returns true
        val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        coEvery { useCases.runCommand(any(), any(), any(), any(), any()) } answers {
            val id = arg<Int>(4)
            flow {
                emit(CommandResult("test", id, true, "", JobType.FILE, "first"))
                gates[id - 1].await()
                emit(CommandResult("test", id, true, "", JobType.FILE, "second"))
            }
        }
        fun intent(id: Int) = mockk<Intent>().also {
            every { it.getIntExtra("processId", any()) } returns id
            every { it.getStringExtra(any()) } returns null
            every { it.getStringExtra("command") } returns """{"name":"test","command":"echo ok"}"""
        }

        service.onStartCommand(intent(1), 0, 10)
        service.onStartCommand(intent(2), 0, 11)
        runCurrent()
        verify(exactly = 0) { service.stopSelfResult(any()) }
        gates[0].complete(Unit)
        runCurrent()
        verify(exactly = 0) { service.stopSelfResult(any()) }
        gates[1].complete(Unit)
        advanceUntilIdle()
        verify(exactly = 1) { service.stopSelfResult(11) }
        verify(exactly = 4) { notification.sendNotification("Command Success", any(), any(), any(), any()) }
        coVerify(exactly = 2) { useCases.runCommand(any(), null, emptyList(), emptyList(), any()) }

        coEvery { useCases.runCommand(any(), any(), any(), any(), any()) } returns emptyFlow()
        service.onStartCommand(intent(3), 0, 12)
        advanceUntilIdle()
        verify(exactly = 1) { service.stopSelfResult(12) }

        coEvery { useCases.runCommand(any(), any(), any(), any(), any()) } throws IllegalStateException("failed")
        service.onStartCommand(intent(4), 0, 13)
        advanceUntilIdle()
        verify(exactly = 1) { service.stopSelfResult(13) }
        verify(exactly = 1) { notification.sendNotification("Command Failed", any(), any(), any(), 4) }
    }
}
