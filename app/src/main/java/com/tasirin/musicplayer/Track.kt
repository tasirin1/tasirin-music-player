package com.tasirin.musicplayer

import org.json.JSONObject

/** Satu lagu dari pustaka (metadata hasil inti Rust). */
data class Track(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int,
    val durationMs: Long,
    val trackNum: Int = 0,
    val lyrics: String = ""
) {
    val durationLabel: String get() = formatMs(durationMs)

    companion object {
        fun fromJson(o: JSONObject): Track {
            val path = o.optString("path", "")
            return Track(
                path = path,
                title = o.optString("title", "").ifBlank { path.substringAfterLast('/') },
                artist = o.optString("artist", ""),
                album = o.optString("album", ""),
                genre = o.optString("genre", ""),
                year = o.optInt("year", 0),
                durationMs = o.optLong("duration_ms", 0),
                trackNum = o.optInt("track", 0),
                lyrics = o.optString("lyrics", "")
            )
        }

        fun formatMs(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            else "$m:${s.toString().padStart(2, '0')}"
        }
    }
}
