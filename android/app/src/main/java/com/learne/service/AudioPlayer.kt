package com.learne.service

import android.media.MediaPlayer
import java.io.IOException

class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String, onComplete: ((duration: Long) -> Unit)? = null) {
        release()
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setDataSource(url)
            mp.setOnCompletionListener {
                val dur = mp.duration.toLong()
                onComplete?.invoke(dur)
                // Do NOT release here — caller manages lifecycle via stop()/release()
            }
            mp.setOnErrorListener { _, what, extra ->
                onComplete?.invoke(0)
                true
            }
            mp.prepareAsync()
            mp.setOnPreparedListener {
                mp.start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            onComplete?.invoke(0)
            release()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        release()
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
}
