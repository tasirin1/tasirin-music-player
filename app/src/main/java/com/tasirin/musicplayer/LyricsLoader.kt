package com.tasirin.musicplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Cari lirik online lewat LRCLIB (gratis, tanpa API key) + cache memori/disk. */
object LyricsLoader {

    private lateinit var ctx: Context
    private val mem = mutableMapOf<String, String>()

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    /** Lirik untuk satu lagu: cache dulu, baru cari online. Null bila tidak ketemu. */
    suspend fun load(track: Track): String? = withContext(Dispatchers.IO) {
        val key = "${track.artist}\u0000${track.title}\u0000${track.album}"
        mem[key]?.let { return@withContext it }
        val file = fileFor(key)
        if (file.exists()) {
            val cached = file.readText()
            if (cached.isNotBlank()) {
                mem[key] = cached
                return@withContext cached
            }
        }
        val found = runCatching { searchOnline(track) }.getOrNull()
        if (found != null) {
            mem[key] = found
            runCatching { file.writeText(found) }
        }
        found
    }

    private fun searchOnline(track: Track): String? {
        val query = "https://lrclib.net/api/search?track_name=${enc(track.title)}&artist_name=${enc(track.artist)}"
            .let { if (track.album.isNotBlank()) "$it&album_name=${enc(track.album)}" else it }
        val conn = (URL(query).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "TasirinMusik/1.0 (Android)")
        }
        return try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            val wantDur = (track.durationMs / 1000).toInt()
            var best: String? = null
            var bestScore = Int.MAX_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val synced = o.optString("syncedLyrics", "")
                val text = synced.ifBlank { o.optString("plainLyrics", "") }
                if (text.isBlank()) continue
                val dur = o.optInt("duration", 0)
                // skor: kedekatan durasi + bonus untuk lirik sinkron (LRC)
                val score = kotlin.math.abs(dur - wantDur) + if (synced.isNotBlank()) 0 else 2000
                if (score < bestScore) {
                    bestScore = score
                    best = text
                }
            }
            best
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun fileFor(key: String): File {
        val dir = File(ctx.cacheDir, "lyrics").apply { mkdirs() }
        return File(dir, md5(key))
    }

    private fun md5(s: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
