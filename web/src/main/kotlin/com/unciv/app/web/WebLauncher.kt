package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration
import com.github.xpenatan.gdx.teavm.backends.web.WebWindowListener
import com.github.xpenatan.gdx.teavm.backends.web.filesystem.FileData
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.filedrop.DroppedFile
import com.unciv.ui.filedrop.FileDrop
import com.unciv.utils.Display
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode

object WebLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        Display.platform = WebDisplay()
        Fonts.fontImplementation = WebFont()

        val config = WebApplicationConfiguration("canvas").apply {
            width = 0
            height = 0
            useGL30 = true
            storagePrefix = "unciv"
            localStoragePrefix = "unciv"
            windowListener = UncivWindowListener()
        }
        WebApplication(WebUncivGame(), config)
    }
}

/** The current web application exposes a responsive viewport mode; browser fullscreen is not exposed here. */
internal class WebDisplay : PlatformDisplay {
    override fun getScreenModes(): Map<Int, ScreenMode> = mapOf(WebScreenMode.Windowed.getId() to WebScreenMode.Windowed)

    override fun setScreenMode(id: Int, settings: GameSettings) {
        // Browser screen modes and resolutions are controlled by the browser. Normalize stale
        // persisted ids to the only mode exposed by this responsive web application.
        settings.screenMode = WebScreenMode.Windowed.getId()
        if (Gdx.graphics.isFullscreen)
            Gdx.graphics.setWindowedMode(Gdx.graphics.width, Gdx.graphics.height)
    }

    private enum class WebScreenMode : ScreenMode {
        Windowed;

        override fun getId() = ordinal

        override fun toString() = name.tr()
    }
}

private class WebUncivGame : UncivGame() {
    override val supportsOnlineMultiplayer = false

    override fun getGcCount(): Int? = null

    override val canDownloadFromUrl get() = false
    override val hasJavaFileAccess: Boolean get() = false
    override val supportsFileDrop: Boolean get() = true
    override val canExitApp: Boolean get() = false
}

private class UncivWindowListener : WebWindowListener {
    override fun acceptFileDropped(file: String) =
        FileDrop.accepts(file)
    override fun filesDropped(files: Array<out FileData>) =
        FileDrop.dispatch(files.map { DroppedFile(it.path, it.bytes) })
}
