package com.codewithram.secretchat.ui.home

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PhoenixChannel(
    private val socketUrl: String,
    internal val topic: String,
    private val params: Map<String, String> = emptyMap()
) {

    private val TAG = "PhoenixChannel"
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private lateinit var webSocket: WebSocket
    private var ref = 1L
    private val pendingReplies = mutableMapOf<Long, (JSONObject) -> Unit>()

    fun getSocket(): WebSocket = webSocket
    var onMessageReceived: ((event: String, payload: JSONObject) -> Unit)? = null
    var onJoinSuccess: ((JSONObject) -> Unit)? = null
    var onJoinError: ((JSONObject) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    private var isConnected = false

    fun connect() {
        val urlWithParams = buildUrlWithParams(socketUrl, params)
        val request = Request.Builder().url(urlWithParams).build()
        webSocket = client.newWebSocket(request, PhoenixWebSocketListener())
    }

    fun disconnect() {
        webSocket.close(1000, "Normal closure")
    }

    fun join() {
        val joinPayload = JSONObject()
        sendMessage("phx_join", joinPayload) { resp ->
            if (resp.optString("status") == "ok") {
                onJoinSuccess?.invoke(resp)
            } else {
                onJoinError?.invoke(resp)
            }
        }
    }

    fun push(event: String, payload: JSONObject, onReply: ((JSONObject) -> Unit)? = null) {
        sendMessage(event, payload, onReply)
    }


    private fun sendMessage(event: String, payload: JSONObject, onReply: ((JSONObject) -> Unit)? = null) {
        val messageRef = ref++
        val msg = JSONObject().apply {
            put("topic", topic)
            put("event", event)
            put("payload", payload)
            put("ref", messageRef.toString())
        }
        onReply?.let { pendingReplies[messageRef] = it }
        webSocket.send(msg.toString())
    }
    fun isConnected(): Boolean {
        return isConnected
    }

    fun pushWithReply(
        event: String,
        payload: JSONObject,
        onOk: ((JSONObject) -> Unit)? = null,
        onError: ((JSONObject) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        timeoutMillis: Long = 5000
    ) {
        val messageRef = ref++
        val msg = JSONObject().apply {
            put("topic", topic)
            put("event", event)
            put("payload", payload)
            put("ref", messageRef.toString())
        }

        if (onTimeout != null) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(timeoutMillis)
                pendingReplies.remove(messageRef)?.let {
                    withContext(Dispatchers.Main) {
                        onTimeout()
                    }
                }
            }
        }

        pendingReplies[messageRef] = { resp ->
            val status = resp.optString("status")
            when (status) {
                "ok" -> onOk?.invoke(resp)
                "error" -> onError?.invoke(resp)
            }
        }

        webSocket.send(msg.toString())
    }


    private fun buildUrlWithParams(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return if (baseUrl.contains("?")) "$baseUrl&$query" else "$baseUrl?$query"
    }

    private inner class PhoenixWebSocketListener : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            join()
            isConnected = true
        }

        override fun onMessage(ws: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                val event = json.optString("event")
                val topic = json.optString("topic")
                val payload = json.optJSONObject("payload") ?: JSONObject()
                val refString = json.optString("ref")
                val refLong = refString.toLongOrNull()

                if (topic == this@PhoenixChannel.topic) {

                    if (refLong != null && pendingReplies.containsKey(refLong)) {
                        pendingReplies.remove(refLong)?.invoke(payload)
                    } else {
                        onMessageReceived?.invoke(event, payload)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WS message", e)
            }
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            ws.close(code, reason)
            isConnected = false
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            isConnected = false
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            isConnected = false
        }
    }
}
