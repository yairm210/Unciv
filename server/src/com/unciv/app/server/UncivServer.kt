package com.unciv.app.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ktor.application.call
import io.ktor.application.log
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.put
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
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

     @Suppress("PrivatePropertyName") // Part of API spec
     private val IdentifyOperators by option(
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

    private fun extractAuth(authString: String?): Pair<String, String>? {
        if (!authV1Enabled)
            return null

        // If auth is enabled a auth string is required
        if (authString == null || !authString.startsWith("Basic "))
            return null

        val decodedString = String(Base64.getDecoder().decode(authString.drop(6)))
        val splitAuthString = decodedString.split(":", limit=2)
        if (splitAuthString.size != 2)
            return null

        return splitAuthString.let { it[0] to it[1] }
    }
    // endregion Auth

    private fun serverRun(serverPort: Int, fileFolderName: String) {
        val portStr: String = if (serverPort == 80) "" else ":$serverPort"

        val fileFolder = File(fileFolderName)
        echo("Starting UncivServer for ${fileFolder.absolutePath} on http://localhost$portStr")
        if (!fileFolder.exists()) fileFolder.mkdirs()
        val server = embeddedServer(Netty, port = serverPort) {
            routing {
                get("/isalive") {
                    log.info("Received isalive request from ${call.request.local.remoteHost}")
                    call.respondText("{authVersion: ${if (authV1Enabled) "1" else "0"}}")
                }
                put("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")

                    // If IdentifyOperators is enabled a Operator IP is displayed
                    if (IdentifyOperators) {
                        log.info("Receiving file: $fileName --Operation sourced from ${call.request.local.remoteHost}")
                    }else{
                        log.info("Receiving file: $fileName")
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

                    // If IdentifyOperators is enabled a Operator IP is displayed
                    if (IdentifyOperators) {
                        log.info("File requested: $fileName --Operation sourced from ${call.request.local.remoteHost}")
                    }else{
                        log.info("File requested: $fileName")
                    }

                    val file = File(fileFolderName, fileName)
                    if (!file.exists()) {

                        // If IdentifyOperators is enabled a Operator IP is displayed
                        if (IdentifyOperators) {
                            log.info("File $fileName not found --Operation sourced from ${call.request.local.remoteHost}")
                        }else{
                            log.info("File $fileName not found")
                        }
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
