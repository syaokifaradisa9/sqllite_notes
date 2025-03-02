package com.example.sqllite_notes

import android.app.Application
import com.example.sqllite_notes.utils.ThemeManager

class NotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize and apply theme settings when the app starts
        val themeManager = ThemeManager(this)
        themeManager.applyTheme()
    }
}