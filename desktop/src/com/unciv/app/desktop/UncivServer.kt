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
    @JvmStatic
    fun main(arg: Array<String>) {
        val fileFolderName = "MultiplayerFiles"
        File(fileFolderName).mkdir()
        println(File(fileFolderName).absolutePath)
        embeddedServer(Netty, port = 8080) {
            routing {
                get("/isalive") {
                    call.respondText("true")
                }
                post("/files/{fileName}") {
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