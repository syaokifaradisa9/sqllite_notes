package com.example.sqllite_notes.helpers

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * AudioSelectorHelper adalah class pembantu yang menangani proses pemilihan
 * dan pengelolaan file audio dalam aplikasi Notes.
 *
 * Class
 ini bertanggung jawab untuk:
 * 1. Membuka pemilih audio sistem
 * 2. Mendapatkan informasi file audio yang dipilih (nama, durasi)
 * 3. Menyalin file audio yang dipilih ke penyimpanan internal aplikasi
 * 4. Memberi tahu komponen lain (AddNoteActivity) ketika audio dipilih
 */
class AudioSelectorHelper(
    private val activity: AppCompatActivity,
    private val onAudioSelected: (Uri, String) -> Unit
) {
    // Tag untuk log debugging
    private val TAG = "AudioSelectorHelper"

    /**
     * Launcher untuk memulai aktivitas pemilihan audio.
     * Menggunakan Activity Result API untuk menangani hasil dari pemilihan audio secara asinkron.
     *
     * Ketika pengguna memilih file audio, callback ini akan dipanggil dengan URI dan
     * judul audio yang dipilih.
     */
    private val pickAudioLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Pengguna telah memilih file audio
            result.data?.data?.let { uri ->
                // Dapatkan nama file audio
                val audioTitle = getAudioFileName(uri) ?: "Audio Recording"

                // Panggil callback dengan URI dan judul audio
                onAudioSelected(uri, audioTitle)
            }
        }
    }

    /**
     * Membuka pemilih audio sistem untuk memilih file audio.
     *
     * Metode ini memulai aktivitas sistem untuk memilih file audio
     * dari penyimpanan perangkat.
     */
    fun openAudioPicker() {
        // Buat intent untuk membuka pemilih audio dari galeri media
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

        // Luncurkan pemilih audio dan tunggu hasilnya di pickAudioLauncher
        pickAudioLauncher.launch(intent)
    }

    /**
     * Mendapatkan nama file dari audio yang dipilih.
     *
     * Metode ini mencoba mendapatkan nama file dari ContentResolver terlebih dahulu.
     * Jika gagal, mencoba mendapatkannya dari path URI.
     *
     * @param uri URI file audio yang dipilih
     * @return Nama file audio, atau null jika tidak dapat ditemukan
     */
    fun getAudioFileName(uri: Uri): String? {
        // Coba dapatkan informasi file dari ContentResolver
        val cursor = activity.contentResolver.query(uri, null, null, null, null)

        return cursor?.use {
            // Dapatkan indeks kolom untuk nama file
            val nameIndex = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

            // Jika kolom ditemukan dan cursor memiliki data
            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex)
            }
            null
        } ?: run {
            // Jika tidak bisa mendapatkan nama dari ContentResolver,
            // coba ekstrak dari path URI
            uri.lastPathSegment?.split("/")?.lastOrNull()
        }
    }

    /**
     * Mendapatkan durasi file audio dalam milidetik.
     *
     * Metode ini menggunakan MediaMetadataRetriever untuk mengekstrak
     * durasi dari file audio.
     *
     * @param uri URI file audio
     * @return Durasi audio dalam milidetik, atau 0 jika gagal
     */
    fun getAudioDuration(uri: Uri): Long {
        try {
            // Gunakan MediaMetadataRetriever untuk mendapatkan metadata audio
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(activity, uri)

            // Ekstrak durasi dari metadata
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            // Lepaskan sumber daya
            retriever.release()

            // Konversi string durasi ke Long, atau kembalikan 0 jika null
            return durationStr?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            // Catat error dan kembalikan 0 jika terjadi kesalahan
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            return 0
        }
    }

    /**
     * Menyalin file audio ke penyimpanan internal aplikasi.
     *
     * Penting untuk menyalin file audio ke penyimpanan internal
     * agar aplikasi tetap dapat mengakses file tersebut bahkan
     * setelah restart atau jika file asli dihapus dari perangkat.
     *
     * @param uri URI file audio yang akan disalin
     * @return Path absolut ke file yang disalin, atau string kosong jika gagal
     */
    fun copyAudioToInternalStorage(uri: Uri): String {
        // Dapatkan nama file atau buat nama default jika tidak tersedia
        val fileName = getAudioFileName(uri) ?: "audio_${System.currentTimeMillis()}.mp3"

        // Buat direktori untuk menyimpan file audio jika belum ada
        val file = File(activity.filesDir, "audio_notes")

        if (!file.exists()) {
            file.mkdirs()
        }

        // Buat file tujuan
        val audioFile = File(file, fileName)

        try {
            // Buka input stream dari URI audio
            activity.contentResolver.openInputStream(uri)?.use { input ->
                // Buka output stream ke file tujuan
                FileOutputStream(audioFile).use { output ->
                    // Buffer untuk menyalin data secara efisien
                    val buffer = ByteArray(4 * 1024) // 4K buffer
                    var read: Int

                    // Salin data dari input ke output
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }

                    // Pastikan semua data ditulis
                    output.flush()
                }
            }

            // Kembalikan path absolut file yang berhasil disalin
            return audioFile.absolutePath
        } catch (e: IOException) {
            // Catat error dan kembalikan string kosong jika gagal
            Log.e(TAG, "Error copying audio file: ${e.message}")
            return ""
        }
    }
}