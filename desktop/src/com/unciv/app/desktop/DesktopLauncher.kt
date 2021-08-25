package com.unciv.app.desktop

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import com.badlogic.gdx.Files
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.sun.jna.Native
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.logic.GameSaver
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.ui.utils.Fonts
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.util.*
import kotlin.concurrent.timer

internal object DesktopLauncher {
    private var discordTimer: Timer? = null

    @JvmStatic
    fun main(arg: Array<String>) {
        // Solves a rendering problem in specific GPUs and drivers.
        // For more info see https://github.com/yairm210/Unciv/pull/3202 and https://github.com/LWJGL/lwjgl/issues/119
        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true")

        ImagePacker.packImages()

        val config = LwjglApplicationConfiguration()
        // Don't activate GL 3.0 because it causes problems for MacOS computers
        config.addIcon("ExtraImages/Icon.png", Files.FileType.Internal)
        config.title = "Unciv"
        config.useHDPI = true
        if (FileHandle(GameSaver.settingsFileName).exists()) {
            val settings = JsonParser().getFromJson(GameSettings::class.java, FileHandle(GameSaver.settingsFileName))
            config.width = settings.windowState.width
            config.height = settings.windowState.height
        }

        val versionFromJar = DesktopLauncher.javaClass.`package`.specificationVersion ?: "Desktop"

        val desktopParameters = UncivGameParameters(
                versionFromJar,
                cancelDiscordEvent = { discordTimer?.cancel() },
                fontImplementation = NativeFontDesktop(Fonts.ORIGINAL_FONT_SIZE.toInt()),
                customSaveLocationHelper = CustomSaveLocationHelperDesktop()
        )

        val game = UncivGame(desktopParameters)

        if (!RaspberryPiDetector.isRaspberryPi()) // No discord RPC for Raspberry Pi, see https://github.com/yairm210/Unciv/issues/1624
            tryActivateDiscord(game)

        LwjglApplication(game, config)
    }

    // Work in Progress?
    @Suppress("unused")
    private fun startMultiplayerServer() {
//        val games = HashMap<String, GameSetupInfo>()
        val files = HashMap<String, String>()
        embeddedServer(Netty, 8080) {
            routing {
                get("/files/getFile/{fileName}") {
                    val fileName = call.parameters["fileName"]
                    if (!files.containsKey(fileName)) call.respond(HttpStatusCode.NotFound,
                            "Game with the name $fileName does not exist")
                    else call.respondText(files[fileName]!!)
                }

                post("/files/{fileName}") {
                    val fileName = call.parameters["fileName"]!!
                    val body = call.receiveText()
                    files[fileName] = body
                }
//
//                get("/getGame/{gameName}") {
//                    val gameName = call.parameters["gameName"]
//                    if(!games.containsKey(gameName)) call.respond(HttpStatusCode.NotFound,
//                            "Game with the name $gameName does not exist")
//                    else call.respondText(Json().toJson(games[gameName]))
//                }
//                get("/getGameNames"){
//                    call.respondText(Json().toJson(games.keys.toList()))
//                }
//                post("/addNewGame/{gameName}") {
//                    val gameName = call.parameters["gameName"]!!
//                    if (games.containsKey(gameName)) {
//                        call.respond(HttpStatusCode.NotAcceptable, "A game with the name $gameName already exists")
//                        return@post
//                    }
//                    val body = call.receiveText()
//                    val newGameInfo:GameSetupInfo
//                    try{
//                        newGameInfo = Json().apply { ignoreUnknownFields }.fromJson(GameSetupInfo::class.java, body)
//                    }
//                    catch (ex:Exception){
//                        call.respond(HttpStatusCode.NotAcceptable, "Could not deserialize json")
//                        return@post
//                    }
//                    games[gameName] = newGameInfo
//                }
            }
        }.start()
    }

    private fun tryActivateDiscord(game: UncivGame) {
        try {
            /*
             We try to load the Discord library manually before the instance initializes.
             This is because if there's a crash when the instance initializes on a similar line,
              it's not within the bounds of the try/catch and thus the app will crash.
             */
            Native.loadLibrary("discord-rpc", DiscordRPC::class.java)
            val handlers = DiscordEventHandlers()
            DiscordRPC.INSTANCE.Discord_Initialize("647066573147996161", handlers, true, null)

            Runtime.getRuntime().addShutdownHook(Thread { DiscordRPC.INSTANCE.Discord_Shutdown() })

            discordTimer = timer(name = "Discord", daemon = true, period = 1000) {
                try {
                    updateRpc(game)
                } catch (ex: Exception) {
                }
            }
        } catch (ex: Throwable) {
            // This needs to be a Throwable because if we can't find the discord_rpc library, we'll get a UnsatisfiedLinkError, which is NOT an exception.
            println("Could not initialize Discord")
        }
    }

    private fun updateRpc(game: UncivGame) {
        if (!game.isInitialized) return
        val presence = DiscordRichPresence()
        val currentPlayerCiv = game.gameInfo.getCurrentPlayerCivilization()
        presence.details = currentPlayerCiv.nation.getLeaderDisplayName().tr()
        presence.largeImageKey = "logo" // The actual image is uploaded to the discord app / applications webpage
        presence.largeImageText = "Turn".tr() + " " + currentPlayerCiv.gameInfo.turns
        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence)
    }
}
