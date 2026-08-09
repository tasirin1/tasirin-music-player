package com.tasirin.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Service latar: notifikasi media + sinkronisasi MediaSession (headset/lock screen/Android Auto). */
class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIF_ID = 1

        private const val ACTION_TOGGLE = "com.tasirin.musicplayer.TOGGLE"
        private const val ACTION_NEXT = "com.tasirin.musicplayer.NEXT"
        private const val ACTION_PREV = "com.tasirin.musicplayer.PREV"
        private const val ACTION_STOP = "com.tasirin.musicplayer.STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notifJob: Job? = null
    private var stateJob: Job? = null
    private var queueJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        MediaSessionManager.init(this)
        // Notifikasi + metadata: cukup saat lagu/status berubah (bukan tiap detik).
        notifJob = scope.launch {
            combine(PlayerController.currentTrack, PlayerController.playing) { t, p -> t to p }
                .debounce(300)
                .collect { (track, isPlaying) ->
                    if (track == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collect
                    }
                    val art = withContext(Dispatchers.IO) { ArtCache.loadSync(track.path) }
                    val notification = buildNotification(track, isPlaying, art)
                    if (isPlaying) startForeground(NOTIF_ID, notification)
                    else getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
                    MediaSessionManager.updateMetadata(track, art)
                }
        }
        // Posisi lock screen/Android Auto: sinkron terus (ringan, tanpa rebuild notifikasi).
        stateJob = scope.launch {
            combine(PlayerController.playing, PlayerController.positionMs) { p, pos -> p to pos }
                .collect { (isPlaying, pos) -> MediaSessionManager.updateState(isPlaying, pos) }
        }
        queueJob = scope.launch {
            PlayerController.queue.debounce(500).collect { MediaSessionManager.updateQueue(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Intent.ACTION_MEDIA_BUTTON -> {
                MediaSessionManager.session()?.let { MediaButtonReceiver.handleIntent(it, intent) }
            }
            ACTION_TOGGLE -> PlayerController.toggle()
            ACTION_NEXT -> PlayerController.next()
            ACTION_PREV -> PlayerController.prev()
            ACTION_STOP -> {
                PlayerController.releaseAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (PlayerController.playing.value) {
            PlayerController.currentTrack.value?.let {
                startForeground(NOTIF_ID, buildNotification(it, true, null))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notifJob?.cancel()
        stateJob?.cancel()
        queueJob?.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Pemutaran Musik", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Kontrol pemutaran saat app di latar" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(track: Track, isPlaying: Boolean, art: Bitmap?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val session = MediaSessionManager.session()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(track.title)
            .setContentText(track.artist.ifBlank { track.album })
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "Sebelumnya", pendingAction(ACTION_PREV)
            ))
            .addAction(NotificationCompat.Action(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Jeda" else "Putar", pendingAction(ACTION_TOGGLE)
            ))
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_next, "Berikutnya", pendingAction(ACTION_NEXT)
            ))
        art?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun pendingAction(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(), Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )
}
