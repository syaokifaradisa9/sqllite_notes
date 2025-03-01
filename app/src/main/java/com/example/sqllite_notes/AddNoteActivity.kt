package com.example.sqllite_notes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
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
import com.example.sqllite_notes.db.NoteDbHelper
import com.example.sqllite_notes.models.Note
import com.example.sqllite_notes.models.NoteContentItem
import com.example.sqllite_notes.models.NotePart
import com.example.sqllite_notes.utils.ImageUtils

class AddNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddNoteBinding
    private val noteContentItems = mutableListOf<NoteContentItem>()
    private lateinit var permissionManager: PermissionManager
    private lateinit var dbHelper: NoteDbHelper
    private var noteId: Long = 0 // 0 means new note

    private val TAG = "AddNoteActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NoteDbHelper(this)

        setupToolbar()
        setupPermissionManager()
        setupListeners()

        // Check if we're editing an existing note
        noteId = intent.getLongExtra(MainActivity.EXTRA_NOTE_ID, 0)
        Log.d(TAG, "Opening note with ID: $noteId")

        if (noteId > 0) {
            // Editing existing note
            loadNote(noteId)
        } else {
            // Creating new note
            initializeEmptyContent()
        }
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

        // Update title based on whether we're editing or creating
        if (noteId > 0) {
            binding.toolbar.title = "Edit Catatan"
        } else {
            binding.toolbar.title = "Tambah Catatan Baru"
        }
    }

    private fun setupListeners() {
        // Image button
        binding.btnAddImage.setOnClickListener {
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.IMAGE)
        }

        // Audio button
        binding.btnAddAudio.setOnClickListener {
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.AUDIO)
        }

        // Save button
        binding.btnSimpan.setOnClickListener {
            saveNote()
        }
    }

    private fun loadNote(noteId: Long) {
        // Clear any existing content
        binding.contentContainer.removeAllViews()
        noteContentItems.clear()

        val note = dbHelper.getNoteById(noteId)
        if (note != null) {
            // Set title
            binding.edtJudul.setText(note.title)

            // Parse content into parts
            val parts = ImageUtils.deserializeNoteParts(note.content)
            Log.d(TAG, "Loaded note with ${parts.size} parts")

            // For each part, create the appropriate view and add it to the container
            for (part in parts) {
                when (part) {
                    is NotePart.TextPart -> {
                        // Add a text field for text content
                        val editText = createEditText(part.text)
                        binding.contentContainer.addView(editText)
                        noteContentItems.add(NoteContentItem.Text(editText))
                    }
                    is NotePart.ImagePart -> {
                        // Try to decode the Base64 string to a bitmap
                        val base64String = part.imagePath
                        Log.d(TAG, "Loading image: ${base64String.take(50)}...")

                        if (ImageUtils.isImage(base64String)) {
                            val bitmap = ImageUtils.base64ToBitmap(base64String)

                            if (bitmap != null) {
                                // Successfully decoded - create an ImageView
                                val imageView = ImageView(this).apply {
                                    id = View.generateViewId()
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        600
                                    ).apply {
                                        setMargins(16, 8, 16, 8)
                                    }
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    setImageBitmap(bitmap)
                                    contentDescription = "Note Image"
                                }

                                binding.contentContainer.addView(imageView)

                                // Store the image with its original Base64 data
                                noteContentItems.add(NoteContentItem.Image(
                                    imageView = imageView,
                                    uri = Uri.EMPTY,
                                    base64String = base64String
                                ))

                                // Add a new text field after each image if this isn't the last item
                                // or if the next item isn't already a text part
                                if (part != parts.lastOrNull() &&
                                    (parts.indexOf(part) + 1 >= parts.size ||
                                            parts[parts.indexOf(part) + 1] !is NotePart.TextPart)) {
                                    val newEditText = createEditText("")
                                    binding.contentContainer.addView(newEditText)
                                    noteContentItems.add(NoteContentItem.Text(newEditText))
                                }
                            } else {
                                // Failed to decode - add an error message
                                Log.e(TAG, "Failed to decode image")
                                addImageLoadErrorMessage()
                            }
                        } else {
                            // Not a valid image string - add an error message
                            Log.e(TAG, "Invalid image data format")
                            addImageLoadErrorMessage()
                        }
                    }
                }
            }

            // If no content was loaded, add an empty text field
            if (noteContentItems.isEmpty()) {
                initializeEmptyContent()
            }
        } else {
            // Note not found, initialize with empty content
            initializeEmptyContent()
        }
    }

    // Helper method to create a standardized EditText
    private fun createEditText(initialText: String): EditText {
        return EditText(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            hint = "Tulis disini..."
            setBackgroundResource(android.R.color.transparent)
            setPadding(24, 16, 24, 16)
            minHeight = 300
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(initialText)

            // Add backspace listener
            addBackspaceListenerToEditText(this)
        }
    }

    // Helper method to add an error message when image loading fails
    private fun addImageLoadErrorMessage() {
        val errorText = EditText(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setText("[Gagal memuat gambar]")
            isEnabled = false
            setTextColor(resources.getColor(android.R.color.holo_red_light, theme))
            setPadding(24, 16, 24, 16)
        }

        binding.contentContainer.addView(errorText)
        noteContentItems.add(NoteContentItem.Text(errorText))
    }

    // Initialize with a single empty text field
    private fun initializeEmptyContent() {
        val firstEditText = createEditText("")
        binding.contentContainer.addView(firstEditText)
        noteContentItems.add(NoteContentItem.Text(firstEditText))
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
        // Create an ImageView for the new image
        val imageView = ImageView(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(imageUri)
            contentDescription = "Note Image"
        }

        binding.contentContainer.addView(imageView)

        // Add the image to our data model
        noteContentItems.add(NoteContentItem.Image(imageView, imageUri))

        // Create a new text field after the image
        val newEditText = createEditText("")
        binding.contentContainer.addView(newEditText)
        noteContentItems.add(NoteContentItem.Text(newEditText))

        // Focus on the new text field
        newEditText.requestFocus()
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, newEditText.bottom)
        }
    }

    private fun addAudioToNote(audioUri: Uri) {
        Toast.makeText(this, "Audio akan diimplementasikan nanti", Toast.LENGTH_SHORT).show()
    }

    private fun saveNote() {
        val title = binding.edtJudul.text.toString().trim()

        if (title.isEmpty()) {
            binding.tilJudul.error = "Judul tidak boleh kosong"
            return
        } else {
            binding.tilJudul.error = null
        }

        // Verify there's actual content
        var hasContent = false
        for (item in noteContentItems) {
            when (item) {
                is NoteContentItem.Text -> {
                    if (item.editText.text.toString().trim().isNotEmpty() &&
                        item.editText.isEnabled) { // Skip disabled error messages
                        hasContent = true
                        break
                    }
                }
                is NoteContentItem.Image -> {
                    hasContent = true
                    break
                }
            }
        }

        if (!hasContent) {
            Toast.makeText(this, "Catatan tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        // Process content items into serializable parts
        val contentParts = mutableListOf<NotePart>()
        var conversionError = false

        for (item in noteContentItems) {
            when (item) {
                is NoteContentItem.Text -> {
                    val text = item.editText.text.toString().trim()
                    if (text.isNotEmpty() && item.editText.isEnabled) { // Skip disabled error messages
                        contentParts.add(NotePart.TextPart(text))
                    }
                }
                is NoteContentItem.Image -> {
                    try {
                        if (item.uri != Uri.EMPTY) {
                            // New image - convert from URI to Base64
                            Log.d(TAG, "Processing new image from URI")
                            val base64Image = ImageUtils.uriToBase64(contentResolver, item.uri)
                            if (base64Image != null) {
                                contentParts.add(NotePart.ImagePart(base64Image))
                            } else {
                                Log.e(TAG, "Failed to convert new image to Base64")
                                conversionError = true
                            }
                        } else if (item.base64String.isNotEmpty()) {
                            // Existing image - use the stored Base64 string
                            Log.d(TAG, "Using existing image Base64 data")
                            contentParts.add(NotePart.ImagePart(item.base64String))
                        } else {
                            // No valid image data
                            Log.e(TAG, "No valid image data found")
                            conversionError = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image: ${e.message}")
                        conversionError = true
                    }
                }
            }
        }

        if (conversionError) {
            Toast.makeText(this, "Beberapa gambar mungkin tidak tersimpan dengan benar", Toast.LENGTH_LONG).show()
        }

        // Serialize content parts
        val serializedContent = ImageUtils.serializeNoteParts(contentParts)
        Log.d(TAG, "Serialized content with ${contentParts.size} parts")

        // Create or update note in database
        val note = Note(
            id = noteId,
            title = title,
            content = serializedContent
        )

        val success: Boolean
        if (noteId > 0) {
            // Update existing note
            success = dbHelper.updateNote(note) > 0
        } else {
            // Insert new note
            val newId = dbHelper.insertNote(note)
            success = newId > 0
            if (success) {
                noteId = newId
            }
        }

        if (success) {
            Toast.makeText(this, "Catatan berhasil disimpan", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Gagal menyimpan catatan", Toast.LENGTH_SHORT).show()
        }
    }
}