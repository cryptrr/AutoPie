package com.autosec.pie.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autosec.pie.domain.ViewModelEvent
import com.autosec.pie.notifications.AutoPieNotification
import com.autosec.pie.viewModels.MainViewModel
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber


class ProcessBroadcastReceiver : BroadcastReceiver() {

    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    val main: MainViewModel by inject(MainViewModel::class.java)


    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        //Timber.d("Intent Received: $intent")


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
        }
    }

}