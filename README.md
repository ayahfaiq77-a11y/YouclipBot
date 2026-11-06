# YouClipBot Android v1

Project Android Studio berbasis Kotlin + Jetpack Compose.

## Yang sudah berfungsi
- Memilih video lokal melalui Android Storage Access Framework.
- Sampling frame dan deteksi wajah on-device memakai ML Kit.
- Membentuk kandidat 30 detik, memberi skor berdasarkan konsistensi/ukuran/posisi wajah.
- Memilih maksimal 5 interval non-overlap.
- Mengekspor MP4 9:16 dengan Media3 Transformer.

## Batasan v1
- Ranking ini memakai sinyal visual wajah, belum transkripsi Whisper atau analisis semantik viral.
- Analisis menyimpan rata-rata posisi wajah, tetapi Media3 v1 menggunakan crop 9:16 terpusat saat ekspor. Dynamic per-frame crop mengikuti wajah memerlukan custom GlShaderProgram/MediaPipe frame processor dan dijadwalkan untuk v2.
- File keluaran berada di Android/data/com.youclipbot.android/files dan dapat diakses melalui Android Studio Device Explorer. Integrasi MediaStore/Share Sheet adalah tahap berikutnya.
- URL YouTube tidak disediakan. Gunakan video lokal yang Anda miliki atau berhak proses.

## Membuka project
1. Ekstrak ZIP.
2. Android Studio > Open > pilih folder YouClipBotAndroidV1.
3. Gunakan JDK 17.
4. Tunggu Gradle Sync.
5. Jalankan pada perangkat fisik Android 8.0+ (API 26+). Perangkat fisik lebih disarankan daripada emulator untuk codec video.

## Build APK
Android Studio > Build > Build APK(s).

## Catatan Gradle
ZIP tidak menyertakan Gradle wrapper binary. Android Studio dapat melakukan sync menggunakan Gradle yang dikelolanya, atau jalankan `gradle wrapper` sekali jika ingin menambahkan wrapper ke repository.
