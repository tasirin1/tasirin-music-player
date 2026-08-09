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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasirin.musicplayer.ArtCache
import com.tasirin.musicplayer.LyricsLoader
import com.tasirin.musicplayer.PlayerController
import com.tasirin.musicplayer.Track
import com.tasirin.musicplayer.ui.theme.Accent

/** Layar "Sekarang Diputar" — sampul besar, lirik, dan kontrol. */
@Composable
fun NowPlayingScreen() {
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

    var onlineLyrics by remember(current.path) { mutableStateOf<String?>(null) }
    var lyricsLoading by remember(current.path) { mutableStateOf(false) }
    var lyricsAttempt by remember(current.path) { mutableStateOf(0) }
    val lyricsText = if (current.lyrics.isNotBlank()) current.lyrics else onlineLyrics

    LaunchedEffect(current.path, showLyrics, lyricsAttempt) {
        if (showLyrics && current.lyrics.isBlank() && onlineLyrics == null && !lyricsLoading) {
            lyricsLoading = true
            onlineLyrics = LyricsLoader.load(current)
            lyricsLoading = false
        }
    }

    val artColor = remember(art) { art?.let { averageColor(it) } }
    val shownPos = dragPos ?: pos.toFloat()
    val maxPos = dur.coerceAtLeast(1L).toFloat()
    val qIndex = queue.indexOfFirst { it.path == current.path }

    Box(Modifier.fillMaxSize().background(bg)) {
        // Latar: sampul melebar + gradasi dari warna dominan sampul
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
                    listOf(
                        artColor?.copy(alpha = 0.4f) ?: Color.Transparent,
                        bg.copy(alpha = 0.55f),
                        bg
                    )
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Sekarang Diputar",
                style = MaterialTheme.typography.titleMedium,
                color = secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Sampul responsif + glow
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1.25f)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyrics) {
                        LyricsCard(
                            lyrics = lyricsText,
                            loading = lyricsLoading,
                            positionMs = pos,
                            onRetry = { lyricsAttempt++ },
                            onClose = { showLyrics = false },
                            surfaceVariant = surfaceVariant
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .scale(1.1f)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            artColor?.copy(alpha = 0.3f) ?: Color.Transparent,
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                )
                        )
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = surfaceVariant,
                            shadowElevation = 28.dp,
                            modifier = Modifier
                                .fillMaxSize()
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
            }
            if (!showLyrics) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ketuk sampul untuk lirik",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                current.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
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
            }
            val meta = listOfNotNull(
                current.genre.takeIf { it.isNotBlank() },
                current.year.takeIf { it > 0 }?.toString(),
                current.trackNum.takeIf { it > 0 }?.let { "Lagu $it" }
            ).joinToString(" · ")
            val queueLabel = if (qIndex >= 0 && queue.size > 1) "${qIndex + 1} dari ${queue.size}" else null
            val infoLine = listOfNotNull(meta.takeIf { it.isNotBlank() }, queueLabel).joinToString(" · ")
            if (infoLine.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    infoLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))
            Slider(
                value = shownPos.coerceIn(0f, maxPos),
                onValueChange = { dragPos = it },
                onValueChangeFinished = {
                    dragPos?.let { PlayerController.seekTo(it.toLong()) }
                    dragPos = null
                },
                valueRange = 0f..maxPos,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = surfaceVariant
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    Track.formatMs(shownPos.toLong()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = secondary
                )
                Text(
                    "-" + Track.formatMs((dur - shownPos.toLong()).coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = secondary
                )
            }

            // Kontrol: baris atas (acak/ulang), baris tengah (prev/play/next)
            Spacer(Modifier.height(8.dp))
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
            val canSkip = queue.size > 1
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerController.prev() }, enabled = canSkip) {
                    Icon(
                        Icons.Filled.SkipPrevious, "Sebelumnya",
                        tint = if (canSkip) MaterialTheme.colorScheme.onSurface else secondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
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
                IconButton(onClick = { PlayerController.next() }, enabled = canSkip) {
                    Icon(
                        Icons.Filled.SkipNext, "Berikutnya",
                        tint = if (canSkip) MaterialTheme.colorScheme.onSurface else secondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
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

/** Lirik tampil di area sampul: baris aktif sinkron ala Oto Music. */
@Composable
private fun LyricsCard(
    lyrics: String?,
    loading: Boolean,
    positionMs: Long,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    surfaceVariant: Color
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surfaceVariant,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lirik",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close, "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            when {
                loading -> Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                lyrics == null -> Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Lirik tidak ditemukan",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = onRetry) {
                            Text("Coba lagi")
                        }
                    }
                }
                else -> {
                    val lines = remember(lyrics) { parseLrc(lyrics) }
                    if (lines.isEmpty()) {
                        Text(
                            lyrics,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp)
                        )
                    } else {
                        SyncedLyrics(
                            lines,
                            positionMs,
                            Modifier.weight(1f).fillMaxWidth(),
                            surfaceVariant
                        )
                    }
                }
            }
        }
    }
}


/** Lirik sinkron ala Oto Music: baris aktif besar + aksen di tengah, lama redup, fade di tepi. */
@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier,
    bg: Color
) {
    val state = rememberLazyListState()
    val index = remember(lines, positionMs) { currentLineIndex(lines, positionMs) }
    var viewportH by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(
        modifier.onSizeChanged {
            viewportH = with(density) { it.height.toDp() }
        }
    ) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(vertical = viewportH / 2),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lines) { i, line ->
                val active = i == index
                Text(
                    line.text.ifBlank { "♪" },
                    style = if (active) {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (active) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .alpha(
                            when {
                                active -> 1f
                                i < index -> 0.35f
                                else -> 0.6f
                            }
                        )
                )
            }
        }
        val fade = bg
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(fade, Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, fade)))
        )
    }

    LaunchedEffect(index, viewportH) {
        if (viewportH > 0.dp) {
            state.animateScrollToItem(index)
        }
    }
}

private data class LyricLine(val timeMs: Long, val text: String)

private val LRC_TAG = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** Parse teks LRC `[mm:ss.xx]baris`; dukung beberapa stempel waktu per baris. */
private fun parseLrc(raw: String): List<LyricLine> {
    val out = mutableListOf<LyricLine>()
    raw.lineSequence().forEach { rawLine ->
        val tags = LRC_TAG.findAll(rawLine).toList()
        if (tags.isEmpty()) return@forEach
        val text = rawLine.substring(tags.last().range.last + 1).trim()
        for (m in tags) {
            val min = m.groupValues[1].toLongOrNull() ?: continue
            val sec = m.groupValues[2].toLongOrNull() ?: continue
            val fracRaw = m.groupValues[3]
            val frac = when (fracRaw.length) {
                3 -> fracRaw.toLongOrNull() ?: 0L
                2 -> (fracRaw.toLongOrNull() ?: 0L) * 10
                1 -> (fracRaw.toLongOrNull() ?: 0L) * 100
                else -> 0L
            }
            out.add(LyricLine(min * 60000 + sec * 1000 + frac, text))
        }
    }
    return out.sortedBy { it.timeMs }
}

/** Baris lirik aktif = baris terakhir yang waktunya sudah lewat posisi saat ini. */
private fun currentLineIndex(lines: List<LyricLine>, posMs: Long): Int {
    var idx = 0
    for (i in lines.indices) {
        if (lines[i].timeMs <= posMs) idx = i else break
    }
    return idx
}

/** Warna rata-rata sampul (untuk gradasi latar) — sampel kecil agar cepat. */
private fun averageColor(bmp: Bitmap): Color {
    val s = Bitmap.createScaledBitmap(bmp, 8, 8, true)
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
    s.recycle()
    val n = 64
    return Color(r / n, g / n, b / n)
}
