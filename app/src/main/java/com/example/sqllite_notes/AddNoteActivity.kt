package com.example.sqllite_notes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.sqllite_notes.databinding.ActivityAddNoteBinding
import com.example.sqllite_notes.models.NoteContentItem
import com.example.sqllite_notes.models.NotePart
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AddNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddNoteBinding
    private val noteContentItems = mutableListOf<NoteContentItem>()
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupPermissionManager()
        setupListeners()
        initializeNoteContent()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                addImageToNote(uri)
            }
        }
    }

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                addAudioToNote(uri)
            }
        }
    }

    private fun setupPermissionManager() {
        permissionManager = PermissionManager(
            activity = this,
            onPermissionGranted = { permissionType ->
                when (permissionType) {
                    PermissionManager.PermissionType.IMAGE -> openImagePicker()
                    PermissionManager.PermissionType.AUDIO -> openAudioPicker()
                    else -> { /* Handle other permission types if needed */ }
                }
            }
        )
    }

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
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.IMAGE)
        }

        // Tombol tambah audio
        binding.btnAddAudio.setOnClickListener {
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.AUDIO)
        }

        // Tombol simpan
        binding.btnSimpan.setOnClickListener {
            saveNote()
        }
    }

    private fun initializeNoteContent() {
        val firstEditText = binding.edtContent1
        noteContentItems.add(NoteContentItem.Text(firstEditText))

        addBackspaceListenerToEditText(firstEditText)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        pickAudioLauncher.launch(intent)
    }

    private fun addBackspaceListenerToEditText(editText: EditText) {
        editText.setOnKeyListener { view, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL
                && event.action == KeyEvent.ACTION_DOWN
                && editText.text.isEmpty()) {

                val currentIndex = noteContentItems.indexOfFirst {
                    (it is NoteContentItem.Text && it.editText == editText)
                }

                if (currentIndex > 0 && noteContentItems[currentIndex - 1] is NoteContentItem.Image) {
                    deleteImageAndEmptyTextField(currentIndex - 1, currentIndex, view)
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    private fun deleteImageAndEmptyTextField(imageIndex: Int, textFieldIndex: Int, sourceView: View) {
        if (imageIndex >= 0 && imageIndex < noteContentItems.size &&
            textFieldIndex >= 0 && textFieldIndex < noteContentItems.size) {

            val imageItem = noteContentItems[imageIndex] as? NoteContentItem.Image ?: return
            val textItem = noteContentItems[textFieldIndex] as? NoteContentItem.Text ?: return

            binding.contentContainer.removeView(imageItem.imageView)
            binding.contentContainer.removeView(textItem.editText)

            noteContentItems.removeAt(textFieldIndex)
            noteContentItems.removeAt(imageIndex)

            if (imageIndex > 0 && noteContentItems[imageIndex - 1] is NoteContentItem.Text) {
                val previousTextField = (noteContentItems[imageIndex - 1] as NoteContentItem.Text).editText
                previousTextField.requestFocus()

                previousTextField.setSelection(previousTextField.text.length)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sourceView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                sourceView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            binding.contentContainer.requestLayout()
        }
    }

    private fun addImageToNote(imageUri: Uri) {
        val imageView = ImageView(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600 // Fixed height for images, can be adjusted
            ).apply {
                setMargins(16, 0, 16, 0)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(imageUri)
            contentDescription = "Note Image"

            setOnClickListener {
                Toast.makeText(context, "Image clicked", Toast.LENGTH_SHORT).show()
            }
        }

        binding.contentContainer.addView(imageView)

        noteContentItems.add(NoteContentItem.Image(imageView, imageUri))

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

        binding.contentContainer.addView(newEditText)
        noteContentItems.add(NoteContentItem.Text(newEditText))

        addBackspaceListenerToEditText(newEditText)

        newEditText.requestFocus()
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, newEditText.bottom)
        }
    }

    private fun addAudioToNote(audioUri: Uri) {
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
}