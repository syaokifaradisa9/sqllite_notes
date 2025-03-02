package com.example.sqllite_notes.helpers

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * ImageSelectorHelper adalah class
 pembantu yang menangani seluruh proses
 * pemilihan dan pengelolaan gambar dalam aplikasi Notes.
 *
 * Class
 ini bertanggung jawab untuk:
 * 1. Membuka pemilih gambar dari galeri perangkat
 * 2. Menerima URI gambar yang dipilih pengguna
 * 3. Membuat dan mengkonfigurasi ImageView untuk menampilkan gambar
 * 4. Menjaga aspek rasio gambar saat ditampilkan
 * 5. Memberi tahu komponen lain (AddNoteActivity) ketika gambar dipilih
 */
class ImageSelectorHelper(
    private val activity: AppCompatActivity,
    private val onImageSelected: (Uri) -> Unit
) {
    /**
     * Launcher untuk memulai aktivitas pemilihan gambar.
     * Menggunakan Activity Result API untuk menangani hasil dari pemilihan gambar secara asinkron.
     *
     * Ketika pengguna memilih gambar, callback ini akan dipanggil dengan URI gambar yang dipilih.
     */
    private val pickImageLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Pengguna telah memilih gambar
            result.data?.data?.let { uri ->
                // Panggil callback dengan URI gambar yang dipilih
                onImageSelected(uri)
            }
        }
    }

    /**
     * Membuka pemilih gambar sistem untuk memilih gambar dari galeri.
     *
     * Metode ini memulai aktivitas sistem untuk memilih gambar
     * dari penyimpanan perangkat.
     */
    fun openImagePicker() {
        // Buat intent untuk membuka pemilih gambar dari galeri media
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        // Luncurkan pemilih gambar dan tunggu hasilnya di pickImageLauncher
        pickImageLauncher.launch(intent)
    }

    /**
     * Membuat dan mengkonfigurasi ImageView untuk menampilkan gambar yang dipilih.
     *
     * Metode ini:
     * 1. Memeriksa dimensi gambar untuk menghitung rasio aspek
     * 2. Membuat ImageView dengan konfigurasi yang sesuai
     * 3. Memastikan gambar ditampilkan dengan rasio aspek yang benar
     *
     * @param imageUri URI gambar yang akan ditampilkan
     * @return ImageView yang telah dikonfigurasi dengan gambar
     */
    fun createImageView(imageUri: Uri): ImageView {
        // Dapatkan dimensi gambar untuk menghitung rasio aspek
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true  // Hanya baca informasi dimensi tanpa memuat seluruh gambar
        }

        try {
            // Buka input stream dari URI gambar untuk membaca dimensinya
            activity.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Hitung rasio aspek - default ke 4:3 jika tidak dapat menentukan
        val aspectRatio = if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        } else {
            4f / 3f  // Rasio aspek default jika tidak dapat menentukan
        }

        // Buat dan konfigurasi ImageView
        return ImageView(activity).apply {
            // Buat ID unik untuk view
            id = ViewGroup.generateViewId()

            // Atur parameter layout
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                // Berikan margin untuk tampilan yang lebih baik
                setMargins(16, 8, 16, 8)
            }

            // Atur properti untuk mempertahankan rasio aspek
            adjustViewBounds = true  // Penting untuk mempertahankan rasio aspek
            scaleType = ImageView.ScaleType.FIT_CENTER

            // Tetapkan gambar dari URI
            setImageURI(imageUri)

            // Deskripsi untuk aksesibilitas
            contentDescription = "Note Image"
        }
    }
}