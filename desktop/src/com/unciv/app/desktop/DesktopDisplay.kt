package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode
import java.awt.GraphicsEnvironment


enum class DesktopScreenMode : ScreenMode {
    // Order will be preserved in Options Popup
    Windowed {
        override fun activate(settings: GameSettings) {
            Gdx.graphics.setUndecorated(false)
            val isFillingDesktop = setWindowedMode(settings)
            if (isFillingDesktop)
                getWindow()?.maximizeWindow()
        }

        override fun hasUserSelectableSize() = true
    },
    Fullscreen {
        override fun activate(settings: GameSettings) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
        }
        override fun hasUserSelectableSize() = false
    },
    Borderless {
        override fun activate(settings: GameSettings) {
            getWindow()?.restoreWindow()
            Gdx.graphics.setUndecorated(true)
            setWindowedMode(settings)
        }
        override fun hasUserSelectableSize() = true
    },
    ;

    override fun getId() = ordinal

    override fun toString() = name.tr()

    abstract fun activate(settings: GameSettings)

    /** @return `true` if window fills entire desktop */
    protected fun setWindowedMode(settings: GameSettings): Boolean {
        // Calling AWT after Gdx is fully initialized seems icky, but seems to have no side effects
        // Found no equivalent in Gdx - available _desktop_ surface without taskbars etc
        val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val maximumWindowBounds = graphicsEnvironment.maximumWindowBounds

        // Make sure an inappropriate saved size doesn't make the window unusable
        val width = settings.windowState.width.coerceIn(120, maximumWindowBounds.width)
        val height = settings.windowState.height.coerceIn(80, maximumWindowBounds.height)

        // Kludge - see also DesktopLauncher - without, moving the window might revert to the size stored in config
        (Lwjgl3Application::class.java).getDeclaredField("config").run {
            isAccessible = true
            get(Gdx.app) as Lwjgl3ApplicationConfiguration
        }.setWindowedMode(width, height)

        Gdx.graphics.setWindowedMode(width, height)

        // Another kludge, prevents visual glitches and crashing cinnamon on Linux Mint
        getWindow()?.run {
            setPosition(positionX, positionY)
        }

        return width == maximumWindowBounds.width && height == maximumWindowBounds.height
    }

    companion object {
        operator fun get(id: Int) = values()[id]

        private fun getWindow() = (Gdx.graphics as? Lwjgl3Graphics)?.window
    }
}

class DesktopDisplay : PlatformDisplay {

    override fun getScreenModes() =
        DesktopScreenMode.values().associateBy { it.getId() }

    override fun setScreenMode(id: Int, settings: GameSettings) {
        DesktopScreenMode[id].activate(settings)
    }

    override fun hasUserSelectableSize(id: Int) =
        DesktopScreenMode[id].hasUserSelectableSize()
}
