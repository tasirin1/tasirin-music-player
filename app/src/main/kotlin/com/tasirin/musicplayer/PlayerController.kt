package com.tasirin.musicplayer

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Kontrol pemutaran (singleton) yang diamati UI dan MusicService.
 * Pemutaran memakai MediaPlayer Android (decode hardware, hemat baterai);
 * pustaka & metadata tetap diolah inti Rust.
 */
object PlayerController {

    enum class RepeatMode { OFF, ALL, ONE }

    val queue = kotlinx.coroutines.flow.MutableStateFlow<List<Track>>(emptyList())
    val currentTrack = kotlinx.coroutines.flow.MutableStateFlow<Track?>(null)
    val playing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val positionMs = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val durationMs = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val shuffle = kotlinx.coroutines.flow.MutableStateFlow(false)
    val repeat = kotlinx.coroutines.flow.MutableStateFlow(RepeatMode.OFF)

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var mp: MediaPlayer? = null
    private var shuffleOrder: List<Int> = emptyList()

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** Mulai putar daftar [list] dari index [start]. */
    fun play(list: List<Track>, start: Int) {
        if (list.isEmpty()) return
        queue.value = list.toList()
        setIndex(start)
        startPlayback()
    }

    fun toggle() {
        if (playing.value) pausePlayback() else resume()
    }

    fun resume() {
        val track = currentTrack.value ?: return
        if (mp == null) {
            startPlayback()
        } else {
            runCatching { mp?.start() }
            playing.value = true
            startTicker()
        }
    }

    fun pausePlayback() {
        playing.value = false
        stopTicker()
        runCatching { mp?.pause() }
    }

    fun next() = skip(1, manual = true)

    fun prev() {
        if (positionMs.value > 3000) seekTo(0) else skip(-1, manual = true)
    }

    fun seekTo(ms: Long) {
        runCatching { mp?.seekTo(ms.toInt().coerceAtLeast(0)) }
        positionMs.value = ms.coerceAtLeast(0)
    }

    fun setShuffle(on: Boolean) {
        shuffle.value = on
        shuffleOrder = if (on) {
            val cur = indexInQueue()
            val rest = queue.value.indices.filter { it != cur }
            listOf(cur) + rest.shuffled()
        } else emptyList()
    }

    fun setRepeat(mode: RepeatMode) {
        repeat.value = mode
        runCatching { mp?.isLooping = mode == RepeatMode.ONE }
    }

    /** Hentikan total (dari notifikasi / aplikasi). */
    fun releaseAll() {
        stopTicker()
        runCatching { mp?.release() }
        mp = null
        playing.value = false
    }

    // ── internal ──

    private fun indexInQueue(): Int = queue.value.indexOfFirst { it.path == currentTrack.value?.path }
        .takeIf { it >= 0 } ?: 0

    private fun setIndex(i: Int) {
        val q = queue.value
        if (q.isEmpty()) return
        val idx = ((i % q.size) + q.size) % q.size
        currentTrack.value = q[idx]
        durationMs.value = q[idx].durationMs
        positionMs.value = 0
    }

    private fun startPlayback() {
        val track = currentTrack.value ?: return
        scope.launch {
            try {
                releasePlayer()
                val p = MediaPlayer()
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                p.setDataSource(track.path)
                p.setOnPreparedListener { prepared ->
                    prepared.start()
                    playing.value = true
                    durationMs.value = prepared.duration.toLong().coerceAtLeast(0L)
                    startTicker()
                    startService()
                }
                p.setOnCompletionListener { onCompleted() }
                p.setOnErrorListener { _, _, _ ->
                    playing.value = false
                    stopTicker()
                    true
                }
                p.prepareAsync()
                mp = p
            } catch (_: Exception) {
                playing.value = false
            }
        }
    }

    private fun releasePlayer() {
        runCatching { mp?.release() }
        mp = null
    }

    private fun skip(dir: Int, manual: Boolean) {
        val q = queue.value
        if (q.isEmpty()) return
        if (shuffle.value && manual && shuffleOrder.isNotEmpty()) {
            val cur = indexInQueue()
            val pos = shuffleOrder.indexOf(cur).takeIf { it >= 0 } ?: 0
            setIndex(shuffleOrder[(pos + dir + shuffleOrder.size) % shuffleOrder.size])
        } else {
            val cur = indexInQueue()
            setIndex(cur + dir)
        }
        startPlayback()
    }

    private fun onCompleted() {
        when (repeat.value) {
            RepeatMode.ONE -> {
                runCatching { mp?.seekTo(0) }
                runCatching { mp?.start() }
                playing.value = true
                positionMs.value = 0
            }
            RepeatMode.ALL -> skip(1, manual = false)
            RepeatMode.OFF -> {
                val q = queue.value
                val cur = indexInQueue()
                if (cur >= q.lastIndex) {
                    playing.value = false
                    stopTicker()
                    runCatching { mp?.seekTo(0) }
                    positionMs.value = 0
                } else {
                    skip(1, manual = false)
                }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                positionMs.value = runCatching { mp?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun startService() {
        val ctx = appContext ?: return
        runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MusicService::class.java))
        }
    }
}
