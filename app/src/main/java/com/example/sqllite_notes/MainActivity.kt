package com.example.sqllite_notes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity(), View.OnClickListener, NoteAdapter.OnNoteClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NoteAdapter
    private lateinit var dbHelper: NoteDbHelper
    private var notes: MutableList<Note> = mutableListOf()

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val REQUEST_ADD_NOTE = 100
        const val REQUEST_UPDATE_NOTE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        dbHelper = NoteDbHelper(this)

        setupRecyclerView()

        val btnAddNote: FloatingActionButton = binding.btnAddNote
        btnAddNote.setOnClickListener(this)

        loadNotesFromDb()
    }

    override fun onResume() {
        super.onResume()
        loadNotesFromDb()
    }

    private fun setupRecyclerView() {
        adapter = NoteAdapter(notes, this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Set up swipe to delete
        setupSwipeToDelete()
    }

    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = notes[position]

                // Show confirmation dialog
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Hapus Catatan")
                    .setMessage("Apakah Anda yakin ingin menghapus catatan \"${note.title}\"?")
                    .setPositiveButton("Hapus") { _, _ ->
                        // Delete the note
                        if (dbHelper.deleteNote(note.id) > 0) {
                            // Remove from list and update adapter
                            notes.removeAt(position)
                            adapter.notifyItemRemoved(position)

                            // Show confirmation
                            Toast.makeText(this@MainActivity, "Catatan berhasil dihapus", Toast.LENGTH_SHORT).show()

                            // Check if we need to show empty view
                            updateEmptyView()
                        } else {
                            // Failed to delete, notify adapter to redraw the item
                            adapter.notifyItemChanged(position)
                            Toast.makeText(this@MainActivity, "Gagal menghapus catatan", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Batal") { _, _ ->
                        // User canceled, notify adapter to redraw the item
                        adapter.notifyItemChanged(position)
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun loadNotesFromDb() {
        notes.clear()
        notes.addAll(dbHelper.getAllNotes())

        adapter.updateNotes(notes)
        updateEmptyView()
    }

    private fun updateEmptyView() {
        if (notes.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_add_note -> {
                val intent = Intent(this@MainActivity, AddNoteActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onNoteClick(note: Note) {
        val intent = Intent(this@MainActivity, AddNoteActivity::class.java)
        intent.putExtra(EXTRA_NOTE_ID, note.id)
        startActivity(intent)
    }
}