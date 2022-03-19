package com.unciv.app.desktop
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import io.ktor.application.*

import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.cio.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File
import kotlin.Exception

fun main() {
    val fileFolderName = "MultiplayerFiles"
    embeddedServer(Netty, port = 8080) {
        routing {
            get("/isalive") {
                call.respondText("true")
            }
            post("/files/{fileName}"){
                val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                val recievedText = call.request.receiveChannel().toInputStream().toString()
                File(fileFolderName, fileName).writeText(recievedText)
            }
            get("/files/{fileName}"){
                val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                val file = File(fileFolderName, fileName)
                if (!file.exists()) throw Exception("File does not exist!")
                call.respondText(file.readText())
            }
            delete("/files/{fileName}"){
                val fileName = call.parameters["fileName"] ?: throw Exception("No fileName!")
                val file = File(fileFolderName, fileName)
                if (!file.exists()) throw Exception("File does not exist!")
                file.delete()
            }
        }
    }.start(wait = true)
}