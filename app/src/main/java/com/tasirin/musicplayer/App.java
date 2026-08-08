package com.tasirin.musicplayer;

import android.content.Context;

/** Penampung konteks aplikasi (dipakai RemoteServer & komponen non-Activity). */
public final class App {

    public static Context ctx;

    private App() {
    }

    public static void init(Context c) {
        ctx = c.getApplicationContext();
    }
}
