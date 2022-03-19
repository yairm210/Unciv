package com.unciv.app.desktop
import io.ktor.application.*

import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val fileFolderName = "MultiplayerFiles"
    embeddedServer(Netty, port = 8080) {
        routing {
            get("/isalive") {
                call.respondText("true")
            }
        }
    }.start(wait = true)
}