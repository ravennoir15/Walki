# WALKIE IBU — prototipe Android offline

## Status
Ini adalah prototipe awal untuk uji coba jaringan Wi-Fi lokal. Belum diuji pada semua merek Android dan belum boleh dianggap sebagai perangkat keselamatan/darurat.

## Cara kerja
- Satu HP membuat hotspot Wi-Fi.
- HP host menjalankan server TCP port 50555.
- HP anggota bergabung ke hotspot yang sama dan memasukkan IP host.
- Audio PCM mono 16 kHz dikirim melalui jaringan lokal; tidak membutuhkan internet.

## Build APK dari HP Android
1. Buat repositori GitHub baru bernama `WALKIE-IBU`.
2. Unggah seluruh isi folder proyek ini ke repositori (bukan folder ZIP sebagai satu file).
3. Buka tab Actions dan jalankan workflow `Build WALKIE IBU APK`.
4. Setelah selesai, buka hasil workflow dan unduh artifact `WALKIE-IBU-debug`.
5. Ekstrak artifact, lalu pasang `app-debug.apk` di HP Android.

## Uji coba
1. Pada HP host, aktifkan hotspot pribadi dari Pengaturan Android.
2. Sambungkan HP anggota ke hotspot itu.
3. Buka aplikasi pada host, tekan `JADIKAN HP INI HOST`.
4. Pada HP anggota, isi alamat IP host. `192.168.43.1` hanya alamat umum; alamat sebenarnya dapat berbeda sesuai perangkat/hotspot.
5. Tekan `GABUNG KE HOST`.
6. Izinkan mikrofon. Tekan dan tahan tombol hijau untuk bicara.

## Batasan prototipe
- Target 5–10 perangkat dan jarak 100–500 meter belum dijamin; jarak bergantung access point, antena, penghalang, interferensi, dan daya perangkat.
- Untuk jangkauan lebih luas, gunakan access point/router outdoor yang sesuai, tetap tanpa koneksi internet.
- Prototipe ini belum menerapkan penguncian giliran bicara (floor control), enkripsi audio, pemulihan koneksi otomatis, atau layanan foreground untuk penggunaan lama.
- Jangan gunakan untuk keadaan darurat atau komunikasi yang memerlukan keandalan tinggi sebelum diuji dan dikembangkan lebih lanjut.
