package com.autosec.pie.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.viewModelScope
import com.autosec.pie.R
import com.autosec.pie.data.ShareItemModel
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.viewModels.ShareReceiverViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class ForegroundService : Service() {

    private val shareReceiverViewModel: ShareReceiverViewModel by inject(ShareReceiverViewModel::class.java)

    private var notificationManager: NotificationManager? = null

    init {
        shareReceiverViewModel.viewModelScope.launch {
            shareReceiverViewModel.main.eventFlow.collect{
                when(it){
                    is ViewModelEvent.CommandCompleted -> stopForeground(STOP_FOREGROUND_REMOVE)
                    else -> {}
                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        // Create a notification channel for API 26+
        val channel = NotificationChannel(
            "foreground_channel",
            "Foreground Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        notificationManager = manager

        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, "foreground_channel")
            .setContentTitle("AutoPie Running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(50, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {
            val commandString = it.getStringExtra("command")
            val currentLink = it.getStringExtra("currentLink")
            val fileUrisString = it.getStringExtra("fileUris")

            val listType = object : TypeToken<List<String>>() {}.type

            val command: ShareItemModel = Gson().fromJson(commandString, ShareItemModel::class.java)
            val fileUris: List<String> = Gson().fromJson(fileUrisString, listType)


            shareReceiverViewModel.runShareCommand(command, currentLink, fileUris)

        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationManager?.cancel(50)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}