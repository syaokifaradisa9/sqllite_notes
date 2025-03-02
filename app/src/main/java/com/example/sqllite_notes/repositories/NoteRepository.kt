package com.example.sqllite_notes.repository

import android.content.Context
import android.net.Uri
import com.example.sqllite_notes.db.NoteDbHelper
import com.example.sqllite_notes.models.Note
import com.example.sqllite_notes.models.NotePart
import com.example.sqllite_notes.utils.MultimediaUtils

class NoteRepository(context: Context) {
    private val dbHelper = NoteDbHelper(context)
    private val contentResolver = context.contentResolver

    fun getAllNotes(): List<Note> {
        return dbHelper.getAllNotes()
    }

    fun getNoteById(noteId: Long): Note? {
        return dbHelper.getNoteById(noteId)
    }

    fun saveNote(note: Note, contentParts: List<NotePart>): Boolean {
        val serializedContent = MultimediaUtils.serializeNoteParts(contentParts)
        val noteToSave = note.copy(content = serializedContent)

        return if (note.id > 0) {
            // Update existing note
            dbHelper.updateNote(noteToSave) > 0
        } else {
            // Insert new note
            dbHelper.insertNote(noteToSave) > 0
        }
    }

    fun deleteNote(noteId: Long): Boolean {
        return dbHelper.deleteNote(noteId) > 0
    }

    fun convertImageToBase64(uri: Uri): String? {
        return MultimediaUtils.uriToBase64(contentResolver, uri)
    }

    fun parseNoteContent(content: String): List<NotePart> {
        return MultimediaUtils.deserializeNoteParts(content)
    }
}