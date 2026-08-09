package com.tasirin.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

/** Pemilik tunggal MediaSession: kontrol headset, lock screen, dan Android Auto. */
object MediaSessionManager {

    private const val QUEUE_TITLE = "Tasirin Musik"

    private var ctx: Context? = null
    private var session: MediaSessionCompat? = null
    private var lastPath: String? = null

    fun init(context: Context) {
        if (session != null) return
        ctx = context.applicationContext
        val s = MediaSessionCompat(context, "TasirinMusik").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = PlayerController.resume()
                override fun onPause() = PlayerController.pausePlayback()
                override fun onStop() = PlayerController.pausePlayback()
                override fun onSkipToNext() = PlayerController.next()
                override fun onSkipToPrevious() = PlayerController.prev()
                override fun onSeekTo(pos: Long) = PlayerController.seekTo(pos)
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    val track = mediaId?.takeIf { it.isNotBlank() }?.let { resolveTrack(it) }
                        ?: return
                    PlayerController.play(listOf(track), 0)
                }
            })
            isActive = true
        }
        session = s
        updateQueue(PlayerController.queue.value)
    }

    fun session(): MediaSessionCompat? = session

    fun updateMetadata(track: Track, art: Bitmap?) {
        val s = session ?: return
        if (lastPath == track.path) return
        lastPath = track.path
        s.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                .apply { art?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
    }

    fun updateState(isPlaying: Boolean, pos: Long) {
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    pos, 1f
                )
                .build()
        )
    }

    fun updateQueue(list: List<Track>) {
        val s = session ?: return
        s.setQueueTitle(QUEUE_TITLE)
        s.setQueue(
            list.mapIndexed { i, t ->
                MediaSessionCompat.QueueItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId(t.path)
                        .setTitle(t.title)
                        .setSubtitle(t.artist.ifBlank { t.album })
                        .setDescription(t.album.ifBlank { t.artist })
                        .build(),
                    i.toLong()
                )
            }
        )
    }

    fun release() {
        session?.release()
        session = null
        lastPath = null
    }

    private fun resolveTrack(mediaId: String): Track? {
        PlayerController.queue.value.firstOrNull { it.path == mediaId }?.let { return it }
        val c = ctx ?: return null
        return MediaLibrary.findByPath(c, mediaId)
    }
}
