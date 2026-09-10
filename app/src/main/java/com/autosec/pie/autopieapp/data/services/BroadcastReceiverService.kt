package com.autopi.autopieapp.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber


class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_SCREEN_ON == intent.action) {
            // Screen is on, schedule the job
            Timber.d("Screen On")

            //scheduleJob(context)
        } else if (Intent.ACTION_SCREEN_OFF == intent.action) {
            // Screen is off, cancel the job
            Timber.d("Screen off")

            //cancelJob(context)
        }
    }

    private fun scheduleJob(context: Context) {
        FileObserverScheduler.schedule(context)
    }

    private fun cancelJob(context: Context) {
        FileObserverScheduler.cancel(context)
    }
}
