# Aurevia Admin (Kotlin)

Aplikasi panel terpisah untuk owner/developer Aurevia, ditulis ulang penuh dengan Kotlin + Jetpack Compose
(menggantikan panel WebView lama).

- Masuk dengan akun `owner` / `developer` / `admin` lewat `POST /v1/auth/login`.
- **Ringkasan**: jumlah pengguna, staf, akun bersentang, perangkat push, room Live, status FCM.
- **Pengguna**: cari akun, beri centang, pilih salah satu dari 10 warna badge, ubah role (khusus owner).
- **Siarkan**: kirim pengumuman yang muncul di notifikasi HP *dan* lonceng Beranda semua pengguna.
- Sesi disimpan terenkripsi AES-GCM dengan kunci Android Keystore (tidak pernah plaintext di disk).

Build: `./gradlew :app:assembleRelease` (JDK 17, compileSdk 35) atau lewat workflow
`.github/workflows/build-admin-app.yml`.
