package com.tasirin.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private EditText folderInput;
    private EditText portInput;
    private Button scanBtn;
    private Button btnPrev;
    private Button btnPlay;
    private Button btnNext;
    private Button btnShuffle;
    private Button btnRepeat;
    private Button btnRemote;
    private TextView trackName;
    private TextView timePos;
    private TextView timeDur;
    private TextView remoteUrl;
    private TextView emptyHint;
    private ListView listView;
    private SeekBar seekBar;

    private final List<Track> list = new ArrayList<>();
    private TrackAdapter adapter;
    private final RemoteServer remote = new RemoteServer();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean dragging;
    private int lastShownIndex = -2;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            ui.postDelayed(this, 500);
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refreshUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.init(getApplicationContext());
        MusicService.loadToggles(this);
        setContentView(R.layout.activity_main);
        bindViews();

        SharedPreferences sp = getSharedPreferences(MusicService.PREFS, MODE_PRIVATE);
        folderInput.setText(sp.getString(MusicService.KEY_FOLDER, MusicService.DEFAULT_FOLDER));
        portInput.setText(String.valueOf(sp.getInt(MusicService.KEY_PORT, MusicService.DEFAULT_PORT)));

        adapter = new TrackAdapter();
        listView.setAdapter(adapter);
        wire();
        ensurePermission();
        registerReceiver(stateReceiver, new IntentFilter(MusicService.ACTION_STATE));
        ui.post(tick);

        if (!MusicService.queue.isEmpty()) {
            list.clear();
            list.addAll(MusicService.queue);
            adapter.notifyDataSetChanged();
        }
        if (list.isEmpty()) {
            scan();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {
        }
        ui.removeCallbacks(tick);
    }

    private void bindViews() {
        folderInput = findViewById(R.id.folderInput);
        portInput = findViewById(R.id.portInput);
        scanBtn = findViewById(R.id.scanBtn);
        btnPrev = findViewById(R.id.btnPrev);
        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnRemote = findViewById(R.id.btnRemote);
        trackName = findViewById(R.id.trackName);
        timePos = findViewById(R.id.timePos);
        timeDur = findViewById(R.id.timeDur);
        remoteUrl = findViewById(R.id.remoteUrl);
        emptyHint = findViewById(R.id.emptyHint);
        listView = findViewById(R.id.listView);
        seekBar = findViewById(R.id.seekBar);
    }

    private void wire() {
        scanBtn.setOnClickListener(v -> scan());
        folderInput.setOnEditorActionListener((v, actionId, event) -> {
            scan();
            return true;
        });
        listView.setOnItemClickListener((parent, v, position, id) ->
                MusicService.playIndex(this, position));
        btnPrev.setOnClickListener(v -> MusicService.prev(this));
        btnPlay.setOnClickListener(v -> MusicService.toggle(this));
        btnNext.setOnClickListener(v -> MusicService.next(this));
        btnShuffle.setOnClickListener(v -> MusicService.toggleShuffle(this));
        btnRepeat.setOnClickListener(v -> MusicService.cycleRepeat(this));
        btnRemote.setOnClickListener(v -> toggleRemote());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    dragging = true;
                    timePos.setText(Util.fmtTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                dragging = false;
                MusicService.seekTo(MainActivity.this, sb.getProgress());
            }
        });
    }

    private void ensurePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, 1);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scan();
        }
    }

    private void scan() {
        final String folder = folderInput.getText().toString().trim();
        if (folder.isEmpty()) {
            toast("Isi folder musik dulu.");
            return;
        }
        getSharedPreferences(MusicService.PREFS, MODE_PRIVATE).edit()
                .putString(MusicService.KEY_FOLDER, folder).apply();
        scanBtn.setEnabled(false);
        scanBtn.setText("Memindai...");
        new Thread(() -> {
            final List<Track> found = FileScanner.scan(new File(folder));
            ui.post(() -> {
                scanBtn.setEnabled(true);
                scanBtn.setText(R.string.scan);
                list.clear();
                list.addAll(found);
                MusicService.setQueue(new ArrayList<>(found));
                adapter.notifyDataSetChanged();
                emptyHint.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                refreshUi();
                toast(found.size() + " lagu di " + folder);
            });
        }, "mp-scan").start();
    }

    private void refreshUi() {
        btnPlay.setText(MusicService.playing ? "|| Jeda" : "▶ Putar");
        trackName.setText(MusicService.trackName.isEmpty()
                ? getString(R.string.no_track) : MusicService.trackName);
        if (!dragging) {
            seekBar.setMax(Math.max(1, (int) MusicService.durationMs));
            seekBar.setProgress((int) MusicService.positionMs);
        }
        timePos.setText(Util.fmtTime(MusicService.positionMs));
        timeDur.setText(Util.fmtTime(MusicService.durationMs));
        btnShuffle.setText("Acak: " + (MusicService.shuffle == 1 ? "Nyala" : "Mati"));
        String[] rep = {"Mati", "Semua", "Satu"};
        btnRepeat.setText("Ulang: " + rep[Math.min(Math.max(MusicService.repeat, 0), 2)]);
        btnRemote.setText("Remote: " + (remote.isRunning() ? "Nyala" : "Mati"));
        if (remote.isRunning()) {
            int port = readPort();
            remoteUrl.setText("Buka di HP/PC: http://" + Util.localIp() + ":" + port);
        } else {
            remoteUrl.setText(R.string.remote_hint);
        }
        if (lastShownIndex != MusicService.playIndex) {
            lastShownIndex = MusicService.playIndex;
            adapter.notifyDataSetChanged();
        }
    }

    private int readPort() {
        try {
            return Integer.parseInt(portInput.getText().toString().trim());
        } catch (Exception e) {
            return MusicService.DEFAULT_PORT;
        }
    }

    private void toggleRemote() {
        int port = readPort();
        getSharedPreferences(MusicService.PREFS, MODE_PRIVATE).edit()
                .putInt(MusicService.KEY_PORT, port).apply();
        try {
            if (remote.isRunning()) {
                remote.stop();
                toast("Remote dimatikan.");
            } else {
                remote.start(port);
                toast("Remote aktif: http://" + Util.localIp() + ":" + port);
            }
        } catch (Exception e) {
            toast("Gagal start remote: " + e.getMessage());
        }
        refreshUi();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private class TrackAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_track, parent, false);
            }
            TextView name = convertView.findViewById(R.id.trackName);
            TextView folder = convertView.findViewById(R.id.trackFolder);
            Track t = list.get(position);
            name.setText((position == MusicService.playIndex ? "▶ " : "") + t.name);
            folder.setText(t.folder);
            return convertView;
        }
    }
}
