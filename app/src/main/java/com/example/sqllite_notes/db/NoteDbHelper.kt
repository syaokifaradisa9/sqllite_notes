package com.example.sqllite_notes.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.sqllite_notes.models.Note

/**
 * NoteDbHelper adalah class yang mengelola akses ke database SQLite.
 * class ini mewarisi SQLiteOpenHelper dan bertanggung jawab untuk:
 * 1. Membuat dan memperbarui struktur database
 * 2. Membuka koneksi ke database
 * 3. Menyediakan metode-metode untuk operasi CRUD (Create, Read, Update, Delete) pada catatan
 *
 * class ini bertindak sebagai lapisan abstraksi antara kode aplikasi dengan database SQLite,
 * memudahkan pengelolaan data tanpa perlu menulis kueri SQL langsung di class lain.
 */
class NoteDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /**
     * Konstanta yang digunakan untuk konfigurasi database
     */
    companion object {
        // Versi database - Dinaikkan saat skema database berubah
        const val DATABASE_VERSION = 1

        // Nama file database yang akan disimpan di perangkat
        const val DATABASE_NAME = "Notes.db"
    }

    /**
     * Dipanggil saat database dibuat untuk pertama kali.
     * Membuat tabel catatan dengan struktur yang didefinisikan di NoteContract.
     *
     * @param db Instance database yang baru dibuat
     */
    override fun onCreate(db: SQLiteDatabase) {
        // Eksekusi perintah SQL untuk membuat tabel dari NoteContract
        db.execSQL(NoteContract.SQL_CREATE_ENTRIES)
    }

    /**
     * Dipanggil saat versi database perlu diperbarui (misalnya setelah update aplikasi).
     * Dalam implementasi sederhana ini, tabel yang ada dihapus dan dibuat ulang.
     *
     * Catatan: Pada aplikasi produksi, sebaiknya gunakan strategi migrasi
     * yang tidak menghapus data pengguna.
     *
     * @param db Instance database
     * @param oldVersion Versi database lama
     * @param newVersion Versi database baru
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Hapus tabel lama dan buat yang baru
        db.execSQL(NoteContract.SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    /**
     * Dipanggil saat versi database diturunkan (downgrade).
     * Jarang digunakan, tetapi ditambahkan untuk kelengkapan.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    /**
     * Menyimpan catatan baru ke dalam database.
     *
     * @param note Objek catatan yang akan disimpan
     * @return ID dari catatan yang baru dibuat, atau -1 jika gagal
     */
    fun insertNote(note: Note): Long {
        // Dapatkan database dalam mode tulis
        val db = writableDatabase

        // Siapkan nilai-nilai yang akan dimasukkan ke database dengan ContentValues
        val values = ContentValues().apply {
            // Masukkan data catatan ke dalam ContentValues
            put(NoteContract.NoteEntry.COLUMN_TITLE, note.title)
            put(NoteContract.NoteEntry.COLUMN_CONTENT, note.content)
            put(NoteContract.NoteEntry.COLUMN_CREATED_AT, System.currentTimeMillis())
        }

        // Lakukan operasi insert dan kembalikan ID dari catatan baru
        // -1 dikembalikan jika operasi gagal
        return db.insert(NoteContract.NoteEntry.TABLE_NAME, null, values)
    }

    /**
     * Memperbarui catatan yang sudah ada di database.
     *
     * @param note Objek catatan yang akan diperbarui (harus memiliki ID yang valid)
     * @return Jumlah baris yang terpengaruh (1 jika berhasil, 0 jika tidak ada yang diperbarui)
     */
    fun updateNote(note: Note): Int {
        // Dapatkan database dalam mode tulis
        val db = writableDatabase

        // Siapkan nilai-nilai yang akan diperbarui
        val values = ContentValues().apply {
            put(NoteContract.NoteEntry.COLUMN_TITLE, note.title)
            put(NoteContract.NoteEntry.COLUMN_CONTENT, note.content)
            // Catatan: Timestamp created_at tidak diperbarui saat edit
        }

        // Definisikan kriteria WHERE untuk memfilter catatan berdasarkan ID
        val selection = "${NoteContract.NoteEntry.COLUMN_ID} = ?"
        val selectionArgs = arrayOf(note.id.toString())

        // Lakukan operasi update dan kembalikan jumlah baris yang diperbarui
        return db.update(
            NoteContract.NoteEntry.TABLE_NAME,
            values,
            selection,
            selectionArgs
        )
    }

    /**
     * Menghapus catatan dari database berdasarkan ID.
     *
     * @param noteId ID catatan yang akan dihapus
     * @return Jumlah baris yang dihapus (1 jika berhasil, 0 jika tidak ditemukan)
     */
    fun deleteNote(noteId: Long): Int {
        // Dapatkan database dalam mode tulis
        val db = writableDatabase

        // Definisikan kriteria WHERE untuk memfilter catatan berdasarkan ID
        val selection = "${NoteContract.NoteEntry.COLUMN_ID} = ?"
        val selectionArgs = arrayOf(noteId.toString())

        // Lakukan operasi delete dan kembalikan jumlah baris yang dihapus
        return db.delete(
            NoteContract.NoteEntry.TABLE_NAME,
            selection,
            selectionArgs
        )
    }

    /**
     * Mengambil semua catatan dari database.
     * Catatan diurutkan berdasarkan waktu pembuatan, dari yang terbaru.
     *
     * @return Daftar objek Note yang berisi semua catatan dalam database
     */
    fun getAllNotes(): List<Note> {
        // Dapatkan database dalam mode baca
        val db = readableDatabase

        // Definisikan kolom yang ingin diambil
        val projection = arrayOf(
            NoteContract.NoteEntry.COLUMN_ID,
            NoteContract.NoteEntry.COLUMN_TITLE,
            NoteContract.NoteEntry.COLUMN_CONTENT,
            NoteContract.NoteEntry.COLUMN_CREATED_AT
        )

        // Urutan hasil (dari catatan terbaru ke terlama)
        val sortOrder = "${NoteContract.NoteEntry.COLUMN_CREATED_AT} DESC"

        // Lakukan query untuk mendapatkan semua catatan
        val cursor = db.query(
            NoteContract.NoteEntry.TABLE_NAME,   // Nama tabel
            projection,                          // Kolom yang diambil
            null,                       // Tidak ada filter WHERE
            null,                    // Tidak ada argumen WHERE
            null,                       // Tidak ada GROUP BY
            null,                        // Tidak ada HAVING
            sortOrder                           // Urutan hasil
        )

        // List untuk menampung hasil query
        val notes = mutableListOf<Note>()

        // Iterasi cursor untuk membaca data dari hasil query
        with(cursor) {
            while (moveToNext()) {
                // Ambil nilai-nilai kolom untuk setiap catatan
                val id = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_ID))
                val title = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_TITLE))
                val content = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CONTENT))
                val createdAt = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CREATED_AT))

                // Buat objek Note dan tambahkan ke daftar
                notes.add(Note(id, title, content, createdAt))
            }
        }

        // Jangan lupa tutup cursor setelah selesai digunakan
        cursor.close()

        return notes
    }

    /**
     * Mengambil satu catatan berdasarkan ID.
     *
     * @param noteId ID catatan yang ingin diambil
     * @return Objek Note jika ditemukan, atau null jika tidak ada
     */
    fun getNoteById(noteId: Long): Note? {
        // Dapatkan database dalam mode baca
        val db = readableDatabase

        // Definisikan kolom yang ingin diambil
        val projection = arrayOf(
            NoteContract.NoteEntry.COLUMN_ID,
            NoteContract.NoteEntry.COLUMN_TITLE,
            NoteContract.NoteEntry.COLUMN_CONTENT,
            NoteContract.NoteEntry.COLUMN_CREATED_AT
        )

        // Filter untuk mengambil catatan dengan ID tertentu
        val selection = "${NoteContract.NoteEntry.COLUMN_ID} = ?"
        val selectionArgs = arrayOf(noteId.toString())

        // Lakukan query untuk mendapatkan catatan dengan ID tertentu
        val cursor = db.query(
            NoteContract.NoteEntry.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        // Variabel untuk menyimpan hasil
        var note: Note? = null

        // Baca data jika cursor berhasil mendapatkan hasil
        with(cursor) {
            if (moveToFirst()) {
                // Ambil nilai-nilai kolom
                val id = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_ID))
                val title = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_TITLE))
                val content = getString(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CONTENT))
                val createdAt = getLong(getColumnIndexOrThrow(NoteContract.NoteEntry.COLUMN_CREATED_AT))

                // Buat objek Note
                note = Note(id, title, content, createdAt)
            }
        }

        // Tutup cursor
        cursor.close()

        return note
    }
}