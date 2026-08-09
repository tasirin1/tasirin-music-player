//! Metadata lagu: judul, artis, album, durasi, dan album art.
//! Parsing tag memakai `lofty` (ID3v2, MP4, FLAC, Vorbis, APE, WMA, dll).

use lofty::prelude::*;
use lofty::read_from_path;
use serde::Serialize;

/// Satu lagu hasil pemindaian (dikirim ke Kotlin sebagai JSON).
#[derive(Serialize, Clone, Debug)]
pub struct TrackMeta {
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub genre: String,
    pub year: u32,
    pub track: u32,
    pub duration_ms: u64,
    pub sample_rate: u32,
    pub lyrics: String,
}

fn tag_text<'a>(tag: Option<&'a lofty::tag::Tag>, f: impl Fn(&'a lofty::tag::Tag) -> Option<std::borrow::Cow<'a, str>>) -> String {
    tag.and_then(|t| f(t))
        .map(|v| v.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_default()
}

/// Lirik dari semua tag yang ada (USLT/©lyr/LYRICS/Lyrics), bukan hanya tag primer.
fn embedded_lyrics(tagged: &lofty::file::TaggedFile) -> String {
    for tag in tagged.tags() {
        if let Some(l) = tag.get_string(&ItemKey::Lyrics) {
            let l = l.trim();
            if !l.is_empty() {
                return l.to_string();
            }
        }
    }
    String::new()
}

/// Judul cadangan dari nama file bila tag kosong.
fn title_from_path(path: &str) -> String {
    std::path::Path::new(path)
        .file_stem()
        .map(|s| s.to_string_lossy().replace('_', " ").trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| path.to_string())
}

/// Baca metadata satu file audio.
pub fn read_meta(path: &str) -> Result<TrackMeta, String> {
    let tagged = read_from_path(path).map_err(|e| e.to_string())?;
    let tag = tagged.primary_tag();
    let props = tagged.properties();

    let title = tag_text(tag, |t| t.title());
    let artist = tag_text(tag, |t| t.artist());
    let album = tag_text(tag, |t| t.album());
    let genre = tag_text(tag, |t| t.genre());
    let lyrics = embedded_lyrics(&tagged);

    Ok(TrackMeta {
        path: path.to_string(),
        title: if title.is_empty() { title_from_path(path) } else { title },
        artist,
        album,
        genre,
        year: tag.and_then(|t| t.year()).unwrap_or(0),
        track: tag.and_then(|t| t.track()).unwrap_or(0),
        duration_ms: props.duration().as_millis() as u64,
        sample_rate: props.sample_rate().unwrap_or(0),
        lyrics,
    })
}

/// Album art pertama (biasanya sampul depan) sebagai byte mentah.
pub fn album_art(path: &str) -> Result<Option<Vec<u8>>, String> {
    let tagged = read_from_path(path).map_err(|e| e.to_string())?;
    let tag = tagged.primary_tag();
    if let Some(t) = tag {
        if let Some(pic) = t.pictures().first() {
            let data = pic.data();
            if !data.is_empty() {
                return Ok(Some(data.to_vec()));
            }
        }
    }
    Ok(None)
}
