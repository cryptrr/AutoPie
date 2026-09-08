package com.autopi.autopieapp.data.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import com.autopi.R
import com.autopi.autopieapp.data.JobType
import com.autopi.autopieapp.domain.ViewModelEvent
import com.autopi.autopieapp.data.services.notifications.AutoPieNotification
import com.autopi.autopieapp.presentation.viewModels.MainViewModel
import com.autopi.core.DispatcherProvider
import com.autopi.use_case.AutoPieUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.io.File
import kotlin.system.exitProcess

class ForegroundService : Service() {
    private val mainViewModel: MainViewModel by inject(MainViewModel::class.java)
    private val dispatchers: DispatcherProvider by inject(DispatcherProvider::class.java)
    private val useCases: AutoPieUseCases by inject(AutoPieUseCases::class.java)
    private val autoPieNotification: AutoPieNotification by inject(AutoPieNotification::class.java)
    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.main) }

    // Accessed only on the main dispatcher. One process may have consecutive multistage requests.
    private val runs = mutableMapOf<Job, Int>()
    private var latestStartId = 0
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        val intent = Intent(this, ProcessBroadcastReceiver::class.java).apply {
            action = "${this@ForegroundService.packageName}.CANCEL_ALL_PROCESSES"
        }
        val cancelIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AutoPieNotification.FOREGROUND_CHANNEL)
            .setContentTitle("AutoPie Running")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, "Cancel", cancelIntent)
            .build()
        startForeground((100000..999999).random(), notification)

        serviceScope.launch {
            mainViewModel.eventFlow.collect { event ->
                when (event) {
                    is ViewModelEvent.CommandStarted -> {
                        if (event.processId in runs.values && event.jobType != JobType.CRON &&
                            event.jobType != JobType.STANDALONE) {
                            notifySafely {
                                autoPieNotification.sendBroadcastNotification(
                                    event.command.name, event.input, event.command, event.processId,
                                    logFile = event.logFile
                                )
                            }
                        }
                    }
                    is ViewModelEvent.CancelProcess -> cancelRuns(event.processId)
                    is ViewModelEvent.CommandStoppedByUser -> cancelRuns(event.processId)
                    is ViewModelEvent.CancelAllProcesses -> runs.keys.toList().forEach { it.cancel() }
                    is ViewModelEvent.StopAutoPie -> {
                        autoPieNotification.cancelAllNotifications()
                        Process.killProcess(Process.myPid())
                        exitProcess(0)
                    }
                    // A completion event is per file/step, not completion of the collected flow.
                    else -> Unit
                }
            }
        }
    }

    private fun cancelRuns(processId: Int) {
        runs.filterValues { it == processId }.keys.toList().forEach { it.cancel() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        val processId = intent.getIntExtra("processId", (100000..999999).random())
        val logPath = File(application.cacheDir, "${processId}.log").absolutePath
        val request = try {
            parseShareCommandRequest(
                intent.getStringExtra("command"), intent.getStringExtra("inputText"),
                intent.getStringExtra("inputFiles"), intent.getStringExtra("commandExtraInputs")
            )
        } catch (error: Exception) {
            Timber.e(error, "Invalid SHARE command request")
            notifySafely {
                autoPieNotification.sendNotification("Command Failed", error.message.orEmpty(), null, logPath, processId)
            }
            stopIfIdle()
            return START_NOT_STICKY
        }

        lateinit var run: Job
        run = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(dispatchers.io) {
                    var keepMultistageShell = false
                    try {
                        useCases.runCommand(
                            request.command, request.inputText, request.inputFiles, request.extras, processId
                        ).collect { receipt ->
                            currentCoroutineContext().ensureActive()
                            keepMultistageShell = receipt.success && receipt.partial
                            notifySafely {
                                autoPieNotification.sendNotification(
                                    if (receipt.success) "Command Success" else "Command Failed",
                                    "${request.command.name} ${receipt.jobKey}", request.command, logPath, processId
                                )
                            }
                        }
                    } catch (error: CancellationException) {
                        keepMultistageShell = false
                        throw error
                    } catch (error: Exception) {
                        keepMultistageShell = false
                        Timber.e(error, "SHARE command failed")
                        notifySafely {
                            autoPieNotification.sendNotification(
                                "Command Failed", "${request.command.name} ${error.message}",
                                request.command, logPath, processId
                            )
                        }
                    } finally {
                        if (request.command.multiStage == true && !keepMultistageShell) {
                            mainViewModel.dispatchEvent(ViewModelEvent.StopShell(processId))
                        }
                    }
                }
            } finally {
                runs.remove(run)
                if (processId !in runs.values) {
                    notifySafely { autoPieNotification.cancelNotification(processId) }
                }
                stopIfIdle()
            }
        }
        runs[run] = processId
        run.start()
        return START_NOT_STICKY
    }

    private fun stopIfIdle() {
        if (!destroyed && runs.isEmpty()) stopSelfResult(latestStartId)
    }

    private inline fun notifySafely(block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            Timber.e(error, "Unable to update SHARE notification")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        runs.values.toSet().forEach { mainViewModel.dispatchEvent(ViewModelEvent.CancelProcess(it)) }
        serviceScope.cancel()
        runs.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
