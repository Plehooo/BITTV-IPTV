# BITTV IPTV

UI mobile-first putih, dark/AMOLED, search, category, favorite, history, HLS player, notifikasi remote, dan remote config.

## Update tanpa update APK

APK membaca `www/data/config.json` saat startup dan setiap 5 menit. Setelah project online di GitHub, ubah file config di repository/hosting yang digunakan aplikasi. Untuk channel, banner, metadata, dan pengumuman, APK tidak perlu diinstall ulang.

**Catatan penting:** kode aplikasi yang dibundel dalam APK tidak otomatis berubah hanya karena GitHub berubah. Jika ingin seluruh UI/JavaScript juga berubah tanpa APK update, gunakan mode remote web-app/asset server atau sistem OTA yang dirancang khusus. Untuk keamanan dan stabilitas, template ini memulai dari remote data/config.

## Termux

```bash
pkg update
pkg install git nodejs
git clone https://github.com/USERNAME/REPOSITORY.git
cd REPOSITORY
npm install
npx cap add android
npx cap sync
git add .
git commit -m "BITTV IPTV"
git push
```

## GitHub Actions

Workflow berada di `.github/workflows/build-apk.yml`. Push ke `main` akan memicu build APK dan menyimpan APK sebagai artifact.
