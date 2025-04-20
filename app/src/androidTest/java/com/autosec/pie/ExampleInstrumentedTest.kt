package com.autosec.pie

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.AndroidJUnitRunner
import com.autosec.pie.di.getTestModule
import com.autosec.pie.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import com.autosec.pie.core.TestDispatchers
import com.autosec.pie.di.useCaseModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AutoPieInstrumentedTests : KoinTest {


    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setup() {
        val application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        startKoinIfNeeded(application)

    }

    private fun startKoinIfNeeded(application: Context) {
        if (GlobalContext.getOrNull() != null) return

        startKoin {
            androidContext(application)
            modules(getTestModule(mainDispatcherRule.testDispatcher), useCaseModule)
        }
    }

    @Test
    fun demo() = runTest {

        assertEquals(1, 1)
    }

    @Test
    fun loadConfigJSON() = runTest(timeout = 10.seconds){
        val viewModel: CommandsListScreenViewModel by inject(CommandsListScreenViewModel::class.java)

        assertNotNull(viewModel.main)

        mainDispatcherRule.scheduler.advanceUntilIdle()

        // Get the current value
        val fullListOfCommands = viewModel.fullListOfCommands.first{it.isNotEmpty()}

        Timber.d("Final commands list: $fullListOfCommands")
        assertEquals(2, fullListOfCommands.size)
    }
}

class AutoPieTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(cl, TestApplication::class.java.name, context)
    }
}

class MainDispatcherRule : TestWatcher() {
    val scheduler = TestCoroutineScheduler()
    val testDispatcher = TestDispatchers(scheduler)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher.main)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}