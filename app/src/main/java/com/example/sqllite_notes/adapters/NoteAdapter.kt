package com.example.sqllite_notes.adapters

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sqllite_notes.R
import com.example.sqllite_notes.models.Note
import com.google.android.material.card.MaterialCardView
import java.util.Date

/**
 * NoteAdapter adalah kelas yang berfungsi sebagai jembatan antara data catatan
 * dengan tampilan RecyclerView pada MainActivity.
 *
 * Adapter ini bertanggung jawab untuk:
 * 1. Membuat holder untuk setiap item catatan
 * 2. Mengikat data catatan ke dalam view holder
 * 3. Menangani interaksi klik pada catatan
 * 4. Memperbarui daftar catatan ketika ada perubahan
 */
class NoteAdapter(
    private var notes: List<Note>,
    private val listener: OnNoteClickListener
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    /**
     * Interface yang digunakan untuk menangani kejadian klik pada catatan.
     * Interface ini diimplementasikan oleh MainActivity untuk membuka
     * aktivitas edit catatan ketika pengguna mengklik salah satu item.
     */
    interface OnNoteClickListener {
        /**
         * Dipanggil ketika pengguna mengklik catatan.
         * @param note Objek catatan yang diklik
         */
        fun onNoteClick(note: Note)
    }

    /**
     * ViewHolder untuk menampung dan mengelola tampilan item catatan.
     * Menampung referensi ke komponen UI yang perlu diperbarui untuk setiap item.
     */
    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Komponen UI dari layout item_note.xml
        val noteCard: MaterialCardView = itemView.findViewById(R.id.note_card)
        val noteTitle: TextView = itemView.findViewById(R.id.note_title)
        val noteDate: TextView = itemView.findViewById(R.id.note_date)
    }

    /**
     * Dipanggil ketika RecyclerView membutuhkan ViewHolder baru.
     * Membuat dan menginisialisasi ViewHolder serta tampilan yang dikaitkan dengannya.
     *
     * @param parent ViewGroup induk tempat tampilan baru akan ditambahkan
     * @param viewType Jenis tampilan yang dibuat (tidak digunakan dalam kasus ini)
     * @return ViewHolder baru yang menampung tampilan item catatan
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        // Inflate layout item_note.xml menjadi view
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    /**
     * Dipanggil oleh RecyclerView untuk menampilkan data pada posisi tertentu.
     * Memperbarui konten ViewHolder untuk menampilkan item pada posisi yang diberikan.
     *
     * @param holder ViewHolder yang perlu diperbarui
     * @param position Posisi item dalam daftar data
     */
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        // Ambil objek catatan pada posisi yang diberikan
        val note = notes[position]

        // Atur judul catatan
        holder.noteTitle.text = note.title

        // Format tanggal pembuatan catatan
        val dateFormat = DateFormat.getMediumDateFormat(holder.itemView.context)
        val formattedDate = dateFormat.format(Date(note.createdAt))
        holder.noteDate.text = formattedDate

        // Atur listener klik pada kartu catatan
        holder.noteCard.setOnClickListener {
            listener.onNoteClick(note)
        }
    }

    /**
     * Mengembalikan jumlah total item dalam daftar data.
     *
     * @return Jumlah catatan dalam daftar
     */
    override fun getItemCount(): Int = notes.size

    /**
     * Memperbarui daftar catatan dengan daftar baru dan
     * memberitahu adapter bahwa data telah berubah.
     *
     * Metode ini dipanggil ketika ada perubahan pada data catatan,
     * misalnya setelah menambah, mengedit, atau menghapus catatan.
     *
     * @param newNotes Daftar catatan baru yang akan ditampilkan
     */
    fun updateNotes(newNotes: List<Note>) {
        this.notes = newNotes
        notifyDataSetChanged()
    }
}