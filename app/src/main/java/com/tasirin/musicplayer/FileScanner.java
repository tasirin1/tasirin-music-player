package com.tasirin.musicplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pindai folder musik (rekursif) tanpa dependensi eksternal. */
public final class FileScanner {

    private static final String[] EXTS = {
            ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".wav", ".flac", ".opus", ".amr", ".mid"
    };

    private FileScanner() {
    }

    public static List<Track> scan(File root) {
        List<Track> out = new ArrayList<>();
        if (root != null && root.isDirectory()) {
            collect(root, out, 0);
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private static void collect(File dir, List<Track> out, int depth) {
        if (depth > 10) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) {
                    collect(f, out, depth + 1);
                }
            } else {
                String n = f.getName().toLowerCase(Locale.US);
                for (String e : EXTS) {
                    if (n.endsWith(e)) {
                        out.add(new Track(f.getAbsolutePath(), Track.baseName(f), dir.getName()));
                        break;
                    }
                }
            }
        }
    }
}
