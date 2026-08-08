# Service & komponen yang dipanggil via intent/refleksi sistem.
-keep class com.tasirin.musicplayer.MusicService { *; }
-keep class com.tasirin.musicplayer.RemoteServer { *; }
-keepclassmembers class * extends android.app.Service { *; }
