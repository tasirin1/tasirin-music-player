package com.tasirin.musicplayer

import android.content.Context
import android.provider.MediaStore

/** Pustaka via MediaStore — untuk Android Auto & resolusi lagu dari media id. */
object MediaLibrary {

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.TRACK
    )

    fun queryAll(context: Context): List<Track> {
        val out = mutableListOf<Track>()
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, sort
        )?.use { c ->
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackNo = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            while (c.moveToNext()) {
                val path = c.getString(data) ?: continue
                if (path.isBlank()) continue
                out.add(
                    Track(
                        path = path,
                        title = c.getString(title)?.takeIf { it.isNotBlank() }
                            ?: path.substringAfterLast('/'),
                        artist = c.getString(artist) ?: "",
                        album = c.getString(album) ?: "",
                        genre = "",
                        year = c.getInt(year),
                        durationMs = c.getLong(dur),
                        trackNum = c.getInt(trackNo)
                    )
                )
            }
        }
        return out
    }

    fun findByPath(context: Context, path: String): Track? {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            uri, projection, "${MediaStore.Audio.Media.DATA}=?", arrayOf(path), null
        )?.use { c ->
            if (!c.moveToFirst()) return@use
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackNo = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val p = c.getString(data) ?: return@use
            return Track(
                path = p,
                title = c.getString(title)?.takeIf { it.isNotBlank() }
                    ?: p.substringAfterLast('/'),
                artist = c.getString(artist) ?: "",
                album = c.getString(album) ?: "",
                genre = "",
                year = c.getInt(year),
                durationMs = c.getLong(dur),
                trackNum = c.getInt(trackNo)
            )
        }
        return null
    }
}
