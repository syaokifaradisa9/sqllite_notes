package com.example.sqllite_notes

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sqllite_notes.adapters.NoteAdapter
import com.example.sqllite_notes.databinding.ActivityMainBinding
import com.example.sqllite_notes.db.NoteDbHelper
import com.example.sqllite_notes.models.Note
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
    }

    private fun loadNotesFromDb() {
        notes.clear()
        notes.addAll(dbHelper.getAllNotes())

        adapter.updateNotes(notes)

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