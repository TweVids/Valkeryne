package com.twevids.valkeryne.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twevids.valkeryne.audio.AudioStreamPlayer
import com.twevids.valkeryne.data.SettingsManager
import com.twevids.valkeryne.model.AppSettings
import com.twevids.valkeryne.model.ChatMessage
import com.twevids.valkeryne.model.MessageSender
import com.twevids.valkeryne.network.GeminiLiveClient
import com.twevids.valkeryne.network.GeminiLiveListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application), GeminiLiveListener {
    private val settingsManager = SettingsManager(application)
    val audioPlayer = AudioStreamPlayer()

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

    private var geminiClient: GeminiLiveClient

    init {
        geminiClient = GeminiLiveClient(_settings.value, this)
        if (_settings.value.apiKey.isEmpty()) {
            _isSettingsOpen.value = true
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

    fun sendMessage(text: String, imageBitmap: android.graphics.Bitmap? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && imageBitmap == null) return

        if (_settings.value.apiKey.isEmpty()) {
            openSettings()
            return
        }

        audioPlayer.stop()

        var imageBytes: ByteArray? = null
        if (imageBitmap != null) {
            val stream = java.io.ByteArrayOutputStream()
            imageBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            imageBytes = stream.toByteArray()
        }

        val userMessage = ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            sender = MessageSender.USER,
            text = trimmed,
            imageBitmap = imageBitmap
        )

        val aiMessageId = "ai_${System.currentTimeMillis()}"

        _messages.value = _messages.value + userMessage
        geminiClient.sendMessage(trimmed, aiMessageId, imageBytes)
    }

    fun toggleAudioPlayback(message: ChatMessage) {
        val currentlyPlayingId = audioPlayer.currentPlayingMessageId.value
        if (currentlyPlayingId == message.id && audioPlayer.isPlaying.value) {
            audioPlayer.stop()
        } else {
            audioPlayer.playFullAudio(message.audioChunks, message.id)
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
        // Immediately stream chunk 1..N starting with chunk 1
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
        }
    }

    override fun onCleared() {
        super.onCleared()
        geminiClient.disconnect()
        audioPlayer.release()
    }
}
