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

class MainActivity : AppCompatActivity(), NoteAdapter.OnNoteClickListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NoteAdapter
    private lateinit var dbHelper: NoteDbHelper
    private lateinit var themeManager: ThemeManager
    private var notes: MutableList<Note> = mutableListOf()

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize theme manager and apply current theme
        themeManager = ThemeManager(this)
        themeManager.applyTheme()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NoteDbHelper(this)

        // Konfigurasi toolbar dan RecyclerView
        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        loadNotesFromDb()

        // Tombol tambah catatan baru - Akan membuka halaman AddNoteActivity
        binding.btnAddNote.setOnClickListener(View.OnClickListener {
            val intent = Intent(this@MainActivity, AddNoteActivity::class.java)
            startActivity(intent)
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme -> {
                showThemeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotesFromDb()
    }

    private fun setupRecyclerView() {
        adapter = NoteAdapter(notes, this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        setupSwipeToDelete()
    }

    // Show theme selection dialog
    private fun showThemeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_theme_selector, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.rgThemeOptions)

        // Set the current theme option as checked
        when (themeManager.getThemeMode()) {
            ThemeManager.MODE_LIGHT -> dialogView.findViewById<RadioButton>(R.id.rbLightTheme).isChecked = true
            ThemeManager.MODE_DARK -> dialogView.findViewById<RadioButton>(R.id.rbDarkTheme).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.rbSystemDefault).isChecked = true
        }

        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Get selected theme mode and apply it
                val selectedThemeMode = when (radioGroup.checkedRadioButtonId) {
                    R.id.rbLightTheme -> ThemeManager.MODE_LIGHT
                    R.id.rbDarkTheme -> ThemeManager.MODE_DARK
                    else -> ThemeManager.MODE_SYSTEM
                }

                // Only apply if theme changed
                if (selectedThemeMode != themeManager.getThemeMode()) {
                    themeManager.setThemeMode(selectedThemeMode)
                    Toast.makeText(this, "Theme updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    // Pengambilan Data dari database
    private fun loadNotesFromDb() {
        notes.clear()
        notes.addAll(dbHelper.getAllNotes())

        adapter.updateNotes(notes)
        updateEmptyView()
    }

    // Setup pemeriksaan apakah ada data
    private fun updateEmptyView() {
        if (notes.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    // Klik Note (Menuju ke halaman edit note)
    override fun onNoteClick(note: Note) {
        val intent = Intent(this@MainActivity, AddNoteActivity::class.java)
        intent.putExtra(EXTRA_NOTE_ID, note.id)
        startActivity(intent)
    }

    // Aksi Delete
    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = notes[position]

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Hapus Catatan")
                    .setMessage("Apakah Anda yakin ingin menghapus catatan \"${note.title}\"?")
                    .setPositiveButton("Hapus") { _, _ ->
                        if (dbHelper.deleteNote(note.id) > 0) {
                            notes.removeAt(position)
                            adapter.notifyItemRemoved(position)

                            Toast.makeText(this@MainActivity, "Catatan berhasil dihapus", Toast.LENGTH_SHORT).show()

                            updateEmptyView()
                        } else {
                            adapter.notifyItemChanged(position)
                            Toast.makeText(this@MainActivity, "Gagal menghapus catatan", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Batal") { _, _ ->
                        adapter.notifyItemChanged(position)
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }
}