package com.unciv.app.desktop

import club.minnced.discord.rpc.DiscordEventHandlers
import club.minnced.discord.rpc.DiscordRPC
import club.minnced.discord.rpc.DiscordRichPresence
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.sun.jna.Native
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.SETTINGS_FILE_NAME
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.WindowState
import com.unciv.ui.utils.Fonts
import com.unciv.utils.Log
import com.unciv.utils.debug
import java.awt.Toolkit
import java.io.File
import java.util.*
import kotlin.concurrent.timer

internal object DesktopLauncher {
    private var discordTimer: Timer? = null

    @JvmStatic
    fun main(arg: Array<String>) {
        Log.backend = DesktopLogBackend()
        // Solves a rendering problem in specific GPUs and drivers.
        // For more info see https://github.com/yairm210/Unciv/pull/3202 and https://github.com/LWJGL/lwjgl/issues/119
        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true")
        // This setting (default 64) limits clipboard transfers. Value in kB!
        // 386 is an almost-arbitrary choice from the saves I had at the moment and their GZipped size.
        // There must be a reason for lwjgl3 being so stingy, which for me meant to stay conservative.
        System.setProperty("org.lwjgl.system.stackSize", "384")

        val isRunFromJAR = DesktopLauncher.javaClass.`package`.specificationVersion != null
        ImagePacker.packImages(isRunFromJAR)

        val config = Lwjgl3ApplicationConfiguration()
        config.setWindowIcon("ExtraImages/Icon.png")
        config.setTitle("Unciv")
        config.setHdpiMode(HdpiMode.Logical)
        config.setWindowSizeLimits(120, 80, -1, -1)

        // We don't need the initial Audio created in Lwjgl3Application, HardenGdxAudio will replace it anyway.
        // Note that means config.setAudioConfig() would be ignored too, those would need to go into the HardenedGdxAudio constructor.
        config.disableAudio(true)

        val settings = getSettingsBeforeGdxInitialized()
        config.setWindowedMode(settings.windowState.width.coerceAtLeast(120), settings.windowState.height.coerceAtLeast(80))

        if (!isRunFromJAR) {
            UniqueDocsWriter().write()
        }

        val platformSpecificHelper = PlatformSpecificHelpersDesktop(config)
        val desktopParameters = UncivGameParameters(
            cancelDiscordEvent = { discordTimer?.cancel() },
            fontImplementation = NativeFontDesktop((Fonts.ORIGINAL_FONT_SIZE * settings.fontSizeMultiplier).toInt(), settings.fontFamily),
            customFileLocationHelper = CustomFileLocationHelperDesktop(),
            crashReportSysInfo = CrashReportSysInfoDesktop(),
            platformSpecificHelper = platformSpecificHelper,
            audioExceptionHelper = HardenGdxAudio()
        )

        val game = UncivGame(desktopParameters)

        tryActivateDiscord(game)
        Lwjgl3Application(game, config)
    }


    /** Specialized function to access settings before Gdx is initialized.
     *
     * @param base Path to the directory where the file should be - if not set, the OS current directory is used (which is "/" on Android)
     */
    fun getSettingsBeforeGdxInitialized(base: String = "."): GameSettings {
        // FileHandle is Gdx, but the class and JsonParser are not dependent on app initialization
        // In fact, at this point Gdx.app or Gdx.files are null but this still works.
        val file = FileHandle(base + File.separator + SETTINGS_FILE_NAME)

        return if (file.exists())
            json().fromJsonFile(
                GameSettings::class.java,
                file
            )
        else GameSettings().apply {
            isFreshlyCreated = true
            resolution = "1200x800" // By default Desktops should have a higher resolution
            // LibGDX not yet configured, use regular java class
            val screensize = Toolkit.getDefaultToolkit().screenSize
            windowState = WindowState(
                width = screensize.width,
                height = screensize.height
            )
            file.writeString(json().toJson(this),false)
        }
    }


    private fun tryActivateDiscord(game: UncivGame) {
        try {
            /*
             We try to load the Discord library manually before the instance initializes.
             This is because if there's a crash when the instance initializes on a similar line,
              it's not within the bounds of the try/catch and thus the app will crash.
             */
            Native.load("discord-rpc", DiscordRPC::class.java)
            val handlers = DiscordEventHandlers()
            DiscordRPC.INSTANCE.Discord_Initialize("647066573147996161", handlers, true, null)

            Runtime.getRuntime().addShutdownHook(Thread { DiscordRPC.INSTANCE.Discord_Shutdown() })

            discordTimer = timer(name = "Discord", daemon = true, period = 1000) {
                try {
                    updateRpc(game)
                } catch (ex: Exception) {
                    debug("Exception while updating Discord Rich Presence", ex)
                }
            }
        } catch (ex: Throwable) {
            // This needs to be a Throwable because if we can't find the discord_rpc library, we'll get a UnsatisfiedLinkError, which is NOT an exception.
            debug("Could not initialize Discord")
        }
    }

    private fun updateRpc(game: UncivGame) {
        if (!game.isInitialized) return
        val presence = DiscordRichPresence()
        presence.largeImageKey = "logo" // The actual image is uploaded to the discord app / applications webpage

        val gameInfo = game.gameInfo
        if (gameInfo != null) {
            val currentPlayerCiv = gameInfo.getCurrentPlayerCivilization()
            presence.details = "${currentPlayerCiv.nation.leaderName} of ${currentPlayerCiv.nation.name}"
            presence.largeImageText = "Turn" + " " + currentPlayerCiv.gameInfo.turns
        }

        DiscordRPC.INSTANCE.Discord_UpdatePresence(presence)
    }
}
