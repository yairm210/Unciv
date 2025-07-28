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
        val civName: String, val message: String, val gameId: String
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

@OptIn(ExperimentalUuidApi::class)
private class WebSocketSessionManager {
    private val gameId2WSSessions = synchronizedMap(mutableMapOf<Uuid, MutableSet<DefaultWebSocketServerSession>>())
    private val wsSession2GameIds = synchronizedMap(mutableMapOf<DefaultWebSocketServerSession, MutableSet<Uuid>>())

    fun isSubscribed(session: DefaultWebSocketServerSession, gameId: String): Boolean = try {
        val gameId = Uuid.parse(gameId)
        gameId2WSSessions.getOrPut(gameId) { synchronizedSet(mutableSetOf()) }.contains(session)
    } catch (_: Throwable) {
        false
    }

    fun subscribe(session: DefaultWebSocketServerSession, gameIds: List<String>): List<String> {
        val uuids = gameIds.mapNotNull { gameId ->
            return@mapNotNull try {
                Uuid.parse(gameId)
            } catch (_: Throwable) {
                null
            }
        }

        wsSession2GameIds.getOrPut(session) { synchronizedSet(mutableSetOf()) }.addAll(uuids)
        for (uuid in uuids) {
            gameId2WSSessions.getOrPut(uuid) { synchronizedSet(mutableSetOf()) }.add(session)
        }

        return uuids.map { it.toString() }
    }

    fun unsubscribe(session: DefaultWebSocketServerSession, gameIds: List<String>) {
        val uuids = gameIds.mapNotNull { gameId ->
            return@mapNotNull try {
                Uuid.parse(gameId)
            } catch (_: Throwable) {
                null
            }
        }

        wsSession2GameIds[session]?.removeAll(uuids)
        for (uuid in uuids) {
            gameId2WSSessions[uuid]?.remove(session)
        }
    }

    suspend fun publish(gameId: String, message: Response) {
        val gameId = try {
            Uuid.parse(gameId)
        } catch (_: Throwable) {
            return
        }

        val sessions = gameId2WSSessions.getOrPut(gameId) { synchronizedSet(mutableSetOf()) }
        for (session in sessions) {
            if (!session.isActive) {
                sessions.remove(session)
                continue
            }
            session.sendSerialized(message)
        }
    }

    fun cleanupSession(session: DefaultWebSocketServerSession) {
        for (gameId in wsSession2GameIds.remove(session) ?: emptyList()) {
            val gameIds = gameId2WSSessions[gameId] ?: continue
            gameIds.remove(session)
            if (gameIds.isEmpty()) {
                gameId2WSSessions.remove(gameId)
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
data class BasicAuthInfo(
    val userId: Uuid,
    val password: String,
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
    @OptIn(ExperimentalUuidApi::class)
    private val authMap: MutableMap<Uuid, String> = mutableMapOf()

    private val wsSessionManager = WebSocketSessionManager()

    @OptIn(ExperimentalUuidApi::class)
    private fun loadAuthFile() {
        val authFile = File("server.auth")
        if (!authFile.exists()) {
            echo("No server.auth file found, creating one")
            authFile.createNewFile()
        } else {
            authMap.putAll(
                authFile.readLines().map { it.split(":") }
                    .associate { Uuid.parse(it[0]) to it[1] }
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
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
        if (!authV1Enabled) return true

        @OptIn(ExperimentalUuidApi::class) val password = authMap[authInfo.userId]
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

                    @OptIn(ExperimentalUuidApi::class) validate { creds ->
                        return@validate try {
                            BasicAuthInfo(userId = Uuid.parse(creds.name), password = creds.password)
                        } catch (_: Throwable) {
                            null
                        }
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

                @OptIn(ExperimentalUuidApi::class) authenticate {
                    put("/files/{fileName}") {
                        val fileName = call.parameters["fileName"] ?: return@put call.respond(
                            HttpStatusCode.BadRequest, "Missing filename!"
                        )

                        val authInfo = call.principal<BasicAuthInfo>() ?: return@put call.respond(
                            HttpStatusCode.BadRequest, "Possibly malformed authentication header!"
                        )

                        // If IdentifyOperators is enabled an Operator IP is displayed
                        if (identifyOperators) {
                            call.application.log.info("Receiving file: $fileName --Operation sourced from ${call.request.local.remoteHost}")
                        } else {
                            call.application.log.info("Receiving file: $fileName")
                        }

                        val file = File(fileFolderName, fileName)
                        if (!validateGameAccess(file, authInfo)) return@put call.respond(HttpStatusCode.Unauthorized)

                        withContext(Dispatchers.IO) {
                            file.outputStream().use {
                                call.request.receiveChannel().toInputStream().copyTo(it)
                            }
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/files/{fileName}") {
                        val fileName = call.parameters["fileName"] ?: return@get call.respond(
                            HttpStatusCode.BadRequest, "Missing filename!"
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

                            val authInfo = call.principal<BasicAuthInfo>() ?: return@get call.respond(
                                HttpStatusCode.BadRequest, "Possibly malformed authentication header!"
                            )

                            when (authMap[authInfo.userId]) {
                                null -> call.respond(HttpStatusCode.NoContent)
                                authInfo.password -> call.respond(HttpStatusCode.OK)
                                else -> call.respond(HttpStatusCode.Unauthorized)
                            }
                        }
                        put("/auth") {
                            call.application.log.info("Received auth password set from ${call.request.local.remoteHost}")

                            val authInfo = call.principal<BasicAuthInfo>() ?: return@put call.respond(
                                HttpStatusCode.BadRequest, "Possibly malformed authentication header!"
                            )

                            val password = authMap[authInfo.userId]
                            if (password == null || password == authInfo.password) {
                                val newPassword = call.receiveText()
                                if (newPassword.length < 6) return@put call.respond(
                                    HttpStatusCode.BadRequest, "Password should be at least 6 characters long"
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

                        try {
                            while (isActive) {
                                val message = receiveDeserialized<Message>()
                                when (message) {
                                    is Message.Chat -> {
                                        if (!message.gameId.isUUID()) {
                                            sendSerialized(
                                                Response.Chat(
                                                    civName = "Server",
                                                    message = "Invalid gameId: '${message.gameId}'. Cannot relay the message!",
                                                )
                                            )
                                            continue
                                        }

                                        if (wsSessionManager.isSubscribed(this, message.gameId)) {
                                            wsSessionManager.publish(
                                                gameId = message.gameId, message = Response.Chat(
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
                                        sendSerialized(
                                            Response.JoinSuccess(
                                                gameIds = wsSessionManager.subscribe(
                                                    this, message.gameIds
                                                )
                                            )
                                        )
                                    }

                                    is Message.Leave -> wsSessionManager.unsubscribe(this, message.gameIds)
                                }
                                yield()
                            }
                        } catch (err: Throwable) {
                            println("An WebSocket session closed due to ${err.message}")
                            wsSessionManager.cleanupSession(this)
                        } finally {
                            println("An WebSocket session closed normally.")
                            wsSessionManager.cleanupSession(this)
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
