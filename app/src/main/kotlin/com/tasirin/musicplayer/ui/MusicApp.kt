package com.tasirin.musicplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tasirin.musicplayer.LibraryViewModel
import com.tasirin.musicplayer.PlayerController
import com.tasirin.musicplayer.ui.theme.MusicAppTheme

/** Akar aplikasi: tema, navigasi bawah, mini player, dan izin audio. */
@Composable
fun MusicApp(vm: LibraryViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("music", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    var tab by rememberSaveable { mutableStateOf(1) }
    var albumOpen by rememberSaveable { mutableStateOf(false) }
    var albumName by rememberSaveable { mutableStateOf<String?>(null) }
    var albumArtist by rememberSaveable { mutableStateOf("") }
    val hasPermission = remember { mutableStateOf(hasAudioPermission(context)) }
    val hasNotifPermission = remember { mutableStateOf(hasNotifPermissionGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPermission.value = hasAudioPermission(context)
        hasNotifPermission.value = hasNotifPermissionGranted(context)
        if (hasPermission.value) vm.loadSaved()
    }

    LaunchedEffect(Unit) {
        // Auto-scan saat dibuka: lagu baru langsung terdeteksi (tanpa tekan "Pindai").
        if (hasPermission.value) vm.loadSaved()
    }
    val hasTrack by PlayerController.currentTrack.collectAsState()

    MusicAppTheme(darkTheme = dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = tab != 0 && hasTrack != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        MiniPlayer(onOpen = { tab = 0; albumOpen = false; albumName = null })
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0; albumOpen = false; albumName = null },
                            icon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                            label = { Text("Sekarang") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1; albumOpen = false; albumName = null },
                            icon = { Icon(Icons.Filled.Album, contentDescription = null) },
                            label = { Text("Album") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2; albumOpen = false; albumName = null },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                            label = { Text("Perpustakaan") }
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3; albumOpen = false; albumName = null },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text("Pengaturan") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val album = albumName
                val screenKey = if (albumOpen && album != null) {
                    "album\u0000$album\u0000$albumArtist"
                } else {
                    "tab\u0000$tab"
                }
                Crossfade(
                    targetState = screenKey,
                    animationSpec = tween(220),
                    label = "screen"
                ) { key ->
                    val parts = key.split("\u0000")
                    when (parts[0]) {
                        "album" -> AlbumScreen(
                            vm = vm,
                            album = parts[1],
                            artist = parts.getOrElse(2) { "" },
                            onBack = { albumOpen = false; albumName = null },
                            onPlay = { list, i ->
                                PlayerController.play(list, i)
                            }
                        )
                        "tab" -> when (parts[1]) {
                            "0" -> NowPlayingScreen()
                            "1" -> AlbumsScreen(
                                vm = vm,
                                onOpenAlbum = { a, ar ->
                                    albumName = a
                                    albumArtist = ar
                                    albumOpen = true
                                }
                            )
                            "2" -> LibraryScreen(
                                vm = vm,
                                onPlay = { list, i ->
                                    PlayerController.play(list, i)
                                    tab = 0
                                }
                            )
                            else -> SettingsScreen(
                                vm = vm,
                                themeMode = themeMode,
                                hasPermission = hasPermission.value,
                                hasNotifPermission = hasNotifPermission.value,
                                onThemeChange = {
                                    themeMode = it
                                    prefs.edit().putString("theme", it).apply()
                                },
                                onRequestPermission = {
                                    permissionLauncher.launch(audioPermissions())
                                },
                                onRequestNotifPermission = {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun audioPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasAudioPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

private fun hasNotifPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
