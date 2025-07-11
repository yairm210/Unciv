package com.unciv.ui.screens.worldscreen.chat

import com.unciv.UncivGame
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.WebSocketDeflateExtension
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import java.util.zip.Deflater.BEST_COMPRESSION
import kotlin.io.encoding.Base64
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


internal object ChatConnectionManager {
    private lateinit var client: HttpClient

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
                extensions {
                    // mostly useless but why not
                    install(WebSocketDeflateExtension) {
                        // in this case the time does not really matter
                        compressionLevel = BEST_COMPRESSION
                        // only compress if outgoing frame is larger than 128 bytes
                        compressIfBiggerThan(bytes = 128)
                    }
                }
            }
            defaultRequest {
                val userId = UncivGame.Current.settings.multiplayer.userId
                // cant there be a better way to access passwords?
                val password = UncivGame.Current.settings.multiplayer.passwords.getOrDefault(
                    UncivGame.Current.onlineMultiplayer.multiplayerServer.getServerUrl(), ""
                )
                val authString = "Basic ${Base64.Default.encode("$userId:$password".toByteArray())}"
                header(HttpHeaders.Authorization, authString)
            }
        }
    }

    private fun runSocket() {

    }

    private fun init() {
        initializeClient()
    }
}
