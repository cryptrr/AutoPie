package com.autosec.pie

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.viewModelScope
import com.autosec.pie.di.appModule
import com.autosec.pie.di.useCaseModule
import com.autosec.pie.logging.FileLoggingTree
import com.autosec.pie.autopieapp.data.services.AutoPieCoreService
import com.autosec.pie.autopieapp.data.services.AutoPieCoreService.Companion.createEmptyCookieFile
import com.autosec.pie.autopieapp.data.services.CronService
import com.autosec.pie.autopieapp.data.services.FileObserverJobService
import com.autosec.pie.autopieapp.data.services.ProcessBroadcastReceiver
import com.autosec.pie.autopieapp.data.services.ScreenStateReceiver
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent
import timber.log.Timber




class MyApplication : Application() {

    private val mainViewModel: MainViewModel by KoinJavaComponent.inject(MainViewModel::class.java)
    private val cronService: CronService by KoinJavaComponent.inject(CronService::class.java)

    private val screenStateReceiver = ScreenStateReceiver()


    override fun onCreate() {
        super.onCreate()

        Timber.plant(FileLoggingTree(this@MyApplication))
        Timber.plant(Timber.DebugTree())

        startKoin {
            androidContext(this@MyApplication)
            modules(appModule, useCaseModule)
        }

        initAutosec()
        AutoPieCoreService.extractRequiredFilesAndMakeExec(this@MyApplication)
        AutoPieCoreService.extractBootstrapArchive(this@MyApplication)

        mainViewModel.viewModelScope.launch {
            scheduleJob()
            scheduleCron()
            startScreenStateReceiver()
            startNotificationReceiver()

            checkForUpdates()
            createEmptyCookieFile()
        }
    }

    private fun scheduleJob() {
        if(!mainViewModel.turnOffFileObservers){
            val componentName = ComponentName(this, FileObserverJobService::class.java)
            val jobInfo = JobInfo.Builder(123, componentName)
                .setPersisted(true) // Keep the job alive after device reboot
                .setRequiresCharging(false)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setRequiresDeviceIdle(false)
                //.setPeriodic(15 * 60 * 1000) // Minimum interval for periodic jobs is 15 minutes
                .build()

            val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.schedule(jobInfo)
        }
    }
    private fun scheduleCron(){
        //cronService.testCronJob()
        cronService.setUpCronJobs()
    }

    private fun startScreenStateReceiver(){
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, filter)
    }

    private fun startNotificationReceiver(){
        val receiver = ProcessBroadcastReceiver()
        val filter = IntentFilter().apply {
            addAction("${this@MyApplication.packageName}.SHOW_NOTIFICATION")
            addAction("${this@MyApplication.packageName}.PLAY_MEDIA")
            addAction("${this@MyApplication.packageName}.CANCEL_NOTIFICATION")
            addAction("${this@MyApplication.packageName}.CANCEL_PROCESS")
        }
        registerReceiver(receiver, filter)
    }

    private fun initAutosec(){
        AutoPieCoreService.initAutosec()
    }

    private fun checkForUpdates(){
        if(mainViewModel.updatesAreAvailable == null){
            mainViewModel.checkForUpdates()
        }

        if(mainViewModel.packageUpdatesAreAvailable == null){
            mainViewModel.checkForPackageUpdates()
        }

    }

}



