package com.example.sqllite_notes

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
    // View binding for accessing UI elements
    private lateinit var binding: ActivityMainBinding

    // Adapter for connecting note data with RecyclerView
    private lateinit var adapter: NoteAdapter

    // Helper for accessing and manipulating database
    private lateinit var dbHelper: NoteDbHelper

    // Manager for handling app theme
    private lateinit var themeManager: ThemeManager

    // List to store notes from database
    private var notes: MutableList<Note> = mutableListOf()

    // Tag for logging
    private val TAG = "MainActivity"

    companion object {
        // Constant for sending note ID to AddNoteActivity
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize and apply theme
        themeManager = ThemeManager(this)
        themeManager.applyTheme()

        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize database helper
        dbHelper = NoteDbHelper(this)

        // Configure toolbar
        setSupportActionBar(binding.toolbar)

        // Set up RecyclerView
        setupRecyclerView()

        // Load notes from database
        loadNotesFromDb()

        // Add listener for add note button
        binding.btnAddNote.setOnClickListener {
            val intent = Intent(this@MainActivity, AddNoteActivity::class.java)
            startActivity(intent)
        }
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

    private fun showThemeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_theme_selector, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.rgThemeOptions)

        when (themeManager.getThemeMode()) {
            ThemeManager.MODE_LIGHT -> dialogView.findViewById<RadioButton>(R.id.rbLightTheme).isChecked = true
            ThemeManager.MODE_DARK -> dialogView.findViewById<RadioButton>(R.id.rbDarkTheme).isChecked = true
            else -> dialogView.findViewById<RadioButton>(R.id.rbSystemDefault).isChecked = true
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selectedThemeMode = when (radioGroup.checkedRadioButtonId) {
                    R.id.rbLightTheme -> ThemeManager.MODE_LIGHT
                    R.id.rbDarkTheme -> ThemeManager.MODE_DARK
                    else -> ThemeManager.MODE_SYSTEM
                }

                if (selectedThemeMode != themeManager.getThemeMode()) {
                    themeManager.setThemeMode(selectedThemeMode)
                    Toast.makeText(this, "Theme updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun loadNotesFromDb() {
        notes.clear()
        notes.addAll(dbHelper.getAllNotes())
        adapter.updateNotes(notes)
        updateEmptyView()
    }

    private fun updateEmptyView() {
        if (notes.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onNoteClick(note: Note) {
        val intent = Intent(this@MainActivity, AddNoteActivity::class.java)
        intent.putExtra(EXTRA_NOTE_ID, note.id)
        startActivity(intent)
    }

    override fun onNoteDuplicate(note: Note) {
        duplicateNote(note)
    }

    /**
     * Shows confirmation dialog for deletion and handles the deletion process
     */
    private fun confirmAndDeleteNote(note: Note, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Catatan")
            .setMessage("Apakah Anda yakin ingin menghapus catatan \"${note.title}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteNote(note, position)
            }
            .setNegativeButton("Batal") { _, _ ->
                // If user cancels, restore item view
                adapter.notifyItemChanged(position)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Implementation of OnNoteClickListener interface for deletion
     * Simply delegates to the confirmation dialog
     */
    override fun onNoteDelete(note: Note, position: Int) {
        confirmAndDeleteNote(note, position)
    }

    /**
     * Handles the actual deletion of a note with improved error handling
     */
    private fun deleteNote(note: Note, position: Int) {
        // Log deletion attempt for debugging
        Log.d(TAG, "Attempting to delete note with ID: ${note.id}, position: $position")

        try {
            // Delete note from database
            val result = dbHelper.deleteNote(note.id)
            Log.d(TAG, "Delete result: $result rows affected")

            if (result > 0) {
                // Check if position is valid
                if (position >= 0 && position < notes.size) {
                    // Remove from local list and update adapter
                    notes.removeAt(position)
                    adapter.notifyItemRemoved(position)

                    // Show success message
                    Toast.makeText(this, "Catatan berhasil dihapus", Toast.LENGTH_SHORT).show()

                    // Update empty state view if needed
                    updateEmptyView()
                } else {
                    Log.e(TAG, "Invalid position: $position, list size: ${notes.size}")
                    // Reload all notes to ensure list is in sync
                    loadNotesFromDb()
                    Toast.makeText(this, "Catatan berhasil dihapus", Toast.LENGTH_SHORT).show()
                }
            } else {
                // If delete fails, restore item view
                adapter.notifyItemChanged(position)
                Log.e(TAG, "Database delete returned 0 rows affected for ID: ${note.id}")
                Toast.makeText(this, "Gagal menghapus catatan - ID tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Handle any exceptions during deletion
            Log.e(TAG, "Error deleting note: ${e.message}", e)
            adapter.notifyItemChanged(position)
            Toast.makeText(this, "Gagal menghapus catatan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun duplicateNote(note: Note) {
        // Create a copy of the note
        val duplicatedNote = Note(
            id = 0, // ID 0 indicates this is a new note
            title = note.title,
            content = note.content,
            createdAt = System.currentTimeMillis()
        )

        // Save duplicated note to database
        val newId = dbHelper.insertNote(duplicatedNote)

        if (newId > 0) {
            // If successfully saved, add note to list and update UI
            val newNote = duplicatedNote.copy(id = newId)
            notes.add(0, newNote) // Add at beginning of list
            adapter.updateNotes(notes)
            updateEmptyView()

            // Scroll to top to show new note
            binding.recyclerView.smoothScrollToPosition(0)

            // Notify user that note was successfully duplicated
            Toast.makeText(this, "Note duplicated", Toast.LENGTH_SHORT).show()
        } else {
            // If save failed, show error message
            Toast.makeText(this, "Failed to duplicate note", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition

                // Check if position is valid
                if (position != RecyclerView.NO_POSITION && position < notes.size) {
                    val note = notes[position]
                    confirmAndDeleteNote(note, position)
                } else {
                    // Invalid position
                    Log.e(TAG, "Invalid position in swipe: $position")
                    adapter.notifyDataSetChanged() // Refresh the entire list
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }
}