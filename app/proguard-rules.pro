# Kelas JNI — nama metode native tidak boleh diobfuscate
-keep class com.tasirin.musicplayer.MusicCore { *; }

# Layanan sistem/media — dipanggil lewat refleksi oleh Android
-keep class com.tasirin.musicplayer.MusicBrowserService { *; }
