package com.autosec.pie.services

import android.app.Application
import android.content.Context
import androidx.work.Data
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.autosec.pie.data.CommandModel
import com.autosec.pie.data.CronCommandModel
import com.autosec.pie.services.FileObserverJobService.DirectoryFileObserver
import com.autosec.pie.utils.Utils
import com.autosec.pie.viewModels.MainViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CronService {

    companion object{

        private val mainViewModel: MainViewModel by inject(MainViewModel::class.java)
        private val activity: Application by inject(Context::class.java)


        fun setUpChronJobs(){

            Timber.d("Setting up cron jobs if any")

            CoroutineScope(Dispatchers.Default).launch {
                val cronConfig = JSONService.readCronConfig()

                if (cronConfig == null) {
                    Timber.d("cron file not available")
                    mainViewModel.schedulerConfigAvailable = false
                    return@launch
                } else {
                    Timber.d("cron file is available")
                    mainViewModel.schedulerConfigAvailable = true
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

                    WorkManager.getInstance(activity).enqueue(workRequest)


                }

            }

        }
    }
}