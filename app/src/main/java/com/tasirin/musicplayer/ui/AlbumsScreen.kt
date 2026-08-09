package com.tasirin.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasirin.musicplayer.ArtCache
import com.tasirin.musicplayer.LibraryViewModel
import com.tasirin.musicplayer.Track

/** Daftar album dari pustaka — kisi 2 kolom ala Apple Music. */
@Composable
fun AlbumsScreen(
    vm: LibraryViewModel,
    onOpenAlbum: (album: String, artist: String) -> Unit
) {
    val tracks by vm.tracks.collectAsState()
    val albums = remember(tracks) {
        tracks
            .groupBy { it.album.ifBlank { "<Tanpa Album>" } }
            .map { (name, list) ->
                AlbumItem(
                    name = name,
                    artist = list.firstOrNull { it.artist.isNotBlank() }?.artist.orEmpty(),
                    tracks = list
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Album",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
        )
        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada album — pindai folder musik",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albums, key = { "${it.name}\u0000${it.artist}" }) { album ->
                    AlbumCard(
                        album = album,
                        onClick = { onOpenAlbum(album.name, album.artist) }
                    )
                }
            }
        }
    }
}

private data class AlbumItem(val name: String, val artist: String, val tracks: List<Track>)

@Composable
private fun AlbumCard(album: AlbumItem, onClick: () -> Unit) {
    val coverPath = album.tracks.firstOrNull()?.path
    val art by produceState<Bitmap?>(null, coverPath) {
        value = coverPath?.let { ArtCache.load(it) }
    }
    Column(
        Modifier
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val bmp = art
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.4f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOf(album.artist, "${album.tracks.size} lagu").filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
