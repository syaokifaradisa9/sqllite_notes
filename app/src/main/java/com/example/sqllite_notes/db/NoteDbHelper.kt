package com.example.sqllite_notes.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.sqllite_notes.models.Note

class NoteDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "Notes.db"
    }

    // Membuat tabel notes saat database pertama kali dibuat
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(NoteContract.SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(NoteContract.SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    fun insertNote(note: Note): Long {
        val db = writableDatabase

        // Menyiapkan nilai yang akan dimasukkan ke database
        val values = ContentValues().apply {
            put(NoteContract.NoteEntry.COLUMN_TITLE, note.title)
            put(NoteContract.NoteEntry.COLUMN_CONTENT, note.content)
            put(NoteContract.NoteEntry.COLUMN_CREATED_AT, System.currentTimeMillis())
        }

        // Mengembalikan ID dari catatan yang baru dibuat
        return db.insert(NoteContract.NoteEntry.TABLE_NAME, null, values)
    }

    fun updateNote(note: Note): Int {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(NoteContract.NoteEntry.COLUMN_TITLE, note.title)
            put(NoteContract.NoteEntry.COLUMN_CONTENT, note.content)
        }

        val selection = "${NoteContract.NoteEntry.COLUMN_ID} = ?"
        val selectionArgs = arrayOf(note.id.toString())

        return db.update(
            NoteContract.NoteEntry.TABLE_NAME,
            values,
            selection,
            selectionArgs
        )
    }

    fun deleteNote(noteId: Long): Int {
        val db = writableDatabase

        val selection = "${NoteContract.NoteEntry.COLUMN_ID} = ?"
        val selectionArgs = arrayOf(noteId.toString())

        return db.delete(
            NoteContract.NoteEntry.TABLE_NAME,
            selection,
            selectionArgs
        )
    }

    fun getAllNotes(): List<Note> {
        val db = readableDatabase

        val projection = arrayOf(
            NoteContract.NoteEntry.COLUMN_ID,
            NoteContract.NoteEntry.COLUMN_TITLE,
            NoteContract.NoteEntry.COLUMN_CONTENT,
            NoteContract.NoteEntry.COLUMN_CREATED_AT
        )

        val sortOrder = "${NoteContract.NoteEntry.COLUMN_CREATED_AT} DESC"

        val cursor = db.query(
            NoteContract.NoteEntry.TABLE_NAME,
            projection,
            null,
            null,
            null,
            null,
            sortOrder
        )

        val notes = mutableListOf<Note>()

        with(cursor) {
            while (moveToNext()) {
                val id = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_ID))
                val title = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_TITLE))
                val content = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CONTENT))
                val createdAt = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CREATED_AT))

                notes.add(Note(id, title, content, createdAt))
            }
        }
        cursor.close()

        return notes
    }

    fun getNoteById(noteId: Long): Note? {
        val db = readableDatabase

        val projection = arrayOf(
            NoteContract.NoteEntry.COLUMN_ID,
            NoteContract.NoteEntry.COLUMN_TITLE,
            NoteContract.NoteEntry.COLUMN_CONTENT,
            NoteContract.NoteEntry.COLUMN_CREATED_AT
        )

        val selection = "${NoteContract.NoteEntry.COLUMN_ID} = ?"
        val selectionArgs = arrayOf(noteId.toString())

        val cursor = db.query(
            NoteContract.NoteEntry.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        var note: Note? = null

        with(cursor) {
            if (moveToFirst()) {
                val id = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_ID))
                val title = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_TITLE))
                val content = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CONTENT))
                val createdAt = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CREATED_AT))

                note = Note(id, title, content, createdAt)
            }
        }
        cursor.close()

        return note
    }
}