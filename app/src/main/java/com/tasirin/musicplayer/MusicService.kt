package com.tasirin.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

/** Service latar: notifikasi media + MediaSession, hidup selama pemutaran. */
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
    private var observing: Job? = null
    private lateinit var session: MediaSessionCompat
    private var lastPath: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "TasirinMusik")
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = PlayerController.resume()
            override fun onPause() = PlayerController.pausePlayback()
            override fun onSkipToNext() = PlayerController.next()
            override fun onSkipToPrevious() = PlayerController.prev()
            override fun onSeekTo(pos: Long) = PlayerController.seekTo(pos)
        })
        session.isActive = true
        observing = scope.launch {
            combine(PlayerController.currentTrack, PlayerController.playing, PlayerController.positionMs) { t, p, pos ->
                Triple(t, p, pos)
            }
                .debounce(600)
                .collect { (track, isPlaying, pos) ->
                    if (track == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collect
                    }
                    val art = withContext(Dispatchers.IO) { ArtCache.loadSync(track.path) }
                    val notification = buildNotification(track, isPlaying, art)
                    if (isPlaying) startForeground(NOTIF_ID, notification)
                    else getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
                    updateSession(track, isPlaying, pos, art)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
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
        observing?.cancel()
        session.release()
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(track.title)
            .setContentText(track.artist.ifBlank { track.album })
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
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

    private suspend fun updateSession(track: Track, isPlaying: Boolean, pos: Long, art: Bitmap?) {
        if (lastPath != track.path) {
            lastPath = track.path
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                    .build()
            )
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    pos, 1f
                )
                .build()
        )
    }
}
