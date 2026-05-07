package com.learne.service

import android.media.MediaPlayer
import java.io.IOException

class AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String, onComplete: ((duration: Long) -> Unit)? = null) {
        release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(url)
                setOnCompletionListener {
                    val dur = duration.toLong()
                    onComplete?.invoke(dur)
                    release()
                }
                setOnErrorListener { _, what, extra ->
                    onComplete?.invoke(0)
                    release()
                    true
                }
                prepareAsync()
                setOnPreparedListener {
                    start()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                onComplete?.invoke(0)
            }
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