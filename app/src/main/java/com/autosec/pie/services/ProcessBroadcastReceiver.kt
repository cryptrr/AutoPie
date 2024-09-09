package com.autosec.pie.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import com.autosec.pie.notifications.AutoPieNotification
import org.koin.java.KoinJavaComponent.inject


class ProcessBroadcastReceiver : BroadcastReceiver() {

    private val autoPieNotification: AutoPieNotification by inject(
        AutoPieNotification::class.java)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.d("Intent Received: $intent")

        if (action == "${context.packageName}.SHOW_NOTIFICATION") {

            autoPieNotification.sendBroadcastNotification(intent, context)

        }
    }


}