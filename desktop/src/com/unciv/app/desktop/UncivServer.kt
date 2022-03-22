package com.unciv.app.desktop

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
    private var serverPort = 8080

    @JvmStatic
    fun main(args: Array<String>) {
        args.forEach { arg ->
            when {
                arg.startsWith("-port=") -> with(arg.removePrefix("-port=").toIntOrNull() ?: 0) {
                    if (this in 1.rangeTo(65535)) serverPort = this
                    else println("'port' must be between 1 and 65535")
                }
            }
        }

        println("Server will run on $serverPort port, you can use '-port=XXXX' custom port.")

        val fileFolderName = "MultiplayerFiles"
        File(fileFolderName).mkdir()
        println(File(fileFolderName).absolutePath)
        embeddedServer(Netty, port = serverPort) {
            routing {
                get("/isalive") {
                    call.respondText("true")
                }
                put("/files/{fileName}") {
                    val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                    withContext(Dispatchers.IO) {
                        val recievedBytes =
                            call.request.receiveChannel().toInputStream().readAllBytes()
                        val textString = String(recievedBytes)
                        println("Recieved text: $textString")
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