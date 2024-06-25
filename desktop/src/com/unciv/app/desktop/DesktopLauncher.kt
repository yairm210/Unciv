package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.unciv.app.desktop.DesktopScreenMode.Companion.getMaximumWindowBounds
import com.unciv.json.json
import com.unciv.logic.files.SETTINGS_FILE_NAME
import com.unciv.logic.files.UncivFiles
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.metadata.GameSettings.WindowState
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Display
import com.unciv.utils.Log
import kotlin.system.exitProcess

internal object DesktopLauncher {

    @JvmStatic
    fun main(arg: Array<String>) {

        // Setup Desktop logging
        Log.backend = DesktopLogBackend()

        // Setup Desktop display
        Display.platform = DesktopDisplay()

        // Setup Desktop font
        Fonts.fontImplementation = DesktopFont()

        // Setup Desktop saver-loader
        UncivFiles.saverLoader = DesktopSaverLoader()
        UncivFiles.preferExternalStorage = false

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
        config.setWindowSizeLimits(WindowState.minimumWidth, WindowState.minimumHeight, -1, -1)

        // LibGDX not yet configured, use regular java class
        val maximumWindowBounds = getMaximumWindowBounds()

        val settings = UncivFiles.getSettingsForPlatformLaunchers()
        if (settings.isFreshlyCreated) {
            settings.screenSize = ScreenSize.Large // By default we guess that Desktops have larger screens
            settings.windowState = WindowState(maximumWindowBounds)
            FileHandle(SETTINGS_FILE_NAME).writeString(json().toJson(settings), false, Charsets.UTF_8.name()) // so when we later open the game we get fullscreen
        }
        // Kludge! This is a workaround - the matching call in DesktopDisplay doesn't "take" quite permanently,
        // the window might revert to the "config" values when the user moves the window - worse if they
        // minimize/restore. And the config default is 640x480 unless we set something here.
        val (width, height) = settings.windowState.coerceIn(maximumWindowBounds)
        config.setWindowedMode(width, height)

        config.setInitialBackgroundColor(BaseScreen.clearColor)

        if (!isRunFromJAR) {
            UniqueDocsWriter().write()
            UiElementDocsWriter().write()
        }

        // HardenGdxAudio extends Lwjgl3Application, and the Lwjgl3Application constructor runs as long as the game runs
        HardenGdxAudio(DesktopGame(config), config)
        exitProcess(0)
    }
}
