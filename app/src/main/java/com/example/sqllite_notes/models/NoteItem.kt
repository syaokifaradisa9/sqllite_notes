package com.example.sqllite_notes.models

import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.ImageView

sealed class NoteContentItem {
    data class Text(val editText: EditText) : NoteContentItem()

    data class Image(
        val imageView: ImageView,
        val uri: Uri,
        var base64String: String = ""
    ) : NoteContentItem()

    data class Audio(
        val audioView: View,
        val uri: Uri,
        var audioPath: String = "",
        var title: String = "Audio Recording"
    ) : NoteContentItem()
}

sealed class NotePart {
    data class TextPart(val text: String) : NotePart()
    data class ImagePart(val imagePath: String) : NotePart()
    data class AudioPart(val audioPath: String, val title: String = "Audio Recording") : NotePart()
}