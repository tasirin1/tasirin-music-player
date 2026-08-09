package com.tasirin.musicplayer

import android.app.Application

/** Inisialisasi global: konteks untuk pemutar & cache album art. */
class MusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ArtCache.init(this)
        PlayerController.attach(this)
    }
}
