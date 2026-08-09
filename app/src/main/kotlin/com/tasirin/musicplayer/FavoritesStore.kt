package com.tasirin.musicplayer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Favorit lagu (by path), tersimpan lokal di SharedPreferences. */
object FavoritesStore {

    private lateinit var prefs: SharedPreferences
    private val _paths = MutableStateFlow<Set<String>>(emptySet())
    val paths: StateFlow<Set<String>> = _paths.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("music", Context.MODE_PRIVATE)
        _paths.value = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    }

    fun toggle(path: String) {
        if (path.isBlank()) return
        val cur = _paths.value.toMutableSet()
        if (!cur.add(path)) cur.remove(path)
        _paths.value = cur
        prefs.edit().putStringSet("favorites", cur).apply()
    }
}
