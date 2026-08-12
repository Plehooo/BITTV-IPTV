# BITTV IPTV - Kotlin player update

Perubahan hanya pada bagian Android Kotlin/player dan data channel. Identitas project tetap `BITTV IPTV` / `com.bittv.iptv`.

## Streaming support
- HLS `.m3u8`
- MPEG-DASH `.mpd`
- Per-channel HTTP headers
- Global headers
- `User-Agent`, `Referer`, `Origin`, `Accept`, dan header lain melalui konfigurasi
- Redirect HTTP diizinkan
- Timeout connect/read
- Retry playback otomatis sampai 3 kali
- URL tokenized tetap dipakai apa adanya; token harus masih valid dari provider.

## Menambah channel
Edit `app/src/main/assets/config.json` dan tambahkan object di `channels`.

Contoh:
```json
{
  "id": "channel-baru",
  "name": "TV Baru",
  "category": "Indonesia",
  "url": "https://example.com/live.m3u8",
  "headers": {
    "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
    "Referer": "https://example.com/",
    "Origin": "https://example.com"
  }
}
```

Untuk DASH, cukup gunakan URL `.mpd`; player mendeteksi `.mpd`/`.m3u8` dan memilih MIME type yang sesuai.

## Build
Buka folder ini di Android Studio lalu pilih `Build > Make Project` atau `Build > Build APK(s)`.

CLI juga tersedia lewat `./gradlew :app:assembleDebug`; environment yang digunakan untuk memodifikasi ZIP ini tidak mempunyai akses internet untuk mengunduh Gradle distribution/dependencies, jadi APK tidak dapat divalidasi/built di sini.
