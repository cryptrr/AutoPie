package com.autopi

import android.app.Application
import android.os.Environment
import com.autopi.autopieapp.data.AutoPieError
import com.autopi.autopieapp.data.CommandModel
import com.autopi.autopieapp.data.CommandType
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DefaultDispatchers
import com.autopi.utils.Shell
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.test.KoinTest
import java.io.File
import kotlin.test.DefaultAsserter.fail


class ProcessManagerTests : KoinTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `runCommandForShareWithEnv throws if the command is unsafe`() = runTest {

        val mockApplication = mockk<Application>(relaxed = true)
        val testCacheDir = File(System.getProperty("java.io.tmpdir"), "autopie-process-test-cache")
        val testFilesDir = File(System.getProperty("java.io.tmpdir"), "autopie-process-test-files")
        testCacheDir.mkdirs()
        testFilesDir.mkdirs()

        every { mockApplication.getString(any()) } returns "Mocked String"
        every { mockApplication.cacheDir } returns testCacheDir
        every { mockApplication.filesDir } returns testFilesDir

        val mockedPreferences = mockk<AppPreferences>(relaxed = true)

        every { mockedPreferences.getStringSync(any()) } returns "Preferences"
        every { mockedPreferences.getString(any()) } returns flowOf("Preferences")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")

        val autoPieConfigPathProvider = AutoPieConfigPathProvider(mockApplication, mockedPreferences)
        val mainViewModel = MainViewModel(
            mockApplication,
            mockedPreferences,
            autoPieConfigPathProvider,
            DefaultDispatchers()
        )
        val processManagerService = ProcessManagerService(
            mainViewModel,
            DefaultDispatchers(),
            mockApplication,
            autoPieConfigPathProvider
        )

        val newCommand = CommandModel(
            type = CommandType.SHARE,
            name = "Delete everything",
            path = "",
            command = "rm -rf /",
            exec = "ffmpeg",
            extras = emptyList(),
        )


        assertThrows(AutoPieError.UnsafeCommandException::class.java){
            runBlocking {
                processManagerService.runCommandForShareWithEnv2(
                    newCommand,
                    newCommand.exec,
                    newCommand.command,
                    newCommand.path,
                    commandExtraInputs = emptyList(),
                    rawInput = "",
                    processId = 51545,
                    jobType = JobType.STANDALONE
                )
            }
        }

    }

//    @Test
//    fun `runCommandForShareWithEnv does not throw for safe command`() = runTest {
//
//        val mockApplication = mockk<Application>(relaxed = true)
//
//        every { mockApplication.getString(any()) } returns "Mocked String"
//
//        val mockedPreferences = mockk<AppPreferences>(relaxed = true)
//
//        every { mockedPreferences.getStringSync(any()) } returns "Preferences"
//        every { mockedPreferences.getString(any()) } returns flowOf("Preferences")
//
//        val processManagerService = ProcessManagerService(MainViewModel(mockApplication, mockedPreferences, DefaultDispatchers()), DefaultDispatchers(), mockApplication)
//
//        val newCommand = CommandModel(
//            type = CommandType.SHARE,
//            name = "Delete everything",
//            path = "",
//            command = "-o \"/storage/emulated/0/dd.mp4\" out.mp3",
//            deleteSourceFile = true,
//            exec = "ffmpeg",
//            extras = emptyList(),
//        )
//
//
//        try {
//            processManagerService.runCommandForShareWithEnv(newCommand, newCommand.exec, newCommand.command, newCommand.path, commandExtraInputs = emptyList(), processId = 51545)
//        }catch (e: Exception){
//            fail("Should not have thrown an exception: ${e.message}")
//        }
//    }

}
