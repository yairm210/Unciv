package com.unciv.ui.screens.worldscreen.chat

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.PasswordChangeEvent
import com.unciv.ui.popups.options.MultiplayerServerUrlChangeEvent
import com.unciv.ui.popups.options.UserIdChangeEvent
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
import kotlin.io.encoding.ExperimentalEncodingApi

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
    private var reconnect = true
    private const val RECONNECT_TIME_MS: Long = 5_000

    private lateinit var job: Job
    private lateinit var client: HttpClient
    private val eventReceiver = EventBus.EventReceiver()
    private var session: DefaultClientWebSocketSession? = null

    private val chatUrl
        get() = URLBuilder(
            UncivGame.Current.onlineMultiplayer.multiplayerServer.getServerUrl()
        ).apply {
            appendPathSegments("chat")
            protocol = if (protocol.isSecure()) URLProtocol.WSS else URLProtocol.WS
        }.build()

    @OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
    private fun initializeClient() {
        // client only needs to be initialized once
        if (::client.isInitialized) return

        client = HttpClient(CIO) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json {
                    classDiscriminator = "type"
                    // DO NOT OMIT
                    // if omitted the "type" field will be missing from all outgoing messages
                    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
                })
            }
        }
    }

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

    suspend fun startSession() {
        try {
            session = client.webSocketSession {
                url(chatUrl)
                header(HttpHeaders.Authorization, UncivGame.Current.settings.multiplayer.getAuthHeader())
            }

            session!!.runCatching {
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

                        is Response.Error -> Unit
                        is Response.JoinSuccess -> Unit
                    }
                }
            }
                .onSuccess { restartSocket() }
                .onFailure { restartSocket() }
        } catch (e: Exception) {
            println("${e.cause} - ${e.message}")
            restartSocket()
        }
    }

    private fun startSocket() {
        if (session !== null) return

        initializeClient()
        job = Concurrency.run("MultiplayerChat") { startSession() }
    }

    private fun restartSocket() {
        if (::job.isInitialized) job.cancel()
        Timer().schedule(timerTask {
            job = Concurrency.run("MultiplayerChat") {
                session?.close()
                startSession()
            }
        }, 2000)
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

        eventReceiver.receive(PasswordChangeEvent::class) {
            if (session !== null) restartSocket()
        }

        eventReceiver.receive(UserIdChangeEvent::class) {
            if (session !== null) restartSocket()
        }

        eventReceiver.receive(MultiplayerServerUrlChangeEvent::class) {
            if (session !== null) restartSocket()
        }
    }
}
