package com.tasirin.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Cache album art: memori (LRU) + disk, sumber dari inti Rust via JNI. */
object ArtCache {

    private lateinit var ctx: Context
    private val mem = object : LinkedHashMap<String, Bitmap>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 96
    }

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    suspend fun load(path: String): Bitmap? = withContext(Dispatchers.IO) { loadSync(path) }

    @Synchronized
    fun loadSync(path: String): Bitmap? {
        if (path.isBlank()) return null
        mem[path]?.let { return it }
        val file = fileFor(path)
        if (file.exists()) {
            decodeAndCache(path, file)?.let { return it }
        }
        val bytes = try {
            MusicCore.albumArt(path)
        } catch (_: Throwable) {
            null
        } ?: return null
        if (bytes.isEmpty()) return null
        try {
            file.writeBytes(bytes)
        } catch (_: Throwable) { }
        return decodeAndCache(path, file)
    }

    private fun decodeAndCache(path: String, file: File): Bitmap? {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        mem[path] = bmp
        return bmp
    }

    private fun fileFor(path: String): File {
        val dir = File(ctx.cacheDir, "art").apply { mkdirs() }
        return File(dir, md5(path))
    }

    private fun md5(s: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
