package com.tasirin.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pemutar musik latar: MediaPlayer + notifikasi + MediaSession (tombol media remote TV). */
public class MusicService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    public static final String ACTION_PLAY_INDEX = "com.tasirin.musicplayer.PLAY_INDEX";
    public static final String ACTION_PLAY_PAUSE = "com.tasirin.musicplayer.PLAY_PAUSE";
    public static final String ACTION_PAUSE = "com.tasirin.musicplayer.PAUSE";
    public static final String ACTION_NEXT = "com.tasirin.musicplayer.NEXT";
    public static final String ACTION_PREV = "com.tasirin.musicplayer.PREV";
    public static final String ACTION_SEEK = "com.tasirin.musicplayer.SEEK";
    public static final String ACTION_STOP = "com.tasirin.musicplayer.STOP";
    public static final String ACTION_STATE = "com.tasirin.musicplayer.STATE";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_POSITION = "position";

    public static final String PREFS = "music_player";
    public static final String KEY_FOLDER = "folder";
    public static final String KEY_PORT = "port";
    public static final String DEFAULT_FOLDER = "/sdcard/Music";
    public static final int DEFAULT_PORT = 8090;

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final String NOTIF_CHANNEL = "musik";
    private static final int NOTIF_ID = 1;

    // State publik (dibaca MainActivity & RemoteServer).
    public static volatile boolean playing = false;
    public static volatile int playIndex = -1;
    public static volatile String trackName = "";
    public static volatile long positionMs = 0;
    public static volatile long durationMs = 0;
    public static volatile int shuffle = 0;
    public static volatile int repeat = REPEAT_NONE;
    public static volatile List<Track> queue = new ArrayList<>();

    private MediaPlayer player;
    private MediaSession mediaSession;
    private PowerManager.WakeLock wakeLock;
    private final Random random = new Random();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (playing && player != null) {
                try {
                    positionMs = player.getCurrentPosition();
                } catch (Exception ignored) {
                }
            }
            main.postDelayed(this, 500);
        }
    };

    // ─── Kontrol statis (dipakai UI, remote web, notifikasi) ───────────

    public static void playIndex(Context ctx, int index) {
        startSvc(ctx, new Intent(ctx, MusicService.class).setAction(ACTION_PLAY_INDEX)
                .putExtra(EXTRA_INDEX, index));
    }

    public static void toggle(Context ctx) {
        startSvc(ctx, new Intent(ctx, MusicService.class).setAction(ACTION_PLAY_PAUSE));
    }

    public static void pause(Context ctx) {
        startSvc(ctx, new Intent(ctx, MusicService.class).setAction(ACTION_PAUSE));
    }

    public static void next(Context ctx) {
        startSvc(ctx, new Intent(ctx, MusicService.class).setAction(ACTION_NEXT));
    }

    public static void prev(Context ctx) {
        startSvc(ctx, new Intent(ctx, MusicService.class).setAction(ACTION_PREV));
    }

    public static void seekTo(Context ctx, long ms) {
        startSvc(ctx, new Intent(ctx, MusicService.class).setAction(ACTION_SEEK)
                .putExtra(EXTRA_POSITION, ms));
    }

    public static void stopAll(Context ctx) {
        startSvc(ctx, new Intent(ctx, MusicService.class).setAction(ACTION_STOP));
    }

    public static void toggleShuffle(Context ctx) {
        shuffle = shuffle == 0 ? 1 : 0;
        saveToggles(ctx);
        ctx.sendBroadcast(new Intent(ACTION_STATE));
    }

    public static void cycleRepeat(Context ctx) {
        repeat = (repeat + 1) % 3;
        saveToggles(ctx);
        ctx.sendBroadcast(new Intent(ACTION_STATE));
    }

    public static void setQueue(List<Track> tracks) {
        queue = tracks != null ? tracks : new ArrayList<>();
        if (playIndex >= queue.size()) {
            playIndex = -1;
        }
    }

    public static void loadToggles(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        shuffle = sp.getInt("shuffle", 0);
        repeat = sp.getInt("repeat", REPEAT_NONE);
    }

    private static void saveToggles(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("shuffle", shuffle)
                .putInt("repeat", repeat)
                .apply();
    }

    private static void startSvc(Context ctx, Intent in) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(in);
        } else {
            ctx.startService(in);
        }
    }

    // ─── Service lifecycle ─────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        App.init(this);
        loadToggles(this);
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        mediaSession = new MediaSession(this, "TasirinMusik");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                toggleInternal();
            }

            @Override
            public void onPause() {
                pauseInternal();
            }

            @Override
            public void onSkipToNext() {
                nextInternal();
            }

            @Override
            public void onSkipToPrevious() {
                prevInternal();
            }

            @Override
            public void onSeekTo(long pos) {
                seekInternal(pos);
            }

            @Override
            public void onStop() {
                pauseInternal();
            }
        });
        mediaSession.setActive(true);
        main.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (action != null) {
            switch (action) {
                case ACTION_PLAY_INDEX:
                    playAt(intent.getIntExtra(EXTRA_INDEX, 0));
                    break;
                case ACTION_PLAY_PAUSE:
                    toggleInternal();
                    break;
                case ACTION_PAUSE:
                    pauseInternal();
                    break;
                case ACTION_NEXT:
                    nextInternal();
                    break;
                case ACTION_PREV:
                    prevInternal();
                    break;
                case ACTION_SEEK:
                    seekInternal(intent.getLongExtra(EXTRA_POSITION, 0));
                    break;
                case ACTION_STOP:
                    stopInternal();
                    stopSelf();
                    break;
                default:
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopInternal();
        main.removeCallbacks(tick);
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ─── Logika pemutaran ──────────────────────────────────────────────

    private void playAt(int index) {
        if (queue == null || queue.isEmpty() || index < 0 || index >= queue.size()) {
            return;
        }
        playIndex = index;
        trackName = queue.get(index).name;
        positionMs = 0;
        durationMs = 0;
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(queue.get(index).path);
            player.setOnPreparedListener(this);
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            playing = true;
            acquireWakeLock();
            broadcastState();
            updateNotification();
            player.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            playing = false;
            broadcastState();
            updateNotification();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        try {
            durationMs = mp.getDuration();
            positionMs = 0;
            if (playing) {
                mp.start();
            }
        } catch (Exception ignored) {
        }
        broadcastState();
        updateNotification();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (repeat == REPEAT_ONE) {
            playAt(playIndex);
            return;
        }
        if (shuffle == 1 && queue.size() > 1) {
            playAt(randomOther(playIndex));
            return;
        }
        if (playIndex + 1 < queue.size()) {
            playAt(playIndex + 1);
        } else if (repeat == REPEAT_ALL) {
            playAt(0);
        } else {
            positionMs = 0;
            pauseInternal();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        int failed = playIndex;
        releasePlayer();
        playing = false;
        // File rusak/format tak didukung: lewati ke lagu berikutnya.
        if (queue.size() > 1) {
            int next = failed + 1 < queue.size() ? failed + 1 : 0;
            if (next != failed) {
                playAt(next);
            }
        }
        broadcastState();
        updateNotification();
        return true;
    }

    private void toggleInternal() {
        if (playing) {
            pauseInternal();
        } else if (player != null) {
            playing = true;
            try {
                player.start();
            } catch (Exception ignored) {
            }
            acquireWakeLock();
            broadcastState();
            updateNotification();
        } else if (queue != null && !queue.isEmpty()) {
            playAt(playIndex >= 0 ? playIndex : 0);
        }
    }

    private void pauseInternal() {
        if (!playing && player == null) {
            return;
        }
        playing = false;
        if (player != null && player.isPlaying()) {
            try {
                player.pause();
                positionMs = player.getCurrentPosition();
            } catch (Exception ignored) {
            }
        }
        releaseWakeLock();
        broadcastState();
        updateNotification();
    }

    private void nextInternal() {
        if (queue == null || queue.isEmpty()) {
            return;
        }
        if (shuffle == 1 && queue.size() > 1) {
            playAt(randomOther(playIndex));
            return;
        }
        if (playIndex + 1 < queue.size()) {
            playAt(playIndex + 1);
        } else if (repeat == REPEAT_ALL) {
            playAt(0);
        } else {
            playAt(0);
        }
    }

    private void prevInternal() {
        if (queue == null || queue.isEmpty()) {
            return;
        }
        if (player != null && positionMs > 3000) {
            seekInternal(0);
            return;
        }
        if (playIndex - 1 >= 0) {
            playAt(playIndex - 1);
        } else if (repeat == REPEAT_ALL) {
            playAt(queue.size() - 1);
        } else {
            seekInternal(0);
        }
    }

    private void seekInternal(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        if (player != null) {
            try {
                player.seekTo((int) ms);
            } catch (Exception ignored) {
            }
        }
        positionMs = ms;
        broadcastState();
    }

    private int randomOther(int current) {
        int r = random.nextInt(queue.size());
        if (r == current) {
            r = (r + 1) % queue.size();
        }
        return r;
    }

    private void stopInternal() {
        releasePlayer();
        playing = false;
        trackName = "";
        playIndex = -1;
        positionMs = 0;
        durationMs = 0;
        releaseWakeLock();
        stopForeground(true);
        broadcastState();
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.reset();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tasirin:music");
            }
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {
        }
    }

    // ─── Notifikasi & MediaSession ─────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(NOTIF_CHANNEL,
                    "Pemutar Musik", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, flags);
        PendingIntent prevPi = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(ACTION_PREV), flags);
        PendingIntent togglePi = PendingIntent.getService(this, 2,
                new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE), flags);
        PendingIntent nextPi = PendingIntent.getService(this, 3,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT), flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIF_CHANNEL)
                : new Notification.Builder(this);
        String title = trackName.isEmpty() ? "Tasirin Musik Player" : trackName;
        return b.setSmallIcon(R.drawable.ic_stat_note)
                .setContentTitle(title)
                .setContentText(playing ? "Sedang diputar" : "Dijeda")
                .setOngoing(true)
                .setContentIntent(openPi)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_previous, "Sebelum", prevPi)
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Jeda" : "Putar", togglePi)
                .addAction(android.R.drawable.ic_media_next, "Berikutnya", nextPi)
                .build();
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) {
        }
    }

    private void broadcastState() {
        sendBroadcast(new Intent(ACTION_STATE));
        if (mediaSession != null) {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
            PlaybackState ps = new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                            positionMs, 1f)
                    .build();
            mediaSession.setPlaybackState(ps);
        }
    }
}
