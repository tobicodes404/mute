package com.tobicodes.mute.core.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.tobicodes.mute.R
import com.tobicodes.mute.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "mute_voice_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.tobicodes.mute.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.tobicodes.mute.ACTION_PREV"
        const val ACTION_NEXT = "com.tobicodes.mute.ACTION_NEXT"
        const val ACTION_STOP = "com.tobicodes.mute.ACTION_STOP"
    }

    private val binder = VoiceBinder()
    private var mediaSession: MediaSessionCompat? = null
    private var _state = MutableStateFlow(TtsManager.State.IDLE)
    val state: StateFlow<TtsManager.State> = _state

    inner class VoiceBinder : Binder() {
        fun getService(): VoiceService = this@VoiceService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> notifyToggle()
            ACTION_PREV -> notifyPrev()
            ACTION_NEXT -> notifyNext()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun notifyToggle() = sendBroadcast(Intent("mute.voice.toggle"))
    private fun notifyPrev() = sendBroadcast(Intent("mute.voice.prev"))
    private fun notifyNext() = sendBroadcast(Intent("mute.voice.next"))

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Reading",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for voice reading"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MuteSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = notifyToggle()
                override fun onPause() = notifyToggle()
                override fun onSkipToPrevious() = notifyPrev()
                override fun onSkipToNext() = notifyNext()
                override fun onStop() = stopSelf()
            })
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    fun updateNotification(
        state: TtsManager.State,
        title: String,
        sentencePreview: String,
        progress: String
    ) {
        try {
        _state.value = state
        
        val isPlaying = state == TtsManager.State.PLAYING
        
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseAction = NotificationCompat.Action(
            playPauseIcon, "Play/Pause",
            actionIntent(ACTION_PLAY_PAUSE)
        )
        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous",
            actionIntent(ACTION_PREV)
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            actionIntent(ACTION_NEXT)
        )
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop",
            actionIntent(ACTION_STOP)
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(sentencePreview)
            .setSubText(progress)
            .setSmallIcon(R.drawable.ic_library)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()

        startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Silently fail — don't crash the app if notification fails
            e.printStackTrace()
        }
    }

    private fun actionIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, VoiceService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }
}
