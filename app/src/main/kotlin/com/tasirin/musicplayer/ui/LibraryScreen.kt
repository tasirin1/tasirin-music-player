package com.tasirin.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasirin.musicplayer.ArtCache
import com.tasirin.musicplayer.FavoritesStore
import com.tasirin.musicplayer.LibraryViewModel
import com.tasirin.musicplayer.PlayerController
import com.tasirin.musicplayer.Track
import com.tasirin.musicplayer.ui.components.Artwork
import com.tasirin.musicplayer.ui.theme.Accent

/** Perpustakaan: daftar lagu dengan pencarian — ala Apple Music. */
@Composable
fun LibraryScreen(vm: LibraryViewModel, onPlay: (List<Track>, Int) -> Unit) {
    val tracks by vm.filtered.collectAsState()
    val query by vm.query.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val favs by FavoritesStore.paths.collectAsState()
    var favOnly by rememberSaveable { mutableStateOf(false) }
    val shown = remember(tracks, favOnly, favs) {
        if (favOnly) tracks.filter { it.path in favs } else tracks
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Perpustakaan",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setQuery(it) },
            placeholder = { Text("Cari lagu, artis, album…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !favOnly,
                onClick = { favOnly = false },
                label = { Text("Semua") }
            )
            FilterChip(
                selected = favOnly,
                onClick = { favOnly = true },
                label = { Text("Favorit") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Favorite, null,
                        tint = Accent, modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        if (shown.isEmpty()) {
            if (favOnly && tracks.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada lagu favorit — ketuk ♥ di lagu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                EmptyLibrary(scanning)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.path }) { track ->
                    TrackRow(
                        track = track,
                        isFavorite = track.path in favs,
                        onToggleFavorite = { FavoritesStore.toggle(track.path) },
                        onClick = { onPlay(shown, shown.indexOf(track)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val art by produceState<Bitmap?>(null, track.path) { value = ArtCache.load(track.path) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(art, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }
                    .joinToString(" · ").ifBlank { track.path.substringAfterLast('/') },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isFavorite) "Hapus favorit" else "Tambah favorit",
                tint = if (isFavorite) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            track.durationLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyLibrary(scanning: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (scanning) {
                CircularProgressIndicator()
                Text("Memindai pustaka…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Belum ada lagu", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Atur folder musik lalu pindai di Pengaturan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
