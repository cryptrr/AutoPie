package com.autosec.pie.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewModelScope
import com.autosec.pie.R
import com.autosec.pie.data.CommandExtraInput
import com.autosec.pie.data.CommandModel
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.notifications.AutoPieNotification
import com.autosec.pie.viewModels.ShareReceiverViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber

class ForegroundService : Service() {

    private val shareReceiverViewModel: ShareReceiverViewModel by inject(ShareReceiverViewModel::class.java)

    private var notificationManager: NotificationManager? = null

    private var processIds : List<Int> = emptyList()

    private var foregroundServiceId : Int? = null

    init {
        shareReceiverViewModel.viewModelScope.launch {
            shareReceiverViewModel.main.eventFlow.collect{
                when(it){
                    is ViewModelEvent.CommandCompleted -> {
                        try {
                            //Remove from the current running processIds list
                            processIds = processIds.filter {item -> item !=  it.processId}

                            Timber.d("ProcessIds at completion of command: $processIds")

                            //Close if no processes remain in the list
                            if(processIds.isEmpty()){
                                Timber.d("All processed completed")
                                onDestroy()
                            }
                        }catch (e: Exception){
                            Timber.e(e)
                        }
                    }
                    is ViewModelEvent.CancelProcess -> {
                        if(processIds.contains(it.processId)){
                            Timber.d("Canceling process ${it.processId}")
                            try {

                                //currentJob?.cancel()
                            }catch (e: Exception){
                                Timber.e(e)
                            }
                        }else{
                            Timber.d("Process Ids not same.")
                        }
                    }
                    else -> {}
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        // Create a notification channel for API 26+

        val foregroundServiceId = (100000..999999).random()


        Timber.d("ForegroundService created with id: $foregroundServiceId")


        val intent = Intent(this, ProcessBroadcastReceiver::class.java).apply {
            action = "${this@ForegroundService.packageName}.CANCEL_PROCESS"
            putExtra("processId", foregroundServiceId)
        }

        val pendingButtonIntent: PendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val notification = NotificationCompat.Builder(this, AutoPieNotification.FOREGROUND_CHANNEL)
            .setContentTitle("AutoPie Running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(
                R.mipmap.ic_launcher,
                "Cancel",
                pendingButtonIntent
            )
            .build()

        startForeground(foregroundServiceId, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {

            CoroutineScope(Dispatchers.IO).launch {

                try {
                    val processId = (100000..999999).random()

                    processIds = processIds + processId

                    Timber.d("ProcessIds at starting command: $processIds")

                    val commandString = it.getStringExtra("command")
                    val currentLink = it.getStringExtra("currentLink")
                    val fileUrisString = it.getStringExtra("fileUris")
                    val commandExtraInputsString = it.getStringExtra("commandExtraInputs")

                    val listType = object : TypeToken<List<String>>() {}.type
                    val commandExtraInputListType = object : TypeToken<List<CommandExtraInput>>() {}.type

                    val command: CommandModel = Gson().fromJson(commandString, CommandModel::class.java)

                    val fileUris: List<String> = Gson().fromJson(fileUrisString, listType)

                    val commandExtraInputs: List<CommandExtraInput> = try {
                        Gson().fromJson(commandExtraInputsString, commandExtraInputListType)
                    }catch (e: Exception){
                        emptyList()
                    }

                    shareReceiverViewModel.runShareCommand(command, currentLink, fileUris, commandExtraInputs, processId)
                }catch (e: Exception){
                    Timber.e(e)
                }
            }

        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onDestroy()
    }
}