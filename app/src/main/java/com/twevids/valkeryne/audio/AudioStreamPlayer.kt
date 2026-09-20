package com.twevids.valkeryne.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue

class AudioStreamPlayer {
    private val sampleRate = 24000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var audioTrack: AudioTrack? = null

    private val chunkQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingMessageId = MutableStateFlow<String?>(null)
    val currentPlayingMessageId: StateFlow<String?> = _currentPlayingMessageId.asStateFlow()

    @Synchronized
    private fun ensureTrack(): AudioTrack {
        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().also {
                    it.setVolume(1.0f)
                }
        }
        return audioTrack!!
    }

    /**
     * Enqueues a raw 16-bit PCM 24kHz chunk. Starts streaming immediately upon chunk 1,
     * and continues sequentially from 1 to N.
     */
    fun enqueueChunk(chunk: ByteArray, messageId: String) {
        chunkQueue.offer(chunk)
        _currentPlayingMessageId.value = messageId

        if (playbackJob == null || playbackJob?.isActive != true) {
            startPlaybackLoop()
        }
    }

    private fun startPlaybackLoop() {
        playbackJob = scope.launch {
            try {
                val track = ensureTrack()
                track.play()
                _isPlaying.value = true

                while (isActive) {
                    // Wait up to 1.5s for the next streaming chunk to arrive over the network
                    val chunk = chunkQueue.poll(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        track.write(chunk, 0, chunk.size)
                    } else {
                        // Stream idle or finished
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isPlaying.value = false
                _currentPlayingMessageId.value = null
            }
        }
    }

    /**
     * Plays the full accumulated audio for an AI response message (all chunks 1..N).
     */
    fun playFullAudio(chunks: List<ByteArray>, messageId: String) {
        stop()

        if (chunks.isEmpty()) return

        _currentPlayingMessageId.value = messageId
        for (chunk in chunks) {
            chunkQueue.offer(chunk)
        }
        startPlaybackLoop()
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        chunkQueue.clear()

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _isPlaying.value = false
        _currentPlayingMessageId.value = null
    }

    fun release() {
        stop()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }
}
