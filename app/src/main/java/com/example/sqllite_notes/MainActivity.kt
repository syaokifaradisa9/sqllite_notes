package com.example.sqllite_notes

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sqllite_notes.adapters.NoteAdapter
import com.example.sqllite_notes.databinding.ActivityMainBinding
import com.example.sqllite_notes.db.NoteDbHelper
import com.example.sqllite_notes.models.Note
import com.example.sqllite_notes.utils.SwipeToDeleteCallback
import com.example.sqllite_notes.utils.ThemeManager

/**
 * MainActivity merupakan kelas utama yang bertanggung jawab menampilkan daftar catatan
 * dan menyediakan interaksi untuk pengguna seperti membuat catatan baru,
 * menghapus catatan, dan mengubah tema aplikasi.
 */
class MainActivity : AppCompatActivity(), NoteAdapter.OnNoteClickListener {
    // View binding untuk mengakses elemen UI tanpa findViewById
    private lateinit var binding: ActivityMainBinding

    // Adapter untuk menghubungkan data catatan dengan RecyclerView
    private lateinit var adapter: NoteAdapter

    // Helper untuk mengakses dan memanipulasi database SQLite
    private lateinit var dbHelper: NoteDbHelper

    // Manager untuk mengelola tema aplikasi (light/dark/system)
    private lateinit var themeManager: ThemeManager

    // Daftar untuk menyimpan catatan dari database
    private var notes: MutableList<Note> = mutableListOf()

    companion object {
        // Konstanta untuk mengirim ID catatan ke AddNoteActivity
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    /**
     * Dipanggil saat activity pertama kali dibuat.
     * Menginisialisasi UI dan menghubungkan dengan data.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inisialisasi dan terapkan tema aplikasi berdasarkan preferensi pengguna
        themeManager = ThemeManager(this)
        themeManager.applyTheme()

        // Inisialisasi view binding dan set sebagai content view
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi helper database untuk mengakses catatan
        dbHelper = NoteDbHelper(this)

        // Konfigurasi toolbar sebagai action bar
        setSupportActionBar(binding.toolbar)

        // Siapkan RecyclerView untuk menampilkan daftar catatan
        setupRecyclerView()

        // Muat catatan dari database
        loadNotesFromDb()

        // Tambahkan listener untuk tombol tambah catatan baru (FAB)
        binding.btnAddNote.setOnClickListener {
            // Buat intent untuk berpindah ke AddNoteActivity
            val intent = Intent(this@MainActivity, AddNoteActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Dipanggil saat menu options perlu dibuat.
     * Menambahkan menu tema ke action bar.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate layout menu dari resource
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    /**
     * Dipanggil saat item menu dipilih.
     * Menangani pemilihan item menu (ganti tema).
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme -> {
                // Jika tombol tema ditekan, tampilkan dialog pilihan tema
                showThemeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Dipanggil setiap kali activity menjadi visible kepada pengguna.
     * Memastikan data catatan selalu dalam kondisi terbaru.
     */
    override fun onResume() {
        super.onResume()
        // Muat ulang catatan dari database saat kembali ke activity
        // Misalnya setelah membuat/mengedit catatan
        loadNotesFromDb()
    }

    /**
     * Menyiapkan RecyclerView dan adapter untuk menampilkan daftar catatan.
     * Juga menyiapkan fitur swipe-to-delete.
     */
    private fun setupRecyclerView() {
        // Inisialisasi adapter dengan daftar catatan dan listener klik
        adapter = NoteAdapter(notes, this)

        // Atur layout manager untuk RecyclerView (tampilan daftar vertikal)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // Hubungkan adapter ke RecyclerView
        binding.recyclerView.adapter = adapter

        // Siapkan gesture swipe untuk menghapus catatan
        setupSwipeToDelete()
    }

    /**
     * Menampilkan dialog untuk memilih tema aplikasi.
     * Pengguna dapat memilih tema Light, Dark, atau System Default.
     */
    private fun showThemeDialog() {
        // Inflate layout dialog dari resource
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_theme_selector, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.rgThemeOptions)

        // Centang opsi tema yang saat ini digunakan
        when (themeManager.getThemeMode()) {
            ThemeManager.MODE_LIGHT -> dialogView.findViewById<RadioButton>(R.id.rbLightTheme).isChecked = true
            ThemeManager.MODE_DARK -> dialogView.findViewById<RadioButton>(R.id.rbDarkTheme).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.rbSystemDefault).isChecked = true
        }

        // Buat dan tampilkan dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Dapatkan mode tema yang dipilih
                val selectedThemeMode = when (radioGroup.checkedRadioButtonId) {
                    R.id.rbLightTheme -> ThemeManager.MODE_LIGHT
                    R.id.rbDarkTheme -> ThemeManager.MODE_DARK
                    else -> ThemeManager.MODE_SYSTEM
                }

                // Terapkan tema hanya jika berubah dari sebelumnya
                if (selectedThemeMode != themeManager.getThemeMode()) {
                    themeManager.setThemeMode(selectedThemeMode)
                    Toast.makeText(this, "Theme updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    /**
     * Memuat catatan dari database ke dalam daftar.
     * Juga memperbarui UI sesuai dengan ketersediaan data.
     */
    private fun loadNotesFromDb() {
        // Kosongkan daftar catatan saat ini
        notes.clear()

        // Tambahkan semua catatan dari database ke daftar
        notes.addAll(dbHelper.getAllNotes())

        // Perbarui adapter dengan daftar catatan yang baru
        adapter.updateNotes(notes)

        // Perbarui tampilan UI (tampilkan pesan kosong jika tidak ada catatan)
        updateEmptyView()
    }

    /**
     * Memperbarui tampilan UI berdasarkan ketersediaan data.
     * Menampilkan pesan "Belum ada catatan" jika tidak ada catatan.
     */
    private fun updateEmptyView() {
        if (notes.isEmpty()) {
            // Jika tidak ada catatan, tampilkan pesan kosong
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            // Jika ada catatan, tampilkan daftar catatan
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    /**
     * Implementasi dari interface OnNoteClickListener.
     * Dipanggil saat pengguna mengklik salah satu catatan.
     */
    override fun onNoteClick(note: Note) {
        // Buat intent untuk membuka AddNoteActivity dalam mode edit
        val intent = Intent(this@MainActivity, AddNoteActivity::class.java)

        // Kirim ID catatan yang diklik ke AddNoteActivity
        intent.putExtra(EXTRA_NOTE_ID, note.id)

        // Mulai activity untuk mengedit catatan
        startActivity(intent)
    }

    /**
     * Mengatur fitur swipe-to-delete untuk RecyclerView.
     * Memungkinkan pengguna menghapus catatan dengan menggeser ke kiri.
     */
    private fun setupSwipeToDelete() {
        // Buat callback untuk menangani aksi swipe
        val swipeToDeleteCallback = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Dapatkan posisi item yang di-swipe
                val position = viewHolder.adapterPosition
                val note = notes[position]

                // Tampilkan dialog konfirmasi sebelum menghapus
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Hapus Catatan")
                    .setMessage("Apakah Anda yakin ingin menghapus catatan \"${note.title}\"?")
                    .setPositiveButton("Hapus") { _, _ ->
                        // Jika pengguna mengkonfirmasi, hapus catatan dari database
                        if (dbHelper.deleteNote(note.id) > 0) {
                            // Hapus dari daftar lokal dan perbarui adapter
                            notes.removeAt(position)
                            adapter.notifyItemRemoved(position)

                            // Tampilkan pesan berhasil
                            Toast.makeText(this@MainActivity, "Catatan berhasil dihapus", Toast.LENGTH_SHORT).show()

                            // Perbarui tampilan empty state jika perlu
                            updateEmptyView()
                        } else {
                            // Jika gagal menghapus, kembalikan tampilan item
                            adapter.notifyItemChanged(position)
                            Toast.makeText(this@MainActivity, "Gagal menghapus catatan", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Batal") { _, _ ->
                        // Jika pengguna membatalkan, kembalikan tampilan item
                        adapter.notifyItemChanged(position)
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        // Hubungkan callback ke RecyclerView dengan ItemTouchHelper
        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }
}