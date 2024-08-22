package com.autosec.pie.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import timber.log.Timber


class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_SCREEN_ON == intent.action) {
            // Screen is on, schedule the job
            Timber.d("Screen On")

            scheduleJob(context)
        } else if (Intent.ACTION_SCREEN_OFF == intent.action) {
            // Screen is off, cancel the job
            Timber.d("Screen off")

            cancelJob(context)
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
}