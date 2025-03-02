package com.example.sqllite_notes.db

import android.provider.BaseColumns

object NoteContract {
    object NoteEntry : BaseColumns {
        const val TABLE_NAME = "notes_sqllite"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_TITLE = "title"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_CREATED_AT = "created_at"
    }

    const val SQL_CREATE_ENTRIES =
        "CREATE TABLE ${NoteEntry.TABLE_NAME} (" +
                "${NoteEntry.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                "${NoteEntry.COLUMN_TITLE} TEXT NOT NULL," +
                "${NoteEntry.COLUMN_CONTENT} TEXT," +
                "${NoteEntry.COLUMN_CREATED_AT} INTEGER NOT NULL)"

    const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${NoteEntry.TABLE_NAME}"
}