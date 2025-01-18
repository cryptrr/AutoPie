package com.autosec.pie.autopieapp.presentation.viewModels

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
import com.autosec.pie.BuildConfig
import com.autosec.pie.core.DispatcherProvider
import com.autosec.pie.autopieapp.data.preferences.AppPreferences
import com.autosec.pie.autopieapp.domain.AppNotification
import com.autosec.pie.autopieapp.domain.Notification
import com.autosec.pie.autopieapp.domain.ViewModelError
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.data.services.FileObserverJobService
import com.autosec.pie.autopieapp.data.services.GithubApiService
import com.autosec.pie.autopieapp.data.services.ProcessManagerService
import com.autosec.pie.autopieapp.data.services.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import timber.log.Timber

class MainViewModel(private val application: Application) : AndroidViewModel(application) {


    private val appPreferences: AppPreferences by KoinJavaComponent.inject(AppPreferences::class.java)

    private val _eventFlow = MutableSharedFlow<ViewModelEvent>(replay = 0)
    val eventFlow = _eventFlow.asSharedFlow()

    var currentCommandKey = mutableStateOf("")

    var viewModelError = MutableSharedFlow<Notification?>(replay = 1)
    var appNotification = MutableSharedFlow<Notification?>(replay = 1)


    var pythonInstallationComplete by mutableStateOf(false)

    var updatesAreAvailable : Boolean? by mutableStateOf(null)

    var updateDetails: ReleaseInfo? by mutableStateOf(null)

    var sharesConfigAvailable by mutableStateOf(false)
    var observerConfigAvailable by mutableStateOf(false)
    var schedulerConfigAvailable by mutableStateOf(false)

    var installInitPackagesPrompt by mutableStateOf(false)


    var turnOffFileObservers by mutableStateOf(appPreferences.getBoolSync(AppPreferences.IS_FILE_OBSERVERS_OFF)).also { state ->
        viewModelScope.launch {
            appPreferences.getBool(AppPreferences.IS_FILE_OBSERVERS_OFF).collectLatest{
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
        viewModelScope.launch {
            viewModelError.emit(error)
        }
    }

    fun toggleFileObservers(){
        viewModelScope.launch {
            if(appPreferences.getBoolSync(AppPreferences.IS_FILE_OBSERVERS_OFF)){
                scheduleJob(application)
                appPreferences.setBool(AppPreferences.IS_FILE_OBSERVERS_OFF, false)

            }else{
                cancelJob(application)
                appPreferences.setBool(AppPreferences.IS_FILE_OBSERVERS_OFF, true)

            }
        }
    }

    fun dispatchEvent(event: ViewModelEvent) {
        Timber.d("Event Fired: $event")
        viewModelScope.launch(Dispatchers.Main) {
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
            ProcessManagerService.clearPackagesCache()
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

    fun checkForUpdates(){
        viewModelScope.launch(Dispatchers.IO){
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



}