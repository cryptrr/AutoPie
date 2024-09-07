package com.autosec.pie.services

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.autosec.pie.data.CronCommandModel
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.utils.Utils
import com.autosec.pie.viewModels.MainViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CronService {


    companion object{

        private val main: MainViewModel by inject(MainViewModel::class.java)


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


        fun setUpChronJobs(){

            Timber.d("Setting up cron jobs if any")

            CoroutineScope(Dispatchers.Default).launch {
                val cronConfig = JSONService.readCronConfig()

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
}