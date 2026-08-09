package com.tasirin.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/** Warna rata-rata sampul (untuk aksen dinamis) — sampel 8x8 agar cepat. */
fun averageColor(bmp: Bitmap): Color {
    // createScaledBitmap mengembalikan bitmap yang sama bila ukurannya sudah 8x8 —
    // jangan sampai recycle bitmap cache yang sedang dipakai.
    val s = if (bmp.width == 8 && bmp.height == 8) bmp else Bitmap.createScaledBitmap(bmp, 8, 8, true)
    var r = 0
    var g = 0
    var b = 0
    for (x in 0 until 8) {
        for (y in 0 until 8) {
            val c = s.getPixel(x, y)
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
    }
    if (s !== bmp) s.recycle()
    val n = 64
    return Color(r / n, g / n, b / n)
}
