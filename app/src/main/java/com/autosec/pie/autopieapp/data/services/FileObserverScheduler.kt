package com.autopi.autopieapp.data.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import timber.log.Timber

/** Owns the single, stable JobScheduler identity and configuration for file observers. */
object FileObserverScheduler {
    const val JOB_ID = 123

    fun schedule(context: Context): Boolean {
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, FileObserverJobService::class.java)
        )
            .setPersisted(true)
            .setRequiresCharging(false)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setRequiresDeviceIdle(false)
            .build()

        val scheduler = context.getSystemService(JobScheduler::class.java)
        val scheduled = scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        if (scheduled) {
            Timber.d("FileObserverJobService scheduled (jobId=$JOB_ID)")
        } else {
            Timber.e("Unable to schedule FileObserverJobService (jobId=$JOB_ID)")
        }
        return scheduled
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        Timber.d("FileObserverJobService cancelled (jobId=$JOB_ID)")
    }
}
