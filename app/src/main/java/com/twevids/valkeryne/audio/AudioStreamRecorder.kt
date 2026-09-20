package com.twevids.valkeryne.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*

class AudioStreamRecorder(
    private val onAudioChunk: (ByteArray) -> Unit
) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSize = maxOf(minBufferSize, 3200) // ~100ms of audio per chunk

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize * 2
                )
            }
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording) {
                    try {
                        val record = audioRecord ?: break
                        if (!isRecording || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) break
                        val readBytes = record.read(buffer, 0, buffer.size)
                        if (readBytes > 0 && isRecording) {
                            onAudioChunk(buffer.copyOf(readBytes))
                        }
                    } catch (t: Throwable) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            val record = audioRecord
            audioRecord = null
            if (record?.state == AudioRecord.STATE_INITIALIZED) {
                record.stop()
                record.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
