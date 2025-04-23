package com.unciv.server

import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    Database.connect("jdbc:sqlite:unciv.db", driver = "org.sqlite.JDBC")

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }
        install(StatusPages) {
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.localizedMessage)
            }
        }
        routing {
            route("/auth") {
                post("/register") {
                    val request = call.receive<RegisterRequest>()
                    val result = transaction {
                        AuthService.register(request.username, request.password)
                    }
                    call.respond(result)
                }
                post("/login") {
                    val request = call.receive<LoginRequest>()
                    val result = transaction {
                        AuthService.login(request.username, request.password)
                    }
                    call.respond(result)
                }
            }
            route("/game") {
                post("/save") {
                    val request = call.receive<SaveGameRequest>()
                    val result = transaction {
                        GameService.saveGame(request.username, request.gameData)
                    }
                    call.respond(result)
                }
                get("/load/{username}") {
                    val username = call.parameters["username"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val result = transaction {
                        GameService.loadGame(username)
                    }
                    call.respond(result)
                }
            }
        }
    }.start(wait = true)
}

data class RegisterRequest(val username: String, val password: String)
data class LoginRequest(val username: String, val password: String)
data class SaveGameRequest(val username: String, val gameData: String)
