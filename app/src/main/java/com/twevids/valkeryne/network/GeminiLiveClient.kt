package com.twevids.valkeryne.network

import android.util.Base64
import com.twevids.valkeryne.model.AppSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface GeminiLiveListener {
    fun onConnectionStatusChanged(isConnected: Boolean, isConnecting: Boolean)
    fun onAiMessageStart(messageId: String)
    fun onTextUpdate(messageId: String, text: String)
    fun onReasoningUpdate(messageId: String, reasoning: String)
    fun onAudioChunkReceived(messageId: String, pcmBytes: ByteArray)
    fun onTurnComplete(messageId: String)
    fun onError(messageId: String, errorMessage: String)
}

class GeminiLiveClient(
    private var settings: AppSettings,
    private val listener: GeminiLiveListener
) {
    // Disabled pingInterval to prevent "SocketTimeoutException: sent ping but didn't receive pong"
    // Gemini Live API manages its own session lifecycle and does not require client-initiated ping frames.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var currentMessageId: String? = null
    private var accumulatedText = StringBuilder()
    private var accumulatedReasoning = StringBuilder()
    private var isConnected = false
    private var isConnecting = false
    private var isSetupComplete = false

    private data class QueuedPrompt(
        val prompt: String,
        val messageId: String,
        val imageBytes: ByteArray? = null
    )

    // Queue for messages sent while connection is establishing
    private var pendingPrompt: QueuedPrompt? = null

    fun updateSettings(newSettings: AppSettings) {
        val needsReconnect = settings.apiKey != newSettings.apiKey || settings.modelId != newSettings.modelId
        settings = newSettings
        if (needsReconnect && isConnected) {
            disconnect()
        }
    }

    fun connect() {
        if (isConnected || isConnecting) return

        val key = settings.apiKey.trim()
        if (key.isEmpty()) {
            listener.onError("", "API Key is required. Please configure it in Settings.")
            return
        }

        isConnecting = true
        isSetupComplete = false
        listener.onConnectionStatusChanged(isConnected = false, isConnecting = true)

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                isConnecting = false
                listener.onConnectionStatusChanged(isConnected = true, isConnecting = false)

                // 1. Send Setup payload as the FIRST message over the WebSocket
                sendSetup(webSocket)
                // Note: We MUST wait for server's "setupComplete" before dispatching any user turns
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                isConnecting = false
                isSetupComplete = false
                listener.onConnectionStatusChanged(isConnected = false, isConnecting = false)

                val errorDesc = StringBuilder(t.localizedMessage ?: "Network connection failure")
                response?.let { resp ->
                    try {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            errorDesc.append(" (HTTP ").append(resp.code).append(": ").append(body).append(")")
                        }
                    } catch (_: Exception) {}
                }
                val targetId = currentMessageId ?: pendingPrompt?.messageId ?: ""
                listener.onError(targetId, "WebSocket Error: $errorDesc")
                pendingPrompt = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                isConnecting = false
                isSetupComplete = false
                listener.onConnectionStatusChanged(isConnected = false, isConnecting = false)

                if (code != 1000) {
                    val targetId = currentMessageId ?: ""
                    listener.onError(targetId, "Connection closed ($code): $reason")
                }
            }
        })
    }

    private fun sendSetup(ws: WebSocket) {
        try {
            val isExtendedThinking = settings.modelId == "gemini-3.8-live-extended-thinking"
            val isGemini25 = settings.modelId == "gemini-2.5-flash-native-audio-preview-12-2025"

            val generationConfig = JSONObject().apply {
                put("responseModalities", JSONArray().apply { put("AUDIO") })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", settings.voiceName)
                        })
                    })
                })
                // Thinking configuration: REQUIRED for extended-thinking, MUST be omitted for gemini-3.8-live
                if (isExtendedThinking) {
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", "low")
                        put("includeThoughts", true)
                    })
                }
                // Token limit specific to Gemini 2.5 Flash Native Audio Preview
                if (isGemini25) {
                    put("maxOutputTokens", 8192)
                }
            }

            val setupObj = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/${settings.modelId}")
                    put("generationConfig", generationConfig)
                    if (settings.systemInstruction.isNotBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", settings.systemInstruction)
                                })
                            })
                        })
                    }
                    put("outputAudioTranscription", JSONObject())
                    put("inputAudioTranscription", JSONObject())
                })
            }
            ws.send(setupObj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMessage(prompt: String, messageId: String, imageBytes: ByteArray? = null) {
        currentMessageId = messageId
        accumulatedText.clear()
        accumulatedReasoning.clear()

        listener.onAiMessageStart(messageId)

        if (!isConnected || !isSetupComplete || webSocket == null) {
            // Queue message and ensure connection is establishing
            pendingPrompt = QueuedPrompt(prompt, messageId, imageBytes)
            if (!isConnected && !isConnecting) {
                connect()
            }
        } else {
            // Connection is active and setup handshake confirmed, dispatch turn
            sendRealtimeMediaAndTurn(prompt, messageId, imageBytes)
        }
    }

    private fun sendRealtimeMediaAndTurn(prompt: String, messageId: String, imageBytes: ByteArray?) {
        scope.launch {
            try {
                // 1. If an image is provided, stream it as realtimeInput mediaChunks
                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    val base64Img = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                    val imgObj = JSONObject().apply {
                        put("realtimeInput", JSONObject().apply {
                            put("mediaChunks", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Img)
                                })
                            })
                        })
                    }
                    val sentImg = webSocket?.send(imgObj.toString()) ?: false
                    android.util.Log.d("GeminiLiveClient", "Sent image frame: $sentImg (${imageBytes.size} bytes)")
                    // Allow the live server vision pipeline 300ms to register the image frame before closing the turn
                    delay(300)
                }

                // 2. Dispatch user prompt via clientContent
                val effectivePrompt = if (prompt.isBlank() && imageBytes != null) {
                    "Describe what you see in this image."
                } else {
                    prompt
                }

                val inputObj = JSONObject().apply {
                    put("clientContent", JSONObject().apply {
                        put("turns", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", effectivePrompt)
                                    })
                                })
                            })
                        })
                        put("turnComplete", true)
                    })
                }
                val sent = webSocket?.send(inputObj.toString()) ?: false
                android.util.Log.d("GeminiLiveClient", "Sent clientContent: $sent")
                if (!sent) {
                    pendingPrompt = QueuedPrompt(prompt, messageId, imageBytes)
                    disconnect()
                    connect()
                }
            } catch (e: Exception) {
                listener.onError(messageId, "Failed to send message: ${e.localizedMessage}")
            }
        }
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            // 1. Setup handshake confirmation
            if (root.has("setupComplete")) {
                isSetupComplete = true
                pendingPrompt?.let { q ->
                    sendRealtimeMediaAndTurn(q.prompt, q.messageId, q.imageBytes)
                    pendingPrompt = null
                }
                return
            }

            // 2. Handle GoAway control message (session expiring or terminating)
            if (root.has("goAway")) {
                isConnected = false
                isSetupComplete = false
                return
            }

            val msgId = currentMessageId ?: pendingPrompt?.messageId ?: return

            if (root.has("error")) {
                val errObj = root.getJSONObject("error")
                val errMsg = errObj.optString("message", "API Error occurred")
                listener.onError(msgId, "Gemini API Error: $errMsg")
                return
            }

            val serverContent = root.optJSONObject("serverContent") ?: return

            // Model turn parts (text, reasoning, audio PCM)
            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null && modelTurn.has("parts")) {
                val parts = modelTurn.getJSONArray("parts")
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)

                    // Reasoning / Thinking check
                    val isThought = part.optBoolean("thought", false)
                    if (isThought && part.has("text")) {
                        accumulatedReasoning.append(part.getString("text"))
                        listener.onReasoningUpdate(msgId, accumulatedReasoning.toString())
                    } else if (part.has("text")) {
                        accumulatedText.append(part.getString("text"))
                        listener.onTextUpdate(msgId, accumulatedText.toString())
                    }

                    // Audio PCM
                    if (part.has("inlineData")) {
                        val inline = part.getJSONObject("inlineData")
                        val base64Data = inline.optString("data")
                        if (base64Data.isNotEmpty()) {
                            val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                            listener.onAudioChunkReceived(msgId, pcmBytes)
                        }
                    }
                }
            }

            // Output Audio Transcriptions
            val transcription = serverContent.optJSONObject("outputTranscription")
            if (transcription != null && transcription.has("text")) {
                val spokenText = transcription.getString("text")
                accumulatedText.append(spokenText)
                listener.onTextUpdate(msgId, accumulatedText.toString())
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                listener.onTurnComplete(msgId)
            }
        } catch (e: Exception) {
            val targetId = currentMessageId ?: ""
            listener.onError(targetId, "Parsing error: ${e.localizedMessage}")
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webSocket = null
        isConnected = false
        isConnecting = false
        isSetupComplete = false
        currentMessageId = null
        listener.onConnectionStatusChanged(isConnected = false, isConnecting = false)
    }
}
