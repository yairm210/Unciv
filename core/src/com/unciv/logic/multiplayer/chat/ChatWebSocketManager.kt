package com.unciv.logic.multiplayer.chat

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.chat.Chat.Companion.relayGlobalMessage
import com.unciv.logic.multiplayer.chat.ChatWebSocketManager.job
import com.unciv.models.metadata.PasswordChanged
import com.unciv.models.metadata.ServerUrlChanged
import com.unciv.models.metadata.UserIdChanged
import com.unciv.utils.Concurrency
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json

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


object ChatWebSocketManager {
    private var errorReconnectionAttempts = 0
    private const val MAX_ERROR_RECONNECTION_ATTEMPTS = 100
    private const val RECONNECT_TIME_MS: Long = 5_000

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

    private val chatUrl
        get() = URLBuilder(
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
     */
    fun requestMessageSend(message: Message) {
        startSocket()
        Concurrency.run("MultiplayerChatSendMessage") {
            withTimeoutOrNull(1000) {
                while (session == null) {
                    delay(50)
                }
            }
            session?.runCatching {
                this.sendSerialized(message)
            }
        }
    }

    private fun handleWebSocketThrowables(t: Throwable) {
        println("ChatError: ${t.message}")

        if (errorReconnectionAttempts == 0) {
            relayGlobalMessage("WebSocket connection closed. Cause: ${t.cause}!")
            if (t.message?.contains("401") == true) {
                relayGlobalMessage("Authentication issue detected! You have to set a password to use Chat.")
            }
        }

        restartSocket(true)
    }

    private suspend fun startSession() {
        try {
            session?.close()
            session = client.webSocketSession {
                url(chatUrl)
                header(HttpHeaders.Authorization, UncivGame.Current.settings.multiplayer.getAuthHeader())
            }

            session!!.runCatching {
                if (isActive) {
                    if (errorReconnectionAttempts == 0) {
                        println("ChatLog: Connected to WebSocket.")
                        relayGlobalMessage("Successfully connected to WebSocket server!")
                    } else if (errorReconnectionAttempts > 0) {
                        println("ChatLog: Re-established webSocket connection.")
                        relayGlobalMessage("Successfully re-established WebSocket connection!")
                    }
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

                        is Response.Error -> relayGlobalMessage("Error: ${response.message}", "Server")
                        is Response.JoinSuccess -> Unit
                    }
                }
            }
                .onSuccess { restartSocket() }
                .onFailure { handleWebSocketThrowables(it) }
        } catch (e: Exception) {
            handleWebSocketThrowables(e)
        }
    }

    private fun startSocket() {
        if (job?.isActive == true) return
        job?.cancel()
        job = Concurrency.run("MultiplayerChat") { startSession() }
    }

    /**
     * By default, this gets autocancelled if the [job] is still running.
     * Force mode will cancel the previous [job] and reassign a new one.
     * This is helpfull when we need to reset [job] due to events.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun restartSocket(dueToError: Boolean = false, force: Boolean = false) {
        if (dueToError) {
            if (errorReconnectionAttempts++ > MAX_ERROR_RECONNECTION_ATTEMPTS) {
                return
            }
        } else errorReconnectionAttempts = 0

        GlobalScope.launch {
            flow {
                delay(RECONNECT_TIME_MS)
                emit(Unit)
            }.collect {
                if (job?.isActive == true && !force)
                    return@collect

                job?.cancel()
                job = Concurrency.run("MultiplayerChat") { startSession() }
            }
        }
    }

    init {
        eventReceiver.receive(ChatMessageReceived::class) {
            if (it.gameId.isEmpty())
                ChatStore.addGlobalMessage(it.civName, it.message)
            else
                ChatStore.getChatByGameId(it.gameId).addMessage(it.civName, it.message)
        }

        eventReceiver.receive(PasswordChanged::class) {
            if (job != null) restartSocket(force = true)
        }

        eventReceiver.receive(UserIdChanged::class) {
            if (job != null) restartSocket(force = true)
        }

        eventReceiver.receive(ServerUrlChanged::class) {
            if (job != null) restartSocket(force = true)
        }
    }
}
