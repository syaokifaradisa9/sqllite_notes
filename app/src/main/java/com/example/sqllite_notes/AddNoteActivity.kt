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
import com.example.sqllite_notes.helpers.AudioSelectorHelper
import com.example.sqllite_notes.utils.MultimediaUtils

/**
 * AddNoteActivity adalah kelas yang bertanggung jawab untuk menangani:
 * 1. Pembuatan catatan baru
 * 2. Pengeditan catatan yang sudah ada
 * 3. Penambahan konten multimedia (gambar, audio) ke catatan
 * 4. Penyimpanan data catatan ke dalam database
 */
class AddNoteActivity : AppCompatActivity() {
    // View binding untuk mengakses elemen UI tanpa findViewById
    private lateinit var binding: ActivityAddNoteBinding

    // Daftar untuk menyimpan semua item konten catatan (teks, gambar, audio)
    private val noteContentItems = mutableListOf<NoteContentItem>()

    // Manager untuk memeriksa dan meminta izin akses ke media
    private lateinit var permissionManager: PermissionManager

    // Helper untuk mengakses dan memanipulasi database SQLite
    private lateinit var dbHelper: NoteDbHelper

    // ID catatan yang sedang diedit (0 jika catatan baru)
    private var noteId: Long = 0

    // Helper classes untuk memilih media
    private lateinit var imageSelectorHelper: ImageSelectorHelper
    private lateinit var audioSelectorHelper: AudioSelectorHelper

    // Tag untuk logging
    private val TAG = "AddNoteActivity"

    /**
     * Menginisialisasi konten kosong (EditText pertama) saat
     * membuat catatan baru
     */
    private fun initializeEmptyContent() {
        val firstEditText = createEditText("")
        binding.contentContainer.addView(firstEditText)
        noteContentItems.add(NoteContentItem.Text(firstEditText))
    }

    /**
     * Menyiapkan permission manager untuk menangani izin akses media
     */
    private fun setupPermissionManager() {
        permissionManager = PermissionManager(
            activity = this,
            onPermissionGranted = { permissionType ->
                // Ketika izin diberikan, buka picker sesuai jenis izin
                when (permissionType) {
                    PermissionManager.PermissionType.IMAGE -> imageSelectorHelper.openImagePicker()
                    PermissionManager.PermissionType.AUDIO -> audioSelectorHelper.openAudioPicker()
                    else -> { /* Handle other permission types if needed */ }
                }
            }
        )
    }

    /**
     * Menyiapkan toolbar dan judul halaman
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Sesuaikan judul berdasarkan mode (tambah/edit)
        if (noteId > 0) {
            binding.toolbar.title = "Edit Catatan"
        } else {
            binding.toolbar.title = "Tambah Catatan Baru"
        }
    }

    /**
     * Menyiapkan listener untuk tombol dan aksi pengguna
     */
    private fun setupListeners() {
        // Listener untuk tombol tambah gambar
        binding.btnAddImage.setOnClickListener {
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.IMAGE)
        }

        // Listener untuk tombol tambah audio
        binding.btnAddAudio.setOnClickListener {
            permissionManager.checkAndRequestPermission(PermissionManager.PermissionType.AUDIO)
        }

        // Listener untuk tombol simpan
        binding.btnSimpan.setOnClickListener {
            saveNote()
        }
    }

    /**
     * Menyiapkan helper untuk memilih media (gambar dan audio)
     */
    private fun setupMediaSelectors() {
        // Inisialisasi helper untuk memilih gambar
        imageSelectorHelper = ImageSelectorHelper(this) { uri ->
            addImageToNote(uri)
        }

        // Inisialisasi helper untuk memilih audio
        audioSelectorHelper = AudioSelectorHelper(this) { uri, title ->
            addAudioToNote(uri, title)
        }
    }

    /**
     * Dipanggil saat activity pertama kali dibuat.
     * Menginisialisasi UI dan semua komponen yang diperlukan.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NoteDbHelper(this)

        setupToolbar()
        setupMediaSelectors()
        setupPermissionManager()
        setupListeners()

        // Ambil ID catatan dari intent (jika dalam mode edit)
        noteId = intent.getLongExtra(MainActivity.EXTRA_NOTE_ID, 0)
        Log.d(TAG, "Opening note with ID: $noteId")

        binding.contentContainer.apply {
            this.showDividers = LinearLayout.SHOW_DIVIDER_NONE
        }

        // Muat catatan jika dalam mode edit, atau inisialisasi kosong jika mode baru
        if (noteId > 0) {
            loadNote(noteId)
        } else {
            initializeEmptyContent()
        }
    }

    /**
     * Dipanggil saat menu options perlu dibuat.
     * Menambahkan menu hapus jika dalam mode edit.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (noteId > 0) {
            // Hanya tampilkan menu hapus jika sedang mengedit catatan yang ada
            menuInflater.inflate(R.menu.menu_delete_note, menu)
        }
        return true
    }

    /**
     * Dipanggil saat item menu dipilih.
     * Menangani aksi menu (hapus atau kembali).
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                // Konfirmasi penghapusan catatan
                confirmDeleteNote()
                true
            }
            android.R.id.home -> {
                // Kembali ke halaman sebelumnya
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Memuat catatan dari database berdasarkan ID
     * dan menampilkan kontennya di UI.
     */
    private fun loadNote(noteId: Long) {
        binding.contentContainer.removeAllViews()
        noteContentItems.clear()

        val note = dbHelper.getNoteById(noteId)
        if (note != null) {
            binding.edtJudul.setText(note.title)

            // Deserialisasi konten catatan menjadi bagian-bagian terpisah (teks, gambar, audio)
            val parts = MultimediaUtils.deserializeNoteParts(note.content)
            Log.d(TAG, "Loaded note with ${parts.size} parts")

            // Muat setiap bagian ke dalam UI
            for (part in parts) {
                when (part) {
                    is NotePart.TextPart -> {
                        // Tambahkan konten teks ke UI
                        val editText = createEditText(part.text)
                        binding.contentContainer.addView(editText)
                        noteContentItems.add(NoteContentItem.Text(editText))
                    }
                    is NotePart.ImagePart -> {
                        // Tambahkan konten gambar ke UI
                        val base64String = part.imagePath
                        Log.d(TAG, "Loading image: ${base64String.take(50)}...")

                        if (MultimediaUtils.isImage(base64String)) {
                            val bitmap = MultimediaUtils.base64ToBitmap(base64String)

                            if (bitmap != null) {
                                // Hitung rasio aspek bitmap
                                val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

                                // Buat ImageView untuk menampilkan gambar
                                val imageView = ImageView(this).apply {
                                    id = View.generateViewId()
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        setMargins(16, 8, 16, 8)
                                    }
                                    adjustViewBounds = true  // Mempertahankan rasio aspek
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setImageBitmap(bitmap)
                                    contentDescription = "Note Image"
                                }

                                binding.contentContainer.addView(imageView)

                                // Tambahkan ke daftar item konten
                                noteContentItems.add(NoteContentItem.Image(
                                    imageView = imageView,
                                    uri = Uri.EMPTY,
                                    base64String = base64String
                                ))

                                // Tambahkan EditText kosong setelah gambar jika diperlukan
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
                        // Tambahkan konten audio ke UI
                        val audioPath = part.audioPath
                        Log.d(TAG, "Loading audio: ${audioPath.take(50)}...")

                        try {
                            // Buat AudioPlayerView untuk memutar audio
                            val audioPlayerView = AudioPlayerView(this).apply {
                                id = View.generateViewId()
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                setAudioSource(audioPath, part.title)
                            }

                            binding.contentContainer.addView(audioPlayerView)

                            // Tambahkan ke daftar item konten
                            noteContentItems.add(NoteContentItem.Audio(
                                audioView = audioPlayerView,
                                uri = Uri.EMPTY,
                                audioPath = audioPath,
                                title = part.title
                            ))

                            // Tambahkan EditText kosong setelah audio jika diperlukan
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

            // Jika tidak ada konten yang berhasil dimuat, inisialisasi dengan konten kosong
            if (noteContentItems.isEmpty()) {
                initializeEmptyContent()
            }
        } else {
            // Jika catatan tidak ditemukan, inisialisasi dengan konten kosong
            initializeEmptyContent()
        }
    }

    /**
     * Menyimpan catatan ke database (membuat baru atau memperbarui yang ada)
     */
    private fun saveNote() {
        val title = binding.edtJudul.text.toString().trim()

        // Validasi judul
        if (title.isEmpty()) {
            binding.tilJudul.error = "Judul tidak boleh kosong"
            return
        } else {
            binding.tilJudul.error = null
        }

        // Verifikasi ada konten aktual
        var hasContent = false
        for (item in noteContentItems) {
            when (item) {
                is NoteContentItem.Text -> {
                    if (item.editText.text.toString().trim().isNotEmpty() &&
                        item.editText.isEnabled) { // Lewati pesan error yang dinonaktifkan
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

        // Validasi konten
        if (!hasContent) {
            Toast.makeText(this, "Catatan tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        // Proses item konten menjadi bagian yang dapat diserialisasi
        val contentParts = mutableListOf<NotePart>()
        var conversionError = false

        // Konversi setiap item konten ke format yang dapat disimpan
        for (item in noteContentItems) {
            when (item) {
                is NoteContentItem.Text -> {
                    val text = item.editText.text.toString().trim()
                    if (text.isNotEmpty() && item.editText.isEnabled) { // Lewati pesan error yang dinonaktifkan
                        contentParts.add(NotePart.TextPart(text))
                    }
                }
                is NoteContentItem.Image -> {
                    try {
                        if (item.uri != Uri.EMPTY) {
                            // Gambar baru - konversi dari URI ke Base64
                            Log.d(TAG, "Processing new image from URI")
                            val base64Image = MultimediaUtils.uriToBase64(contentResolver, item.uri)
                            if (base64Image != null) {
                                contentParts.add(NotePart.ImagePart(base64Image))
                            } else {
                                Log.e(TAG, "Failed to convert new image to Base64")
                                conversionError = true
                            }
                        } else if (item.base64String.isNotEmpty()) {
                            // Gambar yang sudah ada - gunakan string Base64 yang tersimpan
                            Log.d(TAG, "Using existing image Base64 data")
                            contentParts.add(NotePart.ImagePart(item.base64String))
                        } else {
                            // Tidak ada data gambar yang valid
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
                            // Audio baru - salin ke penyimpanan aplikasi
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
                            // Audio yang sudah ada - gunakan path yang tersimpan
                            Log.d(TAG, "Using existing audio data")
                            contentParts.add(NotePart.AudioPart(item.audioPath, item.title))
                        } else {
                            // Tidak ada data audio yang valid
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

        // Tampilkan peringatan jika ada masalah konversi
        if (conversionError) {
            Toast.makeText(this, "Beberapa media mungkin tidak tersimpan dengan benar", Toast.LENGTH_LONG).show()
        }

        // Serialisasi bagian konten
        val serializedContent = MultimediaUtils.serializeNoteParts(contentParts)
        Log.d(TAG, "Serialized content with ${contentParts.size} parts")

        // Buat atau perbarui catatan di database
        val note = Note(
            id = noteId,
            title = title,
            content = serializedContent
        )

        val success: Boolean
        if (noteId > 0) {
            // Perbarui catatan yang sudah ada
            success = dbHelper.updateNote(note) > 0
        } else {
            // Tambahkan catatan baru
            val newId = dbHelper.insertNote(note)
            success = newId > 0
            if (success) {
                noteId = newId
            }
        }

        // Tampilkan pesan hasil penyimpanan dan akhiri activity jika berhasil
        if (success) {
            Toast.makeText(this, "Catatan berhasil disimpan", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Gagal menyimpan catatan", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Menampilkan dialog konfirmasi untuk menghapus catatan
     */
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

    /**
     * Menghapus catatan dari database
     */
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

    /**
     * Menambahkan gambar ke catatan
     */
    private fun addImageToNote(imageUri: Uri) {
        // Buat ImageView untuk menampilkan gambar
        val imageView = imageSelectorHelper.createImageView(imageUri)
        binding.contentContainer.addView(imageView)

        // Tambahkan ke daftar item konten
        noteContentItems.add(NoteContentItem.Image(imageView, imageUri))

        // Tambahkan EditText kosong setelah gambar
        val newEditText = createEditText("")
        binding.contentContainer.addView(newEditText)
        noteContentItems.add(NoteContentItem.Text(newEditText))

        // Fokus ke EditText dan scroll ke posisinya
        newEditText.requestFocus()
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, newEditText.bottom)
        }
    }

    /**
     * Menambahkan audio ke catatan
     */
    private fun addAudioToNote(audioUri: Uri, audioTitle: String = "Audio Recording") {
        try {
            // Buat AudioPlayerView untuk memutar audio
            val audioPlayerView = AudioPlayerView(this).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setAudioSource(audioUri, audioTitle)
            }

            binding.contentContainer.addView(audioPlayerView)

            // Tambahkan ke daftar item konten
            noteContentItems.add(NoteContentItem.Audio(
                audioView = audioPlayerView,
                uri = audioUri,
                title = audioTitle
            ))

            // Tambahkan EditText kosong setelah audio
            val newEditText = createEditText("")
            binding.contentContainer.addView(newEditText)
            noteContentItems.add(NoteContentItem.Text(newEditText))

            // Fokus ke EditText dan scroll ke posisinya
            newEditText.requestFocus()
            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, newEditText.bottom)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error adding audio to note: ${e.message}")
            Toast.makeText(this, "Gagal menambahkan audio", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Menambahkan pesan error ketika gagal memuat gambar
     */
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

    /**
     * Menambahkan pesan error ketika gagal memuat audio
     */
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

    /**
     * Menambahkan listener backspace ke EditText untuk menangani
     * penghapusan media dengan backspace
     */
    private fun addBackspaceListenerToEditText(editText: EditText) {
        editText.setOnKeyListener { view, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL
                && event.action == KeyEvent.ACTION_DOWN
                && editText.text.isEmpty()) {

                // Cari indeks EditText saat ini dalam daftar item konten
                val currentIndex = noteContentItems.indexOfFirst {
                    (it is NoteContentItem.Text && it.editText == editText)
                }

                // Jika ada item sebelumnya, periksa jenisnya
                if (currentIndex > 0) {
                    when (val prevItem = noteContentItems[currentIndex - 1]) {
                        is NoteContentItem.Image -> {
                            // Hapus gambar dan EditText kosong
                            deleteImageAndEmptyTextField(currentIndex - 1, currentIndex, view)
                            return@setOnKeyListener true
                        }
                        is NoteContentItem.Audio -> {
                            // Hapus audio dan EditText kosong
                            deleteAudioAndEmptyTextField(currentIndex - 1, currentIndex, view)
                            return@setOnKeyListener true
                        }
                        else -> {} // Tidak melakukan apa-apa untuk jenis lain
                    }
                }
            }
            false
        }
    }

    /**
     * Menghapus gambar dan EditText kosong yang mengikutinya
     */
    private fun deleteImageAndEmptyTextField(imageIndex: Int, textFieldIndex: Int, sourceView: View) {
        if (imageIndex >= 0 && imageIndex < noteContentItems.size &&
            textFieldIndex >= 0 && textFieldIndex < noteContentItems.size) {

            // Pastikan item yang akan dihapus adalah Image dan Text
            val imageItem = noteContentItems[imageIndex] as? NoteContentItem.Image ?: return
            val textItem = noteContentItems[textFieldIndex] as? NoteContentItem.Text ?: return

            // Hapus view dari container
            binding.contentContainer.removeView(imageItem.imageView)
            binding.contentContainer.removeView(textItem.editText)

            // Hapus dari daftar item konten
            noteContentItems.removeAt(textFieldIndex)
            noteContentItems.removeAt(imageIndex)

            // Fokus ke TextField sebelumnya jika ada
            if (imageIndex > 0 && noteContentItems[imageIndex - 1] is NoteContentItem.Text) {
                val previousTextField = (noteContentItems[imageIndex - 1] as NoteContentItem.Text).editText
                previousTextField.requestFocus()
                previousTextField.setSelection(previousTextField.text.length)
            }

            // Berikan feedback haptic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sourceView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                sourceView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Minta layout untuk diperbarui
            binding.contentContainer.requestLayout()
        }
    }

    /**
     * Menghapus audio dan EditText kosong yang mengikutinya
     */
    private fun deleteAudioAndEmptyTextField(audioIndex: Int, textFieldIndex: Int, sourceView: View) {
        if (audioIndex >= 0 && audioIndex < noteContentItems.size &&
            textFieldIndex >= 0 && textFieldIndex < noteContentItems.size) {

            // Pastikan item yang akan dihapus adalah Audio dan Text
            val audioItem = noteContentItems[audioIndex] as? NoteContentItem.Audio ?: return
            val textItem = noteContentItems[textFieldIndex] as? NoteContentItem.Text ?: return

            // Hapus view dari container
            binding.contentContainer.removeView(audioItem.audioView)
            binding.contentContainer.removeView(textItem.editText)

            // Hapus dari daftar item konten
            noteContentItems.removeAt(textFieldIndex)
            noteContentItems.removeAt(audioIndex)

            // Fokus ke TextField sebelumnya jika ada
            if (audioIndex > 0 && noteContentItems[audioIndex - 1] is NoteContentItem.Text) {
                val previousTextField = (noteContentItems[audioIndex - 1] as NoteContentItem.Text).editText
                previousTextField.requestFocus()
                previousTextField.setSelection(previousTextField.text.length)
            }

            // Berikan feedback haptic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sourceView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                sourceView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Minta layout untuk diperbarui
            binding.contentContainer.requestLayout()
        }
    }

    /**
     * Membuat EditText baru dengan konfigurasi yang sesuai
     */
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

            // Tambahkan listener backspace untuk menghapus media
            addBackspaceListenerToEditText(this)
        }
    }
}