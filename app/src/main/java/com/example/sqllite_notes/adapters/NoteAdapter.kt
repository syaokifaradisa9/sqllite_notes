package com.example.sqllite_notes.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sqllite_notes.R
import com.example.sqllite_notes.models.Note
import com.google.android.material.card.MaterialCardView

class NoteAdapter(
    private var notes: List<Note>,
    private val listener: OnNoteClickListener
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    interface OnNoteClickListener {
        fun onNoteClick(note: Note)
    }

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteCard: MaterialCardView = itemView.findViewById(R.id.note_card)
        val noteTitle: TextView = itemView.findViewById(R.id.note_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]

        holder.noteTitle.text = note.title

        holder.noteCard.setOnClickListener {
            listener.onNoteClick(note)
        }
    }

    override fun getItemCount(): Int = notes.size

    fun updateNotes(newNotes: List<Note>) {
        this.notes = newNotes
        notifyDataSetChanged()
    }
}