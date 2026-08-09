package com.tasirin.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasirin.musicplayer.ArtCache
import com.tasirin.musicplayer.PlayerController
import com.tasirin.musicplayer.Track
import com.tasirin.musicplayer.ui.components.Artwork
import com.tasirin.musicplayer.ui.theme.Accent

/** Layar "Sekarang Diputar" — sampul besar (ketuk → lirik), kontrol, tombol album. */
@Composable
fun NowPlayingScreen(onOpenAlbum: () -> Unit) {
    val track by PlayerController.currentTrack.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    val pos by PlayerController.positionMs.collectAsState()
    val dur by PlayerController.durationMs.collectAsState()
    val shuffle by PlayerController.shuffle.collectAsState()
    val repeat by PlayerController.repeat.collectAsState()
    val queue by PlayerController.queue.collectAsState()

    val path = track?.path
    val art by produceState<Bitmap?>(null, path) { value = path?.let { ArtCache.load(it) } }
    var dragPos by remember { mutableStateOf<Float?>(null) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    val bg = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    val current = track
    if (current == null) {
        EmptyNowPlaying()
        return
    }

    val shownPos = dragPos ?: pos.toFloat()
    val maxPos = dur.coerceAtLeast(1L).toFloat()

    Box(Modifier.fillMaxSize().background(bg)) {
        // Latar: sampul melebar + redup agar teks terbaca
        art?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(1.5f).alpha(0.16f),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, bg.copy(alpha = 0.6f), bg)
                )
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Sekarang Diputar",
                style = MaterialTheme.typography.titleMedium,
                color = secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showLyrics) {
                    LyricsCard(current.lyrics, secondary, surfaceVariant, onClose = { showLyrics = false })
                } else {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = surfaceVariant,
                        shadowElevation = 28.dp,
                        modifier = Modifier
                            .size(300.dp)
                            .clickable { showLyrics = true }
                    ) {
                        art?.let {
                            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.MusicNote, null,
                                tint = secondary, modifier = Modifier.size(96.dp)
                            )
                        }
                    }
                }
            }
            if (!showLyrics && current.lyrics.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Ketuk sampul untuk lirik",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                current.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                current.artist,
                style = MaterialTheme.typography.titleMedium,
                color = secondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (current.album.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    current.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenAlbum,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Lihat album")
                }
            }

            Spacer(Modifier.height(24.dp))
            Slider(
                value = shownPos.coerceIn(0f, maxPos),
                onValueChange = { dragPos = it },
                onValueChangeFinished = {
                    dragPos?.let { PlayerController.seekTo(it.toLong()) }
                    dragPos = null
                },
                valueRange = 0f..maxPos
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    Track.formatMs(shownPos.toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary
                )
                Text(
                    Track.formatMs(dur),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerController.setShuffle(!shuffle) }) {
                    Icon(
                        Icons.Filled.Shuffle, "Acak",
                        tint = if (shuffle) Accent else secondary
                    )
                }
                IconButton(onClick = { PlayerController.prev() }) {
                    Icon(Icons.Filled.SkipPrevious, "Sebelumnya", tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp))
                }
                Surface(
                    shape = CircleShape,
                    color = Accent,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .clickable { PlayerController.toggle() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (playing) "Jeda" else "Putar",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                IconButton(onClick = { PlayerController.next() }) {
                    Icon(Icons.Filled.SkipNext, "Berikutnya", tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = {
                    PlayerController.setRepeat(
                        when (repeat) {
                            PlayerController.RepeatMode.OFF -> PlayerController.RepeatMode.ALL
                            PlayerController.RepeatMode.ALL -> PlayerController.RepeatMode.ONE
                            PlayerController.RepeatMode.ONE -> PlayerController.RepeatMode.OFF
                        }
                    )
                }) {
                    Icon(
                        if (repeat == PlayerController.RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Ulang",
                        tint = if (repeat != PlayerController.RepeatMode.OFF) Accent else secondary
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Selanjutnya", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val rest = queue.dropWhile { it.path != current.path }.drop(1)
            val upNext = (if (rest.isNotEmpty()) rest else queue).take(5)
            upNext.forEach { t ->
                NextRow(t) {
                    val idx = queue.indexOfFirst { it.path == t.path }
                    if (idx >= 0) PlayerController.play(queue, idx)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NextRow(current: Track, onClick: () -> Unit) {
    val art by produceState<Bitmap?>(null, current.path) { value = ArtCache.load(current.path) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(art, size = 40.dp, corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                current.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                current.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            current.durationLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyNowPlaying() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.MusicNote, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Belum ada lagu diputar", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pilih lagu dari Perpustakaan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun LyricsCard(
    lyrics: String,
    secondary: Color,
    surfaceVariant: Color,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lirik", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, "Tutup", tint = secondary)
                }
            }
            if (lyrics.isBlank()) {
                Text(
                    "Tidak ada lirik tertanam di file ini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary
                )
            } else {
                Text(
                    lyrics,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
