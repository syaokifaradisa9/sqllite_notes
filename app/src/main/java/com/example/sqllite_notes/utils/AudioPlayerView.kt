package com.example.sqllite_notes.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.sqllite_notes.R
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AudioPlayerView adalah custom view yang menangani pemutaran audio dalam aplikasi Notes.
 *
 * Class
 ini mewarisi LinearLayout dan menyediakan antarmuka pemutaran audio lengkap dengan:
 * - Tombol play/pause untuk mengontrol pemutaran
 * - SeekBar untuk navigasi posisi audio
 * - Indikator durasi yang menampilkan waktu saat ini dan total durasi
 * - Tampilan judul audio
 *
 * AudioPlayerView dapat memutar audio dari URI (untuk file yang baru dipilih)
 * atau dari path file (untuk audio yang sudah disimpan sebelumnya).
 *
 * Class ini juga menangani siklus hidup MediaPlayer dengan benar untuk mencegah
 * kebocoran memori dan memastikan pemutaran audio berhenti saat view tidak lagi visible.
 */
class AudioPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // MediaPlayer untuk memutar audio
    private var mediaPlayer: MediaPlayer? = null

    // Komponen UI
    private lateinit var btnPlay: MaterialButton     // Tombol play
    private lateinit var btnPause: MaterialButton    // Tombol pause
    private lateinit var seekBar: SeekBar            // SeekBar untuk navigasi posisi audio
    private lateinit var txtDuration: TextView       // TextView untuk menampilkan durasi
    private lateinit var txtTitle: TextView          // TextView untuk menampilkan judul audio

    // Handler untuk memperbarui seekbar secara berkala
    private val handler = Handler(Looper.getMainLooper())

    // Variabel untuk menyimpan sumber audio
    private var audioUri: Uri? = null                // URI audio untuk file yang baru dipilih
    private var audioPath: String? = null           // Path file untuk audio yang sudah disimpan

    // Status MediaPlayer
    private var isPrepared = false                  // Apakah MediaPlayer sudah siap
    private var isPlaying = false                   // Apakah audio sedang diputar

    // TAG untuk logging
    private val TAG = "AudioPlayerView"

    /**
     * Inisialisasi view saat dibuat.
     * Inflate layout dan inisialisasi komponen UI.
     */
    init {
        // Inflate layout dari XML (item_audio_player.xml)
        LayoutInflater.from(context).inflate(R.layout.item_audio_player, this, true)

        // Inisialisasi komponen UI dan setup listener
        initializeViews()
        setupListeners()
    }

    /**
     * Inisialisasi referensi ke komponen UI dari layout yang di-inflate.
     */
    private fun initializeViews() {
        // Dapatkan referensi ke komponen UI dari layout
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        seekBar = findViewById(R.id.seekBar)
        txtDuration = findViewById(R.id.txtDuration)
        txtTitle = findViewById(R.id.txtAudioTitle)

        // Sembunyikan tombol pause pada awalnya
        btnPause.visibility = View.GONE
    }

    /**
     * Menyiapkan listener untuk interaksi pengguna dengan komponen UI.
     */
    private fun setupListeners() {
        // Listener untuk tombol play
        btnPlay.setOnClickListener {
            if (isPrepared) {
                // Jika MediaPlayer sudah siap, langsung mulai pemutaran
                startPlayback()
            } else {
                // Jika belum siap, siapkan dulu baru mulai pemutaran
                prepareAndPlay()
            }
        }

        // Listener untuk tombol pause
        btnPause.setOnClickListener {
            pausePlayback()
        }

        // Listener untuk interaksi dengan seekbar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            // Dipanggil saat progress seekbar berubah
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null) {
                    // Jika perubahan dilakukan oleh pengguna, sesuaikan posisi pemutaran
                    mediaPlayer?.seekTo(progress)
                    // Perbarui tampilan durasi
                    updateDurationText(progress, mediaPlayer?.duration ?: 0)
                }
            }

            // Dipanggil saat pengguna mulai menggeser seekbar
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            // Dipanggil saat pengguna selesai menggeser seekbar
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Mengatur sumber audio dari URI (untuk file audio yang baru dipilih).
     *
     * @param uri URI file audio
     * @param title Judul/nama yang akan ditampilkan untuk audio
     */
    fun setAudioSource(uri: Uri?, title: String = "Audio Recording") {
        // Simpan URI audio dan judul
        audioUri = uri
        audioPath = null
        txtTitle.text = title

        // Lepaskan MediaPlayer yang lama jika ada
        releaseMediaPlayer()

        // Reset status siap
        isPrepared = false
    }

    /**
     * Mengatur sumber audio dari path file (untuk audio yang sudah disimpan).
     *
     * @param path Path ke file audio
     * @param title Judul/nama yang akan ditampilkan untuk audio
     */
    fun setAudioSource(path: String, title: String = "Audio Recording") {
        // Periksa apakah path adalah audio yang dibungkus (wrapped)
        if (MultimediaUtils.isAudio(path)) {
            // Jika ya, ekstrak path file dan judul asli
            val (audioFilePath, audioTitle) = MultimediaUtils.unwrapAudio(path)
            audioPath = audioFilePath
            txtTitle.text = audioTitle
        } else {
            // Jika tidak, gunakan path dan judul yang diberikan
            audioPath = path
            txtTitle.text = title
        }

        // Reset URI audio karena menggunakan path file
        audioUri = null

        // Lepaskan MediaPlayer yang lama jika ada
        releaseMediaPlayer()

        // Reset status siap
        isPrepared = false
    }

    /**
     * Menyiapkan MediaPlayer dan mulai pemutaran audio.
     * Metode ini menangani penyiapan MediaPlayer dari berbagai sumber
     * (URI atau path file) dan menangani error yang mungkin terjadi.
     */
    private fun prepareAndPlay() {
        try {
            // Buat instance MediaPlayer baru
            mediaPlayer = MediaPlayer()

            // Atur sumber audio berdasarkan jenis yang tersedia
            when {
                audioUri != null -> {
                    // Gunakan URI jika tersedia
                    Log.d(TAG, "Mengatur sumber URI: $audioUri")
                    mediaPlayer?.setDataSource(context, audioUri!!)
                }
                audioPath != null -> {
                    // Gunakan path file jika tersedia
                    Log.d(TAG, "Mengatur sumber dari path file: $audioPath")
                    if (audioPath!!.startsWith("content://")) {
                        // Jika path adalah URI konten, parse dan gunakan sebagai URI
                        mediaPlayer?.setDataSource(context, Uri.parse(audioPath))
                    } else {
                        // Jika path adalah file lokal, verifikasi dan gunakan path absolut
                        val file = File(audioPath!!)
                        if (file.exists()) {
                            mediaPlayer?.setDataSource(file.absolutePath)
                        } else {
                            Log.e(TAG, "File audio tidak ditemukan: $audioPath")
                            return
                        }
                    }
                }
                else -> {
                    // Tidak ada sumber yang valid
                    Log.e(TAG, "Tidak ada sumber audio yang ditentukan")
                    return
                }
            }

            // Listener saat MediaPlayer siap untuk digunakan
            mediaPlayer?.setOnPreparedListener { mp ->
                isPrepared = true

                // Dapatkan total durasi audio
                val duration = mp.duration

                // Atur nilai maksimum seekbar sesuai durasi
                seekBar.max = duration

                // Perbarui tampilan durasi awal (0/total)
                updateDurationText(0, duration)

                // Mulai pemutaran
                startPlayback()
            }

            // Listener saat pemutaran selesai
            mediaPlayer?.setOnCompletionListener {
                stopPlayback()
            }

            // Listener untuk menangani error
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Error MediaPlayer: what=$what, extra=$extra")
                isPrepared = false
                btnPlay.visibility = View.VISIBLE
                btnPause.visibility = View.GONE
                handler.removeCallbacks(updateSeekBarRunnable)
                true // Error telah ditangani
            }

            // Siapkan MediaPlayer secara asinkron
            mediaPlayer?.prepareAsync()
        } catch (e: IOException) {
            Log.e(TAG, "Error menyiapkan MediaPlayer: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPlayer dalam status illegal: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error tidak terduga: ${e.message}")
        }
    }

    /**
     * Memulai pemutaran audio.
     * Menampilkan tombol pause dan menyembunyikan tombol play.
     * Memulai pembaruan seekbar secara berkala.
     */
    private fun startPlayback() {
        try {
            mediaPlayer?.start()
            isPlaying = true

            // Update tampilan UI
            btnPlay.visibility = View.GONE
            btnPause.visibility = View.VISIBLE

            // Mulai memperbarui seekbar secara berkala
            handler.postDelayed(updateSeekBarRunnable, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback: ${e.message}")
        }
    }

    /**
     * Menjeda pemutaran audio.
     * Menampilkan tombol play dan menyembunyikan tombol pause.
     * Menghentikan pembaruan seekbar.
     */
    private fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            isPlaying = false

            // Update tampilan UI
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE

            // Hentikan pembaruan seekbar
            handler.removeCallbacks(updateSeekBarRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback: ${e.message}")
        }
    }

    /**
     * Menghentikan pemutaran audio dan mengatur ulang posisi ke awal.
     * Digunakan saat pemutaran selesai atau pengguna ingin mulai dari awal.
     */
    private fun stopPlayback() {
        try {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
            isPlaying = false

            // Update tampilan UI
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
            seekBar.progress = 0
            updateDurationText(0, mediaPlayer?.duration ?: 0)

            // Hentikan pembaruan seekbar
            handler.removeCallbacks(updateSeekBarRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
        }
    }

    /**
     * Memperbarui tampilan teks durasi dengan format MM:SS.
     *
     * @param currentPosition Posisi saat ini dalam milidetik
     * @param totalDuration Total durasi dalam milidetik
     */
    private fun updateDurationText(currentPosition: Int, totalDuration: Int) {
        // Konversi milidetik ke menit dan detik untuk posisi saat ini
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(currentPosition.toLong())
        val currentSeconds = TimeUnit.MILLISECONDS.toSeconds(currentPosition.toLong()) -
                TimeUnit.MINUTES.toSeconds(currentMinutes)

        // Konversi milidetik ke menit dan detik untuk total durasi
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration.toLong())
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration.toLong()) -
                TimeUnit.MINUTES.toSeconds(totalMinutes)

        // Format dan tampilkan durasi (MM:SS / MM:SS)
        txtDuration.text = String.format("%02d:%02d / %02d:%02d",
            currentMinutes, currentSeconds, totalMinutes, totalSeconds)
    }

    /**
     * Runnable untuk memperbarui seekbar secara berkala saat audio diputar.
     * Dijalankan setiap 100ms untuk memberikan pembaruan yang mulus.
     */
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (mediaPlayer != null && isPlaying) {
                try {
                    // Dapatkan posisi saat ini dari MediaPlayer
                    val currentPosition = mediaPlayer!!.currentPosition

                    // Perbarui progress seekbar
                    seekBar.progress = currentPosition

                    // Perbarui tampilan durasi
                    updateDurationText(currentPosition, mediaPlayer!!.duration)

                    // Jadwalkan pembaruan berikutnya setelah 100ms
                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in seekbar update: ${e.message}")
                }
            }
        }
    }

    /**
     * Melepaskan sumber daya MediaPlayer dan membersihkan handler.
     * Penting untuk mencegah kebocoran memori dan memastikan
     * sumber daya dilepaskan dengan benar.
     */
    fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            // Abaikan, mungkin tidak dalam status started
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        }

        // Bersihkan handler
        handler.removeCallbacks(updateSeekBarRunnable)

        // Reset status
        isPlaying = false
        isPrepared = false

        // Reset tampilan UI
        btnPlay.visibility = View.VISIBLE
        btnPause.visibility = View.GONE
    }

    /**
     * Dipanggil saat view dilepaskan dari window.
     * Memastikan MediaPlayer dilepaskan dengan benar saat view tidak lagi visible.
     * Ini penting untuk mencegah kebocoran memori dan penghentian
     * audio saat menavigasi keluar dari layar.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseMediaPlayer()
    }
}