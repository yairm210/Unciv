package com.unciv.app.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


internal object UncivServer {
    @JvmStatic
    fun main(args: Array<String>) = UncivServerRunner().main(args)
}

private class UncivServerRunner : CliktCommand() {
    private val port by option(
        "-p", "-port",
        envvar = "UncivServerPort",
        help = "Server port"
    ).int().restrictTo(1024..49151).default(80)

    private val folder by option(
        "-f", "-folder",
        envvar = "UncivServerFolder",
        help = "Multiplayer file's folder"
    ).default("MultiplayerFiles")

    override fun run() {
        serverRun(port, folder)
    }

    private fun serverRun(serverPort: Int, fileFolderName: String) {
        echo("Starting UncivServer for ${File(fileFolderName).absolutePath} on port $serverPort")
        embeddedServer(Netty, port = serverPort) {
            routing {
                get("/isalive") {
                    log.info("Received isalive request from ${call.request.local.remoteHost}")
                    call.respondText("true")
                }
                put("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    log.info("Receiving file: ${fileName}")
                    val file = File(fileFolderName, fileName)
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
                delete("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    log.info("Deleting file: $fileName")
                    val file = File(fileFolderName, fileName)
                    if (!file.exists()) {
                        call.respond(HttpStatusCode.NotFound, "File does not exist")
                        return@delete
                    }
                    file.delete()
                }
            }
        }.start(wait = true)
    }
}
