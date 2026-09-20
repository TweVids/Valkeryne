package com.twevids.valkeryne.network

import android.util.Base64
import android.util.Log
import com.twevids.valkeryne.model.AppSettings
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
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
    companion object {
        private const val TAG = "GeminiLiveClient"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var currentMessageId: String? = null
    private var accumulatedText = StringBuilder()
    private var accumulatedReasoning = StringBuilder()

    @Volatile
    private var isConnected = false
    @Volatile
    private var isConnecting = false
    @Volatile
    private var isSetupComplete = false
    @Volatile
    private var isExplicitDisconnect = false

    // Pending queues for audio, image, and turns during handshake
    private val pendingAudioQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile
    private var pendingImageBytes: ByteArray? = null
    @Volatile
    private var pendingFinishVoiceTurn = false

    private data class QueuedPrompt(
        val prompt: String,
        val messageId: String,
        val imageBytes: ByteArray? = null
    )
    private var pendingPrompt: QueuedPrompt? = null
    private var reconnectJob: Job? = null

    fun updateSettings(newSettings: AppSettings) {
        val oldSettings = settings
        settings = newSettings
        val needsReconnect = oldSettings.apiKey != newSettings.apiKey ||
                oldSettings.modelId != newSettings.modelId ||
                oldSettings.voiceName != newSettings.voiceName ||
                oldSettings.systemInstruction != newSettings.systemInstruction
        if (needsReconnect) {
            disconnect()
            connect()
        }
    }

    fun connect() {
        if (isConnected || isConnecting) return

        val key = settings.apiKey.trim()
        if (key.isEmpty()) {
            listener.onError("", "API Key is required. Please configure it in Settings.")
            return
        }

        isExplicitDisconnect = false
        isConnecting = true
        isSetupComplete = false
        listener.onConnectionStatusChanged(isConnected = false, isConnecting = true)

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (ws != this@GeminiLiveClient.webSocket) {
                    Log.d(TAG, "Ignoring onOpen from stale WebSocket")
                    return
                }
                Log.d(TAG, "WebSocket connected successfully. Dispatching setup...")
                isConnected = true
                isConnecting = false
                listener.onConnectionStatusChanged(isConnected = true, isConnecting = false)

                // Send Setup payload as the FIRST message over WebSocket
                sendSetup(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (ws != this@GeminiLiveClient.webSocket) return
                handleIncomingMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (ws != this@GeminiLiveClient.webSocket) return
                handleIncomingMessage(bytes.utf8())
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (ws != this@GeminiLiveClient.webSocket) {
                    Log.d(TAG, "Ignoring onFailure from stale WebSocket")
                    return
                }
                Log.e(TAG, "WebSocket failure: ${t.localizedMessage}", t)
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
                listener.onError(targetId, "Live Error: $errorDesc")
                pendingPrompt = null

                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (ws != this@GeminiLiveClient.webSocket) {
                    Log.d(TAG, "Ignoring onClosed from stale WebSocket")
                    return
                }
                Log.w(TAG, "WebSocket closed ($code): $reason")
                isConnected = false
                isConnecting = false
                isSetupComplete = false
                listener.onConnectionStatusChanged(isConnected = false, isConnecting = false)

                if (code != 1000) {
                    val targetId = currentMessageId ?: ""
                    listener.onError(targetId, "Live Connection closed ($code): $reason")
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (isExplicitDisconnect || settings.apiKey.isBlank()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2000)
            if (!isConnected && !isConnecting && !isExplicitDisconnect) {
                Log.d(TAG, "Auto-reconnecting to Gemini Live...")
                connect()
            }
        }
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
                if (isExtendedThinking) {
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", "low")
                        put("includeThoughts", true)
                    })
                }
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
            Log.d(TAG, "Setup message sent for model models/${settings.modelId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup", e)
        }
    }

    fun startVoiceTurn(messageId: String) {
        currentMessageId = messageId
        accumulatedText.clear()
        accumulatedReasoning.clear()
        pendingAudioQueue.clear()
        pendingImageBytes = null
        pendingFinishVoiceTurn = false

        listener.onAiMessageStart(messageId)

        if (!isConnected && !isConnecting) {
            connect()
        }
    }

    fun sendRealtimeImage(imageBytes: ByteArray) {
        if (!isConnected || !isSetupComplete || webSocket == null) {
            Log.d(TAG, "Session not ready yet; buffering realtime image (${imageBytes.size} bytes)")
            pendingImageBytes = imageBytes
            if (!isConnected && !isConnecting) connect()
            return
        }
        sendRealtimeImageInternal(imageBytes)
    }

    private fun sendRealtimeImageInternal(imageBytes: ByteArray) {
        try {
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
            webSocket?.send(imgObj.toString())
            Log.d(TAG, "Dispatched realtime image frame (${imageBytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending realtime image", e)
        }
    }

    fun sendRealtimeAudio(pcmBytes: ByteArray) {
        if (!isConnected || !isSetupComplete || webSocket == null) {
            if (pendingAudioQueue.size < 50) { // Limit buffer to ~5s
                pendingAudioQueue.offer(pcmBytes)
            }
            if (!isConnected && !isConnecting) connect()
            return
        }
        sendRealtimeAudioInternal(pcmBytes)
    }

    private fun sendRealtimeAudioInternal(pcmBytes: ByteArray) {
        try {
            val base64Pcm = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val audioObj = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    })
                })
            }
            webSocket?.send(audioObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending realtime audio", e)
        }
    }

    fun finishVoiceTurn(imageBytes: ByteArray? = null) {
        if (!isConnected || !isSetupComplete || webSocket == null) {
            Log.d(TAG, "finishVoiceTurn requested before setupComplete. Marking pending (hasImage: ${imageBytes != null}).")
            pendingFinishVoiceTurn = true
            pendingImageBytes = imageBytes
            if (!isConnected && !isConnecting) connect()
            return
        }
        finishVoiceTurnInternal(imageBytes)
    }

    private fun finishVoiceTurnInternal(imageBytes: ByteArray? = null) {
        scope.launch {
            try {
                val parts = JSONArray()
                // If a camera frame is provided, embed it directly into the turn as inlineData
                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    val base64Img = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                    parts.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Img)
                        })
                    })
                    // Ensure the model knows to respond to the visual frame and any concurrent speech
                    parts.put(JSONObject().apply {
                        put("text", "Please answer based on the visual camera frame and any spoken audio.")
                    })
                    Log.d(TAG, "Embedded inline image frame into voice turn (${imageBytes.size} bytes)")
                } else {
                    // Audio-only turn: empty text part is required by schema
                    parts.put(JSONObject().apply {
                        put("text", "")
                    })
                }

                val finishObj = JSONObject().apply {
                    put("clientContent", JSONObject().apply {
                        put("turns", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", parts)
                            })
                        })
                        put("turnComplete", true)
                    })
                }
                val sent = webSocket?.send(finishObj.toString()) ?: false
                Log.d(TAG, "finishVoiceTurn dispatched (hasImage: ${imageBytes != null}, turnComplete: true): $sent")
            } catch (e: Exception) {
                Log.e(TAG, "Error finishing voice turn", e)
            }
        }
    }

    fun sendMessage(prompt: String, messageId: String, imageBytes: ByteArray? = null) {
        currentMessageId = messageId
        accumulatedText.clear()
        accumulatedReasoning.clear()
        listener.onAiMessageStart(messageId)

        if (!isConnected || !isSetupComplete || webSocket == null) {
            pendingPrompt = QueuedPrompt(prompt, messageId, imageBytes)
            if (!isConnected && !isConnecting) connect()
        } else {
            sendRealtimeMediaAndTurn(prompt, messageId, imageBytes)
        }
    }

    private fun sendRealtimeMediaAndTurn(prompt: String, messageId: String, imageBytes: ByteArray?) {
        scope.launch {
            try {
                if (imageBytes != null && imageBytes.isNotEmpty()) {
                    sendRealtimeImageInternal(imageBytes)
                    delay(200)
                }

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
                Log.d(TAG, "Sent clientContent text turn: $sent")
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
                Log.d(TAG, "Received setupComplete from Gemini Live server!")
                isSetupComplete = true

                // Flush pending audio chunks
                while (!pendingAudioQueue.isEmpty()) {
                    val chunk = pendingAudioQueue.poll() ?: break
                    sendRealtimeAudioInternal(chunk)
                }

                // Flush pending finish turn (with pending camera frame if any)
                if (pendingFinishVoiceTurn) {
                    pendingFinishVoiceTurn = false
                    val img = pendingImageBytes
                    pendingImageBytes = null
                    finishVoiceTurnInternal(img)
                } else {
                    pendingImageBytes?.let { img ->
                        sendRealtimeImageInternal(img)
                        pendingImageBytes = null
                    }
                }

                // Flush pending text prompt
                pendingPrompt?.let { q ->
                    sendRealtimeMediaAndTurn(q.prompt, q.messageId, q.imageBytes)
                    pendingPrompt = null
                }
                return
            }

            // 2. Handle GoAway control message
            if (root.has("goAway")) {
                Log.w(TAG, "Received goAway from server. Reconnecting session.")
                isConnected = false
                isSetupComplete = false
                scheduleReconnect()
                return
            }

            val msgId = currentMessageId ?: pendingPrompt?.messageId ?: ""

            if (root.has("error")) {
                val errObj = root.getJSONObject("error")
                val errMsg = errObj.optString("message", "API Error occurred")
                Log.e(TAG, "Gemini API Error: $errMsg")
                listener.onError(msgId, "Gemini Error: $errMsg")
                return
            }

            val serverContent = root.optJSONObject("serverContent") ?: return

            // Model turn parts (text, reasoning, audio PCM)
            val modelTurn = serverContent.optJSONObject("modelTurn")
            if (modelTurn != null && modelTurn.has("parts")) {
                val parts = modelTurn.getJSONArray("parts")
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)

                    // Reasoning / Thinking thoughts
                    val isThought = part.optBoolean("thought", false)
                    if (isThought && part.has("text")) {
                        accumulatedReasoning.append(part.getString("text"))
                        listener.onReasoningUpdate(msgId, accumulatedReasoning.toString())
                    } else if (part.has("text")) {
                        accumulatedText.append(part.getString("text"))
                        listener.onTextUpdate(msgId, accumulatedText.toString())
                    }

                    // Audio PCM (24kHz 16-bit little-endian)
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
                Log.d(TAG, "Model turn complete for message: $msgId")
                listener.onTurnComplete(msgId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${e.localizedMessage}", e)
        }
    }

    fun disconnect() {
        isExplicitDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        val wsToCancel = webSocket
        webSocket = null
        try {
            wsToCancel?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isConnected = false
        isConnecting = false
        isSetupComplete = false
        currentMessageId = null
        pendingAudioQueue.clear()
        pendingImageBytes = null
        pendingFinishVoiceTurn = false
        listener.onConnectionStatusChanged(isConnected = false, isConnecting = false)
    }
}
