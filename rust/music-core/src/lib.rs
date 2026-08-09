//! Bridge JNI: fungsi native yang dipanggil dari Kotlin
//! (`com.tasirin.musicplayer.MusicCore`).
//!
//! Semua fungsi dibungkus `catch_unwind` — panic tidak boleh menembus
//! batas JNI (UB).

mod meta;
mod scanner;

use jni::objects::JObject;
use jni::sys::{jbyteArray, jstring};
use jni::JNIEnv;

const VERSION: &str = env!("CARGO_PKG_VERSION");

fn new_string(env: &JNIEnv, s: &str) -> jstring {
    env.new_string(s).map(|v| v.into_raw()).unwrap_or(std::ptr::null_mut())
}

/// `version(): String` — versi inti Rust.
#[no_mangle]
pub extern "system" fn Java_com_tasirin_musicplayer_MusicCore_version(
    env: JNIEnv,
    _this: JObject,
) -> jstring {
    new_string(&env, VERSION)
}

/// `scan(root: String): String` — JSON daftar lagu (`[{...}]`).
/// Gagal apa pun → `[]` (Kotlin tetap dapat hasil kosong, bukan null).
#[no_mangle]
pub extern "system" fn Java_com_tasirin_musicplayer_MusicCore_scan(
    mut env: JNIEnv,
    _this: JObject,
    root: jni::objects::JString,
) -> jstring {
    let json = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let root_str: String = env.get_string(&root).map(|s| s.into()).unwrap_or_default();
        let tracks = scanner::scan_library(&root_str);
        serde_json::to_string(&tracks).unwrap_or_else(|_| "[]".to_string())
    }));
    let json = json.unwrap_or_else(|_| "[]".to_string());
    new_string(&env, &json)
}

/// `albumArt(path: String): ByteArray?` — byte sampul lagu, atau null.
#[no_mangle]
pub extern "system" fn Java_com_tasirin_musicplayer_MusicCore_albumArt(
    mut env: JNIEnv,
    _this: JObject,
    path: jni::objects::JString,
) -> jbyteArray {
    let bytes = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> Option<Vec<u8>> {
        let path_str: String = env.get_string(&path).map(|s| s.into()).ok()?;
        meta::album_art(&path_str).ok().flatten()
    }));
    match bytes {
        Ok(Some(b)) => env.byte_array_from_slice(&b)
            .map(|arr| arr.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        _ => std::ptr::null_mut(),
    }
}
