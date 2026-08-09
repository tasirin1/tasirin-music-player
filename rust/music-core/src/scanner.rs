//! Pemindai pustaka: jalan rekursif dari folder root, baca metadata
//! tiap file audio secara paralel (multi-thread), hasil diurutkan.

use crate::meta::{read_meta, TrackMeta};
use std::path::{Path, PathBuf};
use std::sync::Mutex;

const AUDIO_EXTS: [&str; 13] = [
    "mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav", "wma", "amr", "aiff", "ape", "mp4",
];

/// Folder sistem yang tidak relevan untuk pustaka musik — dilewati.
fn skip_dir(name: &str) -> bool {
    let n = name.to_lowercase();
    n.starts_with('.') || n == "android" || n == "alarms" || n == "notifications" || n == "ringtones"
}

fn collect_files(dir: &Path, out: &mut Vec<PathBuf>) {
    let Ok(entries) = std::fs::read_dir(dir) else { return };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !skip_dir(&name) {
                collect_files(&path, out);
            }
        } else if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
            let ext = ext.to_lowercase();
            if AUDIO_EXTS.contains(&ext.as_str()) {
                out.push(path);
            }
        }
    }
}

/// Pindai seluruh pustaka; gagal satu file tidak menghentikan pemindaian.
pub fn scan_library(root: &str) -> Vec<TrackMeta> {
    let mut files = Vec::new();
    collect_files(Path::new(root), &mut files);
    files.sort();
    if files.is_empty() {
        return Vec::new();
    }

    let threads = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(4).min(8).max(1);
    let files_mutex = Mutex::new(files);
    let out = Mutex::new(Vec::with_capacity(1024));

    std::thread::scope(|scope| {
        for _ in 0..threads {
            scope.spawn(|| loop {
                let file = files_mutex.lock().unwrap().pop();
                let Some(file) = file else { break };
                if let Some(p) = file.to_str() {
                    if let Ok(meta) = read_meta(p) {
                        out.lock().unwrap().push(meta);
                    }
                }
            });
        }
    });

    let mut tracks = out.into_inner().unwrap_or_default();
    tracks.sort_by(|a, b| a.title.to_lowercase().cmp(&b.title.to_lowercase()));
    tracks
}
