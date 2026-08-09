package com.tasirin.musicplayer

/**
 * Bridge JNI ke inti Rust (`libmusiccore.so`).
 * Nama kelas & metode dijaga dari obfuscation oleh proguard-rules.pro.
 */
object MusicCore {
    init {
        System.loadLibrary("musiccore")
    }

    /** Versi inti Rust (CARGO_PKG_VERSION). */
    external fun version(): String

    /** Pindai folder musik → JSON array lagu. Selalu non-null ([] bila gagal). */
    external fun scan(root: String): String

    /** Byte album art (sampul) untuk satu file, atau null bila tidak ada. */
    external fun albumArt(path: String): ByteArray?
}
