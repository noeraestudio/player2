<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# NOERAE Music & Video Player (Hi-Res Audio Studio)

This contains everything you need to run your app locally or build APKs.

View your app in AI Studio: https://ai.studio/apps/12eccfab-9ec9-4c74-b68b-9df764fa82dd

## 📱 Cara Mendapatkan File APK (Download APK)

1. **Langsung dari Direktori / Folder Repositori (Tanpa Build):**
   - Buka folder `release/` di halaman utama repositori GitHub Anda.
   - Klik file **`app-debug.apk`** lalu klik tombol **Download** / **View raw** untuk mengunduh langsung ke HP Android Anda.

2. **Dari Menu Google AI Studio:**
   - Buka menu Settings di Google AI Studio.
   - Pilih opsi **Export as APK/AAB**.

## 💻 Menjalankan Secara Lokal (Android Studio)

**Prasyarat:** [Android Studio Jellyfish / Koala / Ladybug atau yang lebih baru](https://developer.android.com/studio)

1. Buka Android Studio.
2. Pilih **Open** dan pilih folder proyek ini.
3. Tunggu hingga Gradle Sync selesai.
4. Buat file `.env` di direktori proyek dan tambahkan `GEMINI_API_KEY` jika ingin mengaktifkan fitur pembuatan lirik AI otomatis.
5. Jalankan `gradle assembleDebug` atau tekan tombol **Run** (Shift+F10) untuk menginstal langsung ke HP/Emulator Anda.

