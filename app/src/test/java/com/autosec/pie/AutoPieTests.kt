package com.autosec.pie


import android.os.Environment
import com.autosec.pie.autopieApp.data.services.FakeJSONService
import com.autosec.pie.autopieapp.data.CommandCreationModel
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.use_case.CreateCommand
import com.autosec.pie.use_case.GetCommandDetails
import com.autosec.pie.use_case.GetCommandsList
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.test.KoinTest
import timber.log.Timber
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */


class CommandTests : KoinTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()



    @Test
    fun `commands list initially contains two items`() = runTest {

        mockkStatic(Environment::class)

        val mockFile = File("/storage/emulated/0")
        every { Environment.getExternalStorageDirectory() } returns mockFile


        val jsonService = FakeJSONService()
        val getCommandsList = GetCommandsList(jsonService)

        mainDispatcherRule.scheduler.advanceUntilIdle()

        assertEquals(getCommandsList().size, 2)
    }

    @Test
    fun `adding a command makes it 3`() = runTest {

        mockkStatic(Environment::class)

        val mockFile = File("/storage/emulated/0")
        every { Environment.getExternalStorageDirectory() } returns mockFile


        val jsonService = FakeJSONService()
        val createCommand = CreateCommand(jsonService)

        val newCommand = CommandCreationModel(
            selectedCommandType = "SHARE",
            commandName = "Create Plumbus",
            directory = "",
            command = "plumb --create",
            deleteSourceFile = true,
            isValidCommand = true,
            exec = "plumbus",
            commandExtras = emptyList(),
            selectors = "",
            cronInterval = ""
        )

        createCommand(newCommand)


        val getCommandsList = GetCommandsList(jsonService)


        assertEquals(getCommandsList().size, 3)
    }

    @Test
    fun `get a command - success`() = runTest {

        val jsonService = FakeJSONService()
        val getCommand = GetCommandDetails(jsonService)

        val command = getCommand("RSYNC Sync Folder")

        //assertEquals("rsync",command.get("exec").asString)


    }

    @Test
    fun `get a non existent command`() = runTest {

        val jsonService = FakeJSONService()
        val getCommand = GetCommandDetails(jsonService)


        assertThrows(ViewModelError.CommandNotFound::class.java){
            runBlocking {
                getCommand("non existent command")
            }
        }
    }
}

class MainDispatcherRule : TestWatcher() {
    val scheduler = TestCoroutineScheduler()
    val testDispatcher = StandardTestDispatcher(scheduler)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}



