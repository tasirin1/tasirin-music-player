package com.tasirin.musicplayer

import android.app.Application

/** Inisialisasi global: konteks untuk pemutar, cache, favorit, dan lirik. */
class MusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ArtCache.init(this)
        LyricsLoader.init(this)
        FavoritesStore.init(this)
        MediaSessionManager.init(this)
        PlayerController.attach(this)
        PlayerController.restoreLastSession()
    }
}
