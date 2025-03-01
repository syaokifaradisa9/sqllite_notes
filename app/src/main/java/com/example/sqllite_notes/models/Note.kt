package com.example.sqllite_notes.models

data class Note(
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)