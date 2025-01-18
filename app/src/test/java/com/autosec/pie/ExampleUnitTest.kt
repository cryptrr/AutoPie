package com.autosec.pie

import android.app.Application
import com.autosec.pie.di.appModule
import com.autosec.pie.di.testModule
import com.autosec.pie.viewModels.CommandsListScreenViewModel
import com.autosec.pie.viewModels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.koin.test.inject

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */


class ExampleUnitTest : KoinTest {


}



