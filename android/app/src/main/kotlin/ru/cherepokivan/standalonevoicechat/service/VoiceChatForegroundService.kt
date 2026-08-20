package ru.cherepokivan.standalonevoicechat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import ru.cherepokivan.standalonevoicechat.MainActivity
import ru.cherepokivan.standalonevoicechat.R
import ru.cherepokivan.standalonevoicechat.audio.AndroidAudioEngine
import ru.cherepokivan.standalonevoicechat.audio.AudioRoutingController

/**
 * Visible foreground service for user-approved microphone operation. It remains independent from the
 * fail-closed protocol adapter and does not claim to be connected to a server.
 */
class VoiceChatForegroundService : Service() {
    private var audioEngine: AndroidAudioEngine? = null
    private var routing: AudioRoutingController? = null
    private var muted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MUTE -> {
                muted = !muted
                updateNotification()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startVoiceService()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        audioEngine?.close()
        routing?.releaseVoiceRoute()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startVoiceService() {
        createChannel()
        val notification = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        routing = AudioRoutingController(this).also { it.prepareVoiceRoute() }
        audioEngine = AndroidAudioEngine(this).also { engine ->
            engine.startCapture {
                // Capture is intentionally local only until an upstream-supported bootstrap and adapter exist.
            }
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.voice_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.voice_channel_description)
            }
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Simple Voice Chat")
        .setContentText(if (muted) "Microphone muted" else "Foreground voice service active")
        .setOngoing(true)
        .setContentIntent(PendingIntentCompat.getActivity(this, 0, Intent(this, MainActivity::class.java), 0, false))
        .addAction(0, if (muted) "Unmute" else "Mute", actionIntent(ACTION_MUTE, 1))
        .addAction(0, "Disconnect", actionIntent(ACTION_DISCONNECT, 2))
        .build()

    private fun actionIntent(action: String, requestCode: Int) = PendingIntentCompat.getService(
        this,
        requestCode,
        Intent(this, VoiceChatForegroundService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT,
        false
    )

    companion object {
        const val ACTION_START = "ru.cherepokivan.standalonevoicechat.action.START"
        const val ACTION_MUTE = "ru.cherepokivan.standalonevoicechat.action.MUTE"
        const val ACTION_DISCONNECT = "ru.cherepokivan.standalonevoicechat.action.DISCONNECT"
        private const val CHANNEL_ID = "voice_chat"
        private const val NOTIFICATION_ID = 1001
    }
}
