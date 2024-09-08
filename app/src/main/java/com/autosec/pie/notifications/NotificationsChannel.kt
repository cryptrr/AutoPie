package com.autosec.pie.notifications

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.autosec.pie.MainActivity
import com.autosec.pie.R
import timber.log.Timber
import java.io.File

class AutoPieNotification(val context: Application){

    fun createNotificationChannel() {
        val channelId = "100"
        val channelName = "autopie_main"
        val channelDescription = "Command Notifications"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun sendNotification(contentTitle: String, contentText: String, ) {
        val channelId = "100"
        val notificationId = System.currentTimeMillis().toInt()

        Timber.d("Sending notification")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val file = File("/storage/emulated/0/AutoSec/logs/autopie.log")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val openFileIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/plain")  // Adjust MIME type
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION  // Grant permission to the app
        }

        val pendingButtonIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            openFileIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setSilent(true)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.mipmap.ic_launcher,
                "Open Logs",
                pendingButtonIntent
            )

        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                //requestNotificationPermission()

                return
            }
            notify(notificationId, builder.build())
        }
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS

            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the permission
                ActivityCompat.requestPermissions(activity, arrayOf(permission), 1)
            }
        }


    }
}