package com.unciv.app.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ktor.application.*
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
                    println("Received isalive request from ${call.request.local.remoteHost}")
                    call.respondText("true")
                }
                put("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    withContext(Dispatchers.IO) {
                        val receivedBytes =
                            call.request.receiveChannel().toInputStream().readBytes()
                        val textString = String(receivedBytes)
                        println("Received text: $textString")
                        File(fileFolderName, fileName).writeText(textString)
                    }
                }
                get("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    println("Get file: $fileName")
                    val file = File(fileFolderName, fileName)
                    if (!file.exists()) throw Exception("File does not exist!")
                    val fileText = file.readText()
                    println("Text read: $fileText")
                    call.respondText(fileText)
                }
                delete("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    val file = File(fileFolderName, fileName)
                    if (!file.exists()) throw Exception("File does not exist!")
                    file.delete()
                }
            }
        }.start(wait = true)
    }
}