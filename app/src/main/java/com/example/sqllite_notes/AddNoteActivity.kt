package com.example.sqllite_notes

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.sqllite_notes.databinding.ActivityAddNoteBinding
import com.example.sqllite_notes.db.NoteDbHelper
import com.example.sqllite_notes.helpers.ImageSelectorHelper
import com.example.sqllite_notes.models.Note
import com.example.sqllite_notes.models.NoteContentItem
import com.example.sqllite_notes.models.NotePart
import com.example.sqllite_notes.utils.AudioPlayerView
import com.example.sqllite_notes.utils.AudioSelectorHelper
import com.example.sqllite_notes.utils.MultimediaUtils

class AddNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddNoteBinding
    private val noteContentItems = mutableListOf<NoteContentItem>()
    private lateinit var permissionManager: PermissionManager
    private lateinit var dbHelper: NoteDbHelper
    private var noteId: Long = 0

    // Helper classes for selecting media
    private lateinit var imageSelectorHelper: ImageSelectorHelper
    private lateinit var audioSelectorHelper: AudioSelectorHelper

    private val TAG = "AddNoteActivity"

    // Setup
    private fun initializeEmptyContent() {
        val firstEditText = createEditText("")
        binding.contentContainer.addView(firstEditText)
        noteContentItems.add(NoteContentItem.Text(firstEditText))
    }

    private fun setupPermissionManager() {
        permissionManager = PermissionManager(
            activity = this,
            onPermissionGranted = { permissionType ->
                when (permissionType) {
                    PermissionManager.PermissionType.IMAGE -> imageSelectorHelper.openImagePicker()
                    PermissionManager.PermissionType.AUDIO -> audioSelectorHelper.openAudioPicker()
                    else -> { /* Handle other permission types if needed */ }
                }
            }
        )
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        if (noteId > 0) {
            binding.toolbar.title = "Edit Catatan"
        } else {
            binding.toolbar.title = "Tambah Catatan Baru"
        }
    }

    private fun setupListeners() {
        binding.btnAddImage.setOnClickListener {
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.IMAGE)
        }

        binding.btnAddAudio.setOnClickListener {
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.AUDIO)
        }

        binding.btnSimpan.setOnClickListener {
            saveNote()
        }
    }

    private fun setupMediaSelectors() {
        // Initialize image selector helper
        imageSelectorHelper = ImageSelectorHelper(this) { uri ->
            addImageToNote(uri)
        }

        // Initialize audio selector helper
        audioSelectorHelper = AudioSelectorHelper(this) { uri, title ->
            addAudioToNote(uri, title)
        }
    }

    // Initialization
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NoteDbHelper(this)

        setupToolbar()
        setupMediaSelectors()
        setupPermissionManager()
        setupListeners()

        noteId = intent.getLongExtra(MainActivity.EXTRA_NOTE_ID, 0)
        Log.d(TAG, "Opening note with ID: $noteId")

        binding.contentContainer.apply {
            this.showDividers = LinearLayout.SHOW_DIVIDER_NONE
        }

        if (noteId > 0) {
            loadNote(noteId)
        } else {
            initializeEmptyContent()
        }
    }

    // Menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (noteId > 0) {
            menuInflater.inflate(R.menu.menu_delete_note, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                confirmDeleteNote()
                true
            }
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Find Note By Id When Page is loaded
    private fun loadNote(noteId: Long) {
        binding.contentContainer.removeAllViews()
        noteContentItems.clear()

        val note = dbHelper.getNoteById(noteId)
        if (note != null) {
            binding.edtJudul.setText(note.title)

            val parts = MultimediaUtils.deserializeNoteParts(note.content)
            Log.d(TAG, "Loaded note with ${parts.size} parts")

            for (part in parts) {
                when (part) {
                    is NotePart.TextPart -> {
                        val editText = createEditText(part.text)
                        binding.contentContainer.addView(editText)
                        noteContentItems.add(NoteContentItem.Text(editText))
                    }
                    is NotePart.ImagePart -> {
                        val base64String = part.imagePath
                        Log.d(TAG, "Loading image: ${base64String.take(50)}...")

                        if (MultimediaUtils.isImage(base64String)) {
                            val bitmap = MultimediaUtils.base64ToBitmap(base64String)

                            if (bitmap != null) {
                                val imageView = ImageView(this).apply {
                                    id = View.generateViewId()
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        600
                                    ).apply {
                                        setMargins(16, 0, 16, 0)
                                    }
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    setImageBitmap(bitmap)
                                    contentDescription = "Note Image"
                                }

                                binding.contentContainer.addView(imageView)

                                noteContentItems.add(NoteContentItem.Image(
                                    imageView = imageView,
                                    uri = Uri.EMPTY,
                                    base64String = base64String
                                ))

                                if (part == parts.lastOrNull() ||
                                    parts.indexOf(part) + 1 >= parts.size ||
                                    parts[parts.indexOf(part) + 1] !is NotePart.TextPart) {
                                    val newEditText = createEditText("")
                                    binding.contentContainer.addView(newEditText)
                                    noteContentItems.add(NoteContentItem.Text(newEditText))
                                }
                            } else {
                                Log.e(TAG, "Failed to decode image")
                                addImageLoadErrorMessage()
                            }
                        } else {
                            Log.e(TAG, "Invalid image data format")
                            addImageLoadErrorMessage()
                        }
                    }
                    is NotePart.AudioPart -> {
                        val audioPath = part.audioPath
                        Log.d(TAG, "Loading audio: ${audioPath.take(50)}...")

                        try {
                            val audioPlayerView = AudioPlayerView(this).apply {
                                id = View.generateViewId()
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                setAudioSource(audioPath, part.title)
                            }

                            binding.contentContainer.addView(audioPlayerView)

                            noteContentItems.add(NoteContentItem.Audio(
                                audioView = audioPlayerView,
                                uri = Uri.EMPTY,
                                audioPath = audioPath,
                                title = part.title
                            ))

                            if (part == parts.lastOrNull() ||
                                parts.indexOf(part) + 1 >= parts.size ||
                                parts[parts.indexOf(part) + 1] !is NotePart.TextPart) {
                                val newEditText = createEditText("")
                                binding.contentContainer.addView(newEditText)
                                noteContentItems.add(NoteContentItem.Text(newEditText))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load audio player: ${e.message}")
                            addAudioLoadErrorMessage()
                        }
                    }
                }
            }

            if (noteContentItems.isEmpty()) {
                initializeEmptyContent()
            }
        } else {
            initializeEmptyContent()
        }
    }

    // Save Note Action
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
                is NoteContentItem.Image, is NoteContentItem.Audio -> {
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
                            val base64Image = MultimediaUtils.uriToBase64(contentResolver, item.uri)
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
                is NoteContentItem.Audio -> {
                    try {
                        if (item.uri != Uri.EMPTY) {
                            // New audio - copy to app's storage
                            Log.d(TAG, "Processing new audio from URI")
                            val audioFile = audioSelectorHelper.copyAudioToInternalStorage(item.uri)
                            if (audioFile.isNotEmpty()) {
                                val audioPath = MultimediaUtils.wrapAudio(audioFile, item.title)
                                contentParts.add(NotePart.AudioPart(audioPath, item.title))
                            } else {
                                Log.e(TAG, "Failed to copy audio file")
                                conversionError = true
                            }
                        } else if (item.audioPath.isNotEmpty()) {
                            // Existing audio - use the stored path
                            Log.d(TAG, "Using existing audio data")
                            contentParts.add(NotePart.AudioPart(item.audioPath, item.title))
                        } else {
                            // No valid audio data
                            Log.e(TAG, "No valid audio data found")
                            conversionError = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing audio: ${e.message}")
                        conversionError = true
                    }
                }
            }
        }

        if (conversionError) {
            Toast.makeText(this, "Beberapa media mungkin tidak tersimpan dengan benar", Toast.LENGTH_LONG).show()
        }

        // Serialize content parts
        val serializedContent = MultimediaUtils.serializeNoteParts(contentParts)
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

    // Delete Note Action
    private fun confirmDeleteNote() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Catatan")
            .setMessage("Apakah Anda yakin ingin menghapus catatan ini?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteNote()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteNote() {
        if (noteId > 0) {
            val result = dbHelper.deleteNote(noteId)
            if (result > 0) {
                Toast.makeText(this, "Catatan berhasil dihapus", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Gagal menghapus catatan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Methods for adding media to the note
    private fun addImageToNote(imageUri: Uri) {
        val imageView = imageSelectorHelper.createImageView(imageUri)
        binding.contentContainer.addView(imageView)

        noteContentItems.add(NoteContentItem.Image(imageView, imageUri))

        val newEditText = createEditText("")
        binding.contentContainer.addView(newEditText)
        noteContentItems.add(NoteContentItem.Text(newEditText))

        newEditText.requestFocus()
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, newEditText.bottom)
        }
    }

    private fun addAudioToNote(audioUri: Uri, audioTitle: String = "Audio Recording") {
        try {
            // Create audio player view
            val audioPlayerView = AudioPlayerView(this).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setAudioSource(audioUri, audioTitle)
            }

            binding.contentContainer.addView(audioPlayerView)

            // Add to content items
            noteContentItems.add(NoteContentItem.Audio(
                audioView = audioPlayerView,
                uri = audioUri,
                title = audioTitle
            ))

            // Add a text field after audio
            val newEditText = createEditText("")
            binding.contentContainer.addView(newEditText)
            noteContentItems.add(NoteContentItem.Text(newEditText))

            newEditText.requestFocus()
            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, newEditText.bottom)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error adding audio to note: ${e.message}")
            Toast.makeText(this, "Gagal menambahkan audio", Toast.LENGTH_SHORT).show()
        }
    }

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
            setPadding(24, 0, 24, 0)
        }

        binding.contentContainer.addView(errorText)
        noteContentItems.add(NoteContentItem.Text(errorText))
    }

    private fun addAudioLoadErrorMessage() {
        val errorText = EditText(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setText("[Gagal memuat audio]")
            isEnabled = false
            setTextColor(resources.getColor(android.R.color.holo_red_light, theme))
            setPadding(24, 0, 24, 0)
        }

        binding.contentContainer.addView(errorText)
        noteContentItems.add(NoteContentItem.Text(errorText))
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

    private fun deleteAudioAndEmptyTextField(audioIndex: Int, textFieldIndex: Int, sourceView: View) {
        if (audioIndex >= 0 && audioIndex < noteContentItems.size &&
            textFieldIndex >= 0 && textFieldIndex < noteContentItems.size) {

            val audioItem = noteContentItems[audioIndex] as? NoteContentItem.Audio ?: return
            val textItem = noteContentItems[textFieldIndex] as? NoteContentItem.Text ?: return

            binding.contentContainer.removeView(audioItem.audioView)
            binding.contentContainer.removeView(textItem.editText)

            noteContentItems.removeAt(textFieldIndex)
            noteContentItems.removeAt(audioIndex)

            if (audioIndex > 0 && noteContentItems[audioIndex - 1] is NoteContentItem.Text) {
                val previousTextField = (noteContentItems[audioIndex - 1] as NoteContentItem.Text).editText
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

    private fun createEditText(initialText: String): EditText {
        return EditText(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 0, 24, 0)
            }
            hint = "Tulis disini..."
            setBackgroundResource(android.R.color.transparent)

            setPadding(0, 8, 0, 0)
            minHeight = 100
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(initialText)

            addBackspaceListenerToEditText(this)
        }
    }

    private fun addBackspaceListenerToEditText(editText: EditText) {
        editText.setOnKeyListener { view, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL
                && event.action == KeyEvent.ACTION_DOWN
                && editText.text.isEmpty()) {

                val currentIndex = noteContentItems.indexOfFirst {
                    (it is NoteContentItem.Text && it.editText == editText)
                }

                if (currentIndex > 0) {
                    when (val prevItem = noteContentItems[currentIndex - 1]) {
                        is NoteContentItem.Image -> {
                            deleteImageAndEmptyTextField(currentIndex - 1, currentIndex, view)
                            return@setOnKeyListener true
                        }
                        is NoteContentItem.Audio -> {
                            // Similar logic for deleting audio
                            deleteAudioAndEmptyTextField(currentIndex - 1, currentIndex, view)
                            return@setOnKeyListener true
                        }
                        else -> {} // Do nothing for other types
                    }
                }
            }
            false
        }
    }
}