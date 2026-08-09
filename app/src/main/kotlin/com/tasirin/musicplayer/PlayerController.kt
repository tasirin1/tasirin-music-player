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
import org.json.JSONObject

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
    private var pendingSeekMs: Long? = null
    private var restorePaused = false
    private var tickCount = 0

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** Mulai putar daftar [list] dari index [start]. */
    fun play(list: List<Track>, start: Int) {
        if (list.isEmpty()) return
        restorePaused = false
        pendingSeekMs = null
        queue.value = list.toList()
        if (shuffle.value) rebuildShuffle(start)
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
        saveState()
    }

    fun next() = skip(1)

    fun prev() {
        if (positionMs.value > 3000) seekTo(0) else skip(-1)
    }

    fun seekTo(ms: Long) {
        runCatching { mp?.seekTo(ms.toInt().coerceAtLeast(0)) }
        positionMs.value = ms.coerceAtLeast(0)
    }

    /** Pulihkan sesi terakhir (lagu, posisi, status putar) saat app dibuka. */
    fun restoreLastSession() {
        val prefs = appContext?.getSharedPreferences("music", Context.MODE_PRIVATE) ?: return
        val raw = prefs.getString("last_state", null) ?: return
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val path = o.optString("path", "")
        if (path.isBlank()) return
        val track = Track(
            path = path,
            title = o.optString("title", "").ifBlank { path.substringAfterLast('/') },
            artist = o.optString("artist", ""),
            album = o.optString("album", ""),
            genre = o.optString("genre", ""),
            year = o.optInt("year", 0),
            durationMs = o.optLong("duration_ms", 0),
            trackNum = o.optInt("track", 0)
        )
        val pos = o.optLong("pos", 0)
        queue.value = listOf(track)
        currentTrack.value = track
        durationMs.value = track.durationMs
        positionMs.value = pos
        restorePaused = !o.optBoolean("playing", false)
        pendingSeekMs = pos
        startPlayback()
    }

    fun setShuffle(on: Boolean) {
        shuffle.value = on
        shuffleOrder = if (on) {
            val cur = indexInQueue()
            rebuildOrder(cur)
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
        saveState()
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
                    pendingSeekMs?.let { ms ->
                        runCatching { prepared.seekTo(ms.toInt().coerceAtLeast(0)) }
                    }
                    pendingSeekMs = null
                    if (restorePaused) {
                        restorePaused = false
                        prepared.pause()
                        playing.value = false
                    } else {
                        prepared.start()
                        playing.value = true
                        startTicker()
                    }
                    durationMs.value = prepared.duration.toLong().coerceAtLeast(0L)
                    positionMs.value = positionMs.value.coerceAtMost(durationMs.value)
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

    private fun skip(dir: Int) {
        val q = queue.value
        if (q.isEmpty()) return
        restorePaused = false
        pendingSeekMs = null
        if (shuffle.value && shuffleOrder.isNotEmpty()) {
            val cur = indexInQueue()
            val pos = shuffleOrder.indexOf(cur).takeIf { it >= 0 } ?: 0
            setIndex(shuffleOrder[(pos + dir + shuffleOrder.size) % shuffleOrder.size])
        } else {
            val cur = indexInQueue()
            setIndex(cur + dir)
        }
        startPlayback()
    }

    private fun rebuildShuffle(start: Int) {
        shuffleOrder = rebuildOrder(start)
    }

    private fun rebuildOrder(start: Int): List<Int> {
        val rest = queue.value.indices.filter { it != start }
        return listOf(start) + rest.shuffled()
    }

    private fun onCompleted() {
        when (repeat.value) {
            RepeatMode.ONE -> {
                runCatching { mp?.seekTo(0) }
                runCatching { mp?.start() }
                playing.value = true
                positionMs.value = 0
            }
            RepeatMode.ALL -> skip(1)
            RepeatMode.OFF -> {
                if (isLastInOrder()) {
                    playing.value = false
                    stopTicker()
                    runCatching { mp?.seekTo(0) }
                    positionMs.value = 0
                } else {
                    skip(1)
                }
            }
        }
    }

    /** True bila lagu saat ini adalah lagu terakhir urutan (shuffle atau linear). */
    private fun isLastInOrder(): Boolean {
        val q = queue.value
        if (q.isEmpty()) return true
        val cur = indexInQueue()
        if (shuffle.value && shuffleOrder.isNotEmpty()) {
            val pos = shuffleOrder.indexOf(cur).takeIf { it >= 0 } ?: 0
            return pos >= shuffleOrder.lastIndex
        }
        return cur >= q.lastIndex
    }

    private fun startTicker() {
        ticker?.cancel()
        tickCount = 0
        ticker = scope.launch {
            while (isActive) {
                positionMs.value = runCatching { mp?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
                if (++tickCount % 10 == 0) saveState()
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

    /** Simpan lagu + posisi + status putar agar bisa dilanjutkan lain kali. */
    private fun saveState() {
        val t = currentTrack.value ?: return
        val prefs = appContext?.getSharedPreferences("music", Context.MODE_PRIVATE) ?: return
        val o = JSONObject()
            .put("path", t.path)
            .put("title", t.title)
            .put("artist", t.artist)
            .put("album", t.album)
            .put("genre", t.genre)
            .put("year", t.year)
            .put("duration_ms", t.durationMs)
            .put("track", t.trackNum)
            .put("pos", positionMs.value)
            .put("playing", playing.value)
        prefs.edit().putString("last_state", o.toString()).apply()
    }
}
