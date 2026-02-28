package space.httpjames.kagiassistantmaterial.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import space.httpjames.kagiassistantmaterial.R

class PromptStreamingService : Service() {

    companion object {
        const val ACTION_START_STREAM = "ACTION_START_STREAM"
        const val ACTION_CANCEL_STREAM = "ACTION_CANCEL_STREAM"
        const val EXTRA_STREAM_ID = "EXTRA_STREAM_ID"

        private const val CHANNEL_ID = "prompt_streaming_channel"
        private const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monitorJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(1))
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
                        updateNotification()
                    }
                }
            }

            ACTION_CANCEL_STREAM -> {
                if (streamId != null) {
                    StreamingSessionManager.cancelStream(streamId)
                    monitorJobs[streamId]?.cancel()
                    monitorJobs.remove(streamId)
                    onStreamFinished(streamId)
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
                    onStreamFinished(streamId)
                    return@collect
                }
            }
        }
        monitorJobs[streamId] = job
    }

    private fun onStreamFinished(streamId: String) {
        monitorJobs[streamId]?.cancel()
        monitorJobs.remove(streamId)
        StreamingSessionManager.onStreamFinished(streamId)

        if (!StreamingSessionManager.hasActiveStreams() && monitorJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification()
        }
    }

    private fun updateNotification() {
        val count = StreamingSessionManager.activeStreamCount()
        if (count > 0) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(count))
        }
    }

    private fun buildNotification(streamCount: Int): Notification {
        val text = if (streamCount <= 1) {
            "Generating response\u2026"
        } else {
            "Generating $streamCount responses\u2026"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kagi Assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Response Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Kagi Assistant is generating a response"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
