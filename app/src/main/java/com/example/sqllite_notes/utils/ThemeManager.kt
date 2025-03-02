package com.example.sqllite_notes.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(private val context: Context) {
    companion object {
        // Theme modes
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2

        // Preference key constants
        private const val PREF_NAME = "theme_preferences"
        private const val KEY_THEME_MODE = "theme_mode"

        // Theme names for display
        val THEME_NAMES = mapOf(
            MODE_SYSTEM to "System Default",
            MODE_LIGHT to "Light",
            MODE_DARK to "Dark"
        )
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getThemeMode(): Int {
        return preferences.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    fun setThemeMode(mode: Int) {
        // Save the theme preference
        preferences.edit().putInt(KEY_THEME_MODE, mode).apply()

        // Apply the theme immediately
        applyTheme(mode)
    }

    fun applyTheme(mode: Int = getThemeMode()) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                }
            }
        }
    }

    fun getCurrentThemeName(): String {
        return THEME_NAMES[getThemeMode()] ?: "System Default"
    }
}