package com.autopi.autopieapp.data.services

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandResult
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.use_case.AutoPieUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class CronJobWorkerTest {
    private val useCases = mockk<AutoPieUseCases>(relaxed = true)

    @Before fun setUp() {
        startKoin { modules(module { single { useCases } }) }
    }

    @After fun tearDown() = stopKoin()

    private fun worker(attempt: Int = 0, key: String = "scheduled"): CronJobWorker {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns Data.Builder().putString(CronJobWorker.COMMAND_KEY, key).build()
        every { params.runAttemptCount } returns attempt
        return CronJobWorker(mockk<Context>(relaxed = true), params)
    }

    @Test fun changedTypeAndInvalidIntervalNeverExecute() = runTest {
        val commands = listOf(
            CommandModel(type = CommandType.SHARE, cronInterval = "15m"),
            CommandModel(type = CommandType.FILE_OBSERVER, cronInterval = "15m"),
            CommandModel(type = CommandType.CRON, cronInterval = ""),
            CommandModel(type = CommandType.CRON, cronInterval = "0m")
        )
        for (command in commands) {
            coEvery { useCases.getCommandDetails("scheduled") } returns command
            assertEquals(Result.success(), worker().doWork())
        }
        verify(exactly = 0) { useCases.runCronCommand(any(), any(), any()) }
    }

    @Test fun deletedCommandIsNotRetried() = runTest {
        coEvery { useCases.getCommandDetails("scheduled") } throws ViewModelError.CommandNotFound
        assertEquals(Result.success(), worker().doWork())
        verify(exactly = 0) { useCases.runCronCommand(any(), any(), any()) }
    }

    @Test fun unavailableConfigHasBoundedRetries() = runTest {
        coEvery { useCases.getCommandDetails("scheduled") } throws ViewModelError.CommandConfigUnavailable
        assertEquals(Result.retry(), worker(attempt = 0).doWork())
        assertEquals(Result.failure(), worker(attempt = 3).doWork())
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagates() = runTest {
        coEvery { useCases.getCommandDetails("scheduled") } throws CancellationException("stopped")
        worker().doWork()
        Unit
    }

    @Test fun validCronUsesCronRunnerAndDoesNotRetryNonzeroExit() = runTest {
        val command = CommandModel(type = CommandType.CRON, cronInterval = "15m")
        coEvery { useCases.getCommandDetails("scheduled") } returns command
        every { useCases.runCronCommand(command, emptyList(), any()) } returns flowOf(
            CommandResult("scheduled", 123, false, "failed", JobType.CRON, "")
        )
        assertEquals(Result.failure(), worker().doWork())
        verify(exactly = 1) { useCases.runCronCommand(command, emptyList(), any()) }
    }

    @Test fun missingKeyDoesNotLoadOrRunCommand() = runTest {
        assertEquals(Result.failure(), worker(key = "").doWork())
        coVerify(exactly = 0) { useCases.getCommandDetails(any()) }
    }
}
