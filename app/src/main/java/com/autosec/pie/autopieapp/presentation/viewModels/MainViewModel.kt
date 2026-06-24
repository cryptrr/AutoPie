package com.autopi.autopieapp.presentation.viewModels

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autopi.BuildConfig
import com.autopi.autopieapp.data.AutoPieConstants
import com.autopi.autopieapp.data.CommandModel
import com.autopi.core.DispatcherProvider
import com.autopi.autopieapp.data.preferences.AppPreferences
import com.autopi.autopieapp.data.preferences.AutoPieConfigLocation
import com.autopi.autopieapp.data.preferences.AutoPieConfigPathProvider
import com.autopi.autopieapp.domain.AppNotification
import com.autopi.autopieapp.domain.Notification
import com.autopi.autopieapp.domain.ViewModelError
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.data.services.AutoPieCoreService
import com.autopi.autopieapp.data.services.FileObserverJobService
import com.autopi.autopieapp.data.services.GithubApiService
import com.autopi.autopieapp.data.services.ProcessManagerService
import com.autopi.autopieapp.data.services.ReleaseInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File

class MainViewModel(
    private val application: Application,
    private val appPreferences: AppPreferences,
    private val autoPieConfigPathProvider: AutoPieConfigPathProvider,
    private val dispatchers: DispatcherProvider,
) : AndroidViewModel(application) {


    private val processManagerService: ProcessManagerService by inject(ProcessManagerService::class.java)

    private val _eventFlow = MutableSharedFlow<ViewModelEvent>(replay = 0)
    val eventFlow = _eventFlow.asSharedFlow()

    var currentCommandKey = mutableStateOf("")
    var currentSelectedCommand = mutableStateOf<CommandModel?>(null)

    var viewModelError = MutableSharedFlow<Notification?>(replay = 0)
    var appNotification = MutableSharedFlow<Notification?>(replay = 0)


    var pythonInstallationComplete by mutableStateOf(false)

    var updatesAreAvailable : Boolean? by mutableStateOf(null)
    var packageUpdatesAreAvailable : Boolean? by mutableStateOf(null)

    var updateDetails: ReleaseInfo? by mutableStateOf(null)

    var sharesConfigAvailable by mutableStateOf(false)
    var observerConfigAvailable by mutableStateOf(false)
    var schedulerConfigAvailable by mutableStateOf(false)
    var mcpServerActive by mutableStateOf(false)

    var installInitPackagesPrompt by mutableStateOf(false)

    //MOVED HERE FROM SHARERECEIVERVIEWMODEL becase of State Hoisting necessity
    var shareReceiverSearchQuery = mutableStateOf("")




    var turnOnFileObservers by mutableStateOf(appPreferences.getBoolSync(AppPreferences.IS_FILE_OBSERVERS_ON)).also { state ->
        viewModelScope.launch {
            appPreferences.getBool(AppPreferences.IS_FILE_OBSERVERS_ON).collectLatest{
                state.value = it
            }
        }
    }

    var autoPieConfigPath by mutableStateOf(autoPieConfigPathProvider.getAutoSecDirectory().absolutePath)
    var fileLoggerPath by mutableStateOf(autoPieConfigPathProvider.getLogFile().absolutePath)

    var autoPieConfigLocation by mutableStateOf(autoPieConfigPathProvider.getConfigLocationSync()).also { state ->
        viewModelScope.launch {
            autoPieConfigPathProvider.locationFlow.collectLatest {
                state.value = it
                autoPieConfigPath = autoPieConfigPathProvider.getAutoSecDirectory(it).absolutePath
                fileLoggerPath = autoPieConfigPathProvider.getLogFile().absolutePath
            }
        }
    }

    var fileLoggingEnabled by mutableStateOf(appPreferences.getBoolSync(AppPreferences.FILE_LOGGING_ENABLED, true)).also { state ->
        viewModelScope.launch {
            appPreferences.getBool(AppPreferences.FILE_LOGGING_ENABLED, true).collectLatest{
                state.value = it
            }
        }
    }

    var storageManagerPermissionGranted by mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        application.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                application.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    })

    fun showNotification(notification: AppNotification){
        viewModelScope.launch {
            appNotification.emit(notification)
        }
    }

    fun showError(error: ViewModelError){
        Timber.e("Printing Error:")
        viewModelScope.launch {
            viewModelError.emit(error)
        }
    }

    fun toggleFileObservers(){
        viewModelScope.launch {
            if(appPreferences.getBoolSync(AppPreferences.IS_FILE_OBSERVERS_ON)){
                cancelJob(application)
                appPreferences.setBool(AppPreferences.IS_FILE_OBSERVERS_ON, false)
            }else{
                scheduleJob(application)
                appPreferences.setBool(AppPreferences.IS_FILE_OBSERVERS_ON, true)
            }
        }
    }

    fun updateAutoPieConfigLocation(location: AutoPieConfigLocation) {
        if (location == autoPieConfigLocation) {
            return
        }

        viewModelScope.launch(dispatchers.io) {
            val moved = autoPieConfigPathProvider.moveAutoSecDirectoryTo(location)
            if (!moved) {
                showError(ViewModelError.Unknown)
                return@launch
            }

            autoPieConfigPathProvider.setConfigLocation(location)
            AutoPieCoreService.initAutosec()
        }
    }

    fun updateFileLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBool(AppPreferences.FILE_LOGGING_ENABLED, enabled)
        }
    }



    fun dispatchEvent(event: ViewModelEvent) {
        Timber.d("Event Fired: $event")
        viewModelScope.launch(dispatchers.main) {
            when (event) {

                else -> {

                }
            }
            _eventFlow.emit(event)
        }


    }

    fun clearAllBanners(){
        viewModelScope.launch {
            appNotification.emit(null)
            viewModelError.emit(null)
        }
    }

    fun clearPackagesCache(){
        viewModelScope.launch {
            processManagerService.clearPackagesCache()
            showNotification(AppNotification.ClearedPackageCache)
        }
    }

    private fun scheduleJob(context: Context) {
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val builder = JobInfo.Builder(123, ComponentName(context, FileObserverJobService::class.java))
        // configure your job (e.g., network constraints)
        jobScheduler.schedule(builder.build())
        Timber.d("FileObserverJobService restarted")
    }

    private fun cancelJob(context: Context) {
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(123)
        Timber.d("FileObserverJobService stopped")
    }


    fun startMCPServer(){

        val mcpPath = File(autoPieConfigPathProvider.getBinDirectory(), "mcp_server").absolutePath
        val modulePath = File(autoPieConfigPathProvider.getAutoSecDirectory(), "mcp_modules").absolutePath
        val host = "0.0.0.0"
        val port  = "8000"

        viewModelScope.launch(dispatchers.io){
            try {
                processManagerService.startMCPServer(mcpPath, modulePath, host, port)
            }
            catch (e: Exception){
                showNotification(AppNotification.MCPServerStartError)
                mcpServerActive = false
            }
        }

        mcpServerActive = true

        viewModelScope.launch {
            delay(1520L)
            showNotification(AppNotification.MCPServerStarted(host, port))
        }


    }

    fun stopMCPServer(){
        viewModelScope.launch(dispatchers.io){
            try {
                processManagerService.stopMCPServer()
                Timber.d("MCP Server Stopped")
                withContext(dispatchers.main) {
                    mcpServerActive = false
                    showNotification(AppNotification.MCPServerStopped)
                }
            }
            catch (e: Exception){

                showError(ViewModelError.Unknown)
            }
        }
        }

    fun checkForUpdates(){
        viewModelScope.launch(dispatchers.io){
            try {
                val latest = GithubApiService.getLatestRelease() ?: return@launch

                Timber.d(latest.toString())

                val tag_name = latest.tag_name.removePrefix("v")

                if(GithubApiService.compareVersions(tag_name, BuildConfig.VERSION_NAME) > 0){

                    Timber.d("Updates are available")

                    updatesAreAvailable = true

                    updateDetails = latest

                    GithubApiService.getAarch64ApkUrl(latest)?.let{
                        showNotification(AppNotification.UpdatesAvailable(url = it))
                    }

                }
                else{
                    Timber.d("No Updates are available")

                    updatesAreAvailable = false
                }
            }catch (e: Exception){
                Timber.e(e)
            }
        }
    }

    fun checkForPackageUpdates(){
        viewModelScope.launch(dispatchers.io){
            try {
                Timber.d("Checking for package updates")

                if(autoPieConfigPathProvider.usesExternalStorage() && !storageManagerPermissionGranted){
                    Timber.d("Storage permission not granted")

                    return@launch
                }

                val autoSecFolder = autoPieConfigPathProvider.getAutoSecDirectory()

                val installedVersionText = File(autoSecFolder, "version.txt").readText()

                val currentVersionText = AutoPieConstants.AUTOPIE_INIT_ARCHIVE_URL.split("/").takeLast(2).joinToString("/")

                Timber.d("installedVersionText: $installedVersionText , currentVersionText: $currentVersionText")

                delay(3000L)

                if(installedVersionText != currentVersionText) {
                    packageUpdatesAreAvailable = true
                    Timber.d("Package updates available")
                    showNotification(AppNotification.PackageUpdatesAvailable(url = AutoPieConstants.AUTOPIE_PACKAGE_UPDATES_README_URL))
                }else{
                    Timber.d("No package updates available")
                    packageUpdatesAreAvailable = false
                }


            }catch (e: Exception){
                Timber.e(e)
            }
        }
    }



}
