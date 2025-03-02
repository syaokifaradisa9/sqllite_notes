package com.example.sqllite_notes.utils

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

class AudioSelectorHelper(
    private val activity: AppCompatActivity,
    private val onAudioSelected: (Uri, String) -> Unit
) {
    private val TAG = "AudioSelectorHelper"

    // Activity result launcher for picking audio
    private val pickAudioLauncher: ActivityResultLauncher<Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val audioTitle = getAudioFileName(uri) ?: "Audio Recording"
                onAudioSelected(uri, audioTitle)
            }
        }
    }

    /**
     * Opens the system audio picker to select an audio file
     */
    fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        pickAudioLauncher.launch(intent)
    }

    /**
     * Gets the file name of the selected audio
     */
    fun getAudioFileName(uri: Uri): String? {
        val cursor = activity.contentResolver.query(uri, null, null, null, null)

        return cursor?.use {
            val nameIndex = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex)
            }
            null
        } ?: run {
            // If we can't get the name from content resolver, try to get it from the URI path
            uri.lastPathSegment?.split("/")?.lastOrNull()
        }
    }

    /**
     * Gets the duration of the audio file
     */
    fun getAudioDuration(uri: Uri): Long {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(activity, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()

            return durationStr?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            return 0
        }
    }

    /**
     * Copies the audio file to internal storage
     */
    fun copyAudioToInternalStorage(uri: Uri): String {
        val fileName = getAudioFileName(uri) ?: "audio_${System.currentTimeMillis()}.mp3"
        val file = File(activity.filesDir, "audio_notes")

        if (!file.exists()) {
            file.mkdirs()
        }

        val audioFile = File(file, fileName)

        try {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(audioFile).use { output ->
                    val buffer = ByteArray(4 * 1024) // 4K buffer
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            return audioFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error copying audio file: ${e.message}")
            return ""
        }
    }
}