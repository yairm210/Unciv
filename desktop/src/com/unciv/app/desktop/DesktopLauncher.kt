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
import com.unciv.json.json
import com.unciv.logic.files.SETTINGS_FILE_NAME
import com.unciv.logic.files.UncivFiles
import com.unciv.models.metadata.ScreenSize
import com.unciv.models.metadata.WindowState
import com.unciv.utils.Log
import com.unciv.utils.debug
import java.awt.Toolkit
import java.awt.GraphicsEnvironment
import java.util.*
import kotlin.concurrent.timer
import kotlin.math.max
import kotlin.math.min


internal object DesktopLauncher {
    private const val minWidth = 120
    private const val minHeight = 80

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
        config.setMaximized(true)
        config.setWindowSizeLimits(minWidth, minHeight, -1, -1)

        // We don't need the initial Audio created in Lwjgl3Application, HardenGdxAudio will replace it anyway.
        // Note that means config.setAudioConfig() would be ignored too, those would need to go into the HardenedGdxAudio constructor.
        config.disableAudio(true)

        val settings = UncivFiles.getSettingsForPlatformLaunchers()
        if (settings.isFreshlyCreated) {
            settings.screenSize = ScreenSize.Large // By default we guess that Desktops have larger screens
            // LibGDX not yet configured, use regular java class
            val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val maximumWindowBounds = graphicsEnvironment.maximumWindowBounds
            settings.windowState = WindowState(
                width = maximumWindowBounds.width * 4 / 5,
                height = maximumWindowBounds.height * 4 / 5,
                isMaximized = true
            )
            FileHandle(SETTINGS_FILE_NAME).writeString(json().toJson(settings), false) // so when we later open the game we get fullscreen
        }

        // Find the destination monitor by name (relevant only when maximized, otherwise the position alone is key)
        val monitors = Lwjgl3ApplicationConfiguration.getMonitors() // used twice here, so cache it
        val maximizedMonitor = monitors
            .firstOrNull { it.name == settings.windowState.monitor }
            ?: Lwjgl3ApplicationConfiguration.getPrimaryMonitor()

        // Cache saved dimensions for visibility test
        var windowState = settings.windowState  // will be replaced if window found outside hardware
        val x = windowState.x
        val y = windowState.y
        val width = windowState.width.coerceAtLeast(minWidth)
        val height = windowState.height.coerceAtLeast(minHeight)

        // Calculate how much of the saved window is visible on the current monitor config
        // - which might differ from the monitors available when the WindowState was saved
        // (overlap logic same as in Lwjgl3Graphics.getMonitor)
        var totalOverlap = 0
        for (monitor in monitors) {
            val mode = Lwjgl3ApplicationConfiguration.getDisplayMode(monitor)
            val overlapX = max(0, min(x + width, monitor.virtualX + mode.width) - max(x, monitor.virtualX))
            val overlapY = max(0, min(y + height, monitor.virtualY + mode.height) - max(y, monitor.virtualY))
            totalOverlap += overlapX * overlapY
        }

        if (totalOverlap * 3 < width * height) {
            // If less than (this chosen arbitrarily) 33% of the window will still be visible,
            // fix windowState to omit the position, which will center on the primary monitor or the
            // maximized-to monitor if maximized and that monitor's name was found
            windowState = WindowState(width, height, windowState.isMaximized, monitor = windowState.monitor)
        } else {
            config.setWindowPosition(x, y)
        }
        config.setWindowedMode(width, height)
        config.setMaximizedMonitor(maximizedMonitor)
        config.setMaximized(windowState.isMaximized)

        if (!isRunFromJAR) {
            UniqueDocsWriter().write()
            UiElementDocsWriter().write()
        }

        val platformSpecificHelper = PlatformSpecificHelpersDesktop(config, windowState)

        val desktopParameters = UncivGameParameters(
            cancelDiscordEvent = { discordTimer?.cancel() },
            fontImplementation = FontDesktop(),
            customFileLocationHelper = CustomFileLocationHelperDesktop(),
            crashReportSysInfo = CrashReportSysInfoDesktop(),
            platformSpecificHelper = platformSpecificHelper,
            audioExceptionHelper = HardenGdxAudio()
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
