package com.unciv.logic.multiplayer.chat

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.chat.Chat.Companion.relayGlobalMessage
import com.unciv.logic.multiplayer.chat.ChatWebSocket.job
import com.unciv.logic.multiplayer.chat.ChatWebSocket.start
import com.unciv.utils.Concurrency
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

// used when sending a message
@Serializable
sealed class Message {
    @Serializable
    @SerialName("chat")
    data class Chat(
        val gameId: String, val civName: String, val message: String
    ) : Message()

    @Serializable
    @SerialName("join")
    data class Join(
        val gameIds: List<String>
    ) : Message()

    @Serializable
    @SerialName("leave")
    data class Leave(
        val gameIds: List<String>
    ) : Message()
}

// used when receiving a message
@Serializable
sealed class Response {
    @Serializable
    @SerialName("chat")
    data class Chat(
        val gameId: String, val civName: String, val message: String
    ) : Response()

    @Serializable
    @SerialName("joinSuccess")
    data class JoinSuccess(
        val gameIds: List<String>
    ) : Response()

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String
    ) : Response()
}

class ChatRestartException : CancellationException("Chat restart requested")
class ChatStopException : CancellationException("Chat stop requested")

object ChatWebSocket {
    private var isStarted = false

    @OptIn(ExperimentalTime::class)
    private var lastRetry = Clock.System.now()
    private var reconnectionAttempts = 0
    private var reconnectTimeSeconds = INITIAL_RECONNECT_TIME_SECONDS

    private const val INITIAL_RECONNECT_TIME_SECONDS = 1
    private const val MAX_RECONNECTION_ATTEMPTS = 100
    private const val MAX_RECONNECT_TIME_SECONDS = 64
    private const val INITIAL_SESSION_WAIT_FOR_MS = 5_000L

    private var job: Job? = null
    private val eventReceiver = EventBus.EventReceiver()
    private var session: DefaultClientWebSocketSession? = null

    @OptIn(ExperimentalSerializationApi::class)
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 30_000
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                classDiscriminator = "type"
                // DO NOT OMIT
                // if omitted the "type" field will be missing from all outgoing messages
                classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
            })
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun resetExponentialBackoff() {
        lastRetry = Clock.System.now()

        reconnectionAttempts = 0
        reconnectionAttempts = INITIAL_RECONNECT_TIME_SECONDS
    }

    private fun getChatUrl(): Url = URLBuilder(
        UncivGame.Current.onlineMultiplayer.multiplayerServer.getServerUrl()
    ).apply {
        appendPathSegments("chat")
        protocol = if (protocol.isSecure()) URLProtocol.WSS else URLProtocol.WS
    }.build()

    /**
     * Only requests a message to be sent.
     * Does not guarantee delivery.
     * Failures are mosly ignored.
     *
     * The server will relay it back if a delivery was acknowledged and that is when we should display it.
     *
     * Also calls [start] to start the connection in case it was not done beforehand.
     */
    fun requestMessageSend(message: Message) {
        start()
        Concurrency.run("MultiplayerChatSendMessage") {
            withTimeoutOrNull(INITIAL_SESSION_WAIT_FOR_MS) {
                while (session == null) {
                    delay(100)
                }
            }
            session?.runCatching {
                this.sendSerialized(message)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun handleWebSocketThrowables(t: Throwable) {
        print("ChatError: ${t.message}. Reconnecting...")

        if (reconnectionAttempts == 0) {
            lastRetry = Clock.System.now()
            relayGlobalMessage("WebSocket connection closed. Cause: [${t.cause}]!")
            if (t.message?.contains("401") == true) {
                relayGlobalMessage("Authentication issue detected! You have to set a password to use Chat.")
            }
        } else {
            val now = Clock.System.now()
            print(" (Last retry was ${(now - lastRetry).toString(DurationUnit.SECONDS, 2)} ago)")
            lastRetry = now
        }

        println()
        restart(dueToError = true)
    }

    private suspend fun startSession() {
        try {
            session?.close()
            session = client.webSocketSession {
                url(getChatUrl())
                header(HttpHeaders.Authorization, UncivGame.Current.settings.multiplayer.getAuthHeader())
            }

            session!!.runCatching {
                if (isActive) {
                    if (reconnectionAttempts == 0) {
                        println("ChatLog: Connected to WebSocket.")
                        relayGlobalMessage("Successfully connected to WebSocket server!")
                    } else if (reconnectionAttempts > 0) {
                        println("ChatLog: Re-established webSocket connection.")
                        relayGlobalMessage("Successfully re-established WebSocket connection!")
                    }
                    // we are successfully connected
                    resetExponentialBackoff()
                }

                val gameIds = ChatStore.getGameIds()
                    .union(UncivGame.Current.onlineMultiplayer.games.mapNotNull { it.preview?.gameId })
                this.sendSerialized(Message.Join(gameIds.toList()))

                while (this.isActive) {
                    val response = receiveDeserialized<Response>()
                    when (response) {
                        is Response.Chat -> EventBus.send(
                            ChatMessageReceived(
                                response.gameId, response.civName, response.message
                            )
                        )

                        is Response.Error -> relayGlobalMessage("Error: [${response.message}]", "Server")
                        is Response.JoinSuccess -> Unit
                    }
                    yield()
                }
            }
                .onSuccess { restart() }
                .onFailure { handleWebSocketThrowables(it) }
        } catch (e: Exception) {
            handleWebSocketThrowables(e)
        }
    }

    private fun start() {
        if (!isStarted) {
            isStarted = true
            job = Concurrency.run("MultiplayerChat") { startSession() }
        }
    }

    /**
     * Stops the socket and clears all type of chat from [ChatStore]
     */
    fun stop() {
        isStarted = false
        ChatStore.clear()
        job?.cancel(ChatStopException())
    }

    /**
     * By default, this gets autocancelled if the [job] is still running.
     * Force mode will cancel the previous [job] and reassign a new one.
     * This is helpfull when we need to reset [job] due to events.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun restart(dueToError: Boolean = false, force: Boolean = false) {
        if (!isStarted) return
        if (dueToError) {
            if (++reconnectionAttempts > MAX_RECONNECTION_ATTEMPTS) {
                return
            }
        } else resetExponentialBackoff()

        if (force) println("ChatLog: A force restart seems to be requested!")

        GlobalScope.launch {
            if (!force) {
                // exponential backoff same as described here: https://cloud.google.com/memorystore/docs/redis/exponential-backoff
                delay(Random.nextLong(1000) + 1000L * reconnectTimeSeconds)
                reconnectTimeSeconds = (reconnectTimeSeconds * 2).coerceAtMost(MAX_RECONNECT_TIME_SECONDS)
                if (job?.isActive == true) return@launch
            }

            yield()
            job?.cancel(ChatRestartException())
            job = Concurrency.run("MultiplayerChat") { startSession() }
        }
    }

    init {
        eventReceiver.receive(ChatMessageReceived::class) {
            if (it.gameId.isEmpty())
                ChatStore.addGlobalMessage(it.civName, it.message)
            else
                ChatStore.getChatByGameId(it.gameId).addMessage(it.civName, it.message)
        }
    }
}
