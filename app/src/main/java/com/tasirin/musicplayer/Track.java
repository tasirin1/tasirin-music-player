package com.tasirin.musicplayer;

import java.io.File;

/** Satu lagu hasil pindai folder musik. */
public final class Track {

    public final String path;
    public final String name;
    public final String folder;

    public Track(String path, String name, String folder) {
        this.path = path;
        this.name = name;
        this.folder = folder;
    }

    /** Nama file tanpa ekstensi. */
    public static String baseName(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
