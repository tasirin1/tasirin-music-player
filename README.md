# Tasirin Musik Player 🎵

Pemutar musik lokal untuk **Android 5.0+**, dirancang untuk **TV / STB** (navigasi
DPAD penuh) dengan **remote web dari HP/PC** — kecil, cepat, tanpa iklan.

## Fitur

- 🎶 Putar musik lokal: MP3, M4A, AAC, OGG, WAV, FLAC, OPUS, AMR
- 📁 Pindai folder bebas (rekursif, default `/sdcard/Music`)
- ▶️ Kontrol lengkap: play/pause, sebelumnya/berikutnya, seek bar, acak, ulang
- 📡 **Remote web** bawaan: buka `http://<ip-stb>:8090` dari HP/PC untuk
  memilih lagu & kontrol pemutar — tanpa install app lain
- 📺 Ramah TV: tombol besar, fokus DPAD kontras tinggi, tema gelap konsisten
- 🔔 Pemutaran latar dengan notifikasi kontrol (sebelum/putar/jeda/berikutnya)
- 🎮 Tombol media remote TV didukung (MediaSession)
- 💾 APK **super kecil (< 100 KB)** — tanpa library berat, tanpa iklan

## Cara Pakai

1. Install APK dari [GitHub Release](https://github.com/tasirin1/tasirin-music-player/releases).
2. Buka app, isi **folder musik** (default `/sdcard/Music`), tekan **Pindai**.
3. Pilih lagu di daftar untuk memutar. Kontrol ada di bawah layar.
4. **Remote dari HP/PC**: tekan tombol **Remote: Nyala**, lalu buka alamat yang
   muncul di layar (contoh `http://192.168.0.103:8090`).

## Remote Web

Halaman remote berisi daftar lagu, tombol kontrol, seek bar, acak & ulang —
status diperbarui real-time (1 detik). Bisa dipakai banyak perangkat sekaligus.

## Bangun dari Source

```bash
./gradlew :app:assembleRelease
```

- Java murni (tanpa AndroidX), `minSdk 21`, `targetSdk 28`
- R8 + resource shrinking aktif di build release
- CI GitHub Actions otomatis build & tanda tangan keystore `tasirin`

## Lisensi

MIT License — bebas dipakai & dimodifikasi.
