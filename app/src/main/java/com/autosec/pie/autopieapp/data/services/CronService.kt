package com.autosec.pie.autopieapp.data.services

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.autosec.pie.autopieapp.data.CronCommandModel
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.utils.Utils
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import com.autosec.pie.core.DispatcherProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class CronService(private val jsonService: JsonService){


    private val main: MainViewModel by inject(MainViewModel::class.java)
    private val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)



    init {
        try {
            main.viewModelScope.launch {
                main.eventFlow.collect{
                    when(it){
                        is ViewModelEvent.CronConfigChanged -> {
                            Timber.d("Cron config changed: Restarting")
                            setUpChronJobs()
                        }
                        else -> {}
                    }
                }
            }
        }catch (e: Exception){
            Timber.e(e)
        }
    }

    private val activity: Application by inject(Context::class.java)

    fun testCronJob(){

        Timber.d("TESTING CRON JOBS")

        CoroutineScope(dispatchers.default).launch{

            Timber.d("DELAYING FOR 10s")
            delay(10*1000)
            Timber.d("OFF YOU GO")

            val cronConfig = try {
                jsonService.readCronConfig()
            }catch (e: Exception){
                Timber.e(e)
                return@launch
            }

            if (cronConfig == null) {
                Timber.d("cron file not available")
                main.schedulerConfigAvailable = false
                return@launch
            } else {
                Timber.d("cron file is available")
                main.schedulerConfigAvailable = true
            }

            val mapType = object : TypeToken<Map<String, CronCommandModel>>() {}.type

            val data: Map<String, CronCommandModel> = Gson().fromJson(cronConfig, mapType)

            for(cronJob in data.entries){

                val commandJson = Gson().toJson(cronJob.value)

                Timber.d("Cron Command Starting: ${cronJob.key}")

                val inputData = Data.Builder()
                    .putString("commandKey", cronJob.key)
                    .putString("command", commandJson)
                    .build()

                val timeInterval = Utils.parseTimeInterval(cronJob.value.cronInterval) ?: continue

                Timber.d("Cron Interval: $timeInterval")


                val request = OneTimeWorkRequestBuilder<CronJobWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(activity).enqueue(request)

            }
        }

    }


    fun setUpChronJobs(){

        if(!main.storageManagerPermissionGranted){
            return
        }

        Timber.d("Setting up cron jobs if any")

        CoroutineScope(dispatchers.default).launch {
            val cronConfig = try {
                jsonService.readCronConfig()
            }catch (e: Exception){
                Timber.e(e)
                return@launch
            }

            if (cronConfig == null) {
                Timber.d("cron file not available")
                main.schedulerConfigAvailable = false
                return@launch
            } else {
                Timber.d("cron file is available")
                main.schedulerConfigAvailable = true
            }

            val mapType = object : TypeToken<Map<String, CronCommandModel>>() {}.type

            val data: Map<String, CronCommandModel> = Gson().fromJson(cronConfig, mapType)

            for(cronJob in data.entries){

                //Tentative fix for cron path

                cronJob.value.path = File(Environment.getExternalStorageDirectory().absolutePath,cronJob.value.path).absolutePath

                val commandJson = Gson().toJson(cronJob.value)

                Timber.d("Cron Command Starting: ${cronJob.key}")

                val inputData = Data.Builder()
                    .putString("command", commandJson)
                    .build()

                val timeInterval = Utils.parseTimeInterval(cronJob.value.cronInterval) ?: continue

                Timber.d("Cron Interval: $timeInterval")

                val workRequest = if(Utils.checkIfIntervalLessThan15Minutes(timeInterval)){
                    Timber.d("Cron Interval less than Android minimum. So using the allowed minimum of 15 MINUTES")
                    PeriodicWorkRequestBuilder<CronJobWorker>(15, TimeUnit.MINUTES)
                        .setInputData(inputData)
                        .build()
                }else{
                    PeriodicWorkRequestBuilder<CronJobWorker>(timeInterval.first, timeInterval.second)
                        .setInputData(inputData)
                        .build()
                }

                WorkManager.getInstance(activity).enqueueUniquePeriodicWork(cronJob.key, ExistingPeriodicWorkPolicy.UPDATE ,workRequest)

            }
        }

    }
}