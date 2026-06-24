package com.autopi

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.viewModelScope
import androidx.work.Configuration
import androidx.work.WorkManager
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.data.services.AutoPieCoreService
import com.autopi.autopieapp.data.services.AutoPieCoreService.Companion.createEmptyCookieFile
import com.autopi.autopieapp.data.services.CronService
import com.autopi.autopieapp.data.services.FileObserverJobService
import com.autopi.autopieapp.data.services.ProcessBroadcastReceiver
import com.autopi.autopieapp.data.services.ScreenStateReceiver
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.di.appModule
import com.autopi.di.useCaseModule
import com.autopi.logging.FileLoggingTree
import com.termux.app.TermuxApplication
import com.termux.shared.logger.Logger
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.am.TermuxAmSocketServer
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.theme.TermuxThemeUtils
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

        TermuxAppSharedProperties.init(this@MyApplication)
        onCreateTermux()

        startKoin {
            androidContext(this@MyApplication)
            modules(appModule, useCaseModule)
        }

        val appPreferences: AppPreferences by KoinJavaComponent.inject(AppPreferences::class.java)
        val autoPieConfigPathProvider: AutoPieConfigPathProvider by KoinJavaComponent.inject(
            AutoPieConfigPathProvider::class.java
        )

        Timber.plant(FileLoggingTree(appPreferences, autoPieConfigPathProvider))
        Timber.plant(Timber.DebugTree())

        val config = Configuration.Builder()
            .build()

        //WorkManager.initialize(this, config)


        initAutosec()
        AutoPieCoreService.ensureTermuxBootstrapTriggered(this@MyApplication)

        AutoPieCoreService.extractRequiredFilesAndMakeExec(this@MyApplication)
        if(!AutoPieCoreService.isPrimaryUser(this)){
            //AutoPieCoreService.extractBootstrapArchive(this@MyApplication)
        }
        mainViewModel.viewModelScope.launch {
            scheduleJob()
            scheduleCron()
            startScreenStateReceiver()
            startNotificationReceiver()
            AutoPieCoreService.fetchLatestRepositoryJson()
            checkForUpdates()
            createEmptyCookieFile()
            AutoPieCoreService.setAutoPieGraphics()
        }
    }

    private fun scheduleJob() {
        if(mainViewModel.turnOnFileObservers){
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

    fun onCreateTermux() {
        super.onCreate()

        val context = getApplicationContext()

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this)

        // Set log config for the app
        TermuxApplication.setLogConfig(context)

        Logger.logDebug("Starting Application")

        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        //TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);

        // Init app wide SharedProperties loaded from termux.properties
        val properties = TermuxAppSharedProperties.init(context)

        // Init app wide shell manager
        val shellManager = TermuxShellManager.init(context)

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode())

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        var error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true)
        val isTermuxFilesDirectoryAccessible = error == null
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo("TermuxApplication.LOG_TAG", "Termux files directory is accessible")

            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true)
            if (error != null) {
                Logger.logErrorExtended(
                    "TermuxApplication.LOG_TAG",
                    "Create apps/termux-app directory failed\n" + error
                )
                return
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context)
        } else {
            Logger.logErrorExtended(
                "TermuxApplication.LOG_TAG",
                "Termux files directory is not accessible\n" + error
            )
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this)

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this)
        }
    }

}


