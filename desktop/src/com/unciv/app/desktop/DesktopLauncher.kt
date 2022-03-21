package com.unciv.app.desktop

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.sun.jna.Native
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.logic.GameSaver
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.utils.Fonts
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

        val config = Lwjgl3ApplicationConfiguration()
        config.setWindowIcon("ExtraImages/Icon.png")
        config.setTitle("Unciv")
        config.setHdpiMode(HdpiMode.Logical)
        config.setWindowSizeLimits(120, 80, -1, -1)

        val settings = GameSettings.getSettingsForPlatformLaunchers()
        if (!settings.isFreshlyCreated) {
            config.setWindowedMode(settings.windowState.width.coerceAtLeast(120), settings.windowState.height.coerceAtLeast(80))
        }

        val versionFromJar = DesktopLauncher.javaClass.`package`.specificationVersion ?: "Desktop"

        if (versionFromJar == "Desktop") {
            UniqueDocsWriter().write()
        }

        val desktopParameters = UncivGameParameters(
            versionFromJar,
            cancelDiscordEvent = { discordTimer?.cancel() },
            fontImplementation = NativeFontDesktop(Fonts.ORIGINAL_FONT_SIZE.toInt(), settings.fontFamily),
            customSaveLocationHelper = CustomSaveLocationHelperDesktop()
        )

        val game = UncivGame(desktopParameters)

        tryActivateDiscord(game)
        Lwjgl3Application(game, config)
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
        presence.details = "${currentPlayerCiv.nation.leaderName} of ${currentPlayerCiv.nation.name}"
        presence.largeImageKey = "logo" // The actual image is uploaded to the discord app / applications webpage
        presence.largeImageText = "Turn" + " " + currentPlayerCiv.gameInfo.turns
        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence)
    }
}
