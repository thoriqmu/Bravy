package com.pkmk.bravy.util

import android.media.MediaPlayer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object VoiceNotePlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingUrl: String? = null

    private val _playerState = MutableLiveData<PlayerState>()
    val playerState: LiveData<PlayerState> = _playerState

    fun play(url: String) {
        if (mediaPlayer?.isPlaying == true) {
            if (url == currentPlayingUrl) {
                // Jeda jika URL sama
                mediaPlayer?.pause()
                _playerState.value = PlayerState.Paused(url)
                return
            } else {
                // Hentikan yang lama sebelum memulai yang baru
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
        }

        currentPlayingUrl = url
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener {
                start()
                _playerState.value = PlayerState.Playing(url)
            }
            setOnCompletionListener {
                releasePlayer()
            }
            setOnErrorListener { _, _, _ ->
                releasePlayer()
                true
            }
        }
    }

    fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        _playerState.value = PlayerState.Stopped(currentPlayingUrl ?: "")
        currentPlayingUrl = null
    }
}

sealed class PlayerState {
    data class Playing(val url: String) : PlayerState()
    data class Paused(val url: String) : PlayerState()
    data class Stopped(val url: String) : PlayerState()
}