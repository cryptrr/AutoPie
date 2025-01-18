package com.autosec.pie

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.AndroidJUnitRunner
import com.autosec.pie.autopieapp.data.CommandModel
import com.autosec.pie.di.getTestModule
import com.autosec.pie.di.testModule
import com.autosec.pie.autopieapp.presentation.viewModels.CommandsListScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import timber.log.Timber

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AutoPieInstrumentedTests : KoinTest {



    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()



    private lateinit var viewModel: CommandsListScreenViewModel

    //private val scheduler: TestCoroutineScheduler by inject(TestCoroutineScheduler::class.java)



    @Before
    fun setup() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        startKoin {
            androidContext(application)
            modules(getTestModule(mainDispatcherRule.scheduler))
        }

    }

    @Test
    fun loadConfigJSON() = runTest {


        val viewModel: CommandsListScreenViewModel by inject(CommandsListScreenViewModel::class.java)


        assertNotNull(viewModel.main)


        Timber.d(viewModel.fullListOfCommands.toString())


        mainDispatcherRule.scheduler.advanceUntilIdle()


        assert(viewModel.fullListOfCommands.size == 2)
    }
}

class AutoPieTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(cl, TestApplication::class.java.name, context)
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