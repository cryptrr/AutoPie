package com.autosec.pie

import android.app.Application
import android.os.Environment
import com.autosec.pie.autopieapp.data.AutoPieError
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.autopieapp.data.CommandType
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.core.DefaultDispatchers
import com.autosec.pie.utils.Shell
import io.mockk.every
import io.mockk.mockk
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

        every { mockApplication.getString(any()) } returns "Mocked String"

        val mockedPreferences = mockk<AppPreferences>(relaxed = true)

        every { mockedPreferences.getStringSync(any()) } returns "Preferences"
        every { mockedPreferences.getString(any()) } returns flowOf("Preferences")

        val processManagerService = ProcessManagerService(MainViewModel(mockApplication, mockedPreferences, DefaultDispatchers()), DefaultDispatchers(), mockApplication)

        val newCommand = CommandModel(
            type = CommandType.SHARE,
            name = "Delete everything",
            path = "",
            command = "rm -rf /",
            deleteSourceFile = true,
            exec = "ffmpeg",
            extras = emptyList(),
        )


        assertThrows(AutoPieError.UnsafeCommandException::class.java){
            runBlocking {
                processManagerService.runCommandForShareWithEnv(newCommand, newCommand.exec, newCommand.command, newCommand.path, commandExtraInputs = emptyList(), processId = 51545)
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


