package com.unciv.app.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit


internal object UncivServer {
    @JvmStatic
    fun main(args: Array<String>) = UncivServerRunner().main(args)
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
    ).flag("-no-auth", default = false)

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
        if (!authV1Enabled || !file.exists())
            return true

        // If auth is enabled, an auth string is required
        if (authString == null || !authString.startsWith("Basic "))
            return false

        // Extract the user id and password from the auth string
        val (userId, password) = authString.drop(6).split(":")

        if (authMap[userId] == null || authMap[userId] == password)
            return true

        return false

        // TODO Check if the user is the current player and validate its password this requires decoding the game file
    }

    private fun validateAuth(authString: String?): Boolean {
        if (!authV1Enabled)
            return true
        // If auth is enabled a auth string is required
        if (authString == null || !authString.startsWith("Basic "))
            return false

        val (userId, password) = authString.drop(6).split(":")
        if (authMap[userId] == null || authMap[userId] == password)
            return true
        return false
    }
    // endregion Auth

    private fun serverRun(serverPort: Int, fileFolderName: String) {
        val portStr: String = if (serverPort == 80) "" else ":$serverPort"
        echo("Starting UncivServer for ${File(fileFolderName).absolutePath} on http://localhost$portStr")
        val server = embeddedServer(Netty, port = serverPort) {
            routing {
                get("/isalive") {
                    log.info("Received isalive request from ${call.request.local.remoteHost}")
                    call.respondText("{authVersion: ${if (authV1Enabled) "1" else "0"}}")
                }
                put("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    log.info("Receiving file: ${fileName}")
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
                    log.info("File requested: $fileName")
                    val file = File(fileFolderName, fileName)
                    if (!file.exists()) {
                        log.info("File $fileName not found")
                        call.respond(HttpStatusCode.NotFound, "File does not exist")
                        return@get
                    }
                    val fileText = withContext(Dispatchers.IO) { file.readText() }

                    call.respondText(fileText)
                }
                if (authV1Enabled) {
                    get("/auth") {
                        log.info("Received auth request from ${call.request.local.remoteHost}")
                        val authHeader = call.request.headers["Authorization"]
                        if (validateAuth(authHeader)) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Unauthorized)
                        }
                    }
                    put("/auth") {
                        log.info("Received auth password set from ${call.request.local.remoteHost}")
                        val authHeader = call.request.headers["Authorization"]
                        if (validateAuth(authHeader)) {
                            val userId = authHeader?.drop(6)?.split(":")?.get(0)
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
