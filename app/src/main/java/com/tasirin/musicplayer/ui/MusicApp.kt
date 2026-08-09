package com.tasirin.musicplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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

    var tab by rememberSaveable { mutableStateOf(0) }
    var albumOpen by rememberSaveable { mutableStateOf(false) }
    val hasPermission = remember { mutableStateOf(hasAudioPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPermission.value = hasAudioPermission(context)
        if (hasPermission.value) vm.loadSaved()
    }

    MusicAppTheme(darkTheme = dark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    MiniPlayer(onOpen = { tab = 1 })
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0; albumOpen = false },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                            label = { Text("Perpustakaan") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                            label = { Text("Sekarang") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text("Pengaturan") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> LibraryScreen(
                        vm = vm,
                        onPlay = { list, i ->
                            PlayerController.play(list, i)
                            tab = 1
                        }
                    )
                    1 -> {
                        val current by PlayerController.currentTrack.collectAsState()
                        if (albumOpen && current != null) {
                            AlbumScreen(
                                vm = vm,
                                album = current.album,
                                artist = current.artist,
                                onBack = { albumOpen = false },
                                onPlay = { list, i ->
                                    PlayerController.play(list, i)
                                }
                            )
                        } else {
                            NowPlayingScreen(onOpenAlbum = { albumOpen = true })
                        }
                    }
                    else -> SettingsScreen(
                        vm = vm,
                        themeMode = themeMode,
                        hasPermission = hasPermission.value,
                        onThemeChange = {
                            themeMode = it
                            prefs.edit().putString("theme", it).apply()
                        },
                        onRequestPermission = { permissionLauncher.launch(audioPermissions()) }
                    )
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
