package com.example.sqllite_notes.db

import android.provider.BaseColumns

/**
 * NoteContract berfungsi sebagai "kontrak" yang mendefinisikan skema database
 * untuk aplikasi Notes.
 *
 * Tujuan dari kontrak ini adalah:
 * 1. Mengisolasi dan mendefinisikan struktur database dalam satu tempat
 * 2. Memudahkan pemeliharaan dan perubahan skema database
 * 3. Memastikan konsistensi dalam penggunaan nama tabel dan kolom
 */
object NoteContract {
    /**
     * NoteEntry mendefinisikan struktur untuk tabel catatan.
     * Kelas ini mewarisi dari BaseColumns yang menyediakan konstanta _ID
     * yang umum digunakan sebagai identitas unik dalam database.
     */
    object NoteEntry : BaseColumns {
        // Nama tabel untuk menyimpan catatan
        const val TABLE_NAME = "notes_sqllite"

        // Nama kolom untuk ID catatan - menggunakan _ID dari BaseColumns
        const val COLUMN_ID = BaseColumns._ID

        // Nama kolom untuk judul catatan
        const val COLUMN_TITLE = "title"

        // Nama kolom untuk konten catatan (teks, gambar, audio)
        const val COLUMN_CONTENT = "content"

        // Nama kolom untuk waktu pembuatan catatan (timestamp)
        const val COLUMN_CREATED_AT = "created_at"
    }

    /**
     * Perintah SQL untuk membuat tabel catatan.
     * Mendefinisikan semua kolom dan tipe datanya:
     * - ID: Integer, kunci primer, auto-increment
     * - Title: Text, wajib diisi (NOT NULL)
     * - Content: Text, opsional (dapat NULL)
     * - Created_at: Integer (timestamp), wajib diisi (NOT NULL)
     */
    const val SQL_CREATE_ENTRIES =
        "CREATE TABLE ${NoteEntry.TABLE_NAME} (" +
                "${NoteEntry.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                "${NoteEntry.COLUMN_TITLE} TEXT NOT NULL," +
                "${NoteEntry.COLUMN_CONTENT} TEXT," +
                "${NoteEntry.COLUMN_CREATED_AT} INTEGER NOT NULL)"

    /**
     * Perintah SQL untuk menghapus tabel catatan jika sudah ada.
     * Digunakan saat upgrade database atau reset data.
     */
    const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${NoteEntry.TABLE_NAME}"
}