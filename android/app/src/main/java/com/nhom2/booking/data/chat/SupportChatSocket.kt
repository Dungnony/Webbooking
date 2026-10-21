package com.nhom2.booking.data.chat

import android.util.Log
import com.nhom2.booking.data.model.SupportMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "SupportChatSocket"
private const val RECONNECT_DELAY_MS = 5_000L
private const val NORMAL_CLOSURE = 1000
// Each user only ever receives their own conversation here; the backend refuses any other subscription.
private const val PERSONAL_QUEUE = "/user/queue/support-chat"

/**
 * Pushes support-chat messages in real time over STOMP (backend endpoint /ws). Sending still goes through the
 * REST API, as the backend requires.
 */
class SupportChatSocket(
    private val client: OkHttpClient,
    baseUrl: String,
    private val tokenProvider: () -> String?,
    private val json: Json,
) {
    private val httpUrl = baseUrl.toHttpUrl()
    private val socketUrl = httpUrl.newBuilder().addPathSegment("ws").build().toString()
        .replaceFirst("http", "ws")

    /** Reconnects after drops until the collector is cancelled. */
    fun messages(): Flow<SupportMessage> = flow {
        while (true) {
            emitAll(connectOnce())
            delay(RECONNECT_DELAY_MS)
        }
    }

    private fun connectOnce(): Flow<SupportMessage> = callbackFlow {
        val token = tokenProvider()
        if (token == null) {
            close()
            return@callbackFlow
        }
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(StompFrame.connect(httpUrl.host, token).encode())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                for (frame in StompFrame.parseAll(text)) {
                    when (frame.command) {
                        "CONNECTED" -> webSocket.send(StompFrame.subscribe("support-chat", PERSONAL_QUEUE).encode())
                        "MESSAGE" -> decode(frame.body)?.let { trySend(it) }
                        "ERROR" -> {
                            Log.w(TAG, "Support chat refused: ${frame.headers["message"]}")
                            webSocket.close(NORMAL_CLOSURE, null)
                            channel.close()
                        }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Support chat connection lost, retrying", t)
                channel.close()
            }
        }
        val socket = client.newWebSocket(Request.Builder().url(socketUrl).build(), listener)
        awaitClose { socket.close(NORMAL_CLOSURE, null) }
    }

    private fun decode(body: String): SupportMessage? = try {
        json.decodeFromString<SupportMessage>(body)
    } catch (e: SerializationException) {
        Log.w(TAG, "Ignoring unreadable chat message", e)
        null
    }
}
