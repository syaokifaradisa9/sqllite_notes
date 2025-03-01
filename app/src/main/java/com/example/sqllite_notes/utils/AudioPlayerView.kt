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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.sqllite_notes.R
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AudioPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var txtDuration: TextView
    private lateinit var txtTitle: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var audioUri: Uri? = null
    private var audioPath: String? = null
    private var isPrepared = false
    private var isPlaying = false

    private val TAG = "AudioPlayerView"

    init {
        LayoutInflater.from(context).inflate(R.layout.item_audio_player, this, true)
        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        seekBar = findViewById(R.id.seekBar)
        txtDuration = findViewById(R.id.txtDuration)
        txtTitle = findViewById(R.id.txtAudioTitle)

        // Initially hide pause button
        btnPause.visibility = View.GONE
    }

    private fun setupListeners() {
        btnPlay.setOnClickListener {
            if (isPrepared) {
                startPlayback()
            } else {
                prepareAndPlay()
            }
        }

        btnPause.setOnClickListener {
            pausePlayback()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer?.seekTo(progress)
                    updateDurationText(progress, mediaPlayer?.duration ?: 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun setAudioSource(uri: Uri?, title: String = "Audio Recording") {
        audioUri = uri
        audioPath = null
        txtTitle.text = title
        releaseMediaPlayer()
        isPrepared = false
    }

    fun setAudioSource(path: String, title: String = "Audio Recording") {
        // For existing audio saved in the database
        // It might be a wrapped audio path
        if (ImageUtils.isAudio(path)) {
            val (audioFilePath, audioTitle) = ImageUtils.unwrapAudio(path)
            audioPath = audioFilePath
            txtTitle.text = audioTitle
        } else {
            audioPath = path
            txtTitle.text = title
        }
        audioUri = null
        releaseMediaPlayer()
        isPrepared = false
    }

    private fun prepareAndPlay() {
        try {
            mediaPlayer = MediaPlayer()

            // Set the audio source
            when {
                audioUri != null -> {
                    Log.d(TAG, "Setting URI source: $audioUri")
                    mediaPlayer?.setDataSource(context, audioUri!!)
                }
                audioPath != null -> {
                    Log.d(TAG, "Setting file path source: $audioPath")
                    if (audioPath!!.startsWith("content://")) {
                        // Handle content URI stored as string
                        mediaPlayer?.setDataSource(context, Uri.parse(audioPath))
                    } else {
                        // Handle file path
                        val file = File(audioPath!!)
                        if (file.exists()) {
                            mediaPlayer?.setDataSource(file.absolutePath)
                        } else {
                            Log.e(TAG, "Audio file does not exist: $audioPath")
                            return
                        }
                    }
                }
                else -> {
                    Log.e(TAG, "No audio source specified")
                    return
                }
            }

            mediaPlayer?.setOnPreparedListener { mp ->
                isPrepared = true
                val duration = mp.duration
                seekBar.max = duration

                // Format duration
                updateDurationText(0, duration)

                startPlayback()
            }

            mediaPlayer?.setOnCompletionListener {
                stopPlayback()
            }

            mediaPlayer?.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                isPrepared = false
                btnPlay.visibility = View.VISIBLE
                btnPause.visibility = View.GONE
                handler.removeCallbacks(updateSeekBarRunnable)
                true // Error handled
            }

            mediaPlayer?.prepareAsync()
        } catch (e: IOException) {
            Log.e(TAG, "Error preparing MediaPlayer: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPlayer in illegal state: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
        }
    }

    private fun startPlayback() {
        try {
            mediaPlayer?.start()
            isPlaying = true
            btnPlay.visibility = View.GONE
            btnPause.visibility = View.VISIBLE

            // Start updating seekbar
            handler.postDelayed(updateSeekBarRunnable, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback: ${e.message}")
        }
    }

    private fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE

            // Stop updating seekbar
            handler.removeCallbacks(updateSeekBarRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback: ${e.message}")
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
            isPlaying = false
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
            seekBar.progress = 0
            updateDurationText(0, mediaPlayer?.duration ?: 0)

            // Stop updating seekbar
            handler.removeCallbacks(updateSeekBarRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
        }
    }

    private fun updateDurationText(currentPosition: Int, totalDuration: Int) {
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(currentPosition.toLong())
        val currentSeconds = TimeUnit.MILLISECONDS.toSeconds(currentPosition.toLong()) -
                TimeUnit.MINUTES.toSeconds(currentMinutes)

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration.toLong())
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration.toLong()) -
                TimeUnit.MINUTES.toSeconds(totalMinutes)

        txtDuration.text = String.format("%02d:%02d / %02d:%02d",
            currentMinutes, currentSeconds, totalMinutes, totalSeconds)
    }

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (mediaPlayer != null && isPlaying) {
                try {
                    val currentPosition = mediaPlayer!!.currentPosition
                    seekBar.progress = currentPosition

                    // Update duration text
                    updateDurationText(currentPosition, mediaPlayer!!.duration)

                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in seekbar update: ${e.message}")
                }
            }
        }
    }

    fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            // Ignore, might not be in started state
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        }

        handler.removeCallbacks(updateSeekBarRunnable)
        isPlaying = false
        isPrepared = false
        btnPlay.visibility = View.VISIBLE
        btnPause.visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseMediaPlayer()
    }
}