package com.example.sqllite_notes.models

import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.ImageView

/**
 * File ini mendefinisikan dua sealed class yang digunakan untuk merepresentasikan
 * konten catatan dalam dua konteks berbeda:
 *
 * 1. NoteContentItem: Representasi berbasis UI yang digunakan selama pengeditan catatan
 * 2. NotePart: Representasi berbasis data yang digunakan untuk penyimpanan dalam database
 *
 * Pemisahan ini menerapkan prinsip "Separation of Concerns" (Pemisahan Tanggung Jawab)
 * yang memisahkan antara tampilan (UI) dan model data yang disimpan.
 */

/**
 * NoteContentItem adalah sealed class yang merepresentasikan item konten
 * catatan dalam konteks UI (saat pengguna mengedit catatan).
 *
 * Class ini menyimpan referensi ke komponen UI aktual (EditText, ImageView, dll)
 * yang digunakan untuk menampilkan dan berinteraksi dengan konten catatan.
 *
 * Karena menyimpan referensi ke komponen UI, class ini hanya digunakan selama
 * proses edit catatan dan tidak cocok untuk penyimpanan jangka panjang.
 */
sealed class NoteContentItem {
    /**
     * Merepresentasikan teks dalam catatan.
     *
     * @param editText Komponen EditText yang menampilkan dan memungkinkan
     *                 pengguna mengedit teks
     */
    data class Text(val editText: EditText) : NoteContentItem()

    /**
     * Merepresentasikan gambar dalam catatan.
     *
     * @param imageView Komponen ImageView yang menampilkan gambar
     * @param uri URI gambar yang dipilih pengguna (untuk gambar baru)
     * @param base64String String Base64 representasi gambar (untuk gambar yang telah disimpan)
     *                     Digunakan untuk menyimpan gambar dalam database
     */
    data class Image(
        val imageView: ImageView,
        val uri: Uri,
        var base64String: String = ""
    ) : NoteContentItem()

    /**
     * Merepresentasikan audio dalam catatan.
     *
     * @param audioView Komponen View yang menampilkan player audio
     * @param uri URI file audio yang dipilih pengguna (untuk audio baru)
     * @param audioPath Path ke file audio di penyimpanan (untuk audio yang telah disimpan)
     * @param title Judul/nama audio yang ditampilkan ke pengguna
     */
    data class Audio(
        val audioView: View,
        val uri: Uri,
        var audioPath: String = "",
        var title: String = "Audio Recording"
    ) : NoteContentItem()
}

/**
 * NotePart adalah sealed class yang merepresentasikan item konten catatan
 * dalam konteks penyimpanan data (tanpa referensi ke komponen UI).
 *
 * Class ini hanya menyimpan data murni (teks, path, string) yang diperlukan
 * untuk merekonstruksi konten catatan, tanpa ketergantungan pada komponen UI.
 *
 * NotePart digunakan untuk:
 * 1. Menyerialisasi konten catatan sebelum disimpan ke database
 * 2. Mendeserialisasi konten catatan setelah dibaca dari database
 * 3. Mempertahankan data catatan terlepas dari status UI
 */
sealed class NotePart {
    /**
     * Merepresentasikan bagian teks dari catatan.
     *
     * @param text Konten teks aktual
     */
    data class TextPart(val text: String) : NotePart()

    /**
     * Merepresentasikan bagian gambar dari catatan.
     *
     * @param imagePath String yang berisi data gambar terenkode Base64
     *                  atau path ke file gambar
     */
    data class ImagePart(val imagePath: String) : NotePart()

    /**
     * Merepresentasikan bagian audio dari catatan.
     *
     * @param audioPath Path ke file audio atau data terenkode
     * @param title Judul/nama tampilan untuk rekaman audio
     */
    data class AudioPart(val audioPath: String, val title: String = "Audio Recording") : NotePart()
}

/**
 * ALUR DATA DALAM APLIKASI NOTES
 *
 * Transformasi dari UI ke Penyimpanan (saat menyimpan catatan):
 *
 * 1. Saat pengeditan catatan:
 *    - Aplikasi mengelola daftar NoteContentItem
 *    - Setiap elemen UI (EditText, ImageView, AudioPlayerView) direferensikan
 *      dalam NoteContentItem yang sesuai
 *    - Pengguna berinteraksi langsung dengan komponen UI ini
 *
 * 2. Saat catatan disimpan:
 *    - Untuk setiap NoteContentItem:
 *      * Text → TextPart: Teks diambil dari EditText.text
 *      * Image → ImagePart: URI gambar dikonversi ke Base64 string
 *      * Audio → AudioPart: URI audio disalin ke penyimpanan internal, path disimpan
 *    - Daftar NotePart ini kemudian diserialisasi ke string untuk disimpan dalam database
 *
 * Transformasi dari Penyimpanan ke UI (saat membuka catatan):
 *
 * 1. Saat catatan dibuka:
 *    - String dari database dideserialisasi menjadi daftar NotePart
 *    - Untuk setiap NotePart:
 *      * TextPart → Text: EditText baru dibuat dan diisi dengan teks
 *      * ImagePart → Image: Base64 string dikonversi ke Bitmap, ImageView dibuat untuk menampilkannya
 *      * AudioPart → Audio: AudioPlayerView dibuat dan dikonfigurasi dengan path audio
 *    - Komponen UI baru dan data dari NotePart digabungkan menjadi NoteContentItem baru
 *
 */