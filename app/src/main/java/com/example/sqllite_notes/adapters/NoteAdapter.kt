package com.example.sqllite_notes.adapters

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sqllite_notes.R
import com.example.sqllite_notes.models.Note
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Date

class NoteAdapter(
    private var notes: List<Note>,
    private val listener: OnNoteClickListener
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    interface OnNoteClickListener {
        fun onNoteClick(note: Note)
        fun onNoteDuplicate(note: Note)
        fun onNoteDelete(note: Note, position: Int)
    }

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteCard: MaterialCardView = itemView.findViewById(R.id.note_card)
        val noteTitle: TextView = itemView.findViewById(R.id.note_title)
        val noteDate: TextView = itemView.findViewById(R.id.note_date)
        val btnDuplicate: MaterialButton = itemView.findViewById(R.id.btn_duplicate)
        val btnDelete: MaterialButton = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]

        holder.noteTitle.text = note.title + " " + note.id

        val dateFormat = DateFormat.getMediumDateFormat(holder.itemView.context)
        val formattedDate = dateFormat.format(Date(note.createdAt))
        holder.noteDate.text = formattedDate

        // Set click listener on card for opening note
        holder.noteCard.setOnClickListener {
            listener.onNoteClick(note)
        }

        // Set click listener for duplicate button
        holder.btnDuplicate.setOnClickListener {
            listener.onNoteDuplicate(note)
        }

        // Set click listener for delete button
        holder.btnDelete.setOnClickListener {
            listener.onNoteDelete(note, position)
        }

        // Keep long-press menu as alternative access
        holder.noteCard.setOnLongClickListener { view ->
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.inflate(R.menu.menu_note_item_actions)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_duplicate -> {
                        listener.onNoteDuplicate(note)
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
            true
        }
    }

    override fun getItemCount(): Int = notes.size

    fun updateNotes(newNotes: List<Note>) {
        this.notes = newNotes
        notifyDataSetChanged()
    }
}