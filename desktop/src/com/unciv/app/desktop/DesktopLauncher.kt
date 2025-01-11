package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.badlogic.gdx.utils.SharedLibraryLoader
import com.unciv.app.desktop.DesktopScreenMode.Companion.getMaximumWindowBounds
import com.unciv.json.json
import com.unciv.logic.files.SETTINGS_FILE_NAME
import com.unciv.logic.files.UncivFiles
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
import java.io.File
import kotlin.system.exitProcess

internal object DesktopLauncher {

    @JvmStatic
    fun main(arg: Array<String>) {
        if (SharedLibraryLoader.isMac) {
            Configuration.GLFW_LIBRARY_NAME.set("glfw_async")
        }

        // The uniques checker requires the file system to be seet up, which happens after lwjgw initializes it
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
            val errors = RulesetValidator(ruleset).getErrorList(true)
            println(errors.getErrorText(true))
            exitProcess(if (errors.any { it.errorSeverityToReport == RulesetErrorSeverity.Error }) 1 else 0)
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

        val isRunFromJAR = DesktopLauncher.javaClass.`package`.specificationVersion != null
        ImagePacker.packImages(isRunFromJAR)

        val config = Lwjgl3ApplicationConfiguration()
        config.setWindowIcon("ExtraImages/Icon.png")
        config.setTitle("Unciv")
        config.setHdpiMode(HdpiMode.Logical)
        config.setWindowSizeLimits(WindowState.minimumWidth, WindowState.minimumHeight, -1, -1)

        // LibGDX not yet configured, use regular java class
        val maximumWindowBounds = getMaximumWindowBounds()

        val settingsDirectory = customDataDir ?: "."

        val settings = UncivFiles.getSettingsForPlatformLaunchers(settingsDirectory)
        if (settings.isFreshlyCreated) {
            settings.screenSize = ScreenSize.Large // By default we guess that Desktops have larger screens
            settings.windowState = WindowState(maximumWindowBounds)

            FileHandle(settingsDirectory + File.separator + SETTINGS_FILE_NAME).writeString(json().toJson(settings), false, Charsets.UTF_8.name()) // so when we later open the game we get fullscreen
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
        HardenGdxAudio(DesktopGame(config, customDataDir), config)
        exitProcess(0)
    }
}
