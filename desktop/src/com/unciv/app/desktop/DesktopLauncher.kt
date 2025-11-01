package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.badlogic.gdx.utils.Os
import com.badlogic.gdx.utils.SharedLibraryLoader
import com.unciv.UncivGame
import com.unciv.app.desktop.DesktopScreenMode.Companion.getMaximumWindowBounds
import com.unciv.json.json
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.files.UncivFiles.Companion.SETTINGS_FILE_NAME
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.metadata.GameSettings.WindowState
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.ruleset.validation.UniqueAutoUpdater
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Display
import com.unciv.utils.Log
import org.lwjgl.system.Configuration
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Taskbar
import java.awt.Toolkit
import java.io.File
import java.net.URL
import kotlin.system.exitProcess


internal object DesktopLauncher {

    @JvmStatic
    fun main(arg: Array<String>) {

        // The uniques checker requires the file system to be set up, which happens after lwjgl initializes it
        if (arg.isNotEmpty() && arg[0] == "mod-ci") {
            ImagePacker.packImagesPerMod(".", ".")
            val ruleset = Ruleset()
            ruleset.folderLocation = FileHandle(".")
            val jsonsFolder = FileHandle("jsons")
            if (jsonsFolder.exists()) {
                // Load vanilla ruleset from the JAR, in case the mod requires parts of it
                RulesetCache.loadRulesets(consoleMode = true, noMods = true)
                // Load the actual ruleset here
                ruleset.load(jsonsFolder)
            }
            UniqueAutoUpdater.autoupdateUniques(ruleset)
            val errors = RulesetValidator.create(ruleset, true).getErrorList()
            println(errors.getErrorText(true))
            exitProcess(if (errors.any { it.errorSeverityToReport == RulesetErrorSeverity.Error }) 1 else 0)
        }

        if (arg.isNotEmpty() && arg[0] == "--version") {
            println(UncivGame.VERSION.text)
            exitProcess(0)
        }

        if (SharedLibraryLoader.os == Os.MacOsX) {
            Configuration.GLFW_LIBRARY_NAME.set("glfw_async")
            // Since LibGDX 1.13.1 on Mac you cannot call Lwjgl3ApplicationConfiguration.getPrimaryMonitor()
            //  before GraphicsEnvironment.getLocalGraphicsEnvironment().
            GraphicsEnvironment.getLocalGraphicsEnvironment()
        }

        val customDataDirPrefix="--data-dir="
        val customDataDir = arg.find { it.startsWith(customDataDirPrefix) }?.removePrefix(customDataDirPrefix)

        // Setup Desktop logging
        Log.backend = DesktopLogBackend()

        // Setup Desktop display
        Display.platform = DesktopDisplay()

        // Setup Desktop font
        Fonts.fontImplementation = DesktopFont()

        // Setup Desktop saver-loader
        UncivFiles.saverLoader = if (LinuxX11SaverLoader.isRequired()) LinuxX11SaverLoader() else DesktopSaverLoader()
        UncivFiles.preferExternalStorage = false

        // Solves a rendering problem in specific GPUs and drivers.
        // For more info see https://github.com/yairm210/Unciv/pull/3202 and https://github.com/LWJGL/lwjgl/issues/119
        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true")

        val dataDirectory = customDataDir ?: "."

        val isRunFromJAR = DesktopLauncher.javaClass.`package`.specificationVersion != null
        ImagePacker.packImages(isRunFromJAR, dataDirectory)

        val config = Lwjgl3ApplicationConfiguration()
        config.setWindowIcon("ExtraImages/Icons/Unciv32.png", "ExtraImages/Icons/Unciv128.png")
        if (SharedLibraryLoader.os == Os.MacOsX) updateDockIconForMacOs("ExtraImages/Icons/Unciv128.png")
        config.setTitle("Unciv")
        config.setHdpiMode(HdpiMode.Logical)
        config.setWindowSizeLimits(WindowState.minimumWidth, WindowState.minimumHeight, -1, -1)


        // LibGDX not yet configured, use regular java class
        val maximumWindowBounds = getMaximumWindowBounds()


        val settings = UncivFiles.getSettingsForPlatformLaunchers(dataDirectory)
        if (settings.isFreshlyCreated) {
            settings.screenSize = ScreenSize.Large // By default we guess that Desktops have larger screens
            settings.windowState = WindowState(maximumWindowBounds)

            FileHandle(dataDirectory + File.separator + SETTINGS_FILE_NAME).writeString(json().toJson(settings), false, Charsets.UTF_8.name()) // so when we later open the game we get fullscreen
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

        // the Lwjgl3Application constructor runs as long as the game runs
        Lwjgl3Application(DesktopGame(config, customDataDir), config)
        exitProcess(0)
    }

    private fun updateDockIconForMacOs(fileName: String) {
        try {
            val defaultToolkit: Toolkit = Toolkit.getDefaultToolkit()
            val imageResource: URL = FileHandle(fileName).file().toURI().toURL()
            val image: Image = defaultToolkit.getImage(imageResource)
            val taskbar = Taskbar.getTaskbar()
            taskbar.iconImage = image
        } catch (_: Throwable) { }
    }
}
