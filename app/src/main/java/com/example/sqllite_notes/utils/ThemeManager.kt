package com.example.sqllite_notes.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/**
 * ThemeManager adalah Class yang mengelola tema aplikasi, memungkinkan pengguna
 * untuk memilih antara tema terang (light), gelap (dark), atau mengikuti
 * pengaturan sistem.
 *
 * Class ini bertanggung jawab untuk:
 * 1. Menyimpan preferensi tema pengguna secara persisten menggunakan SharedPreferences
 * 2. Menerapkan tema yang sesuai ke aplikasi
 * 3. Menyediakan antarmuka yang konsisten untuk akses dan perubahan tema
 * 4. Menangani kompatibilitas tema di berbagai versi Android
 *
 * ThemeManager digunakan di seluruh aplikasi untuk memastikan konsistensi tema
 * bahkan setelah aplikasi direstart.
 */
class ThemeManager(private val context: Context) {
    /**
     * Companion object berisi konstanta dan properti statis yang terkait dengan
     * pengelolaan tema.
     *
     * Object ini menyimpan:
     * 1. Kode mode tema (SYSTEM, LIGHT, DARK)
     * 2. Konstanta untuk nama SharedPreferences dan kunci
     * 3. Pemetaan mode tema ke nama untuk tampilan UI
     */
    companion object {
        // Kode mode tema
        const val MODE_SYSTEM = 0  // Mengikuti pengaturan sistem
        const val MODE_LIGHT = 1   // tema terang
        const val MODE_DARK = 2    // tema gelap

        // Konstanta untuk SharedPreferences
        private const val PREF_NAME = "theme_preferences"  // Nama file SharedPreferences
        private const val KEY_THEME_MODE = "theme_mode"    // Kunci untuk menyimpan mode tema

        // Pemetaan kode tema ke nama untuk ditampilkan di UI
        val THEME_NAMES = mapOf(
            MODE_SYSTEM to "System Default",
            MODE_LIGHT to "Light",
            MODE_DARK to "Dark"
        )
    }

    /**
     * SharedPreferences digunakan untuk menyimpan preferensi tema pengguna
     * secara persisten. Ini memastikan pengaturan tema dipertahankan
     * bahkan setelah aplikasi ditutup atau perangkat direstart.
     *
     * Diinisialisasi dengan lazy untuk memastikan pembuatan yang efisien
     * saat dibutuhkan (tidak memuat SharedPreferences sampai benar-benar digunakan).
     */
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Mendapatkan mode tema yang saat ini dipilih.
     *
     * Jika tidak ada mode yang tersimpan, mengembalikan MODE_SYSTEM sebagai default.
     *
     * @return Kode mode tema (MODE_SYSTEM, MODE_LIGHT, atau MODE_DARK)
     */
    fun getThemeMode(): Int {
        return preferences.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    /**
     * Mengatur mode tema baru dan menerapkannya segera.
     *
     * Metode ini:
     * 1. Menyimpan preferensi tema yang dipilih ke SharedPreferences
     * 2. Menerapkan tema baru secara langsung
     *
     * @param mode Kode mode tema yang baru (MODE_SYSTEM, MODE_LIGHT, atau MODE_DARK)
     */
    fun setThemeMode(mode: Int) {
        // Simpan preferensi tema
        preferences.edit().putInt(KEY_THEME_MODE, mode).apply()

        // Terapkan tema segera
        applyTheme(mode)
    }

    /**
     * Menerapkan tema berdasarkan mode yang dipilih atau tersimpan.
     *
     * Metode ini menggunakan AppCompatDelegate untuk mengatur mode malam (night mode)
     * yang mengontrol apakah aplikasi menggunakan tema terang atau gelap.
     *
     * @param mode Kode mode tema yang akan diterapkan. Jika tidak ditentukan,
     *             akan menggunakan mode yang tersimpan di SharedPreferences.
     */
    fun applyTheme(mode: Int = getThemeMode()) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> {
                // Untuk MODE_SYSTEM, perilaku berbeda tergantung versi Android
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10 (Q) dan di atasnya mendukung mode tema sistem
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    // Untuk versi sebelum Android 10, gunakan mode berbasis baterai
                    // yang beralih ke tema gelap saat mode hemat baterai aktif
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                }
            }
        }
    }

    /**
     * Mendapatkan nama tema yang saat ini aktif dalam bentuk yang mudah dibaca.
     *
     * Berguna untuk menampilkan nama tema yang dipilih di UI.
     *
     * @return Nama tema yang mudah dibaca ("System Default", "Light", atau "Dark")
     */
    fun getCurrentThemeName(): String {
        return THEME_NAMES[getThemeMode()] ?: "System Default"
    }
}