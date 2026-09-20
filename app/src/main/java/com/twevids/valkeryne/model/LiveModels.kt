package com.twevids.valkeryne.model

data class LiveModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val supportsReasoning: Boolean = false
)

object LiveModels {
    val ALL = listOf(
        LiveModelInfo(
            id = "gemini-3.8-live",
            name = "Gemini 3.8 Live",
            description = "Default ultra-low latency voice agent and dialogue without reasoning delay."
        ),
        LiveModelInfo(
            id = "gemini-3.8-live-extended-thinking",
            name = "Gemini 3.8 Live Extended Thinking",
            description = "High-reasoning voice model with background thought turns.",
            supportsReasoning = true
        ),
        LiveModelInfo(
            id = "gemini-3.1-flash-live-preview",
            name = "Gemini 3.1 Flash Live Preview",
            description = "Low-latency live preview for real-time conversational dialogue."
        ),
        LiveModelInfo(
            id = "gemini-2.5-flash-native-audio-preview-12-2025",
            name = "Gemini 2.5 Flash Native Audio Preview",
            description = "Native audio model with continuous voice streaming."
        )
    )

    val VOICES = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")

    const val DEFAULT_SYSTEM_INSTRUCTION =
        "You are a helpful, conversational, and friendly voice AI assistant. Keep responses natural and concise."
}

enum class MessageSender {
    USER, AI
}

data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String = "",
    val reasoning: String = "",
    val audioChunks: List<ByteArray> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppSettings(
    val apiKey: String = "",
    val modelId: String = "gemini-3.8-live",
    val voiceName: String = "Puck",
    val systemInstruction: String = LiveModels.DEFAULT_SYSTEM_INSTRUCTION
)
