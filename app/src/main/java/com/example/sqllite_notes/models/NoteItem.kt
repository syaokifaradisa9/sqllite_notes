package com.example.sqllite_notes.models

import android.net.Uri
import android.widget.EditText
import android.widget.ImageView

sealed class NoteContentItem {
    data class Text(val editText: EditText) : NoteContentItem()
    data class Image(val imageView: ImageView, val uri: Uri) : NoteContentItem()
}

sealed class NotePart {
    data class TextPart(val text: String) : NotePart()
    data class ImagePart(val imagePath: String) : NotePart()
}