package com.twevids.valkeryne.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twevids.valkeryne.audio.AudioStreamPlayer
import com.twevids.valkeryne.audio.AudioStreamRecorder
import com.twevids.valkeryne.data.SettingsManager
import com.twevids.valkeryne.model.AppSettings
import com.twevids.valkeryne.model.ChatMessage
import com.twevids.valkeryne.model.MessageSender
import com.twevids.valkeryne.network.GeminiLiveClient
import com.twevids.valkeryne.network.GeminiLiveListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatViewModel(application: Application) : AndroidViewModel(application), GeminiLiveListener {
    private val settingsManager = SettingsManager(application)
    val audioPlayer = AudioStreamPlayer()
    private var audioRecorder: AudioStreamRecorder? = null

    private val _settings = MutableStateFlow(settingsManager.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    // Fullscreen Camera & Live Voice State
    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isHoldingToSpeak = MutableStateFlow(false)
    val isHoldingToSpeak: StateFlow<Boolean> = _isHoldingToSpeak.asStateFlow()

    private var geminiClient: GeminiLiveClient

    init {
        geminiClient = GeminiLiveClient(_settings.value, this)
        audioRecorder = AudioStreamRecorder { pcmBytes ->
            geminiClient.sendRealtimeAudio(pcmBytes)
        }
        if (_settings.value.apiKey.isEmpty()) {
            _isSettingsOpen.value = true
        } else {
            geminiClient.connect()
        }
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun saveSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        settingsManager.saveSettings(newSettings)
        geminiClient.updateSettings(newSettings)
        closeSettings()
    }

    fun toggleCamera() {
        _isCameraEnabled.value = !_isCameraEnabled.value
    }

    fun flipCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun onHoldToSpeechStart(currentFrame: Bitmap?) {
        if (_settings.value.apiKey.isEmpty()) {
            openSettings()
            return
        }

        audioPlayer.stop()
        _isHoldingToSpeak.value = true

        val aiMessageId = "ai_${System.currentTimeMillis()}"

        // 1. Begin AI turn and start streaming voice chunks from mic immediately
        geminiClient.startVoiceTurn(aiMessageId)
        audioRecorder?.start()

        // 2. If camera is active, compress & send current frame concurrently on background thread
        if (_isCameraEnabled.value && currentFrame != null) {
            viewModelScope.launch(Dispatchers.Default) {
                val imageBytes = compressBitmap(currentFrame)
                if (imageBytes != null) {
                    geminiClient.sendRealtimeImage(imageBytes)
                }
            }
        }
    }

    fun onHoldToSpeechEnd() {
        if (!_isHoldingToSpeak.value) return
        _isHoldingToSpeak.value = false
        audioRecorder?.stop()
        geminiClient.finishVoiceTurn()
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray? {
        return try {
            val softwareBmp = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val maxDim = 1024
            val scaledBmp = if (softwareBmp.width > maxDim || softwareBmp.height > maxDim) {
                val ratio = softwareBmp.width.toFloat() / softwareBmp.height.toFloat()
                val (w, h) = if (ratio > 1f) {
                    Pair(maxDim, (maxDim / ratio).toInt())
                } else {
                    Pair((maxDim * ratio).toInt(), maxDim)
                }
                Bitmap.createScaledBitmap(softwareBmp, w, h, true)
            } else {
                softwareBmp
            }
            val stream = java.io.ByteArrayOutputStream()
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // GeminiLiveListener callbacks
    override fun onConnectionStatusChanged(isConnected: Boolean, isConnecting: Boolean) {
        _isConnected.value = isConnected
        _isConnecting.value = isConnecting
    }

    override fun onAiMessageStart(messageId: String) {
        val aiMessage = ChatMessage(
            id = messageId,
            sender = MessageSender.AI,
            isStreaming = true
        )
        _messages.value = _messages.value + aiMessage
    }

    override fun onTextUpdate(messageId: String, text: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(text = text) else msg
        }
    }

    override fun onReasoningUpdate(messageId: String, reasoning: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(reasoning = reasoning) else msg
        }
    }

    override fun onAudioChunkReceived(messageId: String, pcmBytes: ByteArray) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) {
                msg.copy(audioChunks = msg.audioChunks + pcmBytes)
            } else msg
        }
        audioPlayer.enqueueChunk(pcmBytes, messageId)
    }

    override fun onTurnComplete(messageId: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(isStreaming = false) else msg
        }
    }

    override fun onError(messageId: String, errorMessage: String) {
        if (messageId.isNotEmpty()) {
            _messages.value = _messages.value.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(error = errorMessage, isStreaming = false)
                } else msg
            }
        } else {
            val last = _messages.value.lastOrNull()
            if (last != null && last.sender == MessageSender.AI) {
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == last.id) msg.copy(error = errorMessage, isStreaming = false) else msg
                }
            } else {
                _messages.value = _messages.value + ChatMessage(
                    id = "err_${System.currentTimeMillis()}",
                    sender = MessageSender.AI,
                    error = errorMessage,
                    isStreaming = false
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder?.stop()
        geminiClient.disconnect()
        audioPlayer.release()
    }
}
