package com.unciv.app.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections.synchronizedMap
import java.util.Collections.synchronizedSet
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object UncivServer {
    @JvmStatic
    fun main(args: Array<String>) = UncivServerRunner().main(args)
}

@Serializable
data class IsAliveInfo(val authVersion: Int, val chatVersion: Int)

@Serializable
sealed class Message {
    @Serializable
    @SerialName("chat")
    data class Chat(
        val civName: String, val message: String, val gameId: String? = null
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
        val civName: String, val message: String, val gameId: String? = null
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

private class WebSocketSessionManager {
    private val userId2GameIds = synchronizedMap(mutableMapOf<String, MutableSet<String>>())

    private val gameId2WSSessions =
        synchronizedMap(mutableMapOf<String, MutableSet<DefaultWebSocketServerSession>>())

    fun removeSession(userId: String, session: DefaultWebSocketServerSession) {
        val gameIds = userId2GameIds.remove(userId)
        for (gameId in gameIds ?: emptyList()) {
            gameId2WSSessions[gameId]?.remove(session)
        }
    }

    fun subscribe(userId: String, gameIds: List<String>, session: DefaultWebSocketServerSession) {
        userId2GameIds.getOrPut(userId) { synchronizedSet(mutableSetOf()) }.addAll(gameIds)
        for (gameId in gameIds) {
            gameId2WSSessions.getOrPut(gameId) { synchronizedSet(mutableSetOf()) }.add(session)
        }
    }

    fun unsubscribe(userId: String, gameIds: List<String>, session: DefaultWebSocketServerSession) {
        userId2GameIds[userId]?.removeAll(gameIds)
        for (gameId in gameIds) {
            gameId2WSSessions[gameId]?.remove(session)
        }
    }

    fun hasGameId(userId: String, gameId: String) =
        userId2GameIds.getOrPut(userId) { synchronizedSet(mutableSetOf()) }.contains(gameId)

    suspend fun publish(gameId: String, message: Response) {
        val sessions = gameId2WSSessions.getOrPut(gameId) { synchronizedSet(mutableSetOf()) }
        for (session in sessions) {
            if (!session.isActive) {
                sessions.remove(session)
                continue
            }
            session.sendSerialized(message)
        }
    }
}

data class BasicAuthInfo(
    val userId: String,
    val password: String,
    val isValidUUID: Boolean = false
) : Principal

/**
 * Checks if a [String] is a valid UUID
 */
@OptIn(ExperimentalUuidApi::class)
private fun String.isUUID(): Boolean {
    return try {
        Uuid.parse(this)
        true
    } catch (_: Throwable) {
        false
    }
}

private class UncivServerRunner : CliktCommand() {
    private val port by option(
        "-p", "-port",
        envvar = "UncivServerPort",
        help = "Server port"
    ).int().restrictTo(0..65535).default(8080)

    private val folder by option(
        "-f", "-folder",
        envvar = "UncivServerFolder",
        help = "Multiplayer file's folder"
    ).default("MultiplayerFiles")

    private val authV1Enabled by option(
        "-a", "-auth",
        envvar = "UncivServerAuth",
        help = "Enable Authentication"
    ).flag("-no-auth", default = true)

    private val chatV1Enabled by option(
        "-c", "-chat",
        envvar = "UncivServerChat",
        help = "Enable Authentication"
    ).flag("-no-chat", default = true)

    private val identifyOperators by option(
        "-i", "-Identify",
        envvar = "UncivServerIdentify",
        help = "Display each operation archive request IP to assist management personnel"
    ).flag("-no-Identify", default = false)

    lateinit var isAliveInfo: IsAliveInfo

    override fun run() {
        isAliveInfo = IsAliveInfo(
            authVersion = if (authV1Enabled) 1 else 0,
            chatVersion = if (chatV1Enabled) 1 else 0,
        )
        serverRun(port, folder)
    }

    // region Auth
    private val authMap: MutableMap<String, String> = mutableMapOf()

    private val wsSessionManager = WebSocketSessionManager()

    private fun loadAuthFile() {
        val authFile = File("server.auth")
        if (!authFile.exists()) {
            echo("No server.auth file found, creating one")
            authFile.createNewFile()
        } else {
            authMap.putAll(
                authFile.readLines().map { it.split(":") }.associate { it[0] to it[1] }
            )
        }
    }

    private fun saveAuthFile() {
        val authFile = File("server.auth")
        authFile.writeText(authMap.map { "${it.key}:${it.value}" }.joinToString("\n"))
    }

    /**
     * @return true if either auth is disabled, no password is set for the current player,
     * or the password is correct
     */
    private fun validateGameAccess(file: File, authInfo: BasicAuthInfo): Boolean {
        if (!file.exists())
            return true

        return validateAuth(authInfo)

        // TODO Check if the user is the current player and validate its password this requires decoding the game file
    }

    private fun validateAuth(authInfo: BasicAuthInfo): Boolean {
        if (!authV1Enabled)
            return true

        val password = authMap[authInfo.userId]
        return password == null || password == authInfo.password
    }
    // endregion Auth

    private fun serverRun(serverPort: Int, fileFolderName: String) {
        val portStr: String = if (serverPort == 80) "" else ":$serverPort"

        val file = File(fileFolderName)
        echo("Starting UncivServer for ${file.absolutePath} on http://localhost$portStr")
        if (!file.exists()) file.mkdirs()
        val server = embeddedServer(Netty, port = serverPort) {
            install(ContentNegotiation) { json() }

            install(Authentication) {
                basic {
                    realm = "Optional for /files and /auth, Mandatory for /chat"

                    @OptIn(ExperimentalUuidApi::class)
                    validate { creds ->
                        val isValidUUID = creds.name.isUUID()
                        BasicAuthInfo(creds.name, creds.password, isValidUUID)
                    }
                }
            }

            if (chatV1Enabled) install(WebSockets) {
                pingPeriodMillis = 30_000
                timeoutMillis = 60_000
                maxFrameSize = Long.MAX_VALUE
                @OptIn(ExperimentalSerializationApi::class)
                contentConverter = KotlinxWebsocketSerializationConverter(Json {
                    classDiscriminator = "type"
                    // DO NOT OMIT
                    // if omitted the "type" field will be missing from all outgoing messages
                    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
                })
            }

            routing {
                get("/isalive") {
                    call.application.log.info("Received isalive request from ${call.request.local.remoteHost}")
                    call.respond(isAliveInfo)
                }

                authenticate {
                    put("/files/{fileName}") {
                        val fileName = call.parameters["fileName"] ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing filename!"
                        )

                        val authInfo = call.principal<BasicAuthInfo>() ?: return@put call.respond(
                            HttpStatusCode.BadRequest,
                            "Possibly malformed authentication header!"
                        )

                        if (!authInfo.isValidUUID)
                            return@put call.respond(HttpStatusCode.BadRequest, "Bad userId")

                        // If IdentifyOperators is enabled an Operator IP is displayed
                        if (identifyOperators) {
                            call.application.log.info("Receiving file: $fileName --Operation sourced from ${call.request.local.remoteHost}")
                        } else {
                            call.application.log.info("Receiving file: $fileName")
                        }

                        val file = File(fileFolderName, fileName)
                        if (!validateGameAccess(file, authInfo))
                            return@put call.respond(HttpStatusCode.Unauthorized)

                        withContext(Dispatchers.IO) {
                            file.outputStream().use {
                                call.request.receiveChannel().toInputStream().copyTo(it)
                            }
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/files/{fileName}") {
                        val fileName = call.parameters["fileName"] ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing filename!"
                        )

                        // If IdentifyOperators is enabled an Operator IP is displayed
                        if (identifyOperators) {
                            call.application.log.info("File requested: $fileName --Operation sourced from ${call.request.local.remoteHost}")
                        } else {
                            call.application.log.info("File requested: $fileName")
                        }

                        val file = File(fileFolderName, fileName)
                        if (!file.exists()) {
                            // If IdentifyOperators is enabled an Operator IP is displayed
                            if (identifyOperators) {
                                call.application.log.info("File $fileName not found --Operation sourced from ${call.request.local.remoteHost}")
                            } else {
                                call.application.log.info("File $fileName not found")
                            }

                            return@get call.respond(HttpStatusCode.NotFound, "File does not exist")
                        }

                        val fileText = withContext(Dispatchers.IO) { file.readText() }
                        call.respondText(fileText)
                    }
                    if (authV1Enabled) {
                        get("/auth") {
                            call.application.log.info("Received auth request from ${call.request.local.remoteHost}")

                            val authInfo =
                                call.principal<BasicAuthInfo>() ?: return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    "Possibly malformed authentication header!"
                                )

                            if (!authInfo.isValidUUID)
                                return@get call.respond(HttpStatusCode.BadRequest, "Bad userId")

                            when (authMap[authInfo.userId]) {
                                null -> call.respond(HttpStatusCode.NoContent)
                                authInfo.password -> call.respond(HttpStatusCode.OK)
                                else -> call.respond(HttpStatusCode.Unauthorized)
                            }
                        }
                        put("/auth") {
                            call.application.log.info("Received auth password set from ${call.request.local.remoteHost}")

                            val authInfo =
                                call.principal<BasicAuthInfo>() ?: return@put call.respond(
                                    HttpStatusCode.BadRequest,
                                    "Possibly malformed authentication header!"
                                )

                            if (!authInfo.isValidUUID)
                                return@put call.respond(HttpStatusCode.BadRequest, "Bad userId")

                            val password = authMap[authInfo.userId]
                            if (password == null || password == authInfo.password) {
                                val newPassword = call.receiveText()
                                if (newPassword.length < 6)
                                    return@put call.respond(
                                        HttpStatusCode.BadRequest,
                                        "Password should be at least 6 characters long"
                                    )
                                authMap[authInfo.userId] = newPassword
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.Unauthorized)
                            }
                        }
                    }

                    if (chatV1Enabled) webSocket("/chat") {
                        val authInfo = call.principal<BasicAuthInfo>()
                        if (authInfo == null) {
                            sendSerialized(Response.Error("No authentication info found!"))
                            return@webSocket close()
                        }

                        val serverPassword = authMap[authInfo.userId]
                        if (serverPassword == null || serverPassword != authInfo.password) {
                            sendSerialized(Response.Error("Authentication failed!"))
                            return@webSocket close()
                        }

                        val userId = authInfo.userId
                        try {
                            while (true) {
                                val message = receiveDeserialized<Message>()
                                when (message) {
                                    is Message.Chat -> {
                                        if (message.gameId == null) {
                                            sendSerialized(
                                                Response.Chat(
                                                    civName = "Server",
                                                    message = "No gameId found. Cannot relay the message!",
                                                )
                                            )
                                            continue
                                        }

                                        if (!message.gameId.isUUID()) {
                                            sendSerialized(
                                                Response.Chat(
                                                    civName = "Server",
                                                    message = "Invalid gameId: '${message.gameId}'. Cannot relay the message!",
                                                )
                                            )
                                            continue
                                        }

                                        if (wsSessionManager.hasGameId(userId, message.gameId)) {
                                            wsSessionManager.publish(
                                                gameId = message.gameId,
                                                message = Response.Chat(
                                                    civName = message.civName,
                                                    message = message.message,
                                                    gameId = message.gameId,
                                                )
                                            )
                                        } else {
                                            sendSerialized(Response.Error("You are not subscribed to this channel!"))
                                        }
                                    }

                                    is Message.Join -> {
                                        wsSessionManager.subscribe(userId, message.gameIds, this)
                                        sendSerialized(Response.JoinSuccess(gameIds = message.gameIds))
                                    }

                                    is Message.Leave ->
                                        wsSessionManager.unsubscribe(userId, message.gameIds, this)
                                }
                                yield()
                            }
                        } catch (err: Throwable) {
                            println("An WebSocket session closed due to ${err.message}")
                            wsSessionManager.removeSession(userId, this)
                        } finally {
                            println("An WebSocket session closed normally.")
                            wsSessionManager.removeSession(userId, this)
                        }
                    }
                }
            }
        }.start(wait = false)

        if (authV1Enabled) {
            loadAuthFile()
        }

        echo("Server running on http://localhost$portStr! Press Ctrl+C to stop")
        Runtime.getRuntime().addShutdownHook(Thread {
            echo("Shutting down server...")

            if (authV1Enabled) {
                saveAuthFile()
            }

            server.stop(1, 5, TimeUnit.SECONDS)
        })
        Thread.currentThread().join()
    }
}
