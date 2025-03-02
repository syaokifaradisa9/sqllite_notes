package com.example.sqllite_notes

import android.app.Application
import com.example.sqllite_notes.utils.ThemeManager

/**
 * NotesApplication adalah Class aplikasi utama yang mewarisi Android Application.
 *
 * Class Application dalam Android adalah komponen fundamental yang:
 * 1. Diinisialisasi sebelum komponen lain dalam aplikasi (Activity, Service, dll)
 * 2. Mempertahankan state global aplikasi sepanjang siklus hidup aplikasi
 * 3. Menyediakan titik masuk untuk inisialisasi komponen dan library yang perlu
 *    disiapkan saat aplikasi pertama kali diluncurkan
 *
 * NotesApplication dideklarasikan dalam AndroidManifest.xml dengan atribut
 * android:name=".NotesApplication" pada elemen <application>, sehingga sistem
 * Android tahu untuk membuat instance dari Class
 ini saat aplikasi dimulai.
 *
 * Dalam aplikasi Notes, Class ini digunakan terutama untuk inisialisasi tema
 * aplikasi pada saat startup, memastikan preferensi tema pengguna diterapkan
 * segera setelah aplikasi diluncurkan.
 */
class NotesApplication : Application() {
    /**
     * Metode onCreate() dipanggil ketika aplikasi pertama kali diluncurkan
     * dan sebelum Activity atau komponen lain dibuat. Ini adalah tempat
     * ideal untuk melakukan inisialisasi yang diperlukan sebelum pengguna
     * mulai berinteraksi dengan aplikasi.
     *
     * Dalam konteks aplikasi Notes, metode ini menginisialisasi dan menerapkan
     * pengaturan tema, memastikan bahwa UI aplikasi secara konsisten mengikuti
     * preferensi tema pengguna (terang, gelap, atau mengikuti sistem) bahkan
     * setelah aplikasi direstart.
     */
    override fun onCreate() {
        super.onCreate()  // Selalu panggil implementasi induk terlebih dahulu

        // Inisialisasi dan terapkan pengaturan tema saat aplikasi dimulai
        val themeManager = ThemeManager(this)
        themeManager.applyTheme()
    }
}