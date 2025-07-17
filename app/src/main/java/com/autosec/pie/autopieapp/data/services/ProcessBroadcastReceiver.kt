package com.autosec.pie.autopieapp.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autosec.pie.autopieapp.domain.ViewModelEvent
import com.autosec.pie.autopieapp.data.services.notifications.AutoPieNotification
import com.autosec.pie.autopieapp.presentation.viewModels.MainViewModel
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber


class ProcessBroadcastReceiver : BroadcastReceiver() {

    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    val main: MainViewModel by inject(MainViewModel::class.java)


    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.d("Intent Received: $intent")


        try {
            when(action){
                "${context.packageName}.SHOW_NOTIFICATION" -> {
                    autoPieNotification.sendBroadcastNotification(intent, context)
                }
                "${context.packageName}.CANCEL_NOTIFICATION" -> {
                    autoPieNotification.cancelNotification(intent, context)
                }
                "${context.packageName}.CANCEL_PROCESS" -> {

                    Timber.d("${context.packageName}.CANCEL_PROCESS")

                    val processId = intent.getIntExtra("processId", 0)

                    main.dispatchEvent(ViewModelEvent.CancelProcess(processId))

                }
                "${context.packageName}.STOP_AUTOPIE" -> {

                    Timber.d("${context.packageName}.STOP_AUTOPIE")

                    main.dispatchEvent(ViewModelEvent.StopAutoPie)

                }
                "${context.packageName}.PLAY_MEDIA" -> {

                    Timber.d("${context.packageName}.PLAY_MEDIA")

                    autoPieNotification.sendMediaReadyNotification(intent, context)

                }
            }
        }catch (e: Exception){
            Timber.e(e)
        }
    }

}