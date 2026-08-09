# Tasirin Musik 🎵

Pemutar musik lokal yang **elegan** untuk Android 10+ — antarmuka terinspirasi
Apple Music, inti pemrosesan ditulis dalam **Rust**, tanpa remote web, tanpa
iklan.

## Fitur

- 🎨 **Tampilan ala Apple Music**: tab Sekarang Diputar (sampul besar + latar
  berwarna), perpustakaan dengan pencarian, tab Album (kisi album), dan
  mini player.
- 🎤 **Lirik ala Oto Music**: ketuk sampul untuk lirik — lirik tertanam
  (USLT/LRC) tampil sinkron dengan highlight baris aktif, atau dicari online
  lewat LRCLIB bila tidak tertanam.
- 📁 **Pilih folder lewat picker sistem** (SAF): tekan "Folder musik" di
  Pengaturan, pilih folder lewat dialog sistem, lalu otomatis dipindai.
- 🦀 **Inti Rust** (`libmusiccore.so` via JNI): pemindaian pustaka paralel dan
  parsing metadata memakai crate [`lofty`](https://crates.io/crates/lofty)
  (ID3v2, MP4, FLAC, Vorbis, APE, WMA, dll) — cepat & hemat memori.
- 🖼️ Album art diekstrak dari tag, di-cache ke disk + memori.
- ▶️ Pemutaran latar lengkap: notifikasi media dengan artwork
  (play/pause/next/prev), kontrol di **lock screen**, tombol media headset
  (`MediaButtonReceiver`), dan tampil di **Android Auto** lewat
  `MediaBrowserServiceCompat` (browse pustaka + putar dari media id).
- 🎛️ Kontrol lengkap: acak, ulang (mati/semua/satu), seek bar, lirik sinkron.
- 🌓 Tema sistem/gelap/terang.
- 📱 Android 10+ (minSdk 29), APK kecil (R8 + resource shrinking).

## Cara Pakai

1. Pasang APK dari [GitHub Release](https://github.com/tasirin1/tasirin-music-player/releases).
2. Izinkan akses audio (Android 13+ meminta `READ_MEDIA_AUDIO`).
3. Di **Pengaturan**, tekan "Folder musik" untuk memilih folder lewat picker
   sistem (SAF) — pemindaian otomatis berjalan setelah folder dipilih.
4. Pilih lagu di Perpustakaan; layar "Sekarang Diputar" terbuka otomatis.

## Arsitektur

```
rust/music-core/          Inti Rust (cdylib → libmusiccore.so)
  src/lib.rs              Bridge JNI (com.tasirin.musicplayer.MusicCore)
  src/scanner.rs          Pemindaian folder paralel (multi-thread)
  src/meta.rs             Metadata + album art (lofty)
app/src/main/kotlin/      UI Compose + service (100% Kotlin)
  .../ui/                 Sekarang Diputar, Perpustakaan, Album, Pengaturan
  .../MusicService        Notifikasi media (latar)
  .../MediaSessionManager MediaSession tunggal: headset, lock screen, Android Auto
  .../MusicBrowserService Browser pustaka untuk Android Auto
  .../MediaLibrary        Akses pustaka via MediaStore (AA & resolusi media id)
  .../PlayerController    Kontrol pemutaran (MediaPlayer Android)
```

Alasan MediaPlayer (bukan decode Rust): decode hardware = hemat baterai dan
mendukung banyak format tanpa biaya CPU. Rust menangani bagian berat yang
bersifat *compute*: pemindaian & metadata.

## Bangun dari Source

**Wajib: build hanya lewat GitHub Actions** (jangan build lokal). Commit ke
`main` → workflow otomatis:

1. Compile inti Rust untuk `arm64-v8a` & `armeabi-v7a` (cargo-ndk).
2. `gradle testDebugUnitTest assembleRelease` dengan keystore resmi Tasirin.
3. Upload APK ke Release `v1.0` (versionCode = run number CI).

```bash
# Pantau build
gh run list --limit 1 --json databaseId,status,conclusion
gh run watch <run-id> --exit-status
```

## Struktur Repo

- `rust/music-core/` — crate Rust (workspace root `Cargo.toml`)
- `app/` — modul Android (Kotlin + Compose Material3)
- `.github/workflows/build.yml` — build + tanda tangan + release otomatis

## Aturan Pengembangan

- **Jangan build lokal** — selalu CI (GitHub Actions).
- Satu commit satu tujuan logis, pesan `type: deskripsi`, bahasa Indonesia.
- Perubahan metadata/scan → `rust/music-core/`; UI → `app/.../ui/`;
  pemutaran/service → `PlayerController.kt` / `MusicService.kt`.
- `versionName` tetap `1.0`; `versionCode` diatur CI.

## Lisensi

MIT — lihat [LICENSE](LICENSE).
