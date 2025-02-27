package com.example.sqllite_notes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import com.example.sqllite_notes.databinding.ActivityAddNoteBinding
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AddNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddNoteBinding
    private val noteContentItems = mutableListOf<NoteContentItem>()

    // Program Utama
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d("AddNoteActivity", "Sebelum inflate binding")
            binding = ActivityAddNoteBinding.inflate(layoutInflater)
            Log.d("AddNoteActivity", "Setelah inflate binding")
            setContentView(binding.root)
            Log.d("AddNoteActivity", "Setelah set content view")

            setupToolbar()
            Log.d("AddNoteActivity", "Setelah setup toolbar")
            setupListeners()
            Log.d("AddNoteActivity", "Setelah setup listeners")
            initializeNoteContent()
            Log.d("AddNoteActivity", "Setelah initialize content")
        } catch (e: Exception) {
            Log.e("AddNoteActivity", "Error dalam onCreate: ${e.message}", e)
            Toast.makeText(this, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Activity Result Launcher untuk image
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                addImageToNote(uri)
            }
        }
    }

    // Activity Result Launcher untuk audio
    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                addAudioToNote(uri)
            }
        }
    }

    // Buat metode terpisah untuk meminta izin
    private fun requestSpecificPermissions(requestType: PermissionRequest) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (requestType) {
                PermissionRequest.IMAGE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                PermissionRequest.AUDIO -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                else -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        requestPermissionLauncher.launch(permissionsToRequest)
    }

    // Request permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Periksa jika semua izin yang diperlukan diberikan
        val mediaPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true

        if (mediaPermissionGranted) {
            // Izin diberikan, lanjutkan dengan operasi yang memerlukan izin
            when (currentPermissionRequest) {
                PermissionRequest.IMAGE -> openImagePicker()
                PermissionRequest.AUDIO -> openAudioPicker()
            }
        } else {
            // Izin ditolak - periksa apakah perlu menampilkan "rationale"
            val permissionsToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            // Periksa apakah kita harus menampilkan rationale untuk salah satu izin
            val shouldShowRationale = permissionsToCheck.any {
                shouldShowRequestPermissionRationale(it)
            }

            if (shouldShowRationale) {
                // Pengguna menolak izin tapi belum memilih "Don't ask again"
                // Tampilkan dialog yang menjelaskan mengapa izin diperlukan
                AlertDialog.Builder(this)
                    .setTitle("Izin Diperlukan")
                    .setMessage("Aplikasi memerlukan akses ke media untuk menambahkan gambar dan audio ke catatan Anda.")
                    .setPositiveButton("Coba Lagi") { _, _ ->
                        // Minta izin lagi ketika pengguna klik "Coba Lagi"
                        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            when (currentPermissionRequest) {
                                PermissionRequest.IMAGE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                                PermissionRequest.AUDIO -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                                else -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
                            }
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        // Minta izin lagi
                        requestSpecificPermissions(currentPermissionRequest)
                    }
                    .setNegativeButton("Batal") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this,
                            "Fitur ini memerlukan izin media untuk berfungsi",
                            Toast.LENGTH_LONG).show()
                    }
                    .show()
            } else {
                // Pengguna mungkin telah menolak permanen (dengan "Don't ask again")
                // Arahkan ke pengaturan aplikasi
                AlertDialog.Builder(this)
                    .setTitle("Izin Media Diperlukan")
                    .setMessage("Aplikasi memerlukan akses ke media untuk berfungsi dengan baik. Silakan aktifkan izin di pengaturan aplikasi.")
                    .setPositiveButton("Buka Pengaturan") { _, _ ->
                        // Buka halaman pengaturan aplikasi
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Batal") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this,
                            "Fitur ini tidak dapat digunakan tanpa izin media",
                            Toast.LENGTH_LONG).show()
                    }
                    .show()
            }
        }
    }

    private enum class PermissionRequest {
        IMAGE, AUDIO
    }

    private var currentPermissionRequest = PermissionRequest.IMAGE

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupListeners() {
        // Tombol tambah gambar
        binding.btnAddImage.setOnClickListener {
            currentPermissionRequest = PermissionRequest.IMAGE
            checkPermissionAndOpenPicker(PermissionRequest.IMAGE)
        }

        // Tombol tambah audio
        binding.btnAddAudio.setOnClickListener {
            currentPermissionRequest = PermissionRequest.AUDIO
            checkPermissionAndOpenPicker(PermissionRequest.AUDIO)
        }

        // Tombol simpan
        binding.btnSimpan.setOnClickListener {
            saveNote()
        }
    }

    private fun initializeNoteContent() {
        // Inisialisasi dengan hanya satu EditText pertama
        val firstEditText = binding.edtContent1
        noteContentItems.add(NoteContentItem.Text(firstEditText))

        // Tambahkan listener backspace pada EditText pertama
        addBackspaceListenerToEditText(firstEditText, 0)
    }

    private fun checkPermissionAndOpenPicker(request: PermissionRequest) {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            when (request) {
                PermissionRequest.IMAGE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                PermissionRequest.AUDIO -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            // Izin sudah diberikan
            when (request) {
                PermissionRequest.IMAGE -> openImagePicker()
                PermissionRequest.AUDIO -> openAudioPicker()
            }
        } else {
            // Meminta izin
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        pickAudioLauncher.launch(intent)
    }

    /**
     * Menambahkan key listener pada EditText untuk mendeteksi penekanan backspace
     */
    private fun addBackspaceListenerToEditText(editText: EditText, position: Int) {
        editText.setOnKeyListener { view, keyCode, event ->
            // Deteksi penekanan backspace saat EditText kosong
            if (keyCode == KeyEvent.KEYCODE_DEL
                && event.action == KeyEvent.ACTION_DOWN
                && editText.text.isEmpty()) {

                // Cari item sebelum EditText ini
                val currentIndex = noteContentItems.indexOfFirst {
                    (it is NoteContentItem.Text && it.editText == editText)
                }

                // Pastikan ini bukan EditText pertama dan item sebelumnya adalah gambar
                if (currentIndex > 0 && noteContentItems[currentIndex - 1] is NoteContentItem.Image) {
                    // Hapus gambar dan berikan referensi ke view untuk haptic feedback
                    deleteImageAt(currentIndex - 1, view)
                    return@setOnKeyListener true // Konsumsi event
                }
            }
            false // Biarkan event diproses normal jika tidak menangani
        }
    }

    /**
     * Menghapus gambar pada indeks tertentu
     * @param imageIndex indeks gambar dalam noteContentItems
     * @param sourceView view yang digunakan untuk memberikan umpan balik haptic
     */
    private fun deleteImageAt(imageIndex: Int, sourceView: View) {
        if (imageIndex >= 0 && imageIndex < noteContentItems.size) {
            val imageItem = noteContentItems[imageIndex] as? NoteContentItem.Image ?: return

            // Hapus ImageView dari container
            binding.contentContainer.removeView(imageItem.imageView)

            // Hapus dari list konten
            noteContentItems.removeAt(imageIndex)

            // Fokus ke EditText sebelum atau sesudah gambar
            if (imageIndex > 0 && noteContentItems[imageIndex - 1] is NoteContentItem.Text) {
                // Fokus ke EditText sebelum gambar
                (noteContentItems[imageIndex - 1] as NoteContentItem.Text).editText.requestFocus()
            } else if (imageIndex < noteContentItems.size && noteContentItems[imageIndex] is NoteContentItem.Text) {
                // Fokus ke EditText setelah gambar
                (noteContentItems[imageIndex] as NoteContentItem.Text).editText.requestFocus()
            }

            // Animasikan penghapusan untuk pengalaman pengguna yang lebih baik
            val animation = AlphaAnimation(1f, 0f).apply {
                duration = 300
                fillAfter = true
            }

            // Untuk menyegarkan layout setelah menghapus
            binding.contentContainer.requestLayout()

            // Beri umpan balik haptic menggunakan view yang diberikan
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sourceView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                sourceView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    private fun addImageToNote(imageUri: Uri) {
        // Buat ImageView untuk menampilkan gambar (kode yang sudah ada)
        val imageView = ImageView(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600 // Tinggi gambar, bisa disesuaikan
            ).apply {
                setMargins(16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(imageUri)
            contentDescription = "Note Image"
        }

        // Tambahkan ImageView ke container
        binding.contentContainer.addView(imageView)

        // Tambahkan item gambar ke daftar konten
        noteContentItems.add(NoteContentItem.Image(imageView, imageUri))

        // Buat EditText baru untuk teks setelah gambar
        // (Pastikan kode di bawah ini sesuai dengan implementasi yang sudah ada)
        val newEditText = EditText(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Tulis disini..."
            setBackgroundResource(android.R.color.transparent)
            setPadding(24, 16, 24, 16)
            minHeight = 300 // Minimum height
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        // Tambahkan EditText baru ke container
        binding.contentContainer.addView(newEditText)

        // Tambahkan item teks ke daftar konten
        noteContentItems.add(NoteContentItem.Text(newEditText))

        // Tambahkan listener backspace pada EditText baru
        addBackspaceListenerToEditText(newEditText, noteContentItems.size - 1)

        // Fokus ke field teks baru
        newEditText.requestFocus()
    }

    private fun addAudioToNote(audioUri: Uri) {
        // TODO: Implementasikan tampilan untuk audio yang mirip dengan implementasi gambar
        // Untuk MVP (Minimum Viable Product), Anda bisa fokus pada gambar terlebih dahulu
        Toast.makeText(this, "Audio akan diimplementasikan nanti", Toast.LENGTH_SHORT).show()
    }

    private fun saveNote() {
        val judul = binding.edtJudul.text.toString().trim()

        if (judul.isEmpty()) {
            binding.tilJudul.error = "Judul tidak boleh kosong"
            return
        } else {
            binding.tilJudul.error = null
        }

        // Verifikasi apakah ada konten
        var hasContent = false
        for (item in noteContentItems) {
            when (item) {
                is NoteContentItem.Text -> {
                    if (item.editText.text.toString().trim().isNotEmpty()) {
                        hasContent = true
                        break
                    }
                }
                is NoteContentItem.Image -> {
                    hasContent = true
                    break
                }
                // Tambahkan case untuk audio dan tipe konten lainnya
            }
        }

        if (!hasContent) {
            Toast.makeText(this, "Catatan tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate ID untuk catatan
        val noteId = UUID.randomUUID().toString()

        // Membuat daftar untuk menyimpan konten catatan dalam format yang bisa disimpan
        val contentParts = mutableListOf<NotePart>()

        // Proses setiap item konten
        for (item in noteContentItems) {
            when (item) {
                is NoteContentItem.Text -> {
                    val text = item.editText.text.toString().trim()
                    if (text.isNotEmpty()) {
                        contentParts.add(NotePart.TextPart(text))
                    }
                }
                is NoteContentItem.Image -> {
                    // Simpan gambar ke penyimpanan internal dan dapatkan path-nya
                    val imagePath = saveImageToInternalStorage(item.uri, noteId, contentParts.size)
                    if (imagePath != null) {
                        contentParts.add(NotePart.ImagePart(imagePath))
                    }
                }
                // Tambahkan case untuk audio dan tipe konten lainnya
            }
        }

        Toast.makeText(this, "Catatan berhasil disimpan", Toast.LENGTH_SHORT).show()
        finish()
    }

    // Fungsi untuk menyimpan gambar ke penyimpanan internal
    private fun saveImageToInternalStorage(uri: Uri, noteId: String, index: Int): String? {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "IMG_${noteId}_$index.jpg"
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Kelas-kelas untuk representasi konten catatan
    sealed class NoteContentItem {
        data class Text(val editText: EditText) : NoteContentItem()
        data class Image(val imageView: ImageView, val uri: Uri) : NoteContentItem()
        // Tambahkan class untuk audio dan tipe konten lainnya
    }

    // Kelas-kelas untuk representasi konten catatan yang akan disimpan
    sealed class NotePart {
        data class TextPart(val text: String) : NotePart()
        data class ImagePart(val imagePath: String) : NotePart()
        // Tambahkan class untuk audio dan tipe konten lainnya
    }
}