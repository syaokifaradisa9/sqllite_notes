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
        /// Inisialisasi dengan satu EditText pertama
        val firstEditText = binding.edtContent1
        noteContentItems.add(NoteContentItem.Text(firstEditText))

        // Tambahkan listener backspace
        addBackspaceListenerToEditText(firstEditText)
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
    private fun addBackspaceListenerToEditText(editText: EditText) {
        editText.setOnKeyListener { view, keyCode, event ->
            // Deteksi penekanan backspace saat EditText kosong
            if (keyCode == KeyEvent.KEYCODE_DEL
                && event.action == KeyEvent.ACTION_DOWN
                && editText.text.isEmpty()) {

                // Cari posisi EditText ini dalam daftar konten
                val currentIndex = noteContentItems.indexOfFirst {
                    (it is NoteContentItem.Text && it.editText == editText)
                }

                // Pastikan ini bukan EditText pertama dan item sebelumnya adalah gambar
                if (currentIndex > 0 && noteContentItems[currentIndex - 1] is NoteContentItem.Image) {
                    // Hapus gambar beserta text field kosong ini
                    deleteImageAndEmptyTextField(currentIndex - 1, currentIndex, view)
                    return@setOnKeyListener true // Konsumsi event
                }
            }
            false // Biarkan event diproses normal jika tidak menangani
        }
    }

    /**
     * Menghapus gambar dan text field kosong di bawahnya
     * @param imageIndex indeks gambar dalam noteContentItems
     * @param textFieldIndex indeks text field dalam noteContentItems
     * @param sourceView view yang digunakan untuk umpan balik haptic
     */
    private fun deleteImageAndEmptyTextField(imageIndex: Int, textFieldIndex: Int, sourceView: View) {
        if (imageIndex >= 0 && imageIndex < noteContentItems.size &&
            textFieldIndex >= 0 && textFieldIndex < noteContentItems.size) {

            // Pastikan tipe item sesuai ekspektasi
            val imageItem = noteContentItems[imageIndex] as? NoteContentItem.Image ?: return
            val textItem = noteContentItems[textFieldIndex] as? NoteContentItem.Text ?: return

            // Hapus ImageView dan EditText dari layout container
            binding.contentContainer.removeView(imageItem.imageView)
            binding.contentContainer.removeView(textItem.editText)

            // Hapus dari list konten - hapus dari indeks lebih tinggi dulu
            // untuk menghindari perubahan indeks setelah penghapusan
            noteContentItems.removeAt(textFieldIndex)
            noteContentItems.removeAt(imageIndex)

            // Fokus ke EditText sebelumnya jika ada
            if (imageIndex > 0 && noteContentItems[imageIndex - 1] is NoteContentItem.Text) {
                val previousTextField = (noteContentItems[imageIndex - 1] as NoteContentItem.Text).editText
                previousTextField.requestFocus()

                // Opsional: posisikan kursor di akhir teks
                previousTextField.setSelection(previousTextField.text.length)
            }

            // Berikan umpan balik haptic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sourceView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                sourceView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Segarkan layout
            binding.contentContainer.requestLayout()
        }
    }

    /**
     * Adds an image to the note at the current position and creates a new text field below it
     * @param imageUri Uri of the selected image
     */
    private fun addImageToNote(imageUri: Uri) {
        // Create ImageView to display the image
        val imageView = ImageView(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600 // Fixed height for images, can be adjusted
            ).apply {
                setMargins(16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(imageUri)
            contentDescription = "Note Image"

            // Optional: add click listener for image preview/edit
            setOnClickListener {
                // You could implement image preview or editing functionality here
                Toast.makeText(context, "Image clicked", Toast.LENGTH_SHORT).show()
            }
        }

        // Add ImageView to the layout container
        binding.contentContainer.addView(imageView)

        // Add image item to the note content data structure
        noteContentItems.add(NoteContentItem.Image(imageView, imageUri))

        // Create a new EditText for text entry below the image
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

            // Optional: add text watcher if you need to track changes
            /*
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // Track changes or auto-save
                }
            })
            */
        }

        // Add the new EditText to the layout container
        binding.contentContainer.addView(newEditText)

        // Add text item to the note content data structure
        noteContentItems.add(NoteContentItem.Text(newEditText))

        // Add backspace listener to enable deleting the image when backspace is pressed
        // in an empty text field
        addBackspaceListenerToEditText(newEditText)

        // Set focus to the new text field
        newEditText.requestFocus()

        // Optional: scroll to show the new text field
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, newEditText.bottom)
        }
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