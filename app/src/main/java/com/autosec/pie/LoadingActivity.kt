package com.autopi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autopi.ui.theme.AutoPieTheme
import java.lang.ref.WeakReference

class LoadingActivity : ComponentActivity() {

    private var processId: Int? = null

    companion object {
        private const val EXTRA_PROCESS_ID = "process_id"
        private val lock = Any()
        private val launchedProcessIds = mutableSetOf<Int>()
        private val pendingDismissals = mutableSetOf<Int>()
        private val activeActivities = mutableMapOf<Int, WeakReference<LoadingActivity>>()

        fun start(context: Context, processId: Int) {
            synchronized(lock) {
                launchedProcessIds.add(processId)
                pendingDismissals.remove(processId)
            }
            val intent = Intent(context, LoadingActivity::class.java).apply {
                putExtra(EXTRA_PROCESS_ID, processId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun dismiss(processId: Int) {
            val activity = synchronized(lock) {
                if (!launchedProcessIds.remove(processId)) {
                    return
                }
                activeActivities.remove(processId)?.get().also {
                    if (it == null) {
                        // The command may finish before startActivity reaches onCreate.
                        pendingDismissals.add(processId)
                    }
                }
            }
            activity?.runOnUiThread { activity.finish() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        processId = intent.getIntExtra(EXTRA_PROCESS_ID, -1).takeIf { it >= 0 }
        val shouldDismiss = processId?.let { id ->
            synchronized(lock) {
                if (pendingDismissals.remove(id)) {
                    true
                } else {
                    activeActivities[id] = WeakReference(this)
                    false
                }
            }
        } ?: false
        if (shouldDismiss) {
            finish()
            return
        }

        setContent {
            AutoPieTheme() {
                Surface {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(90.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        processId?.let { id ->
            synchronized(lock) {
                if (activeActivities[id]?.get() === this) {
                    activeActivities.remove(id)
                }
                if (!isChangingConfigurations) {
                    launchedProcessIds.remove(id)
                    pendingDismissals.remove(id)
                }
            }
        }
        super.onDestroy()
    }
}
