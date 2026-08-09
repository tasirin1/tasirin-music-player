package com.tasirin.musicplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Pustaka lagu: pemindaian (inti Rust), pencarian, dan status. */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("music", android.content.Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _folder = MutableStateFlow(
        prefs.getString("folder", "/storage/emulated/0/Music") ?: "/storage/emulated/0/Music"
    )
    val folder: StateFlow<String> = _folder.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    val filtered: StateFlow<List<Track>> =
        combine(_tracks, _query) { tracks, q ->
            if (q.isBlank()) tracks
            else tracks.filter {
                it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadCached()
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFolder(f: String) {
        _folder.value = f.trim()
        prefs.edit().putString("folder", _folder.value).apply()
    }

    /** Muat pustaka tersimpan, lalu segarkan dengan pemindaian baru. */
    fun loadSaved() {
        val f = _folder.value
        if (f.isNotBlank()) scan(f, silent = true)
    }

    fun scan(folderPath: String, silent: Boolean = false) {
        if (_scanning.value) return
        setFolder(folderPath)
        viewModelScope.launch {
            _scanning.value = true
            _status.value = "Memindai…"
            val list = withContext(Dispatchers.IO) {
                runCatching { MusicCore.scan(_folder.value) }
                    .map(::parseTracks)
                    .getOrDefault(emptyList())
            }
            _tracks.value = list
            saveLibrary(list)
            val totalMin = list.sumOf { it.durationMs } / 60000
            _status.value = when {
                list.isEmpty() -> "Tidak ada lagu di ${_folder.value}"
                else -> "${list.size} lagu · ${totalMin} menit · ${_folder.value}"
            }
            _scanning.value = false
        }
    }

    fun clear() {
        _tracks.value = emptyList()
        prefs.edit().remove("library").apply()
        _status.value = ""
    }

    private fun loadCached() {
        val raw = prefs.getString("library", null) ?: return
        val list = runCatching { parseTracks(raw) }.getOrDefault(emptyList())
        if (list.isNotEmpty()) {
            _tracks.value = list
            _status.value = "${list.size} lagu tersimpan — pindai untuk menyegarkan"
        }
    }

    private fun saveLibrary(list: List<Track>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("path", t.path)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("genre", t.genre)
                    .put("year", t.year)
                    .put("duration_ms", t.durationMs)
            )
        }
        prefs.edit().putString("library", arr.toString()).apply()
    }

    private fun parseTracks(json: String): List<Track> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { Track.fromJson(arr.getJSONObject(it)) }
    }
}
