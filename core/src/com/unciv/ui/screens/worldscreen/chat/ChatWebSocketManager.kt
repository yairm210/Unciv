package com.unciv.ui.screens.worldscreen.chat

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.PasswordChangeEvent
import com.unciv.ui.popups.options.MultiplayerServerUrlChangeEvent
import com.unciv.ui.popups.options.UserIdChangeEvent
import com.unciv.ui.screens.worldscreen.chat.Chat.Companion.relayGlobalMessage
import com.unciv.utils.Concurrency
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import java.util.Timer
import kotlin.concurrent.timerTask

// used when sending a message
@Serializable
internal sealed class Message {
    @Serializable
    @SerialName("chat")
    data class Chat(
        val civName: String, val gameId: String, val message: String
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
internal sealed class Response {
    @Serializable
    @SerialName("chat")
    data class Chat(
        val civName: String, val gameId: String, val message: String
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


internal object ChatWebSocketManager {
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

    /*
     * Delivery not guaranted & failures are mosly ignored.
     */
    fun requestMessageSend(message: Message) {
        startSocket()
        if (session === null) return
        Concurrency.run("MultiplayerChatSendMessage") {
            session!!.runCatching {
                this.sendSerialized(message)
            }
        }
    }

    fun handleWebSocketThrowables(t: Throwable) {
        println("ChatError: ${t.message}")

        if (errorReconnectionAttempts == 0)
            relayGlobalMessage("${t.cause}: WebSocket connection closed!")

        if (
            t.cause is WebSocketException &&
            errorReconnectionAttempts == 0 &&
            t.message != null && t.message!!.contains("401")
        ) {
            relayGlobalMessage("WebSocket connection due to authentication issue. You have to set a password to use Chat.")
        }

        restartSocket(true)
    }

    suspend fun startSession() {
        try {
            session = client.webSocketSession {
                url(chatUrl)
                header(HttpHeaders.Authorization, UncivGame.Current.settings.multiplayer.getAuthHeader())
            }

            session!!.runCatching {
                if (isActive) {
                    if (errorReconnectionAttempts == 0) {
                        relayGlobalMessage("Successfully connected to WebSocket server!")
                    } else if (errorReconnectionAttempts > 0) {
                        relayGlobalMessage("Successfully re-established websocket connection!")
                    }
                }

                val gameIds = ChatStore.getGameIds()
                    .union(UncivGame.Current.onlineMultiplayer.games.mapNotNull { it.preview?.gameId })
                this.sendSerialized(Message.Join(gameIds.toList()))

                while (this.isActive) {
                    val response = receiveDeserialized<Response>()
                    when (response) {
                        is Response.Chat -> EventBus.send(
                            ChatMessageReceivedEvent(
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
        if (session != null) return

        job?.cancel()
        job = Concurrency.run("MultiplayerChat") { startSession() }
    }

    private fun restartSocket(dueToError: Boolean = false) {
        if (dueToError) {
            if (errorReconnectionAttempts++ > MAX_ERROR_RECONNECTION_ATTEMPTS) {
                return
            }
        } else errorReconnectionAttempts = 0

        Timer().schedule(timerTask {
            job?.cancel()
            job = Concurrency.run("MultiplayerChat") {
                session?.close()
                startSession()
            }
        }, RECONNECT_TIME_MS)
    }

    init {
        // empty Ids are used to display server & system messages unspecific to any Chat
        eventReceiver.receive(ChatMessageSendRequestEvent::class, { it.gameId.isNotEmpty() }) { event ->
            requestMessageSend(
                Message.Chat(
                    event.civName, event.gameId, event.message
                )
            )
        }

        eventReceiver.receive(ChatMessageReceivedEvent::class) {
            if (it.gameId.isEmpty())
                ChatStore.addGlobalMessage(it.civName, it.message)
            else
                ChatStore.getChatByGameId(it.gameId).addMessage(it.civName, it.message)
        }

        eventReceiver.receive(PasswordChangeEvent::class) {
            if (job != null) restartSocket()
        }

        eventReceiver.receive(UserIdChangeEvent::class) {
            if (job != null) restartSocket()
        }

        eventReceiver.receive(MultiplayerServerUrlChangeEvent::class) {
            if (job != null) restartSocket()
        }
    }
}
