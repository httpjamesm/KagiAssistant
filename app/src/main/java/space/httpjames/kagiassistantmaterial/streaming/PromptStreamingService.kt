package space.httpjames.kagiassistantmaterial.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.MainActivity
import space.httpjames.kagiassistantmaterial.R

class PromptStreamingService : Service() {

    companion object {
        const val ACTION_START_STREAM = "ACTION_START_STREAM"
        const val ACTION_CANCEL_STREAM = "ACTION_CANCEL_STREAM"
        const val EXTRA_STREAM_ID = "EXTRA_STREAM_ID"

        private const val CHANNEL_ID = "prompt_streaming_channel"
        private const val COMPLETION_CHANNEL_ID = "prompt_completion_channel"
        private const val NOTIFICATION_ID = 1
        const val COMPLETION_NOTIFICATION_ID = 2
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monitorJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildTypingNotification(1))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val streamId = intent?.getStringExtra(EXTRA_STREAM_ID)

        when (intent?.action) {
            ACTION_START_STREAM -> {
                if (streamId != null) {
                    val request = StreamingSessionManager.pendingRequests.remove(streamId)
                    if (request != null) {
                        StreamingSessionManager.startStream(streamId, request)
                        monitorStream(streamId)
                        updateTypingNotification()
                    }
                }
            }

            ACTION_CANCEL_STREAM -> {
                if (streamId != null) {
                    StreamingSessionManager.cancelStream(streamId)
                    monitorJobs[streamId]?.cancel()
                    monitorJobs.remove(streamId)
                    onStreamFinished(streamId, cancelled = true)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun monitorStream(streamId: String) {
        val job = serviceScope.launch {
            val flow = StreamingSessionManager.getStream(streamId)
            flow.collect { chunk ->
                if (chunk.done) {
                    onStreamFinished(streamId, cancelled = false)
                    return@collect
                }
            }
        }
        monitorJobs[streamId] = job
    }

    private fun onStreamFinished(streamId: String, cancelled: Boolean) {
        monitorJobs[streamId]?.cancel()
        monitorJobs.remove(streamId)

        if (!cancelled && !StreamingSessionManager.isAppInForeground) {
            showCompletionNotification(streamId)
        }

        StreamingSessionManager.onStreamFinished(streamId)

        if (!StreamingSessionManager.hasActiveStreams() && monitorJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateTypingNotification()
        }
    }

    private fun showCompletionNotification(streamId: String) {
        val metadata = StreamingSessionManager.streamMetadata[streamId] ?: return
        val title = metadata.threadTitle ?: "Kagi Assistant"
        val responseText = metadata.lastResponseText
        if (responseText.isNullOrBlank()) return

        val truncated = if (responseText.length > 200) {
            responseText.take(200) + "\u2026"
        } else {
            responseText
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(truncated)
            .setStyle(NotificationCompat.BigTextStyle().bigText(truncated))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun updateTypingNotification() {
        val count = StreamingSessionManager.activeStreamCount()
        if (count > 0) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildTypingNotification(count))
        }
    }

    private fun buildTypingNotification(streamCount: Int): Notification {
        val text = if (streamCount <= 1) {
            "Assistant is typing\u2026"
        } else {
            "Assistant is typing ($streamCount threads)\u2026"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kagi Assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val typingChannel = NotificationChannel(
                CHANNEL_ID,
                "Response Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Kagi Assistant is generating a response"
                setShowBadge(false)
            }
            manager.createNotificationChannel(typingChannel)

            val completionChannel = NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Response Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows when Kagi Assistant has finished generating a response"
            }
            manager.createNotificationChannel(completionChannel)
        }
    }
}
