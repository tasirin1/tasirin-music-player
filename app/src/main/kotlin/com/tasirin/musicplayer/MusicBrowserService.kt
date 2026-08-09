package com.tasirin.musicplayer

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browser pustaka untuk Android Auto — konten = antrean saat ini / pustaka. */
class MusicBrowserService : MediaBrowserServiceCompat() {

    companion object {
        private const val ROOT_ID = "root"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        MediaSessionManager.session()?.let { sessionToken = it.sessionToken }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): MediaBrowserServiceCompat.BrowserRoot? = MediaBrowserServiceCompat.BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }
        result.detach()
        scope.launch {
            val tracks = withContext(Dispatchers.IO) {
                val q = PlayerController.queue.value
                if (q.isNotEmpty()) q else MediaLibrary.queryAll(applicationContext)
            }
            result.sendResult(
                tracks.mapIndexed { i, t ->
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId(t.path)
                            .setTitle(t.title)
                            .setSubtitle(t.artist.ifBlank { t.album })
                            .setDescription(t.album.ifBlank { t.artist })
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                }.toMutableList()
            )
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
}
