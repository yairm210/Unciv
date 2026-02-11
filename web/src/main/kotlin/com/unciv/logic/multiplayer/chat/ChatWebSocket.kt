package com.unciv.logic.multiplayer.chat

import com.badlogic.gdx.utils.JsonReader
import com.unciv.UncivGame
import com.unciv.platform.PlatformCapabilities
import com.unciv.utils.Log
import java.util.concurrent.CancellationException
import kotlin.math.min

sealed class Message {
    data class Chat(
        val civName: String,
        val message: String,
        val gameId: String,
    ) : Message()

    data class Join(
        val gameIds: List<String>,
    ) : Message()

    data class Leave(
        val gameIds: List<String>,
    ) : Message()
}

sealed class Response {
    data class Chat(
        val civName: String,
        val message: String,
        val gameId: String? = null,
    ) : Response()

    data class JoinSuccess(
        val gameIds: List<String>,
    ) : Response()

    data class Error(
        val message: String,
    ) : Response()
}

class ChatRestartException : CancellationException("Chat restart requested")
class ChatStopException : CancellationException("Chat stop requested")

object ChatWebSocket {
    private const val baseReconnectDelayMs = 1000
    private const val maxReconnectDelayMs = 64000
    private const val maxReconnectAttempts = 50

    private val jsonReader = JsonReader()
    private val pendingMessages = ArrayDeque<String>()
    private var socket: Any? = null
    private var started = false
    private var reconnectAttempts = 0
    private var reconnectDelayMs = baseReconnectDelayMs
    private var reconnectScheduled = false

    fun requestMessageSend(message: Message) {
        if (!PlatformCapabilities.current.onlineMultiplayer) {
            if (message is Message.Chat) {
                ChatStore.relayGlobalMessage("Chat is unavailable on web in this phase.")
            }
            return
        }
        start()
        enqueueOrSend(encodeMessage(message))
    }

    fun stop() {
        started = false
        reconnectScheduled = false
        pendingMessages.clear()
        tryClose("client_stop")
        ChatStore.clear()
    }

    fun restart(dueToError: Boolean = false, force: Boolean = false) {
        if (!PlatformCapabilities.current.onlineMultiplayer) return
        if (!started) {
            if (force) {
                pendingMessages.clear()
                reconnectAttempts = 0
                reconnectDelayMs = baseReconnectDelayMs
                reconnectScheduled = false
                tryClose("restart_ignored")
            }
            return
        }
        if (dueToError) {
            reconnectAttempts += 1
            if (reconnectAttempts > maxReconnectAttempts) {
                ChatStore.relayGlobalMessage("Chat reconnect failed: too many attempts.")
                return
            }
        } else {
            reconnectAttempts = 0
            reconnectDelayMs = baseReconnectDelayMs
        }
        scheduleReconnect(force)
    }

    private fun start() {
        if (started) return
        started = true
        tryClose("start")
        connect()
    }

    private fun connect() {
        val url = buildChatUrl()
        if (url.isBlank()) {
            Log.error("Chat websocket URL is empty.")
            return
        }
        var current: Any? = null
        current = WebSocketInterop.connect(
            url,
            null,
            open@{
                if (socket != current) return@open
                onOpen()
            },
            message@{ message ->
                if (socket != current) return@message
                onMessage(message)
            },
            error@{ error ->
                if (socket != current) return@error
                onError(error)
            },
            close@{ code, reason ->
                if (socket != current) return@close
                onClose(code, reason)
            }
        )
        socket = current
    }

    private fun onOpen() {
        reconnectAttempts = 0
        reconnectDelayMs = baseReconnectDelayMs
        reconnectScheduled = false
        ChatStore.relayGlobalMessage("Connected to multiplayer chat.")
        flushJoinAndPending()
    }

    private fun onMessage(raw: String) {
        val parsed = tryParseResponse(raw) ?: return
        when (parsed) {
            is Response.Chat -> ChatStore.relayChatMessage(parsed)
            is Response.Error -> ChatStore.relayGlobalMessage("Error: ${parsed.message}")
            is Response.JoinSuccess -> Unit
        }
    }

    private fun onError(message: String) {
        Log.error("Chat websocket error: $message")
        restart(dueToError = true, force = true)
    }

    private fun onClose(code: Int, reason: String) {
        if (!started) return
        val details = if (reason.isNotBlank()) " ($code: $reason)" else " ($code)"
        ChatStore.relayGlobalMessage("Chat connection closed$details")
        restart(dueToError = true, force = false)
    }

    private fun scheduleReconnect(force: Boolean) {
        if (reconnectScheduled && !force) return
        reconnectScheduled = true
        val delayMs = if (force) 0 else reconnectDelayMs
        reconnectDelayMs = min(reconnectDelayMs * 2, maxReconnectDelayMs)
        WebSocketInterop.schedule(Runnable {
            reconnectScheduled = false
            tryClose("reconnect")
            connect()
        }, delayMs)
    }

    private fun tryClose(reason: String) {
        val current = socket
        socket = null
        if (current != null) {
            WebSocketInterop.close(current, 1000, reason)
        }
    }

    private fun enqueueOrSend(payload: String) {
        val current = socket
        if (current == null || WebSocketInterop.readyState(current) != 1) {
            pendingMessages.add(payload)
            return
        }
        WebSocketInterop.send(current, payload)
    }

    private fun flushJoinAndPending() {
        val gameIds = ChatStore.getGameIds()
            .union(UncivGame.Current.onlineMultiplayer.games.mapNotNull { it.preview?.gameId })
        if (gameIds.isNotEmpty()) {
            enqueueOrSend(encodeMessage(Message.Join(gameIds.toList())))
        }
        while (pendingMessages.isNotEmpty()) {
            WebSocketInterop.send(socket, pendingMessages.removeFirst())
        }
    }

    private fun buildChatUrl(): String {
        val base = UncivGame.Current.onlineMultiplayer.multiplayerServer.getServerUrl()
        if (base.isBlank()) return ""
        var url = base.trim().trimEnd('/')
        url = if (url.startsWith("https://")) {
            "wss://${url.substringAfter("https://")}"
        } else if (url.startsWith("http://")) {
            "ws://${url.substringAfter("http://")}"
        } else {
            "ws://$url"
        }
        url += "/chat"
        val auth = UncivGame.Current.settings.multiplayer.getAuthHeader()
        if (auth.isNotBlank()) {
            val encoded = WebSocketInterop.encodeURIComponent(auth)
            url += "?auth=$encoded"
        }
        return url
    }

    private fun encodeMessage(message: Message): String {
        return when (message) {
            is Message.Chat -> buildJson("chat", mapOf(
                "civName" to message.civName,
                "message" to message.message,
                "gameId" to message.gameId,
            ))
            is Message.Join -> buildJson("join", mapOf("gameIds" to message.gameIds))
            is Message.Leave -> buildJson("leave", mapOf("gameIds" to message.gameIds))
        }
    }

    private fun buildJson(type: String, fields: Map<String, Any?>): String {
        val builder = StringBuilder()
        builder.append('{')
        builder.append("\"type\":\"").append(escapeJson(type)).append("\"")
        for ((key, value) in fields) {
            builder.append(',')
            builder.append('"').append(escapeJson(key)).append("\":")
            when (value) {
                null -> builder.append("null")
                is String -> builder.append('"').append(escapeJson(value)).append('"')
                is List<*> -> {
                    builder.append('[')
                    var first = true
                    for (item in value) {
                        if (!first) builder.append(',')
                        first = false
                        val text = item?.toString().orEmpty()
                        builder.append('"').append(escapeJson(text)).append('"')
                    }
                    builder.append(']')
                }
                else -> builder.append('"').append(escapeJson(value.toString())).append('"')
            }
        }
        builder.append('}')
        return builder.toString()
    }

    private fun escapeJson(value: String): String {
        val builder = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    private fun tryParseResponse(raw: String): Response? {
        val parsed = try {
            jsonReader.parse(raw)
        } catch (_: Throwable) {
            return null
        }
        if (parsed == null || !parsed.isObject) return null
        val type = parsed.getString("type", "")
        return when (type) {
            "chat" -> Response.Chat(
                civName = parsed.getString("civName", "System"),
                message = parsed.getString("message", ""),
                gameId = parsed.getString("gameId", null),
            )
            "error" -> Response.Error(parsed.getString("message", "Unknown error"))
            "joinSuccess" -> {
                val items = parsed.get("gameIds")
                val gameIds = ArrayList<String>()
                if (items != null && items.isArray) {
                    var child = items.child
                    while (child != null) {
                        gameIds.add(child.asString())
                        child = child.next
                    }
                }
                Response.JoinSuccess(gameIds)
            }
            else -> null
        }
    }
}
