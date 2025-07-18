package com.unciv.app.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal object UncivServer {
    @JvmStatic
    fun main(args: Array<String>) = UncivServerRunner().main(args)
}

@Serializable
data class IsAliveInfo(val authVersion: Int)

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
    ).flag("-no-auth", default = false)

     private val identifyOperators by option(
        "-i", "-Identify",
        envvar = "UncivServerIdentify",
        help = "Display each operation archive request IP to assist management personnel"
    ).flag("-no-Identify", default = false)


    override fun run() {
        serverRun(port, folder)
    }

    // region Auth
    private val authMap: MutableMap<String, String> = mutableMapOf()

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
    private fun validateGameAccess(file: File, authString: String?): Boolean {
        if (!file.exists())
            return true

        return validateAuth(authString)

        // TODO Check if the user is the current player and validate its password this requires decoding the game file
    }

    private fun validateAuth(authString: String?): Boolean {
        if (!authV1Enabled)
            return true

        val (userId, password) = extractAuth(authString) ?: return false
        return authMap[userId] == null || authMap[userId] == password
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun extractAuth(authHeader: String?): Pair<String, String>? {
        if (!authV1Enabled)
            return null

        // If auth is enabled an authorization header is required
        if (authHeader == null || !authHeader.startsWith("Basic "))
            return null

        val decodedString = Base64.Default.decode(authHeader.drop(6)).decodeToString()
        val splitAuthString = decodedString.split(":", limit = 2)
        if (splitAuthString.size != 2)
            return null

        return splitAuthString.let { it[0] to it[1] }
    }
    // endregion Auth

    private fun serverRun(serverPort: Int, fileFolderName: String) {
        val portStr: String = if (serverPort == 80) "" else ":$serverPort"
        val isAliveInfo = IsAliveInfo(authVersion = if (authV1Enabled) 1 else 0)

        val file = File(fileFolderName)
        echo("Starting UncivServer for ${file.absolutePath} on http://localhost$portStr")
        if (!file.exists()) file.mkdirs()
        val server = embeddedServer(Netty, port = serverPort) {
            install(ContentNegotiation) { json() }

            routing {
                get("/isalive") {
                    call.application.log.info("Received isalive request from ${call.request.local.remoteHost}")
                    call.respond(isAliveInfo)
                }
                put("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    
                    // If IdentifyOperators is enabled an Operator IP is displayed
                    if (identifyOperators) {
                        call.application.log.info("Receiving file: $fileName --Operation sourced from ${call.request.local.remoteHost}")
                    } else {
                        call.application.log.info("Receiving file: $fileName")
                    }
                     
                    val file = File(fileFolderName, fileName)

                    if (!validateGameAccess(file, call.request.headers["Authorization"])) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@put
                    }

                    withContext(Dispatchers.IO) {
                        file.outputStream().use {
                            call.request.receiveChannel().toInputStream().copyTo(it)
                        }
                    }
                    call.respond(HttpStatusCode.OK)
                }
                get("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    
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
                        call.respond(HttpStatusCode.NotFound, "File does not exist")
                        return@get
                    }
                    val fileText = withContext(Dispatchers.IO) { file.readText() }

                    call.respondText(fileText)
                }
                if (authV1Enabled) {
                    get("/auth") {
                        call.application.log.info("Received auth request from ${call.request.local.remoteHost}")

                        val authHeader = call.request.headers["Authorization"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, "Missing authorization header!")
                            return@get
                        }

                        val (userId, password) = extractAuth(authHeader) ?: run {
                            call.respond(HttpStatusCode.BadRequest, "Malformed authorization header!")
                            return@get
                        }

                        when (authMap[userId]) {
                            null -> call.respond(HttpStatusCode.NoContent)
                            password -> call.respond(HttpStatusCode.OK)
                            else -> call.respond(HttpStatusCode.Unauthorized)
                        }
                    }
                    put("/auth") {
                        call.application.log.info("Received auth password set from ${call.request.local.remoteHost}")
                        val authHeader = call.request.headers["Authorization"]
                        if (validateAuth(authHeader)) {
                            val (userId, _) = extractAuth(authHeader) ?: Pair(null, null)
                            if (userId != null) {
                                authMap[userId] = call.receiveText()
                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.BadRequest)
                            }
                        } else {
                            call.respond(HttpStatusCode.Unauthorized)
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
